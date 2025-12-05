package com.jeffdisher.october.server;

import java.util.Collection;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import com.jeffdisher.october.actions.EntityActionSimpleMove;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
import com.jeffdisher.october.net.EntityUpdatePerField;
import com.jeffdisher.october.net.PartialEntityUpdate;
import com.jeffdisher.october.net.NetworkLayer;
import com.jeffdisher.october.net.PacketFromClient;
import com.jeffdisher.october.persistence.PackagedCuboid;
import com.jeffdisher.october.persistence.ResourceLoader;
import com.jeffdisher.october.persistence.SuspendedCuboid;
import com.jeffdisher.october.persistence.SuspendedEntity;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.types.PartialPassive;
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
	 * 50 ms/tick is 20 ticks/sec.
	 */
	public static final long DEFAULT_MILLIS_PER_TICK = 50L;

	// General and configuration variables.
	private final long _millisPerTick;
	private final int _clientViewDistanceMaximum;
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
		_clientViewDistanceMaximum = config.clientViewDistanceMaximum;
		NetworkListener networkListener = new NetworkListener();
		network.readyAndStartListening(networkListener);
		_network = network;
		_loader = loader;
		Random random = new Random();
		TickListener tickListener = new TickListener();
		_tickRunner = new TickRunner(TICK_RUNNER_THREAD_COUNT
				, _millisPerTick
				, loader.creatureIdAssigner
				, loader.passiveIdAssigner
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
		
		_tickAdvancer = new _TickAdvancer(config);
		// Note:  We don't allow the view distance to change after start-up so capture it here to make that clear.
		_stateManager = new ServerStateManager(new _Callouts(), _millisPerTick);
		
		// We want to prime the state manager's thread check.
		_messages.enqueue(() -> {
			_stateManager.setOwningThread();
		});
		
		// Register our various attachments into the monitoring agent.
		_monitoringAgent.setOperatorCommandSink(new MonitoringAgent.OperatorCommandSink()
		{
			@Override
			public void submitEntityMutation(int clientId, IEntityAction<IMutablePlayerEntity> command)
			{
				_tickRunner.enqueueOperatorMutation(clientId, command);
			}
			@Override
			public void requestConfigBroadcast()
			{
				_messages.enqueue(() -> {
					_stateManager.broadcastConfig(config);
				});
			}
			@Override
			public void sendChatMessage(int targetId, String message)
			{
				_messages.enqueue(() -> {
					_stateManager.sendConsoleMessage(targetId, message);
				});
			}
			@Override
			public void installSampler(MonitoringAgent.Sampler sampler)
			{
				_messages.enqueue(() -> {
					Assert.assertTrue(null == _currentSampler);
					_currentSampler = sampler;
				});
			}
			@Override
			public void pauseTickProcessing()
			{
				_tickAdvancer.pause();
			}
			@Override
			public void resumeTickProcessing()
			{
				_tickAdvancer.resume();
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
		// We first need to resume execution of the background thread if waiting in the tick advancer.
		_tickAdvancer.resume();
		
		// Shut down the state manager in its own thread.
		CountDownLatch latch = new CountDownLatch(1);
		_messages.enqueue(() -> {
			// Allow the state manager to flush anything it has stored.
			_stateManager.shutdown();
			latch.countDown();
		});
		try
		{
			latch.await();
		}
		catch (InterruptedException e1)
		{
			// We don't use interruption.
			throw Assert.unexpected(e1);
		}
		
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
		public void clientConnected(int clientId, NetworkLayer.PeerToken token, String name, int cuboidViewDistance)
		{
			Assert.assertTrue(cuboidViewDistance >= 0);
			_messages.enqueue(() -> {
				int viewDistance = Math.min(cuboidViewDistance, _clientViewDistanceMaximum);
				_stateManager.clientConnected(clientId, name, viewDistance);
				
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
		// We expect that this class is a singleton so we will record relevant world state (config) changes which might require a notification to the clients.
		private final WorldConfig _sharedConfig;
		private int _previousDayStartTick;
		private boolean _isPaused;
		
		public _TickAdvancer(WorldConfig config)
		{
			_sharedConfig = config;
			_previousDayStartTick = config.dayStartTick;
		}
		
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
			
			// Before we schedule the next tick and start it processing, see if we should be running.
			boolean shouldResetSchedule = false;
			synchronized (this)
			{
				shouldResetSchedule = _isPaused;
				while (_isPaused)
				{
					try
					{
						this.wait();
					}
					catch (InterruptedException e)
					{
						// We don't use interruption.
						throw Assert.unexpected(e);
					}
				}
			}
			
			// Determine when the next tick should run (we increment the previous time to not slide).
			if (shouldResetSchedule)
			{
				// Act as though this was set for "now"
				_nextTickMillis = _currentTimeMillisProvider.getAsLong();
			}
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
			
			// See if there was a change to the config (set by EntityChangeSetDayAndSpawn).
			// TODO:  Generalize this kind of callout into the TickRunner if it becomes more common or varied.
			if (_previousDayStartTick != _sharedConfig.dayStartTick)
			{
				_stateManager.broadcastConfig(_sharedConfig);
				_previousDayStartTick = _sharedConfig.dayStartTick;
			}
		}
		
		/**
		 * Pauses the tick requests by blocking the background thread.
		 * NOTE:  The background thread is blocked so that we also stop processing network packets, etc.
		 */
		public synchronized void pause()
		{
			_isPaused = true;
		}
		
		public synchronized void resume()
		{
			_isPaused = false;
			this.notifyAll();
		}
	}

	private final class _Callouts implements ServerStateManager.ICallouts
	{
		@Override
		public void resources_writeToDisk(Collection<PackagedCuboid> cuboids,Collection<SuspendedEntity> entities, long gameTimeMillis)
		{
			_loader.writeBackToDisk(cuboids, entities, gameTimeMillis);
		}
		@Override
		public void resources_tryWriteToDisk(Collection<PackagedCuboid> cuboids, Collection<SuspendedEntity> entities, long gameTimeMillis)
		{
			_loader.tryWriteBackToDisk(cuboids, entities, gameTimeMillis);
		}
		@Override
		public void resources_getAndRequestBackgroundLoad(Collection<SuspendedCuboid<CuboidData>> out_loadedCuboids
				, Collection<SuspendedEntity> out_loadedEntities
				, Collection<CuboidAddress> requestedCuboids
				, Collection<Integer> requestedEntityIds
				, long currentGameMillis
		)
		{
			_loader.getResultsAndRequestBackgroundLoad(out_loadedCuboids
				, out_loadedEntities
				, requestedCuboids
				, requestedEntityIds
				, currentGameMillis
			);
		}
		@Override
		public boolean network_isNetworkWriteReady(int clientId)
		{
			return _network.isNetworkWriteReady(clientId);
		}
		@Override
		public PacketFromClient network_peekOrRemoveNextPacketFromClient(int clientId, PacketFromClient toRemove)
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
		public void network_sendPartialPassive(int clientId, PartialPassive partial)
		{
			_network.sendPartialPassive(clientId, partial);
		}
		@Override
		public void network_sendPartialPassiveUpdate(int clientId, int entityId, EntityLocation location, EntityLocation velocity)
		{
			_network.sendPartialPassiveUpdate(clientId, entityId, location, velocity);
		}
		@Override
		public void network_removePassive(int clientId, int entityId)
		{
			_network.removePassive(clientId, entityId);
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
		public void network_sendEntityUpdate(int clientId, int entityId, EntityUpdatePerField update)
		{
			_network.sendEntityUpdate(clientId, entityId, update);
		}
		@Override
		public void network_sendPartialEntityUpdate(int clientId, int entityId, PartialEntityUpdate update)
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
		public void network_sendBlockEvent(int clientId, EventRecord.Type type, AbsoluteLocation location, int entitySource)
		{
			_network.sendBlockEvent(clientId, type, location, entitySource);
		}
		@Override
		public void network_sendEntityEvent(int clientId, EventRecord.Type type, EventRecord.Cause cause, AbsoluteLocation optionalLocation, int entityTarget, int entitySource)
		{
			_network.sendEntityEvent(clientId, type, cause, optionalLocation, entityTarget, entitySource);
		}
		@Override
		public boolean runner_enqueueEntityChange(int entityId, EntityActionSimpleMove<IMutablePlayerEntity> change, long commitLevel)
		{
			return _tickRunner.enqueueEntityChange(entityId, change, commitLevel);
		}
		@Override
		public void handleClientUpdateOptions(int clientId, int clientViewDistance)
		{
			Assert.assertTrue(clientViewDistance >= 0);
			_messages.enqueue(() -> {
				int viewDistance = Math.min(clientViewDistance, _clientViewDistanceMaximum);
				_stateManager.setClientViewDistance(clientId, viewDistance);
			});
		}
	}
}
