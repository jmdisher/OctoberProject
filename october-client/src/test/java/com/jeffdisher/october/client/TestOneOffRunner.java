package com.jeffdisher.october.client;

import java.util.Map;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.logic.HeightMapHelpers;
import com.jeffdisher.october.mutations.MutationPlaceSelectedBlock;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.CuboidGenerator;
import com.jeffdisher.october.utils.Encoding;


public class TestOneOffRunner
{
	private static final long MILLIS_PER_TICK = 100L;
	private static Environment ENV;
	private static Item STONE_ITEM;
	private static Item DIRT_ITEM;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		STONE_ITEM = ENV.items.getItemById("op.stone");
		DIRT_ITEM = ENV.items.getItemById("op.dirt");
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void basicMutation() throws Throwable
	{
		// Create a world with the player and 2 cuboids, showing that we can place a block and verify changes.
		MutableEntity mutable = MutableEntity.createForTest(1);
		mutable.newLocation = new EntityLocation(5.0f, 5.0f, 0.0f);
		mutable.setSelectedKey(1);
		mutable.newInventory.addAllItems(STONE_ITEM, 1);
		Entity entity = mutable.freeze();
		AbsoluteLocation target = mutable.newLocation.getBlockLocation().getRelative(1, 1, 0);
		
		CuboidAddress airAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(airAddress, ENV.special.AIR);
		CuboidAddress stoneAddress = CuboidAddress.fromInt(0, 0, -1);
		CuboidData stoneCuboid = CuboidGenerator.createFilledCuboid(stoneAddress, ENV.blocks.fromItem(STONE_ITEM));
		MutationPlaceSelectedBlock place = new MutationPlaceSelectedBlock(target, null);
		
		OneOffRunner.StatePackage start = new OneOffRunner.StatePackage(entity, Map.of(airAddress, airCuboid
				, stoneAddress, stoneCuboid
		), Map.of(airAddress, HeightMapHelpers.buildHeightMap(airCuboid)
				, stoneAddress, HeightMapHelpers.buildHeightMap(stoneCuboid)
		), null, Map.of());
		_Events catcher = new _Events();
		OneOffRunner.StatePackage end = OneOffRunner.runOneChange(start, catcher, MILLIS_PER_TICK, 1L, new OneOffSubActionWrapper(place));
		
		Assert.assertEquals(EventRecord.Type.BLOCK_PLACED, catcher.event.type());
		Assert.assertTrue(start.world().get(stoneAddress) == end.world().get(stoneAddress));
		Assert.assertEquals(STONE_ITEM.number(), end.world().get(stoneAddress).getData15(AspectRegistry.BLOCK, target.getBlockAddress()));
		Assert.assertEquals(0, end.thisEntity().inventory().currentEncumbrance);
	}

	@Test
	public void failedBasicMutation() throws Throwable
	{
		MutableEntity mutable = MutableEntity.createForTest(1);
		mutable.newLocation = new EntityLocation(5.0f, 5.0f, 0.0f);
		mutable.setSelectedKey(1);
		mutable.newInventory.addAllItems(STONE_ITEM, 1);
		Entity entity = mutable.freeze();
		AbsoluteLocation target = mutable.newLocation.getBlockLocation().getRelative(1, 1, 0);
		
		CuboidAddress airAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(airAddress, ENV.special.AIR);
		airCuboid.setData15(AspectRegistry.BLOCK, target.getBlockAddress(), STONE_ITEM.number());
		CuboidAddress stoneAddress = CuboidAddress.fromInt(0, 0, -1);
		CuboidData stoneCuboid = CuboidGenerator.createFilledCuboid(stoneAddress, ENV.blocks.fromItem(STONE_ITEM));
		MutationPlaceSelectedBlock place = new MutationPlaceSelectedBlock(target, null);
		
		OneOffRunner.StatePackage start = new OneOffRunner.StatePackage(entity, Map.of(airAddress, airCuboid
				, stoneAddress, stoneCuboid
		), Map.of(airAddress, HeightMapHelpers.buildHeightMap(airCuboid)
				, stoneAddress, HeightMapHelpers.buildHeightMap(stoneCuboid)
		), null, Map.of());
		_Events catcher = new _Events();
		OneOffRunner.StatePackage end = OneOffRunner.runOneChange(start, catcher, MILLIS_PER_TICK, 1L, new OneOffSubActionWrapper(place));
		Assert.assertNull(end);
	}

	@Test
	public void mutationSchedulesMutation() throws Throwable
	{
		// Tests that there are no problems when a block mutation schedules a secondary block mutation (even though OneOffRunner ignores these).
		MutableEntity mutable = MutableEntity.createForTest(1);
		mutable.newLocation = new EntityLocation(5.0f, 5.0f, 1.0f);
		mutable.setSelectedKey(1);
		mutable.newInventory.addAllItems(DIRT_ITEM, 1);
		Entity entity = mutable.freeze();
		AbsoluteLocation target = mutable.newLocation.getBlockLocation().getRelative(1, 1, 0);
		
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		short grass = ENV.items.getItemById("op.grass").number();
		for (int y = 0; y < Encoding.CUBOID_EDGE_SIZE; ++y)
		{
			for (int x = 0; x < Encoding.CUBOID_EDGE_SIZE; ++x)
			{
				cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(x, y, 0), grass);
			}
		}
		MutationPlaceSelectedBlock place = new MutationPlaceSelectedBlock(target, null);
		
		OneOffRunner.StatePackage start = new OneOffRunner.StatePackage(entity
			, Map.of(address, cuboid)
			, Map.of(address, HeightMapHelpers.buildHeightMap(cuboid))
			, null
			, Map.of()
		);
		_Events catcher = new _Events();
		OneOffRunner.StatePackage end = OneOffRunner.runOneChange(start, catcher, MILLIS_PER_TICK, 1L, new OneOffSubActionWrapper(place));
		
		Assert.assertEquals(EventRecord.Type.BLOCK_PLACED, catcher.event.type());
		Assert.assertEquals(DIRT_ITEM.number(), end.world().get(address).getData15(AspectRegistry.BLOCK, target.getBlockAddress()));
		Assert.assertEquals(0, end.thisEntity().inventory().currentEncumbrance);
	}


	private static class _Events implements TickProcessingContext.IEventSink
	{
		private EventRecord event;
		@Override
		public void post(EventRecord event)
		{
			Assert.assertNull(this.event);
			this.event = event;
		}
	}
}
