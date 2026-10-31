package com.jeffdisher.october.pathing;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.ViscosityReader;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


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


	private final TickProcessingContext.IBlockFetcher _previousBlockLookUp;
	private final EntityVolume _volume;
	private final float _limit;
	private final boolean _allowWest;
	private final boolean _allowEast;
	private final boolean _allowSouth;
	private final boolean _allowNorth;

	public WalkingNavigator(TickProcessingContext.IBlockFetcher previousBlockLookUp
		, EntityVolume volume
		, float limit
		, boolean allowWest
		, boolean allowEast
		, boolean allowSouth
		, boolean allowNorth
	)
	{
		_previousBlockLookUp = previousBlockLookUp;
		_volume = volume;
		_limit = limit;
		_allowWest = allowWest;
		_allowEast = allowEast;
		_allowSouth = allowSouth;
		_allowNorth = allowNorth;
	}

	@Override
	public BasicSpot startSpot(EntityLocation entityBase)
	{
		// We can start here so long as it isn't stuck in a block.
		float viscosity = _findMaxViscosityFromBase(entityBase);
		boolean isAllowed = (viscosity < BlockAspect.VISOSITY_FLOAT_SOLID);
		
		BasicSpot result = null;
		if (isAllowed)
		{
			PathLocation baseLocation = PathLocation.forEntityBase(entityBase);
			PathLocation down = baseLocation.getRelative(0, 0, -1);
			boolean isOnSolidBlock = (BlockAspect.VISOSITY_FLOAT_SOLID == _findMaxViscosityFromBlock(down));
			result = new BasicSpot(baseLocation, 0.0f, isOnSolidBlock, viscosity);
		}
		return result;
	}

	@Override
	public BasicSpot enterSpot(BasicSpot from, PathLocation location)
	{
		// This helper never receives null.
		Assert.assertTrue(null != from);
		
		// We will only bother trying this if the current location is not solid.
		float viscosity = _findMaxViscosityFromBlock(location);
		boolean isAllowed = (viscosity < BlockAspect.VISOSITY_FLOAT_SOLID);
		boolean isOnSolidBlock;
		float cost;
		if (isAllowed)
		{
			// First, we will apply our checks around directions.
			PathLocation previousStep = from.location;
			FacingDirection direction = PathLocation.adjacentDirection(previousStep, location);
			boolean wasXyStep;
			switch(direction)
			{
			case WEST:
				isAllowed = _allowWest;
				wasXyStep = true;
				cost = COST_STEP_FLAT;
				break;
			case EAST:
				isAllowed = _allowEast;
				wasXyStep = true;
				cost = COST_STEP_FLAT;
				break;
			case SOUTH:
				isAllowed = _allowSouth;
				wasXyStep = true;
				cost = COST_STEP_FLAT;
				break;
			case NORTH:
				isAllowed = _allowNorth;
				wasXyStep = true;
				cost = COST_STEP_FLAT;
				break;
			case UP:
				// This is a jump so make sure that the previous location was on solid block or swimmable, but non-swimmable MUST be zero viscosity.
				boolean wasOnSolidBlock = from.isOnGround;
				float previousBlockViscosity = from.viscosity;
				boolean wasSwimmable = _isSwimmable(previousBlockViscosity);
				
				isAllowed = ((wasOnSolidBlock && (BlockAspect.VISOSITY_FLOAT_ZERO == previousBlockViscosity)) || wasSwimmable);
				wasXyStep = false;
				cost = COST_JUMP;
				break;
			case DOWN:
				// We will assume a fall is always ok.
				wasXyStep = false;
				cost = COST_FALL;
				break;
			default:
				throw Assert.unreachable();
			}
			
			if (isAllowed)
			{
				PathLocation down = location.getRelative(0, 0, -1);
				isOnSolidBlock = (BlockAspect.VISOSITY_FLOAT_SOLID == _findMaxViscosityFromBlock(down));
			}
			else
			{
				isOnSolidBlock = false;
			}
			if (wasXyStep && isAllowed)
			{
				boolean isSwimmable = _isSwimmable(viscosity);
				boolean wasOnSolidBlock = from.isOnGround;
				boolean wasSwimmable = _isSwimmable(from.viscosity);
				
				// One of these needs to be solid/swimmable or we couldn't move between them.
				isAllowed = (isOnSolidBlock || isSwimmable) || (wasOnSolidBlock || wasSwimmable);
			}
		}
		else
		{
			isOnSolidBlock = false;
			cost = 0.0f;
		}
		
		BasicSpot result = null;
		if (isAllowed)
		{
			float previousDistance = from.distance;
			float distance = previousDistance + cost;
			if (distance <= _limit)
			{
				result = new BasicSpot(location, distance, isOnSolidBlock, viscosity);
			}
		}
		return result;
	}

	/**
	 * A method which allows callers to rely on the same viscosity check logic the receiver will use, internally.  This
	 * is primarily to avoid needing to pass a duplicated ViscosityReader around with the receiver.
	 * 
	 * @param base The base location of the entity.
	 * @return The maximum viscosity of the entity the receiver checks based at the given base location.
	 */
	public float getViscosityAtBase(EntityLocation base)
	{
		return _findMaxViscosityFromBase(base);
	}


	private float _findMaxViscosityFromBlock(PathLocation block)
	{
		return _findMaxViscosityFromBase(block.getBaseLocation());
	}

	private float _findMaxViscosityFromBase(EntityLocation blockBase)
	{
		// We just use the common ViscosityReader and convert those results to these high-level thresholds.
		ViscosityReader reader = new ViscosityReader(Environment.getShared(), _previousBlockLookUp);
		return reader.getMaxStillViscosityInVolume(blockBase, _volume);
	}

	private boolean _isSwimmable(float viscosity)
	{
		return (viscosity >= BlockAspect.VISOSITY_FLOAT_SWIMMABLE) && (viscosity < BlockAspect.VISOSITY_FLOAT_SOLID);
	}
}
