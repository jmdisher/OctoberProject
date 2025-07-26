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
	/**
	 * The maximum length of a NAME property (they will be discarded if the string is greater than this).
	 */
	public static final int NAME_MAX_LENGTH = 32;

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

	public static NonStackableItem reduceDurabilityOrBreak(NonStackableItem old, int durabilityToRemove, int randomNumberTo255)
	{
		// First, find the durability (if there isn't one, this can't be broken).
		NonStackableItem newItem;
		
		// We will walk the list of properties to check for durability and enchantments, then apply them at the end.
		// Any other properties will just be added to the new list.
		int durability = 0;
		int enchantmentLevel = 0;
		List<Property<?>> props = new ArrayList<>();
		for (Property<?> prop : old.properties())
		{
			if (PropertyRegistry.DURABILITY == prop.type())
			{
				durability = PropertyRegistry.DURABILITY.type().cast(prop.value());
				// If there is a durability, it must be > 0.
				Assert.assertTrue(durability > 0);
			}
			else if (PropertyRegistry.ENCHANT_DURABILITY == prop.type())
			{
				enchantmentLevel = PropertyRegistry.ENCHANT_DURABILITY.type().cast(prop.value());
				// This level must be > 0.
				Assert.assertTrue(enchantmentLevel > 0);
				// We re-add this since it isn't changing.
				props.add(prop);
			}
			else
			{
				// Just add this to the list.
				props.add(prop);
			}
		}
		
		// Check to see if this even can be damaged.
		if (durability > 0)
		{
			// Now, check to see if the enchantment should be applied.
			int sample = Math.floorMod(randomNumberTo255, 1 + enchantmentLevel);
			if (0 == sample)
			{
				// Apply this change
				if (durability > durabilityToRemove)
				{
					// Normal wear.
					Property<Integer> newProp = new Property<Integer>(PropertyRegistry.DURABILITY, durability - durabilityToRemove);
					props.add(newProp);
					newItem = new NonStackableItem(old.type(), Collections.unmodifiableList(props));
				}
				else
				{
					// Broken.
					newItem = null;
				}
			}
			else
			{
				// No change.
				newItem = old;
			}
		}
		else
		{
			// No durability for this type.
			newItem = old;
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

	public static String getName(NonStackableItem item)
	{
		// We default to the type name.
		String name = item.type().name();
		for (Property<?> prop : item.properties())
		{
			if (PropertyRegistry.NAME == prop.type())
			{
				String newName = PropertyRegistry.NAME.type().cast(prop.value());
				if ((0 == newName.length()) || (newName.length() > NAME_MAX_LENGTH))
				{
					// This is invalid so just ignore it.
				}
				else
				{
					name = newName;
				}
				break;
			}
		}
		return name;
	}
}
