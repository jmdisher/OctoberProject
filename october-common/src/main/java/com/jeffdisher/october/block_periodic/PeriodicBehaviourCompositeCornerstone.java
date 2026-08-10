package com.jeffdisher.october.block_periodic;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.IMutableBlockProxy;
import com.jeffdisher.october.types.TickProcessingContext;


public class PeriodicBehaviourCompositeCornerstone implements IBlockPeriodicBehaviour
{
	/**
	 * We will poll the composite cornerstone every 5 seconds to see if it should change its active state.
	 * Ideally, this would be replaced with an event-based solution but these may not be in the same cuboid so it would
	 * require some kind of "on load" event for other blocks in the composition which will complicate the system a lot
	 * for something which is otherwise quite low-cost, even if hack-ish with this 5-second delay.
	 */
	public static final long COMPOSITE_CHECK_FREQUENCY = 5_000L;

	@Override
	public void doInitialRegistration(TickProcessingContext context, IMutableBlockProxy newBlock)
	{
		newBlock.requestFutureMutation(COMPOSITE_CHECK_FREQUENCY);
	}

	@Override
	public void runPeriodic(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy newBlock)
	{
		// See if we need to change the state of the composite.
		env.composites.processCornerstoneUpdate(env, context, location, newBlock);
		newBlock.requestFutureMutation(COMPOSITE_CHECK_FREQUENCY);
	}
}
