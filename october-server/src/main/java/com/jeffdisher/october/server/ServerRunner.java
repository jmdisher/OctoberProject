package com.jeffdisher.october.server;

import java.util.Collection;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.mutations.IEntityUpdate;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.mutations.IPartialEntityUpdate;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
import com.jeffdisher.october.net.NetworkLayer;
import com.jeffdisher.october.net.Packet;
import com.jeffdisher.october.persistence.PackagedCuboid;
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
	private final MonitoringAgent _monitoringAgent;
	private long _nextTickMillis;
	private final _TickAdvancer _tickAdvancer;
	// When we are due to start the next tick (after we receive the callback that the previous is done), we schedule the advancer.
	private _TickAdvancer _scheduledAdvancer;
	private final ServerStateManager _stateManager;
	private MonitoringAgent.Sampler _currentSampler;

	// Note that the runner takes ownership of CuboidLoader and will shut it down.
	public ServerRunner(long millisPerTick
			, IServerAdapter network
			, ResourceLoader loader
			, LongSupplier currentTimeMillisProvider
			, MonitoringAgent monitoringAgent
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
		}, "ServerRunner");
		_currentTimeMillisProvider = currentTimeMillisProvider;
		_monitoringAgent = monitoringAgent;
		
		_tickAdvancer = new _TickAdvancer();
		_stateManager = new ServerStateManager(new _Callouts());
		
		// Register our various attachments into the monitoring agent.
		_monitoringAgent.setOperatorCommandSink(new MonitoringAgent.OperatorCommandSink()
		{
			@Override
			public void submitEntityMutation(int clientId, IMutationEntity<IMutablePlayerEntity> command)
			{
				_tickRunner.enqueueOperatorMutation(clientId, command);
			}
			@Override
			public void requestConfigBroadcast()
			{
				_stateManager.broadcastConfig(config);
			}
			@Override
			public void sendChatMessage(int targetId, String message)
			{
				_stateManager.sendConsoleMessage(targetId, message);
			}
			@Override
			public void installSampler(MonitoringAgent.Sampler sampler)
			{
				_messages.enqueue(() -> {
					Assert.assertTrue(null == _currentSampler);
					_currentSampler = sampler;
				});
			}
		});
		
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
		_nextTickMillis = _currentTimeMillisProvider.getAsLong() + _millisPerTick;
		Runnable next = _messages.pollForNext(_millisPerTick, _scheduledAdvancer);
		long nanosAfterRun = 0L;
		while (null != next)
		{
			nanosAfterRun = _runAndSample(next, nanosAfterRun);
			// If we are ready to schedule the tick advancer, find out when.
			long millisToWait;
			if (null != _scheduledAdvancer)
			{
				long currentTime = _currentTimeMillisProvider.getAsLong();
				millisToWait = _nextTickMillis - currentTime;
				if (millisToWait > 0)
				{
					// This is the normal behaviour - we have a tick to schedule (the TickTunner is idle) but we don't want to run it yet.
				}
				else
				{
					if (millisToWait < -_millisPerTick)
					{
						// We only want to log that we are dropping a tick if we are more than 1 tick behind since ticks are never scheduled ahead-of-time.
						System.out.println("WARNING:  Dropping tick!");
						_nextTickMillis += _millisPerTick;
					}
					// In any case, we are ready to run this so do it now, not even going through the message queue.
					nanosAfterRun = _runAndSample(_scheduledAdvancer, nanosAfterRun);
					// This should have cleared the instance variable.
					Assert.assertTrue(null == _scheduledAdvancer);
					// Since we ran the action, just go back to waiting without limit.
					millisToWait = 0L;
				}
			}
			else
			{
				millisToWait = 0L;
			}
			next = _messages.pollForNext(millisToWait, _scheduledAdvancer);
		}
	}

	private long _runAndSample(Runnable next, long nanosAfterRun)
	{
		long nanosBeforeRun = System.nanoTime();
		next.run();
		long nanosWaitingForTask = (nanosBeforeRun - nanosAfterRun);
		nanosAfterRun = System.nanoTime();
		if (null != _currentSampler)
		{
			long nanosRunningTask = (nanosAfterRun - nanosBeforeRun);
			_currentSampler.consumeTaskSample(nanosWaitingForTask, nanosRunningTask);
		}
		return nanosAfterRun;
	}


	private class NetworkListener implements IServerAdapter.IListener
	{
		@Override
		public void clientConnected(int clientId, NetworkLayer.PeerToken token, String name)
		{
			_messages.enqueue(() -> {
				_stateManager.clientConnected(clientId, name);
				
				_monitoringAgent.clientConnected(clientId, token, name);
			});
		}
		@Override
		public void clientDisconnected(int clientId)
		{
			_messages.enqueue(() -> {
				_stateManager.clientDisconnected(clientId);
				// This message is now in-order so we can ack the disconnect.
				_network.acknowledgeDisconnect(clientId);
				
				_monitoringAgent.clientDisconnected(clientId);
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
				
				_monitoringAgent.snapshotPublished(completedSnapshot);
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
			TickRunner.TickStats stats = snapshot.stats();
			long preamble = stats.millisTickPreamble();
			long parallel = stats.millisTickParallelPhase();
			long postamble = stats.millisTickPostamble();
			long tickTime = preamble + parallel + postamble;
			if (tickTime > _millisPerTick)
			{
				stats.writeToStream(System.out);
			}
			if (null != _currentSampler)
			{
				boolean shouldRetire = _currentSampler.shouldRetireAfterTickSample(stats);
				if (shouldRetire)
				{
					_currentSampler = null;
				}
			}
		}
	}

	private final class _Callouts implements ServerStateManager.ICallouts
	{
		@Override
		public void resources_writeToDisk(Collection<PackagedCuboid> cuboids,Collection<SuspendedEntity> entities)
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
		public Packet network_peekOrRemoveNextPacketFromClient(int clientId, Packet toRemove)
		{
			return _network.peekOrRemoveNextPacketFromClient(clientId, toRemove);
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
		public void network_sendConfig(int clientId, WorldConfig config)
		{
			_network.sendConfig(clientId, config);
		}
		@Override
		public void network_sendClientJoined(int clientId, int joinedClientId, String name)
		{
			_network.sendClientJoined(clientId, joinedClientId, name);
		}
		@Override
		public void network_sendClientLeft(int clientId, int leftClientId)
		{
			_network.sendClientLeft(clientId, leftClientId);
		}
		@Override
		public void network_sendChatMessage(int clientId, int senderId, String message)
		{
			_network.sendChatMessage(clientId, senderId, message);
		}
		@Override
		public boolean runner_enqueueEntityChange(int entityId, IMutationEntity<IMutablePlayerEntity> change, long commitLevel)
		{
			return _tickRunner.enqueueEntityChange(entityId, change, commitLevel);
		}
	}
}
