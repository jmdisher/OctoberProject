package com.jeffdisher.october.aspects;

import java.util.Map;
import java.util.Set;

import com.jeffdisher.october.types.Block;


/**
 * Describes the types of blocks which can passively spread to other blocks or are destroyed by other blocks covering
 * them.
 * The most obvious example of this is grass:  It can spread to nearby dirt blocks but reverts to dirt is not under air,
 * specifically.
 * An example of a different use-case is tilled soil:  It doesn't spread but is made invalid by placing things on top of
 * it and converts to dirt.
 * TODO:  For now, we just hard-code all of this but it should move to data files in the future.
 */
public class GroundCoverRegistry
{
	private final Map<Block, Block> _groundCoverToTargets;
	private final Map<Block, Block> _nonSpreadingToBaseType;
	private final Map<Block, Set<Block>> _canGrowGroundCover;

	public GroundCoverRegistry(ItemRegistry items, BlockAspect blocks)
	{
		Block grass = blocks.getAsPlaceableBlock(items.getItemById("op.grass"));
		Block dirt = blocks.getAsPlaceableBlock(items.getItemById("op.dirt"));
		Block soil = blocks.getAsPlaceableBlock(items.getItemById("op.tilled_soil"));
		_groundCoverToTargets = Map.of(grass, dirt
		);
		_nonSpreadingToBaseType = Map.of(soil, dirt
		);
		_canGrowGroundCover = Map.of(dirt, Set.of(grass)
		);
	}

	/**
	 * Checks if a given block is a ground cover block (not to be confused with a target for spread).
	 * 
	 * @param block The block to check.
	 * @return True if the block is ground cover.
	 */
	public boolean isGroundCover(Block block)
	{
		return _groundCoverToTargets.containsKey(block) || _nonSpreadingToBaseType.containsKey(block);
	}

	/**
	 * Checks if a given possibleGroundCoverBlock block is ground cover and can exist under the above type block.
	 * 
	 * @param possibleGroundCoverBlock The block which might be a ground cover block.
	 * @param above The block type above.
	 * @return True if this is a ground cover type and can exist under the above type.
	 */
	public boolean canGroundCoverExistUnder(Block possibleGroundCoverBlock, Block above)
	{
		// For now, at least, we will consider all groundcover to be able to exist under any breathable block.
		// We always assume inactive since we don't want this to change based on that state.
		boolean isActive = false;
		return (_groundCoverToTargets.containsKey(possibleGroundCoverBlock) || _nonSpreadingToBaseType.containsKey(possibleGroundCoverBlock))
				&& Environment.getShared().blocks.canBreatheInBlock(above, isActive);
	}

	/**
	 * Finds the type of target block possibleGroundCoverBlock grows on.
	 * 
	 * @param possibleGroundCoverBlock The block which might be a ground cover block.
	 * @return The type of block this can grow on or null if not a ground cover block.
	 */
	public Block getSpreadToTypeForGroundCover(Block possibleGroundCoverBlock)
	{
		return _groundCoverToTargets.get(possibleGroundCoverBlock);
	}

	/**
	 * Finds what type of block a ground cover block should revert into when covered.
	 * 
	 * @param possibleGroundCoverBlock The block which might be a ground cover block.
	 * @return The block type this should revert into or null if this isn't a ground cover type.
	 */
	public Block getRevertTypeForGroundCover(Block possibleGroundCoverBlock)
	{
		return _groundCoverToTargets.containsKey(possibleGroundCoverBlock)
				? _groundCoverToTargets.get(possibleGroundCoverBlock)
				: _nonSpreadingToBaseType.get(possibleGroundCoverBlock)
		;
	}

	/**
	 * Used to check if a given block type is a target type for some kind of ground cover.
	 * 
	 * @param blockType The block type to check.
	 * @return The set of block types which can spread to this block or null if there aren't any (never empty).
	 */
	public Set<Block> canGrowGroundCover(Block blockType)
	{
		return _canGrowGroundCover.get(blockType);
	}
}
