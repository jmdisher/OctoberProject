package com.jeffdisher.october.block_set;

import java.util.List;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.block_periodic.IBlockPeriodicBehaviour;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.IMutableBlockProxy;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Wraps the IBlockPeriodicBehaviour instance for when the block is set.
 */
public class BlockSetBehaviourPeriodicWrapper implements IBlockSetBehaviour
{
	private final List<IBlockPeriodicBehaviour> _periodicList;

	public BlockSetBehaviourPeriodicWrapper(List<IBlockPeriodicBehaviour> periodicList)
	{
		_periodicList = periodicList;
	}

	@Override
	public void didSetBlock(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy proxy, Block replacedType)
	{
		for (IBlockPeriodicBehaviour periodic : _periodicList)
		{
			periodic.doInitialRegistration(context, proxy);
		}
	}
}
