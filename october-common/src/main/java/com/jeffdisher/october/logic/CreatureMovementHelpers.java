package com.jeffdisher.october.logic;

import com.jeffdisher.october.mutations.EntityChangeJump;
import com.jeffdisher.october.mutations.EntityChangeSwim;
import com.jeffdisher.october.mutations.EntityChangeTopLevelMovement;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.IMutableCreatureEntity;


/**
 * Helpers related to AI logic around around how creatures move through the world.
 */
public class CreatureMovementHelpers
{
	/**
	 * We use this as a "reasonably close" threshold since we can't reasonably compare against 0.0f in floats.
	 */
	public static final float FLOAT_THRESHOLD = 0.01f;

	/**
	 * Creates a list of movements to position the creature within its current block such that it can move in the
	 * direction of directionHint.  Returns an empty list if the creature is already in directionHint or is aligned on
	 * that edge of its current block.
	 * 
	 * @param supplier Looks up the viscosity of various blocks.
	 * @param creatureLocation The creature's location.
	 * @param creatureVelocity The creature's velocity.
	 * @param creatureType The type of creature.
	 * @param directionHint The block we need to eventually enter.
	 * @param timeLimitMillis The number of milliseconds left in the tick.
	 * @param viscosityFraction The viscosity of the current block ([0.0 .. 1.0]) where 1.0 is solid.
	 * @param isIdleMovement True if this movement is just idle and not one with a specific goal.
	 * @return The next move to make to centre in the block toward directionHint (null if there is no useful action).
	 */
	public static EntityChangeTopLevelMovement<IMutableCreatureEntity> prepareForMove(ViscosityReader supplier
			, EntityLocation creatureLocation
			, EntityLocation creatureVelocity
			, EntityType creatureType
			, AbsoluteLocation directionHint
			, long timeLimitMillis
			, float viscosityFraction
			, boolean isIdleMovement
	)
	{
		// Find our current location.
		AbsoluteLocation baseLocation = creatureLocation.getBlockLocation();
		float width = creatureType.volume().width();
		
		// First, make sure that any edge of the entity isn't outside of its current block or directionHint.
		// NOTE:  These bounds are for the specific base location, not width (as it accounts for width).
		float westBound = (float)baseLocation.x() + FLOAT_THRESHOLD;
		float eastBound = (float)baseLocation.x() + 1.0f - width - FLOAT_THRESHOLD;
		float southBound = (float)baseLocation.y() + FLOAT_THRESHOLD;
		float northBound = (float)baseLocation.y() + 1.0f - width - FLOAT_THRESHOLD;
		
		float targetX = creatureLocation.x();
		float targetY = creatureLocation.y();
		
		if (directionHint.y() > baseLocation.y())
		{
			// North.
			float possibleY = northBound;
			northBound += 1.0f;
			// If we are already north of our new possible target, just stay where we are.
			if (targetY > possibleY)
			{
				// We are already closer than we need to be.
			}
			else
			{
				targetY = possibleY;
			}
		}
		else if (directionHint.x() > baseLocation.x())
		{
			// East.
			float possibleX = eastBound;
			eastBound += 1.0f;
			// If we are already east of our new possible target, just stay where we are.
			if (targetX > possibleX)
			{
				// We are already closer than we need to be.
			}
			else
			{
				targetX = possibleX;
			}
		}
		else if (directionHint.y() < baseLocation.y())
		{
			// South.
			float possibleY = southBound;
			southBound -= 1.0f;
			// If we are already south of our new possible target, just stay where we are.
			if (targetY < possibleY)
			{
				// We are already closer than we need to be.
			}
			else
			{
				targetY = possibleY;
			}
		}
		else if (directionHint.x() < baseLocation.x())
		{
			// West.
			float possibleX = westBound;
			westBound -= 1.0f;
			// If we are already west of our new possible target, just stay where we are.
			if (targetX < possibleX)
			{
				// We are already closer than we need to be.
			}
			else
			{
				targetX = possibleX;
			}
		}
		else
		{
			// The target is probably above or below us so we don't need horizontal movement.
		}
		
		// Now, make sure that whatever target axes were unchanged still fit within our bounds.
		if (targetX > eastBound)
		{
			targetX = eastBound;
		}
		if (targetY > northBound)
		{
			targetY = northBound;
		}
		
		// Now, move.
		float speed = creatureType.blocksPerSecond();
		float speedMultiplier = isIdleMovement
				? 0.5f
				: 1.0f
		;
		// TODO:  Make sure that we are applying the baseline velocity change due to gravity and coasting in the same way in all cases here, and in MovementAccumulator.
		EntityLocation updatedVelocity = _getExistingVelocityChange(creatureVelocity, timeLimitMillis, viscosityFraction);
		EntityChangeTopLevelMovement<IMutableCreatureEntity> move = _moveByX(supplier, creatureLocation, updatedVelocity, creatureType.volume(), timeLimitMillis, speed * speedMultiplier, viscosityFraction, targetX);
		if (null == move)
		{
			move = _moveByY(supplier, creatureLocation, updatedVelocity, creatureType.volume(), timeLimitMillis, speed * speedMultiplier, viscosityFraction, targetY);
		}
		return move;
	}

	/**
	 * Creates the changes required for the given creature to move to targetBlock.
	 * 
	 * @param supplier Looks up the viscosity of various blocks.
	 * @param creatureLocation The creature's location.
	 * @param creatureVelocity The creature's velocity.
	 * @param yaw The creature's existing yaw.
	 * @param pitch The creature's existing pitch.
	 * @param creatureType The type of creature.
	 * @param targetBlock The target location.
	 * @param timeLimitMillis The number of milliseconds left in the tick.
	 * @param viscosityFraction The viscosity of the current block ([0.0 .. 1.0]) where 1.0 is solid.
	 * @param isIdleMovement True if this movement is just idle and not one with a specific goal.
	 * @param isBlockSwimmable True if the creature is in a block where they can swim.
	 * @return The next move toward targetBlock (null if there is no useful action at this time - usually just pass
	 * time).
	 */
	public static EntityChangeTopLevelMovement<IMutableCreatureEntity> moveToNextLocation(ViscosityReader supplier
			, EntityLocation creatureLocation
			, EntityLocation creatureVelocity
			, byte yaw
			, byte pitch
			, EntityType creatureType
			, AbsoluteLocation targetBlock
			, long timeLimitMillis
			, float viscosityFraction
			, boolean isIdleMovement
			, boolean isBlockSwimmable
	)
	{
		// TODO:  Make sure that we are applying the baseline velocity change due to gravity and coasting in the same way in all cases here, and in MovementAccumulator.
		EntityLocation updatedVelocity = _getExistingVelocityChange(creatureVelocity, timeLimitMillis, viscosityFraction);
		
		// We might need to jump, walk, or do nothing.
		// If the target is above us and we are on the ground, 
		EntityChangeTopLevelMovement<IMutableCreatureEntity> change;
		if (targetBlock.z() > creatureLocation.z())
		{
			// We need to go up so see if we should jump, swim, or hope our momentum will get us there.
			IMutationEntity<IMutableCreatureEntity> subAction;
			EntityLocation newVelocity;
			if (SpatialHelpers.isBlockAligned(creatureLocation.z()))
			{
				// Jump.
				subAction = new EntityChangeJump<>();
				newVelocity = new EntityLocation(updatedVelocity.x(), updatedVelocity.y(), EntityChangeJump.JUMP_FORCE);
			}
			else if (isBlockSwimmable)
			{
				// Swim.
				subAction = new EntityChangeSwim<>();
				newVelocity = new EntityLocation(updatedVelocity.x(), updatedVelocity.y(), EntityChangeSwim.SWIM_FORCE);
			}
			else
			{
				// We will have to rely on our momentum to carry us there (or we are just failing to reach it).
				subAction = null;
				newVelocity = updatedVelocity;
			}
			change = new _TopLevelBuilder(supplier)
				.buildChange(creatureLocation
					, newVelocity
					, creatureType.volume()
					, EntityChangeTopLevelMovement.Intensity.STANDING
					, yaw
					, pitch
					, timeLimitMillis
					, subAction
				);
		}
		else
		{
			// We might need to walk, coast in the air (which is the same as walking), or just fall.
			// We will just move over by the axis which differs and otherwise stay where we are in the other axis.
			float stepX = creatureLocation.x();
			float stepY = creatureLocation.y();
			AbsoluteLocation creatureBase = creatureLocation.getBlockLocation();
			float width = creatureType.volume().width();
			if (targetBlock.x() > creatureBase.x())
			{
				stepX = targetBlock.x() + FLOAT_THRESHOLD;
			}
			else if (targetBlock.x() < creatureBase.x())
			{
				stepX = targetBlock.x() + 1.0f - FLOAT_THRESHOLD - width;
			}
			if (targetBlock.y() > creatureBase.y())
			{
				stepY = targetBlock.y() + FLOAT_THRESHOLD;
			}
			else if (targetBlock.y() < creatureBase.y())
			{
				stepY = targetBlock.y() + 1.0f - FLOAT_THRESHOLD - width;
			}
			float stepZ = targetBlock.z();
			EntityLocation stepLocation = new EntityLocation(stepX, stepY, stepZ);
			float distanceX = Math.abs(stepLocation.x() - creatureLocation.x());
			float distanceY = Math.abs(stepLocation.y() - creatureLocation.y());
			// We don't want to walk diagonally so just see which is the largest distance.
			float maxHorizontal = Math.max(distanceX, distanceY);
			if (maxHorizontal > FLOAT_THRESHOLD)
			{
				// We need to move horizontally so figure out which way.
				float speed = creatureType.blocksPerSecond();
				float speedMultiplier = isIdleMovement
						? 0.5f
						: 1.0f
				;
				if (maxHorizontal == distanceX)
				{
					change = _moveByX(supplier, creatureLocation, updatedVelocity, creatureType.volume(), timeLimitMillis, speed * speedMultiplier, viscosityFraction, stepLocation.x());
				}
				else
				{
					change = _moveByY(supplier, creatureLocation, updatedVelocity, creatureType.volume(), timeLimitMillis, speed * speedMultiplier, viscosityFraction, stepLocation.y());
				}
			}
			else
			{
				// We don't need horizontal movement so just do nothing.
				change = null;
			}
		}
		return change;
	}

	/**
	 * A helper to create a change just to stand in place and pass time (allowing for falling/coasting, etc).
	 * 
	 * @param supplier Looks up the viscosity of various blocks.
	 * @param creatureLocation The creature's location.
	 * @param creatureVelocity The creature's velocity.
	 * @param yaw The creature's existing yaw.
	 * @param pitch The creature's existing pitch.
	 * @param creatureType The type of creature.
	 * @param timeLimitMillis The number of milliseconds left in the tick.
	 * @param viscosityFraction The viscosity of the current block ([0.0 .. 1.0]) where 1.0 is solid.
	 * @param subAction The sub-action to embed in the change (can be null).
	 * @return The change to pass time while standing (never null).
	 */
	public static EntityChangeTopLevelMovement<IMutableCreatureEntity> buildStandingChange(ViscosityReader supplier
		, EntityLocation creatureLocation
		, EntityLocation creatureVelocity
		, byte yaw
		, byte pitch
		, EntityType creatureType
		, long timeLimitMillis
		, float viscosityFraction
	)
	{
		// TODO:  Make sure that we are applying the baseline velocity change due to gravity and coasting in the same way in all cases here, and in MovementAccumulator.
		EntityLocation updatedVelocity = _getExistingVelocityChange(creatureVelocity, timeLimitMillis, viscosityFraction);
		
		return new _TopLevelBuilder(supplier)
			.buildChange(creatureLocation
				, updatedVelocity
				, creatureType.volume()
				, EntityChangeTopLevelMovement.Intensity.STANDING
				, yaw
				, pitch
				, timeLimitMillis
				, null
			);
	}


	private static EntityChangeTopLevelMovement<IMutableCreatureEntity> _moveByX(ViscosityReader supplier, EntityLocation location, EntityLocation velocity, EntityVolume volume, long timeLimitMillis, float currentCreatureSpeed, float viscosityFraction, float targetX)
	{
		// NOTE:  This call assumes that moving in X is possible (on solid ground or swimming).
		float moveX = targetX - location.x();
		float sign = Math.signum(moveX);
		float absoluteMove = Math.abs(moveX);
		EntityChangeTopLevelMovement<IMutableCreatureEntity> move = null;
		if (absoluteMove > FLOAT_THRESHOLD)
		{
			float inverseViscosity = (1.0f - viscosityFraction);
			float effectiveSpeed = inverseViscosity * currentCreatureSpeed;
			
			EntityLocation newVelocity = new EntityLocation(sign * effectiveSpeed, 0.0f, velocity.z());
			move = new _TopLevelBuilder(supplier)
				.buildChange(location
					, newVelocity
					, volume
					, EntityChangeTopLevelMovement.Intensity.WALKING
					, (moveX > 0.0f) ? OrientationHelpers.YAW_EAST : OrientationHelpers.YAW_WEST
					, OrientationHelpers.PITCH_FLAT
					, timeLimitMillis
					, null
				);
		}
		return move;
	}

	private static EntityChangeTopLevelMovement<IMutableCreatureEntity> _moveByY(ViscosityReader supplier, EntityLocation location, EntityLocation velocity, EntityVolume volume, long timeLimitMillis, float currentCreatureSpeed, float viscosityFraction, float targetY)
	{
		// NOTE:  This call assumes that moving in Y is possible (on solid ground or swimming).
		float moveY = targetY - location.y();
		float sign = Math.signum(moveY);
		float absoluteMove = Math.abs(moveY);
		EntityChangeTopLevelMovement<IMutableCreatureEntity> move = null;
		if (absoluteMove > FLOAT_THRESHOLD)
		{
			float inverseViscosity = (1.0f - viscosityFraction);
			float effectiveSpeed = inverseViscosity * currentCreatureSpeed;
			
			EntityLocation newVelocity = new EntityLocation(0.0f, sign * effectiveSpeed, velocity.z());
			move = new _TopLevelBuilder(supplier)
				.buildChange(location
					, newVelocity
					, volume
					, EntityChangeTopLevelMovement.Intensity.WALKING
					, (moveY > 0.0f) ? OrientationHelpers.YAW_NORTH : OrientationHelpers.YAW_SOUTH
					, OrientationHelpers.PITCH_FLAT
					, timeLimitMillis
					, null
				);
		}
		return move;
	}

	private static EntityLocation _getExistingVelocityChange(EntityLocation creatureVelocity, long timeLimitMillis, float viscosityFraction)
	{
		// This changes velocity based on coasting and gravity.
		float inverseViscosity = (1.0f - viscosityFraction);
		float newXVelocity = EntityChangeTopLevelMovement.velocityAfterViscosityAndCoast(inverseViscosity, creatureVelocity.x());
		float newYVelocity = EntityChangeTopLevelMovement.velocityAfterViscosityAndCoast(inverseViscosity, creatureVelocity.y());
		float newZVelocity = EntityMovementHelpers.zVelocityAfterGravity(creatureVelocity.z(), inverseViscosity, timeLimitMillis);
		return new EntityLocation(newXVelocity, newYVelocity, newZVelocity);
	}


	private static class _TopLevelBuilder
	{
		private final ViscosityReader _supplier;
		private EntityLocation location;
		private EntityLocation velocity;
		
		public _TopLevelBuilder(ViscosityReader supplier)
		{
			_supplier = supplier;
		}
		public EntityChangeTopLevelMovement<IMutableCreatureEntity> buildChange(EntityLocation creatureLocation
			, EntityLocation creatureVelocity
			, EntityVolume creatureVolume
			, EntityChangeTopLevelMovement.Intensity intensity
			, byte yaw
			, byte pitch
			, long timeLimitMillis
			, IMutationEntity<IMutableCreatureEntity> subAction
		)
		{
			float secondsToMove = (float)timeLimitMillis / 1000.0f;
			EntityLocation effectiveMovement = new EntityLocation(secondsToMove * creatureVelocity.x()
				, secondsToMove * creatureVelocity.y()
				, secondsToMove * creatureVelocity.z()
			);
			EntityMovementHelpers.interactiveEntityMove(creatureLocation, creatureVolume, effectiveMovement, new EntityMovementHelpers.InteractiveHelper() {
				@Override
				public void setLocationAndCancelVelocity(EntityLocation finalLocation, boolean cancelX, boolean cancelY, boolean cancelZ)
				{
					_TopLevelBuilder.this.location = finalLocation;
					// We keep the velocity we proposed, except for any axes which were cancelled due to collision.
					_TopLevelBuilder.this.velocity = new EntityLocation(cancelX ? 0.0f : creatureVelocity.x()
						, cancelY ? 0.0f : creatureVelocity.y()
						, cancelZ ? 0.0f : creatureVelocity.z()
					);
				}
				@Override
				public float getViscosityForBlockAtLocation(AbsoluteLocation location)
				{
					return _supplier.getViscosityFraction(location);
				}
			});
			
			return new EntityChangeTopLevelMovement<>(this.location
				, this.velocity
				, intensity
				, yaw
				, pitch
				, subAction
			);
		}
	}
}
