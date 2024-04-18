package com.jeffdisher.october.data;

import java.nio.ByteBuffer;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.worldgen.CuboidGenerator;


public class TestCuboidData
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
	public void serializeEmpty()
	{
		CuboidAddress cuboidAddress = new CuboidAddress((short) 0, (short) 0, (short) 0);
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.blocks.AIR);
		
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		Object resume = input.serializeResumable(null, buffer);
		Assert.assertNull(resume);
		buffer.flip();
		
		CuboidData output = CuboidData.createEmpty(cuboidAddress);
		resume = output.deserializeResumable(null, buffer);
		Assert.assertNull(resume);
		BlockAddress testAddress = new BlockAddress((byte)0, (byte)0, (byte)0);
		Assert.assertEquals((short) 0, output.getData15(AspectRegistry.BLOCK, testAddress));
		Assert.assertNull(output.getDataSpecial(AspectRegistry.INVENTORY, testAddress));
	}

	@Test
	public void serializeSimple()
	{
		BlockAddress testAddress = new BlockAddress((byte)0, (byte)0, (byte)0);
		CuboidAddress cuboidAddress = new CuboidAddress((short) 0, (short) 0, (short) 0);
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.blocks.AIR);
		input.setData15(AspectRegistry.BLOCK, testAddress, (short)1);
		input.setDataSpecial(AspectRegistry.INVENTORY, testAddress, Inventory.start(5).add(ENV.items.STONE, 2).finish());
		
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		Object resume = input.serializeResumable(null, buffer);
		Assert.assertNull(resume);
		buffer.flip();
		
		CuboidData output = CuboidData.createEmpty(cuboidAddress);
		resume = output.deserializeResumable(null, buffer);
		Assert.assertNull(resume);
		Assert.assertEquals((short) 1, output.getData15(AspectRegistry.BLOCK, testAddress));
		Inventory inv = output.getDataSpecial(AspectRegistry.INVENTORY, testAddress);
		Assert.assertEquals(5, inv.maxEncumbrance);
		Assert.assertEquals(4, inv.currentEncumbrance);
		Assert.assertEquals(1, inv.items.size());
		Assert.assertEquals(2, inv.getCount(ENV.items.STONE));
	}

	@Test(expected = AssertionError.class)
	public void serializeNoProgress()
	{
		BlockAddress testAddress = new BlockAddress((byte)0, (byte)0, (byte)0);
		CuboidAddress cuboidAddress = new CuboidAddress((short) 0, (short) 0, (short) 0);
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.blocks.AIR);
		input.setData15(AspectRegistry.BLOCK, testAddress, (short)1);
		input.setDataSpecial(AspectRegistry.INVENTORY, testAddress, Inventory.start(5).add(ENV.items.STONE, 2).finish());
		
		// 4-bytes is the smallest buffer we can use to serialize or deserialize but it won't be enough to make progress through Inventory aspect.
		ByteBuffer buffer = ByteBuffer.allocate(4);
		CuboidData output = CuboidData.createEmpty(cuboidAddress);
		Object resumeSerialize = input.serializeResumable(null, buffer);
		Object resumeDeserialize = null;
		while (null != resumeSerialize)
		{
			resumeSerialize = input.serializeResumable(resumeSerialize, buffer);
			buffer.flip();
			resumeDeserialize = output.deserializeResumable(resumeDeserialize, buffer);
			buffer.clear();
		}
		// We should throw an assertion error due to making no progress.
	}

	@Test
	public void serializeOverflow()
	{
		BlockAddress testAddress = new BlockAddress((byte)0, (byte)0, (byte)0);
		CuboidAddress cuboidAddress = new CuboidAddress((short) 0, (short) 0, (short) 0);
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.blocks.AIR);
		input.setData15(AspectRegistry.BLOCK, testAddress, (short)1);
		input.setDataSpecial(AspectRegistry.INVENTORY, testAddress, Inventory.start(5).add(ENV.items.STONE, 2).finish());
		
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
			resumeDeserialize = output.deserializeResumable(resumeDeserialize, buffer);
			buffer.clear();
		} while (null != resumeSerialize);
		Assert.assertNull(resumeDeserialize);
		
		Assert.assertEquals((short) 1, output.getData15(AspectRegistry.BLOCK, testAddress));
		Inventory inv = output.getDataSpecial(AspectRegistry.INVENTORY, testAddress);
		Assert.assertEquals(5, inv.maxEncumbrance);
		Assert.assertEquals(4, inv.currentEncumbrance);
		Assert.assertEquals(1, inv.items.size());
		Assert.assertEquals(2, inv.getCount(ENV.items.STONE));
	}
}
