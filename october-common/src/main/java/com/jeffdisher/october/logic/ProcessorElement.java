package com.jeffdisher.october.logic;

import java.util.concurrent.atomic.AtomicInteger;


public class ProcessorElement
{
	public final int id;

	// Internal variable related to parallel executor synchronization.
	private final SyncPoint _sync;
	private final AtomicInteger _sharedUnitCounter;
	private int _lastWorkUnit;
	// The next unit is the same as last unit if we don't know our next unit.
	private int _nextWorkUnit;

	// Public variables related to per-thread tick execution statistics.
	public int entitiesProcessed;
	public int entityChangesProcessed;
	public long millisInCrowdProcessor;
	
	public int creaturesProcessed;
	public int creatureChangesProcessed;
	public long millisInCreatureProcessor;
	
	public int cuboidsProcessed;
	public int cuboidMutationsProcessed;
	public int cuboidBlockupdatesProcessed;
	public long millisInWorldProcessor;

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
		boolean isLastThread = _sync.synchronizeAndReleaseLast();
		if (isLastThread)
		{
			// Everyone has synchronized so we can reset the atomic here.
			_sharedUnitCounter.set(0);
		}
		// Every thread breaks out of this when fully synchronized so we can reset the local counter.
		_lastWorkUnit = -1;
		_nextWorkUnit = _lastWorkUnit;
		return isLastThread;
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

	public PerThreadStats consumeAndResetStats()
	{
		PerThreadStats stats = new PerThreadStats(this.entitiesProcessed
				, this.entityChangesProcessed
				, this.millisInCrowdProcessor
				
				, this.creaturesProcessed
				, this.creatureChangesProcessed
				, this.millisInCreatureProcessor
				
				, this.cuboidsProcessed
				, this.cuboidMutationsProcessed
				, this.cuboidBlockupdatesProcessed
				, this.millisInWorldProcessor
		);
		
		this.entitiesProcessed = 0;
		this.entityChangesProcessed = 0;
		this.millisInCrowdProcessor = 0L;
		
		this.creaturesProcessed = 0;
		this.creatureChangesProcessed = 0;
		this.millisInCreatureProcessor = 0L;
		
		this.cuboidsProcessed = 0;
		this.cuboidMutationsProcessed = 0;
		this.cuboidBlockupdatesProcessed = 0;
		this.millisInWorldProcessor = 0L;
		
		return stats;
	}

	/**
	 * The statistics of what a specific thread does during the parallel tick phase.
	 */
	public static record PerThreadStats(int entitiesProcessed
			, int entityChangesProcessed
			, long millisInCrowdProcessor
			
			, int creaturesProcessed
			, int creatureChangesProcessed
			, long millisInCreatureProcessor
			
			, int cuboidsProcessed
			, int cuboidMutationsProcessed
			, int cuboidBlockupdatesProcessed
			, long millisInWorldProcessor
	)
	{}
}
