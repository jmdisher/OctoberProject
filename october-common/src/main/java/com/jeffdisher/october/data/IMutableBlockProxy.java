package com.jeffdisher.october.data;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.OrientationAspect;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.CraftOperation;
import com.jeffdisher.october.types.EnchantingOperation;
import com.jeffdisher.october.types.FuelState;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.ItemSlot;


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
	void setDamage(int damage);
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
	 * @param flags The new flags for this block (as defined in FlagsAspect).
	 */
	void setFlags(byte flags);
	/**
	 * Serializes all known aspects for this block into the given buffer.
	 * 
	 * @param buffer The buffer which should contain the serialized value of this block's aspects.
	 */
	void serializeToBuffer(ByteBuffer buffer);
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
	/**
	 * Requests a MutationBlockPeriodic mutation be run against this block's location after at least millisToDelay have
	 * passed.  Note that, if there is already a mutation scheduled for this location, only the one with the earliest
	 * due time will be honoured.
	 * 
	 * @param millisToDelay Milliseconds to delay before running the mutation (must be positive).
	 */
	void requestFutureMutation(long millisToDelay);
	/**
	 * Sets the orientation of this block (currently just used for multi-block roots).
	 * 
	 * @param direction The direction to set.
	 */
	void setOrientation(OrientationAspect.Direction direction);
	/**
	 * Sets the location of the root block of this multi-block.
	 * 
	 * @param rootLocation The multi-block root location.
	 */
	void setMultiBlockRoot(AbsoluteLocation rootLocation);
	/**
	 * @param slot The special ItemSlot for this block, null to clear (doesn't check if it can have one).
	 */
	void setSpecialSlot(ItemSlot slot);
	/**
	 * @param operation The new enchanting operation (can be null).
	 */
	void setEnchantingOperation(EnchantingOperation operation);
}
