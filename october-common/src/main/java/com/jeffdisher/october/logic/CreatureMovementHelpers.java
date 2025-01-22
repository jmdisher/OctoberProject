package com.jeffdisher.october.logic;

import com.jeffdisher.october.mutations.EntityChangeJump;
import com.jeffdisher.october.mutations.EntityChangeMove;
import com.jeffdisher.october.mutations.EntityChangeSwim;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
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
	 * @param creatureType The type of creature.
	 * @param directionHint The block we need to eventually enter.
	 * @param timeLimitMillis The number of milliseconds left in the tick.
	 * @param viscosityFraction The viscosity of the current block ([0.0 .. 1.0]) where 1.0 is solid.
	 * @param isIdleMovement True if this movement is just idle and not one with a specific goal.
	 * @return The next move to make to centre in the block toward directionHint (null if there is no useful action).
	 */
	public static EntityChangeMove<IMutableCreatureEntity> prepareForMove(EntityLocation creatureLocation
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
		// We will apply the viscosity directly to speed.
		float effectiveSpeed = (1.0f - viscosityFraction) * speed;
		float speedMultipler = isIdleMovement
				? 0.5f
				: 1.0f
		;
		EntityChangeMove<IMutableCreatureEntity> move = _moveByX(creatureLocation, timeLimitMillis, effectiveSpeed, speedMultipler, targetX);
		if (null == move)
		{
			move = _moveByY(creatureLocation, timeLimitMillis, effectiveSpeed, speedMultipler, targetY);
		}
		return move;
	}

	/**
	 * Creates the changes required for the given creature to move to targetBlock.
	 * 
	 * @param creatureLocation The creature's location.
	 * @param creatureType The type of creature.
	 * @param targetBlock The target location.
	 * @param timeLimitMillis The number of milliseconds left in the tick.
	 * @param viscosityFraction The viscosity of the current block ([0.0 .. 1.0]) where 1.0 is solid.
	 * @param isIdleMovement True if this movement is just idle and not one with a specific goal.
	 * @param isBlockSwimmable True if the creature is in a block where they can swim.
	 * @return The next move toward targetBlock (null if there is no useful action at this time - usually just pass
	 * time).
	 */
	public static IMutationEntity<IMutableCreatureEntity> moveToNextLocation(EntityLocation creatureLocation
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
		IMutationEntity<IMutableCreatureEntity> change;
		if (targetBlock.z() > creatureLocation.z())
		{
			// We need to go up so see if we should jump, swim, or hope our momentum will get us there.
			if (SpatialHelpers.isBlockAligned(creatureLocation.z()))
			{
				// Jump.
				change = new EntityChangeJump<>();
			}
			else if (isBlockSwimmable)
			{
				// Swim.
				change = new EntityChangeSwim<>();
			}
			else
			{
				// We will have to rely on our momentum to carry us there (or we are just failing to reach it).
				change = null;
			}
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
				float baseSpeed = creatureType.blocksPerSecond();
				// We will apply the viscosity directly to speed.
				float effectiveSpeed = (1.0f - viscosityFraction) * baseSpeed;
				float speedMultipler = isIdleMovement
						? 0.5f
						: 1.0f
				;
				// We need to move horizontally so figure out which way.
				if (maxHorizontal == distanceX)
				{
					change = _moveByX(creatureLocation, timeLimitMillis, effectiveSpeed, speedMultipler, stepLocation.x());
				}
				else
				{
					change = _moveByY(creatureLocation, timeLimitMillis, effectiveSpeed, speedMultipler, stepLocation.y());
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


	private static EntityChangeMove<IMutableCreatureEntity> _moveByX(EntityLocation location, long timeLimitMillis, float baseSpeed, float speedMultipler, float targetX)
	{
		float moveX = targetX - location.x();
		float sign = Math.signum(moveX);
		float absoluteMove = Math.abs(moveX);
		float speed = baseSpeed * speedMultipler;
		float maxDistanceInOneMutation = EntityChangeMove.MAX_PER_STEP_SPEED_MULTIPLIER * speed;
		EntityChangeMove<IMutableCreatureEntity> move = null;
		if (absoluteMove > FLOAT_THRESHOLD)
		{
			float oneAbs = Math.min(absoluteMove, maxDistanceInOneMutation);
			float oneMove = sign * oneAbs;
			float secondsToMove = (oneAbs / speed);
			// Round up to make sure that the mutation actually can fit in the time.
			long millisToMove = Math.min(timeLimitMillis, (long) Math.ceil(secondsToMove * 1000.0f));
			if (millisToMove > 0L)
			{
				EntityChangeMove.Direction direction = (oneMove > 0.0f)
						? EntityChangeMove.Direction.EAST
						: EntityChangeMove.Direction.WEST
				;
				move = new EntityChangeMove<>(millisToMove, speedMultipler, direction);
			}
			absoluteMove -= oneAbs;
		}
		return move;
	}

	private static EntityChangeMove<IMutableCreatureEntity> _moveByY(EntityLocation location, long timeLimitMillis, float baseSpeed, float speedMultipler, float targetY)
	{
		float moveY = targetY - location.y();
		float sign = Math.signum(moveY);
		float absoluteMove = Math.abs(moveY);
		float speed = baseSpeed * speedMultipler;
		float maxDistanceInOneMutation = EntityChangeMove.MAX_PER_STEP_SPEED_MULTIPLIER * speed;
		EntityChangeMove<IMutableCreatureEntity> move = null;
		if (absoluteMove > FLOAT_THRESHOLD)
		{
			float oneAbs = Math.min(absoluteMove, maxDistanceInOneMutation);
			float oneMove = sign * oneAbs;
			float secondsToMove = (oneAbs / speed);
			// Round up to make sure that the mutation actually can fit in the time.
			long millisToMove = Math.min(timeLimitMillis, (long) Math.ceil(secondsToMove * 1000.0f));
			if (millisToMove > 0L)
			{
				EntityChangeMove.Direction direction = (oneMove > 0.0f)
						? EntityChangeMove.Direction.NORTH
						: EntityChangeMove.Direction.SOUTH
				;
				move = new EntityChangeMove<>(millisToMove, speedMultipler, direction);
			}
			absoluteMove -= oneAbs;
		}
		return move;
	}
}
