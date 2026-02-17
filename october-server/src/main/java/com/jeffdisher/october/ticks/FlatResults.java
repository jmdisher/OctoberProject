package com.jeffdisher.october.ticks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.CuboidHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.BlockChangeDescription;
import com.jeffdisher.october.logic.LogicLayerHelpers;
import com.jeffdisher.october.logic.ScheduledChange;
import com.jeffdisher.october.logic.ScheduledMutation;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.CuboidColumnAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutableCreatureEntity;
import com.jeffdisher.october.types.IPassiveAction;
import com.jeffdisher.october.types.PassiveEntity;
import com.jeffdisher.october.types.TargetedAction;
import com.jeffdisher.october.utils.Assert;


/**
 * This is a helper record type which converts the final shape of TickOutput into the maps consumed by other top-level
 * logic (since TickOutput types are just lists to keep the merging cheap and other interactions low-overhead).
 */
public record FlatResults(Map<CuboidColumnAddress, ColumnHeightMap> columnHeightMaps
	, Map<CuboidAddress, IReadOnlyCuboidData> cuboidsByAddress
	, Map<CuboidAddress, CuboidHeightMap> heightMapsByAddress
	
	, Map<CuboidAddress, List<MutationBlockSetBlock>> resultantBlockChangesByCuboid
	, Map<CuboidAddress, List<AbsoluteLocation>> blockUpdatesByCuboid
	, Map<CuboidAddress, List<AbsoluteLocation>> lightingUpdatesByCuboid
	, Map<CuboidAddress, List<AbsoluteLocation>> logicUpdatesByCuboid
	, Set<AbsoluteLocation> allChangedBlockLocations
	
	, Map<CuboidAddress, List<ScheduledMutation>> blockMutationsByCuboid
	, Map<CuboidAddress, Map<BlockAddress, Long>> periodicMutationsByCuboid
	
	, Map<Integer, Entity> entitiesById
	, Map<Integer, Long> clientCommitLevelsById
	, Map<Integer, CreatureEntity> creaturesById
	, Map<Integer, PassiveEntity> passivesById
	
	, Map<Integer, List<ScheduledChange>> entityActionsById
	, Map<Integer, List<IEntityAction<IMutableCreatureEntity>>> creatureActionsById
	, Map<Integer, List<IPassiveAction>> passiveActionsById
)
{
	public static FlatResults fromOutput(TickOutput masterFragment)
	{
		// Collect the column data.
		Map<CuboidColumnAddress, ColumnHeightMap> columnHeightMaps = masterFragment.world().columns().stream()
			.collect(Collectors.toMap((TickOutput.ColumnHeightOutput output) -> output.columnAddress(), (TickOutput.ColumnHeightOutput output) -> output.columnHeightMap()))
		;
		
		// Collect to cuboid-related data.
		Map<CuboidAddress, IReadOnlyCuboidData> cuboidsByAddress = new HashMap<>();
		Map<CuboidAddress, CuboidHeightMap> heightMapsByAddress = new HashMap<>();
		
		Map<CuboidAddress, List<MutationBlockSetBlock>> resultantBlockChangesByCuboid = new HashMap<>();
		Map<CuboidAddress, List<AbsoluteLocation>> blockUpdatesByCuboid = new HashMap<>();
		Map<CuboidAddress, List<AbsoluteLocation>> lightingUpdatesByCuboid = new HashMap<>();
		Set<AbsoluteLocation> allChangedBlockLocations = new HashSet<>();
		Set<AbsoluteLocation> potentialLogicChangeSet = new HashSet<>();
		
		Map<CuboidAddress, Map<BlockAddress, Long>> periodicMutationsByCuboid = new HashMap<>();
		
		for (TickOutput.CuboidOutput oneCuboid : masterFragment.world().cuboids())
		{
			CuboidAddress address = oneCuboid.address();
			
			cuboidsByAddress.put(address, (null != oneCuboid.updatedCuboidOrNull())
				? oneCuboid.updatedCuboidOrNull()
				: oneCuboid.previousCuboid()
			);
			heightMapsByAddress.put(address, (null != oneCuboid.updatedHeightMapOrNull())
				? oneCuboid.updatedHeightMapOrNull()
				: oneCuboid.previousHeightMap()
			);
			
			List<MutationBlockSetBlock> blockChanges = new ArrayList<>();
			List<AbsoluteLocation> updateLocations = new ArrayList<>();
			List<AbsoluteLocation> lightingUpdateLocations = new ArrayList<>();
			for (BlockChangeDescription change : oneCuboid.blockChanges())
			{
				MutationBlockSetBlock blockSetBlock = change.serializedForm();
				AbsoluteLocation location = blockSetBlock.getAbsoluteLocation();
				allChangedBlockLocations.add(location);
				blockChanges.add(blockSetBlock);
				if (change.requiresUpdateEvent())
				{
					updateLocations.add(location);
				}
				if (change.requiresLightingCheck())
				{
					lightingUpdateLocations.add(location);
				}
				
				// Logic changes are more complicated, as they don't usually change within the block, but adjacent
				// ones (except for conduit changes) so build the set and then split it by cuboid in a later pass.
				byte logicBits = change.logicCheckBits();
				if (0x0 != logicBits)
				{
					LogicLayerHelpers.populateSetWithPotentialLogicChanges(potentialLogicChangeSet, location, logicBits);
				}
			}
			if (!blockChanges.isEmpty())
			{
				resultantBlockChangesByCuboid.put(address, Collections.unmodifiableList(blockChanges));
			}
			if (!updateLocations.isEmpty())
			{
				blockUpdatesByCuboid.put(address, Collections.unmodifiableList(updateLocations));
			}
			if (!lightingUpdateLocations.isEmpty())
			{
				lightingUpdatesByCuboid.put(address, Collections.unmodifiableList(lightingUpdateLocations));
			}
			
			if (!oneCuboid.periodicNotReadyMutations().isEmpty())
			{
				periodicMutationsByCuboid.put(address, oneCuboid.periodicNotReadyMutations());
			}
		}
		
		// Collect the normal block mutations.
		Map<CuboidAddress, List<ScheduledMutation>> blockMutationsByCuboid = new HashMap<>();
		for (ScheduledMutation scheduledMutation : masterFragment.newlyScheduledMutations())
		{
			_scheduleMutationForCuboid(blockMutationsByCuboid, scheduledMutation);
		}
		for (ScheduledMutation scheduledMutation : masterFragment.world().notYetReadyMutations())
		{
			_scheduleMutationForCuboid(blockMutationsByCuboid, scheduledMutation);
		}
		
		// Logic updates require a post-pass, since they actually change adjacent blocks, not themselves.
		Map<CuboidAddress, List<AbsoluteLocation>> logicUpdatesByCuboid = new HashMap<>();
		for (AbsoluteLocation location : potentialLogicChangeSet)
		{
			CuboidAddress address = location.getCuboidAddress();
			List<AbsoluteLocation> existing = logicUpdatesByCuboid.get(address);
			if (null == existing)
			{
				existing = new ArrayList<>();
				logicUpdatesByCuboid.put(address, existing);
			}
			existing.add(location);
		}
		
		// Collect the entities and the client commit levels.
		Map<Integer, Entity> entitiesById = new HashMap<>();
		Map<Integer, Long> clientCommitLevelsById = new HashMap<>();
		for (TickOutput.EntityOutput value : masterFragment.entities().entityOutput())
		{
			Entity updated = value.updatedEntity();
			Entity toSnapshot = (null != updated)
				? updated
				: value.previousEntity()
			;
			int id = value.entityId();
			entitiesById.put(id, toSnapshot);
			clientCommitLevelsById.put(id, value.clientCommitLevel());
		}
		
		// Collect the creatures.
		Map<Integer, CreatureEntity> creaturesById = new HashMap<>();
		for (TickOutput.BasicOutput<CreatureEntity> value : masterFragment.creatures().creatureOutput())
		{
			if (!value.didDie())
			{
				CreatureEntity updated = value.updated();
				CreatureEntity toSnapshot = (null != updated)
					? updated
					: value.previous()
				;
				creaturesById.put(value.id(), toSnapshot);
			}
		}
		for (CreatureEntity newCreature : masterFragment.spawnedCreatures())
		{
			Object old = creaturesById.put(newCreature.id(), newCreature);
			Assert.assertTrue(null == old);
		}
		
		// Collect the passives.
		Map<Integer, PassiveEntity> passivesById = new HashMap<>();
		for (TickOutput.BasicOutput<PassiveEntity> value : masterFragment.passives().passiveOutput())
		{
			if (!value.didDie())
			{
				PassiveEntity updated = value.updated();
				PassiveEntity toSnapshot = (null != updated)
					? updated
					: value.previous()
				;
				passivesById.put(value.id(), toSnapshot);
			}
		}
		for (PassiveEntity newPassive : masterFragment.spawnedPassives())
		{
			Object old = passivesById.put(newPassive.id(), newPassive);
			Assert.assertTrue(null == old);
		}
		
		// Extract all entity actions which weren't yet run or were freshly scheduled.
		Map<Integer, List<ScheduledChange>> entityActionsById = new HashMap<>();
		for (TargetedAction<ScheduledChange> targeted : masterFragment.newlyScheduledChanges())
		{
			_scheduleChangesForEntity(entityActionsById, targeted.targetId(), targeted.action());
		}
		for (TickOutput.EntityOutput ent : masterFragment.entities().entityOutput())
		{
			int id = ent.entityId();
			Assert.assertTrue(id > 0);
			
			// We want to schedule anything which wasn't yet ready.
			for (ScheduledChange change : ent.notYetReadyUnsortedActions())
			{
				_scheduleChangesForEntity(entityActionsById, id, change);
			}
		}
		
		// Extract creature actions which were scheduled.
		Map<Integer, List<IEntityAction<IMutableCreatureEntity>>> creatureActionsById = new HashMap<>();
		for (TargetedAction<IEntityAction<IMutableCreatureEntity>> targeted : masterFragment.newlyScheduledCreatureChanges())
		{
			_scheduleChangesForEntity(creatureActionsById, targeted.targetId(), targeted.action());
		}
		
		// Extract passive actions which were scheduled.
		Map<Integer, List<IPassiveAction>> passiveActionsById = new HashMap<>();
		for (TargetedAction<IPassiveAction> targeted : masterFragment.newlyScheduledPassiveActions())
		{
			_scheduleChangesForEntity(passiveActionsById, targeted.targetId(), targeted.action());
		}
		
		return new FlatResults(columnHeightMaps
			
			, Collections.unmodifiableMap(cuboidsByAddress)
			, Collections.unmodifiableMap(heightMapsByAddress)
			, Collections.unmodifiableMap(resultantBlockChangesByCuboid)
			, Collections.unmodifiableMap(blockUpdatesByCuboid)
			, Collections.unmodifiableMap(lightingUpdatesByCuboid)
			, _lockMapOfLists(logicUpdatesByCuboid)
			, Collections.unmodifiableSet(allChangedBlockLocations)
			
			, _lockMapOfLists(blockMutationsByCuboid)
			, Collections.unmodifiableMap(periodicMutationsByCuboid)
			
			, Collections.unmodifiableMap(entitiesById)
			, Collections.unmodifiableMap(clientCommitLevelsById)
			, Collections.unmodifiableMap(creaturesById)
			, Collections.unmodifiableMap(passivesById)
			
			, _lockMapOfLists(entityActionsById)
			, _lockMapOfLists(creatureActionsById)
			, _lockMapOfLists(passiveActionsById)
		);
	}


	private static void _scheduleMutationForCuboid(Map<CuboidAddress, List<ScheduledMutation>> nextTickMutations, ScheduledMutation mutation)
	{
		CuboidAddress address = mutation.mutation().getAbsoluteLocation().getCuboidAddress();
		List<ScheduledMutation> queue = nextTickMutations.get(address);
		if (null == queue)
		{
			queue = new LinkedList<>();
			nextTickMutations.put(address, queue);
		}
		queue.add(mutation);
	}

	private static <T> void _scheduleChangesForEntity(Map<Integer, List<T>> nextTickChanges, int entityId, T action)
	{
		List<T> queue = nextTickChanges.get(entityId);
		if (null == queue)
		{
			// We want to build this as mutable.
			queue = new LinkedList<>();
			nextTickChanges.put(entityId, queue);
		}
		queue.add(action);
	}

	private static <K, L> Map<K, List<L>> _lockMapOfLists(Map<K, List<L>> input)
	{
		return input.entrySet().stream()
			.collect(Collectors.toMap((Map.Entry<K, List<L>> ent) -> ent.getKey()
				, (Map.Entry<K, List<L>> ent) -> Collections.unmodifiableList(ent.getValue()))
			)
		;
	}
}
