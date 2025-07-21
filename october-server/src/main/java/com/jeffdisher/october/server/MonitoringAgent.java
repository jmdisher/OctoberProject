package com.jeffdisher.october.server;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

import com.jeffdisher.october.net.NetworkLayer;
import com.jeffdisher.october.net.NetworkServer;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.utils.Assert;


/**
 * Used by ConsoleHandler to read out the state of the system for informational commands.
 * Note that these calls to read or change state can come in from any thread so care must be taken to avoid concurrent
 * modifications or other racy errors.
 */
public class MonitoringAgent
{
	private final Map<Integer, String> _connectedClientIds = new HashMap<>();
	private final Map<Integer, NetworkLayer.PeerToken> _connectedClientTokens = new HashMap<>();
	private volatile NetworkServer<?> _network;
	private volatile TickRunner.Snapshot _lastSnapshot;
	private volatile OperatorCommandSink _commandSink;

	public void setNetwork(NetworkServer<?> network)
	{
		// This should only be called once (and is ALWAYS called before anything else).
		Assert.assertTrue(null == _network);
		_network = network;
	}

	public synchronized void clientConnected(int clientId, NetworkLayer.PeerToken token, String name)
	{
		String old = _connectedClientIds.put(clientId, name);
		Assert.assertTrue(null == old);
		_connectedClientTokens.put(clientId, token);
	}

	public synchronized void clientDisconnected(int clientId)
	{
		String old = _connectedClientIds.remove(clientId);
		Assert.assertTrue(null != old);
		_connectedClientTokens.remove(clientId);
	}

	public void snapshotPublished(TickRunner.Snapshot snapshot)
	{
		_lastSnapshot = snapshot;
	}

	public NetworkServer<?> getNetwork()
	{
		return _network;
	}

	public void setOperatorCommandSink(OperatorCommandSink sink)
	{
		_commandSink = sink;
	}

	public OperatorCommandSink getCommandSink()
	{
		return _commandSink;
	}

	public synchronized Map<Integer, String> getClientsCopy()
	{
		return new HashMap<>(_connectedClientIds);
	}

	public synchronized NetworkLayer.PeerToken getTokenForClient(int clientId)
	{
		return _connectedClientTokens.get(clientId);
	}

	public TickRunner.Snapshot getLastSnapshot()
	{
		return _lastSnapshot;
	}


	public static interface OperatorCommandSink
	{
		void submitEntityMutation(int clientId, IEntityAction<IMutablePlayerEntity> command);
		void requestConfigBroadcast();
		void sendChatMessage(int targetId, String message);
		void installSampler(Sampler sampler);
		void pauseTickProcessing();
		void resumeTickProcessing();
	}

	public static class Sampler
	{
		public static final int TICK_SAMPLE_SIZE = 100;
		
		private long _totalNanosWaiting;
		private long _totalNanosInTasks;
		private long _slowestTask;
		private long _totalTasksRun;
		
		private long _totalMillisInTicks;
		private TickRunner.TickStats _slowestTick;
		private long _slowestTickMillis;
		private int _ticksRemaining = TICK_SAMPLE_SIZE;
		
		public void consumeTaskSample(long nanosWaitingForTask, long nanosInTask)
		{
			_totalNanosWaiting += nanosWaitingForTask;
			_totalNanosInTasks += nanosInTask;
			_slowestTask = Math.max(_slowestTask, nanosInTask);
			_totalTasksRun += 1L;
		}
		public synchronized boolean shouldRetireAfterTickSample(TickRunner.TickStats stats)
		{
			long totalMillis = stats.millisTickPreamble() + stats.millisTickParallelPhase() + stats.millisTickPostamble();
			if (totalMillis > _slowestTickMillis)
			{
				_slowestTickMillis = totalMillis;
				_slowestTick = stats;
			}
			_totalMillisInTicks += totalMillis;
			_ticksRemaining -= 1;
			boolean shouldRetire = (0 == _ticksRemaining);
			if (shouldRetire)
			{
				this.notifyAll();
			}
			return shouldRetire;
		}
		public synchronized void waitForSample() throws InterruptedException
		{
			while (_ticksRemaining > 0)
			{
				this.wait();
			}
		}
		public void logToStream(PrintStream out)
		{
			out.println("--- Sampler results after capturing " + TICK_SAMPLE_SIZE + " ticks:");
			out.println("Ran " + _totalTasksRun + " ServerRunner tasks:");
			out.println("\tNanos running: " + _totalNanosInTasks);
			out.println("\tNanos waiting: " + _totalNanosWaiting);
			out.println("\tNanos slowest: " + _slowestTask);
			out.println("\tNanos average: " + (_totalNanosInTasks / _totalTasksRun));
			out.println("Spent " + _totalMillisInTicks + " ms in ticks, slowest taking " + _slowestTickMillis + " ms:");
			_slowestTick.writeToStream(out);
			out.println("--- End report");
		}
	}
}
