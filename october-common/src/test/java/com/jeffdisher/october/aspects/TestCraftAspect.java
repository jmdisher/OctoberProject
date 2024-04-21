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
		Craft craft = new Craft((short)1
				, "Simple"
				, Craft.Classification.COMMON
				, new Items[] { new Items(ENV.items.DIRT, 2), new Items(ENV.items.STONE, 1)  }
				, new Item[] {ENV.items.COAL_ORE, ENV.items.COAL_ORE, ENV.items.IRON_ORE }
				, 1000L
		);
		Inventory inv = Inventory.start(50).addStackable(ENV.items.DIRT, 4).addStackable(ENV.items.STONE, 2).finish();
		Assert.assertTrue(CraftAspect.canApply(craft, inv));
		MutableInventory mutable = new MutableInventory(inv);
		CraftAspect.craft(craft, mutable);
		inv = mutable.freeze();
		Assert.assertEquals(2, inv.getCount(ENV.items.DIRT));
		Assert.assertEquals(1, inv.getCount(ENV.items.STONE));
		Assert.assertEquals(2, inv.getCount(ENV.items.COAL_ORE));
		Assert.assertEquals(1, inv.getCount(ENV.items.IRON_ORE));
	}
}
