package com.jeffdisher.october.block_set;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.IMutableBlockProxy;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Note that the cornerstone behaviour is also invoked via the periodic behaviour so this call is to do do so, inline,
 * while the periodic is just to verify it is still valid.
 */
public class BlockSetBehaviourCornerstone implements IBlockSetBehaviour
{
	@Override
	public void didSetBlock(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy proxy, Block replacedType)
	{
		env.composites.processCornerstoneUpdate(env, context, location, proxy);
	}
}
