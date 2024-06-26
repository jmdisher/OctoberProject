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
	 * 
	 * @param creature The creature.
	 * @return The list of moves to make (empty if already in a good position).
	 */
	public static List<IMutationEntity<IMutableCreatureEntity>> centreOnCurrentBlock(CreatureEntity creature)
	{
		EntityLocation location = creature.location();
		float width = EntityConstants.getVolume(creature).width();
		int baseX = (int) Math.floor(location.x());
		int baseY = (int) Math.floor(location.y());
		int edgeX = (int) Math.floor(location.x() + width);
		int edgeY = (int) Math.floor(location.y() + width);
		float speed = EntityConstants.getBlocksPerSecondSpeed(creature);
		
		List<IMutationEntity<IMutableCreatureEntity>> list = new ArrayList<>();
		if (baseX != edgeX)
		{
			// We need to move east/west.
			// Simplify this by finding what would be the "very centre" instead of the tedious math to do the bare minimum.
			float targetX = ((float) baseX) + (1.0f - width) / 2.0f;
			_moveByX(list, location, speed, targetX);
		}
		if (baseY != edgeY)
		{
			// We need to move north/south.
			// Simplify this by finding what would be the "very centre" instead of the tedious math to do the bare minimum.
			float targetY = ((float) baseY) + (1.0f - width) / 2.0f;
			_moveByY(list, location, speed, targetY);
		}
		return list;
	}

	/**
	 * Creates the changes required for the given creature to move to targetBlock.
	 * 
	 * @param creature The creature.
	 * @param targetBlock The target location.
	 * @return The list of changes, potentially empty but never null.
	 */
	public static List<IMutationEntity<IMutableCreatureEntity>> moveToNextLocation(CreatureEntity creature, AbsoluteLocation targetBlock)
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
			float speed = EntityConstants.getBlocksPerSecondSpeed(creature);
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
		EntityLocation tempLocation = location;
		float maxDistanceInOneMutation = EntityChangeMove.MAX_PER_STEP_SPEED_MULTIPLIER * speed;
		while (absoluteMove > FLOAT_THRESHOLD)
		{
			float oneAbs = Math.min(absoluteMove, maxDistanceInOneMutation);
			float oneMove = sign * oneAbs;
			EntityChangeMove<IMutableCreatureEntity> move = new EntityChangeMove<>(tempLocation, speed, oneMove, 0.0f);
			out_list.add(move);
			tempLocation = new EntityLocation(tempLocation.x() + oneMove, tempLocation.y(), tempLocation.z());
			absoluteMove -= oneAbs;
		}
	}

	private static void _moveByY(List<IMutationEntity<IMutableCreatureEntity>> out_list, EntityLocation location, float speed, float targetY)
	{
		float moveY = targetY - location.y();
		float sign = Math.signum(moveY);
		float absoluteMove = Math.abs(moveY);
		EntityLocation tempLocation = location;
		float maxDistanceInOneMutation = EntityChangeMove.MAX_PER_STEP_SPEED_MULTIPLIER * speed;
		while (absoluteMove > FLOAT_THRESHOLD)
		{
			float oneAbs = Math.min(absoluteMove, maxDistanceInOneMutation);
			float oneMove = sign * oneAbs;
			EntityChangeMove<IMutableCreatureEntity> move = new EntityChangeMove<>(tempLocation, speed, 0.0f, oneMove);
			out_list.add(move);
			tempLocation = new EntityLocation(tempLocation.x(), tempLocation.y() + oneMove, tempLocation.z());
			absoluteMove -= oneAbs;
		}
	}
}
