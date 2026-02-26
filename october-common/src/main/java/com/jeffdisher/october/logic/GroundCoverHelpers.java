package com.jeffdisher.october.logic;

import java.util.List;
import java.util.Set;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Helpers related to how ground cover spreads.
 */
public class GroundCoverHelpers
{
	/**
	 * Finds all the potential target blocks near the given source.
	 * Note that this is read-only and sends no mutations.
	 * 
	 * @param env The environment.
	 * @param previousBlockLookUp Lookup function for previous tick proxies.
	 * @param source The location we are considering the spread source.
	 * @param sourceBlock The type of ground cover to spread.
	 * @return The list of possible spread locations (might be empty but never null).
	 */
	public static List<AbsoluteLocation> findSpreadNeighbours(Environment env, TickProcessingContext.IBlockFetcher previousBlockLookUp, AbsoluteLocation source, Block sourceBlock)
	{
		Block target = env.groundCover.getSpreadToTypeForGroundCover(sourceBlock);
		
		// Note that, while this can only be called with ground cover, some ground cover is non-spreading.
		List<AbsoluteLocation> neighbours;
		if (null != target)
		{
			List<AbsoluteLocation> toCheck = _getSymmetricSearchList(source);
			
			neighbours = toCheck.stream().filter((AbsoluteLocation check) -> {
				BlockProxy proxy = previousBlockLookUp.readBlock(check);
				boolean canSpread = false;
				if (null != proxy)
				{
					Block block = proxy.getBlock();
					canSpread = (target == block);
				}
				return canSpread;
			}).toList();
		}
		else
		{
			neighbours = List.of();
		}
		return neighbours;
	}

	/**
	 * Checks if existingBlock at check location can be converted to groundCoverType.  This depends on the this
	 * transformation being valid, the block above being air, and there being a valid source of this groundCoverType.
	 * Note that this is read-only and sends no mutations.
	 * 
	 * @param env The environment.
	 * @param previousBlockLookUp Lookup function for previous tick proxies.
	 * @param check The block location to check.
	 * @param proxy The block proxy.
	 * @return True if this can be ignited.
	 */
	public static boolean canChangeToGroundCover(Environment env, TickProcessingContext.IBlockFetcher previousBlockLookUp, AbsoluteLocation check, Block existingBlock, Block groundCoverType)
	{
		boolean canChange = false;
		
		// First, check if this is even a valid change.
		if (existingBlock == env.groundCover.getSpreadToTypeForGroundCover(groundCoverType))
		{
			// Check the block above, since that will influence our decision.
			BlockProxy blockAbove = previousBlockLookUp.readBlock(check.getRelative(0, 0, 1));
			if ((null != blockAbove) && env.groundCover.canGroundCoverExistUnder(groundCoverType, blockAbove.getBlock()))
			{
				// Make sure that there is a valid source, that we are a valid target for this, and that the block above doesn't prevent it.
				List<AbsoluteLocation> toCheck = _getSymmetricSearchList(check);
				
				// Make sure that any of the source blocks are the right type.
				canChange = toCheck.stream().anyMatch((AbsoluteLocation location) -> {
					BlockProxy proxy = previousBlockLookUp.readBlock(location);
					return ((null != proxy) && (groundCoverType == proxy.getBlock()));
				});
			}
		}
		return canChange;
	}

	/**
	 * Checks if a block at check location of existingBlock type could turn into some ground cover type.  This checks if
	 * the existingBlock has any potential ground cover types which can spread from a nearby block to this one,
	 * considering the block currently above it.
	 * Note that this is read-only and sends no mutations.
	 * 
	 * @param env The environment.
	 * @param previousBlockLookUp Lookup function for previous tick proxies.
	 * @param check The block location to check.
	 * @param existingBlock The current block current.
	 * @return The ground cover block type this could become, or null if there isn't one.
	 */
	public static Block findPotentialGroundCoverType(Environment env, TickProcessingContext.IBlockFetcher previousBlockLookUp, AbsoluteLocation check, Block existingBlock)
	{
		Block blockToSpread = null;
		
		// See what possible gound cover types this block can become.
		Set<Block> possibleGroundCoverTypes = env.groundCover.canGrowGroundCover(existingBlock);
		if (null != possibleGroundCoverTypes)
		{
			// Make sure that we can read the block type above (since we are checking under it).
			BlockProxy blockAbove = previousBlockLookUp.readBlock(check.getRelative(0, 0, 1));
			
			if (null != blockAbove)
			{
				Block typeAbove = blockAbove.getBlock();
				
				// Check all the locations where ground cover could be.
				List<AbsoluteLocation> toCheck = _getSymmetricSearchList(check);
				
				// TODO:  We currently just find the first match but we will need a prioritization for this, in the future.
				blockToSpread = toCheck.stream().map((AbsoluteLocation location) -> {
					BlockProxy proxy = previousBlockLookUp.readBlock(location);
					Block suggestion = null;
					if (null != proxy)
					{
						Block block = proxy.getBlock();
						if (possibleGroundCoverTypes.contains(block))
						{
							if (env.groundCover.canGroundCoverExistUnder(block, typeAbove))
							{
								suggestion = block;
							}
						}
					}
					return suggestion;
				})
						.filter((Block block) -> (null != block))
						.findFirst().orElse(null)
				;
			}
		}
		return blockToSpread;
	}

	/**
	 * Checks if the existingBlock type at check location should be reverted from ground cover to a different kind of
	 * block, based on whether the block above it has invalidated it being ground cover.
	 * 
	 * @param env The environment.
	 * @param previousBlockLookUp Lookup function for previous tick proxies.
	 * @param check The block location to check.
	 * @param existingBlock The current block type in check location.
	 * @return The block to revert to or null if this should be left unchanged.
	 */
	public static Block checkRevertGroundCover(Environment env, TickProcessingContext.IBlockFetcher previousBlockLookUp, AbsoluteLocation check, Block existingBlock)
	{
		Block possibleBlock = env.groundCover.getRevertTypeForGroundCover(existingBlock);
		// We expect that this will only be called with a ground cover block type.
		Assert.assertTrue(null != possibleBlock);
		BlockProxy proxy = previousBlockLookUp.readBlock(check.getRelative(0, 0, 1));
		return ((null != proxy) && !env.groundCover.canGroundCoverExistUnder(existingBlock, proxy.getBlock()))
				? possibleBlock
				: null
		;
	}


	private static List<AbsoluteLocation> _getSymmetricSearchList(AbsoluteLocation source)
	{
		// We just want to check each cardinal direction, with a potential step up or down (12 checks).
		// Note that these are symmetric so the same list is checked when searching spread to and spread from.
		List<AbsoluteLocation> toCheck = List.of(
				source.getRelative(1, 0, -1)
				, source.getRelative(1, 0, 0)
				, source.getRelative(1, 0, 1)
				, source.getRelative(-1, 0, -1)
				, source.getRelative(-1, 0, 0)
				, source.getRelative(-1, 0, 1)
				, source.getRelative(0, 1, -1)
				, source.getRelative(0, 1, 0)
				, source.getRelative(0, 1, 1)
				, source.getRelative(0, -1, -1)
				, source.getRelative(0, -1, 0)
				, source.getRelative(0, -1, 1)
		);
		return toCheck;
	}
}
