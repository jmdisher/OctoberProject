package com.jeffdisher.october.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.jeffdisher.october.net.EntityUpdatePerField;
import com.jeffdisher.october.net.PartialEntityUpdate;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.MutablePartialEntity;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.types.PartialPassive;
import com.jeffdisher.october.utils.Assert;


/**
 * Similar to WorldContainer but for the various entities in the world.  Note that only the local entity has a projected
 * version and all other entity types are shadow instances, only.
 */
public class EntityContainer
{
	private Entity _thisShadowEntity;
	private Entity _thisProjectedEntity;
	private final Map<Integer, PartialEntity> _shadowCrowd = new HashMap<>();
	private final Map<Integer, PartialPassive> _shadowPassives = new HashMap<>();
	private final Set<Integer> _allPlayerIds = new HashSet<>();
	private final Set<Integer> _allCreatureIds = new HashSet<>();

	/**
	 * Sets the current shadow entity.
	 * 
	 * @param shadowEntity The shadow entity state, from the server.
	 */
	public void setThisEntity(Entity shadowEntity)
	{
		_thisShadowEntity = shadowEntity;
		_allPlayerIds.add(shadowEntity.id());
	}

	/**
	 * Sets the projected entity to be this instance.
	 * 
	 * @param entity The instance to set as the new projected entity.
	 */
	public void setProjectedLocalEntity(Entity entity)
	{
		// For clarity, we want to null out redundant references.
		if (_thisShadowEntity == entity)
		{
			_thisProjectedEntity = null;
		}
		else
		{
			_thisProjectedEntity = entity;
		}
	}

	/**
	 * Resets internal projected state, meaning that only the shadow copy of the local entity will be available.
	 */
	public void clearProjectedState()
	{
		_thisProjectedEntity = null;
	}

	/**
	 * Applies the given update to the shadow entity instance.
	 * Note that function may not be appropriate here since it internally modifies data state of shadow data, doesn't
	 * just define how to read and write this data, but it does keep the API simple.
	 * 
	 * @param thisEntityUpdate The change to apply to the local shadow entity.
	 */
	public void applyUpdatesToThisShadowEntity(EntityUpdatePerField thisEntityUpdate)
	{
		// These must already exist if they are being updated.
		Assert.assertTrue(null != _thisShadowEntity);
		
		MutableEntity mutable = MutableEntity.existing(_thisShadowEntity);
		thisEntityUpdate.applyToEntity(mutable);
		_thisShadowEntity = mutable.freeze();
	}

	/**
	 * Applies the given updates to the shadow partial entity instances.
	 * Note that function may not be appropriate here since it internally modifies data state of shadow data, doesn't
	 * just define how to read and write this data, but it does keep the API simple.
	 * 
	 * @param partialEntityUpdates The updates to apply.
	 * @return The list of new entity instances which were updated by this call.
	 */
	public List<PartialEntity> applyUpdatesToShadowPartialEntities(List<PartialEntityUpdate> partialEntityUpdates)
	{
		List<PartialEntity> changedEntities = new ArrayList<>();
		for (PartialEntityUpdate update : partialEntityUpdates)
		{
			int entityId = update.getEntityId();
			
			PartialEntity partialEntityToChange = _shadowCrowd.get(entityId);
			// These must already exist if they are being updated.
			Assert.assertTrue(null != partialEntityToChange);
			MutablePartialEntity mutable = MutablePartialEntity.existing(partialEntityToChange);
			update.applyToEntity(mutable);
			PartialEntity frozen = mutable.freeze();
			
			// If this was sent to us, it means that it MUST make a state change (otherwise, the server is sending useless data).
			Assert.assertTrue(partialEntityToChange != frozen);
			_shadowCrowd.put(entityId, frozen);
			changedEntities.add(frozen);
		}
		return changedEntities;
	}

	/**
	 * Applies the given updates to the shadow passive entity instances.
	 * Note that function may not be appropriate here since it internally modifies data state of shadow data, doesn't
	 * just define how to read and write this data, but it does keep the API simple.
	 * 
	 * @param partialPassiveUpdates The updates to apply.
	 * @return The list of new passive instances which were updated by this call.
	 */
	public List<PartialPassive> applyUpdatesToShadowPassiveEntities(List<PassiveUpdate> partialPassiveUpdates)
	{
		List<PartialPassive> changedEntities = new ArrayList<>();
		for (PassiveUpdate update : partialPassiveUpdates)
		{
			int entityId = update.passiveEntityId();
			PartialPassive passiveToChange = _shadowPassives.get(entityId);
			// These must already exist if they are being updated.
			Assert.assertTrue(null != passiveToChange);
			PartialPassive updated = new PartialPassive(entityId
				, passiveToChange.type()
				, update.location()
				, update.velocity()
				, passiveToChange.extendedData()
			);
			
			// If this was sent to us, it means that it MUST make a state change (otherwise, the server is sending useless data).
			Assert.assertTrue(passiveToChange != updated);
			_shadowPassives.put(entityId, updated);
			changedEntities.add(updated);
		}
		return changedEntities;
	}

	/**
	 * Adds the given shadow entities and passives to the container.
	 * 
	 * @param addedEntities PartialEntity instances to add.
	 * @param addedPassives PartialPassive instances to add.
	 */
	public void addShadowEntities(List<PartialEntity> addedEntities, List<PartialPassive> addedPassives)
	{
		for (PartialEntity partial : addedEntities)
		{
			int id = partial.id();
			_shadowCrowd.put(id, partial);
			if (id > 0)
			{
				_allPlayerIds.add(id);
			}
			else
			{
				_allCreatureIds.add(id);
			}
		}
		_shadowPassives.putAll(addedPassives.stream().collect(Collectors.toMap((PartialPassive entity) -> entity.id(), (PartialPassive entity) -> entity)));
	}

	/**
	 * Removes the given entities and passives, by ID.
	 * 
	 * @param removedEntities The list of entity IDs to remove.
	 * @param removedPassives The list of passive IDs to remove.
	 */
	public void removeShadowEntities(List<Integer> removedEntities, List<Integer> removedPassives)
	{
		_shadowCrowd.keySet().removeAll(removedEntities);
		_shadowPassives.keySet().removeAll(removedPassives);
		
		_allPlayerIds.removeAll(removedEntities);
		_allCreatureIds.removeAll(removedEntities);
	}

	/**
	 * @return The set of all player (not creature) IDs known to the container, including the local entity.
	 */
	public Set<Integer> getAllPlayerEntityIds()
	{
		return Collections.unmodifiableSet(_allPlayerIds);
	}

	/**
	 * @return The set of all creature IDs known to the container.
	 */
	public Set<Integer> getAllCreatureEntityIds()
	{
		return Collections.unmodifiableSet(_allCreatureIds);
	}

	/**
	 * @return The set of all passive IDs known to the container.
	 */
	public Set<Integer> getPassiveEntityIds()
	{
		return Collections.unmodifiableSet(_shadowPassives.keySet());
	}

	/**
	 * @return The projected local entity or shadow, if there is no projected entity state.
	 */
	public Entity getProjectedOrShadowLocalEntity()
	{
		return (null != _thisProjectedEntity)
			? _thisProjectedEntity
			: _thisShadowEntity
		;
	}

	/**
	 * @param id The ID to look up.
	 * @return Returns a PartialPassive for the given passive ID (returns null if not known).
	 */
	public PartialPassive getPassiveById(int id)
	{
		return _shadowPassives.get(id);
	}

	/**
	 * @param id The ID to look up.
	 * @return Returns a PartialEntity for the given player/creature ID (returns null if not known).
	 */
	public PartialEntity getPartialEntityById(int id)
	{
		return _shadowCrowd.get(id);
	}
}
