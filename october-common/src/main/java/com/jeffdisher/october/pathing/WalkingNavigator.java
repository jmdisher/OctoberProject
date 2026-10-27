package com.jeffdisher.october.pathing;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.logic.ViscosityReader;
import com.jeffdisher.october.subactions.EntitySubActionStepUp;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.SubBlock;
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
	/**
	 * Due to sub-block collision, we will check if the solid ground is 1/4 the way below the current location.
	 */
	public static final float BELOW_Z_OFFSET = -1.0f / SubBlock.SUB_BLOCK_EDGE_FLOAT;
	/**
	 * When climbing stairs, we step back in order to not collide with the high part of the step.  We try to align our
	 * positive edge with this offset.
	 */
	public static final float POSITIVE_STEP_OFFSET = 0.49f;


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
			boolean isOnSolidBlock = (BlockAspect.VISOSITY_FLOAT_SOLID == _findMaxViscosityFromBase(entityBase.getRelative(0.0f, 0.0f, BELOW_Z_OFFSET)));
			result = new BasicSpot(entityBase.getBlockLocation(), 0.0f, isOnSolidBlock, viscosity);
		}
		return result;
	}

	@Override
	public BasicSpot enterSpot(BasicSpot from, AbsoluteLocation location)
	{
		// This helper never receives null.
		Assert.assertTrue(null != from);
		
		// The first thing we check if this is a horizontal movement since some of them are not allowed, no matter the collision.
		AbsoluteLocation previousStep = from.location;
		int deltaX = location.x() - previousStep.x();
		int deltaY = location.y() - previousStep.y();
		boolean wasXyStep = (previousStep.z() == location.z());
		boolean isAllowed = true;
		if (wasXyStep)
		{
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
		}
		
		// Now, we will check if we can move into this block without colliding with anything solid.
		EntityLocation realLocation = location.toEntityLocation();
		float viscosity;
		if (isAllowed)
		{
			viscosity = _findMaxViscosityFromBase(realLocation);
			isAllowed = (viscosity < BlockAspect.VISOSITY_FLOAT_SOLID);
			
			// If we aren't allowed here, what happens if we take a step up.
			if (!isAllowed && wasXyStep)
			{
				// Note that this could be a slab or a stair so we want to consider sliding _into_ the block from the other side.
				float x = realLocation.x();
				float y = realLocation.y();
				if (deltaX < 0)
				{
					// We are walking toward the West so just put the location at +0.5f.
					x += 0.5f;
				}
				else if (deltaX > 0)
				{
					// We are travelling to the East so back off to fit in 0.49f.
					x = _shavePositive(x);
				}
				else if (deltaY < 0)
				{
					// We are walking toward the South so just put the location at +0.5f.
					y += 0.5f;
				}
				else if (deltaY > 0)
				{
					// We are travelling to the North so back off to fit in 0.49f.
					y = _shavePositive(y);
				}
				
				EntityLocation stepLocation = new EntityLocation(x
					, y
					, realLocation.z() + EntitySubActionStepUp.MAX_STEP_HEIGHT
				);
				float stepViscosity = _findMaxViscosityFromBase(stepLocation);
				isAllowed = (stepViscosity < BlockAspect.VISOSITY_FLOAT_SOLID);
				if (isAllowed)
				{
					realLocation = stepLocation;
					viscosity = stepViscosity;
				}
			}
		}
		else
		{
			viscosity = 1.0f;
		}
		
		boolean isOnSolidBlock;
		float cost;
		if (isAllowed)
		{
			// First, we will apply our checks around directions.
			if (wasXyStep)
			{
				cost = COST_STEP_FLAT;
			}
			else
			{
				// We are jumping or falling so make sure that is legal and determine the cost.
				if (location.z() > previousStep.z())
				{
					// This is a jump so make sure that the previous location was on solid block or swimmable, but non-swimmable MUST be zero viscosity.
					boolean wasOnSolidBlock = from.isOnGround;
					float previousBlockViscosity = from.viscosity;
					boolean wasSwimmable = _isSwimmable(previousBlockViscosity);
					
					isAllowed = ((wasOnSolidBlock && (BlockAspect.VISOSITY_FLOAT_ZERO == previousBlockViscosity)) || wasSwimmable);
					cost = COST_JUMP;
				}
				else
				{
					// We will assume a fall is always ok.
					cost = COST_FALL;
				}
			}
			
			if (isAllowed)
			{
				isOnSolidBlock = (BlockAspect.VISOSITY_FLOAT_SOLID == _findMaxViscosityFromBase(realLocation.getRelative(0.0f, 0.0f, BELOW_Z_OFFSET)));
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

	private float _shavePositive(float f)
	{
		float edge = f + _volume.width();
		float fraction = SpatialHelpers.getPositiveFractionalComponent(edge);
		if (fraction > POSITIVE_STEP_OFFSET)
		{
			float shave = fraction - POSITIVE_STEP_OFFSET;
			f -= shave;
		}
		return f;
	}
}
