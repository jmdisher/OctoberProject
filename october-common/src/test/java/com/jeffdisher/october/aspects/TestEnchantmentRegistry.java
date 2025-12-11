package com.jeffdisher.october.aspects;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.logic.PropertyHelpers;
import com.jeffdisher.october.properties.PropertyRegistry;
import com.jeffdisher.october.properties.PropertyType;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Enchantment;
import com.jeffdisher.october.types.Infusion;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.NonStackableItem;


public class TestEnchantmentRegistry
{
	private static Environment ENV;
	private static Block ENCHANTING_TABLE;
	private static Item IRON_PICKAXE;
	private static Item ITEM_STONE;
	private static Item ITEM_STONE_BRICK;
	private static Block STONE_BRICK;
	private static Item IRON_INGOT;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		ENCHANTING_TABLE = ENV.blocks.fromItem(ENV.items.getItemById("op.enchanting_table"));
		IRON_PICKAXE = ENV.items.getItemById("op.iron_pickaxe");
		ITEM_STONE = ENV.items.getItemById("op.stone");
		ITEM_STONE_BRICK = ENV.items.getItemById("op.stone_brick");
		STONE_BRICK = ENV.blocks.fromItem(ITEM_STONE_BRICK);
		IRON_INGOT = ENV.items.getItemById("op.iron_ingot");
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void getAll() throws Throwable
	{
		Assert.assertFalse(ENV.enchantments.allEnchantments(ENCHANTING_TABLE).isEmpty());
		Assert.assertFalse(ENV.enchantments.allInfusions(ENCHANTING_TABLE).isEmpty());
		Assert.assertNull(ENV.enchantments.allEnchantments(STONE_BRICK));
		Assert.assertNull(ENV.enchantments.allInfusions(STONE_BRICK));
	}

	@Test
	public void validEnchantment() throws Throwable
	{
		NonStackableItem pickaxe = PropertyHelpers.newItemWithDefaults(ENV, IRON_PICKAXE);
		Enchantment enchantment = ENV.enchantments.getEnchantment(ENCHANTING_TABLE, pickaxe, List.of(ITEM_STONE, IRON_INGOT, ITEM_STONE, IRON_INGOT));
		Assert.assertNotNull(enchantment);
		Assert.assertTrue(EnchantmentRegistry.canApplyToTarget(pickaxe, ENV.enchantments.enchantmentForNumber(1)));
	}

	@Test
	public void enchantmentWrongTarget() throws Throwable
	{
		NonStackableItem axe = PropertyHelpers.newItemWithDefaults(ENV, ENV.items.getItemById("op.copper_axe"));
		Enchantment enchantment = ENV.enchantments.getEnchantment(ENCHANTING_TABLE, axe, List.of(ITEM_STONE, ITEM_STONE, IRON_INGOT, IRON_INGOT));
		Assert.assertNull(enchantment);
	}

	@Test
	public void enchantmentMissingItem() throws Throwable
	{
		NonStackableItem pickaxe = PropertyHelpers.newItemWithDefaults(ENV, IRON_PICKAXE);
		Enchantment enchantment = ENV.enchantments.getEnchantment(ENCHANTING_TABLE, pickaxe, List.of(ITEM_STONE, ITEM_STONE, IRON_INGOT));
		Assert.assertNull(enchantment);
	}

	@Test
	public void enchantmentOverflow() throws Throwable
	{
		NonStackableItem pickaxe = PropertyHelpers.newItemWithDefaults(ENV, IRON_PICKAXE);
		Map<PropertyType<?>, Object> properties = new HashMap<>(pickaxe.properties());
		properties.put(PropertyRegistry.ENCHANT_DURABILITY, (byte)127);
		pickaxe = new NonStackableItem(IRON_PICKAXE, properties);
		Enchantment enchantment = ENV.enchantments.getEnchantment(ENCHANTING_TABLE, pickaxe, List.of(ITEM_STONE, ITEM_STONE, IRON_INGOT, IRON_INGOT));
		Assert.assertNull(enchantment);
		Assert.assertFalse(EnchantmentRegistry.canApplyToTarget(pickaxe, ENV.enchantments.enchantmentForNumber(1)));
	}

	@Test
	public void validInfusion() throws Throwable
	{
		Infusion infusion = ENV.enchantments.getInfusion(ENCHANTING_TABLE, ITEM_STONE_BRICK, List.of(ITEM_STONE, IRON_INGOT, ITEM_STONE, IRON_INGOT));
		Assert.assertNotNull(infusion);
	}

	@Test
	public void infusionMissingItem() throws Throwable
	{
		Infusion infusion = ENV.enchantments.getInfusion(ENCHANTING_TABLE, ITEM_STONE_BRICK, List.of(ITEM_STONE, ITEM_STONE, IRON_INGOT));
		Assert.assertNull(infusion);
	}

	@Test
	public void canonicalSort() throws Throwable
	{
		Assert.assertEquals(List.of(ITEM_STONE, ITEM_STONE, IRON_INGOT, IRON_INGOT), EnchantmentRegistry.getCanonicallySortedList(List.of(ITEM_STONE, IRON_INGOT, IRON_INGOT, ITEM_STONE)));
	}
}
