package com.jeffdisher.october.block_movement;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.FuelState;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestMovableBlockData
{
	private static Environment ENV;
	private static Block FURNACE;
	private static Block DOUBLE_DOOR;
	private static Item LOG_ITEM;
	private static Item CHARCOAL_ITEM;
	@BeforeClass
	public static void setup() throws Throwable
	{
		ENV = Environment.createSharedInstance();
		FURNACE = ENV.blocks.fromItem(ENV.items.getItemById("op.furnace"));
		DOUBLE_DOOR = ENV.blocks.fromItem(ENV.items.getItemById("op.double_door_base"));
		LOG_ITEM = ENV.items.getItemById("op.log");
		CHARCOAL_ITEM = ENV.items.getItemById("op.charcoal");
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void canBeMoved()
	{
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		AbsoluteLocation furnace = new AbsoluteLocation(1, 2, 3);
		AbsoluteLocation doorRoot = new AbsoluteLocation(5, 6, 7);
		cuboid.setData15(AspectRegistry.BLOCK, furnace.getBlockAddress(), FURNACE.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, doorRoot.getBlockAddress(), DOUBLE_DOOR.item().number());
		
		BlockProxy furnaceProxy = BlockProxy.load(furnace.getBlockAddress(), cuboid);
		BlockProxy doorProxy = BlockProxy.load(doorRoot.getBlockAddress(), cuboid);
		
		Assert.assertTrue(MovableBlockData.canBeMoved(furnaceProxy));
		Assert.assertFalse(MovableBlockData.canBeMoved(doorProxy));
	}

	@Test
	public void moveEmptyAir()
	{
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		
		BlockProxy readProxy = BlockProxy.load(BlockAddress.fromInt(4, 5, 6), cuboid);
		MovableBlockData blockData = MovableBlockData.fromProxy(readProxy);
		
		MutableBlockProxy writeProxy = new MutableBlockProxy(new AbsoluteLocation(10, 11, 12), cuboid);
		blockData.clearProxyAndApply(writeProxy);
		Assert.assertFalse(writeProxy.didChange());
	}

	@Test
	public void moveActiveFurnace()
	{
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		AbsoluteLocation start = new AbsoluteLocation(1, 2, 3);
		AbsoluteLocation end = new AbsoluteLocation(4, 5, 6);
		cuboid.setData15(AspectRegistry.BLOCK, start.getBlockAddress(), FURNACE.item().number());
		Inventory inventory = Inventory.start(ENV.stations.getNormalInventorySize(FURNACE))
			.addStackable(LOG_ITEM, 4)
			.finish()
		;
		cuboid.setDataSpecial(AspectRegistry.INVENTORY, start.getBlockAddress(), inventory);
		Inventory fuelInventory = Inventory.start(ENV.stations.getFuelInventorySize(FURNACE))
			.addStackable(CHARCOAL_ITEM, 1)
			.finish()
		;
		FuelState fuelState = new FuelState(0, null, fuelInventory);
		cuboid.setDataSpecial(AspectRegistry.FUELLED, start.getBlockAddress(), fuelState);
		
		BlockProxy readProxy = BlockProxy.load(start.getBlockAddress(), cuboid);
		MovableBlockData blockData = MovableBlockData.fromProxy(readProxy);
		
		MutableBlockProxy writeProxy = new MutableBlockProxy(end, cuboid);
		blockData.clearProxyAndApply(writeProxy);
		Assert.assertTrue(writeProxy.didChange());
		writeProxy.writeBack(cuboid);
		
		Assert.assertEquals(cuboid.getData15(AspectRegistry.BLOCK, start.getBlockAddress()), cuboid.getData15(AspectRegistry.BLOCK, end.getBlockAddress()));
		Assert.assertEquals(cuboid.getDataSpecial(AspectRegistry.INVENTORY, start.getBlockAddress()), cuboid.getDataSpecial(AspectRegistry.INVENTORY, end.getBlockAddress()));
		Assert.assertEquals(cuboid.getDataSpecial(AspectRegistry.FUELLED, start.getBlockAddress()), cuboid.getDataSpecial(AspectRegistry.FUELLED, end.getBlockAddress()));
	}
}
