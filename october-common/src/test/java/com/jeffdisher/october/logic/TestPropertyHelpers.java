package com.jeffdisher.october.logic;

import java.util.Map;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.properties.PropertyRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.NonStackableItem;


public class TestPropertyHelpers
{
	private static Environment ENV;
	private static Item IRON_SWORD;
	private static Item IRON_PICKAXE;
	private static Item PORTAL_ORB;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		IRON_SWORD = ENV.items.getItemById("op.iron_sword");
		IRON_PICKAXE = ENV.items.getItemById("op.iron_pickaxe");
		PORTAL_ORB = ENV.items.getItemById("op.portal_orb");
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

	@Test
	public void enchantedToolMaterialMultipler()
	{
		NonStackableItem item = new NonStackableItem(IRON_PICKAXE, Map.of(PropertyRegistry.DURABILITY, ENV.durability.getDurability(IRON_PICKAXE)
			, PropertyRegistry.ENCHANT_TOOL_EFFICIENCY, (byte)5
		));
		Assert.assertEquals(17, PropertyHelpers.getToolMaterialEfficiency(ENV, item));
	}

	@Test
	public void orbLocation()
	{
		NonStackableItem item = PropertyHelpers.newItemWithDefaults(ENV, PORTAL_ORB);
		Assert.assertEquals(PORTAL_ORB.name(), PropertyHelpers.getName(item));
		Assert.assertEquals(0, PropertyHelpers.getDurability(item));
		Assert.assertEquals(null, PropertyHelpers.getLocation(item));
		
		AbsoluteLocation location = new AbsoluteLocation(5, -99, 26);
		item = new NonStackableItem(PORTAL_ORB, Map.of(PropertyRegistry.LOCATION, location));
		Assert.assertEquals(PORTAL_ORB.name(), PropertyHelpers.getName(item));
		Assert.assertEquals(0, PropertyHelpers.getDurability(item));
		Assert.assertEquals(location, PropertyHelpers.getLocation(item));
	}

	@Test
	public void getEnchantmentLevel()
	{
		NonStackableItem item = new NonStackableItem(IRON_SWORD, Map.of(PropertyRegistry.DURABILITY, ENV.durability.getDurability(IRON_SWORD)
			, PropertyRegistry.ENCHANT_WEAPON_MELEE, (byte)5
		));
		byte value = PropertyHelpers.getBytePropertyValue(item.properties(), PropertyRegistry.ENCHANT_WEAPON_MELEE);
		Assert.assertEquals((byte)5, value);
	}
}
