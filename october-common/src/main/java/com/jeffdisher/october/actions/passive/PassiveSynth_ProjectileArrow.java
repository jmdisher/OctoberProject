package com.jeffdisher.october.actions.passive;

import com.jeffdisher.october.actions.EntityActionTakeDamageFromEntity;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.logic.DamageHelpers;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.logic.EntityMovementHelpers;
import com.jeffdisher.october.logic.NudgeHelpers;
import com.jeffdisher.october.logic.RayCastHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.IMutableCreatureEntity;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.PassiveEntity;
import com.jeffdisher.october.types.PassiveType;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * These are synthesized by the system for every tick and applied to every loaded PROJECTILE_ARROW passive entity.
 * This means that it is responsible for normal movement (which is just passively applying existing velocity and
 * gravity) but also the special collision logic when it hits a player or creature.
 */
public class PassiveSynth_ProjectileArrow
{
	public static final int DAMAGE_ARROW = 30;

	private PassiveSynth_ProjectileArrow()
	{
		// No point in instantiating these.
	}

	public static PassiveEntity applyChange(TickProcessingContext context, EntityCollection entitiyCollection, PassiveEntity entity)
	{
		Environment env = Environment.getShared();
		
		// Currently, we only have the ItemSlot type.
		PassiveType type = entity.type();
		Assert.assertTrue(PassiveType.PROJECTILE_ARROW == type);
		
		// Check if this should despawn due to damage or if we should apply movement.
		PassiveEntity result;
		if (DamageHelpers.findEnvironmentalDamageInVolume(env, context.previousBlockLookUp, entity.location(), entity.type().volume()) > 0)
		{
			// Passives immediately despawn upon taking damage.
			result = null;
		}
		else
		{
			// This is still alive so apply movement.
			EntityLocation startLocation = entity.location();
			EntityLocation startVelocity = entity.velocity();
			EntityVolume volume = type.volume();
			
			// First, we want to see if we will collide with anything.
			// -does damage to player or creature
			// -changes into ITEM_SLOT if it collides with a solid block
			EntityLocation endOfRay = new EntityLocation(startLocation.x() + startVelocity.x()
				, startLocation.y() + startVelocity.y()
				, startLocation.z() + startVelocity.z()
			);
			RayCastHelpers.RayBlock solidCollision = RayCastHelpers.findFirstCollision(startLocation, endOfRay, (AbsoluteLocation location) -> {
				BlockProxy proxy = context.previousBlockLookUp.apply(location);
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
			
			// If we hit a solid object, we might still have hit an entity before we got there so we will need to check entities, either way, but potentially resizing the end of the ray.
			if (null != solidCollision)
			{
				EntityLocation shortVelocity = startVelocity.makeScaledInstance(solidCollision.rayDistance() / startVelocity.getMagnitude());
				endOfRay = new EntityLocation(startLocation.x() + shortVelocity.x()
					, startLocation.y() + shortVelocity.y()
					, startLocation.z() + shortVelocity.z()
				);
			}
			
			// See if we hit an entity and either damage it and despawn or just move normally.
			int targetId = RayCastHelpers.findFirstCollisionInCollection(env, startLocation, endOfRay, entitiyCollection);
			if (targetId > 0)
			{
				// Hit a player.
				BodyPart target = BodyPart.values()[context.randomInt.applyAsInt(BodyPart.values().length)];
				EntityActionTakeDamageFromEntity<IMutablePlayerEntity> damage = new EntityActionTakeDamageFromEntity<>(target, DAMAGE_ARROW, 0);
				context.newChangeSink.next(targetId, damage);
				NudgeHelpers.nudgeFromArrow(context, targetId, startVelocity);
				
				result = null;
			}
			else if (targetId < 0)
			{
				// Hit a creature.
				BodyPart target = BodyPart.values()[context.randomInt.applyAsInt(BodyPart.values().length)];
				EntityActionTakeDamageFromEntity<IMutableCreatureEntity> damage = new EntityActionTakeDamageFromEntity<>(target, DAMAGE_ARROW, 0);
				context.newChangeSink.creature(targetId, damage);
				NudgeHelpers.nudgeFromArrow(context, targetId, startVelocity);
				
				result = null;
			}
			else
			{
				if (null != solidCollision)
				{
					// We hit a solid block so convert into an item without velocity.
					EntityLocation stillVelocity = new EntityLocation(0.0f, 0.0f, 0.0f);
					ItemSlot stack = ItemSlot.fromStack(new Items(env.items.getItemById("op.arrow"), 1));
					context.passiveSpawner.spawnPassive(PassiveType.ITEM_SLOT, startLocation, stillVelocity, stack);
					result = null;
				}
				else
				{
					// Hit nothing so just move.
					float seconds = (float)context.millisPerTick / EntityMovementHelpers.FLOAT_MILLIS_PER_SECOND;
					EntityMovementHelpers.HighLevelMovementResult movement = EntityMovementHelpers.commonMovementIdiom(context.previousBlockLookUp
						, startLocation
						, startVelocity
						, volume
						, 0.0f
						, 0.0f
						, 0.0f
						, seconds
					);
					EntityLocation finalLocation = movement.location();
					EntityLocation finalVelocity = movement.velocity();
					
					// We already checked whether or not it hit something so this MUST have moved and gravity will change velocity.
					Assert.assertTrue(!finalLocation.equals(entity.location()) && !finalVelocity.equals(entity.velocity()));
					result = new PassiveEntity(entity.id()
						, type
						, finalLocation
						, finalVelocity
						, entity.extendedData()
						, entity.lastAliveMillis()
					);
				}
			}
		}
		return result;
	}
}
