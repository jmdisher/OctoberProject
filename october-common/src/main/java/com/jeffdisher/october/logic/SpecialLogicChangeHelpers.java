package com.jeffdisher.october.logic;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Helpers for the OFFLINE_MANUAL logic block types which have special logic associated with logic state changes.
 */
public class SpecialLogicChangeHelpers
{
	public static void handleSpecialLogicChange(TickProcessingContext context, IMutableBlockProxy mutable, AbsoluteLocation location, Block block, byte currentFlags, boolean setHigh)
	{
		Environment env = Environment.getShared();
		if (env.special.blockCuboidLoader == block)
		{
			// If we are becoming active, we just want to request our cuboid be loaded and request a follow-up mutation so we can re-check the keep-alive.
			if (setHigh)
			{
				_keepCuboidLoaded(context, mutable, location);
			}
			byte flags = setHigh
				? FlagsAspect.set(currentFlags, FlagsAspect.FLAG_ACTIVE)
				: FlagsAspect.clear(currentFlags, FlagsAspect.FLAG_ACTIVE)
			;
			mutable.setFlags(flags);
		}
		else
		{
			// We are missing a special-case.
			throw Assert.unreachable();
		}
	}

	public static void periodicUpdate(TickProcessingContext context, IMutableBlockProxy mutable, AbsoluteLocation location, Block block, byte currentFlags)
	{
		Environment env = Environment.getShared();
		if (env.special.blockCuboidLoader == block)
		{
			boolean isActive = FlagsAspect.isSet(currentFlags, FlagsAspect.FLAG_ACTIVE);
			if (isActive)
			{
				_keepCuboidLoaded(context, mutable, location);
			}
		}
		else
		{
			// We are missing a special-case.
			throw Assert.unreachable();
		}
	}


	private static void _keepCuboidLoaded(TickProcessingContext context, IMutableBlockProxy mutable, AbsoluteLocation location)
	{
		context.keepAliveSink.accept(location.getCuboidAddress());
		// We need to get another periodic mutation before the unload so that we can maintain the keep-alive.
		mutable.requestFutureMutation(MiscConstants.CUBOID_KEEP_ALIVE_MILLIS - context.millisPerTick);
	}
}
