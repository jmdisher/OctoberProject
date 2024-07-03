package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.List;

import com.jeffdisher.october.mutations.EntityChangeJump;
import com.jeffdisher.october.mutations.EntityChangeMove;
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
	public static final float FLOAT_THRESHOLD = 0.01f;

	/**
	 * Creates a list of movements to centre this entity on their current block.  Note that this isn't "centre" in a
	 * "very centre" sense but just not flowing onto other blocks.
	 * Additionally, if the creature is already in the block, the directionalHint will be used to position it against a
	 * wall instead of the centre.
	 * 
	 * @param creature The creature.
	 * @param directionHint Used to decide if we should lean to a specific side of the block instead of the centre.
	 * @return The list of moves to make (empty if already in a good position).
	 */
	public static List<IMutationEntity<IMutableCreatureEntity>> centreOnCurrentBlock(CreatureEntity creature, AbsoluteLocation directionHint)
	{
		EntityLocation location = creature.location();
		float width = EntityConstants.getVolume(creature.type()).width();
		int baseX = (int) Math.floor(location.x());
		int baseY = (int) Math.floor(location.y());
		int edgeX = (int) Math.floor(location.x() + width);
		int edgeY = (int) Math.floor(location.y() + width);
		float speed = EntityConstants.getBlocksPerSecondSpeed(creature.type());
		
		List<IMutationEntity<IMutableCreatureEntity>> list = new ArrayList<>();
		int hintX = directionHint.x();
		if (baseX != edgeX)
		{
			// We need to move east/west.
			// Simplify this by finding what would be the "very centre" instead of the tedious math to do the bare minimum.
			float targetX = ((float) baseX) + (1.0f - width) / 2.0f;
			_moveByX(list, location, speed, targetX);
		}
		else if (hintX != baseX)
		{
			// We should lean into this wall.
			float targetX = (hintX > baseX)
					? ((float)hintX - width)
					: (float)baseX
			;
			_moveByX(list, location, speed, targetX);
		}
		int hintY = directionHint.y();
		if (baseY != edgeY)
		{
			// We need to move north/south.
			// Simplify this by finding what would be the "very centre" instead of the tedious math to do the bare minimum.
			float targetY = ((float) baseY) + (1.0f - width) / 2.0f;
			_moveByY(list, location, speed, targetY);
		}
		else if (hintY != baseY)
		{
			// We should lean into this wall.
			float targetY = (hintY > baseY)
					? ((float)hintY - width)
					: (float)baseY
			;
			_moveByY(list, location, speed, targetY);
		}
		return list;
	}

	/**
	 * Creates the changes required for the given creature to move to targetBlock.
	 * 
	 * @param creature The creature.
	 * @param targetBlock The target location.
	 * @param isIdleMovement True if this movement is just idle and not one with a specific goal.
	 * @return The list of changes, potentially empty but never null.
	 */
	public static List<IMutationEntity<IMutableCreatureEntity>> moveToNextLocation(CreatureEntity creature, AbsoluteLocation targetBlock, boolean isIdleMovement)
	{
		// We might need to jump, walk, or do nothing.
		// If the target is above us and we are on the ground, 
		List<IMutationEntity<IMutableCreatureEntity>> changes;
		EntityLocation startLocation = creature.location();
		if ((targetBlock.z() > startLocation.z()) && SpatialHelpers.isBlockAligned(startLocation.z()))
		{
			// Jump.
			EntityChangeJump<IMutableCreatureEntity> jump = new EntityChangeJump<>();
			changes = List.of(jump);
		}
		else
		{
			// We might need to walk, coast in the air (which is the same as walking), or just fall.
			EntityLocation stepLocation = new EntityLocation(targetBlock.x(), targetBlock.y(), targetBlock.z());
			float distanceX = Math.abs(stepLocation.x() - startLocation.x());
			float distanceY = Math.abs(stepLocation.y() - startLocation.y());
			// We don't want to walk diagonally.
			float maxHorizontal = Math.max(distanceX, distanceY);
			float speed = EntityConstants.getBlocksPerSecondSpeed(creature.type());
			if (isIdleMovement)
			{
				// We want idle movements to be slower (half speed).
				speed /= 2.0f;
			}
			float maxDistanceInOneMutation = EntityChangeMove.MAX_PER_STEP_SPEED_MULTIPLIER * speed;
			if (maxHorizontal > maxDistanceInOneMutation)
			{
				// We need to move horizontally so figure out which way.
				List<IMutationEntity<IMutableCreatureEntity>> list = new ArrayList<>();
				if (maxHorizontal == distanceX)
				{
					_moveByX(list, startLocation, speed, stepLocation.x());
				}
				else
				{
					_moveByY(list, startLocation, speed, stepLocation.y());
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


	private static void _moveByX(List<IMutationEntity<IMutableCreatureEntity>> out_list, EntityLocation location, float speed, float targetX)
	{
		float moveX = targetX - location.x();
		float sign = Math.signum(moveX);
		float absoluteMove = Math.abs(moveX);
		float maxDistanceInOneMutation = EntityChangeMove.MAX_PER_STEP_SPEED_MULTIPLIER * speed;
		while (absoluteMove > FLOAT_THRESHOLD)
		{
			float oneAbs = Math.min(absoluteMove, maxDistanceInOneMutation);
			float oneMove = sign * oneAbs;
			EntityChangeMove<IMutableCreatureEntity> move = new EntityChangeMove<>(speed, oneMove, 0.0f);
			out_list.add(move);
			absoluteMove -= oneAbs;
		}
	}

	private static void _moveByY(List<IMutationEntity<IMutableCreatureEntity>> out_list, EntityLocation location, float speed, float targetY)
	{
		float moveY = targetY - location.y();
		float sign = Math.signum(moveY);
		float absoluteMove = Math.abs(moveY);
		float maxDistanceInOneMutation = EntityChangeMove.MAX_PER_STEP_SPEED_MULTIPLIER * speed;
		while (absoluteMove > FLOAT_THRESHOLD)
		{
			float oneAbs = Math.min(absoluteMove, maxDistanceInOneMutation);
			float oneMove = sign * oneAbs;
			EntityChangeMove<IMutableCreatureEntity> move = new EntityChangeMove<>(speed, 0.0f, oneMove);
			out_list.add(move);
			absoluteMove -= oneAbs;
		}
	}
}
