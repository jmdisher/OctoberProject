package com.jeffdisher.october.data;

import java.nio.ByteBuffer;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.OrientationAspect;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.utils.CuboidGenerator;


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
		CuboidData output = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		BlockAddress address = location.getBlockAddress();
		
		// Store into the block's inventory and see how that serializes.
		MutableBlockProxy proxy = new MutableBlockProxy(location, input);
		Block table = ENV.blocks.fromItem(ENV.items.getItemById("op.crafting_table"));
		proxy.setBlockAndClear(table);
		proxy.setInventory(Inventory.start(ENV.stations.getNormalInventorySize(table)).addStackable(stoneItem, 1).finish());
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		Assert.assertTrue(proxy.didChange());
		proxy.writeBack(input);
		proxy.serializeToBuffer(buffer);
		// (verified experimentally).
		Assert.assertEquals(19, buffer.position());
		byte[] raw = new byte[buffer.position()];
		buffer.flip().get(raw);
		MutationBlockSetBlock setBlock = new MutationBlockSetBlock(location, raw);
		setBlock.applyState(output);
		buffer.clear();
		
		// Illuminate this block and then serialize it to show that only that index is serialized.
		proxy = new MutableBlockProxy(location, input);
		proxy.setLight((byte)5);
		Assert.assertTrue(proxy.didChange());
		proxy.writeBack(input);
		proxy.serializeToBuffer(buffer);
		// (verified experimentally).
		Assert.assertEquals(2, buffer.position());
		raw = new byte[buffer.position()];
		buffer.flip().get(raw);
		setBlock = new MutationBlockSetBlock(location, raw);
		setBlock.applyState(output);
		buffer.clear();
		
		// Now, reset the block type and verify that the inventory is cleared (notably not the lighting, since it is updated later).
		proxy = new MutableBlockProxy(location, input);
		proxy.setBlockAndClear(STONE);
		Assert.assertTrue(proxy.didChange());
		proxy.writeBack(input);
		proxy.serializeToBuffer(buffer);
		// (verified experimentally).
		Assert.assertEquals(8, buffer.position());
		raw = new byte[buffer.position()];
		buffer.flip().get(raw);
		setBlock = new MutationBlockSetBlock(location, raw);
		setBlock.applyState(output);
		buffer.clear();
		
		Assert.assertEquals(STONE.item().number(), output.getData15(AspectRegistry.BLOCK, address));
		Assert.assertEquals(null, output.getDataSpecial(AspectRegistry.INVENTORY, address));
	}

	@Test
	public void multiBlock()
	{
		Block door = ENV.blocks.fromItem(ENV.items.getItemById("op.double_door_base"));
		AbsoluteLocation rootLocation = new AbsoluteLocation(1, 1, 1);
		AbsoluteLocation extensionLocation = rootLocation.getRelative(0, 0, 1);
		CuboidData input = CuboidGenerator.createFilledCuboid(rootLocation.getCuboidAddress(), ENV.special.AIR);
		
		MutableBlockProxy rootProxy = new MutableBlockProxy(rootLocation, input);
		rootProxy.setBlockAndClear(door);
		rootProxy.setOrientation(OrientationAspect.Direction.NORTH);
		Assert.assertTrue(rootProxy.didChange());
		rootProxy.writeBack(input);
		MutableBlockProxy extensionProxy = new MutableBlockProxy(extensionLocation, input);
		extensionProxy.setBlockAndClear(door);
		extensionProxy.setMultiBlockRoot(rootLocation);
		Assert.assertTrue(extensionProxy.didChange());
		extensionProxy.writeBack(input);
		
		Assert.assertEquals(door.item().number(), input.getData15(AspectRegistry.BLOCK, rootLocation.getBlockAddress()));
		Assert.assertEquals(OrientationAspect.directionToByte(OrientationAspect.Direction.NORTH), input.getData7(AspectRegistry.ORIENTATION, rootLocation.getBlockAddress()));
		Assert.assertEquals(door.item().number(), input.getData15(AspectRegistry.BLOCK, extensionLocation.getBlockAddress()));
		Assert.assertEquals(rootLocation, input.getDataSpecial(AspectRegistry.MULTI_BLOCK_ROOT, extensionLocation.getBlockAddress()));
		
		// Now clear these with the usual helper.
		rootProxy = new MutableBlockProxy(rootLocation, input);
		rootProxy.setBlockAndClear(ENV.special.AIR);
		Assert.assertTrue(rootProxy.didChange());
		rootProxy.writeBack(input);
		extensionProxy = new MutableBlockProxy(extensionLocation, input);
		extensionProxy.setBlockAndClear(ENV.special.AIR);
		Assert.assertTrue(extensionProxy.didChange());
		extensionProxy.writeBack(input);
		
		Assert.assertEquals(ENV.special.AIR.item().number(), input.getData15(AspectRegistry.BLOCK, rootLocation.getBlockAddress()));
		Assert.assertEquals(OrientationAspect.directionToByte(OrientationAspect.Direction.NORTH), input.getData7(AspectRegistry.ORIENTATION, rootLocation.getBlockAddress()));
		Assert.assertEquals(ENV.special.AIR.item().number(), input.getData15(AspectRegistry.BLOCK, extensionLocation.getBlockAddress()));
		Assert.assertEquals(null, input.getDataSpecial(AspectRegistry.MULTI_BLOCK_ROOT, extensionLocation.getBlockAddress()));
	}

	@Test
	public void specialSlot()
	{
		AbsoluteLocation location = new AbsoluteLocation(1, 1, 1);
		CuboidAddress cuboidAddress = location.getCuboidAddress();
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		BlockAddress address = location.getBlockAddress();
		
		MutableBlockProxy proxy = new MutableBlockProxy(location, input);
		proxy.setBlockAndClear(STONE);
		Items stack = new Items(STONE.item(), 2);
		proxy.setSpecialSlot(ItemSlot.fromStack(stack));
		
		CuboidData updated = CuboidData.mutableClone(input);
		Assert.assertTrue(proxy.didChange());
		proxy.writeBack(updated);
		Assert.assertEquals(stack, updated.getDataSpecial(AspectRegistry.SPECIAL_ITEM_SLOT, address).stack);
		
		proxy = new MutableBlockProxy(location, updated);
		proxy.setBlockAndClear(ENV.special.AIR);
		
		updated = CuboidData.mutableClone(updated);
		Assert.assertTrue(proxy.didChange());
		proxy.writeBack(updated);
		Assert.assertEquals(null, updated.getDataSpecial(AspectRegistry.SPECIAL_ITEM_SLOT, address));
	}
}
