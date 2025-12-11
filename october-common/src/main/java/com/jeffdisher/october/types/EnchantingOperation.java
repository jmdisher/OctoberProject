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
	, List<ItemSlot> consumedItems
)
{
	/**
	 * A helper to dig into the appropriate sub-element.
	 * 
	 * @return The required central type of whatever the underlying operation is.
	 */
	public Item getRequiredCentralType()
	{
		return (null != this.enchantment)
			? this.enchantment.targetItem()
			: this.infusion.centralItem()
		;
	}

	/**
	 * A helper to dig into the appropriate sub-element.
	 * 
	 * @return The required millis of charge which must be applied to whatever the underlying operation is.
	 */
	public long getRequiredChargeMillis()
	{
		return (null != this.enchantment)
			? this.enchantment.millisToApply()
			: this.infusion.millisToApply()
		;
	}

	/**
	 * A helper to dig into the appropriate sub-element.
	 * 
	 * @return The required items the underlying operation must consume, once charged, in order to complete and be
	 * applied to the central item.
	 */
	public List<Item> getRequiredItems()
	{
		return (null != this.enchantment)
			? this.enchantment.consumedItems()
			: this.infusion.consumedItems()
		;
	}
}
