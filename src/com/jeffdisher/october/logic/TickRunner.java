package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.jeffdisher.october.utils.Assert;
import com.jeffdisher.october.utils.Encoding;


public class TickRunner
{
	private final SyncPoint _syncPoint;
	private final Thread[] _threads;
	private WorldState _completedWorld;
	private List<IMutation> _mutations;
	private final WorldState.ProcessedFragment[] _partial;
	private long _lastCompletedTick;
	private long _nextTick;

	public TickRunner(int threadCount, WorldState.IBlockChangeListener listener)
	{
		AtomicInteger atomic = new AtomicInteger(0);
		_syncPoint = new SyncPoint(threadCount);
		_threads = new Thread[threadCount];
		_partial = new WorldState.ProcessedFragment[threadCount];
		_lastCompletedTick = -1;
		_nextTick = 0;
		for (int i = 0; i < threadCount; ++i)
		{
			int id = i;
			_threads[i] = new Thread(() -> {
				ProcessorElement thisThread = new ProcessorElement(id, _syncPoint, atomic);
				WorldState world = _mergeTickStateAndWaitForNext(thisThread, null);
				while (null != world)
				{
					// Run the tick.
					WorldState.ProcessedFragment fragment = world.buildNewWorldParallel(thisThread, listener);
					world = _mergeTickStateAndWaitForNext(thisThread, fragment);
				}
			}, "Tick Runner #" + i);
		}
	}

	public void start(WorldState startWorld)
	{
		Assert.assertTrue(null == _completedWorld);
		_completedWorld = startWorld;
		for (Thread thread : _threads)
		{
			thread.start();
		}
	}

	// This will wait for the previous tick to complete before it kicks off the next one but it will return before that tick completes.
	public synchronized void runTick()
	{
		// Wait for the previous tick to complete.
		_locked_waitForTickComplete();
		// Advance to the next tick.
		_nextTick += 1;
		this.notifyAll();
	}

	public void enqueueMutation(IMutation mutation)
	{
		if (null == _mutations)
		{
			_mutations = new ArrayList<>();
		}
		_mutations.add(mutation);
	}

	public void shutdown()
	{
		// Notify them to shut down.
		synchronized (this)
		{
			// Wait until the previous tick is done.
			_locked_waitForTickComplete();
			// If the next tick is a negative number, the system will exit.
			_nextTick = -1;
			this.notifyAll();
		}
		
		// Now, join on everyone.
		for (Thread thread : _threads)
		{
			try
			{
				thread.join();
			}
			catch (InterruptedException e)
			{
				// We don't use interruption.
				throw Assert.unexpected(e);
			}
		}
	}


	private WorldState _mergeTickStateAndWaitForNext(ProcessorElement elt, WorldState.ProcessedFragment fragmentCompleted)
	{
		// Store whatever work we finished from the just-completed tick.
		_partial[elt.id] = fragmentCompleted;
		if (elt.synchronizeAndReleaseLast())
		{
			// All the data is stored so process it and wait for a request to run the next tick before releasing everyone.
			if (null != fragmentCompleted)
			{
				Map<Long, CuboidState> worldState = new HashMap<>();
				for (WorldState.ProcessedFragment fragment : _partial)
				{
					worldState.putAll(fragment.stateFragment());
				}
				// Now, apply any mutations generated in the now-completed tick (which weren't local mutations).
				for (WorldState.ProcessedFragment fragment : _partial)
				{
					for (IMutation mutation : fragment.exportedMutations())
					{
						_scheduleMutationOnCuboid(worldState, mutation);
					}
				}
				
				// Apply the other mutations which were enqueued since last tick completed and we parked.
				if (null != _mutations)
				{
					for (IMutation mutation : _mutations)
					{
						_scheduleMutationOnCuboid(worldState, mutation);
					}
					_mutations = null;
				}
				
				// Replace the world with the new state.
				_completedWorld = new WorldState(worldState);
			}
			
			// Reset internal counters.
			for (int i = 0; i < _partial.length; ++i)
			{
				_partial[i] = null;
			}
			
			_acknowledgeTickCompleteAndWaitForNext();
			
			// Now, we can release everyone and they will read _nextTick to see if we are still running.
			elt.releaseWaitingThreads();
		}
		
		// If the next tick was set negative, it means exit.
		WorldState worldToRun = null;
		if (_nextTick > 0)
		{
			worldToRun = _completedWorld;
		}
		return worldToRun;
	}

	private synchronized void _acknowledgeTickCompleteAndWaitForNext()
	{
		// Acknowledge that the tick is completed.
		_lastCompletedTick = _nextTick;
		this.notifyAll();
		while (_lastCompletedTick == _nextTick)
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

	private void _scheduleMutationOnCuboid(Map<Long, CuboidState> worldState, IMutation mutation)
	{
		short[] address = Encoding.getCombinedCuboidAddress(mutation.getAbsoluteLocation());
		long hash = Encoding.encodeCuboidAddress(address);
		CuboidState cuboid = worldState.get(hash);
		if (null != cuboid)
		{
			cuboid.enqueueMutation(mutation);
		}
		else
		{
			System.err.println("WARNING:  Mutation dropped due to missing cuboid");
		}
	}

	private void _locked_waitForTickComplete()
	{
		while (_lastCompletedTick != _nextTick)
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
}
