package com.jeffdisher.october.data;

import java.nio.ByteBuffer;

import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.CraftOperation;
import com.jeffdisher.october.types.FuelState;
import com.jeffdisher.october.types.Inventory;


/**
 * Adds high-level methods for writing to a single block.
 */
public interface IMutableBlockProxy extends IBlockProxy
{
	/**
	 * Sets the given block type and clears any other aspects.  This means that any aspects from the previous block type
	 * must be read first.
	 * 
	 * @param block The new block type.
	 */
	void setBlockAndClear(Block block);
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
	 * Stores the given fuel state for the block.  Note that an empty fuel state will be stored as a null.
	 * 
	 * @param fuel The new fuel state (not null).
	 */
	void setFuel(FuelState fuel);
	/**
	 * @param light The new light level for this block.
	 */
	void setLight(byte light);
	/**
	 * @param logic The new logic level for this block.
	 */
	void setLogic(byte logic);
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
	/**
	 * Note that ephemeral state only exists within a single tick (always null in speculative projection) so this will
	 * be null if not set on THIS tick.
	 * Note that this type is implementation dependent so multiple mutation types which want to use this will need to be
	 * aware of each other.
	 * 
	 * @return Returns the result of the last "setEphemeralState(Object)" call from within this tick.
	 */
	Object getEphemeralState();
	/**
	 * Sets the ephemeral state for this tick which can later be requested with "getEphemeralState()".  Note that this
	 * will always reset to null at the beginning of a tick (resets on every mutation in speculative projection).
	 * 
	 * @param state The state object to set.
	 */
	void setEphemeralState(Object state);
}
