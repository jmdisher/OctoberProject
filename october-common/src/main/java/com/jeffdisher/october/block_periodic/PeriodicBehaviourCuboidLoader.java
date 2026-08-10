package com.jeffdisher.october.block_periodic;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.IMutableBlockProxy;
import com.jeffdisher.october.types.TickProcessingContext;


public class PeriodicBehaviourCuboidLoader implements IBlockPeriodicBehaviour
{
	@Override
	public void doInitialRegistration(TickProcessingContext context, IMutableBlockProxy newBlock)
	{
		newBlock.requestFutureMutation(MiscConstants.CUBOID_KEEP_ALIVE_MILLIS - context.millisPerTick);
	}

	@Override
	public void runPeriodic(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy newBlock)
	{
		byte flags = newBlock.getFlags();
		boolean isActive = FlagsAspect.isSet(flags, FlagsAspect.FLAG_ACTIVE);
		
		if (isActive)
		{
			context.keepAliveSink.accept(location.getCuboidAddress());
		}
		newBlock.requestFutureMutation(MiscConstants.CUBOID_KEEP_ALIVE_MILLIS - context.millisPerTick);
	}
}
