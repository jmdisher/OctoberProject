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
	private static Item WOODEN_CHISEL;
	private static Item IRON_SWORD;
	private static Item DIAMOND;
	private static Item COPPER_INGOT;
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
		WOODEN_CHISEL = ENV.items.getItemById("op.wooden_chisel");
		IRON_SWORD = ENV.items.getItemById("op.iron_sword");
		DIAMOND = ENV.items.getItemById("op.diamond");
		COPPER_INGOT = ENV.items.getItemById("op.copper_ingot");
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
		Assert.assertTrue(EnchantmentRegistry.canApplyToTarget(pickaxe, enchantment.enchantmentToApply()));
	}

	@Test
	public void enchantmentWrongTarget() throws Throwable
	{
		// Orbs have properties but not enchantments.
		NonStackableItem orb = PropertyHelpers.newItemWithDefaults(ENV, ENV.items.getItemById("op.portal_orb"));
		Enchantment enchantment = ENV.enchantments.getEnchantment(ENCHANTING_TABLE, orb, List.of(ITEM_STONE, ITEM_STONE, IRON_INGOT, IRON_INGOT));
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
		Assert.assertFalse(EnchantmentRegistry.canApplyToTarget(pickaxe, PropertyRegistry.ENCHANT_DURABILITY));
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

	@Test
	public void canEnchant() throws Throwable
	{
		Assert.assertTrue(ENV.enchantments.canEnchant(ENCHANTING_TABLE));
		Assert.assertFalse(ENV.enchantments.canEnchant(STONE_BRICK));
	}

	@Test
	public void variousValidEnchantments() throws Throwable
	{
		// We use the chisel here, not the pickaxe, since the pickaxe technically does enough damage to have the melee damage enchantment.
		NonStackableItem chisel = PropertyHelpers.newItemWithDefaults(ENV, WOODEN_CHISEL);
		NonStackableItem sword = PropertyHelpers.newItemWithDefaults(ENV, IRON_SWORD);
		Enchantment durabilityChisel = ENV.enchantments.getEnchantment(ENCHANTING_TABLE, chisel, List.of(ITEM_STONE, IRON_INGOT, ITEM_STONE, IRON_INGOT));
		Enchantment durabilitySword = ENV.enchantments.getEnchantment(ENCHANTING_TABLE, sword, List.of(ITEM_STONE, IRON_INGOT, ITEM_STONE, IRON_INGOT));
		Enchantment damageChisel = ENV.enchantments.getEnchantment(ENCHANTING_TABLE, chisel, List.of(DIAMOND, COPPER_INGOT, IRON_INGOT, IRON_INGOT));
		Enchantment damageSword = ENV.enchantments.getEnchantment(ENCHANTING_TABLE, sword, List.of(DIAMOND, COPPER_INGOT, IRON_INGOT, IRON_INGOT));
		Enchantment efficiencyChisel = ENV.enchantments.getEnchantment(ENCHANTING_TABLE, chisel, List.of(COPPER_INGOT, COPPER_INGOT, IRON_INGOT, IRON_INGOT));
		Enchantment efficiencySword = ENV.enchantments.getEnchantment(ENCHANTING_TABLE, sword, List.of(COPPER_INGOT, COPPER_INGOT, IRON_INGOT, IRON_INGOT));
		
		Assert.assertEquals(10_000L, durabilityChisel.millisToApply());
		Assert.assertEquals(10_000L, durabilitySword.millisToApply());
		Assert.assertNull(damageChisel);
		Assert.assertEquals(15_000L, damageSword.millisToApply());
		Assert.assertEquals(20_000L, efficiencyChisel.millisToApply());
		Assert.assertNull(efficiencySword);
	}

	@Test
	public void checkApplicationToTarget() throws Throwable
	{
		NonStackableItem pickaxe = PropertyHelpers.newItemWithDefaults(ENV, IRON_PICKAXE);
		Map<PropertyType<?>, Object> properties = new HashMap<>(pickaxe.properties());
		properties.put(PropertyRegistry.ENCHANT_DURABILITY, (byte)127);
		NonStackableItem fullPickaxe = new NonStackableItem(IRON_PICKAXE, properties);
		NonStackableItem sword = PropertyHelpers.newItemWithDefaults(ENV, IRON_SWORD);
		
		Assert.assertTrue(EnchantmentRegistry.canApplyToTarget(pickaxe, PropertyRegistry.ENCHANT_DURABILITY));
		Assert.assertFalse(EnchantmentRegistry.canApplyToTarget(fullPickaxe, PropertyRegistry.ENCHANT_DURABILITY));
		Assert.assertTrue(EnchantmentRegistry.canApplyToTarget(sword, PropertyRegistry.ENCHANT_DURABILITY));
		Assert.assertTrue(EnchantmentRegistry.canApplyToTarget(pickaxe, PropertyRegistry.ENCHANT_TOOL_EFFICIENCY));
		Assert.assertTrue(EnchantmentRegistry.canApplyToTarget(fullPickaxe, PropertyRegistry.ENCHANT_TOOL_EFFICIENCY));
		Assert.assertFalse(EnchantmentRegistry.canApplyToTarget(sword, PropertyRegistry.ENCHANT_TOOL_EFFICIENCY));
		Assert.assertTrue(EnchantmentRegistry.canApplyToTarget(pickaxe, PropertyRegistry.ENCHANT_WEAPON_MELEE));
		Assert.assertTrue(EnchantmentRegistry.canApplyToTarget(fullPickaxe, PropertyRegistry.ENCHANT_WEAPON_MELEE));
		Assert.assertTrue(EnchantmentRegistry.canApplyToTarget(sword, PropertyRegistry.ENCHANT_WEAPON_MELEE));
	}
}
