package com.jeffdisher.october.logic;

import java.util.Map;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.properties.PropertyRegistry;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.NonStackableItem;


public class TestPropertyHelpers
{
	private static Environment ENV;
	private static Item IRON_SWORD;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		IRON_SWORD = ENV.items.getItemById("op.iron_sword");
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void durability()
	{
		NonStackableItem item = PropertyHelpers.newItemWithDefaults(ENV, IRON_SWORD);
		Assert.assertEquals(ENV.durability.getDurability(IRON_SWORD), PropertyHelpers.getDurability(item));
		
		item = PropertyHelpers.reduceDurabilityOrBreak(item, 5, 100);
		Assert.assertEquals(ENV.durability.getDurability(IRON_SWORD) - 5, PropertyHelpers.getDurability(item));
	}

	@Test
	public void name()
	{
		NonStackableItem item = PropertyHelpers.newItemWithDefaults(ENV, IRON_SWORD);
		Assert.assertEquals(IRON_SWORD.name(), PropertyHelpers.getName(item));
		
		String name = "custom name";
		item = new NonStackableItem(IRON_SWORD, Map.of(PropertyRegistry.NAME, name));
		Assert.assertEquals(name, PropertyHelpers.getName(item));
		
		String tooLong = "this name is too long and will be rejected";
		item = new NonStackableItem(IRON_SWORD, Map.of(PropertyRegistry.NAME, tooLong));
		Assert.assertEquals(IRON_SWORD.name(), PropertyHelpers.getName(item));
	}

	@Test
	public void enchantedDurability()
	{
		NonStackableItem item = new NonStackableItem(IRON_SWORD, Map.of(PropertyRegistry.DURABILITY, ENV.durability.getDurability(IRON_SWORD)
			, PropertyRegistry.ENCHANT_DURABILITY, (byte)1
		));
		Assert.assertEquals(ENV.durability.getDurability(IRON_SWORD), PropertyHelpers.getDurability(item));
		
		item = PropertyHelpers.reduceDurabilityOrBreak(item, 5, 3);
		Assert.assertEquals(ENV.durability.getDurability(IRON_SWORD), PropertyHelpers.getDurability(item));
		
		item = PropertyHelpers.reduceDurabilityOrBreak(item, 5, 2);
		Assert.assertEquals(ENV.durability.getDurability(IRON_SWORD) - 5, PropertyHelpers.getDurability(item));
	}

	@Test
	public void enchantedWeaponMeleeDamage()
	{
		NonStackableItem item = new NonStackableItem(IRON_SWORD, Map.of(PropertyRegistry.DURABILITY, ENV.durability.getDurability(IRON_SWORD)
			, PropertyRegistry.ENCHANT_WEAPON_MELEE, (byte)5
		));
		Assert.assertEquals(15, PropertyHelpers.getWeaponMeleeDamage(ENV, item));
	}
}
