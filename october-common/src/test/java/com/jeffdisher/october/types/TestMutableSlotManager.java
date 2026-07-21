package com.jeffdisher.october.types;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.PropertyHelpers;


public class TestMutableSlotManager
{
	private static Environment ENV;
	private static Item STONE_ITEM;
	private static Item SWORD_ITEM;
	@BeforeClass
	public static void setup() throws Throwable
	{
		ENV = Environment.createSharedInstance();
		STONE_ITEM = ENV.items.getItemById("op.stone");
		SWORD_ITEM = ENV.items.getItemById("op.iron_sword");
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void basicUsage()
	{
		MutableInventory inv = new MutableInventory(Inventory.start(50).finish());
		int[] hotbar = new int[Entity.HOTBAR_SIZE];
		MutableSlotManager manager = new MutableSlotManager(inv, hotbar, 0);
		
		NonStackableItem sword = PropertyHelpers.newItemWithDefaults(ENV, SWORD_ITEM);
		
		Assert.assertTrue(manager.addStackableItems(STONE_ITEM, 5));
		Assert.assertTrue(manager.addNonStackable(sword));
		
		Assert.assertEquals(Entity.NO_SELECTION, manager.getSelectedKey());
		Assert.assertEquals(0, manager.getHotbarIndex());
		manager.setHotbarIndex(3);
		Assert.assertEquals(3, manager.getHotbarIndex());
		manager.setSelectedKey(1);
		manager.setHotbarIndex(4);
		Assert.assertEquals(Entity.NO_SELECTION, manager.getSelectedKey());
		Assert.assertEquals(1, hotbar[3]);
		manager.setSelectedKey(1);
		Assert.assertEquals(1, manager.getSelectedKey());
		Assert.assertEquals(Entity.NO_SELECTION, hotbar[3]);
		manager.setSelectedKey(2);
		Assert.assertEquals(2, manager.getSelectedKey());
		manager.removeNonStackable(2);
		Assert.assertEquals(Entity.NO_SELECTION, manager.getSelectedKey());
		manager.setSelectedKey(1);
		manager.removeStackable(STONE_ITEM, 3);
		Assert.assertEquals(1, manager.getSelectedKey());
		Assert.assertEquals(2, manager.getCount(STONE_ITEM));
		manager.removeStackable(STONE_ITEM, 2);
		Assert.assertEquals(Entity.NO_SELECTION, manager.getSelectedKey());
		Assert.assertEquals(0, manager.getCount(STONE_ITEM));
		Assert.assertTrue(hotbar == manager.test_unsafeHotbar());
	}
}
