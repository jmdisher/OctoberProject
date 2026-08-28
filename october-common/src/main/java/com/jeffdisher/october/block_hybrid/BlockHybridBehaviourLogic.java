package com.jeffdisher.october.block_hybrid;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.aspects.LogicAspect;
import com.jeffdisher.october.aspects.LogicAspect.ISignalChangeCallback;
import com.jeffdisher.october.block_set.IBlockSetBehaviour;
import com.jeffdisher.october.block_update.IBlockUpdateBehaviour;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.IMutableBlockProxy;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Called in both a set-block or block update case.
 */
public class BlockHybridBehaviourLogic implements IBlockUpdateBehaviour, IBlockSetBehaviour
{
	private final LogicAspect.ISignalChangeCallback _handler;

	public BlockHybridBehaviourLogic(ISignalChangeCallback handler)
	{
		_handler = handler;
	}

	@Override
	public void doRunUpdate(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy proxy)
	{
		_apply(env, context, location, proxy);
	}

	@Override
	public void didSetBlock(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy proxy, Block replacedType)
	{
		_apply(env, context, location, proxy);
	}


	private void _apply(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy proxy)
	{
		FacingDirection outputDirection = proxy.getOrientation();
		boolean isActive = _handler.shouldStoreHighSignal(env, context.previousBlockLookUp, location, outputDirection);
		byte flags = proxy.getFlags();
		if (isActive != FlagsAspect.isSet(flags, FlagsAspect.FLAG_ACTIVE))
		{
			flags = isActive
				? FlagsAspect.set(flags, FlagsAspect.FLAG_ACTIVE)
				: FlagsAspect.clear(flags, FlagsAspect.FLAG_ACTIVE)
			;
			proxy.setFlags(flags);
			
			// Note that we keep the change of block ACTIVE state and the response to this change as 2 distinct callbacks.
			LogicAspect.IActiveFlagChangeCallback changeState = env.logic.flagChangeHandler(proxy.getBlock());
			if (null != changeState)
			{
				changeState.activeFlagDidChange(context, proxy, location, isActive);
			}
		}
	}
}
