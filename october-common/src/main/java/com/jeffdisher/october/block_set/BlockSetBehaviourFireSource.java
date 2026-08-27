package com.jeffdisher.october.block_set;

import java.util.List;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.FireHelpers;
import com.jeffdisher.october.mutations.MutationBlockStartFire;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.IMutableBlockProxy;
import com.jeffdisher.october.types.TickProcessingContext;


public class BlockSetBehaviourFireSource implements IBlockSetBehaviour
{
	@Override
	public void didSetBlock(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy proxy, Block replacedType)
	{
		// Only start a fire if the block wasn't already a source.
		if (!env.blocks.isFireSource(replacedType))
		{
			List<AbsoluteLocation> flammable = FireHelpers.findFlammableNeighbours(env, context, location);
			for (AbsoluteLocation neighour : flammable)
			{
				MutationBlockStartFire startFire = new MutationBlockStartFire(neighour);
				context.mutationSink.future(startFire, MutationBlockStartFire.IGNITION_DELAY_MILLIS);
			}
		}
	}
}
