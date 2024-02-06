package com.jeffdisher.october.process;

import java.io.IOException;
import java.net.InetAddress;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.ReentrantLock;

import com.jeffdisher.october.client.ClientRunner;
import com.jeffdisher.october.client.IClientAdapter;
import com.jeffdisher.october.client.SpeculativeProjection;
import com.jeffdisher.october.data.CuboidCodec;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.net.NetworkClient;
import com.jeffdisher.october.net.Packet;
import com.jeffdisher.october.net.Packet_CuboidFragment;
import com.jeffdisher.october.net.Packet_CuboidStart;
import com.jeffdisher.october.net.Packet_EndOfTick;
import com.jeffdisher.october.net.Packet_Entity;
import com.jeffdisher.october.net.Packet_MutationBlock;
import com.jeffdisher.october.net.Packet_MutationEntityFromClient;
import com.jeffdisher.october.net.Packet_MutationEntityFromServer;
import com.jeffdisher.october.net.Packet_RemoveEntity;
import com.jeffdisher.october.registries.Craft;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.utils.Assert;


/**
 * Internally manages connecting to the server, making updates to an internal projection, translating calls into
 * mutations, sending the mutations to the server, and maintaining network state.
 * Exposes a high-level action interface and callbacks (called on caller thread when it is available) related to changes
 * to the game state.  Internally, this is essentially just connecting ClientRunner to NetworkClient.
 */
public class ClientProcess
{
	private final IListener _listener;
	private final _LockingList _pendingCallbacks;
	private final ClientRunner _clientRunner;
	private IClientAdapter.IListener _messagesToClientRunner;

	// State which can be used in synchronization, as it is updated by background threads.
	private long _lastTickFromServer;
	private int _assignedClientId;
	private boolean _isEntityLoaded;

	private final NetworkClient _client;

	// Network buffer state.
	private final ReentrantLock _networkBufferLock;
	private boolean _networkReady;
	private final Queue<Packet> _outgoing;

	/**
	 * Creates and starts up a new client process.  Returns once the connection is established, but before the handshake
	 * is complete.
	 * 
	 * @param listener The callback interface which will receive updates.
	 * @param address The address of the server.
	 * @param port The port number of the server.
	 * @param clientName The name the client should use to identify itself.
	 * @throws IOException There was a network error connecting to the server.
	 */
	public ClientProcess(IListener listener, InetAddress address, int port, String clientName) throws IOException
	{
		_listener = listener;
		_pendingCallbacks = new _LockingList();
		_clientRunner = new ClientRunner(new _NetworkAdapter(), new _ProjectionListener(), new _RunnerListener());
		// This should be set once the runner is started but before it returns.
		Assert.assertTrue(null != _messagesToClientRunner);
		
		// Create the connection (note that this will return when the connection is accepted but the handshake completes in the background).
		_client = new NetworkClient(new _NetworkClientListener(), address, port, clientName);
		
		_networkBufferLock = new ReentrantLock();
		_networkReady = false;
		_outgoing = new LinkedList<>();
	}

	/**
	 * Shuts down the local client logic and network connection.
	 */
	public void disconnect()
	{
		_clientRunner.disconnect();
	}

	/**
	 * The generic way to send actions into the client layer.  Changes will be applied to the local projection and sent
	 * to the server.
	 * 
	 * @param change The change to apply.
	 * @param commitLevel The commit level associated with this change.
	 */
	public void sendAction(IMutationEntity change, long commitLevel)
	{
		Packet_MutationEntityFromClient packet = new Packet_MutationEntityFromClient(change, commitLevel);
		_background_bufferPacket(packet);
		_runPendingCallbacks();
	}

	/**
	 * Waits until this client's ID has been received with the end of the handshake.
	 * 
	 * @return The client's ID.
	 * @throws InterruptedException Interrupted while waiting.
	 */
	public synchronized int waitForClientId() throws InterruptedException
	{
		while (0 == _assignedClientId)
		{
			this.wait();
		}
		return _assignedClientId;
	}

	/**
	 * Waits until this client's first entity packet has arrived and returns the latest tick observed.
	 * NOTE:  Even though the packet has been received, this may return before it has been relayed to the listener as
	 * there is some buffering of those callbacks.
	 * 
	 * @return The last tick observed in a packet.
	 * @throws InterruptedException Interrupted while waiting.
	 */
	public synchronized long waitForLocalEntity(long currentTimeMillis) throws InterruptedException
	{
		while (!_isEntityLoaded)
		{
			this.wait();
		}
		_clientRunner.runPendingCalls(currentTimeMillis);
		_runPendingCallbacks();
		return _lastTickFromServer;
	}

	/**
	 * Waits until the packet with the given tick number has been observed.
	 * NOTE:  Even though the packet has been received, this may return before it has been relayed to the listener as
	 * there is some buffering of those callbacks.
	 * 
	 * @return The last tick observed in a packet.
	 * @throws InterruptedException Interrupted while waiting.
	 */
	public synchronized long waitForTick(long tickNumber, long currentTimeMillis) throws InterruptedException
	{
		while (_lastTickFromServer < tickNumber)
		{
			this.wait();
		}
		_clientRunner.runPendingCalls(currentTimeMillis);
		_runPendingCallbacks();
		return _lastTickFromServer;
	}

	/**
	 * Runs any pending call-outs.
	 * 
	 * @param currentTimeMillis The current time, in milliseconds.
	 */
	public void runPendingCalls(long currentTimeMillis)
	{
		_clientRunner.runPendingCalls(currentTimeMillis);
		_runPendingCallbacks();
	}

	/**
	 * Creates the change to begin breaking a block.  Note that changes which come in before it completes will
	 * invalidate it.
	 * Note that this CANNOT be called if there is still an in-progress activity running (as that could allow the move
	 * to be "from" a stale location).  Call "isActivityInProgress()" first.
	 * 
	 * @param blockLocation The location of the block to break.
	 * @param expectedBlock The block we expect to see in this location (so we fail on race).
	 * @param currentTimeMillis The current time, in milliseconds.
	 * @return The number of milliseconds this operation will take (meaning an explicit cancel should be sent if it
	 * shouldn't wait to complete).
	 */
	public long beginBreakBlock(AbsoluteLocation blockLocation, Item expectedBlock, long currentTimeMillis)
	{
		long millisCost = _clientRunner.beginBreakBlock(blockLocation, expectedBlock, currentTimeMillis);
		_runPendingCallbacks();
		return millisCost;
	}

	/**
	 * Creates the mutation to the entity to begin the sequence of operations to pick up items from an inventory block.
	 * Note that this CANNOT be called if there is still an in-progress activity running.  Call "isActivityInProgress()"
	 * first.
	 * 
	 * @param blockLocation The location of the block containing items.
	 * @param itemsToPull The items to transfer (actual item transfer could be smaller).
	 * @param currentTimeMillis The current time, in milliseconds.
	 */
	public void pullItemsFromInventory(AbsoluteLocation blockLocation, Items itemsToPull, long currentTimeMillis)
	{
		_clientRunner.pullItemsFromInventory(blockLocation, itemsToPull, currentTimeMillis);
		_runPendingCallbacks();
	}

	/**
	 * Creates the mutation to the entity to begin the sequence of operations to put items into a block's inventory.
	 * Note that this CANNOT be called if there is still an in-progress activity running.  Call "isActivityInProgress()"
	 * first.
	 * 
	 * @param blockLocation The location of the block where the items should be stored.
	 * @param itemsToPush The items to transfer (actual item transfer could be smaller if they can't all fit).
	 * @param currentTimeMillis The current time, in milliseconds.
	 */
	public void pushItemsToInventory(AbsoluteLocation blockLocation, Items itemsToPush, long currentTimeMillis)
	{
		_clientRunner.pushItemsToInventory(blockLocation, itemsToPush, currentTimeMillis);
		_runPendingCallbacks();
	}

	/**
	 * Changes the item selected by the current entity.
	 * 
	 * @param itemType The item type to select (will fail if not in their inventory).
	 * @param currentTimeMillis The current time, in milliseconds.
	 */
	public void selectItemInInventory(Item itemType, long currentTimeMillis)
	{
		_clientRunner.selectItemInInventory(itemType, currentTimeMillis);
		_runPendingCallbacks();
	}

	/**
	 * Places an instance of the item currently selected in the inventory in the world.
	 * 
	 * @param blockLocation The location where the block will be placed.
	 * @param currentTimeMillis The current time, in milliseconds.
	 */
	public void placeSelectedBlock(AbsoluteLocation blockLocation, long currentTimeMillis)
	{
		_clientRunner.placeSelectedBlock(blockLocation, currentTimeMillis);
		_runPendingCallbacks();
	}

	/**
	 * Creates the change to move the entity from the current location in the speculative projection by the given x/y
	 * distances.  The change will internally account for things like an existing z-vector, when falling or jumping,
	 * when building the final target location.
	 * Additionally, it will ignore the x and y movements if they aren't possible (hitting a wall), allowing any
	 * existing z movement to be handled.
	 * Note that this CANNOT be called if there is still an in-progress activity running (as that could allow the move
	 * to be "from" a stale location).  Call "isActivityInProgress()" first.
	 * 
	 * @param xDistance How far to move in the x direction.
	 * @param yDistance How far to move in the y direction.
	 * @param currentTimeMillis The current time, in milliseconds.
	 */
	public void moveHorizontal(float xDistance, float yDistance, long currentTimeMillis)
	{
		_clientRunner.moveHorizontal(xDistance, yDistance, currentTimeMillis);
		_runPendingCallbacks();
	}

	/**
	 * Makes the entity "jump", giving it a positive z-vector.
	 * Note that this CANNOT be called if there is still an in-progress activity running (as that could allow the move
	 * to be "from" a stale location).  Call "isActivityInProgress()" first.
	 * 
	 * @param currentTimeMillis The current time, in milliseconds.
	 */
	public void jump(long currentTimeMillis)
	{
		_clientRunner.jump(currentTimeMillis);
		_runPendingCallbacks();
	}

	/**
	 * Requests a crafting operation start.
	 * 
	 * @param operation The crafting operation to run.
	 * @param currentTimeMillis The current time, in milliseconds.
	 */
	public void craft(Craft operation, long currentTimeMillis)
	{
		_clientRunner.craft(operation, currentTimeMillis);
		_runPendingCallbacks();
	}

	/**
	 * Allows time to pass to account for things like falling, etc.
	 * 
	 * @param currentTimeMillis The current time, in milliseconds.
	 */
	public void doNothing(long currentTimeMillis)
	{
		_clientRunner.doNothing(currentTimeMillis);
		_runPendingCallbacks();
	}

	/**
	 * Tries to complete any in-progress activity, returning true if it is still pending and false if it completed or
	 * there was nothing pending.
	 * 
	 * @param currentTimeMillis The current time, in milliseconds.
	 * @return True if there is still an in-progress activity pending.
	 */
	public boolean isActivityInProgress(long currentTimeMillis)
	{
		return _clientRunner.isActivityInProgress(currentTimeMillis);
	}


	private void _background_bufferPacket(Packet packet)
	{
		_networkBufferLock.lock();
		try
		{
			if (_networkReady)
			{
				_client.sendMessage(packet);
				_networkReady = false;
			}
			else
			{
				_outgoing.add(packet);
			}
		}
		finally
		{
			_networkBufferLock.unlock();
		}
	}

	private void _background_networkReadyForWrite()
	{
		_networkBufferLock.lock();
		try
		{
			Assert.assertTrue(!_networkReady);
			if (_outgoing.isEmpty())
			{
				_networkReady = true;
			}
			else
			{
				Packet packet = _outgoing.poll();
				_client.sendMessage(packet);
			}
		}
		finally
		{
			_networkBufferLock.unlock();
		}
	}

	private synchronized void _background_updateTickNumber(long latestTickNumer)
	{
		Assert.assertTrue((0 == _lastTickFromServer) || ((_lastTickFromServer + 1) == latestTickNumer));
		_lastTickFromServer = latestTickNumer;
		this.notifyAll();
	}

	private synchronized void _background_setClientId(int assignedId)
	{
		Assert.assertTrue(0 == _assignedClientId);
		Assert.assertTrue(assignedId > 0);
		_assignedClientId = assignedId;
		this.notifyAll();
	}

	private synchronized void _background_entityLoaded(int entityId)
	{
		if (!_isEntityLoaded && (entityId == _assignedClientId))
		{
			_isEntityLoaded = true;
			this.notifyAll();
		}
	}

	private void _runPendingCallbacks()
	{
		Runnable next = _pendingCallbacks.getNext();
		while (null != next)
		{
			next.run();
			next = _pendingCallbacks.getNext();
		}
	}


	// NOTE:  These callbacks are issued on the background network thread.
	private class _NetworkClientListener implements NetworkClient.IListener
	{
		private CuboidCodec.Deserializer _deserializer = null;
		
		@Override
		public void handshakeCompleted(int assignedId)
		{
			_messagesToClientRunner.adapterConnected(assignedId);
			_background_setClientId(assignedId);
		}
		@Override
		public synchronized void networkReady()
		{
			_background_networkReadyForWrite();
		}
		@Override
		public void packetReceived(Packet packet)
		{
			// We need to decode this for the ClientRunner.
			// For now, we will just do this with type checks although we might want to use a virtual call into the Packet to clean this up, in the future.
			if (packet instanceof Packet_CuboidStart)
			{
				Assert.assertTrue(null == _deserializer);
				_deserializer = new CuboidCodec.Deserializer((Packet_CuboidStart) packet);
			}
			else if (packet instanceof Packet_CuboidFragment)
			{
				CuboidData done = _deserializer.processPacket((Packet_CuboidFragment) packet);
				if (null != done)
				{
					_deserializer = null;
					_messagesToClientRunner.receivedCuboid(done);
				}
			}
			else if (packet instanceof Packet_Entity)
			{
				Packet_Entity safe = (Packet_Entity)packet;
				_messagesToClientRunner.receivedEntity(safe.entity);
				_background_entityLoaded(safe.entity.id());
			}
			else if (packet instanceof Packet_MutationEntityFromClient)
			{
				// The client never receives this type.
				throw Assert.unreachable();
			}
			else if (packet instanceof Packet_MutationEntityFromServer)
			{
				Packet_MutationEntityFromServer safe = (Packet_MutationEntityFromServer) packet;
				_messagesToClientRunner.receivedChange(safe.entityId, safe.mutation);
			}
			else if (packet instanceof Packet_MutationBlock)
			{
				Packet_MutationBlock safe = (Packet_MutationBlock) packet;
				_messagesToClientRunner.receivedMutation(safe.mutation);
			}
			else if (packet instanceof Packet_EndOfTick)
			{
				Packet_EndOfTick safe = (Packet_EndOfTick) packet;
				_messagesToClientRunner.receivedEndOfTick(safe.tickNumber, safe.latestLocalCommitIncluded);
				_background_updateTickNumber(safe.tickNumber);
			}
			else if (packet instanceof Packet_RemoveEntity)
			{
				Packet_RemoveEntity safe = (Packet_RemoveEntity)packet;
				_messagesToClientRunner.removeEntity(safe.entityId);
			}
		}
	}

	// NOTE:  These callbacks are issued on the background network thread.
	private class _NetworkAdapter implements IClientAdapter
	{
		@Override
		public void connectAndStartListening(IListener listener)
		{
			Assert.assertTrue(null == _messagesToClientRunner);
			_messagesToClientRunner = listener;
		}
		@Override
		public void disconnect()
		{
			_client.stop();
		}
		@Override
		public void sendChange(IMutationEntity change, long commitLevel)
		{
			Packet_MutationEntityFromClient packet = new Packet_MutationEntityFromClient(change, commitLevel);
			_background_bufferPacket(packet);
		}
	}

	// Note that these calls are issued on the thread which calls into the ClientRunner, meaning the thread we are treating as "main", from the user.
	private class _ProjectionListener implements SpeculativeProjection.IProjectionListener
	{
		@Override
		public void cuboidDidLoad(IReadOnlyCuboidData cuboid)
		{
			_pendingCallbacks.add(() -> {
				_listener.cuboidDidLoad(cuboid);
			});
		}
		@Override
		public void cuboidDidChange(IReadOnlyCuboidData cuboid)
		{
			_pendingCallbacks.add(() -> {
				_listener.cuboidDidChange(cuboid);
			});
		}
		@Override
		public void cuboidDidUnload(CuboidAddress address)
		{
			_pendingCallbacks.add(() -> {
				_listener.cuboidDidUnload(address);
			});
		}
		@Override
		public void entityDidLoad(Entity entity)
		{
			_pendingCallbacks.add(() -> {
				_listener.entityDidLoad(entity);
			});
		}
		@Override
		public void entityDidChange(Entity entity)
		{
			_pendingCallbacks.add(() -> {
				_listener.entityDidChange(entity);
			});
		}
		@Override
		public void entityDidUnload(int id)
		{
			_pendingCallbacks.add(() -> {
				_listener.entityDidUnload(id);
			});
		}
	}

	// Note that these calls are issued on the thread which calls into the ClientRunner, meaning the thread we are treating as "main", from the user.
	private class _RunnerListener implements ClientRunner.IListener
	{
		@Override
		public void clientDidConnectAndLogin(int assignedLocalEntityId)
		{
			_pendingCallbacks.add(() -> {
				_listener.connectionEstablished(assignedLocalEntityId);
			});
		}
		@Override
		public void clientDisconnected()
		{
			_messagesToClientRunner.adapterDisconnected();
			_pendingCallbacks.add(() -> {
				_listener.connectionClosed();
			});
		}
	}

	private static class _LockingList
	{
		private final Queue<Runnable> _queue = new LinkedList<>();
		public synchronized void add(Runnable next)
		{
			_queue.add(next);
		}
		public synchronized Runnable getNext()
		{
			return _queue.isEmpty()
					? null
					: _queue.poll()
			;
		}
	}

	/**
	 * Note that these callbacks are issued on the caller's thread when it calls into the process, but they are buffered
	 * between those calls.  This means that minimally something like runPendingCalls() must be called, periodically.
	 */
	public interface IListener
	{
		void connectionEstablished(int assignedEntityId);
		void connectionClosed();
		
		void cuboidDidLoad(IReadOnlyCuboidData cuboid);
		void cuboidDidChange(IReadOnlyCuboidData cuboid);
		void cuboidDidUnload(CuboidAddress address);
		
		void entityDidLoad(Entity entity);
		void entityDidChange(Entity entity);
		void entityDidUnload(int id);
	}
}