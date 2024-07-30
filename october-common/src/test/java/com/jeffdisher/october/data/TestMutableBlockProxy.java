package com.jeffdisher.october.data;

import java.nio.ByteBuffer;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.StationRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.worldgen.CuboidGenerator;


public class TestMutableBlockProxy
{
	private static Environment ENV;
	private static Block STONE;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		STONE = ENV.blocks.fromItem(ENV.items.getItemById("op.stone"));
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void noChange()
	{
		AbsoluteLocation location = new AbsoluteLocation(1, 1, 1);
		CuboidAddress cuboidAddress = location.getCuboidAddress();
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		
		MutableBlockProxy proxy = new MutableBlockProxy(location, input);
		
		Assert.assertFalse(proxy.didChange());
	}

	@Test
	public void simpleChange()
	{
		AbsoluteLocation location = new AbsoluteLocation(1, 1, 1);
		CuboidAddress cuboidAddress = location.getCuboidAddress();
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		BlockAddress address = location.getBlockAddress();
		
		MutableBlockProxy proxy = new MutableBlockProxy(location, input);
		proxy.setBlockAndClear(STONE);
		
		CuboidData updated = CuboidData.mutableClone(input);
		Assert.assertTrue(proxy.didChange());
		proxy.writeBack(updated);
		Assert.assertEquals(STONE.item().number(), updated.getData15(AspectRegistry.BLOCK, address));
	}

	@Test
	public void revertedChange()
	{
		AbsoluteLocation location = new AbsoluteLocation(1, 1, 1);
		CuboidAddress cuboidAddress = location.getCuboidAddress();
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		
		MutableBlockProxy proxy = new MutableBlockProxy(location, input);
		proxy.setBlockAndClear(STONE);
		proxy.setBlockAndClear(ENV.special.AIR);
		
		Assert.assertFalse(proxy.didChange());
	}

	@Test
	public void simpleSerialization()
	{
		Item stoneItem = ENV.items.getItemById("op.stone");
		AbsoluteLocation location = new AbsoluteLocation(1, 1, 1);
		CuboidAddress cuboidAddress = location.getCuboidAddress();
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		BlockAddress address = location.getBlockAddress();
		
		// Store into the block's inventory and see how that serializes.
		MutableBlockProxy proxy = new MutableBlockProxy(location, input);
		proxy.setInventory(Inventory.start(StationRegistry.CAPACITY_BLOCK_EMPTY).addStackable(stoneItem, 1).finish());
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		Assert.assertTrue(proxy.didChange());
		proxy.serializeToBuffer(buffer);
		// (verified experimentally).
		Assert.assertEquals(16, buffer.position());
		buffer.flip();
		proxy = new MutableBlockProxy(location, input);
		proxy.deserializeFromBuffer(buffer);
		Assert.assertEquals(0, buffer.remaining());
		Assert.assertTrue(proxy.didChange());
		proxy.writeBack(input);
		buffer.clear();
		
		// Illuminate this block and then serialize it to show that only that index is serialized.
		proxy = new MutableBlockProxy(location, input);
		proxy.setLight((byte)5);
		Assert.assertTrue(proxy.didChange());
		proxy.serializeToBuffer(buffer);
		// (verified experimentally).
		Assert.assertEquals(2, buffer.position());
		buffer.flip();
		proxy = new MutableBlockProxy(location, input);
		proxy.deserializeFromBuffer(buffer);
		Assert.assertEquals(0, buffer.remaining());
		Assert.assertTrue(proxy.didChange());
		proxy.writeBack(input);
		buffer.clear();
		
		// Now, reset the block type and verify that the inventory is cleared (notably not the lighting, since it is updated later).
		proxy = new MutableBlockProxy(location, input);
		proxy.setBlockAndClear(STONE);
		Assert.assertTrue(proxy.didChange());
		proxy.serializeToBuffer(buffer);
		// (verified experimentally).
		Assert.assertEquals(8, buffer.position());
		buffer.flip();
		proxy = new MutableBlockProxy(location, input);
		proxy.deserializeFromBuffer(buffer);
		Assert.assertEquals(0, buffer.remaining());
		Assert.assertTrue(proxy.didChange());
		proxy.writeBack(input);
		buffer.clear();
		
		Assert.assertEquals(STONE.item().number(), input.getData15(AspectRegistry.BLOCK, address));
		Assert.assertEquals(null, input.getDataSpecial(AspectRegistry.INVENTORY, address));
	}
}
