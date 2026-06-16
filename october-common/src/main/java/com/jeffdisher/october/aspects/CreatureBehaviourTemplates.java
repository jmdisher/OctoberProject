package com.jeffdisher.october.aspects;

import com.jeffdisher.october.actions.EntityActionImpregnateCreature;
import com.jeffdisher.october.actions.EntityActionTakeDamageFromEntity;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.logic.NudgeHelpers;
import com.jeffdisher.october.logic.RayCastHelpers;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.subactions.EntitySubActionReleaseWeapon;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MinimalEntity;
import com.jeffdisher.october.types.MutableCreature;
import com.jeffdisher.october.types.PassiveType;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Contains the implementations of EntityType.IBehaviourTemplate.
 */
public class CreatureBehaviourTemplates
{
	/**
	 * A creature will wait one second between attacks.
	 */
	public static final long MILLIS_ATTACK_COOLDOWN = 1000L;
	/**
	 * A creature will wait 5 seconds between ranged attacks.
	 */
	public static final long MILLIS_RANGED_ATTACK_COOLDOWN = 5000L;
	/**
	 * The timeout from exiting love mode until it can be entered again.
	 */
	public static final long MILLIS_BREEDING_COOLDOWN = 5L * 60L * 1000L;

	public static class LivestockTemplate implements EntityType.IBehaviourTemplate
	{
		@Override
		public EntityType.TargetEntity findDeliberateTarget(MutableCreature creature, EntityCollection entityCollection)
		{
			// This is livestock so choose our target based on whether we are looking for a partner or food.
			EntityType.TargetEntity newTarget;
			if (((CreatureExtendedData.LivestockData)creature.newExtendedData).inLoveMode())
			{
				// Find another of this type in breeding mode.
				EntityType.TargetEntity[] target = new EntityType.TargetEntity[1];
				float[] distanceToTarget = new float[] { Float.MAX_VALUE };
				EntityType thisType = creature.getType();
				EntityVolume thisVolume = thisType.volume();
				int thisCreatureId = creature.getId();
				EntityLocation sourceEyeLocation = SpatialHelpers.getEyeLocation(creature.getLocation(), thisVolume);
				entityCollection.walkCreaturesInViewDistance(creature, (CreatureEntity check) -> {
					// Ignore ourselves and make sure that they are the same type and in love mode.
					if ((thisCreatureId != check.id()) && (thisType == check.type()))
					{
						CreatureExtendedData.LivestockData safe = (CreatureExtendedData.LivestockData)check.extendedData();
						if (safe.inLoveMode())
						{
							// See how far away they are so we choose the closest.
							EntityLocation end = check.location();
							float distance = SpatialHelpers.distanceFromLocationToVolume(sourceEyeLocation, end, thisVolume);
							if (distance < distanceToTarget[0])
							{
								target[0] = new EntityType.TargetEntity(check.id(), end);
								distanceToTarget[0] = distance;
							}
						}
					}
				});
				newTarget = target[0];
			}
			else
			{
				// We will keep this simple:  Find the closest player holding our breeding item, up to our limit.
				EntityType.TargetEntity[] target = new EntityType.TargetEntity[1];
				float[] distanceToTarget = new float[] { Float.MAX_VALUE };
				EntityType thisType = creature.getType();
				EntityLocation sourceEyeLocation = SpatialHelpers.getEyeLocation(creature.getLocation(), thisType.volume());
				EntityVolume playerVolume = Environment.getShared().creatures.PLAYER.volume();
				entityCollection.walkPlayersInViewDistance(creature, (Entity player) -> {
					// See if this player has the breeding item in their hand.
					if (thisType.breedingItem() == _itemInPlayerHand(player))
					{
						// See how far away they are so we choose the closest.
						EntityLocation end = player.location();
						float distance = SpatialHelpers.distanceFromLocationToVolume(sourceEyeLocation, end, playerVolume);
						if (distance < distanceToTarget[0])
						{
							target[0] = new EntityType.TargetEntity(player.id(), end);
							distanceToTarget[0] = distance;
						}
					}
				});
				newTarget = target[0];
			}
			return newTarget;
		}
		@Override
		public boolean isTargetValid(MutableCreature creature, EntityCollection entityCollection)
		{
			// We can only call this if there is a target.
			int targetId = creature.movementPlan.targetEntityId();
			Assert.assertTrue(CreatureEntity.NO_TARGET_ENTITY_ID != targetId);
			
			// How we look at the target depends on our type and state.
			EntityType creatureType = creature.getType();
			boolean isValid = false;
			// This may be a player or a partner creature, depending on state.
			CreatureExtendedData.LivestockData extendedData = (CreatureExtendedData.LivestockData)creature.newExtendedData;
			if (extendedData.inLoveMode())
			{
				// We must be looking at a partner so make sure that they are here and still in breeding mode.
				CreatureEntity partner = entityCollection.getCreatureById(targetId);
				if (null != partner)
				{
					CreatureExtendedData.LivestockData safe = (CreatureExtendedData.LivestockData)partner.extendedData();
					isValid = safe.inLoveMode();
				}
				else
				{
					isValid = false;
				}
			}
			else
			{
				// We must be looking at a player so make sure they still have food.
				Entity player = entityCollection.getPlayerById(targetId);
				if (null != player)
				{
					EntityLocation sourceEye = SpatialHelpers.getEyeLocation(creature.getLocation(), creatureType.volume());
					EntityLocation playerBase = player.location();
					EntityVolume playerVolume = Environment.getShared().creatures.PLAYER.volume();
					float distance = SpatialHelpers.distanceFromLocationToVolume(sourceEye, playerBase, playerVolume);
					if (distance <= creatureType.viewDistance())
					{
						isValid = (creatureType.breedingItem() == _itemInPlayerHand(player));
					}
				}
			}
			return isValid;
		}
		@Override
		public boolean didTakeSpecialAction(MutableCreature creature, TickProcessingContext context)
		{
			CreatureExtendedData.LivestockData changedData = _newExtendedDataAfterLivestockAction(context
				, creature.getId()
				, creature.newType
				, creature.newLocation
				, (CreatureExtendedData.LivestockData)creature.newExtendedData
				, (null != creature.movementPlan) ? creature.movementPlan.targetEntityId() : CreatureEntity.NO_TARGET_ENTITY_ID
			);
			boolean isDone;
			if (null != changedData)
			{
				creature.newExtendedData = changedData;
				creature.movementPlan = null;
				isDone = true;
			}
			else
			{
				isDone = false;
			}
			return isDone;
		}
		@Override
		public boolean setCreaturePregnant(MutableCreature creature, EntityLocation sireLocation, long gameTimeMillis)
		{
			boolean didBecomePregnant = false;
			// We can only do this if already in love mode.
			CreatureExtendedData.LivestockData extendedData = (CreatureExtendedData.LivestockData) creature.newExtendedData;
			if (extendedData.inLoveMode())
			{
				// Average the locations.
				EntityLocation parentLocation = creature.getLocation();
				EntityLocation spawnLocation = new EntityLocation((sireLocation.x() + parentLocation.x()) / 2.0f
						, (sireLocation.y() + parentLocation.y()) / 2.0f
						, (sireLocation.z() + parentLocation.z()) / 2.0f
				);
				// Clear the love mode, set the spawn location, and clear existing plans.
				long breedingReadyMillis = gameTimeMillis + MILLIS_BREEDING_COOLDOWN;
				CreatureExtendedData.LivestockData updated = new CreatureExtendedData.LivestockData(
					false
					, spawnLocation
					, breedingReadyMillis
				);
				creature.newExtendedData = updated;
				creature.movementPlan = null;
				creature.newShouldTakeAction = true;
				didBecomePregnant = true;
			}
			return didBecomePregnant;
		}
		
		// Returns null if there was no livestock action taken.
		private static CreatureExtendedData.LivestockData _newExtendedDataAfterLivestockAction(TickProcessingContext context
			, int creatureId
			, EntityType creatureType
			, EntityLocation location
			, CreatureExtendedData.LivestockData extendedData
			, int targetEntityId
		)
		{
			CreatureExtendedData.LivestockData changedData = null;
			
			// See if we are pregnant or searching for our mate.
			if (null != extendedData.offspringLocation())
			{
				// Spawn the creature and clear our offspring location.
				Environment env = Environment.getShared();
				EntityType offspringType = env.creatures.getOffspringType(creatureType);
				context.creatureSpawner.spawnCreature(offspringType, extendedData.offspringLocation());
				CreatureExtendedData.LivestockData updated = new CreatureExtendedData.LivestockData(
					false
					, null
					, extendedData.breedingReadyMillis()
				);
				changedData = updated;
			}
			else if (extendedData.inLoveMode() && (CreatureEntity.NO_TARGET_ENTITY_ID != targetEntityId))
			{
				// We are in love mode, and have found a target, so see if we are close enough to impregnate our target.
				// We have a target so see if we are in love mode and if they are in range to breed.
				MinimalEntity targetEntity = context.previousEntityLookUp.getById(targetEntityId);
				// If we got here, they must not have unloaded (we would have observed that in didUpdateTargetLocation.
				Assert.assertTrue(null != targetEntity);
				
				// See if they are within mating distance and we are the father.
				EntityLocation sourceEyeLocation = SpatialHelpers.getEyeLocation(location, creatureType.volume());
				EntityLocation targetBase = targetEntity.location();
				EntityVolume targetVolume = targetEntity.type().volume();
				float distance = SpatialHelpers.distanceFromLocationToVolume(sourceEyeLocation, targetBase, targetVolume);
				float matingDistance = creatureType.actionDistance();
				if ((distance <= matingDistance) && (targetEntity.id() < creatureId))
				{
					// Send the message to impregnate them.
					EntityActionImpregnateCreature sperm = new EntityActionImpregnateCreature(location);
					context.newChangeSink.creature(targetEntityId, sperm);
					// We can also now clear our plans since we are done with them.
					// However, we exited love mode so record when we should re-enter it.
					long breedingReadyMillis = context.currentTickTimeMillis + MILLIS_BREEDING_COOLDOWN;
					CreatureExtendedData.LivestockData updated = new CreatureExtendedData.LivestockData(
						false
						, null
						, breedingReadyMillis
					);
					changedData = updated;
				}
			}
			return changedData;
		}
	}

	public static class LivestockBabyTemplate implements EntityType.IBehaviourTemplate
	{
		@Override
		public EntityType.TargetEntity findDeliberateTarget(MutableCreature creature, EntityCollection entityCollection)
		{
			// These never have deliberate paths.
			return null;
		}
		@Override
		public boolean isTargetValid(MutableCreature creature, EntityCollection entityCollection)
		{
			throw Assert.unreachable();
		}
		@Override
		public boolean didTakeSpecialAction(MutableCreature creature, TickProcessingContext context)
		{
			boolean isDone = false;
			
			// The only special action a baby can take is growing up, so see if that is ready.
			CreatureExtendedData.BabyData extendedData = (CreatureExtendedData.BabyData)creature.newExtendedData;
			if (context.currentTickTimeMillis >= extendedData.maturityMillis())
			{
				// We will change the type to the corresponding adult.
				EntityType currentType = creature.getType();
				EntityType adultType = currentType.adultType();
				Assert.assertTrue(null != adultType);
				creature.changeEntityType(adultType, context.currentTickTimeMillis);
				isDone = true;
			}
			return isDone;
		}
		@Override
		public boolean setCreaturePregnant(MutableCreature creature, EntityLocation sireLocation, long gameTimeMillis)
		{
			throw Assert.unreachable();
		}
	}

	public static class HostileMeleeTemplate implements EntityType.IBehaviourTemplate
	{
		@Override
		public EntityType.TargetEntity findDeliberateTarget(MutableCreature creature, EntityCollection entityCollection)
		{
			return _findPlayerInRange(entityCollection, creature);
		}
		@Override
		public boolean isTargetValid(MutableCreature creature, EntityCollection entityCollection)
		{
			return _isHostileTargetValid(entityCollection, creature);
		}
		@Override
		public boolean didTakeSpecialAction(MutableCreature creature, TickProcessingContext context)
		{
			boolean isDone;
			// The only special action we will take is attacking but this path will also reset our tracking if the target moves.
			// We don't have an objective measurement of time but the tick rate is considered constant within a server instance so we will estimate time passed.
			long millisSinceLastAttack = context.currentTickTimeMillis - creature.newLastAttackMillis;
			if ((null != creature.movementPlan) && (CreatureEntity.NO_TARGET_ENTITY_ID != creature.movementPlan.targetEntityId()) && (millisSinceLastAttack >= MILLIS_ATTACK_COOLDOWN))
			{
				// We are tracking a target so see if they have moved (since we would need to clear our existing targets and
				// movement plans unless they are close enough for other actions).
				MinimalEntity targetEntity = context.previousEntityLookUp.getById(creature.movementPlan.targetEntityId());
				// If we got here, they must not have unloaded (we would have observed that in didUpdateTargetLocation.
				Assert.assertTrue(null != targetEntity);
				
				// See if they are in attack range.
				EntityType creatureType = creature.getType();
				EntityLocation sourceEyeLocation = SpatialHelpers.getEyeLocation(creature.getLocation(), creatureType.volume());
				EntityLocation targetBase = targetEntity.location();
				EntityVolume targetVolume = targetEntity.type().volume();
				float distance = SpatialHelpers.distanceFromLocationToVolume(sourceEyeLocation, targetBase, targetVolume);
				float attackDistance = creatureType.actionDistance();
				if (distance <= attackDistance)
				{
					// We can attack them so choose the target.
					int index = context.randomInt.applyAsInt(BodyPart.values().length);
					BodyPart target = BodyPart.values()[index];
					EntityActionTakeDamageFromEntity<IMutablePlayerEntity> takeDamage = new EntityActionTakeDamageFromEntity<>(target, creatureType.attackDamage(), creature.getId());
					context.newChangeSink.next(creature.movementPlan.targetEntityId(), takeDamage);
					NudgeHelpers.nudgeFromMelee(context
						, creature.movementPlan.targetEntityId()
						, creature.newLocation
						, creature.getType().volume()
						, targetBase
						, targetVolume
					);
					
					// Since we sent the attack, put us on attack cooldown.
					creature.newLastAttackMillis = context.currentTickTimeMillis;
					// We only count a successful attack as an "action".
					isDone = true;
				}
				else
				{
					// Too far away.
					isDone = false;
				}
			}
			else
			{
				// Nothing to do.
				isDone = false;
			}
			return isDone;
		}
		@Override
		public boolean setCreaturePregnant(MutableCreature creature, EntityLocation sireLocation, long gameTimeMillis)
		{
			throw Assert.unreachable();
		}
	}

	public static class HostileRangedTemplate implements EntityType.IBehaviourTemplate
	{
		@Override
		public EntityType.TargetEntity findDeliberateTarget(MutableCreature creature, EntityCollection entityCollection)
		{
			return _findPlayerInRange(entityCollection, creature);
		}
		@Override
		public boolean isTargetValid(MutableCreature creature, EntityCollection entityCollection)
		{
			return _isHostileTargetValid(entityCollection, creature);
		}
		@Override
		public boolean didTakeSpecialAction(MutableCreature creature, TickProcessingContext context)
		{
			boolean isDone;
			// The only special action we will take is attacking but this path will also reset our tracking if the target moves.
			// We don't have an objective measurement of time but the tick rate is considered constant within a server instance so we will estimate time passed.
			long millisSinceLastAttack = context.currentTickTimeMillis - creature.newLastAttackMillis;
			if ((null != creature.movementPlan) && (CreatureEntity.NO_TARGET_ENTITY_ID != creature.movementPlan.targetEntityId()) && (millisSinceLastAttack >= MILLIS_RANGED_ATTACK_COOLDOWN))
			{
				// We are tracking a target so see if they have moved (since we would need to clear our existing targets and
				// movement plans unless they are close enough for other actions).
				MinimalEntity targetEntity = context.previousEntityLookUp.getById(creature.movementPlan.targetEntityId());
				// If we got here, they must not have unloaded (we would have observed that in didUpdateTargetLocation.
				Assert.assertTrue(null != targetEntity);
				
				// See if they are in attack range - we will aim for the centre of the entity, since that will give us a large target.
				EntityType creatureType = creature.getType();
				EntityLocation sourceEye = SpatialHelpers.getEyeLocation(creature.getLocation(), creatureType.volume());
				EntityLocation targetBase = targetEntity.location();
				EntityVolume targetVolume = targetEntity.type().volume();
				float distance = SpatialHelpers.distanceFromLocationToVolume(sourceEye, targetBase, targetVolume);
				float attackDistance = creatureType.actionDistance();
				if (distance <= attackDistance)
				{
					// We are in range so find the vector which will fire in an arc toward the centre of the target.
					EntityLocation targetCentre = SpatialHelpers.getCentreOfRegion(targetBase, targetVolume);
					
					// Make sure that we can see them.
					Environment env = Environment.getShared();
					RayCastHelpers.RayBlock solidCollision = RayCastHelpers.findFirstCollision(sourceEye, targetCentre, (AbsoluteLocation location) -> {
						BlockProxy proxy = context.previousBlockLookUp.readBlock(location);
						boolean shouldStop;
						if (null != proxy)
						{
							boolean isActive = FlagsAspect.isSet(proxy.getFlags(), FlagsAspect.FLAG_ACTIVE);
							shouldStop = env.blocks.isSolid(proxy.getBlock(), isActive);
						}
						else
						{
							shouldStop = true;
						}
						return shouldStop;
					});
					
					if (null == solidCollision)
					{
						// Nothing in the way so see if there is a ballistic trajectory to satisfy this.
						EntityLocation startVector = SpatialHelpers.getBallisticVector(sourceEye, targetCentre, EntitySubActionReleaseWeapon.PROJECTILE_POWER_MULTIPLIER);
						if (null != startVector)
						{
							// Create the arrow.
							context.passiveSpawner.spawnPassive(PassiveType.PROJECTILE_ARROW, sourceEye, startVector, null);
							
							// We only count a successful attack as an "action".
							isDone = true;
						}
						else
						{
							// There is no way to hit there from here.
							isDone = false;
						}
					}
					else
					{
						// There is something in the way.
						isDone = false;
					}
					// Since we at least tried to find a valid attack, put us on attack cooldown.
					creature.newLastAttackMillis = context.currentTickTimeMillis;
				}
				else
				{
					// Too far away.
					isDone = false;
				}
			}
			else
			{
				// Nothing to do.
				isDone = false;
			}
			return isDone;
		}
		@Override
		public boolean setCreaturePregnant(MutableCreature creature, EntityLocation sireLocation, long gameTimeMillis)
		{
			throw Assert.unreachable();
		}
	}

	public static class VillagerTemplate implements EntityType.IBehaviourTemplate
	{
		@Override
		public EntityType.TargetEntity findDeliberateTarget(MutableCreature creature, EntityCollection entityCollection)
		{
			// These never have deliberate paths.
			return null;
		}
		@Override
		public boolean isTargetValid(MutableCreature creature, EntityCollection entityCollection)
		{
			throw Assert.unreachable();
		}
		@Override
		public boolean didTakeSpecialAction(MutableCreature creature, TickProcessingContext context)
		{
			// No special actions.
			return false;
		}
		@Override
		public boolean setCreaturePregnant(MutableCreature creature, EntityLocation sireLocation, long gameTimeMillis)
		{
			throw Assert.unreachable();
		}
	}


	private static EntityType.TargetEntity _findPlayerInRange(EntityCollection entityCollection, IMutableMinimalEntity creature)
	{
		EntityType.TargetEntity[] target = new EntityType.TargetEntity[1];
		float[] distanceToTarget = new float[] { Float.MAX_VALUE };
		EntityLocation sourceEyeLocation = SpatialHelpers.getEyeLocation(creature.getLocation(), creature.getType().volume());
		EntityVolume playerVolume = Environment.getShared().creatures.PLAYER.volume();
		entityCollection.walkPlayersInViewDistance(creature, (Entity player) -> {
			// We are looking for any player so just choose the closest.
			EntityLocation end = player.location();
			float distance = SpatialHelpers.distanceFromLocationToVolume(sourceEyeLocation, end, playerVolume);
			if (distance < distanceToTarget[0])
			{
				target[0] = new EntityType.TargetEntity(player.id(), end);
				distanceToTarget[0] = distance;
			}
		});
		return target[0];
	}

	private static Item _itemInPlayerHand(Entity player)
	{
		int itemKey = player.hotbarItems()[player.hotbarIndex()];
		Items itemsInHand = player.inventory().getStackForKey(itemKey);
		return (null != itemsInHand)
				? itemsInHand.type()
				: null
		;
	}

	private static boolean _isHostileTargetValid(EntityCollection entityCollection, MutableCreature creature)
	{
		// We can only call this if there is a target.
		int targetId = creature.movementPlan.targetEntityId();
		Assert.assertTrue(CreatureEntity.NO_TARGET_ENTITY_ID != targetId);
		
		// How we look at the target depends on our type and state.
		EntityType creatureType = creature.getType();
		boolean isValid = false;
		
		// Make sure that they still exist, are in range.
		Entity player = entityCollection.getPlayerById(targetId);
		if (null != player)
		{
			EntityLocation sourceEye = SpatialHelpers.getEyeLocation(creature.newLocation, creatureType.volume());
			EntityLocation playerBase = player.location();
			EntityVolume playerVolume = Environment.getShared().creatures.PLAYER.volume();
			float distance = SpatialHelpers.distanceFromLocationToVolume(sourceEye, playerBase, playerVolume);
			isValid = (distance <= creatureType.viewDistance());
		}
		return isValid;
	}
}
