package com.jeffdisher.october.ticks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.CuboidHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.BlockChangeDescription;
import com.jeffdisher.october.logic.LogicLayerHelpers;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.CuboidColumnAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.PassiveEntity;
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
	
	, Map<CuboidAddress, Map<BlockAddress, Long>> periodicMutationsByCuboid
	
	, Map<Integer, Entity> entitiesById
	, Map<Integer, Long> clientCommitLevelsById
	, Map<Integer, CreatureEntity> creaturesById
	, Map<Integer, PassiveEntity> passivesById
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
		
		// Logic updates require a post-pass, since they actually change adjacent blocks, not themselves.
		Map<CuboidAddress, List<AbsoluteLocation>> logicUpdatesByCuboid = new HashMap<>();
		for (AbsoluteLocation location : potentialLogicChangeSet)
		{
			CuboidAddress cuboid = location.getCuboidAddress();
			if (!logicUpdatesByCuboid.containsKey(cuboid))
			{
				logicUpdatesByCuboid.put(cuboid, new ArrayList<>());
			}
			logicUpdatesByCuboid.get(cuboid).add(location);
		}
		logicUpdatesByCuboid = logicUpdatesByCuboid.entrySet().stream()
			.collect(Collectors.toMap((Map.Entry<CuboidAddress, List<AbsoluteLocation>> ent) -> ent.getKey()
				, (Map.Entry<CuboidAddress, List<AbsoluteLocation>> ent) -> Collections.unmodifiableList(ent.getValue()))
			)
		;
		
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
		
		return new FlatResults(columnHeightMaps
			
			, Collections.unmodifiableMap(cuboidsByAddress)
			, Collections.unmodifiableMap(heightMapsByAddress)
			, Collections.unmodifiableMap(resultantBlockChangesByCuboid)
			, Collections.unmodifiableMap(blockUpdatesByCuboid)
			, Collections.unmodifiableMap(lightingUpdatesByCuboid)
			, logicUpdatesByCuboid
			, Collections.unmodifiableSet(allChangedBlockLocations)
			
			, Collections.unmodifiableMap(periodicMutationsByCuboid)
			
			, Collections.unmodifiableMap(entitiesById)
			, Collections.unmodifiableMap(clientCommitLevelsById)
			, Collections.unmodifiableMap(creaturesById)
			, Collections.unmodifiableMap(passivesById)
		);
	}
}
