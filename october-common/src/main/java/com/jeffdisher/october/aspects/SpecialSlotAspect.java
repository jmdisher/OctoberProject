package com.jeffdisher.october.aspects;

import java.util.Collections;
import java.util.Set;

import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.utils.Assert;


/**
 * Helpers for interacting with the SPECIAL_ITEM_SLOT aspect.
 */
public class SpecialSlotAspect
{
	public static final String ID_PEDESTAL = "op.pedestal";

	public static SpecialSlotAspect load(ItemRegistry items, BlockAspect blocks)
	{
		// TODO:  Convert this logic and constant into some declarative data file.
		Block pedestal = blocks.fromItem(items.getItemById(ID_PEDESTAL));
		Assert.assertTrue(null != pedestal);
		
		Set<Block> hasSpecial = Set.of(pedestal);
		Set<Block> canSwapOut = Set.of(pedestal);
		return new SpecialSlotAspect(hasSpecial, canSwapOut);
	}


	private final Set<Block> _hasSpecial;
	private final Set<Block> _canSwapOut;

	private SpecialSlotAspect(Set<Block> hasSpecial, Set<Block> canSwapOut)
	{
		_hasSpecial = Collections.unmodifiableSet(hasSpecial);
		_canSwapOut = Collections.unmodifiableSet(canSwapOut);
	}

	/**
	 * Checks if this block type can have a special slot.
	 * 
	 * @param block The block type.
	 * @return True if this block type can have a special slot, false if it should always be left null.
	 */
	public boolean hasSpecialSlot(Block block)
	{
		return _hasSpecial.contains(block);
	}

	/**
	 * Checks if the block type both has a special slot and should allow the contents to be removed or dropped when
	 * broken.  If not, the contents cannot be removed and are destroyed if the block is destroyed.
	 * 
	 * @param block The block type.
	 * @return True if the special slot contents can be removed or drop when the block is broken (otherwise, destroyed
	 * when the block is broken).
	 */
	public boolean canRemoveOrDrop(Block block)
	{
		return _canSwapOut.contains(block);
	}
}
