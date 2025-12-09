package com.jeffdisher.october.logic;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.properties.PropertyRegistry;
import com.jeffdisher.october.properties.PropertyType;
import com.jeffdisher.october.types.AbsoluteLocation;
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
		Map<PropertyType<?>, Object> properties = (startingDurability > 0)
			? Map.of(PropertyRegistry.DURABILITY, startingDurability)
			: Map.of()
		;
		return new NonStackableItem(item, properties);
	}

	public static NonStackableItem reduceDurabilityOrBreak(NonStackableItem old, int durabilityToRemove, int randomNumberTo255)
	{
		// First, find the durability (if there isn't one, this can't be broken).
		NonStackableItem newItem;
		
		// We will walk the list of properties to check for durability and enchantments, then apply them at the end.
		// Any other properties will just be added to the new list.
		int durability = _getValue(old.properties(), PropertyRegistry.DURABILITY, 0);
		int enchantmentLevel = _getValue(old.properties(), PropertyRegistry.ENCHANT_DURABILITY, (byte)0);
		
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
					Map<PropertyType<?>, Object> newProps = new HashMap<>(old.properties());
					int newDurability = durability - durabilityToRemove;
					newProps.put(PropertyRegistry.DURABILITY, newDurability);
					newItem = new NonStackableItem(old.type(), Collections.unmodifiableMap(newProps));
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
		int durability = _getValue(item.properties(), PropertyRegistry.DURABILITY, 0);
		return durability;
	}

	public static String getName(NonStackableItem item)
	{
		// We default to the type name.
		String defaultName = item.type().name();
		String name = _getValue(item.properties(), PropertyRegistry.NAME, item.type().name());
		if ((0 == name.length()) || (name.length() > NAME_MAX_LENGTH))
		{
			// This is invalid so just ignore it.
			name = defaultName;
		}
		return name;
	}

	public static int getWeaponMeleeDamage(Environment env, NonStackableItem weapon)
	{
		// Start with the basic damage.
		int baseDamage = env.tools.toolWeaponDamage(weapon.type());
		int enchantment = _getValue(weapon.properties(), PropertyRegistry.ENCHANT_WEAPON_MELEE, (byte)0);
		
		// We just add them together (could be different in the future).
		int damage = baseDamage + enchantment;
		
		// Clamp this at byte max.
		return Math.min(damage, Byte.MAX_VALUE);
	}

	public static int getToolMaterialEfficiency(Environment env, NonStackableItem tool)
	{
		// Start with the basic damage.
		int baseMultiplier = env.tools.toolSpeedModifier(tool.type());
		int enchantment = _getValue(tool.properties(), PropertyRegistry.ENCHANT_TOOL_EFFICIENCY, (byte)0);
		
		// We just add them together (could be different in the future).
		int damage = baseMultiplier + enchantment;
		
		// Clamp this at byte max.
		return Math.min(damage, Byte.MAX_VALUE);
	}

	public static AbsoluteLocation getLocation(NonStackableItem item)
	{
		// This is null in most cases.
		return _getValue(item.properties(), PropertyRegistry.LOCATION, null);
	}

	public static byte getBytePropertyValue(Map<PropertyType<?>, Object> properties, PropertyType<Byte> type)
	{
		return _getValue(properties, type, (byte)0);
	}


	private static <T> T _getValue(Map<PropertyType<?>, Object> properties, PropertyType<T> type, T missingValue)
	{
		T value = missingValue;
		if (properties.containsKey(type))
		{
			Object raw = properties.get(type);
			value = type.type().cast(raw);
		}
		return value;
	}
}
