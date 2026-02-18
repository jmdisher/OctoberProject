package com.jeffdisher.october.ticks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.jeffdisher.october.actions.EntityActionSimpleMove;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.ScheduledChange;
import com.jeffdisher.october.logic.ScheduledMutation;
import com.jeffdisher.october.persistence.SuspendedCuboid;
import com.jeffdisher.october.persistence.SuspendedEntity;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.PassiveEntity;
import com.jeffdisher.october.utils.Assert;


public record PreTickState(Map<CuboidAddress, IReadOnlyCuboidData> cuboidsByAddress
	, Map<CuboidAddress, CuboidHeightMap> heightMapsByAddress
	
	, Map<Integer, Entity> entitiesById
	, Map<Integer, Long> clientCommitLevelsById
	, Map<Integer, CreatureEntity> creaturesById
	, Map<Integer, PassiveEntity> passivesById
	
	, Map<CuboidAddress, List<ScheduledMutation>> blockMutationsByAddress
	, Map<CuboidAddress, Map<BlockAddress, Long>> periodicMutationsByCuboid
	
	, Map<Integer, List<ScheduledChange>> entityActionsById
	
	, Set<CuboidAddress> cuboidsLoadedThisTick
	, Map<AbsoluteLocation, BlockProxy> previousProxyCache
)
{
	public static PreTickState fromChanges(TickOutput masterFragment
		, FlatResults flatResults
		
		, List<SuspendedCuboid<IReadOnlyCuboidData>> newCuboids
		, Set<CuboidAddress> cuboidsToDrop
		
		, List<SuspendedEntity> newEntities
		, Map<Integer, EntityActionSimpleMove<IMutablePlayerEntity>> newEntityChanges
		, Map<Integer, Long> newCommitLevels
		, List<Integer> removedEntityIds
		
		, Map<Integer, List<ScheduledChange>> entityActionsFromConsole
		
	)
	{
		// We now update our mutable collections for the materials to use in the next tick.
		Map<CuboidAddress, IReadOnlyCuboidData> cuboidsByAddress = new HashMap<>(flatResults.cuboidsByAddress());
		Map<CuboidAddress, CuboidHeightMap> heightMapsByAddress = new HashMap<>(flatResults.heightMapsByAddress());
		Map<Integer, Entity> entitiesById = new HashMap<>(flatResults.entitiesById());
		Map<Integer, Long> clientCommitLevelsById = new HashMap<>(flatResults.clientCommitLevelsById());
		Map<Integer, CreatureEntity> creaturesById = new HashMap<>(flatResults.creaturesById());
		Map<Integer, PassiveEntity> passivesById = new HashMap<>(flatResults.passivesById());
		
		// Add any newly-loaded cuboids with their associated creatures and passives.
		Map<CuboidAddress, List<ScheduledMutation>> blockMutationsByAddress = new HashMap<>(flatResults.blockMutationsByCuboid());
		Map<CuboidAddress, Map<BlockAddress, Long>> periodicMutationsByCuboid = new HashMap<>(flatResults.periodicMutationsByCuboid());
		Set<CuboidAddress> cuboidsLoadedThisTick = new HashSet<>();
		if (null != newCuboids)
		{
			for (SuspendedCuboid<IReadOnlyCuboidData> suspended : newCuboids)
			{
				IReadOnlyCuboidData cuboid = suspended.cuboid();
				CuboidAddress address = cuboid.getCuboidAddress();
				
				cuboidsLoadedThisTick.add(address);
				
				Object old = cuboidsByAddress.put(address, cuboid);
				Assert.assertTrue(null == old);
				
				old = heightMapsByAddress.put(address, suspended.heightMap());
				Assert.assertTrue(null == old);
				
				// Load any creatures associated with this cuboid.
				for (CreatureEntity loadedCreature : suspended.creatures())
				{
					creaturesById.put(loadedCreature.id(), loadedCreature);
				}
				
				// Load any passives associated with this cuboid.
				for (PassiveEntity loadedPassive : suspended.passives())
				{
					passivesById.put(loadedPassive.id(), loadedPassive);
				}
				
				// Add any suspended mutations which came with the cuboid.
				List<ScheduledMutation> pending = suspended.pendingMutations();
				if (!pending.isEmpty())
				{
					old = blockMutationsByAddress.put(address, new ArrayList<>(pending));
					// This must not already be present (this was just created above here).
					Assert.assertTrue(null == old);
				}
				
				// Add any periodic mutations loaded with the cuboid.
				Map<BlockAddress, Long> periodic = suspended.periodicMutationMillis();
				if (!periodic.isEmpty())
				{
					old = periodicMutationsByCuboid.put(address, new HashMap<>(periodic));
					// This must not already be present (this was just created above here).
					Assert.assertTrue(null == old);
				}
			}
		}
		
		// Add newly-loaded player entities.
		Map<Integer, List<ScheduledChange>> entityActionsById = new HashMap<>(flatResults.entityActionsById());
		if (null != newEntities)
		{
			for (SuspendedEntity suspended : newEntities)
			{
				Entity entity = suspended.entity();
				int id = entity.id();
				Object old = entitiesById.put(id, entity);
				// This must not already be present.
				Assert.assertTrue(null == old);
				
				// Add any suspended mutations which came with the entity.
				List<ScheduledChange> changes = suspended.changes();
				if (!changes.isEmpty())
				{
					old = entityActionsById.put(id, new ArrayList<>(changes));
					// This must not already be present (this was just created above here).
					Assert.assertTrue(null == old);
				}
			}
		}
		for (Map.Entry<Integer, EntityActionSimpleMove<IMutablePlayerEntity>> container : newEntityChanges.entrySet())
		{
			// These are coming in from outside, so they should be run immediately (no delay for future), after anything already scheduled from the previous tick.
			int id = container.getKey();
			ScheduledChange change = new ScheduledChange(container.getValue(), 0L);
			List<ScheduledChange> existing = entityActionsById.get(id);
			if (null != existing)
			{
				// This is starting as read-only, from the snapshot, so wrap the data.
				existing = new ArrayList<>(existing);
			}
			else
			{
				existing = new ArrayList<>();
			}
			existing.add(change);
			entityActionsById.put(id, existing);
		}
		
		// Add in any changes from the console (operator commands) which need to be run on specific entities.
		for (Map.Entry<Integer, List<ScheduledChange>> container : entityActionsFromConsole.entrySet())
		{
			// This is much like the case where these come from the client, but there could be a list of them and we don't know their concrete type.
			int id = container.getKey();
			List<ScheduledChange> list = container.getValue();
			
			List<ScheduledChange> existing = entityActionsById.get(id);
			if (null != existing)
			{
				// This may be read-only, so wrap the data.
				existing = new ArrayList<>(existing);
				existing.addAll(list);
			}
			else
			{
				// Just drop in the list we have.
				existing = list;
			}
			// We changed the instance so re-add it.
			entityActionsById.put(id, existing);
		}
		
		// Update our map of latest client commit levels.
		clientCommitLevelsById.putAll(newCommitLevels);
		
		// Remove anything old.
		if (null != cuboidsToDrop)
		{
			for (CuboidAddress address : cuboidsToDrop)
			{
				Object old = cuboidsByAddress.remove(address);
				// This must already be present.
				Assert.assertTrue(null != old);
				old = heightMapsByAddress.remove(address);
				Assert.assertTrue(null != old);
				
				// Remove any creatures in this cuboid.
				// TODO:  Change this to use some sort of spatial look-up mechanism since this loop is attrocious.
				Iterator<Map.Entry<Integer, CreatureEntity>> expensive = creaturesById.entrySet().iterator();
				while (expensive.hasNext())
				{
					Map.Entry<Integer, CreatureEntity> one = expensive.next();
					EntityLocation loc = one.getValue().location();
					if (loc.getBlockLocation().getCuboidAddress().equals(address))
					{
						expensive.remove();
					}
				}
				// Similarly, remove the passives.
				Iterator<Map.Entry<Integer, PassiveEntity>> expensivePassives = passivesById.entrySet().iterator();
				while (expensivePassives.hasNext())
				{
					Map.Entry<Integer, PassiveEntity> one = expensivePassives.next();
					EntityLocation loc = one.getValue().location();
					if (loc.getBlockLocation().getCuboidAddress().equals(address))
					{
						expensivePassives.remove();
					}
				}
				
				// Remove any of the scheduled operations for this cuboid.
				blockMutationsByAddress.remove(address);
				periodicMutationsByCuboid.remove(address);
			}
		}
		if (null != removedEntityIds)
		{
			for (int entityId : removedEntityIds)
			{
				Entity old = entitiesById.remove(entityId);
				// This must have been present.
				Assert.assertTrue(null != old);
				
				// Remove any of the scheduled operations against this entity.
				entityActionsById.remove(entityId);
			}
		}
		
		// Carry forward the proxy cache from the previous tick unless the corresponding block locations changed or where removed.
		Set<AbsoluteLocation> changedBlocksInPreviousTick = flatResults.allChangedBlockLocations();
		Map<AbsoluteLocation, BlockProxy> previousProxyCache = masterFragment.populatedProxyCache().entrySet().stream()
			.filter((Map.Entry<AbsoluteLocation, BlockProxy> ent) -> {
				AbsoluteLocation location = ent.getKey();
				boolean shouldDrop = changedBlocksInPreviousTick.contains(location);
				if (!shouldDrop && (null != cuboidsToDrop))
				{
					CuboidAddress cuboidAddress = location.getCuboidAddress();
					shouldDrop = cuboidsToDrop.contains(cuboidAddress);
				}
				return !shouldDrop;
			})
			.collect(Collectors.toMap((Map.Entry<AbsoluteLocation, BlockProxy> ent) -> ent.getKey(), (Map.Entry<AbsoluteLocation, BlockProxy> ent) -> ent.getValue()))
		;
		
		// TODO:  We should probably remove this once we are sure we know what is happening and/or find a cheaper way to check this.
		for (CuboidAddress key : blockMutationsByAddress.keySet())
		{
			// Given that these can only be scheduled against loaded cuboids, which can only be explicitly unloaded above, anything remaining must still be present.
			Assert.assertTrue(cuboidsByAddress.containsKey(key));
		}
		for (CuboidAddress key : periodicMutationsByCuboid.keySet())
		{
			// Given that these can only be scheduled against loaded cuboids, which can only be explicitly unloaded above, anything remaining must still be present.
			Assert.assertTrue(cuboidsByAddress.containsKey(key));
		}
		for (int entityId : entityActionsById.keySet())
		{
			// Given that these can only be scheduled against loaded entities, which can only be explicitly unloaded above, anything remaining must still be present.
			Assert.assertTrue(entitiesById.containsKey(entityId));
		}
		
		return new PreTickState(Collections.unmodifiableMap(cuboidsByAddress)
			, Collections.unmodifiableMap(heightMapsByAddress)
			
			, Collections.unmodifiableMap(entitiesById)
			, Collections.unmodifiableMap(clientCommitLevelsById)
			, Collections.unmodifiableMap(creaturesById)
			, Collections.unmodifiableMap(passivesById)
			
			, _lockMapOfLists(blockMutationsByAddress)
			, _lockMapOfMaps(periodicMutationsByCuboid)
			
			, _lockMapOfLists(entityActionsById)
			
			, Collections.unmodifiableSet(cuboidsLoadedThisTick)
			, Collections.unmodifiableMap(previousProxyCache)
		);
	}


	private static <K, L> Map<K, List<L>> _lockMapOfLists(Map<K, List<L>> input)
	{
		return input.entrySet().stream()
			.collect(Collectors.toMap((Map.Entry<K, List<L>> ent) -> ent.getKey()
				, (Map.Entry<K, List<L>> ent) -> Collections.unmodifiableList(ent.getValue()))
			)
		;
	}

	private static <K, KI, VI> Map<K, Map<KI, VI>> _lockMapOfMaps(Map<K, Map<KI, VI>> input)
	{
		return input.entrySet().stream()
			.collect(Collectors.toMap((Map.Entry<K, Map<KI, VI>> ent) -> ent.getKey()
				, (Map.Entry<K, Map<KI, VI>> ent) -> Collections.unmodifiableMap(ent.getValue()))
			)
		;
	}
}
