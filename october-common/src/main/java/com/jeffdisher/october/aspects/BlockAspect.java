package com.jeffdisher.october.aspects;

import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Item;


/**
 * Contains helpers for looking up information related to the block aspect.
 * Note that the block aspect is fundamentally based on the "Damage" aspect, since it has to do with being able to
 * break blocks.
 */
public class BlockAspect
{
	public static final Block[] BLOCKS_BY_TYPE = new Block[ItemRegistry.ITEMS_BY_TYPE.length];

	static {
		// We construct the blocks by looking at items.
		for (int i = 0; i < BLOCKS_BY_TYPE.length; ++i)
		{
			Item item = ItemRegistry.ITEMS_BY_TYPE[i];
			BLOCKS_BY_TYPE[i] = (DamageAspect.NOT_BLOCK != DamageAspect.getToughness(item))
					? new Block(item.number())
					: null
			;
		}
	}

	/**
	 * @return The block object representing the given item, null if it can't be placed as a block.
	 */
	public static Block getBlock(Item item)
	{
		return BLOCKS_BY_TYPE[item.number()];
	}
}
