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
		// If we are becoming active, we just want to request our cuboid be loaded and request a follow-up mutation so we can re-check the keep-alive.
		if (isActive)
		{
			_keepCuboidLoaded(context, mutable, location);
		}
	}

	public static void periodicUpdate(TickProcessingContext context, IMutableBlockProxy mutable, AbsoluteLocation location, boolean isActive)
	{
		if (isActive)
		{
			_keepCuboidLoaded(context, mutable, location);
		}
	}


	private static void _keepCuboidLoaded(TickProcessingContext context, IMutableBlockProxy mutable, AbsoluteLocation location)
	{
		context.keepAliveSink.accept(location.getCuboidAddress());
		// We need to get another periodic mutation before the unload so that we can maintain the keep-alive.
		mutable.requestFutureMutation(MiscConstants.CUBOID_KEEP_ALIVE_MILLIS - context.millisPerTick);
	}
}
