package com.jeffdisher.october.data;

import java.nio.ByteBuffer;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.aspects.InventoryAspect;
import com.jeffdisher.october.registries.AspectRegistry;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.worldgen.CuboidGenerator;


public class TestMutableBlockProxy
{
	@Test
	public void noChange()
	{
		AbsoluteLocation location = new AbsoluteLocation(1, 1, 1);
		CuboidAddress cuboidAddress = location.getCuboidAddress();
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ItemRegistry.AIR);
		
		MutableBlockProxy proxy = new MutableBlockProxy(location, input);
		
		Assert.assertFalse(proxy.didChange());
	}

	@Test
	public void simpleChange()
	{
		AbsoluteLocation location = new AbsoluteLocation(1, 1, 1);
		CuboidAddress cuboidAddress = location.getCuboidAddress();
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ItemRegistry.AIR);
		BlockAddress address = location.getBlockAddress();
		
		MutableBlockProxy proxy = new MutableBlockProxy(location, input);
		proxy.setBlockAndClear(BlockAspect.STONE);
		
		CuboidData updated = CuboidData.mutableClone(input);
		Assert.assertTrue(proxy.didChange());
		proxy.writeBack(updated);
		Assert.assertEquals(BlockAspect.STONE.number(), updated.getData15(AspectRegistry.BLOCK, address));
	}

	@Test
	public void revertedChange()
	{
		AbsoluteLocation location = new AbsoluteLocation(1, 1, 1);
		CuboidAddress cuboidAddress = location.getCuboidAddress();
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ItemRegistry.AIR);
		
		MutableBlockProxy proxy = new MutableBlockProxy(location, input);
		proxy.setBlockAndClear(BlockAspect.STONE);
		proxy.setBlockAndClear(BlockAspect.AIR);
		
		Assert.assertFalse(proxy.didChange());
	}

	@Test
	public void simpleSerialization()
	{
		AbsoluteLocation location = new AbsoluteLocation(1, 1, 1);
		CuboidAddress cuboidAddress = location.getCuboidAddress();
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ItemRegistry.AIR);
		BlockAddress address = location.getBlockAddress();
		
		// Store into the block's inventory and see how that serializes.
		MutableBlockProxy proxy = new MutableBlockProxy(location, input);
		proxy.setInventory(Inventory.start(InventoryAspect.CAPACITY_AIR).add(ItemRegistry.STONE, 1).finish());
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		Assert.assertTrue(proxy.didChange());
		proxy.serializeToBuffer(buffer);
		// (verified experimentally).
		Assert.assertEquals(12, buffer.position());
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
		proxy.setBlockAndClear(BlockAspect.STONE);
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
		
		Assert.assertEquals(BlockAspect.STONE.number(), input.getData15(AspectRegistry.BLOCK, address));
		Assert.assertEquals(null, input.getDataSpecial(AspectRegistry.INVENTORY, address));
	}
}
