package com.jeffdisher.october.block_periodic;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.IMutableBlockProxy;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Called by MutationBlockPeriodic when a periodic mutation should be applied.
 * The implementation chosen in based on the current block type when the mutation started running.
 * The implementation is responsible for whatever action should be taken by that block type, periodically, as well as
 * rescheduling the next periodic mutation.
 */
public interface IBlockPeriodicBehaviour
{
	public void runPeriodic(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy newBlock);
}
