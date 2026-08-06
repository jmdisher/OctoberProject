package com.jeffdisher.october.block_periodic;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.PortalHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.IMutableBlockProxy;
import com.jeffdisher.october.types.TickProcessingContext;


public class PeriodicBehaviourPortalKeystone implements IBlockPeriodicBehaviour
{
	@Override
	public void runPeriodic(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy newBlock)
	{
		PortalHelpers.handlePortalSurface(env, context, location, newBlock);
	}
}
