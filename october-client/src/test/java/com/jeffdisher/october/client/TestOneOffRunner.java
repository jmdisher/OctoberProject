package com.jeffdisher.october.client;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.logic.PropertyHelpers;
import com.jeffdisher.october.subactions.EntityChangeUseSelectedItemOnBlock;
import com.jeffdisher.october.subactions.EntitySubActionDropItemsAsPassive;
import com.jeffdisher.october.subactions.EntitySubActionPickUpPassive;
import com.jeffdisher.october.subactions.EntitySubActionPopOutOfBlock;
import com.jeffdisher.october.subactions.MutationPlaceSelectedBlock;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.PartialPassive;
import com.jeffdisher.october.types.PassiveType;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.CuboidGenerator;
import com.jeffdisher.october.utils.Encoding;


public class TestOneOffRunner
{
	private static final long MILLIS_PER_TICK = 100L;
	private static Environment ENV;
	private static Item STONE_ITEM;
	private static Item DIRT_ITEM;
	private static Item STONE_HOE;
	private static Block STONE_BLOCK;
	@BeforeClass
	public static void setup() throws Throwable
	{
		ENV = Environment.createSharedInstance();
		STONE_ITEM = ENV.items.getItemById("op.stone");
		DIRT_ITEM = ENV.items.getItemById("op.dirt");
		STONE_HOE = ENV.items.getItemById("op.stone_hoe");
		STONE_BLOCK = ENV.blocks.fromItem(STONE_ITEM);
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
		
		OneOffRunner.InputState start = new OneOffRunner.InputState(entity, Map.of(airAddress, airCuboid
				, stoneAddress, stoneCuboid
		), Map.of(), Map.of()
			, _buildProxyLoader(airCuboid, stoneCuboid)
		);
		_Events catcher = new _Events();
		OneOffRunner.OutputState end = OneOffRunner.runOneChange(start, catcher, MILLIS_PER_TICK, 1L, new OneOffSubActionWrapper(place));
		
		Assert.assertEquals(EventRecord.Type.BLOCK_PLACED, catcher.event.type());
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
		
		OneOffRunner.InputState start = new OneOffRunner.InputState(entity, Map.of(airAddress, airCuboid
				, stoneAddress, stoneCuboid
		), Map.of(), Map.of()
			, _buildProxyLoader(airCuboid, stoneCuboid)
		);
		_Events catcher = new _Events();
		OneOffRunner.OutputState end = OneOffRunner.runOneChange(start, catcher, MILLIS_PER_TICK, 1L, new OneOffSubActionWrapper(place));
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
		
		OneOffRunner.InputState start = new OneOffRunner.InputState(entity
			, Map.of(address, cuboid)
			, Map.of()
			, Map.of()
			, _buildProxyLoader(cuboid)
		);
		_Events catcher = new _Events();
		OneOffRunner.OutputState end = OneOffRunner.runOneChange(start, catcher, MILLIS_PER_TICK, 1L, new OneOffSubActionWrapper(place));
		
		Assert.assertEquals(EventRecord.Type.BLOCK_PLACED, catcher.event.type());
		Assert.assertEquals(0, end.thisEntity().inventory().currentEncumbrance);
	}

	@Test
	public void dropItemAsPassive() throws Throwable
	{
		// We should see the inventory change immediately but the passive will not appear (as it comes from the server).
		int entityId = 1;
		MutableEntity mutable = MutableEntity.createForTest(entityId);
		mutable.newInventory.addAllItems(DIRT_ITEM, 1);
		mutable.setSelectedKey(1);
		mutable.newLocation = new EntityLocation(1.0f, 2.0f, 1.0f);
		Entity entity = mutable.freeze();
		
		AbsoluteLocation entityLocation = mutable.newLocation.getBlockLocation();
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, STONE_BLOCK);
		cuboid.setData15(AspectRegistry.BLOCK, entityLocation.getBlockAddress(), ENV.special.AIR.item().number());
		
		OneOffRunner.InputState start = new OneOffRunner.InputState(entity
			, Map.of(address, cuboid)
			, Map.of()
			, Map.of()
			, _buildProxyLoader(cuboid)
		);
		EntitySubActionDropItemsAsPassive drop = new EntitySubActionDropItemsAsPassive(1, true);
		OneOffRunner.OutputState end = OneOffRunner.runOneChange(start, null, MILLIS_PER_TICK, 1L, new OneOffSubActionWrapper(drop));
		
		Entity result = end.thisEntity();
		Assert.assertEquals(0, result.inventory().currentEncumbrance);
		Assert.assertEquals(0, MutableEntity.existing(result).getSelectedKey());
	}

	@Test
	public void pickUpPassive() throws Throwable
	{
		// Make sure that the pick-up action works correctly in one-off, requiring that it sees the passives.
		int entityId = 1;
		MutableEntity mutable = MutableEntity.createForTest(entityId);
		mutable.newLocation = new EntityLocation(1.0f, 2.0f, 1.0f);
		Entity entity = mutable.freeze();
		
		AbsoluteLocation entityLocation = mutable.newLocation.getBlockLocation();
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, STONE_BLOCK);
		cuboid.setData15(AspectRegistry.BLOCK, entityLocation.getBlockAddress(), ENV.special.AIR.item().number());
		
		ItemSlot slot = ItemSlot.fromStack(new Items(STONE_ITEM, 5));
		PartialPassive near = new PartialPassive(1
			, PassiveType.ITEM_SLOT
			, mutable.newLocation
			, new EntityLocation(0.0f, 0.0f, 0.0f)
			, slot
		);
		PartialPassive far = new PartialPassive(2
			, PassiveType.ITEM_SLOT
			, new EntityLocation(3.0f, 0.0f, 2.0f)
			, new EntityLocation(0.0f, 0.0f, 0.0f)
			, slot
		);
		
		OneOffRunner.InputState start = new OneOffRunner.InputState(entity
			, Map.of(address, cuboid)
			, Map.of()
			, Map.of(near.id(), near
				, far.id(), far
			)
			, _buildProxyLoader(cuboid)
		);
		
		// Show that this works when in range, but fails when out of range.
		Assert.assertNotNull(OneOffRunner.runOneChange(start, null, MILLIS_PER_TICK, 1L, new OneOffSubActionWrapper(new EntitySubActionPickUpPassive(near.id()))));
		Assert.assertNull(OneOffRunner.runOneChange(start, null, MILLIS_PER_TICK, 1L, new OneOffSubActionWrapper(new EntitySubActionPickUpPassive(far.id()))));
	}

	@Test
	public void popOutOfBlock() throws Throwable
	{
		// Just show a basic instance of popping out of a block.
		int entityId = 1;
		MutableEntity mutable = MutableEntity.createForTest(entityId);
		mutable.newLocation = new EntityLocation(1.0f, 2.0f, 1.8f);
		Entity entity = mutable.freeze();
		
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, mutable.newLocation.getBlockLocation().getBlockAddress(), STONE_BLOCK.item().number());
		
		OneOffRunner.InputState start = new OneOffRunner.InputState(entity
			, Map.of(address, cuboid)
			, Map.of()
			, Map.of()
			, _buildProxyLoader(cuboid)
		);
		EntityLocation target = new EntityLocation(1.0f, 2.0f, 2.0f);
		EntitySubActionPopOutOfBlock<IMutablePlayerEntity> pop = new EntitySubActionPopOutOfBlock<>(target);
		OneOffRunner.OutputState end = OneOffRunner.runOneChange(start, null, MILLIS_PER_TICK, 1L, new OneOffSubActionWrapper(pop));
		
		Entity result = end.thisEntity();
		Assert.assertEquals(target, result.location());
	}

	@Test
	public void ignoreHoeWear() throws Throwable
	{
		// Just shoe that using a hoe on a block ignores the tool durability degradation.
		MutableEntity mutable = MutableEntity.createForTest(1);
		mutable.newLocation = new EntityLocation(5.0f, 5.0f, 5.0f);
		NonStackableItem hoe = PropertyHelpers.newItemWithDefaults(ENV, STONE_HOE);
		mutable.newInventory.addNonStackableAllowingOverflow(hoe);
		mutable.setSelectedKey(1);
		Entity entity = mutable.freeze();
		AbsoluteLocation target = mutable.newLocation.getBlockLocation().getRelative(0, 0, -1);
		
		CuboidAddress airAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(airAddress, ENV.special.AIR);
		airCuboid.setData15(AspectRegistry.BLOCK, target.getBlockAddress(), DIRT_ITEM.number());
		
		EntityChangeUseSelectedItemOnBlock till = new EntityChangeUseSelectedItemOnBlock(target);
		OneOffRunner.InputState start = new OneOffRunner.InputState(entity, Map.of(airAddress, airCuboid
		), Map.of(), Map.of()
			, _buildProxyLoader(airCuboid)
		);
		OneOffRunner.OutputState end = OneOffRunner.runOneChange(start, null, MILLIS_PER_TICK, 1_000L, new OneOffSubActionWrapper(till));
		
		Assert.assertTrue(hoe == end.thisEntity().inventory().getNonStackableForKey(1));
	}


	private static Function<AbsoluteLocation, BlockProxy> _buildProxyLoader(CuboidData... cuboids)
	{
		Map<CuboidAddress, CuboidData> map = Arrays.stream(cuboids)
			.collect(Collectors.toMap((CuboidData cuboid) -> cuboid.getCuboidAddress(), (CuboidData cuboid) -> cuboid))
		;
		return (AbsoluteLocation location) -> {
			CuboidData cuboid = map.get(location.getCuboidAddress());
			return (null != cuboid)
				? new BlockProxy(location.getBlockAddress(), cuboid)
				: null
			;
		};
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
