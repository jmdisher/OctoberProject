package com.jeffdisher.october.logic;

import java.util.List;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Helpers related to fire spread.
 * Note that fire is stored as a flag in the FLAGS aspect.
 */
public class FireHelpers
{
	/**
	 * Finds all the flammable blocks near the given source.
	 * 
	 * @param env The environment.
	 * @param context The current tick's context.
	 * @param source The location we are considering the fire source.
	 * @return The list of flammable locations (might be empty but never null).
	 */
	public static List<AbsoluteLocation> findFlammableNeighbours(Environment env, TickProcessingContext context, AbsoluteLocation source)
	{
		// We want to choose the block below, 2 blocks above, the block in each direction and the block above each of those (11 checks).
		List<AbsoluteLocation> toCheck = List.of(
				source.getRelative(0, 0, -1)
				
				, source.getRelative(0, 0, 1)
				, source.getRelative(0, 0, 2)
				
				, source.getRelative(1, 0, 0)
				, source.getRelative(1, 0, 1)
				, source.getRelative(-1, 0, 0)
				, source.getRelative(-1, 0, 1)
				, source.getRelative(0, 1, 0)
				, source.getRelative(0, 1, 1)
				, source.getRelative(0, -1, 0)
				, source.getRelative(0, -1, 1)
		);
		
		return toCheck.stream().filter((AbsoluteLocation check) -> {
			BlockProxy proxy = context.previousBlockLookUp.apply(check);
			boolean isFlammable = false;
			if (null != proxy)
			{
				Block block = proxy.getBlock();
				if (env.blocks.isFlammable(block))
				{
					boolean isBurning = FlagsAspect.isSet(proxy.getFlags(), FlagsAspect.FLAG_BURNING);
					isFlammable = !isBurning;
				}
			}
			return isFlammable;
		}).toList();
	}

	/**
	 * Checks if the given check location is near a fire source.  A fire source could be an actual fire source block
	 * type or a flammable block which is on fire.
	 * 
	 * @param env The environment.
	 * @param context The current tick's context.
	 * @param check The block to check.
	 * @return True if check is near any fire sources.
	 */
	public static boolean isNearFireSource(Environment env, TickProcessingContext context, AbsoluteLocation check)
	{
		// We check the reverse of the check list in findFlammableNeighbours().
		List<AbsoluteLocation> toCheck = List.of(
				check.getRelative(0, 0, 1)
				
				, check.getRelative(0, 0, -1)
				, check.getRelative(0, 0, -2)
				
				, check.getRelative(-1, 0, 0)
				, check.getRelative(-1, 0, -1)
				, check.getRelative(1, 0, 0)
				, check.getRelative(1, 0, -1)
				, check.getRelative(0, -1, 0)
				, check.getRelative(0, -1, -1)
				, check.getRelative(0, 1, 0)
				, check.getRelative(0, 1, -1)
		);
		
		return toCheck.stream().anyMatch((AbsoluteLocation location) -> {
			BlockProxy proxy = context.previousBlockLookUp.apply(location);
			boolean isFireSource = false;
			if (null != proxy)
			{
				Block block = proxy.getBlock();
				if (env.blocks.isFireSource(block) || FlagsAspect.isSet(proxy.getFlags(), FlagsAspect.FLAG_BURNING))
				{
					isFireSource = true;
				}
			}
			return isFireSource;
		});
	}
}
