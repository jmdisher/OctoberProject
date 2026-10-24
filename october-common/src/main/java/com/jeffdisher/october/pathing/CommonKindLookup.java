package com.jeffdisher.october.pathing;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.logic.VolumeIterator;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * This common implementation of the BlockKind lookup routine is kept here so it can be more easily tested or considered
 * in isolation.  It was originally extracted from CreatureLogic.
 * TODO:  This implementation currently has a limitation where it won't shift over in a block to avoid colliding with an
 * adjacent block.
 */
public class CommonKindLookup implements Function<AbsoluteLocation, WalkingNavigator.BlockKind>
{
	private final BlockAspect _blocks;
	private final TickProcessingContext.IBlockFetcher _previousBlockLookUp;
	private final EntityLocation _offsetBias;
	private final EntityVolume _volume;

	public CommonKindLookup(TickProcessingContext.IBlockFetcher previousBlockLookUp, EntityLocation startLocation, EntityVolume volume)
	{
		_blocks = Environment.getShared().blocks;
		_previousBlockLookUp = previousBlockLookUp;
		_offsetBias = startLocation.getOffsetIntoBlock();
		_volume = volume;
	}

	@Override
	public WalkingNavigator.BlockKind apply(AbsoluteLocation location)
	{
		// We default to assuming that the path is walkable, unless we find anything more interesting.
		WalkingNavigator.BlockKind kind = WalkingNavigator.BlockKind.WALKABLE;
		
		// We will apply the offset bias since this entity might be straddling blocks in a way which spills across more than usual.
		EntityLocation blockBase = location.toEntityLocation();
		EntityLocation adjustedBase = blockBase.getRelativeForLocation(_offsetBias);
		List<AbsoluteLocation> interior = VolumeIterator.getAllInVolume(adjustedBase, _volume);
		Map<AbsoluteLocation, BlockProxy> proxies = _previousBlockLookUp.readBlockBatch(interior);
		for (AbsoluteLocation loc : interior)
		{
			WalkingNavigator.BlockKind sub = _singleBlock(proxies.get(loc));
			if (WalkingNavigator.BlockKind.SOLID == sub)
			{
				// We saturate to solid so break if we hit this.
				kind = sub;
				break;
			}
			else if (WalkingNavigator.BlockKind.WALKABLE == kind)
			{
				// If we are still walkable, switch to whatever this is.
				kind = sub;
			}
		}
		return kind;
	}

	private WalkingNavigator.BlockKind _singleBlock(BlockProxy proxy)
	{
		WalkingNavigator.BlockKind kind;
		if (null == proxy)
		{
			// If we can't find the proxy, we will treat this as solid.
			kind = WalkingNavigator.BlockKind.SOLID;
		}
		else
		{
			Block block = proxy.getBlock();
			boolean isActive = FlagsAspect.isSet(proxy.getFlags(), FlagsAspect.FLAG_ACTIVE);
			if (_blocks.isSolid(block, isActive))
			{
				kind = WalkingNavigator.BlockKind.SOLID;
			}
			else if (_blocks.canSwimInBlock(block, isActive))
			{
				kind = WalkingNavigator.BlockKind.SWIMMABLE;
			}
			else
			{
				kind = WalkingNavigator.BlockKind.WALKABLE;
			}
		}
		return kind;
	}
}
