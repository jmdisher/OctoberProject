package com.jeffdisher.october.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.CuboidHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
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
		Map<AbsoluteLocation, MutationBlockSetBlock> updatesToApply = _createUpdateMap(cuboidUpdates, _shadowWorld.keySet());
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

	public ProjectedState buildProjectedState(Map<AbsoluteLocation, MutationBlockSetBlock> projectedBlockChanges)
	{
		return new ProjectedState(_thisShadowEntity, _shadowWorld, _shadowHeightMap, projectedBlockChanges);
		
	}

	public PartialEntity getEntity(int entityId)
	{
		return _shadowCrowd.get(entityId);
	}


	private Map<AbsoluteLocation, MutationBlockSetBlock> _createUpdateMap(List<MutationBlockSetBlock> updates, Set<CuboidAddress> loadedCuboids)
	{
		Map<AbsoluteLocation, MutationBlockSetBlock> map = new HashMap<>();
		for (MutationBlockSetBlock update : updates)
		{
			AbsoluteLocation location = update.getAbsoluteLocation();
			CuboidAddress address = location.getCuboidAddress();
			// If the server sent us an update, we MUST have it loaded.
			Assert.assertTrue(loadedCuboids.contains(address));
			
			MutationBlockSetBlock old = map.put(location, update);
			// We are not expecting duplicates.
			Assert.assertTrue(null == old);
		}
		return map;
	}

	private _UpdateTuple _applyUpdatesToShadowState(IEntityUpdate thisEntityUpdate
			, Map<Integer, List<IPartialEntityUpdate>> partialEntityUpdates
			, Map<AbsoluteLocation, MutationBlockSetBlock> updatesToApply
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
			, Map<AbsoluteLocation, MutationBlockSetBlock> updatesToApply
	)
	{
		// NOTE:  This logic is similar to WorldProcessor but partially-duplicated here to avoid all the other requirements of the WorldProcessor or redundant operations it would perform.
		Map<CuboidAddress, CuboidData> workingCuboids = new HashMap<>();
		for (Map.Entry<AbsoluteLocation, MutationBlockSetBlock> entry : updatesToApply.entrySet())
		{
			AbsoluteLocation location = entry.getKey();
			CuboidAddress address = location.getCuboidAddress();
			if (!workingCuboids.containsKey(address))
			{
				IReadOnlyCuboidData readOnly = _shadowWorld.get(address);
				CuboidData mutableCuboid = CuboidData.mutableClone(readOnly);
				workingCuboids.put(address, mutableCuboid);
			}
			
			// Apply the change.
			CuboidData mutableCuboid = workingCuboids.get(address);
			MutationBlockSetBlock update = entry.getValue();
			update.applyState(mutableCuboid);
		}
		
		// Prepare the output and build the height maps for these changes.
		for (Map.Entry<CuboidAddress, CuboidData> entry : workingCuboids.entrySet())
		{
			CuboidAddress address = entry.getKey();
			CuboidData mutableCuboid = entry.getValue();
			out_updatedCuboids.put(address, mutableCuboid);
			out_updatedMaps.put(address, HeightMapHelpers.buildHeightMap(mutableCuboid));
		}
	}


	public static record ApplicationSummary(Set<Integer> partialEntitiesChanged
			, Map<AbsoluteLocation, MutationBlockSetBlock> changedBlocks
	) {}

	private static record _UpdateTuple(Entity updatedShadowEntity
			, Map<Integer, PartialEntity> entitiesChangedInTick
			, Map<CuboidAddress, IReadOnlyCuboidData> stateFragment
			, Map<CuboidAddress, CuboidHeightMap> heightFragment
	) {}
}
