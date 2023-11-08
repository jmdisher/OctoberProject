package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import com.jeffdisher.october.data.Block;
import com.jeffdisher.october.utils.Assert;
import com.jeffdisher.october.utils.Encoding;


public class TickRunner
{
	private final SyncPoint _syncPoint;
	private final Thread[] _threads;
	private WorldState _completedWorld;
	private List<IMutation> _mutations;
	private List<CuboidState> _newCuboids;
	private final WorldState.ProcessedFragment[] _partial;
	private long _lastCompletedTick;
	private long _nextTick;
	// We use an explicit lock to guard shared data, instead of overloading the monitor, since the monitor shouldn't be used purely for data guards.
	private ReentrantLock _sharedDataLock;

	public TickRunner(int threadCount, WorldState.IBlockChangeListener listener)
	{
		AtomicInteger atomic = new AtomicInteger(0);
		_syncPoint = new SyncPoint(threadCount);
		_threads = new Thread[threadCount];
		_partial = new WorldState.ProcessedFragment[threadCount];
		_lastCompletedTick = -1;
		_nextTick = 0;
		_sharedDataLock = new ReentrantLock();
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

	public void start()
	{
		Assert.assertTrue(null == _completedWorld);
		// Create an empty world - the cuboids will be asynchronously loaded.
		_completedWorld = new WorldState(Collections.emptyMap());
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
		_sharedDataLock.lock();
		try
		{
			if (null == _mutations)
			{
				_mutations = new ArrayList<>();
			}
			_mutations.add(mutation);
		}
		finally
		{
			_sharedDataLock.unlock();
		}
	}

	public void cuboidWasLoaded(CuboidState cuboid)
	{
		_sharedDataLock.lock();
		try
		{
			if (null == _newCuboids)
			{
				_newCuboids = new ArrayList<>();
			}
			_newCuboids.add(cuboid);
		}
		finally
		{
			_sharedDataLock.unlock();
		}
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

	/**
	 * Copies out all aspects of the block at the given absolute location, returning null if it is in an unloaded
	 * cuboid.
	 * Note that this isn't synchronized with a tick so the Block will be internally consistent but will be read from
	 * whatever world is "current" at the time of the call.
	 * This call is mostly just added for convenience in tests, etc, as most other uses will make direct low-level calls
	 * or will read from some kind of cached projection.
	 * 
	 * @param absoluteLocation The xyz location of the block.
	 * @return The block copy or null if the location isn't loaded.
	 */
	public Block getBlock(int[] absoluteLocation)
	{
		return _completedWorld.getBlock(absoluteLocation);
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
				
				// Load other cuboids and apply other mutations enqueued since the last tick.
				List<CuboidState> newCuboids;
				List<IMutation> newMutations;
				_sharedDataLock.lock();
				try
				{
					newCuboids = _newCuboids;
					_newCuboids = null;
					newMutations = _mutations;
					_mutations = null;
				}
				finally
				{
					_sharedDataLock.unlock();
				}
				
				// First, add the new cuboids.
				if (null != newCuboids)
				{
					for (CuboidState cuboid : newCuboids)
					{
						long hash = Encoding.encodeCuboidAddress(cuboid.data.getCuboidAddress());
						CuboidState replaced = worldState.put(hash, cuboid);
						// This should NOT already be here.
						Assert.assertTrue(null == replaced);
					}
				}
				
				// Apply the mutations from internal operations.
				for (WorldState.ProcessedFragment fragment : _partial)
				{
					for (IMutation mutation : fragment.exportedMutations())
					{
						_scheduleMutationOnCuboid(worldState, mutation);
					}
				}
				
				// Apply the other mutations which were enqueued since last tick completed and we parked.
				if (null != newMutations)
				{
					for (IMutation mutation : newMutations)
					{
						_scheduleMutationOnCuboid(worldState, mutation);
					}
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
