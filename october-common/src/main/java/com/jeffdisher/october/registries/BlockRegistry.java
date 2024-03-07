package com.jeffdisher.october.registries;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.aspects.DamageAspect;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Item;


/**
 * Just contain the subset of Item objects which can be placed in the world as Block objects.
 */
public class BlockRegistry
{
	/**
	 * Since blocks are the non-negative item types, this helper exists to look them up by block type.
	 */
	public static final Block[] BLOCKS_BY_TYPE = new Block[BlockAspect.TOTAL_BLOCK_TYPES];

	static {
		// We construct the blocks by looking at items.
		for (int i = 0; i < BLOCKS_BY_TYPE.length; ++i)
		{
			Item item = ItemRegistry.ITEMS_BY_TYPE[i];
			BLOCKS_BY_TYPE[i] = (DamageAspect.NOT_BLOCK != item.toughness())
					? new Block(item.number())
					: null
			;
		}
	}

	private BlockRegistry()
	{
		// No instantiation.
	}
}
