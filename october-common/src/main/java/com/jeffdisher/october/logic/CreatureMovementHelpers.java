package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.List;

import com.jeffdisher.october.mutations.EntityChangeJump;
import com.jeffdisher.october.mutations.EntityChangeMove;
import com.jeffdisher.october.mutations.EntityChangeSwim;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.EntityConstants;
import com.jeffdisher.october.types.EntityLocation;
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
	 * @param creature The creature.
	 * @param directionHint The block we need to eventually enter.
	 * @param isIdleMovement True if this movement is just idle and not one with a specific goal.
	 * @return The list of moves to make (empty if already in a good position).
	 */
	public static List<IMutationEntity<IMutableCreatureEntity>> prepareForMove(CreatureEntity creature, AbsoluteLocation directionHint, boolean isIdleMovement)
	{
		// Find our current location.
		EntityLocation location = creature.location();
		AbsoluteLocation baseLocation = location.getBlockLocation();
		float width = EntityConstants.getVolume(creature.type()).width();
		
		// First, make sure that any edge of the entity isn't outside of its current block or directionHint.
		// NOTE:  These bounds are for the specific base location, not width (as it accounts for width).
		float westBound = (float)baseLocation.x() + FLOAT_THRESHOLD;
		float eastBound = (float)baseLocation.x() + 1.0f - width - FLOAT_THRESHOLD;
		float southBound = (float)baseLocation.y() + FLOAT_THRESHOLD;
		float northBound = (float)baseLocation.y() + 1.0f - width - FLOAT_THRESHOLD;
		
		float targetX = location.x();
		float targetY = location.y();
		
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
		List<IMutationEntity<IMutableCreatureEntity>> list = new ArrayList<>();
		float speed = EntityConstants.getBlocksPerSecondSpeed(creature.type());
		float speedMultipler = isIdleMovement
				? 0.5f
				: 1.0f
		;
		_moveByX(list, location, speed, speedMultipler, targetX);
		_moveByY(list, location, speed, speedMultipler, targetY);
		return list;
	}

	/**
	 * Creates the changes required for the given creature to move to targetBlock.
	 * 
	 * @param creature The creature.
	 * @param targetBlock The target location.
	 * @param isIdleMovement True if this movement is just idle and not one with a specific goal.
	 * @param isBlockSwimmable True if the creature is in a block where they can swim.
	 * @return The list of changes, potentially empty but never null.
	 */
	public static List<IMutationEntity<IMutableCreatureEntity>> moveToNextLocation(CreatureEntity creature, AbsoluteLocation targetBlock, boolean isIdleMovement, boolean isBlockSwimmable)
	{
		// We might need to jump, walk, or do nothing.
		// If the target is above us and we are on the ground, 
		List<IMutationEntity<IMutableCreatureEntity>> changes;
		EntityLocation startLocation = creature.location();
		if (targetBlock.z() > startLocation.z())
		{
			// We need to go up so see if we should jump, swim, or hope our momentum will get us there.
			if (SpatialHelpers.isBlockAligned(startLocation.z()))
			{
				// Jump.
				EntityChangeJump<IMutableCreatureEntity> jump = new EntityChangeJump<>();
				changes = List.of(jump);
			}
			else if (isBlockSwimmable)
			{
				// Swim.
				EntityChangeSwim<IMutableCreatureEntity> swim = new EntityChangeSwim<>();
				changes = List.of(swim);
			}
			else
			{
				// We will have to rely on our momentum to carry us there (or we are just failing to reach it).
				changes = List.of();
			}
		}
		else
		{
			// We might need to walk, coast in the air (which is the same as walking), or just fall.
			EntityLocation stepLocation = new EntityLocation(targetBlock.x(), targetBlock.y(), targetBlock.z());
			float distanceX = Math.abs(stepLocation.x() - startLocation.x());
			float distanceY = Math.abs(stepLocation.y() - startLocation.y());
			// We don't want to walk diagonally.
			float maxHorizontal = Math.max(distanceX, distanceY);
			float baseSpeed = EntityConstants.getBlocksPerSecondSpeed(creature.type());
			float speedMultipler = 1.0f;
			if (isIdleMovement)
			{
				// We want idle movements to be slower (half speed).
				speedMultipler = 0.5f;
			}
			if (maxHorizontal > FLOAT_THRESHOLD)
			{
				// We need to move horizontally so figure out which way.
				List<IMutationEntity<IMutableCreatureEntity>> list = new ArrayList<>();
				// We will move to the centre of the block to avoid edge rounding errors (also looks a bit better).
				float width = EntityConstants.getVolume(creature.type()).width();
				if (maxHorizontal == distanceX)
				{
					float targetX = ((float) stepLocation.x()) + (1.0f - width) / 2.0f;
					_moveByX(list, startLocation, baseSpeed, speedMultipler, targetX);
				}
				else
				{
					float targetY = ((float) stepLocation.y()) + (1.0f - width) / 2.0f;
					_moveByY(list, startLocation, baseSpeed, speedMultipler, targetY);
				}
				changes = list;
			}
			else
			{
				// We don't need horizontal movement so just do nothing.
				changes = List.of();
			}
		}
		return changes;
	}


	private static void _moveByX(List<IMutationEntity<IMutableCreatureEntity>> out_list, EntityLocation location, float baseSpeed, float speedMultipler, float targetX)
	{
		float moveX = targetX - location.x();
		float sign = Math.signum(moveX);
		float absoluteMove = Math.abs(moveX);
		float speed = baseSpeed * speedMultipler;
		float maxDistanceInOneMutation = EntityChangeMove.MAX_PER_STEP_SPEED_MULTIPLIER * speed;
		while (absoluteMove > FLOAT_THRESHOLD)
		{
			float oneAbs = Math.min(absoluteMove, maxDistanceInOneMutation);
			float oneMove = sign * oneAbs;
			float secondsToMove = (oneAbs / speed);
			// Round up to make sure that the mutation actually can fit in the time.
			long millisToMove = (long) Math.ceil(secondsToMove * 1000.0f);
			if (millisToMove > 0L)
			{
				EntityChangeMove.Direction direction = (oneMove > 0.0f)
						? EntityChangeMove.Direction.EAST
						: EntityChangeMove.Direction.WEST
				;
				EntityChangeMove<IMutableCreatureEntity> move = new EntityChangeMove<>(millisToMove, speedMultipler, direction);
				out_list.add(move);
			}
			absoluteMove -= oneAbs;
		}
	}

	private static void _moveByY(List<IMutationEntity<IMutableCreatureEntity>> out_list, EntityLocation location, float baseSpeed, float speedMultipler, float targetY)
	{
		float moveY = targetY - location.y();
		float sign = Math.signum(moveY);
		float absoluteMove = Math.abs(moveY);
		float speed = baseSpeed * speedMultipler;
		float maxDistanceInOneMutation = EntityChangeMove.MAX_PER_STEP_SPEED_MULTIPLIER * speed;
		while (absoluteMove > FLOAT_THRESHOLD)
		{
			float oneAbs = Math.min(absoluteMove, maxDistanceInOneMutation);
			float oneMove = sign * oneAbs;
			float secondsToMove = (oneAbs / speed);
			// Round up to make sure that the mutation actually can fit in the time.
			long millisToMove = (long) Math.ceil(secondsToMove * 1000.0f);
			if (millisToMove > 0L)
			{
				EntityChangeMove.Direction direction = (oneMove > 0.0f)
						? EntityChangeMove.Direction.NORTH
						: EntityChangeMove.Direction.SOUTH
				;
				EntityChangeMove<IMutableCreatureEntity> move = new EntityChangeMove<>(millisToMove, speedMultipler, direction);
				out_list.add(move);
			}
			absoluteMove -= oneAbs;
		}
	}
}
