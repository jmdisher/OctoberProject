package com.jeffdisher.october.types;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.registries.ItemRegistry;


public class TestMutableInventory
{
	@Test
	public void createFromEmpty() throws Throwable
	{
		Inventory original = Inventory.start(10).finish();
		MutableInventory inv = new MutableInventory(original);
		
		Inventory frozen = inv.freeze();
		Assert.assertEquals(original.maxEncumbrance, frozen.maxEncumbrance);
		Assert.assertEquals(original.items.size(), frozen.items.size());
		Assert.assertEquals(original.currentEncumbrance, frozen.currentEncumbrance);
	}

	@Test
	public void basicAddition() throws Throwable
	{
		Inventory original = Inventory.start(10).finish();
		MutableInventory inv = new MutableInventory(original);
		Assert.assertEquals(0, inv.getCount(ItemRegistry.LOG));
		Assert.assertEquals(5, inv.maxVacancyForItem(ItemRegistry.LOG));
		Assert.assertTrue(inv.addAllItems(ItemRegistry.LOG, 4));
		Assert.assertEquals(4, inv.getCount(ItemRegistry.LOG));
		Assert.assertEquals(1, inv.maxVacancyForItem(ItemRegistry.LOG));
		Assert.assertEquals(1, inv.addItemsBestEfforts(ItemRegistry.LOG, 4));
		Assert.assertEquals(5, inv.getCount(ItemRegistry.LOG));
		Assert.assertEquals(0, inv.maxVacancyForItem(ItemRegistry.LOG));
		
		Inventory frozen = inv.freeze();
		Assert.assertEquals(original.maxEncumbrance, frozen.maxEncumbrance);
		Assert.assertEquals(1, frozen.items.size());
		Assert.assertEquals(10, frozen.currentEncumbrance);
	}

	@Test
	public void basicRemoval() throws Throwable
	{
		Inventory original = Inventory.start(10).add(ItemRegistry.LOG, 2).finish();
		MutableInventory inv = new MutableInventory(original);
		Assert.assertEquals(2, inv.getCount(ItemRegistry.LOG));
		Assert.assertEquals(3, inv.maxVacancyForItem(ItemRegistry.LOG));
		inv.removeItems(ItemRegistry.LOG, 1);
		Assert.assertEquals(1, inv.getCount(ItemRegistry.LOG));
		Assert.assertEquals(4, inv.maxVacancyForItem(ItemRegistry.LOG));
		inv.removeItems(ItemRegistry.LOG, 1);
		Assert.assertEquals(0, inv.getCount(ItemRegistry.LOG));
		Assert.assertEquals(5, inv.maxVacancyForItem(ItemRegistry.LOG));
		
		Inventory frozen = inv.freeze();
		Assert.assertEquals(original.maxEncumbrance, frozen.maxEncumbrance);
		Assert.assertEquals(0, frozen.items.size());
		Assert.assertEquals(0, frozen.currentEncumbrance);
	}

	@Test
	public void addRemoveUnchanged() throws Throwable
	{
		Inventory original = Inventory.start(10).add(ItemRegistry.LOG, 2).finish();
		MutableInventory inv = new MutableInventory(original);
		inv.addAllItems(ItemRegistry.STONE, 1);
		inv.removeItems(ItemRegistry.LOG, 1);
		inv.removeItems(ItemRegistry.STONE, 1);
		inv.addAllItems(ItemRegistry.LOG, 1);
		
		Inventory frozen = inv.freeze();
		Assert.assertTrue(original == frozen);
	}

	@Test
	public void clearAndAdd() throws Throwable
	{
		Inventory original = Inventory.start(10).add(ItemRegistry.STONE, 1).finish();
		MutableInventory inv = new MutableInventory(original);
		Assert.assertEquals(1, inv.getCount(ItemRegistry.STONE));
		Assert.assertTrue(inv.addAllItems(ItemRegistry.LOG, 4));
		inv.clearInventory();
		Assert.assertEquals(0, inv.getCount(ItemRegistry.STONE));
		Assert.assertEquals(0, inv.getCount(ItemRegistry.LOG));
		Assert.assertTrue(inv.addAllItems(ItemRegistry.LOG, 1));
		
		Inventory frozen = inv.freeze();
		Assert.assertEquals(original.maxEncumbrance, frozen.maxEncumbrance);
		Assert.assertEquals(1, frozen.items.size());
		Assert.assertEquals(1, frozen.items.get(ItemRegistry.LOG).count());
		Assert.assertEquals(2, frozen.currentEncumbrance);
	}
}
