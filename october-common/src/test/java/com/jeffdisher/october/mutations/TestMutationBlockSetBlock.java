package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;
import java.util.Set;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestMutationBlockSetBlock
{
	private static Environment ENV;
	private static Item STONE_ITEM;
	private static Block STONE;
	private static Block LOG;
	@BeforeClass
	public static void setup() throws Throwable
	{
		ENV = Environment.createSharedInstance();
		STONE_ITEM = ENV.items.getItemById("op.stone");
		STONE = ENV.blocks.fromItem(STONE_ITEM);
		LOG = ENV.blocks.fromItem(ENV.items.getItemById("op.log"));
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void matchedData()
	{
		CuboidAddress address = CuboidAddress.fromInt(-1, 0, 1);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		AbsoluteLocation location = address.getBase();
		MutableBlockProxy proxy = new MutableBlockProxy(location, cuboid);
		proxy.setBlockAndClear(STONE);
		proxy.didChange();
		proxy.writeBack(cuboid);
		MutationBlockSetBlock one = MutationBlockSetBlock.extractFromProxy(ByteBuffer.allocate(64), proxy);
		MutationBlockSetBlock two = MutationBlockSetBlock.extractFromProxy(ByteBuffer.allocate(64), proxy);
		proxy.setBlockAndClear(LOG);
		proxy.didChange();
		proxy.writeBack(cuboid);
		MutationBlockSetBlock three = MutationBlockSetBlock.extractFromProxy(ByteBuffer.allocate(64), proxy);
		Assert.assertTrue(one.doesDataMatch(two));
		Assert.assertFalse(one.doesDataMatch(three));
	}

	@Test
	public void aspectsOverOther()
	{
		CuboidAddress address = CuboidAddress.fromInt(-1, 0, 1);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		AbsoluteLocation location = address.getBase();
		MutableBlockProxy proxy = new MutableBlockProxy(location, cuboid);
		proxy.setBlockAndClear(STONE);
		proxy.didChange();
		proxy.writeBack(cuboid);
		MutationBlockSetBlock one = MutationBlockSetBlock.extractFromProxy(ByteBuffer.allocate(64), proxy);
		proxy.setBlockAndClear(LOG);
		proxy.setInventory(Inventory.start(10).addStackable(STONE_ITEM, 1).finish());
		proxy.didChange();
		proxy.writeBack(cuboid);
		MutationBlockSetBlock three = MutationBlockSetBlock.extractFromProxy(ByteBuffer.allocate(64), proxy);
		
		Set<Aspect<?, ?>> aspects = one.getChangedAspectsAfter(three);
		Assert.assertEquals(1, aspects.size());
		aspects = three.getChangedAspectsAfter(one);
		Assert.assertEquals(2, aspects.size());
	}

	@Test
	public void aspectsOverCuboid()
	{
		CuboidAddress address = CuboidAddress.fromInt(-1, 0, 1);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		AbsoluteLocation location = address.getBase();
		MutableBlockProxy proxy = new MutableBlockProxy(location, cuboid);
		proxy.setBlockAndClear(STONE);
		proxy.didChange();
		proxy.writeBack(cuboid);
		MutationBlockSetBlock one = MutationBlockSetBlock.extractFromProxy(ByteBuffer.allocate(64), proxy);
		proxy.setBlockAndClear(LOG);
		proxy.setInventory(Inventory.start(10).addStackable(STONE_ITEM, 1).finish());
		proxy.didChange();
		proxy.writeBack(cuboid);
		MutationBlockSetBlock three = MutationBlockSetBlock.extractFromProxy(ByteBuffer.allocate(64), proxy);
		
		cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		Set<Aspect<?, ?>> aspects = one.changedAspectsVersusCuboid(cuboid);
		Assert.assertEquals(1, aspects.size());
		Assert.assertEquals(AspectRegistry.BLOCK, aspects.iterator().next());
		cuboid.setData15(AspectRegistry.BLOCK, location.getBlockAddress(), LOG.item().number());
		aspects = three.changedAspectsVersusCuboid(cuboid);
		Assert.assertEquals(1, aspects.size());
		Assert.assertEquals(AspectRegistry.INVENTORY, aspects.iterator().next());
	}

	@Test
	public void merged()
	{
		CuboidAddress address = CuboidAddress.fromInt(-1, 0, 1);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		AbsoluteLocation location = address.getBase();
		MutableBlockProxy proxy = new MutableBlockProxy(location, cuboid);
		proxy.setBlockAndClear(STONE);
		proxy.didChange();
		proxy.writeBack(cuboid);
		MutationBlockSetBlock one = MutationBlockSetBlock.extractFromProxy(ByteBuffer.allocate(64), proxy);
		proxy.setBlockAndClear(LOG);
		proxy.setInventory(Inventory.start(10).addStackable(STONE_ITEM, 1).finish());
		proxy.didChange();
		proxy.writeBack(cuboid);
		MutationBlockSetBlock three = MutationBlockSetBlock.extractFromProxy(ByteBuffer.allocate(64), proxy);
		
		cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		MutationBlockSetBlock merged = MutationBlockSetBlock.merge(one, three);
		Set<Aspect<?, ?>> aspects = merged.changedAspectsVersusCuboid(cuboid);
		Assert.assertEquals(2, aspects.size());
		merged.applyState(cuboid);
		Assert.assertEquals(LOG.item().number(), cuboid.getData15(AspectRegistry.BLOCK, location.getBlockAddress()));
		Assert.assertEquals(1, cuboid.getDataSpecial(AspectRegistry.INVENTORY, location.getBlockAddress()).getCount(STONE_ITEM));
		
		cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		merged = MutationBlockSetBlock.merge(three, one);
		aspects = merged.changedAspectsVersusCuboid(cuboid);
		Assert.assertEquals(2, aspects.size());
		merged.applyState(cuboid);
		Assert.assertEquals(STONE.item().number(), cuboid.getData15(AspectRegistry.BLOCK, location.getBlockAddress()));
		Assert.assertEquals(1, cuboid.getDataSpecial(AspectRegistry.INVENTORY, location.getBlockAddress()).getCount(STONE_ITEM));
	}

	@Test
	public void checkSingleAspect()
	{
		CuboidAddress address = CuboidAddress.fromInt(-1, 0, 1);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		AbsoluteLocation location = address.getBase();
		MutableBlockProxy proxy = new MutableBlockProxy(location, cuboid);
		proxy.setBlockAndClear(STONE);
		proxy.didChange();
		proxy.writeBack(cuboid);
		MutationBlockSetBlock one = MutationBlockSetBlock.extractFromProxy(ByteBuffer.allocate(64), proxy);
		proxy.setInventory(Inventory.start(10).addStackable(STONE_ITEM, 1).finish());
		proxy.didChange();
		proxy.writeBack(cuboid);
		MutationBlockSetBlock two = MutationBlockSetBlock.extractFromProxy(ByteBuffer.allocate(64), proxy);
		MutationBlockSetBlock merged = MutationBlockSetBlock.merge(one, two);
		
		Assert.assertTrue(one.isSingleAspect(AspectRegistry.BLOCK));
		Assert.assertFalse(one.isSingleAspect(AspectRegistry.LIGHT));
		Assert.assertFalse(two.isSingleAspect(AspectRegistry.BLOCK));
		Assert.assertTrue(two.isSingleAspect(AspectRegistry.INVENTORY));
		Assert.assertFalse(merged.isSingleAspect(AspectRegistry.BLOCK));
		Assert.assertFalse(merged.isSingleAspect(AspectRegistry.INVENTORY));
		Assert.assertFalse(merged.isSingleAspect(AspectRegistry.LIGHT));
	}
}
