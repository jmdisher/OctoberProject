package com.jeffdisher.october.ticks;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.ProcessorElement;
import com.jeffdisher.october.logic.ScheduledChange;
import com.jeffdisher.october.logic.ScheduledMutation;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.CuboidColumnAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.PassiveEntity;


/**
 * The snapshot of immutable state created whenever a tick is completed.
 */
public record TickSnapshot(long tickNumber
	, Map<CuboidAddress, SnapshotCuboid> cuboids
	, Map<Integer, SnapshotEntity> entities
	, Map<Integer, SnapshotCreature> creatures
	, Map<Integer, SnapshotPassive> passives
	, Map<CuboidColumnAddress, ColumnHeightMap> completedHeightMaps
	
	, List<EventRecord> postedEvents
	, Set<CuboidAddress> internallyMarkedAlive
	
	, TickStats stats
)
{
	public static record SnapshotCuboid(
		// Never null.
		IReadOnlyCuboidData completed
		// Null if there are no changes or non-empty.
		, List<MutationBlockSetBlock> blockChanges
		// Never null but can be empty.
		, List<ScheduledMutation> scheduledBlockMutations
		// Never null but can be empty.
		, Map<BlockAddress, Long> periodicMutationMillis
	)
	{}

	public static record SnapshotEntity(
		// The version of the entity at the end of the tick (never null).
		Entity completed
		// The previous version of the entity (null if not changed in this tick).
		, Entity previousVersion
		// The last commit level from the connected client.
		, long commitLevel
		// Never null but can be empty.
		, List<ScheduledChange> scheduledMutations
	)
	{}

	public static record SnapshotCreature(
		// Never null.
		CreatureEntity completed
		// The previous version of the entity (null if not changed in this tick).
		, CreatureEntity previousVersion
	)
	{}

	public static record SnapshotPassive(
		// Never null.
		PassiveEntity completed
		// The previous version of the entity (null if not changed in this tick).
		, PassiveEntity previousVersion
	)
	{}

	public static record TickStats(long tickNumber
			, long nanosInPreamble
			, long nanosInParallelPhase
			, long nanosInPostamble
			, ProcessorElement.PerThreadStats[] threadStats
			, int committedEntityMutationCount
			, int committedCuboidMutationCount
	) {
		public void writeToStream(PrintStream out)
		{
			long nanosPerMilli = 1_000_000L;
			long millisInPreamble = this.nanosInPreamble / nanosPerMilli;
			long millisInParallel = this.nanosInParallelPhase / nanosPerMilli;
			long millisInPostamble = this.nanosInPostamble / nanosPerMilli;
			long millisInFullCycle = millisInPreamble + millisInParallel + millisInPostamble;
			out.println("Log for slow (" + millisInFullCycle + " ms) tick " + this.tickNumber);
			out.println("\tPreamble: " + millisInPreamble + " ms");
			out.println("\tParallel: " + millisInParallel + " ms");
			for (int i = 0; i < this.threadStats.length; ++i)
			{
				ProcessorElement.PerThreadStats thread = this.threadStats[i];
				long millisInEnginePlayers = thread.nanosInEnginePlayers() / nanosPerMilli;
				long millisInEngineCreatures = thread.nanosInEngineCreatures() / nanosPerMilli;
				long millisInEnginePassives = thread.nanosInEnginePassives() / nanosPerMilli;
				long millisInEngineCuboids = thread.nanosInEngineCuboids() / nanosPerMilli;
				long millisInEngineSpawner = thread.nanosInEngineSpawner() / nanosPerMilli;
				long millisProcessingOperator = thread.nanosProcessingOperator() / nanosPerMilli;
				out.printf("\t-Thread %d ran %d work units in %d ms\n", i, thread.workUnitsProcessed(), (millisInEnginePlayers + millisInEngineCreatures + millisInEngineCuboids + millisInEngineSpawner + millisProcessingOperator));
				out.printf("\t\t=%d ms in EnginePlayer: %d players, %d actions\n", millisInEnginePlayers, thread.playersProcessed(), thread.playerActionsProcessed());
				out.printf("\t\t=%d ms in EngineCreatures: %d creatures, %d actions\n", millisInEngineCreatures, thread.creaturesProcessed(), thread.creatureActionsProcessed());
				out.printf("\t\t=%d ms in EnginePassives: %d passives, %d actions\n", millisInEnginePassives, thread.passivesProcessed(), thread.passiveActionsProcessed());
				out.printf("\t\t=%d ms in EngineCuboids: %d cuboids, %d mutations, %d block updates\n", millisInEngineCuboids, thread.cuboidsProcessed(), thread.cuboidMutationsProcessed(), thread.cuboidBlockupdatesProcessed());
				if (millisInEngineSpawner > 0L)
				{
					out.printf("\t\t=%d ms in EngineSpawner\n", millisInEngineSpawner);
				}
				if (millisProcessingOperator > 0L)
				{
					out.printf("\t\t=%d ms running operator commands\n", millisProcessingOperator);
				}
			}
			out.println("\tPostamble: " + millisInPostamble + " ms");
		}
	}
}
