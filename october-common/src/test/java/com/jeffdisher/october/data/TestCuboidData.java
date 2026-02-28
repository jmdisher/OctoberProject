package com.jeffdisher.october.data;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
	public static void setup() throws Throwable
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
		Assert.assertFalse(BlockProxy.doAspectsMatch(testAddress, base, test));
		test.setData15(AspectRegistry.BLOCK, testAddress, (short)0);
		Assert.assertTrue(BlockProxy.doAspectsMatch(testAddress, base, test));
		
		Inventory i1 = Inventory.start(10).addStackable(STONE_ITEM, 1).finish();
		test.setDataSpecial(AspectRegistry.INVENTORY, testAddress, i1);
		Assert.assertFalse(BlockProxy.doAspectsMatch(testAddress, base, test));
		test.setDataSpecial(AspectRegistry.INVENTORY, testAddress, null);
		Assert.assertTrue(BlockProxy.doAspectsMatch(testAddress, base, test));
		
		test.setDataSpecial(AspectRegistry.DAMAGE, testAddress, 100);
		Assert.assertFalse(BlockProxy.doAspectsMatch(testAddress, base, test));
		test.setDataSpecial(AspectRegistry.DAMAGE, testAddress, null);
		Assert.assertTrue(BlockProxy.doAspectsMatch(testAddress, base, test));
		
		CraftOperation c1 = new CraftOperation(null, 100L);
		test.setDataSpecial(AspectRegistry.CRAFTING, testAddress, c1);
		Assert.assertFalse(BlockProxy.doAspectsMatch(testAddress, base, test));
		test.setDataSpecial(AspectRegistry.CRAFTING, testAddress, null);
		Assert.assertTrue(BlockProxy.doAspectsMatch(testAddress, base, test));
		
		FuelState f1 = new FuelState(100, STONE_ITEM, i1);
		test.setDataSpecial(AspectRegistry.FUELLED, testAddress, f1);
		Assert.assertFalse(BlockProxy.doAspectsMatch(testAddress, base, test));
		test.setDataSpecial(AspectRegistry.FUELLED, testAddress, null);
		Assert.assertTrue(BlockProxy.doAspectsMatch(testAddress, base, test));
		
		test.setData7(AspectRegistry.LIGHT, testAddress, (byte)10);
		Assert.assertFalse(BlockProxy.doAspectsMatch(testAddress, base, test));
		test.setData7(AspectRegistry.LIGHT, testAddress, (byte)0);
		Assert.assertTrue(BlockProxy.doAspectsMatch(testAddress, base, test));
		
		test.setData7(AspectRegistry.LOGIC, testAddress, (byte)10);
		Assert.assertFalse(BlockProxy.doAspectsMatch(testAddress, base, test));
		test.setData7(AspectRegistry.LOGIC, testAddress, (byte)0);
		Assert.assertTrue(BlockProxy.doAspectsMatch(testAddress, base, test));
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

	@Test
	public void batchRead()
	{
		BlockAddress stone1 = BlockAddress.fromInt(1, 2, 3);
		BlockAddress stone2 = BlockAddress.fromInt(4, 5, 6);
		BlockAddress stone3 = BlockAddress.fromInt(7, 8, 9);
		BlockAddress stone4 = BlockAddress.fromInt(10, 11, 12);
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		short stoneNumber = STONE_ITEM.number();
		input.setData15(AspectRegistry.BLOCK, stone1, stoneNumber);
		input.setData15(AspectRegistry.BLOCK, stone2, stoneNumber);
		input.setData15(AspectRegistry.BLOCK, stone3, stoneNumber);
		input.setData15(AspectRegistry.BLOCK, stone4, stoneNumber);
		
		BlockAddress air = BlockAddress.fromInt(10, 9, 8);
		short[] numbers = input.batchReadData15(AspectRegistry.BLOCK, new BlockAddress[] {
			stone1,
			stone2,
			stone3,
			air,
			stone4,
		});
		Assert.assertArrayEquals(new short[] {
			stoneNumber,
			stoneNumber,
			stoneNumber,
			0,
			stoneNumber,
		}, numbers);
	}

	@Test
	public void sortAddresses()
	{
		List<BlockAddress> list = new ArrayList<>();
		BlockAddress address1 = BlockAddress.fromInt(1, 1, 1);
		BlockAddress address2 = BlockAddress.fromInt(1, 2, 3);
		BlockAddress address3 = BlockAddress.fromInt(2, 2, 2);
		BlockAddress address4 = BlockAddress.fromInt(4, 5, 6);
		BlockAddress address5 = BlockAddress.fromInt(7, 8, 9);
		BlockAddress address6 = BlockAddress.fromInt(10, 11, 12);
		
		Assert.assertEquals(7, IReadOnlyCuboidData.getBatchSortOrder(address1));
		Assert.assertEquals(29, IReadOnlyCuboidData.getBatchSortOrder(address2));
		Assert.assertEquals(56, IReadOnlyCuboidData.getBatchSortOrder(address3));
		Assert.assertEquals(458, IReadOnlyCuboidData.getBatchSortOrder(address4));
		Assert.assertEquals(1829, IReadOnlyCuboidData.getBatchSortOrder(address5));
		Assert.assertEquals(3698, IReadOnlyCuboidData.getBatchSortOrder(address6));
		
		list.add(address2);
		list.add(address1);
		list.add(address3);
		list.add(address4);
		list.add(address5);
		list.add(address1);
		list.add(address6);
		list.add(address3);
		list.add(address4);
		
		// Note that duplicates aren't allowed when making a batch read so this is just to show how the sorting works.
		BlockAddress[] sorted = list.toArray((int size) -> new BlockAddress[size]);
		Arrays.sort(sorted, new IReadOnlyCuboidData.BlockAddressBatchComparator());
		Assert.assertEquals(9, sorted.length);
		Assert.assertEquals(address1, sorted[0]);
		Assert.assertEquals(address1, sorted[1]);
		Assert.assertEquals(address2, sorted[2]);
		Assert.assertEquals(address3, sorted[3]);
		Assert.assertEquals(address3, sorted[4]);
		Assert.assertEquals(address4, sorted[5]);
		Assert.assertEquals(address4, sorted[6]);
		Assert.assertEquals(address5, sorted[7]);
		Assert.assertEquals(address6, sorted[8]);
	}
}
