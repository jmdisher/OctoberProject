package com.jeffdisher.october.block_periodic;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.HopperHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.IMutableBlockProxy;
import com.jeffdisher.october.types.TickProcessingContext;


public class PeriodicBehaviourHopper implements IBlockPeriodicBehaviour
{
	public static final long MILLIS_BETWEEN_HOPPER_CALLS = 1_000L;

	@Override
	public void runPeriodic(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy newBlock)
	{
		HopperHelpers.tryProcessHopper(context, location, newBlock);
		newBlock.requestFutureMutation(MILLIS_BETWEEN_HOPPER_CALLS);
	}
}
