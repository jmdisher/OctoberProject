package com.jeffdisher.october.aspects;

import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Item;


/**
 * Contains helpers for looking up information related to the block aspect.
 * For convenience, this exposes some constants which map to the constants in ItemRegistry.
 */
public class BlockAspect
{
	private final ItemRegistry _items;

	public final Block[] BLOCKS_BY_TYPE;

	public final Block AIR;
	public final Block STONE;
	public final Block LOG;
	public final Block PLANK;
	public final Block STONE_BRICK;
	public final Block CRAFTING_TABLE;
	public final Block FURNACE;
	public final Block COAL_ORE;
	public final Block IRON_ORE;
	public final Block DIRT;
	public final Block WATER_SOURCE;
	public final Block WATER_STRONG;
	public final Block WATER_WEAK;
	public final Block LANTERN;
	public final Block SAPLING;
	public final Block LEAF;
	public final Block WHEAT_SEEDLING;
	public final Block WHEAT_YOUNG;
	public final Block WHEAT_MATURE;

	private static Block _register(Block[] array, Item item)
	{
		short number = item.number();
		Block block = new Block(item);
		array[number] = block;
		return block;
	}

	public BlockAspect(ItemRegistry items)
	{
		_items = items;
		this.BLOCKS_BY_TYPE = new Block[items.ITEMS_BY_TYPE.length];

		this.AIR = _register(this.BLOCKS_BY_TYPE, items.AIR);
		this.STONE = _register(this.BLOCKS_BY_TYPE, items.STONE);
		this.LOG = _register(this.BLOCKS_BY_TYPE, items.LOG);
		this.PLANK = _register(this.BLOCKS_BY_TYPE, items.PLANK);
		this.STONE_BRICK = _register(this.BLOCKS_BY_TYPE, items.STONE_BRICK);
		this.CRAFTING_TABLE = _register(this.BLOCKS_BY_TYPE, items.CRAFTING_TABLE);
		this.FURNACE = _register(this.BLOCKS_BY_TYPE, items.FURNACE);
		this.COAL_ORE = _register(this.BLOCKS_BY_TYPE, items.COAL_ORE);
		this.IRON_ORE = _register(this.BLOCKS_BY_TYPE, items.IRON_ORE);
		this.DIRT = _register(this.BLOCKS_BY_TYPE, items.DIRT);
		this.WATER_SOURCE = _register(this.BLOCKS_BY_TYPE, items.WATER_SOURCE);
		this.WATER_STRONG = _register(this.BLOCKS_BY_TYPE, items.WATER_STRONG);
		this.WATER_WEAK = _register(this.BLOCKS_BY_TYPE, items.WATER_WEAK);
		this.LANTERN = _register(this.BLOCKS_BY_TYPE, items.LANTERN);
		this.SAPLING = _register(this.BLOCKS_BY_TYPE, items.SAPLING);
		this.LEAF = _register(this.BLOCKS_BY_TYPE, items.LEAF);
		this.WHEAT_SEEDLING = _register(this.BLOCKS_BY_TYPE, items.WHEAT_SEEDLING);
		this.WHEAT_YOUNG = _register(this.BLOCKS_BY_TYPE, items.WHEAT_YOUNG);
		this.WHEAT_MATURE = _register(this.BLOCKS_BY_TYPE, items.WHEAT_MATURE);
	}

	/**
	 * Used to determine if the given block is something like air/water/etc which can just be overwritten by another
	 * block.
	 * Note that those which cannot be replaced are potentially breakable (although could be indestructible).
	 * 
	 * @param block The block to check.
	 * @return True if block can be directly overwritten by another.
	 */
	public boolean canBeReplaced(Block block)
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
	public boolean permitsEntityMovement(Block block)
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
	public boolean canExistOnBlock(Block topBlock, Block bottomBlock)
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
	public Block getAsPlaceableBlock(Item itemType)
	{
		// Most items just become the corresponding block, but some are special.
		Block block = null;
		if (null != itemType)
		{
			block = BLOCKS_BY_TYPE[itemType.number()];
			if (null == block)
			{
				// Check the special-cases.
				if (_items.WHEAT_SEED == itemType)
				{
					block = this.WHEAT_SEEDLING;
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
	public Item[] droppedBlocksOnBreak(Block block)
	{
		Item[] dropped;
		if (LEAF == block)
		{
			dropped = new Item[] { _items.SAPLING };
		}
		else if ((WHEAT_SEEDLING == block)
				|| (WHEAT_YOUNG == block)
		)
		{
			dropped = new Item[] { _items.WHEAT_SEED };
		}
		else if ((WHEAT_MATURE == block)
		)
		{
			dropped = new Item[] {
					_items.WHEAT_SEED,
					_items.WHEAT_SEED,
					_items.WHEAT_ITEM,
					_items.WHEAT_ITEM,
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
