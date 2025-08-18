package com.jeffdisher.october.logic;

import java.util.Set;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.aspects.OrientationAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Composite structures are those who have an emergent behaviour in a special cornerstone block when other specific
 * blocks are arranged around it.
 * These helpers are used apply that emergent behaviour and change corresponding block states or schedule follow-up
 * mutations in response.
 */
public class CompositeHelpers
{
	/**
	 * We will poll the composite cornerstone every 5 seconds to see if it should change its active state.
	 * Ideally, this would be replaced with an event-based solution but these may not be in the same cuboid so it would
	 * require some kind of "on load" event for other blocks in the composition which will complicate the system a lot
	 * for something which is otherwise quite low-cost, even if hack-ish with this 5-second delay.
	 */
	public static final long COMPOSITE_CHECK_FREQUENCY = 5_000L;
	public static final String VOID_STONE_ID = "op.void_stone";
	public static final String VOID_LAMP_ID = "op.void_lamp";
	public static final String PORTAL_KEYSTONE_ID = "op.portal_keystone";

	/**
	 * Called when a cornerstone block is placed or receives a periodic update event in order to check if any state
	 * needs to change.
	 * This call also re-requests the periodic update for this block.
	 * 
	 * @param env The environment.
	 * @param context The current tick context.
	 * @param location The location of the cornerstone block.
	 * @param proxy The mutable proxy for the cornerstone block.
	 */
	public static void processCornerstoneUpdate(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy proxy)
	{
		// NOTE:  This assumes that the type we were given is already a valid cornerstone type.
		boolean shouldBeActive = _shouldBeActive(env, context, location, proxy);
		byte flags = proxy.getFlags();
		boolean wasActive = FlagsAspect.isSet(flags, FlagsAspect.FLAG_ACTIVE);
		if (shouldBeActive != wasActive)
		{
			byte newFlags = shouldBeActive
				? FlagsAspect.set(flags, FlagsAspect.FLAG_ACTIVE)
				: FlagsAspect.clear(flags, FlagsAspect.FLAG_ACTIVE)
			;
			proxy.setFlags(newFlags);
		}
		proxy.requestFutureMutation(COMPOSITE_CHECK_FREQUENCY);
	}


	private static boolean _shouldBeActive(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy proxy)
	{
		boolean isValid = false;
		// TODO:  In the future, these hard-coded IDs and relative mappings need to be defined in a data file.
		Block type = proxy.getBlock();
		String blockId = type.item().id();
		if (VOID_LAMP_ID.equals(blockId))
		{
			AbsoluteLocation underLocation = location.getRelative(0, 0, -1);
			BlockProxy underProxy = context.previousBlockLookUp.apply(underLocation);
			if (null != underProxy)
			{
				// Check if this is the valid type.
				Block underType = underProxy.getBlock();
				isValid = VOID_STONE_ID.equals(underType.item().id());
			}
			else
			{
				// Note that we could request that this be loaded here but that would easily cause cycles when multiple
				// cornerstones exist in neighbouring cuboids so we will just assume that this case only happens in the
				// periphery of users and allow it to become inactive.
			}
		}
		else if (PORTAL_KEYSTONE_ID.equals(blockId))
		{
			Block stoneBlock = env.blocks.fromItem(env.items.getItemById(VOID_STONE_ID));
			Set<AbsoluteLocation> stoneSet = Set.of(
				location.getRelative(-1, 0, 0)
				, location.getRelative(-2, 0, 0)
				, location.getRelative(-2, 0, 1)
				, location.getRelative(-2, 0, 2)
				, location.getRelative(-2, 0, 3)
				, location.getRelative(-2, 0, 4)
				, location.getRelative(-1, 0, 4)
				, location.getRelative( 0, 0, 4)
				, location.getRelative( 1, 0, 4)
				, location.getRelative( 2, 0, 4)
				, location.getRelative( 2, 0, 3)
				, location.getRelative( 2, 0, 2)
				, location.getRelative( 2, 0, 1)
				, location.getRelative( 2, 0, 0)
				, location.getRelative( 1, 0, 0)
			);
			Set<AbsoluteLocation> airSet = Set.of(
				location.getRelative(-1, 0, 1)
				, location.getRelative(0, 0, 1)
				, location.getRelative(1, 0, 1)
				, location.getRelative(-1, 0, 2)
				, location.getRelative(0, 0, 2)
				, location.getRelative(1, 0, 2)
				, location.getRelative(-1, 0, 3)
				, location.getRelative(0, 0, 3)
				, location.getRelative(1, 0, 3)
			);
			OrientationAspect.Direction orientation = proxy.getOrientation();
			isValid = _matchBlockTypes(context, orientation, stoneSet, stoneBlock);
			if (isValid)
			{
				isValid = _matchBlockTypes(context, orientation, airSet, env.special.AIR);
			}
		}
		else
		{
			// NOTE:  This can ONLY be called on a valid cornerstone so this would be a usage error.
			throw Assert.unreachable();
		}
		return isValid;
	}

	private static boolean _matchBlockTypes(TickProcessingContext context, OrientationAspect.Direction orientation, Set<AbsoluteLocation> locations, Block blockMatch)
	{
		boolean isValid = true;
		for (AbsoluteLocation target : locations)
		{
			// Note that we need to correct this for orientation.
			AbsoluteLocation rotated = orientation.rotateAboutZ(target);
			BlockProxy targetProxy = context.previousBlockLookUp.apply(rotated);
			if (null == targetProxy)
			{
				// Request that this is loaded since remove portals are sometimes checked.
				context.keepAliveSink.accept(target.getCuboidAddress());
				
				// We can't answer in the affirmative so fail out.
				isValid = false;
				break;
			}
			else if (blockMatch != targetProxy.getBlock())
			{
				// Not the correct block so fail out.
				isValid = false;
				break;
			}
		}
		return isValid;
	}
}
