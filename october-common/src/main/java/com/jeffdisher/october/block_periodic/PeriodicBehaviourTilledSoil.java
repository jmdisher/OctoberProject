package com.jeffdisher.october.block_periodic;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.PlantHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.IMutableBlockProxy;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Periodically updates the hydration state of tilled soil.
 */
public class PeriodicBehaviourTilledSoil implements IBlockPeriodicBehaviour
{
	/**
	 * We will try every 2 minutes.
	 */
	public static final long MILLIS_BETWEEN_CHECK_CALLS = 2L * 60L * 1000L;

	@Override
	public void doInitialRegistration(TickProcessingContext context, IMutableBlockProxy newBlock)
	{
		newBlock.requestFutureMutation(MILLIS_BETWEEN_CHECK_CALLS);
	}

	@Override
	public void runPeriodic(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy newBlock)
	{
		PlantHelpers.runSoilHydrationPeriodic(env, context, location, newBlock);
	}
}
