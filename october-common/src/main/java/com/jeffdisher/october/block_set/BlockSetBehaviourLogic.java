package com.jeffdisher.october.block_set;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.aspects.LogicAspect;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.IMutableBlockProxy;
import com.jeffdisher.october.types.TickProcessingContext;


public class BlockSetBehaviourLogic implements IBlockSetBehaviour
{
	private final LogicAspect.ISignalChangeCallback _placementHandler;

	public BlockSetBehaviourLogic(LogicAspect.ISignalChangeCallback placementHandler)
	{
		_placementHandler = placementHandler;
	}

	@Override
	public void didSetBlock(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy proxy, Block replacedType)
	{
		FacingDirection outputDirection = proxy.getOrientation();
		boolean startActive = _placementHandler.shouldStoreHighSignal(env, context.previousBlockLookUp, location, outputDirection);
		
		if (startActive)
		{
			// Setting the block clears the flags so we are always setting this.
			byte flags = proxy.getFlags();
			flags = FlagsAspect.set(flags, FlagsAspect.FLAG_ACTIVE);
			proxy.setFlags(flags);
			
			Block newType = proxy.getBlock();
			LogicAspect.IActiveFlagChangeCallback changeState = env.logic.flagChangeHandler(newType);
			if (null != changeState)
			{
				changeState.activeFlagDidChange(context, proxy, location, startActive);
			}
		}
	}
}
