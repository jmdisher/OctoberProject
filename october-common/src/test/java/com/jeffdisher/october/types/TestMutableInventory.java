package com.jeffdisher.october.types;

import java.util.Map;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;


public class TestMutableInventory
{
	private static int INVENTORY_SIZE = 100;
	private static Environment ENV;
	private static Item STONE_ITEM;
	private static Item LOG_ITEM;
	private static Item PLANK_ITEM;
	private static Item DIRT_ITEM;
	private static Item SWORD_ITEM;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		STONE_ITEM = ENV.items.getItemById("op.stone");
		LOG_ITEM = ENV.items.getItemById("op.log");
		PLANK_ITEM = ENV.items.getItemById("op.plank");
		DIRT_ITEM = ENV.items.getItemById("op.dirt");
		SWORD_ITEM = ENV.items.getItemById("op.diamond_sword");
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void createFromEmpty() throws Throwable
	{
		Inventory original = Inventory.start(INVENTORY_SIZE).finish();
		MutableInventory inv = new MutableInventory(original);
		
		Inventory frozen = inv.freeze();
		Assert.assertEquals(original.maxEncumbrance, frozen.maxEncumbrance);
		Assert.assertEquals(original.sortedKeys().size(), frozen.sortedKeys().size());
		Assert.assertEquals(original.currentEncumbrance, frozen.currentEncumbrance);
	}

	@Test
	public void basicAddition() throws Throwable
	{
		Inventory original = Inventory.start(INVENTORY_SIZE).finish();
		MutableInventory inv = new MutableInventory(original);
		Assert.assertEquals(0, inv.getCount(LOG_ITEM));
		Assert.assertEquals(25, inv.maxVacancyForItem(LOG_ITEM));
		Assert.assertTrue(inv.addAllItems(LOG_ITEM, 4));
		Assert.assertEquals(4, inv.getCount(LOG_ITEM));
		Assert.assertEquals(21, inv.maxVacancyForItem(LOG_ITEM));
		Assert.assertEquals(4, inv.addItemsBestEfforts(LOG_ITEM, 4));
		Assert.assertEquals(8, inv.getCount(LOG_ITEM));
		Assert.assertEquals(17, inv.maxVacancyForItem(LOG_ITEM));
		
		Inventory frozen = inv.freeze();
		Assert.assertEquals(original.maxEncumbrance, frozen.maxEncumbrance);
		Assert.assertEquals(1, frozen.sortedKeys().size());
		Assert.assertEquals(32, frozen.currentEncumbrance);
	}

	@Test
	public void basicRemoval() throws Throwable
	{
		Inventory original = Inventory.start(INVENTORY_SIZE).addStackable(LOG_ITEM, 2).finish();
		MutableInventory inv = new MutableInventory(original);
		Assert.assertEquals(2, inv.getCount(LOG_ITEM));
		Assert.assertEquals(23, inv.maxVacancyForItem(LOG_ITEM));
		inv.removeStackableItems(LOG_ITEM, 1);
		Assert.assertEquals(1, inv.getCount(LOG_ITEM));
		Assert.assertEquals(24, inv.maxVacancyForItem(LOG_ITEM));
		inv.removeStackableItems(LOG_ITEM, 1);
		Assert.assertEquals(0, inv.getCount(LOG_ITEM));
		Assert.assertEquals(25, inv.maxVacancyForItem(LOG_ITEM));
		
		Inventory frozen = inv.freeze();
		Assert.assertEquals(original.maxEncumbrance, frozen.maxEncumbrance);
		Assert.assertEquals(0, frozen.sortedKeys().size());
		Assert.assertEquals(0, frozen.currentEncumbrance);
	}

	@Test
	public void addRemoveUnchanged() throws Throwable
	{
		Inventory original = Inventory.start(INVENTORY_SIZE).addStackable(LOG_ITEM, 2).finish();
		MutableInventory inv = new MutableInventory(original);
		inv.addAllItems(STONE_ITEM, 1);
		inv.removeStackableItems(LOG_ITEM, 1);
		inv.removeStackableItems(STONE_ITEM, 1);
		inv.addAllItems(LOG_ITEM, 1);
		
		Inventory frozen = inv.freeze();
		Assert.assertTrue(original == frozen);
	}

	@Test
	public void clearAndAdd() throws Throwable
	{
		Inventory original = Inventory.start(INVENTORY_SIZE).addStackable(STONE_ITEM, 1).finish();
		MutableInventory inv = new MutableInventory(original);
		Assert.assertEquals(1, inv.getCount(STONE_ITEM));
		Assert.assertTrue(inv.addAllItems(LOG_ITEM, 4));
		inv.clearInventory(null);
		Assert.assertEquals(0, inv.getCount(STONE_ITEM));
		Assert.assertEquals(0, inv.getCount(LOG_ITEM));
		Assert.assertTrue(inv.addAllItems(LOG_ITEM, 1));
		
		Inventory frozen = inv.freeze();
		Assert.assertEquals(original.maxEncumbrance, frozen.maxEncumbrance);
		Assert.assertEquals(1, frozen.sortedKeys().size());
		Assert.assertEquals(1, frozen.getCount(LOG_ITEM));
		Assert.assertEquals(4, frozen.currentEncumbrance);
	}

	@Test
	public void clearAndReplace() throws Throwable
	{
		Inventory original = Inventory.start(INVENTORY_SIZE).addStackable(STONE_ITEM, 1).finish();
		Inventory replacement = Inventory.start(INVENTORY_SIZE).addStackable(PLANK_ITEM, 3).addStackable(STONE_ITEM, 2).finish();
		MutableInventory inv = new MutableInventory(original);
		Assert.assertEquals(1, inv.getCount(STONE_ITEM));
		Assert.assertTrue(inv.addAllItems(LOG_ITEM, 4));
		inv.clearInventory(replacement);
		Assert.assertEquals(2, inv.getCount(STONE_ITEM));
		Assert.assertEquals(3, inv.getCount(PLANK_ITEM));
		Assert.assertEquals(0, inv.getCount(LOG_ITEM));
		Assert.assertTrue(inv.addAllItems(LOG_ITEM, 1));
		
		Inventory frozen = inv.freeze();
		Assert.assertEquals(original.maxEncumbrance, frozen.maxEncumbrance);
		Assert.assertEquals(3, frozen.sortedKeys().size());
		Assert.assertEquals(2, frozen.getCount(STONE_ITEM));
		Assert.assertEquals(3, frozen.getCount(PLANK_ITEM));
		Assert.assertEquals(1, frozen.getCount(LOG_ITEM));
		Assert.assertEquals(18, frozen.currentEncumbrance);
	}

	@Test
	public void clearAndReplaceUnlike() throws Throwable
	{
		// Verify that we freeze correctly without assuming that keys aren't always matching stack/non-stack (since "clear" will reset).
		NonStackableItem first = new NonStackableItem(SWORD_ITEM, Map.of());
		Inventory original = Inventory.start(INVENTORY_SIZE).addNonStackable(first).addStackable(STONE_ITEM, 1).finish();
		NonStackableItem second = new NonStackableItem(SWORD_ITEM, Map.of());
		Inventory replacement = Inventory.start(INVENTORY_SIZE).addStackable(PLANK_ITEM, 2).addNonStackable(second).finish();
		MutableInventory inv = new MutableInventory(original);
		inv.clearInventory(replacement);
		Inventory frozen = inv.freeze();
		Assert.assertEquals(2, frozen.sortedKeys().size());
		Assert.assertEquals(1, frozen.getIdOfStackableType(PLANK_ITEM));
		Assert.assertEquals(2, frozen.getIdOfNonStackableInstance(second));
	}

	@Test
	public void integerOverflow() throws Throwable
	{
		// Normally, we can over-fill inventories but verify that this is cleared if the current encumbrance overflows.
		Inventory original = Inventory.start(INVENTORY_SIZE).finish();
		MutableInventory inv = new MutableInventory(original);
		int itemEncumbrance = ENV.encumbrance.getEncumbrance(LOG_ITEM);
		int countToOverflow = (Integer.MAX_VALUE / itemEncumbrance) + 1;
		inv.addItemsAllowingOverflow(LOG_ITEM, countToOverflow);
		Assert.assertEquals(countToOverflow, inv.getCount(LOG_ITEM));
		
		// This should empty when freezing.
		Inventory frozen = inv.freeze();
		Assert.assertEquals(original.maxEncumbrance, frozen.maxEncumbrance);
		Assert.assertEquals(0, frozen.sortedKeys().size());
		Assert.assertEquals(0, frozen.currentEncumbrance);
	}

	@Test
	public void checkFreezeOrder() throws Throwable
	{
		// Verify that we correctly check the types and the count when updating after freeze.
		Inventory original = Inventory.start(INVENTORY_SIZE).addStackable(STONE_ITEM, 1).finish();
		MutableInventory inv = new MutableInventory(original);
		inv.clearInventory(null);
		inv.addAllItems(LOG_ITEM, 1);
		
		Inventory frozen = inv.freeze();
		Assert.assertEquals(1, frozen.getCount(LOG_ITEM));
	}

	@Test
	public void nonStackable() throws Throwable
	{
		// Show that non-stackable items are handled correctly.
		NonStackableItem sword1 = new NonStackableItem(SWORD_ITEM, Map.of());
		NonStackableItem sword2 = new NonStackableItem(SWORD_ITEM, Map.of());
		NonStackableItem sword3 = new NonStackableItem(SWORD_ITEM, Map.of());
		NonStackableItem sword4 = new NonStackableItem(SWORD_ITEM, Map.of());
		Inventory original = Inventory.start(INVENTORY_SIZE)
				.addNonStackable(sword1)
				.addStackable(STONE_ITEM, 1)
				.addNonStackable(sword2)
				.addStackable(STONE_ITEM, 1)
				.finish();
		MutableInventory inv = new MutableInventory(original);
		inv.addAllItems(STONE_ITEM, 1);
		inv.addNonStackableBestEfforts(sword3);
		
		// Items are added in the order they are given to the builder, but stackables are combined.
		int sword1Key = 1;
		int stoneKey = 2;
		int sword2Key = 3;
		int sword3Key = 4;
		int sword4Key = 5;
		int dirtKey = 6;
		inv.removeNonStackableItems(sword1Key);
		inv.addNonStackableAllowingOverflow(sword4);
		inv.addAllItems(DIRT_ITEM, 2);
		
		Assert.assertEquals(stoneKey, inv.getIdOfStackableType(STONE_ITEM));
		Assert.assertEquals(sword2Key, inv.getIdOfNonStackableInstance(sword2));
		Assert.assertEquals(sword3Key, inv.getIdOfNonStackableInstance(sword3));
		Assert.assertEquals(sword4Key, inv.getIdOfNonStackableInstance(sword4));
		Assert.assertEquals(dirtKey, inv.getIdOfStackableType(DIRT_ITEM));
		
		Inventory frozen = inv.freeze();
		Assert.assertEquals(0, frozen.getCount(LOG_ITEM));
		Assert.assertEquals(3, frozen.getCount(STONE_ITEM));
		Assert.assertArrayEquals(new Object[] { Integer.valueOf(stoneKey), Integer.valueOf(sword2Key), Integer.valueOf(sword3Key), Integer.valueOf(sword4Key), Integer.valueOf(dirtKey) }, frozen.sortedKeys().toArray());
		Assert.assertEquals(STONE_ITEM, frozen.getStackForKey(stoneKey).type());
		Assert.assertTrue(sword2 == frozen.getNonStackableForKey(sword2Key));
		Assert.assertTrue(sword3 == frozen.getNonStackableForKey(sword3Key));
		Assert.assertTrue(sword4 == frozen.getNonStackableForKey(sword4Key));
		Assert.assertEquals(DIRT_ITEM, frozen.getStackForKey(dirtKey).type());
		
		// Make sure that these are still resolved correctly as slots.
		Assert.assertEquals(STONE_ITEM, frozen.getSlotForKey(stoneKey).stack.type());
		Assert.assertTrue(sword2 == frozen.getSlotForKey(sword2Key).nonStackable);
		Assert.assertTrue(sword3 == frozen.getSlotForKey(sword3Key).nonStackable);
		Assert.assertTrue(sword4 == frozen.getSlotForKey(sword4Key).nonStackable);
		Assert.assertEquals(DIRT_ITEM, frozen.getSlotForKey(dirtKey).stack.type());
	}

	@Test
	public void bestEffortsFull() throws Throwable
	{
		Inventory original = Inventory.start(INVENTORY_SIZE).finish();
		MutableInventory inv = new MutableInventory(original);
		int planksToAdd = inv.maxVacancyForItem(PLANK_ITEM) - 1;
		int added = inv.addItemsBestEfforts(PLANK_ITEM, planksToAdd);
		Assert.assertEquals(planksToAdd, added);
		Assert.assertEquals(1, inv.freeze().sortedKeys().size());
		Assert.assertEquals(98, inv.getCurrentEncumbrance());
		
		added = inv.addItemsBestEfforts(LOG_ITEM, 1);
		Assert.assertEquals(0, added);
		Assert.assertEquals(1, inv.freeze().sortedKeys().size());
		
		Inventory frozen = inv.freeze();
		Assert.assertEquals(original.maxEncumbrance, frozen.maxEncumbrance);
		Assert.assertEquals(1, frozen.sortedKeys().size());
		Assert.assertEquals(98, frozen.currentEncumbrance);
	}
}
