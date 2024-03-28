package com.jeffdisher.october.aspects;

import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Item;


/**
 * Contains helpers for looking up information related to the block aspect.
 * For convenience, this exposes some constants which map to the constants in ItemRegistry.
 */
public class BlockAspect
{
	public static final Block[] BLOCKS_BY_TYPE = new Block[ItemRegistry.ITEMS_BY_TYPE.length];

	public static final Block AIR = _register(ItemRegistry.AIR);
	public static final Block STONE = _register(ItemRegistry.STONE);
	public static final Block LOG = _register(ItemRegistry.LOG);
	public static final Block PLANK = _register(ItemRegistry.PLANK);
	public static final Block STONE_BRICK = _register(ItemRegistry.STONE_BRICK);
	public static final Block CRAFTING_TABLE = _register(ItemRegistry.CRAFTING_TABLE);
	public static final Block FURNACE = _register(ItemRegistry.FURNACE);
	public static final Block COAL_ORE = _register(ItemRegistry.COAL_ORE);
	public static final Block IRON_ORE = _register(ItemRegistry.IRON_ORE);
	public static final Block DIRT = _register(ItemRegistry.DIRT);
	public static final Block WATER_SOURCE = _register(ItemRegistry.WATER_SOURCE);
	public static final Block WATER_STRONG = _register(ItemRegistry.WATER_STRONG);
	public static final Block WATER_WEAK = _register(ItemRegistry.WATER_WEAK);
	public static final Block LANTERN = _register(ItemRegistry.LANTERN);
	public static final Block SAPLING = _register(ItemRegistry.SAPLING);
	public static final Block LEAF = _register(ItemRegistry.LEAF);
	public static final Block WHEAT_SEEDLING = _register(ItemRegistry.WHEAT_SEEDLING);
	public static final Block WHEAT_YOUNG = _register(ItemRegistry.WHEAT_YOUNG);
	public static final Block WHEAT_MATURE = _register(ItemRegistry.WHEAT_MATURE);

	private static Block _register(Item item)
	{
		short number = item.number();
		Block block = new Block(item);
		BLOCKS_BY_TYPE[number] = block;
		return block;
	}


	/**
	 * Used to determine if the given block is something like air/water/etc which can just be overwritten by another
	 * block.
	 * Note that those which cannot be replaced are potentially breakable (although could be indestructible).
	 * 
	 * @param block The block to check.
	 * @return True if block can be directly overwritten by another.
	 */
	public static boolean canBeReplaced(Block block)
	{
		return (AIR == block)
				|| (WATER_SOURCE == block)
				|| (WATER_STRONG == block)
				|| (WATER_WEAK == block)
		;
	}

	/**
	 * Used to determine if the given block is something an entity can walk through, like air/water/etc.
	 * NOTE:  These blocks should probably all permit an air inventory or entities will walk through them but items
	 * can't be dropped here, which seems odd.
	 * 
	 * @param block The block to check.
	 * @return True if block allows an entity to pass through.
	 */
	public static boolean permitsEntityMovement(Block block)
	{
		return (AIR == block)
				|| (WATER_SOURCE == block)
				|| (WATER_STRONG == block)
				|| (WATER_WEAK == block)
				|| (SAPLING == block)
				|| (WHEAT_SEEDLING == block)
				|| (WHEAT_YOUNG == block)
				|| (WHEAT_MATURE == block)
		;
	}

	/**
	 * Used to determine if the given block can exist on top of another block type.  This is generally true but some
	 * types have specific requirements.
	 * 
	 * @param topBlock The block being checked (on top).
	 * @param bottomBlock The block underneath.
	 * @return True if topBlock can exist on top of bottomBlock.
	 */
	public static boolean canExistOnBlock(Block topBlock, Block bottomBlock)
	{
		boolean canExist = true;
		if ((SAPLING == topBlock)
				|| (WHEAT_SEEDLING == topBlock)
				|| (WHEAT_YOUNG == topBlock)
				|| (WHEAT_MATURE == topBlock)
		)
		{
			// These growing blocks can only exist on dirt (unloaded shouldn't really happen but we will say no).
			canExist = (null != bottomBlock) && (DIRT == bottomBlock);
		}
		return canExist;
	}

	/**
	 * Returns the given item's corresponding block type when being placed in the world, null if it can't be placed.
	 * 
	 * @param itemType The item to place.
	 * @return The block type to place in the world, null if it can't be placed.
	 */
	public static Block getAsPlaceableBlock(Item itemType)
	{
		// Most items just become the corresponding block, but some are special.
		Block block = null;
		if (null != itemType)
		{
			block = BLOCKS_BY_TYPE[itemType.number()];
			if (null == block)
			{
				// Check the special-cases.
				if (ItemRegistry.WHEAT_SEED == itemType)
				{
					block = BlockAspect.WHEAT_SEEDLING;
				}
			}
		}
		return block;
	}

	/**
	 * Returns the array of items which should be dropped when the given block is broken, in the world.
	 * 
	 * @param block The block to break.
	 * @return The array of items (never null).
	 */
	public static Item[] droppedBlocksOnBread(Block block)
	{
		Item[] dropped;
		if (LEAF == block)
		{
			dropped = new Item[] { ItemRegistry.SAPLING };
		}
		else if ((WHEAT_SEEDLING == block)
				|| (WHEAT_YOUNG == block)
		)
		{
			dropped = new Item[] { ItemRegistry.WHEAT_SEED };
		}
		else if ((WHEAT_MATURE == block)
		)
		{
			dropped = new Item[] {
					ItemRegistry.WHEAT_SEED,
					ItemRegistry.WHEAT_SEED,
					ItemRegistry.WHEAT_ITEM,
					ItemRegistry.WHEAT_ITEM,
			};
		}
		else
		{
			// By default, all other blocks just drop as their item type.
			dropped = new Item[] { block.item() };
		}
		return dropped;
	}
}
