package com.jeffdisher.october.pathing;

import java.util.function.Function;

import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.EntityLocation;


/**
 * An implementation of the INavigator interface which provides path finding support for creatures which walk normally.
 */
public class WalkingNavigator implements PathFinder.INavigator<BasicSpot>
{
	/**
	 * Falling 1 block will still be considered as cost, but a very small one.
	 */
	public static final float COST_FALL = 0.5f;
	/**
	 * Jumping up a block should be considered expensive.
	 */
	public static final float COST_JUMP = 1.5f;
	/**
	 * Walking on flat ground will be the baseline action.
	 */
	public static final float COST_STEP_FLAT = 1.0f;


	private final Function<AbsoluteLocation, BlockKind> _blockKind;
	private final float _limit;
	private final boolean _allowWest;
	private final boolean _allowEast;
	private final boolean _allowSouth;
	private final boolean _allowNorth;

	public WalkingNavigator(Function<AbsoluteLocation, BlockKind> blockKind
		, EntityLocation source
		, EntityLocation target
		, float existingLimit
		, boolean allowWest
		, boolean allowEast
		, boolean allowSouth
		, boolean allowNorth
	)
	{
		_blockKind = blockKind;
		_limit = _getStartingLimit(source, target, existingLimit);
		_allowWest = allowWest;
		_allowEast = allowEast;
		_allowSouth = allowSouth;
		_allowNorth = allowNorth;
	}

	@Override
	public BasicSpot enterSpot(BasicSpot from, AbsoluteLocation location)
	{
		// We will only bother trying this if the current location is not solid.
		BlockKind locationKind = _blockKind.apply(location);
		boolean isAllowed = (BlockKind.SOLID != locationKind);
		float cost = 0.0f;
		if (isAllowed && (null != from))
		{
			// First, we will apply our checks around directions.
			AbsoluteLocation previousStep = from.location;
			boolean wasXyStep = (previousStep.z() == location.z());
			if (wasXyStep)
			{
				int deltaX = location.x() - previousStep.x();
				int deltaY = location.y() - previousStep.y();
				if (deltaX < 0)
				{
					isAllowed = _allowWest;
				}
				else if (deltaX > 0)
				{
					isAllowed = _allowEast;
				}
				else if (deltaY < 0)
				{
					isAllowed = _allowSouth;
				}
				else if (deltaY > 0)
				{
					isAllowed = _allowNorth;
				}
				cost = COST_STEP_FLAT;
			}
			else
			{
				// We aren't jumping or falling so make sure that is legal and determine the cost.
				if (location.z() > previousStep.z())
				{
					// This is a jump so make sure that the previous location was on solid block or swimmable.
					AbsoluteLocation previousDown = previousStep.getRelative(0, 0, -1);
					boolean wasOnSolidBlock = (BlockKind.SOLID == _blockKind.apply(previousDown));
					boolean wasSwimmable = (BlockKind.SWIMMABLE == _blockKind.apply(previousStep));
					
					isAllowed = (wasOnSolidBlock || wasSwimmable);
					cost = COST_JUMP;
				}
				else
				{
					// We will assume a fall is always ok.
					cost = COST_FALL;
				}
			}
			
			if (wasXyStep && isAllowed)
			{
				// Make sure that we aren't flying.
				AbsoluteLocation down = location.getRelative(0, 0, -1);
				boolean isOnSolidBlock = (BlockKind.SOLID == _blockKind.apply(down));
				boolean isSwimmable = (BlockKind.SWIMMABLE == locationKind);
				
				AbsoluteLocation previousDown = previousStep.getRelative(0, 0, -1);
				boolean wasOnSolidBlock = (BlockKind.SOLID == _blockKind.apply(previousDown));
				boolean wasSwimmable = (BlockKind.SWIMMABLE == _blockKind.apply(previousStep));
				
				// One of these needs to be solid/swimmable or we couldn't move between them.
				isAllowed = (isOnSolidBlock || isSwimmable) || (wasOnSolidBlock || wasSwimmable);
			}
		}
		
		BasicSpot result = null;
		if (isAllowed)
		{
			float previousDistance = (null != from)
				? from.distance
				: 0.0f
			;
			float distance = previousDistance + cost;
			if (distance <= _limit)
			{
				result = new BasicSpot(location, distance);
			}
		}
		return result;
	}


	private static float _getStartingLimit(EntityLocation source, EntityLocation target, float existingLimit)
	{
		float distance;
		if (null != target)
		{
			// We need to translate the source into the relative location of the destination.
			EntityLocation sourceOffset = source.getOffsetIntoBlock();
			EntityLocation targetOffset = target.getOffsetIntoBlock();
			distance = (targetOffset.x() - sourceOffset.x())
				+ (targetOffset.y() - sourceOffset.y())
				+ (targetOffset.z() - sourceOffset.z())
			;
		}
		else
		{
			distance = 0.0f;
		}
		return existingLimit - distance;
	}


	/**
	 * An enum describing the block kinds which are relevant for basic walking navigation.
	 */
	public static enum BlockKind
	{
		/**
		 * A block which can be traversed easily.
		 */
		WALKABLE,
		/**
		 * A block which cannot be passed through and which supports an entity.
		 */
		SOLID,
		/**
		 * A block which is "WALKABLE" but also allows swimming (and is not breathable).
		 */
		SWIMMABLE,
	}
}
