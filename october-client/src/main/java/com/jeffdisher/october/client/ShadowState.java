package com.jeffdisher.october.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.CuboidHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.logic.HeightMapHelpers;
import com.jeffdisher.october.mutations.IEntityUpdate;
import com.jeffdisher.october.mutations.IPartialEntityUpdate;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.MutablePartialEntity;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.utils.Assert;


/**
 * Contains the client-side shadow copy of the server-side authoritative state which is visible to this client.
 */
public class ShadowState
{
	private Entity _thisShadowEntity;
	private final Map<CuboidAddress, IReadOnlyCuboidData> _shadowWorld;
	private final Map<CuboidAddress, CuboidHeightMap> _shadowHeightMap;
	private final Map<Integer, PartialEntity> _shadowCrowd;

	public ShadowState()
	{
		_shadowWorld = new HashMap<>();
		_shadowHeightMap = new HashMap<>();
		_shadowCrowd = new HashMap<>();
	}

	/**
	 * Sets the local entity.  Note that this can only be called once and must be called before the first end of tick is
	 * delivered.
	 * 
	 * @param thisEntity The initial state to use for the local entity.
	 */
	public void setThisEntity(Entity thisEntity)
	{
		// This can only be set once.
		Assert.assertTrue(null == _thisShadowEntity);
		_thisShadowEntity = thisEntity;
	}

	public Entity getThisEntity()
	{
		return _thisShadowEntity;
	}

	public Map<CuboidAddress, IReadOnlyCuboidData> getCopyOfWorld()
	{
		return new HashMap<>(_shadowWorld);
	}

	public IReadOnlyCuboidData getCuboid(CuboidAddress address)
	{
		return _shadowWorld.get(address);
	}

	public ApplicationSummary absorbAuthoritativeChanges(List<PartialEntity> addedEntities
			, List<IReadOnlyCuboidData> addedCuboids
			
			, IEntityUpdate thisEntityUpdate
			, Map<Integer, List<IPartialEntityUpdate>> partialEntityUpdates
			, List<MutationBlockSetBlock> cuboidUpdates
			
			, List<Integer> removedEntities
			, List<CuboidAddress> removedCuboids
	)
	{
		// We assume that we must have been told about ourselves before this first tick.
		Assert.assertTrue(null != _thisShadowEntity);
		
		_shadowCrowd.putAll(addedEntities.stream().collect(Collectors.toMap((PartialEntity entity) -> entity.id(), (PartialEntity entity) -> entity)));
		_shadowWorld.putAll(addedCuboids.stream().collect(Collectors.toMap((IReadOnlyCuboidData cuboid) -> cuboid.getCuboidAddress(), (IReadOnlyCuboidData cuboid) -> cuboid)));
		_shadowHeightMap.putAll(addedCuboids.stream().collect(Collectors.toMap((IReadOnlyCuboidData cuboid) -> cuboid.getCuboidAddress(), (IReadOnlyCuboidData cuboid) -> HeightMapHelpers.buildHeightMap(cuboid))));
		
		// Apply all of these to the shadow state, much like TickRunner.  We ONLY change the shadow state in response to these authoritative changes.
		Map<CuboidAddress, List<MutationBlockSetBlock>> updatesToApply = _createUpdateMap(cuboidUpdates, _shadowWorld.keySet());
		_UpdateTuple shadowUpdates = _applyUpdatesToShadowState(thisEntityUpdate, partialEntityUpdates, updatesToApply);
		
		// Apply these to the shadow collections.
		// (we ignore exported changes or mutations since we will wait for the server to send those to us, once it commits them)
		if (null != shadowUpdates.updatedShadowEntity)
		{
			_thisShadowEntity = shadowUpdates.updatedShadowEntity;
		}
		_shadowCrowd.putAll(shadowUpdates.entitiesChangedInTick);
		_shadowWorld.putAll(shadowUpdates.stateFragment());
		_shadowHeightMap.putAll(shadowUpdates.heightFragment());
		
		// Remove before moving on to our projection.
		_shadowCrowd.keySet().removeAll(removedEntities);
		_shadowWorld.keySet().removeAll(removedCuboids);
		_shadowHeightMap.keySet().removeAll(removedCuboids);
		
		return new ApplicationSummary(shadowUpdates.entitiesChangedInTick.keySet(), updatesToApply);
	}

	public ProjectedState buildProjectedState()
	{
		Map<CuboidAddress, IReadOnlyCuboidData> projectedWorld = new HashMap<>(_shadowWorld);
		Map<CuboidAddress, CuboidHeightMap> projectedHeightMap = new HashMap<>(_shadowHeightMap);
		return new ProjectedState(_thisShadowEntity, projectedWorld, projectedHeightMap);
		
	}

	public PartialEntity getEntity(int entityId)
	{
		return _shadowCrowd.get(entityId);
	}


	private Map<CuboidAddress, List<MutationBlockSetBlock>> _createUpdateMap(List<MutationBlockSetBlock> updates, Set<CuboidAddress> loadedCuboids)
	{
		Map<CuboidAddress, List<MutationBlockSetBlock>> updatesByCuboid = new HashMap<>();
		for (MutationBlockSetBlock update : updates)
		{
			CuboidAddress address = update.getAbsoluteLocation().getCuboidAddress();
			// If the server sent us an update, we MUST have it loaded.
			Assert.assertTrue(loadedCuboids.contains(address));
			
			List<MutationBlockSetBlock> queue = updatesByCuboid.get(address);
			if (null == queue)
			{
				queue = new LinkedList<>();
				updatesByCuboid.put(address, queue);
			}
			queue.add(update);
		}
		return updatesByCuboid;
	}

	private _UpdateTuple _applyUpdatesToShadowState(IEntityUpdate thisEntityUpdate
			, Map<Integer, List<IPartialEntityUpdate>> partialEntityUpdates
			, Map<CuboidAddress, List<MutationBlockSetBlock>> updatesToApply
	)
	{
		Entity updatedShadowEntity = _applyLocalEntityUpdatesToShadowState(thisEntityUpdate);
		Map<Integer, PartialEntity> entitiesChangedInTick = _applyPartialEntityUpdatesToShadowState(partialEntityUpdates);
		
		Map<CuboidAddress, IReadOnlyCuboidData> updatedCuboids = new HashMap<>();
		Map<CuboidAddress, CuboidHeightMap> updatedMaps = new HashMap<>();
		_applyCuboidUpdatesToShadowState(updatedCuboids
				, updatedMaps
				, updatesToApply
		);
		_UpdateTuple shadowUpdates = new _UpdateTuple(updatedShadowEntity
				, entitiesChangedInTick
				, updatedCuboids
				, updatedMaps
		);
		return shadowUpdates;
	}

	private Entity _applyLocalEntityUpdatesToShadowState(IEntityUpdate thisEntityUpdate)
	{
		// We won't use the CrowdProcessor here since it applies IMutationEntity but the IEntityUpdate instances are simpler.
		Entity updatedShadowEntity = null;
		if (null != thisEntityUpdate)
		{
			Entity entityToChange = _thisShadowEntity;
			// These must already exist if they are being updated.
			Assert.assertTrue(null != entityToChange);
			MutableEntity mutable = MutableEntity.existing(entityToChange);
			thisEntityUpdate.applyToEntity(mutable);
			Entity frozen = mutable.freeze();
			if (entityToChange != frozen)
			{
				updatedShadowEntity = frozen;
			}
		}
		return updatedShadowEntity;
	}

	private Map<Integer, PartialEntity> _applyPartialEntityUpdatesToShadowState(Map<Integer, List<IPartialEntityUpdate>> partialEntityUpdates)
	{
		Map<Integer, PartialEntity> entitiesChangedInTick = new HashMap<>();
		for (Map.Entry<Integer, List<IPartialEntityUpdate>> elt : partialEntityUpdates.entrySet())
		{
			int entityId = elt.getKey();
			PartialEntity partialEntityToChange = _shadowCrowd.get(entityId);
			// These must already exist if they are being updated.
			Assert.assertTrue(null != partialEntityToChange);
			MutablePartialEntity mutable = MutablePartialEntity.existing(partialEntityToChange);
			for (IPartialEntityUpdate update : elt.getValue())
			{
				update.applyToEntity(mutable);
			}
			PartialEntity frozen = mutable.freeze();
			if (partialEntityToChange != frozen)
			{
				entitiesChangedInTick.put(entityId, frozen);
			}
		}
		return entitiesChangedInTick;
	}

	private void _applyCuboidUpdatesToShadowState(Map<CuboidAddress, IReadOnlyCuboidData> out_updatedCuboids
			, Map<CuboidAddress, CuboidHeightMap> out_updatedMaps
			, Map<CuboidAddress, List<MutationBlockSetBlock>> updatesToApply
	)
	{
		// NOTE:  This logic is similar to WorldProcessor but partially-duplicated here to avoid all the other requirements of the WorldProcessor or redundant operations it would perform.
		for (Map.Entry<CuboidAddress, List<MutationBlockSetBlock>> entry : updatesToApply.entrySet())
		{
			Set<AbsoluteLocation> existingUpdates = new HashSet<>();
			CuboidAddress address = entry.getKey();
			IReadOnlyCuboidData readOnly = _shadowWorld.get(address);
			
			List<MutationBlockSetBlock> updates = entry.getValue();
			// This list can never be empty.
			Assert.assertTrue(!updates.isEmpty());
			CuboidData mutableCuboid = CuboidData.mutableClone(readOnly);
			for (MutationBlockSetBlock update : entry.getValue())
			{
				AbsoluteLocation location = update.getAbsoluteLocation();
				// We expect only one update per location - if this fails, we need to update this algorithm (although the current plan is just to make a single update parameterized).
				boolean didAdd = existingUpdates.add(location);
				Assert.assertTrue(didAdd);
				
				MutableBlockProxy proxy = new MutableBlockProxy(location, readOnly);
				update.applyState(proxy);
				// The server should never tell us to update something in a way which isn't a change.
				Assert.assertTrue(proxy.didChange());
				proxy.writeBack(mutableCuboid);
			}
			out_updatedCuboids.put(address, mutableCuboid);
			out_updatedMaps.put(address, HeightMapHelpers.buildHeightMap(mutableCuboid));
		}
	}


	public static record ApplicationSummary(Set<Integer> partialEntitiesChanged
			, Map<CuboidAddress, List<MutationBlockSetBlock>> changesByCuboid
	) {}

	private static record _UpdateTuple(Entity updatedShadowEntity
			, Map<Integer, PartialEntity> entitiesChangedInTick
			, Map<CuboidAddress, IReadOnlyCuboidData> stateFragment
			, Map<CuboidAddress, CuboidHeightMap> heightFragment
	) {}
}
