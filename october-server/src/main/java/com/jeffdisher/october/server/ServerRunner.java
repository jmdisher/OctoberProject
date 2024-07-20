package com.jeffdisher.october.server;

import java.util.Collection;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.ProcessorElement;
import com.jeffdisher.october.mutations.IEntityUpdate;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.mutations.IPartialEntityUpdate;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
import com.jeffdisher.october.net.Packet_MutationEntityFromClient;
import com.jeffdisher.october.persistence.ResourceLoader;
import com.jeffdisher.october.persistence.SuspendedCuboid;
import com.jeffdisher.october.persistence.SuspendedEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.types.WorldConfig;
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
	private final _TickAdvancer _tickAdvancer;
	// When we are due to start the next tick (after we receive the callback that the previous is done), we schedule the advancer.
	private _TickAdvancer _scheduledAdvancer;
	private final ServerStateManager _stateManager;

	// Note that the runner takes ownership of CuboidLoader and will shut it down.
	public ServerRunner(long millisPerTick
			, IServerAdapter network
			, ResourceLoader loader
			, LongSupplier currentTimeMillisProvider
			, WorldConfig config
	)
	{
		_millisPerTick = millisPerTick;
		NetworkListener networkListener = new NetworkListener();
		network.readyAndStartListening(networkListener);
		_network = network;
		_loader = loader;
		Random random = new Random();
		TickListener tickListener = new TickListener();
		_tickRunner = new TickRunner(TICK_RUNNER_THREAD_COUNT
				, _millisPerTick
				, loader.creatureIdAssigner
				, (int bound) -> random.nextInt(bound)
				, tickListener
				, config
		);
		
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
		_tickAdvancer = new _TickAdvancer();
		_stateManager = new ServerStateManager(new _Callouts());
		
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
		
		// Allow the state manager to flush anything it has stored.
		_stateManager.shutdown();
		
		// Shut down the cuboid loader.
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
				_stateManager.clientConnected(clientId);
			});
		}
		@Override
		public void clientDisconnected(int clientId)
		{
			_messages.enqueue(() -> {
				_stateManager.clientDisconnected(clientId);
				// This message is now in-order so we can ack the disconnect.
				_network.acknowledgeDisconnect(clientId);
			});
		}
		@Override
		public void clientReadReady(int clientId)
		{
			_messages.enqueue(() -> {
				_stateManager.clientReadReady(clientId);
			});
		}
	}

	private class TickListener implements Consumer<TickRunner.Snapshot>
	{
		@Override
		public void accept(TickRunner.Snapshot completedSnapshot)
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


	private final class _TickAdvancer implements Runnable
	{
		@Override
		public void run()
		{
			// We schedule this only after receiving a callback that the tick is complete so this should return with the snapshot, immediately, and let the next tick start.
			TickRunner.Snapshot snapshot = _tickRunner.waitForPreviousTick();
			ServerStateManager.TickChanges nextTickChanges = _stateManager.setupNextTickAfterCompletion(snapshot);
			
			_tickRunner.setupChangesForTick(nextTickChanges.newCuboids()
					, nextTickChanges.cuboidsToUnload()
					, nextTickChanges.newEntities()
					, nextTickChanges.entitiesToUnload()
			);
			
			// We send the end of tick to a "fake" client 0 so tests can rely on seeing that (real implementations should just ignore it).
			_network.sendEndOfTick(FAKE_CLIENT_ID, snapshot.tickNumber(), 0L);
			
			// Determine when the next tick should run (we increment the previous time to not slide).
			_nextTickMillis += _millisPerTick;
			// We will schedule the next tick once we get the completed callback.
			_scheduledAdvancer = null;
			
			// Kick off the next tick since we are now done the setup.
			_tickRunner.startNextTick();
			
			// Check if this needs to be logged.
			long preamble = snapshot.millisTickPreamble();
			long parallel = snapshot.millisTickParallelPhase();
			long postamble = snapshot.millisTickPostamble();
			long tickTime = preamble + parallel + postamble;
			if (tickTime > _millisPerTick)
			{
				System.out.println("Log for slow (" + tickTime + " ms) tick " + snapshot.tickNumber());
				System.out.println("\tPreamble: " + preamble + " ms");
				System.out.println("\tParallel: " + parallel + " ms");
				for (ProcessorElement.PerThreadStats thread : snapshot.threadStats())
				{
					System.out.println("\t-Crowd: " + thread.millisInCrowdProcessor() + " ms, Creatures: " + thread.millisInCreatureProcessor() + " ms, World: " + thread.millisInWorldProcessor() + " ms");
					System.out.println("\t\tEntities processed: " + thread.entitiesProcessed() + ", changes processed " + thread.entityChangesProcessed());
					System.out.println("\t\tCreatures processed: " + thread.creaturesProcessed() + ", changes processed " + thread.creatureChangesProcessed());
					System.out.println("\t\tCuboids processed: " + thread.cuboidsProcessed() + ", mutations processed " + thread.cuboidMutationsProcessed() + ", updates processed " + thread.cuboidBlockupdatesProcessed());
				}
				System.out.println("\tPostamble: " + postamble + " ms");
			}
		}
	}

	private final class _Callouts implements ServerStateManager.ICallouts
	{
		@Override
		public void resources_writeToDisk(Collection<SuspendedCuboid<IReadOnlyCuboidData>> cuboids,Collection<SuspendedEntity> entities)
		{
			_loader.writeBackToDisk(cuboids, entities);
		}
		@Override
		public void resources_getAndRequestBackgroundLoad(Collection<SuspendedCuboid<CuboidData>> out_loadedCuboids
				, Collection<SuspendedEntity> out_loadedEntities
				, Collection<CuboidAddress> requestedCuboids
				, Collection<Integer> requestedEntityIds
		)
		{
			_loader.getResultsAndRequestBackgroundLoad(out_loadedCuboids, out_loadedEntities, requestedCuboids, requestedEntityIds);
		}
		@Override
		public Packet_MutationEntityFromClient network_peekOrRemoveNextMutationFromClient(int clientId, Packet_MutationEntityFromClient toRemove)
		{
			return _network.peekOrRemoveNextMutationFromClient(clientId, toRemove);
		}
		@Override
		public void network_sendFullEntity(int clientId, Entity entity)
		{
			_network.sendFullEntity(clientId, entity);
		}
		@Override
		public void network_sendPartialEntity(int clientId, PartialEntity entity)
		{
			_network.sendPartialEntity(clientId, entity);
		}
		@Override
		public void network_removeEntity(int clientId, int entityId)
		{
			_network.removeEntity(clientId, entityId);
		}
		@Override
		public void network_sendCuboid(int clientId, IReadOnlyCuboidData cuboid)
		{
			_network.sendCuboid(clientId, cuboid);
		}
		@Override
		public void network_removeCuboid(int clientId, CuboidAddress address)
		{
			_network.removeCuboid(clientId, address);
		}
		@Override
		public void network_sendEntityUpdate(int clientId, int entityId, IEntityUpdate update)
		{
			_network.sendEntityUpdate(clientId, entityId, update);
		}
		@Override
		public void network_sendPartialEntityUpdate(int clientId, int entityId, IPartialEntityUpdate update)
		{
			_network.sendPartialEntityUpdate(clientId, entityId, update);
		}
		@Override
		public void network_sendBlockUpdate(int clientId, MutationBlockSetBlock update)
		{
			_network.sendBlockUpdate(clientId, update);
		}
		@Override
		public void network_sendEndOfTick(int clientId, long tickNumber, long latestLocalCommitIncluded)
		{
			_network.sendEndOfTick(clientId, tickNumber, latestLocalCommitIncluded);
		}
		@Override
		public boolean runner_enqueueEntityChange(int entityId, IMutationEntity<IMutablePlayerEntity> change, long commitLevel)
		{
			return _tickRunner.enqueueEntityChange(entityId, change, commitLevel);
		}
	}
}
