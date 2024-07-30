package com.jeffdisher.october.aspects;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.types.Craft;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableInventory;


public class TestCraftAspect
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
	public void simpleCraft() throws Throwable
	{
		Item dirtItem = ENV.items.getItemById("op.dirt");
		Item stoneItem = ENV.items.getItemById("op.stone");
		Item coalOreItem = ENV.items.getItemById("op.coal_ore");
		Item ironOreItem = ENV.items.getItemById("op.iron_ore");
		Craft craft = new Craft((short)1
				, "Simple"
				, "COMMON"
				, new Items[] { new Items(dirtItem, 2), new Items(stoneItem, 1)  }
				, new Item[] {coalOreItem, coalOreItem, ironOreItem }
				, 1000L
		);
		Inventory inv = Inventory.start(50).addStackable(dirtItem, 4).addStackable(stoneItem, 2).finish();
		Assert.assertTrue(CraftAspect.canApply(craft, inv));
		MutableInventory mutable = new MutableInventory(inv);
		CraftAspect.craft(ENV, craft, mutable);
		inv = mutable.freeze();
		Assert.assertEquals(2, inv.getCount(dirtItem));
		Assert.assertEquals(1, inv.getCount(stoneItem));
		Assert.assertEquals(2, inv.getCount(coalOreItem));
		Assert.assertEquals(1, inv.getCount(ironOreItem));
	}

	@Test
	public void nonStack() throws Throwable
	{
		Item pick = ENV.items.getItemById("op.iron_pickaxe");
		Item dirtItem = ENV.items.getItemById("op.dirt");
		Item stoneItem = ENV.items.getItemById("op.stone");
		Craft craft = new Craft((short)1
				, "Non-stack"
				, "COMMON"
				, new Items[] { new Items(dirtItem, 1), new Items(stoneItem, 1)  }
				, new Item[] { pick }
				, 1000L
		);
		Inventory inv = Inventory.start(50).addStackable(dirtItem, 2).addStackable(stoneItem, 1).finish();
		Assert.assertTrue(CraftAspect.canApply(craft, inv));
		MutableInventory mutable = new MutableInventory(inv);
		CraftAspect.craft(ENV, craft, mutable);
		inv = mutable.freeze();
		Assert.assertEquals(2, inv.sortedKeys().size());
		Assert.assertEquals(1, inv.getCount(dirtItem));
		Assert.assertEquals(pick, inv.getNonStackableForKey(3).type());
	}
}
