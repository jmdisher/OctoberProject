package com.jeffdisher.october.data;

import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.CraftOperation;
import com.jeffdisher.october.types.FuelState;
import com.jeffdisher.october.types.Inventory;


/**
 * Grants high-level read-only access to the data under a single block.
 */
public interface IBlockProxy
{
	/**
	 * @return The block at this block location.
	 */
	Block getBlock();
	/**
	 * Note that this will fake up an empty inventory if this item can support one, only returning null if the block
	 * type doesn't have one.
	 * 
	 * @return The immutable inventory object (potentially empty or null).
	 */
	Inventory getInventory();
	/**
	 * @return The damage value for this block (usually 0).
	 */
	short getDamage();
	/**
	 * @return The crafting operation for this block (usually null).
	 */
	CraftOperation getCrafting();
	/**
	 * Note that this will fake up an empty fuel state if this item can support one, only returning null if the block
	 * type doesn't have one.
	 * 
	 * @return The immutable fuel state object (potentially empty or null).
	 */
	FuelState getFuel();
	/**
	 * @return The light value for this block ([0..15]).
	 */
	byte getLight();
	/**
	 * @return The logic value for this block ([0..15]).
	 */
	byte getLogic();
	/**
	 * @return The flags for this block (as defined in FlagsAspect).
	 */
	byte getFlags();
}
