package com.jeffdisher.october.logic;

import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.IMutableBlockProxy;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Helpers for the "op.cuboid_loader" block, since it needs some special triggers related to when it becomes active as
 * well as periodically.
 */
public class CuboidLoaderHelpers
{
	public static void didActiveFlagChange(TickProcessingContext context, IMutableBlockProxy mutable, AbsoluteLocation location, boolean isActive)
	{
		// TODO:  Remove this when this helper class is inlined.
	}

	public static void periodicUpdate(TickProcessingContext context, IMutableBlockProxy mutable, AbsoluteLocation location, boolean isActive)
	{
		if (isActive)
		{
			context.keepAliveSink.accept(location.getCuboidAddress());
		}
		mutable.requestFutureMutation(MiscConstants.CUBOID_KEEP_ALIVE_MILLIS - context.millisPerTick);
	}
}
