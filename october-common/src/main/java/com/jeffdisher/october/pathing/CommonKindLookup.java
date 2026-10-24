package com.jeffdisher.october.pathing;

import java.util.function.Function;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.ViscosityReader;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * This common implementation of the BlockKind lookup routine is kept here so it can be more easily tested or considered
 * in isolation.  It was originally extracted from CreatureLogic.
 */
public class CommonKindLookup implements Function<AbsoluteLocation, WalkingNavigator.BlockKind>
{
	private final TickProcessingContext.IBlockFetcher _previousBlockLookUp;
	private final EntityVolume _volume;

	public CommonKindLookup(TickProcessingContext.IBlockFetcher previousBlockLookUp, EntityVolume volume)
	{
		_previousBlockLookUp = previousBlockLookUp;
		_volume = volume;
	}

	@Override
	public WalkingNavigator.BlockKind apply(AbsoluteLocation location)
	{
		// We will apply the offset bias since this entity might be straddling blocks in a way which spills across more than usual.
		EntityLocation blockBase = location.toEntityLocation();
		
		return _findKindFromBase(blockBase);
	}

	public WalkingNavigator.BlockKind checkFromBase(EntityLocation entityBase)
	{
		return _findKindFromBase(entityBase);
	}


	private WalkingNavigator.BlockKind _findKindFromBase(EntityLocation blockBase)
	{
		// We just use the common ViscosityReader and convert those results to these high-level thresholds.
		ViscosityReader reader = new ViscosityReader(Environment.getShared(), _previousBlockLookUp);
		float maxViscosity = reader.getMaxStillViscosityInVolume(blockBase, _volume);
		
		WalkingNavigator.BlockKind kind;
		if (BlockAspect.VISOSITY_FLOAT_SOLID == maxViscosity)
		{
			kind = WalkingNavigator.BlockKind.SOLID;
		}
		else if (maxViscosity >= BlockAspect.VISOSITY_FLOAT_SWIMMABLE)
		{
			kind = WalkingNavigator.BlockKind.SWIMMABLE;
		}
		else
		{
			kind = WalkingNavigator.BlockKind.WALKABLE;
		}
		return kind;
	}
}
