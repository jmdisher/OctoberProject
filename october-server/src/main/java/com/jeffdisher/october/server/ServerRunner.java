package com.jeffdisher.october.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.ScheduledMutation;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
import com.jeffdisher.october.persistence.ResourceLoader;
import com.jeffdisher.october.persistence.SuspendedCuboid;
import com.jeffdisher.october.server.TickRunner.Snapshot;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.utils.Assert;
import com.jeffdisher.october.utils.MessageQueue;


/**
 * This is the logical top-level of the server.  It is still designed to be embedded, so it must be created by an
 * external main and all of its external dependencies injected.  It will still create internal utilities which is owns
 * and configures.
 * It runs in its own thread, primarily just to isolate its lifecycle from other components.
 */
public class ServerRunner
{
	/**
	 * Currently, we default to running 4 threads since that should give reasonable stress testing while being feasible
	 * on modern systems.
	 */
	public static final int TICK_RUNNER_THREAD_COUNT = 4;
	/**
	 * The "fake" client receives the end-of-tick message so that tests can see progress before client connections.
	 */
	public static final int FAKE_CLIENT_ID = 0;
	/**
	 * The number of milliseconds in a tick in the standard configuration.
	 */
	public static final long DEFAULT_MILLIS_PER_TICK = 100L;

	// General and configuration variables.
	private final long _millisPerTick;
	private final IServerAdapter _network;
	private final ResourceLoader _loader;
	private final TickRunner _tickRunner;

	// Information related to internal thread state and message passing.
	private final MessageQueue _messages;
	private final Thread _background;

	// Variables "owned" by the background thread.
	private final LongSupplier _currentTimeMillisProvider;
	private long _nextTickMillis;
	private final Map<Integer, ClientState> _connectedClients;
	private final Queue<Integer> _newClients;
	private final Queue<Integer> _removedClients;
	private final _TickAdvancer _tickAdvancer;
	// When we are due to start the next tick (after we receive the callback that the previous is done), we schedule the advancer.
	private _TickAdvancer _scheduledAdvancer;

	// Note that the runner takes ownership of CuboidLoader and will shut it down.
	public ServerRunner(long millisPerTick
			, IServerAdapter network
			, ResourceLoader loader
			, LongSupplier currentTimeMillisProvider
	)
	{
		_millisPerTick = millisPerTick;
		NetworkListener networkListener = new NetworkListener();
		network.readyAndStartListening(networkListener);
		_network = network;
		_loader = loader;
		TickListener tickListener = new TickListener();
		_tickRunner = new TickRunner(TICK_RUNNER_THREAD_COUNT, _millisPerTick, tickListener);
		
		_messages = new MessageQueue();
		_background = new Thread(()-> {
			try
			{
				_backgroundMain();
			}
			catch (Throwable t)
			{
				// This is a fatal error so just stop.
				// We will manage this differently in the future but this makes test/debug turn-around simpler in the near-term.
				t.printStackTrace();
				System.exit(101);
			}
		});
		_currentTimeMillisProvider = currentTimeMillisProvider;
		
		_nextTickMillis = _currentTimeMillisProvider.getAsLong() + _millisPerTick;
		_connectedClients = new HashMap<>();
		_newClients = new LinkedList<>();
		_removedClients = new LinkedList<>();
		_tickAdvancer = new _TickAdvancer();
		
		// Starting a thread in a constructor isn't ideal but this does give us a simple interface.
		_background.start();
		// Start the TickRunner, once we have set everything up.
		_tickRunner.start();
	}

	/**
	 * Shuts down the server, returning once the internal thread has joined.
	 */
	public void shutdown()
	{
		// Stop accepting messages.
		_messages.shutdown();
		
		// Stop the background thread so that it stops trying to run ticks.
		try
		{
			_background.join();
		}
		catch (InterruptedException e)
		{
			// We don't use interruption.
			throw Assert.unexpected(e);
		}
		
		// Shut down the tick runner.
		_tickRunner.shutdown();
		
		// Shut down the cuboid loader.
		// (first, we want to finish any remaining write-back).
		if (!_tickAdvancer.completedCuboids.isEmpty())
		{
			// We need to package up the cuboids with any suspended operations.
			Collection<SuspendedCuboid<IReadOnlyCuboidData>> cuboidResources = new ArrayList<>();
			for (IReadOnlyCuboidData cuboid : _tickAdvancer.completedCuboids)
			{
				CuboidAddress address = cuboid.getCuboidAddress();
				List<ScheduledMutation> suspended = _tickAdvancer.scheduledBlockMutations.get(address);
				if (null == suspended)
				{
					suspended = List.of();
				}
				cuboidResources.add(new SuspendedCuboid<IReadOnlyCuboidData>(cuboid, suspended));
			}
			_loader.writeBackToDisk(cuboidResources, _tickAdvancer.completedEntities);
		}
		_loader.shutdown();
	}


	private void _backgroundMain()
	{
		long currentTime = _currentTimeMillisProvider.getAsLong();
		long millisToWait = _nextTickMillis - currentTime;
		while (millisToWait <= 0L)
		{
			System.out.println("WARNING:  Dropping tick on startup!");
			_nextTickMillis += _millisPerTick;
			millisToWait = _nextTickMillis - currentTime;
		}
		Runnable next = _messages.pollForNext(millisToWait, _scheduledAdvancer);
		while (null != next)
		{
			next.run();
			// If we are ready to schedule the tick advancer, find out when.
			if (null != _scheduledAdvancer)
			{
				currentTime = _currentTimeMillisProvider.getAsLong();
				millisToWait = _nextTickMillis - currentTime;
				while (millisToWait <= 0L)
				{
					// This was already due so skip to the next.
					System.out.println("WARNING:  Dropping tick!");
					_nextTickMillis += _millisPerTick;
					millisToWait = _nextTickMillis - currentTime;
				}
			}
			next = _messages.pollForNext(millisToWait, _scheduledAdvancer);
		}
	}


	private class NetworkListener implements IServerAdapter.IListener
	{
		@Override
		public void clientConnected(int clientId)
		{
			_messages.enqueue(() -> {
				// Add this to the list of new clients (we will send them the snapshot and inject them after the the current tick is done tick).
				_newClients.add(clientId);
				System.out.println("Client connected: " + clientId);
			});
		}
		@Override
		public void clientDisconnected(int clientId)
		{
			_messages.enqueue(() -> {
				// Remove them from the list of connected clients (we can do this immediately).
				_connectedClients.remove(clientId);
				// We also want to add them to the list of clients which must be unloaded in the logic engine.
				_removedClients.add(clientId);
				System.out.println("Client disconnected: " + clientId);
			});
		}
		@Override
		public void changeReceived(int clientId, IMutationEntity change, long commitLevel)
		{
			_messages.enqueue(() -> {
				// This doesn't need to enter the TickRunner at any particular time so we can add it here and it will be rolled into the next tick.
				boolean didAdd = _tickRunner.enqueueEntityChange(clientId, change, commitLevel);
				if (!didAdd)
				{
					// There is something wrong with this client so disconnect them.
					System.out.println("Disconnecting client due to overflow: " + clientId);
					_network.disconnectClient(clientId);
				}
			});
		}
	}

	private class TickListener implements Consumer<TickRunner.Snapshot>
	{
		@Override
		public void accept(Snapshot completedSnapshot)
		{
			// We capture this callback to keep the message queue in-order:  This is the last thing which happens within
			// the tick so we know that all tick callbacks are completed by the time we execute this message so we can
			// update the connected clients.
			_messages.enqueue(() -> {
				// This means we can schedule the next tick.
				_scheduledAdvancer = _tickAdvancer;
			});
		}
	}


	private static final class ClientState
	{
		public EntityLocation location;
		
		// The data we think that this client already has.  These are used for determining what they should be told to load/drop as well as filtering updates to what they can apply.
		public final Set<Integer> knownEntities;
		public final Set<CuboidAddress> knownCuboids;
		
		public ClientState(EntityLocation initialLocation)
		{
			this.location = initialLocation;
			
			// Create empty containers for what this client has observed.
			this.knownEntities = new HashSet<>();
			this.knownCuboids = new HashSet<>();
		}
	}


	private final class _TickAdvancer implements Runnable
	{
		// It could take several ticks for a cuboid to be loaded/generated and we don't want to redundantly load them so track what is pending.
		private Set<CuboidAddress> _requestedCuboids = new HashSet<>();
		// We capture the collection of loaded cuboids at each tick so that we can write them back to disk when we shut down.
		public Collection<IReadOnlyCuboidData> completedCuboids = Collections.emptySet();
		public Map<CuboidAddress, List<ScheduledMutation>> scheduledBlockMutations = Collections.emptyMap();
		// Same thing with entities.
		public Collection<Entity> completedEntities = Collections.emptySet();
		
		@Override
		public void run()
		{
			// We schedule this only after receiving a callback that the tick is complete so this should return with the snapshot, immediately, and let the next tick start.
			// Note:  We could just wait here to force all new entities to load in the next tick, but that isn't essential so just unblock it.
			// (any new callbacks will be queued behind this, anyway, which is all that matters).
			TickRunner.Snapshot snapshot = _tickRunner.waitForPreviousTick();
			this.completedCuboids = snapshot.completedCuboids().values();
			this.completedEntities = snapshot.completedEntities().values();
			this.scheduledBlockMutations = snapshot.scheduledBlockMutations();
			
			// Remove any of the cuboids in the snapshot from any that we had requested.
			_requestedCuboids.removeAll(snapshot.completedCuboids().keySet());
			
			// We want to create a set of all the cuboids which are actually required, based on the entities.
			// (we will use this for load/unload decisions)
			Set<CuboidAddress> referencedCuboids = new HashSet<>();
			for (Map.Entry<Integer, ClientState> elt : _connectedClients.entrySet())
			{
				int clientId = elt.getKey();
				ClientState state = elt.getValue();
				
				// Update the location snapshot in the ClientState in case the entity moved.
				Entity entity = snapshot.completedEntities().get(clientId);
				// This may not be here if they just joined.
				if (null != entity)
				{
					state.location = entity.location();
				}
				
				_sendUpdatesToClient(clientId, state, snapshot, referencedCuboids);
			}
			
			// We send the end of tick to a "fake" client 0 so tests can rely on seeing that (real implementations should just ignore it).
			_network.sendEndOfTick(FAKE_CLIENT_ID, snapshot.tickNumber(), 0L);
			
			// Note that we will clear the removed clients BEFORE adding new ones since it is theoretically possible for
			// a client to disconnect and reconnect in the same tick and the logic has this implicit assumption.
			// Create a copy of the removed clients so we can clear the long-lived container.
			// (we removed this from the connected clients, earlier).
			Collection<Integer> removedClients = new ArrayList<>(_removedClients);
			_removedClients.clear();
			
			// Determine what we should unload.
			// -start with the currently loaded cuboids
			Set<CuboidAddress> orphanedCuboids = new HashSet<>(snapshot.completedCuboids().keySet());
			// -remove any which we referenced via entities
			orphanedCuboids.removeAll(referencedCuboids);
			// -remove any which we already know are pending loads
			orphanedCuboids.removeAll(_requestedCuboids);
			
			// Determine what cuboids were referenced which are not yet loaded.
			Set<CuboidAddress> cuboidsToLoad = new HashSet<>(referencedCuboids);
			// -remove those we already requested
			cuboidsToLoad.removeAll(_requestedCuboids);
			// -remove those which are already loaded.
			cuboidsToLoad.removeAll(snapshot.completedCuboids().keySet());
			
			// Request that we save back anything we are unloading before we request that anything new be loaded.
			// (this is most important in the corner-case where the same entity left and rejoined in the same tick)
			if (!orphanedCuboids.isEmpty() || !removedClients.isEmpty())
			{
				Collection<SuspendedCuboid<IReadOnlyCuboidData>> saveCuboids = orphanedCuboids.stream().map((CuboidAddress address) -> {
					IReadOnlyCuboidData cuboid = snapshot.completedCuboids().get(address);
					List<ScheduledMutation> suspended = _tickAdvancer.scheduledBlockMutations.get(address);
					if (null == suspended)
					{
						suspended = List.of();
					}
					return new SuspendedCuboid<IReadOnlyCuboidData>(cuboid, suspended);
				}).toList();
				Collection<Entity> saveEntities = removedClients.stream().map(
						(Integer id) -> snapshot.completedEntities().get(id)
				).toList();
				_loader.writeBackToDisk(saveCuboids, saveEntities);
			}
			
			// Request any missing cuboids or new entities and see what we got back from last time.
			Collection<SuspendedCuboid<CuboidData>> newCuboids = new ArrayList<>();
			Collection<Entity> newEntities = new ArrayList<>();
			_loader.getResultsAndRequestBackgroundLoad(newCuboids, newEntities, cuboidsToLoad, _newClients);
			_requestedCuboids.addAll(cuboidsToLoad);
			// We have requested the clients so drop this, now.
			_newClients.clear();
			
			// Walk through any new clients, adding them to the world.
			for (Entity newEntity : newEntities)
			{
				// Adding this here means that the client will see their entity join the world in a future tick, after they receive the snapshot from the previous tick.
				// This client is now connected and can receive events.
				_connectedClients.put(newEntity.id(), new ClientState(newEntity.location()));
			}
			
			// Push any required data into the TickRunner before we kick-off the tick.
			// We need to run through these to make them the read-only variants for the TickRunner.
			Collection<SuspendedCuboid<IReadOnlyCuboidData>> readOnlyCuboids = newCuboids.stream().map(
					(SuspendedCuboid<CuboidData> readWrite) -> new SuspendedCuboid<IReadOnlyCuboidData>(readWrite.cuboid(), readWrite.mutations())
			).toList();
			_tickRunner.setupChangesForTick(readOnlyCuboids, orphanedCuboids, newEntities, removedClients);
			
			// Determine when the next tick should run (we increment the previous time to not slide).
			_nextTickMillis += _millisPerTick;
			// We will schedule the next tick once we get the completed callback.
			_scheduledAdvancer = null;
			
			// Kick off the next tick since we are now done the setup.
			_tickRunner.startNextTick();
		}
		
		private void _sendUpdatesToClient(int clientId
				, ClientState state
				, TickRunner.Snapshot snapshot
				, Set<CuboidAddress> out_referencedCuboids
		)
		{
			// We want to send the mutations for any of the cuboids and entities which are already loaded.
			_sendEntityUpdates(clientId, state, snapshot);
			
			_sendCuboidUpdates(clientId, state, snapshot, out_referencedCuboids);
			
			// Finally, send them the end of tick.
			// (note that the commit level won't be in the snapshot if they just joined).
			long commitLevel = snapshot.commitLevels().containsKey(clientId)
					? snapshot.commitLevels().get(clientId)
					: 0L
			;
			_network.sendEndOfTick(clientId, snapshot.tickNumber(), commitLevel);
		}
		
		private void _sendEntityUpdates(int clientId, ClientState state, TickRunner.Snapshot snapshot)
		{
			// Add any entities this client hasn't seen.
			for (Map.Entry<Integer, Entity> entry : snapshot.completedEntities().entrySet())
			{
				int entityId = entry.getKey();
				if (state.knownEntities.contains(entityId))
				{
					// We know this entity so send any updated mutations.
					List<IMutationEntity> mutations = snapshot.resultantMutationsById().get(entityId);
					if (null != mutations)
					{
						for (IMutationEntity mutation : mutations)
						{
							_network.sendChange(clientId, entityId, mutation);
						}
					}
				}
				else
				{
					// We don't know this entity so send them.
					_network.sendEntity(clientId, entry.getValue());
					state.knownEntities.add(entityId);
				}
			}
			// Remove any extra entities which have since departed.
			if (state.knownEntities.size() > snapshot.completedEntities().size())
			{
				Iterator<Integer> entityIterator = state.knownEntities.iterator();
				while (entityIterator.hasNext())
				{
					int entityId = entityIterator.next();
					if (!snapshot.completedEntities().containsKey(entityId))
					{
						_network.removeEntity(clientId, entityId);
						entityIterator.remove();
					}
				}
			}
		}
		
		private void _sendCuboidUpdates(int clientId
				, ClientState state
				, TickRunner.Snapshot snapshot
				, Set<CuboidAddress> out_referencedCuboids
		)
		{
			// See if this entity has seen the cuboid where they are standing or the surrounding cuboids.
			// TODO:  This should be optimized around entity movement and cuboid generation, as opposed to this "spinning" approach.
			
			CuboidAddress currentCuboid = state.location.getBlockLocation().getCuboidAddress();
			
			// Walk the existing cuboids and remove any which are out of range.
			Iterator<CuboidAddress> iter = state.knownCuboids.iterator();
			while (iter.hasNext())
			{
				CuboidAddress address = iter.next();
				int xDelta = Math.abs(currentCuboid.x() - address.x());
				int yDelta = Math.abs(currentCuboid.y() - address.y());
				int zDelta = Math.abs(currentCuboid.z() - address.z());
				if ((xDelta > 1) || (yDelta > 1) || (zDelta > 1))
				{
					_network.removeCuboid(clientId, address);
					iter.remove();
				}
			}
			
			// Check the cuboids immediately around the entity and send any which are missing.
			for (int i = -1; i <= 1; ++i)
			{
				for (int j = -1; j <= 1; ++j)
				{
					for (int k = -1; k <= 1; ++k)
					{
						CuboidAddress oneCuboid = new CuboidAddress((short) (currentCuboid.x() + i), (short) (currentCuboid.y() + j), (short) (currentCuboid.z() + k));
						if (state.knownCuboids.contains(oneCuboid))
						{
							// We know about this cuboid so send any updates.
							List<MutationBlockSetBlock> mutations = snapshot.resultantBlockChangesByCuboid().get(oneCuboid);
							if (null != mutations)
							{
								for (MutationBlockSetBlock mutation : mutations)
								{
									_network.sendBlockUpdate(clientId, mutation);
								}
							}
						}
						else
						{
							// We haven't seen this yet so just send it.
							IReadOnlyCuboidData cuboidData = snapshot.completedCuboids().get(oneCuboid);
							// This may not yet be loaded.
							if (null != cuboidData)
							{
								_network.sendCuboid(clientId, cuboidData);
								state.knownCuboids.add(oneCuboid);
							}
							else
							{
								// Not yet loaded - we will either request this based on out_referencedCuboids or already did.
							}
						}
						// Record that we referenced this.
						out_referencedCuboids.add(oneCuboid);
					}
				}
			}
		}
	}
}
