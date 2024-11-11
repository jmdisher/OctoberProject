package com.jeffdisher.october.types;

import java.util.List;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.CraftAspect;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.logic.LogicLayerHelpers;
import com.jeffdisher.october.worldgen.CuboidGenerator;


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
		
		int stoneKey = inv.getIdOfStackableType(STONE_ITEM);
		Assert.assertTrue(stoneKey > 0);
		
		Items items = inv.getStackForKey(stoneKey);
		Assert.assertEquals(STONE_ITEM, items.type());
		Assert.assertEquals(CreativeInventory.STACK_SIZE, items.count());
		
		// We shouldn't see a stackable sword but we should find one in the inventory.
		Assert.assertEquals(0, inv.getIdOfStackableType(SWORD_ITEM));
		Inventory fake = CreativeInventory.fakeInventory();
		List<Integer> keys = fake.sortedKeys().stream().filter((Integer key) -> {
			NonStackableItem found = fake.getNonStackableForKey(key);
			return (null != found) && (SWORD_ITEM == found.type());
		}).toList();
		Assert.assertEquals(1, keys.size());
		int swordKey = keys.get(0);
		Assert.assertTrue(swordKey > 0);
		NonStackableItem nonStack = inv.getNonStackableForKey(swordKey);
		Assert.assertEquals(SWORD_ITEM, nonStack.type());
		Assert.assertEquals(ENV.durability.getDurability(SWORD_ITEM), nonStack.durability());
		
		Assert.assertEquals(CreativeInventory.STACK_SIZE, inv.getCount(STONE_ITEM));
		
		Assert.assertTrue(inv.addAllItems(STONE_ITEM, 100));
		
		Assert.assertEquals(100, inv.addItemsBestEfforts(STONE_ITEM, 100));
		
		inv.addItemsAllowingOverflow(STONE_ITEM, 100);
		
		Assert.assertTrue(inv.addNonStackableBestEfforts(new NonStackableItem(SWORD_ITEM, 100)));
		
		inv.addNonStackableAllowingOverflow(new NonStackableItem(SWORD_ITEM, 100));
		
		inv.replaceNonStackable(swordKey, new NonStackableItem(SWORD_ITEM, 100));
		
		Assert.assertEquals(Integer.MAX_VALUE, inv.maxVacancyForItem(STONE_ITEM));
		Assert.assertEquals(Integer.MAX_VALUE, inv.maxVacancyForItem(SWORD_ITEM));
		
		inv.removeStackableItems(STONE_ITEM, 100);
		
		inv.removeNonStackableItems(swordKey);
		
		Assert.assertEquals(0, inv.getCurrentEncumbrance());
	}

	@Test
	public void checkFakeInventory() throws Throwable
	{
		Inventory inv = CreativeInventory.fakeInventory();
		Assert.assertEquals(Integer.MAX_VALUE, inv.maxEncumbrance);
		int encumbrance = 0;
		// We will assume the build order of the fake inventory in order to match these.
		int nextKey = 1;
		for (Item item : ENV.items.ITEMS_BY_TYPE)
		{
			int number = item.number();
			if (0 == number)
			{
				// Air is the special-case which is NOT included.
			}
			else if (ENV.durability.isStackable(item))
			{
				if (0 != ENV.encumbrance.getEncumbrance(item))
				{
					Items stackable = inv.getStackForKey(nextKey);
					Assert.assertEquals(item, stackable.type());
					Assert.assertEquals(CreativeInventory.STACK_SIZE, stackable.count());
					encumbrance += CreativeInventory.STACK_SIZE * ENV.encumbrance.getEncumbrance(item);
					nextKey += 1;
				}
			}
			else
			{
				NonStackableItem nonStackable = inv.getNonStackableForKey(nextKey);
				Assert.assertEquals(item, nonStackable.type());
				Assert.assertEquals(ENV.durability.getDurability(item), nonStackable.durability());
				encumbrance += ENV.encumbrance.getEncumbrance(item);
				nextKey += 1;
			}
		}
		Assert.assertEquals(encumbrance, inv.currentEncumbrance);
	}

	@Test
	public void replacedItem() throws Throwable
	{
		// Make sure that we correctly handle cases of items which are replaced in inventory when used (buckets change to full/empty).
		IMutableInventory inv = new CreativeInventory();
		Item emptyBucket = ENV.items.getItemById("op.bucket_empty");
		Item fullBucket = ENV.items.getItemById("op.bucket_water");
		inv.replaceNonStackable(emptyBucket.number(), new NonStackableItem(fullBucket, 0));
	}

	@Test
	public void checkPlacedBlockTypes() throws Throwable
	{
		// Verify that all the items in the inventory have a valid placed block type or are non-stackable (anything else is just a non-item type).
		Inventory inv = CreativeInventory.fakeInventory();
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		TickProcessingContext context = ContextBuilder.build()
				.lookups((AbsoluteLocation location) -> {
					return new BlockProxy(location.getBlockAddress(), airCuboid);
				}, null)
				.finish()
		;
		AbsoluteLocation targetLocation = new AbsoluteLocation(10, 11, 12);
		for (Integer key : inv.sortedKeys())
		{
			Items stack = inv.getStackForKey(key);
			NonStackableItem nonStack = inv.getNonStackableForKey(key);
			if (null != stack)
			{
				Assert.assertNull(nonStack);
				Item type = stack.type();
				Block block = ENV.blocks.getAsPlaceableBlock(type);
				if (null != block)
				{
					Block toPlace = LogicLayerHelpers.blockTypeToPlace(context, targetLocation, block);
					Assert.assertNotNull(toPlace);
				}
			}
			else
			{
				Assert.assertNotNull(nonStack);
			}
		}
	}

	@Test
	public void craftInInventory() throws Throwable
	{
		Inventory inv = CreativeInventory.fakeInventory();
		Craft craft = ENV.crafting.getCraftById("op.planks_to_crafting_table");
		Assert.assertTrue(CraftAspect.canApply(craft, inv));
	}
}
