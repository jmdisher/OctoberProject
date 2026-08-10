package com.jeffdisher.october.block_periodic;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.PortalHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.IMutableBlockProxy;
import com.jeffdisher.october.types.TickProcessingContext;


public class PeriodicBehaviourPortalKeystone implements IBlockPeriodicBehaviour
{
	@Override
	public void doInitialRegistration(TickProcessingContext context, IMutableBlockProxy newBlock)
	{
		newBlock.requestFutureMutation(PeriodicBehaviourCompositeCornerstone.COMPOSITE_CHECK_FREQUENCY);
	}

	@Override
	public void runPeriodic(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy newBlock)
	{
		PortalHelpers.handlePortalSurface(env, context, location, newBlock);
		
		// NOTE:  This is redundant, in actual usage, since the portal keystone IS a composite cornerstone, but the code
		// shouldn't hide that assumption.  We run both handlers so this will change nothing.
		newBlock.requestFutureMutation(PeriodicBehaviourCompositeCornerstone.COMPOSITE_CHECK_FREQUENCY);
	}
}
