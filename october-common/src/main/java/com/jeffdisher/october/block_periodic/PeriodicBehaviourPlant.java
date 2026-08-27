package com.jeffdisher.october.block_periodic;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.PlantHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.IMutableBlockProxy;
import com.jeffdisher.october.types.TickProcessingContext;


public class PeriodicBehaviourPlant implements IBlockPeriodicBehaviour
{
	public static final long MILLIS_BETWEEN_GROWTH_CALLS = 10_000L;

	@Override
	public void doInitialRegistration(TickProcessingContext context, IMutableBlockProxy newBlock)
	{
		newBlock.requestFutureMutation(MILLIS_BETWEEN_GROWTH_CALLS);
	}

	@Override
	public void runPeriodic(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy newBlock)
	{
		// Run the growth attempt (this will change newBlock to advance growth).
		PlantHelpers.runPlantPeriodic(env, context, location, newBlock);
	}
}
