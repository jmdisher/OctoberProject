package com.jeffdisher.october.block_set;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.FireHelpers;
import com.jeffdisher.october.mutations.MutationBlockStartFire;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.IMutableBlockProxy;
import com.jeffdisher.october.types.TickProcessingContext;


public class BlockSetBehaviourFlammable implements IBlockSetBehaviour
{
	@Override
	public void didSetBlock(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy proxy, Block replacedType)
	{
		// If this block changed into a flammable type, see if it should receive an ignition mutation.
		if (!env.blocks.isFlammable(replacedType) && FireHelpers.canIgnite(env, context, location, proxy))
		{
			MutationBlockStartFire startFire = new MutationBlockStartFire(location);
			context.mutationSink.future(startFire, MutationBlockStartFire.IGNITION_DELAY_MILLIS);
		}
	}
}
