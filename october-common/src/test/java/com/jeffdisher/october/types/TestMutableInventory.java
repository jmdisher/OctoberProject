package com.jeffdisher.october.types;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;


public class TestMutableInventory
{
	private static Environment ENV;
	private static Item STONE_ITEM;
	private static Item LOG_ITEM;
	private static Item PLANK_ITEM;
	private static Item DIRT_ITEM;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		STONE_ITEM = ENV.items.getItemById("op.stone");
		LOG_ITEM = ENV.items.getItemById("op.log");
		PLANK_ITEM = ENV.items.getItemById("op.plank");
		DIRT_ITEM = ENV.items.getItemById("op.dirt");
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void createFromEmpty() throws Throwable
	{
		Inventory original = Inventory.start(10).finish();
		MutableInventory inv = new MutableInventory(original);
		
		Inventory frozen = inv.freeze();
		Assert.assertEquals(original.maxEncumbrance, frozen.maxEncumbrance);
		Assert.assertEquals(original.sortedKeys().size(), frozen.sortedKeys().size());
		Assert.assertEquals(original.currentEncumbrance, frozen.currentEncumbrance);
	}

	@Test
	public void basicAddition() throws Throwable
	{
		Inventory original = Inventory.start(10).finish();
		MutableInventory inv = new MutableInventory(original);
		Assert.assertEquals(0, inv.getCount(LOG_ITEM));
		Assert.assertEquals(5, inv.maxVacancyForItem(LOG_ITEM));
		Assert.assertTrue(inv.addAllItems(LOG_ITEM, 4));
		Assert.assertEquals(4, inv.getCount(LOG_ITEM));
		Assert.assertEquals(1, inv.maxVacancyForItem(LOG_ITEM));
		Assert.assertEquals(1, inv.addItemsBestEfforts(LOG_ITEM, 4));
		Assert.assertEquals(5, inv.getCount(LOG_ITEM));
		Assert.assertEquals(0, inv.maxVacancyForItem(LOG_ITEM));
		
		Inventory frozen = inv.freeze();
		Assert.assertEquals(original.maxEncumbrance, frozen.maxEncumbrance);
		Assert.assertEquals(1, frozen.sortedKeys().size());
		Assert.assertEquals(10, frozen.currentEncumbrance);
	}

	@Test
	public void basicRemoval() throws Throwable
	{
		Inventory original = Inventory.start(10).addStackable(LOG_ITEM, 2).finish();
		MutableInventory inv = new MutableInventory(original);
		Assert.assertEquals(2, inv.getCount(LOG_ITEM));
		Assert.assertEquals(3, inv.maxVacancyForItem(LOG_ITEM));
		inv.removeStackableItems(LOG_ITEM, 1);
		Assert.assertEquals(1, inv.getCount(LOG_ITEM));
		Assert.assertEquals(4, inv.maxVacancyForItem(LOG_ITEM));
		inv.removeStackableItems(LOG_ITEM, 1);
		Assert.assertEquals(0, inv.getCount(LOG_ITEM));
		Assert.assertEquals(5, inv.maxVacancyForItem(LOG_ITEM));
		
		Inventory frozen = inv.freeze();
		Assert.assertEquals(original.maxEncumbrance, frozen.maxEncumbrance);
		Assert.assertEquals(0, frozen.sortedKeys().size());
		Assert.assertEquals(0, frozen.currentEncumbrance);
	}

	@Test
	public void addRemoveUnchanged() throws Throwable
	{
		Inventory original = Inventory.start(10).addStackable(LOG_ITEM, 2).finish();
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
		Inventory original = Inventory.start(10).addStackable(STONE_ITEM, 1).finish();
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
		Assert.assertEquals(2, frozen.currentEncumbrance);
	}

	@Test
	public void clearAndReplace() throws Throwable
	{
		Inventory original = Inventory.start(10).addStackable(STONE_ITEM, 1).finish();
		Inventory replacement = Inventory.start(10).addStackable(PLANK_ITEM, 3).addStackable(STONE_ITEM, 2).finish();
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
		Assert.assertEquals(9, frozen.currentEncumbrance);
	}

	@Test
	public void integerOverflow() throws Throwable
	{
		// Normally, we can over-fill inventories but verify that this is cleared if the current encumbrance overflows.
		Inventory original = Inventory.start(10).finish();
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
		Inventory original = Inventory.start(10).addStackable(STONE_ITEM, 1).finish();
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
		Inventory original = Inventory.start(10)
				.addNonStackable(new NonStackableItem(STONE_ITEM, 0))
				.addStackable(STONE_ITEM, 1)
				.addNonStackable(new NonStackableItem(PLANK_ITEM, 0))
				.addStackable(STONE_ITEM, 1)
				.finish();
		MutableInventory inv = new MutableInventory(original);
		inv.addAllItems(STONE_ITEM, 1);
		inv.addNonStackableBestEfforts(new NonStackableItem(DIRT_ITEM, 0));
		// Items are added in the order they are given to the builder (first stackable added counts).
		int firstNonStackableItem = 1;
		inv.removeNonStackableItems(firstNonStackableItem);
		inv.addNonStackableAllowingOverflow(new NonStackableItem(LOG_ITEM, 0));
		
		Inventory frozen = inv.freeze();
		Assert.assertEquals(0, frozen.getCount(LOG_ITEM));
		Assert.assertEquals(3, frozen.getCount(STONE_ITEM));
		Assert.assertArrayEquals(new Object[] { Integer.valueOf(2), Integer.valueOf(3), Integer.valueOf(4), Integer.valueOf(5) }, frozen.sortedKeys().toArray());
		Assert.assertEquals(STONE_ITEM, frozen.getStackForKey(2).type());
		Assert.assertEquals(PLANK_ITEM, frozen.getNonStackableForKey(3).type());
		Assert.assertEquals(DIRT_ITEM, frozen.getNonStackableForKey(4).type());
		Assert.assertEquals(LOG_ITEM, frozen.getNonStackableForKey(5).type());
	}

	@Test
	public void bestEffortsFull() throws Throwable
	{
		Inventory original = Inventory.start(10).finish();
		MutableInventory inv = new MutableInventory(original);
		int planksToAdd = inv.maxVacancyForItem(PLANK_ITEM) - 1;
		int added = inv.addItemsBestEfforts(PLANK_ITEM, planksToAdd);
		Assert.assertEquals(planksToAdd, added);
		Assert.assertEquals(1, inv.freeze().sortedKeys().size());
		Assert.assertEquals(9, inv.getCurrentEncumbrance());
		
		added = inv.addItemsBestEfforts(LOG_ITEM, 1);
		Assert.assertEquals(0, added);
		Assert.assertEquals(1, inv.freeze().sortedKeys().size());
		
		Inventory frozen = inv.freeze();
		Assert.assertEquals(original.maxEncumbrance, frozen.maxEncumbrance);
		Assert.assertEquals(1, frozen.sortedKeys().size());
		Assert.assertEquals(9, frozen.currentEncumbrance);
	}
}
