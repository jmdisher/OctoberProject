package com.jeffdisher.october.logic;

import java.util.concurrent.atomic.AtomicInteger;


public class ProcessorElement
{
	public final int id;
	public int mutationCount;
	public long lastCompletedTick;
	private final SyncPoint _sync;
	private final AtomicInteger _sharedUnitCounter;
	private int _lastWorkUnit;
	// The next unit is the same as last unit if we don't know our next unit.
	private int _nextWorkUnit;

	public ProcessorElement(int id, SyncPoint sync, AtomicInteger sharedUnitCounter)
	{
		this.id = id;
		_sync = sync;
		_sharedUnitCounter = sharedUnitCounter;
		// We will initialize the last work unit to the one before the next.
		_lastWorkUnit = _sharedUnitCounter.get() - 1;
		_nextWorkUnit = _lastWorkUnit;
	}

	public void synchronizeThreads()
	{
		_sync.synchronizeThreads();
	}

	public boolean synchronizeAndReleaseLast()
	{
		return _sync.synchronizeAndReleaseLast();
	}

	public void releaseWaitingThreads()
	{
		_sync.releaseWaitingThreads();
	}

	public boolean handleNextWorkUnit()
	{
		if (_nextWorkUnit == _lastWorkUnit)
		{
			// We don't know the next work unit so go get the next one.
			_nextWorkUnit = _sharedUnitCounter.getAndIncrement();
		}
		// Now, increment the last counter, returning true if it matches the next.
		_lastWorkUnit += 1;
		return (_nextWorkUnit == _lastWorkUnit);
	}

	public void resetCounters(int i)
	{
		_lastWorkUnit = i;
		_nextWorkUnit = i;
	}
}
