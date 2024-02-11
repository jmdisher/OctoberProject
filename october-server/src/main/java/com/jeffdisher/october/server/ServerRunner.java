package com.jeffdisher.october.server;

import java.util.Collection;
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
import com.jeffdisher.october.logic.EntityActionValidator;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.persistence.CuboidLoader;
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
	public static final int TICK_RUNNER_THREAD_COUNT = 1;
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
	private final CuboidLoader _loader;
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

	public ServerRunner(long millisPerTick
			, IServerAdapter network
			, CuboidLoader loader
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
		// Shut down the tick runner.
		_tickRunner.shutdown();
		// We can now join on the background thread since it has nothing else to block on.
		try
		{
			_background.join();
		}
		catch (InterruptedException e)
		{
			// We don't use interruption.
			throw Assert.unexpected(e);
		}
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
			});
		}
		@Override
		public void changeReceived(int clientId, IMutationEntity change, long commitLevel)
		{
			_messages.enqueue(() -> {
				// This doesn't need to enter the TickRunner at any particular time so we can add it here and it will be rolled into the next tick.
				_tickRunner.enqueueEntityChange(clientId, change, commitLevel);
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
		
		@Override
		public void run()
		{
			// We schedule this only after receiving a callback that the tick is complete so this should return with the snapshot, immediately, and let the next tick start.
			// Note:  We could just wait here to force all new entities to load in the next tick, but that isn't essential so just unblock it.
			// (any new callbacks will be queued behind this, anyway, which is all that matters).
			TickRunner.Snapshot snapshot = _tickRunner.startNextTick();
			
			// Remove any of the cuboids in the snapshot from any that we had requested.
			_requestedCuboids.removeAll(snapshot.completedCuboids().keySet());
			
			// Here, all we are interested in doing is incrementally loading more of the world and crowd for connected clients not completely enlightened.
			Set<CuboidAddress> cuboidsToLoad = new HashSet<>();
			Consumer<CuboidAddress> cuboidRequester = (CuboidAddress address) -> {
				// If this is something we already requested, it just means it hasn't yet arrived or been loaded into a snapshot so don't request it again.
				boolean didAdd = _requestedCuboids.add(address);
				if (didAdd)
				{
					cuboidsToLoad.add(address);
				}
			};
			for (Map.Entry<Integer, ClientState> elt : _connectedClients.entrySet())
			{
				// In the future, this is where we will add cuboids from a radius around the entity and drop whatever they move away from.
				// For now, we just tell them about all the entities and the single cuboid where they are.
				int clientId = elt.getKey();
				ClientState state = elt.getValue();
				
				_sendUpdatesToClient(clientId, state, snapshot, cuboidRequester);
			}
			
			// We send the end of tick to a "fake" client 0 so tests can rely on seeing that (real implementations should just ignore it).
			_network.sendEndOfTick(FAKE_CLIENT_ID, snapshot.tickNumber(), 0L);
			
			// Request any missing cuboids and see what we got back from last time.
			Collection<CuboidData> newCuboids = _loader.getResultsAndIssueRequest(cuboidsToLoad);
			if (!newCuboids.isEmpty())
			{
				_tickRunner.cuboidsWereLoaded(newCuboids);
			}
			
			// Walk through any new clients, adding them to the world.
			while (!_newClients.isEmpty())
			{
				int clientId = _newClients.remove();
				Entity initial = EntityActionValidator.buildDefaultEntity(clientId);
				// Adding this here means that the client will see their entity join the world in a future tick, after they receive the snapshot from the previous tick.
				_tickRunner.entityDidJoin(initial);
				
				// This client is now connected and can receive events.
				_connectedClients.put(clientId, new ClientState(initial.location()));
			}
			
			// Walk through any removed clients, removing them from the world.
			while (!_removedClients.isEmpty())
			{
				int clientId = _removedClients.remove();
				_tickRunner.entityDidLeave(clientId);
				// (we removed this from the connected clients, earlier).
			}
			
			// Determine when the next tick should run (we increment the previous time to not slide).
			_nextTickMillis += _millisPerTick;
			// We will schedule the next tick once we get the completed callback.
			_scheduledAdvancer = null;
		}
		
		private void _sendUpdatesToClient(int clientId
				, ClientState state
				, TickRunner.Snapshot snapshot
				, Consumer<CuboidAddress> cuboidRequester
		)
		{
			// We want to send the mutations for any of the cuboids and entities which are already loaded.
			_sendEntityUpdates(clientId, state, snapshot);
			
			_sendCuboidUpdates(clientId, state, snapshot, cuboidRequester);
			
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
				, Consumer<CuboidAddress> cuboidRequester
		)
		{
			// See if this entity has seen the cuboid where they are standing or the surrounding cuboids.
			// TODO:  This should be optimized around entity movement and cuboid generation, as opposed to this "spinning" approach.
			
			CuboidAddress currentCuboid = state.location.getBlockLocation().getCuboidAddress();
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
							List<IMutationBlock> mutations = snapshot.resultantMutationsByCuboid().get(oneCuboid);
							if (null != mutations)
							{
								for (IMutationBlock mutation : mutations)
								{
									_network.sendMutation(clientId, mutation);
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
								// Request this from the loader.
								cuboidRequester.accept(oneCuboid);
							}
						}
					}
				}
			}
		}
	}
}
