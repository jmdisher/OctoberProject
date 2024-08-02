package com.jeffdisher.october.types;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;


public class TestCreativeInventory
{
	private static Environment ENV;
	private static Item STONE_ITEM;
	private static Item SWORD_ITEM;
	@BeforeClass
	public static void setup()
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
	public void checkBasicResponses() throws Throwable
	{
		// Just check that the interface works as we expect.
		CreativeInventory inv = new CreativeInventory();
		
		Assert.assertEquals((int)STONE_ITEM.number(), inv.getIdOfStackableType(STONE_ITEM));
		
		Items items = inv.getStackForKey((int)STONE_ITEM.number());
		Assert.assertEquals(STONE_ITEM, items.type());
		Assert.assertEquals(Integer.MAX_VALUE, items.count());
		
		NonStackableItem nonStack = inv.getNonStackableForKey(SWORD_ITEM.number());
		Assert.assertEquals(SWORD_ITEM, nonStack.type());
		Assert.assertEquals(ENV.durability.getDurability(SWORD_ITEM), nonStack.durability());
		
		Assert.assertEquals(Integer.MAX_VALUE, inv.getCount(STONE_ITEM));
		
		Assert.assertTrue(inv.addAllItems(STONE_ITEM, 100));
		
		Assert.assertEquals(100, inv.addItemsBestEfforts(STONE_ITEM, 100));
		
		inv.addItemsAllowingOverflow(STONE_ITEM, 100);
		
		Assert.assertTrue(inv.addNonStackableBestEfforts(new NonStackableItem(SWORD_ITEM, 100)));
		
		inv.addNonStackableAllowingOverflow(new NonStackableItem(SWORD_ITEM, 100));
		
		inv.replaceNonStackable(SWORD_ITEM.number(), new NonStackableItem(SWORD_ITEM, 100));
		
		Assert.assertEquals(Integer.MAX_VALUE, inv.maxVacancyForItem(STONE_ITEM));
		Assert.assertEquals(Integer.MAX_VALUE, inv.maxVacancyForItem(SWORD_ITEM));
		
		inv.removeStackableItems(STONE_ITEM, 100);
		
		inv.removeNonStackableItems(SWORD_ITEM.number());
		
		Assert.assertEquals(0, inv.getCurrentEncumbrance());
	}

	@Test
	public void checkFakeInventory() throws Throwable
	{
		Inventory inv = CreativeInventory.fakeInventory();
		Assert.assertEquals(Integer.MAX_VALUE, inv.maxEncumbrance);
		int encumbrance = 0;
		for (Item item : ENV.items.ITEMS_BY_TYPE)
		{
			int key = item.number();
			if (0 == key)
			{
				// Air is the special-case which is NOT included.
			}
			else if (ENV.durability.isStackable(item))
			{
				Items stackable = inv.getStackForKey(key);
				Assert.assertEquals(item, stackable.type());
				Assert.assertEquals(1, stackable.count());
			}
			else
			{
				NonStackableItem nonStackable = inv.getNonStackableForKey(key);
				Assert.assertEquals(item, nonStackable.type());
				Assert.assertEquals(ENV.durability.getDurability(item), nonStackable.durability());
			}
			encumbrance += ENV.encumbrance.getEncumbrance(item);
		}
		Assert.assertEquals(encumbrance, inv.currentEncumbrance);
	}
}
