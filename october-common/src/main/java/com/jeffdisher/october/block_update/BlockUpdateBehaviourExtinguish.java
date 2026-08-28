package com.jeffdisher.october.block_update;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.logic.FireHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.IMutableBlockProxy;
import com.jeffdisher.october.types.TickProcessingContext;


public class BlockUpdateBehaviourExtinguish implements IBlockUpdateBehaviour
{
	@Override
	public void doRunUpdate(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy proxy)
	{
		// Check if this was burning and should be extinguished (happens when water flows on top).
		if (FireHelpers.shouldExtinguish(env, context, location, proxy))
		{
			byte flags = proxy.getFlags();
			flags = FlagsAspect.clear(flags, FlagsAspect.FLAG_BURNING);
			proxy.setFlags(flags);
		}
	}
}
