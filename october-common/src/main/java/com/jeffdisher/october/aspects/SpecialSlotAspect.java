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
	public static final String ID_PORTAL_KEYSTONE = "op.portal_keystone";
	public static final String ID_ENCHANTING_TABLE = "op.enchanting_table";

	public static SpecialSlotAspect load(ItemRegistry items, BlockAspect blocks)
	{
		// TODO:  Convert this logic and constant into some declarative data file.
		Block pedestal = blocks.fromItem(items.getItemById(ID_PEDESTAL));
		Assert.assertTrue(null != pedestal);
		Block keystone = blocks.fromItem(items.getItemById(ID_PORTAL_KEYSTONE));
		Assert.assertTrue(null != keystone);
		Block enchantingTable = blocks.fromItem(items.getItemById(ID_ENCHANTING_TABLE));
		Assert.assertTrue(null != enchantingTable);
		
		Set<Block> hasSpecial = Set.of(pedestal, keystone, enchantingTable);
		return new SpecialSlotAspect(hasSpecial);
	}


	private final Set<Block> _hasSpecial;

	private SpecialSlotAspect(Set<Block> hasSpecial)
	{
		_hasSpecial = Collections.unmodifiableSet(hasSpecial);
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
}
