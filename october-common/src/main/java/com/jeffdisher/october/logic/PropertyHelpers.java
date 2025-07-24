package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.properties.Property;
import com.jeffdisher.october.properties.PropertyRegistry;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.utils.Assert;


/**
 * High-level helpers used to interact with NonStackableItem instances.
 */
public class PropertyHelpers
{
	public static NonStackableItem newItemWithDefaults(Environment env, Item item)
	{
		// Can only be called for non-stackable.
		Assert.assertTrue(!env.durability.isStackable(item));
		
		// Find the default durability for this item type.
		int startingDurability = env.durability.getDurability(item);
		// We only want to add the durability property if this can be broken.
		List<Property<?>> properties = (startingDurability > 0)
			? List.of(new Property<Integer>(PropertyRegistry.DURABILITY, startingDurability))
			: List.of()
		;
		return new NonStackableItem(item, properties);
	}

	public static NonStackableItem reduceDurabilityOrBreak(NonStackableItem old, int durabilityToRemove)
	{
		// First, find the durability (if there isn't one, this can't be broken).
		NonStackableItem newItem;
		// TODO: Generalize this once we have multiple types.
		if (0 == old.properties().size())
		{
			// This is unbreakable.
			newItem = old;
		}
		else
		{
			// We will rebuild the list as we go but set it to null if this should break.
			List<Property<?>> props = new ArrayList<>();
			for (Property<?> prop : old.properties())
			{
				if (PropertyRegistry.DURABILITY == prop.type())
				{
					int durability = PropertyRegistry.DURABILITY.type().cast(prop.value());
					// If there is a durability, it must be > 0.
					Assert.assertTrue(durability > 0);
					if (durability > durabilityToRemove)
					{
						// Normal wear.
						Property<Integer> newProp = new Property<Integer>(PropertyRegistry.DURABILITY, durability - durabilityToRemove);
						props.add(newProp);
					}
					else
					{
						// Broken.
						props = null;
						break;
					}
				}
				else
				{
					// Just add this to the list.
					props.add(prop);
				}
			}
			
			if (null != props)
			{
				// Normal wear.
				newItem = new NonStackableItem(old.type(), Collections.unmodifiableList(props));
			}
			else
			{
				// Broke.
				newItem = null;
			}
		}
		return newItem;
	}

	public static int getDurability(NonStackableItem item)
	{
		// If there is no durability, it is unbreakable (0).
		int durability = 0;
		for (Property<?> prop : item.properties())
		{
			if (PropertyRegistry.DURABILITY == prop.type())
			{
				durability = PropertyRegistry.DURABILITY.type().cast(prop.value());
				// If there is a durability, it must be > 0.
				Assert.assertTrue(durability > 0);
				break;
			}
		}
		return durability;
	}
}
