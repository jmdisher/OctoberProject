package com.jeffdisher.october.logic;

import com.jeffdisher.october.actions.EntityActionSimpleMove;
import com.jeffdisher.october.subactions.EntityChangeJump;
import com.jeffdisher.october.subactions.EntityChangeSwim;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.IEntitySubAction;
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
	 * @param creatureLocation The creature's location.
	 * @param creatureVelocity The creature's velocity.
	 * @param creatureType The type of creature.
	 * @param directionHint The block we need to eventually enter.
	 * @param timeLimitMillis The number of milliseconds left in the tick.
	 * @param viscosityFraction The viscosity of the current block ([0.0 .. 1.0]) where 1.0 is solid.
	 * @param isIdleMovement True if this movement is just idle and not one with a specific goal.
	 * @return The next move to make to centre in the block toward directionHint (null if there is no useful action).
	 */
	public static EntityActionSimpleMove<IMutableCreatureEntity> prepareForMove(EntityLocation creatureLocation
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
		float widthEdge = (float)Math.ceil(width) - width;
		float westBound = (float)baseLocation.x() + FLOAT_THRESHOLD;
		float eastBound = (float)baseLocation.x() + widthEdge - FLOAT_THRESHOLD;
		float southBound = (float)baseLocation.y() + FLOAT_THRESHOLD;
		float northBound = (float)baseLocation.y() + widthEdge - FLOAT_THRESHOLD;
		
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
		EntityActionSimpleMove<IMutableCreatureEntity> move = _moveByX(creatureLocation, timeLimitMillis, speed * speedMultiplier, viscosityFraction, targetX, creatureVelocity.x());
		if (null == move)
		{
			move = _moveByY(creatureLocation, timeLimitMillis, speed * speedMultiplier, viscosityFraction, targetY, creatureVelocity.y());
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
	public static EntityActionSimpleMove<IMutableCreatureEntity> moveToNextLocation(ViscosityReader supplier
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
		// We might need to jump, walk, or do nothing.
		// If the target is above us and we are on the ground, 
		EntityActionSimpleMove<IMutableCreatureEntity> change;
		if (targetBlock.z() > creatureLocation.z())
		{
			// We need to go up so see if we should jump, swim, or hope our momentum will get us there.
			IEntitySubAction<IMutableCreatureEntity> subAction;
			if (EntityChangeJump.canJumpWithReader(supplier, creatureLocation, creatureType.volume(), creatureVelocity))
			{
				// Jump.
				subAction = new EntityChangeJump<>();
			}
			else if (isBlockSwimmable && (creatureVelocity.z() <= 0.0f))
			{
				// Swim.
				subAction = new EntityChangeSwim<>();
			}
			else
			{
				// We will have to rely on our momentum to carry us there (or we are just failing to reach it).
				subAction = null;
			}
			change = new EntityActionSimpleMove<>(0.0f
				, 0.0f
				, EntityActionSimpleMove.Intensity.STANDING
				, yaw
				, pitch
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
					change = _moveByX(creatureLocation, timeLimitMillis, speed * speedMultiplier, viscosityFraction, stepLocation.x(), creatureVelocity.x());
				}
				else
				{
					change = _moveByY(creatureLocation, timeLimitMillis, speed * speedMultiplier, viscosityFraction, stepLocation.y(), creatureVelocity.y());
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


	private static EntityActionSimpleMove<IMutableCreatureEntity> _moveByX(EntityLocation location, long timeLimitMillis, float currentCreatureSpeed, float viscosityFraction, float targetX, float passiveVelocityX)
	{
		// NOTE:  This call assumes that moving in X is possible (on solid ground or swimming).
		float moveX = targetX - location.x();
		float absoluteMove = Math.abs(moveX);
		EntityActionSimpleMove<IMutableCreatureEntity> move = null;
		if (absoluteMove > FLOAT_THRESHOLD)
		{
			// Note that we use viscosity to estimate how far we will move but the actual units of movement are just in terms of our basic speed.
			float distanceToMove = _findRawMoveInTime(currentCreatureSpeed, viscosityFraction, moveX, passiveVelocityX, timeLimitMillis);
			
			move = new EntityActionSimpleMove<>(distanceToMove
				, 0.0f
				, EntityActionSimpleMove.Intensity.WALKING
				, (moveX > 0.0f) ? OrientationHelpers.YAW_EAST : OrientationHelpers.YAW_WEST
				, OrientationHelpers.PITCH_FLAT
				, null
			);
		}
		return move;
	}

	private static EntityActionSimpleMove<IMutableCreatureEntity> _moveByY(EntityLocation location, long timeLimitMillis, float currentCreatureSpeed, float viscosityFraction, float targetY, float passiveVelocityY)
	{
		// NOTE:  This call assumes that moving in Y is possible (on solid ground or swimming).
		float moveY = targetY - location.y();
		float absoluteMove = Math.abs(moveY);
		EntityActionSimpleMove<IMutableCreatureEntity> move = null;
		if (absoluteMove > FLOAT_THRESHOLD)
		{
			// Note that we use viscosity to estimate how far we will move but the actual units of movement are just in terms of our basic speed.
			float distanceToMove = _findRawMoveInTime(currentCreatureSpeed, viscosityFraction, moveY, passiveVelocityY, timeLimitMillis);
			
			move = new EntityActionSimpleMove<>(0.0f
				, distanceToMove
				, EntityActionSimpleMove.Intensity.WALKING
				, (moveY > 0.0f) ? OrientationHelpers.YAW_NORTH : OrientationHelpers.YAW_SOUTH
				, OrientationHelpers.PITCH_FLAT
				, null
			);
		}
		return move;
	}

	private static float _findRawMoveInTime(float currentCreatureSpeed
		, float viscosityFraction
		, float move
		, float passiveVelocity
		, long timeLimitMillis
	)
	{
		float inverseViscosity = (1.0f - viscosityFraction);
		float effectiveSpeed = inverseViscosity * currentCreatureSpeed;
		
		float seconds = (float)timeLimitMillis / 1000.0f;
		float effectivePassiveMovement = inverseViscosity * passiveVelocity * seconds;
		float movementSum = move - effectivePassiveMovement;
		float absoluteMove = Math.abs(movementSum);
		float maxDistanceInTime = effectiveSpeed * seconds;
		float distanceInTime = Math.min(absoluteMove, maxDistanceInTime);
		float fractionToMove = distanceInTime / maxDistanceInTime;
		
		float rawDistanceInTime = currentCreatureSpeed * seconds;
		float distanceToMove = rawDistanceInTime * fractionToMove;
		float activeDistance = Math.signum(movementSum) * distanceToMove;
		return activeDistance;
	}
}
