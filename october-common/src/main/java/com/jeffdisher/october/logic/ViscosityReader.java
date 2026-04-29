package com.jeffdisher.october.logic;

import java.util.List;
import java.util.Map;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * A utility helper to resolve viscosity from a BlockProxy (since it requires a few checks we don't want to duplicate).
 * This is an instance type, instead of merely a static helper, so that it can be passed into utilities which need it.
 */
public class ViscosityReader
{
	private final Environment _env;
	private final TickProcessingContext.IBlockFetcher _blockLookup;

	public ViscosityReader(Environment env, TickProcessingContext.IBlockFetcher blockLookup)
	{
		_env = env;
		_blockLookup = blockLookup;
	}

	public float getMaxStillViscosityInVolume(EntityLocation base, EntityVolume volume)
	{
		List<AbsoluteLocation> locations = VolumeIterator.getAllInVolume(base, volume);
		Map<AbsoluteLocation, BlockProxy> map = _blockLookup.readBlockBatch(locations);
		
		float viscosity;
		if (map.size() < locations.size())
		{
			// We were missing something so just default to saying full viscosity.
			viscosity = 1.0f;
		}
		else
		{
			// In this case, we are always just considering the standing viscosity, not falling from above.
			boolean fromAbove = false;
			viscosity = 0.0f;
			for (BlockProxy proxy : map.values())
			{
				float one = _env.blocks.getViscosityFraction(proxy.getBlock(), FlagsAspect.isSet(proxy.getFlags(), FlagsAspect.FLAG_ACTIVE), fromAbove);
				viscosity = Math.max(one, viscosity);
			}
		}
		return viscosity;
	}

	public boolean isSolidBlockInVolume(EntityLocation base, EntityVolume volume, boolean fromAbove)
	{
		List<AbsoluteLocation> locations = VolumeIterator.getAllInVolume(base, volume);
		Map<AbsoluteLocation, BlockProxy> map = _blockLookup.readBlockBatch(locations);
		
		boolean isSolid;
		if (map.size() < locations.size())
		{
			// We were missing something so just default to saying it is solid.
			isSolid = true;
		}
		else
		{
			isSolid = false;
			for (BlockProxy proxy : map.values())
			{
				float one = _env.blocks.getViscosityFraction(proxy.getBlock(), FlagsAspect.isSet(proxy.getFlags(), FlagsAspect.FLAG_ACTIVE), fromAbove);
				if (1.0f == one)
				{
					isSolid = true;
					break;
				}
			}
		}
		return isSolid;
	}
}
