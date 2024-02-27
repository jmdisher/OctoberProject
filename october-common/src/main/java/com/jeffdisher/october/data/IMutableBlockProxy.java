package com.jeffdisher.october.data;

import java.nio.ByteBuffer;

import com.jeffdisher.october.types.CraftOperation;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;


/**
 * Adds high-level methods for writing to a single block.
 */
public interface IMutableBlockProxy extends IBlockProxy
{
	/**
	 * Sets the given item type and clears any other aspects.  This means that any aspects from the previous block type
	 * must be read first.
	 * 
	 * @param item The new item type.
	 */
	void setItemAndClear(Item item);
	/**
	 * Stores the given inventory for the block.  Note that an empty inventory will be stored as a null.
	 * 
	 * @param inv The new inventory (not null).
	 */
	void setInventory(Inventory inv);
	/**
	 * @param damage The new damage value.
	 */
	void setDamage(short damage);
	/**
	 * @param crafting The new crafting operation (can be null).
	 */
	void setCrafting(CraftOperation crafting);
	/**
	 * Serializes all known aspects for this block into the given buffer.
	 * 
	 * @param buffer The buffer which should contain the serialized value of this block's aspects.
	 */
	void serializeToBuffer(ByteBuffer buffer);
	/**
	 * Deserializes the given buffer, over-writing all block aspects with its contents.
	 * 
	 * @param buffer The buffer which will be deserialized to populate this block's aspects.
	 */
	void deserializeFromBuffer(ByteBuffer buffer);
}
