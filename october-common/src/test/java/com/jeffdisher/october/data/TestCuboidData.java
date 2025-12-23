package com.jeffdisher.october.data;

import java.nio.ByteBuffer;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.PropertyHelpers;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CraftOperation;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.FuelState;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestCuboidData
{
	private static Environment ENV;
	private static Item STONE_ITEM;
	private static Item IRON_SWORD;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		STONE_ITEM = ENV.items.getItemById("op.stone");
		IRON_SWORD = ENV.items.getItemById("op.iron_sword");
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void serializeEmpty()
	{
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		Object resume = input.serializeResumable(null, buffer);
		Assert.assertNull(resume);
		buffer.flip();
		
		CuboidData output = CuboidData.createEmpty(cuboidAddress);
		DeserializationContext context = DeserializationContext.empty(Environment.getShared()
			, buffer
		);
		resume = output.deserializeResumable(null, context);
		Assert.assertNull(resume);
		BlockAddress testAddress = BlockAddress.fromInt(0, 0, 0);
		Assert.assertEquals((short) 0, output.getData15(AspectRegistry.BLOCK, testAddress));
		Assert.assertNull(output.getDataSpecial(AspectRegistry.INVENTORY, testAddress));
	}

	@Test
	public void serializeSimple()
	{
		BlockAddress testAddress = BlockAddress.fromInt(0, 0, 0);
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		input.setData15(AspectRegistry.BLOCK, testAddress, (short)1);
		input.setDataSpecial(AspectRegistry.INVENTORY, testAddress, Inventory.start(5).addStackable(STONE_ITEM, 2).finish());
		
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		Object resume = input.serializeResumable(null, buffer);
		Assert.assertNull(resume);
		buffer.flip();
		
		CuboidData output = CuboidData.createEmpty(cuboidAddress);
		DeserializationContext context = DeserializationContext.empty(Environment.getShared()
			, buffer
		);
		resume = output.deserializeResumable(null, context);
		Assert.assertNull(resume);
		Assert.assertEquals((short) 1, output.getData15(AspectRegistry.BLOCK, testAddress));
		Inventory inv = output.getDataSpecial(AspectRegistry.INVENTORY, testAddress);
		Assert.assertEquals(5, inv.maxEncumbrance);
		Assert.assertEquals(8, inv.currentEncumbrance);
		Assert.assertEquals(1, inv.sortedKeys().size());
		Assert.assertEquals(2, inv.getCount(STONE_ITEM));
	}

	@Test(expected = AssertionError.class)
	public void serializeNoProgress()
	{
		BlockAddress testAddress = BlockAddress.fromInt(0, 0, 0);
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		input.setData15(AspectRegistry.BLOCK, testAddress, (short)1);
		input.setDataSpecial(AspectRegistry.INVENTORY, testAddress, Inventory.start(5).addStackable(STONE_ITEM, 2).finish());
		
		// 4-bytes is the smallest buffer we can use to serialize or deserialize but it won't be enough to make progress through Inventory aspect.
		ByteBuffer buffer = ByteBuffer.allocate(4);
		CuboidData output = CuboidData.createEmpty(cuboidAddress);
		Object resumeSerialize = input.serializeResumable(null, buffer);
		Object resumeDeserialize = null;
		while (null != resumeSerialize)
		{
			resumeSerialize = input.serializeResumable(resumeSerialize, buffer);
			buffer.flip();
			DeserializationContext context = DeserializationContext.empty(Environment.getShared()
				, buffer
			);
			resumeDeserialize = output.deserializeResumable(resumeDeserialize, context);
			buffer.clear();
		}
		// We should throw an assertion error due to making no progress.
	}

	@Test
	public void serializeOverflow()
	{
		BlockAddress testAddress = BlockAddress.fromInt(0, 0, 0);
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		input.setData15(AspectRegistry.BLOCK, testAddress, (short)1);
		input.setDataSpecial(AspectRegistry.INVENTORY, testAddress, Inventory.start(5).addStackable(STONE_ITEM, 2).finish());
		
		// Make the smallest buffer which can contain the single inventory element and its key (smaller than this and we will fail due to making no progress).
		// "13" was determined experimentally but is a single inventory item:  4 (max_encumbrance) + 1 (items_in_inventory) + 2 (key) + 2 (item_type) + 4 (item_count).
		// However, the minimum size for CuboidData is fixed at 17 bytes.
		ByteBuffer buffer = ByteBuffer.allocate(17);
		CuboidData output = CuboidData.createEmpty(cuboidAddress);
		Object resumeSerialize = null;
		Object resumeDeserialize = null;
		do
		{
			resumeSerialize = input.serializeResumable(resumeSerialize, buffer);
			buffer.flip();
			DeserializationContext context = DeserializationContext.empty(Environment.getShared()
				, buffer
			);
			resumeDeserialize = output.deserializeResumable(resumeDeserialize, context);
			buffer.clear();
		} while (null != resumeSerialize);
		Assert.assertNull(resumeDeserialize);
		
		Assert.assertEquals((short) 1, output.getData15(AspectRegistry.BLOCK, testAddress));
		Inventory inv = output.getDataSpecial(AspectRegistry.INVENTORY, testAddress);
		Assert.assertEquals(5, inv.maxEncumbrance);
		Assert.assertEquals(8, inv.currentEncumbrance);
		Assert.assertEquals(1, inv.sortedKeys().size());
		Assert.assertEquals(2, inv.getCount(STONE_ITEM));
	}

	@Test
	public void walkBlocks()
	{
		BlockAddress testAddress = BlockAddress.fromInt(0, 0, 0);
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		input.setData15(AspectRegistry.BLOCK, testAddress, (short)1);
		
		input.walkData(AspectRegistry.BLOCK, (BlockAddress base, byte size, Short value) -> {
			Assert.assertEquals(testAddress, base);
			Assert.assertEquals((byte)1, size);
			Assert.assertEquals(1, value.shortValue());
		}, ENV.special.AIR.item().number());
	}

	@Test
	public void compareProxies()
	{
		BlockAddress testAddress = BlockAddress.fromInt(10, 11, 12);
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData base = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		CuboidData test = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		
		test.setData15(AspectRegistry.BLOCK, testAddress, (short)1);
		Assert.assertFalse(new BlockProxy(testAddress, base).doAspectsMatch(new BlockProxy(testAddress, test)));
		test.setData15(AspectRegistry.BLOCK, testAddress, (short)0);
		Assert.assertTrue(new BlockProxy(testAddress, base).doAspectsMatch(new BlockProxy(testAddress, test)));
		
		Inventory i1 = Inventory.start(10).addStackable(STONE_ITEM, 1).finish();
		test.setDataSpecial(AspectRegistry.INVENTORY, testAddress, i1);
		Assert.assertFalse(new BlockProxy(testAddress, base).doAspectsMatch(new BlockProxy(testAddress, test)));
		test.setDataSpecial(AspectRegistry.INVENTORY, testAddress, null);
		Assert.assertTrue(new BlockProxy(testAddress, base).doAspectsMatch(new BlockProxy(testAddress, test)));
		
		test.setData15(AspectRegistry.DAMAGE, testAddress, (short)100);
		Assert.assertFalse(new BlockProxy(testAddress, base).doAspectsMatch(new BlockProxy(testAddress, test)));
		test.setData15(AspectRegistry.DAMAGE, testAddress, (short)0);
		Assert.assertTrue(new BlockProxy(testAddress, base).doAspectsMatch(new BlockProxy(testAddress, test)));
		
		CraftOperation c1 = new CraftOperation(null, 100L);
		test.setDataSpecial(AspectRegistry.CRAFTING, testAddress, c1);
		Assert.assertFalse(new BlockProxy(testAddress, base).doAspectsMatch(new BlockProxy(testAddress, test)));
		test.setDataSpecial(AspectRegistry.CRAFTING, testAddress, null);
		Assert.assertTrue(new BlockProxy(testAddress, base).doAspectsMatch(new BlockProxy(testAddress, test)));
		
		FuelState f1 = new FuelState(100, STONE_ITEM, i1);
		test.setDataSpecial(AspectRegistry.FUELLED, testAddress, f1);
		Assert.assertFalse(new BlockProxy(testAddress, base).doAspectsMatch(new BlockProxy(testAddress, test)));
		test.setDataSpecial(AspectRegistry.FUELLED, testAddress, null);
		Assert.assertTrue(new BlockProxy(testAddress, base).doAspectsMatch(new BlockProxy(testAddress, test)));
		
		test.setData7(AspectRegistry.LIGHT, testAddress, (byte)10);
		Assert.assertFalse(new BlockProxy(testAddress, base).doAspectsMatch(new BlockProxy(testAddress, test)));
		test.setData7(AspectRegistry.LIGHT, testAddress, (byte)0);
		Assert.assertTrue(new BlockProxy(testAddress, base).doAspectsMatch(new BlockProxy(testAddress, test)));
		
		test.setData7(AspectRegistry.LOGIC, testAddress, (byte)10);
		Assert.assertFalse(new BlockProxy(testAddress, base).doAspectsMatch(new BlockProxy(testAddress, test)));
		test.setData7(AspectRegistry.LOGIC, testAddress, (byte)0);
		Assert.assertTrue(new BlockProxy(testAddress, base).doAspectsMatch(new BlockProxy(testAddress, test)));
	}

	@Test
	public void serializeSpecialItemSlot()
	{
		BlockAddress testAddress1 = BlockAddress.fromInt(0, 0, 0);
		BlockAddress testAddress2 = BlockAddress.fromInt(0, 0, 1);
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		Items stack = new Items(STONE_ITEM, 5);
		NonStackableItem nonStack = PropertyHelpers.newItemWithDefaults(ENV, IRON_SWORD);
		input.setData15(AspectRegistry.BLOCK, testAddress1, (short)1);
		input.setDataSpecial(AspectRegistry.INVENTORY, testAddress1, Inventory.start(5).addStackable(STONE_ITEM, 2).finish());
		input.setDataSpecial(AspectRegistry.SPECIAL_ITEM_SLOT, testAddress1, ItemSlot.fromStack(stack));
		input.setDataSpecial(AspectRegistry.SPECIAL_ITEM_SLOT, testAddress2, ItemSlot.fromNonStack(nonStack));
		
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		Object resume = input.serializeResumable(null, buffer);
		Assert.assertNull(resume);
		buffer.flip();
		
		CuboidData output = CuboidData.createEmpty(cuboidAddress);
		DeserializationContext context = DeserializationContext.empty(Environment.getShared()
			, buffer
		);
		resume = output.deserializeResumable(null, context);
		Assert.assertNull(resume);
		Assert.assertEquals((short) 1, output.getData15(AspectRegistry.BLOCK, testAddress1));
		Inventory inv = output.getDataSpecial(AspectRegistry.INVENTORY, testAddress1);
		Assert.assertEquals(5, inv.maxEncumbrance);
		Assert.assertEquals(8, inv.currentEncumbrance);
		Assert.assertEquals(1, inv.sortedKeys().size());
		Assert.assertEquals(2, inv.getCount(STONE_ITEM));
		Assert.assertEquals(stack, output.getDataSpecial(AspectRegistry.SPECIAL_ITEM_SLOT, testAddress1).stack);
		Assert.assertEquals(nonStack, output.getDataSpecial(AspectRegistry.SPECIAL_ITEM_SLOT, testAddress2).nonStackable);
	}
}
