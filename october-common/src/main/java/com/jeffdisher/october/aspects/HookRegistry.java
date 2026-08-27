package com.jeffdisher.october.aspects;

import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.IMutableBlockProxy;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * The HookRegistry is a high-level object which uses the other elements of the Environment to connect high-level events
 * in the system to the various actions which need to follow as a result.
 * An example of this is the "didSetBlock" call wherein specific types of blocks will need to set other auxiliary state
 * and/or register for follow-up actions when a block is set, based on the block type or replaced block type.
 */
public class HookRegistry
{
	public static HookRegistry setupHooks(
	)
	{
		return new HookRegistry();
	}


	private HookRegistry(
	)
	{
	}

	public void didSetBlock(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy proxy, Block replacedType)
	{
		Block newType = proxy.getBlock();
		env.periodic.behaviour(newType).doInitialRegistration(context, proxy);
	}

	public void doRunPeriodic(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy proxy)
	{
		Block block = proxy.getBlock();
		env.periodic.behaviour(block).runPeriodic(env, context, location, proxy);
	}
}
