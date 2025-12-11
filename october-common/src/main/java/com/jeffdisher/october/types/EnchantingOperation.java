package com.jeffdisher.october.types;

import java.util.List;


/**
 * The EnchantingOperation is much like CraftingOperation but has 2 phases:
 * -"charging" - This is where time passes in order to "charge" the enchantment
 * -"completing" - This is when the enchantment is fully charged and is waiting for required items to be draw in from
 *  the pedestals in order to succeed or fail at that time.
 * Note that there must be precisely one of enchantment OR infusion.
 */
public record EnchantingOperation(long chargedMillis
	, Enchantment enchantment
	, Infusion infusion
	, ItemSlot targetItem
	, List<ItemSlot> consumedItems
)
{
}
