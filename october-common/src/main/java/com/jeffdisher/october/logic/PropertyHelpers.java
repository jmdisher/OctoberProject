package com.jeffdisher.october.logic;

import com.jeffdisher.october.properties.Property;
import com.jeffdisher.october.properties.PropertyRegistry;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.NonStackableItem;


/**
 * High-level helpers used to interact with NonStackableItem instances.
 */
public class PropertyHelpers
{
	public static NonStackableItem newItem(Item item, int durability)
	{
		return new NonStackableItem(item, new Property<Integer>(PropertyRegistry.DURABILITY, durability));
	}

	public static NonStackableItem withReplacedDurability(NonStackableItem old, int durability)
	{
		return new NonStackableItem(old.type(), new Property<Integer>(PropertyRegistry.DURABILITY, durability));
	}
}
