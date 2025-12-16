package com.jeffdisher.october.types;

import java.util.List;

import com.jeffdisher.october.properties.PropertyType;


public record Enchantment(Block table
	, long millisToApply
	, Item targetItem
	, List<Item> consumedItems
	, PropertyType<Byte> enchantmentToApply
)
{
}
