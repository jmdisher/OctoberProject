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
	public int playersProcessed;
	public int playerActionsProcessed;
	public long nanosInEnginePlayers;

	public int creaturesProcessed;
	public int creatureActionsProcessed;
	public long nanosInEngineCreatures;

	public int passivesProcessed;
	public int passiveActionsProcessed;
	public long nanosInEnginePassives;

	public int workUnitsProcessed;
	public int cuboidsProcessed;
	public int cuboidMutationsProcessed;
	public int cuboidBlockupdatesProcessed;
	public long nanosInEngineCuboids;

	public long nanosInEngineSpawner;
	public long nanosProcessingOperator;

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
		PerThreadStats stats = new PerThreadStats(this.playersProcessed
			, this.playerActionsProcessed
			, this.nanosInEnginePlayers
			
			, this.creaturesProcessed
			, this.creatureActionsProcessed
			, this.nanosInEngineCreatures
			
			, this.passivesProcessed
			, this.passiveActionsProcessed
			, this.nanosInEnginePassives
			
			, this.workUnitsProcessed
			, this.cuboidsProcessed
			, this.cuboidMutationsProcessed
			, this.cuboidBlockupdatesProcessed
			, this.nanosInEngineCuboids
			
			, this.nanosInEngineSpawner
			, this.nanosProcessingOperator
		);
		
		this.playersProcessed = 0;
		this.playerActionsProcessed = 0;
		this.nanosInEnginePlayers = 0L;
		
		this.creaturesProcessed = 0;
		this.creatureActionsProcessed = 0;
		this.nanosInEngineCreatures = 0L;
		
		this.passivesProcessed = 0;
		this.passiveActionsProcessed = 0;
		this.nanosInEnginePassives = 0L;
		
		this.workUnitsProcessed = 0;
		this.cuboidsProcessed = 0;
		this.cuboidMutationsProcessed = 0;
		this.cuboidBlockupdatesProcessed = 0;
		this.nanosInEngineCuboids = 0L;
		
		this.nanosInEngineSpawner = 0L;
		this.nanosProcessingOperator = 0L;
		
		return stats;
	}

	/**
	 * The statistics of what a specific thread does during the parallel tick phase.
	 */
	public static record PerThreadStats(int playersProcessed
		, int playerActionsProcessed
		, long nanosInEnginePlayers
		
		, int creaturesProcessed
		, int creatureActionsProcessed
		, long nanosInEngineCreatures
		
		, int passivesProcessed
		, int passiveActionsProcessed
		, long nanosInEnginePassives
		
		, int workUnitsProcessed
		, int cuboidsProcessed
		, int cuboidMutationsProcessed
		, int cuboidBlockupdatesProcessed
		, long nanosInEngineCuboids
		
		, long nanosInEngineSpawner
		, long nanosProcessingOperator
	)
	{}
}
