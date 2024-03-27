package com.jeffdisher.october.types;

import com.jeffdisher.october.registries.ItemRegistry;


/**
 * Represents the subset of Item objects which can be placed in the world.  This type is just to provide a high-level
 * distinction between Item and Block so they just wrap the number.
 */
public record Block(short number)
{
	/**
	 * @return The item representation of this block.
	 */
	public Item asItem()
	{
		return _asItem();
	}

	@Override
	public String toString()
	{
		return "Block(" + _asItem().name() +")";
	}

	/**
	 * Used to determine if this block is something like air/water/etc.
	 * Note that those which cannot be replaced are potentially breakable (although could be indestructible).
	 * 
	 * @return True if this block can be directly overwritten by another.
	 */
	public boolean canBeReplaced()
	{
		Item item = _asItem();
		return (ItemRegistry.AIR == item)
				|| (ItemRegistry.WATER_SOURCE == item)
				|| (ItemRegistry.WATER_STRONG == item)
				|| (ItemRegistry.WATER_WEAK == item)
		;
	}

	/**
	 * Used to determine if this block is something an entity can walk through, like air/water/etc.
	 * NOTE:  These blocks should probably all permit an air inventory or entities will walk through them but items
	 * can't be dropped here.
	 * 
	 * @return True if this block allows an entity to pass through.
	 */
	public boolean permitsEntityMovement()
	{
		Item item = _asItem();
		return (ItemRegistry.AIR == item)
				|| (ItemRegistry.WATER_SOURCE == item)
				|| (ItemRegistry.WATER_STRONG == item)
				|| (ItemRegistry.WATER_WEAK == item)
				|| (ItemRegistry.SAPLING == item)
				|| (ItemRegistry.WHEAT_SEEDLING == item)
				|| (ItemRegistry.WHEAT_YOUNG == item)
				|| (ItemRegistry.WHEAT_MATURE == item)
		;
	}

	/**
	 * Used to determine if this block can exist on top of another.  This is generally true but some types have specific
	 * requirements.
	 * 
	 * @param bottomBlock The block underneath the receiver.
	 * @return True if this block can exist on top of bottomBlock.
	 */
	public boolean canExistOnBlock(Block bottomBlock)
	{
		boolean canExist = true;
		Item item = _asItem();
		if ((ItemRegistry.SAPLING == item)
				|| (ItemRegistry.WHEAT_SEEDLING == item)
				|| (ItemRegistry.WHEAT_YOUNG == item)
				|| (ItemRegistry.WHEAT_MATURE == item)
		)
		{
			// These growing blocks can only exist on dirt.
			canExist = (null != bottomBlock) && (ItemRegistry.DIRT == bottomBlock.asItem());
		}
		return canExist;
	}


	private Item _asItem()
	{
		return ItemRegistry.ITEMS_BY_TYPE[number];
	}
}
