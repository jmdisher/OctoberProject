package com.jeffdisher.october.block_periodic;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.IMutableBlockProxy;
import com.jeffdisher.october.types.TickProcessingContext;


public class PeriodicBehaviourCompositeCornerstone implements IBlockPeriodicBehaviour
{
	@Override
	public void runPeriodic(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy newBlock)
	{
		// See if we need to change the state of the composite.
		// Note that this implicitly calls requestFutureMutation (called via multiple paths).
		env.composites.processCornerstoneUpdate(env, context, location, newBlock);
	}
}
