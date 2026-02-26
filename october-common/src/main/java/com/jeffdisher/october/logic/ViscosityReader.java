package com.jeffdisher.october.logic;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.types.AbsoluteLocation;
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

	public float getViscosityFraction(AbsoluteLocation location, boolean fromAbove)
	{
		return _getViscosity(location, fromAbove);
	}

	public float getInverseViscosity(AbsoluteLocation location, boolean fromAbove)
	{
		return 1.0f - _getViscosity(location, fromAbove);
	}


	private float _getViscosity(AbsoluteLocation location, boolean fromAbove)
	{
		BlockProxy proxy = _blockLookup.readBlock(location);
		float viscosity;
		if (null != proxy)
		{
			// Find the viscosity based on block type.
			viscosity = _env.blocks.getViscosityFraction(proxy.getBlock(), FlagsAspect.isSet(proxy.getFlags(), FlagsAspect.FLAG_ACTIVE), fromAbove);
		}
		else
		{
			// This is missing so we will just treat it as a solid block.
			viscosity = 1.0f;
		}
		return viscosity;
	}
}
