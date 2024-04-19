package com.jeffdisher.october.types;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;


public class TestMutableInventory
{
	private static Environment ENV;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
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
		Assert.assertEquals(original.sortedItems().size(), frozen.sortedItems().size());
		Assert.assertEquals(original.currentEncumbrance, frozen.currentEncumbrance);
	}

	@Test
	public void basicAddition() throws Throwable
	{
		Inventory original = Inventory.start(10).finish();
		MutableInventory inv = new MutableInventory(original);
		Assert.assertEquals(0, inv.getCount(ENV.items.LOG));
		Assert.assertEquals(5, inv.maxVacancyForItem(ENV.items.LOG));
		Assert.assertTrue(inv.addAllItems(ENV.items.LOG, 4));
		Assert.assertEquals(4, inv.getCount(ENV.items.LOG));
		Assert.assertEquals(1, inv.maxVacancyForItem(ENV.items.LOG));
		Assert.assertEquals(1, inv.addItemsBestEfforts(ENV.items.LOG, 4));
		Assert.assertEquals(5, inv.getCount(ENV.items.LOG));
		Assert.assertEquals(0, inv.maxVacancyForItem(ENV.items.LOG));
		
		Inventory frozen = inv.freeze();
		Assert.assertEquals(original.maxEncumbrance, frozen.maxEncumbrance);
		Assert.assertEquals(1, frozen.sortedItems().size());
		Assert.assertEquals(10, frozen.currentEncumbrance);
	}

	@Test
	public void basicRemoval() throws Throwable
	{
		Inventory original = Inventory.start(10).add(ENV.items.LOG, 2).finish();
		MutableInventory inv = new MutableInventory(original);
		Assert.assertEquals(2, inv.getCount(ENV.items.LOG));
		Assert.assertEquals(3, inv.maxVacancyForItem(ENV.items.LOG));
		inv.removeItems(ENV.items.LOG, 1);
		Assert.assertEquals(1, inv.getCount(ENV.items.LOG));
		Assert.assertEquals(4, inv.maxVacancyForItem(ENV.items.LOG));
		inv.removeItems(ENV.items.LOG, 1);
		Assert.assertEquals(0, inv.getCount(ENV.items.LOG));
		Assert.assertEquals(5, inv.maxVacancyForItem(ENV.items.LOG));
		
		Inventory frozen = inv.freeze();
		Assert.assertEquals(original.maxEncumbrance, frozen.maxEncumbrance);
		Assert.assertEquals(0, frozen.sortedItems().size());
		Assert.assertEquals(0, frozen.currentEncumbrance);
	}

	@Test
	public void addRemoveUnchanged() throws Throwable
	{
		Inventory original = Inventory.start(10).add(ENV.items.LOG, 2).finish();
		MutableInventory inv = new MutableInventory(original);
		inv.addAllItems(ENV.items.STONE, 1);
		inv.removeItems(ENV.items.LOG, 1);
		inv.removeItems(ENV.items.STONE, 1);
		inv.addAllItems(ENV.items.LOG, 1);
		
		Inventory frozen = inv.freeze();
		Assert.assertTrue(original == frozen);
	}

	@Test
	public void clearAndAdd() throws Throwable
	{
		Inventory original = Inventory.start(10).add(ENV.items.STONE, 1).finish();
		MutableInventory inv = new MutableInventory(original);
		Assert.assertEquals(1, inv.getCount(ENV.items.STONE));
		Assert.assertTrue(inv.addAllItems(ENV.items.LOG, 4));
		inv.clearInventory();
		Assert.assertEquals(0, inv.getCount(ENV.items.STONE));
		Assert.assertEquals(0, inv.getCount(ENV.items.LOG));
		Assert.assertTrue(inv.addAllItems(ENV.items.LOG, 1));
		
		Inventory frozen = inv.freeze();
		Assert.assertEquals(original.maxEncumbrance, frozen.maxEncumbrance);
		Assert.assertEquals(1, frozen.sortedItems().size());
		Assert.assertEquals(1, frozen.getCount(ENV.items.LOG));
		Assert.assertEquals(2, frozen.currentEncumbrance);
	}

	@Test
	public void integerOverflow() throws Throwable
	{
		// Normally, we can over-fill inventories but verify that this is cleared if the current encumbrance overflows.
		Inventory original = Inventory.start(10).finish();
		MutableInventory inv = new MutableInventory(original);
		int itemEncumbrance = ENV.inventory.getEncumbrance(ENV.items.LOG);
		int countToOverflow = (Integer.MAX_VALUE / itemEncumbrance) + 1;
		inv.addItemsAllowingOverflow(ENV.items.LOG, countToOverflow);
		Assert.assertEquals(countToOverflow, inv.getCount(ENV.items.LOG));
		
		// This should empty when freezing.
		Inventory frozen = inv.freeze();
		Assert.assertEquals(original.maxEncumbrance, frozen.maxEncumbrance);
		Assert.assertEquals(0, frozen.sortedItems().size());
		Assert.assertEquals(0, frozen.currentEncumbrance);
	}

	@Test
	public void checkFreezeOrder() throws Throwable
	{
		// Verify that we correctly check the types and the count when updating after freeze.
		Inventory original = Inventory.start(10).add(ENV.items.STONE, 1).finish();
		MutableInventory inv = new MutableInventory(original);
		inv.clearInventory();
		inv.addAllItems(ENV.items.LOG, 1);
		
		Inventory frozen = inv.freeze();
		Assert.assertEquals(1, frozen.getCount(ENV.items.LOG));
	}
}
