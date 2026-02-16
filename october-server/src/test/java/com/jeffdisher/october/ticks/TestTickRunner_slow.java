package com.jeffdisher.october.ticks;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.actions.EntityActionSimpleMove;
import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.aspects.LightAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.CreatureIdAssigner;
import com.jeffdisher.october.logic.HeightMapHelpers;
import com.jeffdisher.october.logic.OrientationHelpers;
import com.jeffdisher.october.logic.PassiveIdAssigner;
import com.jeffdisher.october.logic.ScheduledMutation;
import com.jeffdisher.october.mutations.EntityChangeMutation;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.MutationBlockFurnaceCraft;
import com.jeffdisher.october.mutations.MutationBlockPeriodic;
import com.jeffdisher.october.mutations.MutationBlockReplace;
import com.jeffdisher.october.mutations.MutationBlockStoreItems;
import com.jeffdisher.october.mutations.ReplaceBlockMutation;
import com.jeffdisher.october.persistence.SuspendedCuboid;
import com.jeffdisher.october.persistence.SuspendedEntity;
import com.jeffdisher.october.server.ServerRunner;
import com.jeffdisher.october.subactions.EntityChangeIncrementalBlockBreak;
import com.jeffdisher.october.subactions.EntityChangeSetBlockLogicState;
import com.jeffdisher.october.subactions.MutationEntityPushItems;
import com.jeffdisher.october.subactions.MutationPlaceSelectedBlock;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Difficulty;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.IEntitySubAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.WorldConfig;
import com.jeffdisher.october.utils.CuboidGenerator;
import com.jeffdisher.october.utils.Encoding;


/**
 * Tests split out from the existing TestTickRunner due to them being both slow and stable (they are rarely useful in
 * new development testing but do slow it down).
 */
public class TestTickRunner_slow
{
	public static final long MILLIS_PER_TICK = 10L;
	private static Environment ENV;
	private static Item STONE_ITEM;
	private static Item LOG_ITEM;
	private static Item PLANK_ITEM;
	private static Item CHARCOAL_ITEM;
	private static Item DIRT_ITEM;
	private static Item SAPLING_ITEM;
	private static Item LANTERN_ITEM;
	private static Item LEAF_ITEM;
	private static Item TILLED_SOIL_ITEM;
	private static Item WHEAT_SEED_ITEM;
	private static Item WHEAT_ITEM_ITEM;
	private static Item WHEAT_SEEDLING_ITEM;
	private static Item WHEAT_YOUNG_ITEM;
	private static Item WHEAT_MATURE_ITEM;
	private static Item WATER_STRONG;
	private static Block STONE;
	private static Block WATER_SOURCE;
	@BeforeClass
	public static void setup() throws Throwable
	{
		ENV = Environment.createSharedInstance();
		STONE_ITEM = ENV.items.getItemById("op.stone");
		LOG_ITEM = ENV.items.getItemById("op.log");
		PLANK_ITEM = ENV.items.getItemById("op.plank");
		CHARCOAL_ITEM = ENV.items.getItemById("op.charcoal");
		DIRT_ITEM = ENV.items.getItemById("op.dirt");
		SAPLING_ITEM = ENV.items.getItemById("op.sapling");
		LANTERN_ITEM = ENV.items.getItemById("op.lantern");
		LEAF_ITEM = ENV.items.getItemById("op.leaf");
		TILLED_SOIL_ITEM = ENV.items.getItemById("op.tilled_soil");
		WHEAT_SEED_ITEM = ENV.items.getItemById("op.wheat_seed");
		WHEAT_ITEM_ITEM = ENV.items.getItemById("op.wheat_item");
		WHEAT_SEEDLING_ITEM = ENV.items.getItemById("op.wheat_seedling");
		WHEAT_YOUNG_ITEM = ENV.items.getItemById("op.wheat_young");
		WHEAT_MATURE_ITEM = ENV.items.getItemById("op.wheat_mature");
		WATER_STRONG = ENV.items.getItemById("op.water_strong");
		STONE = ENV.blocks.fromItem(STONE_ITEM);
		WATER_SOURCE = ENV.blocks.fromItem(ENV.items.getItemById("op.water_source"));
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void furnaceLoadAndCraft()
	{
		// Create a cuboid of furnaces, load one with fuel and ingredients, and watch it craft.
		int burnPlankMillis = ENV.fuel.millisOfFuel(PLANK_ITEM);
		long burnPlankTicks = burnPlankMillis / MILLIS_PER_TICK;
		long craftCharcoalMillis = ENV.crafting.getCraftById("op.furnace_logs_to_charcoal").millisPerCraft;
		long craftCharcoalTicks = craftCharcoalMillis / MILLIS_PER_TICK;
		
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.blocks.fromItem(ENV.items.getItemById("op.furnace")));
		TickRunner runner = _createTestRunner();
		int entityId1 = 1;
		int entityId2 = 2;
		MutableEntity mutable1 = MutableEntity.createForTest(entityId1);
		mutable1.newInventory.addAllItems(LOG_ITEM, 3);
		int logKey = mutable1.newInventory.getIdOfStackableType(LOG_ITEM);
		Entity entity1 = mutable1.freeze();
		MutableEntity mutable2 = MutableEntity.createForTest(entityId2);
		mutable2.newInventory.addAllItems(PLANK_ITEM, 2);
		int plankKey = mutable2.newInventory.getIdOfStackableType(PLANK_ITEM);
		Entity entity2 = mutable2.freeze();
		runner.setupChangesForTick(List.of(new SuspendedCuboid<IReadOnlyCuboidData>(cuboid, HeightMapHelpers.buildHeightMap(cuboid), List.of(), List.of(), Map.of(), List.of()))
				, null
				, List.of(new SuspendedEntity(entity1, List.of()), new SuspendedEntity(entity2, List.of()))
				, null
		);
		
		runner.start();
		// Remember that the first tick is just returning the empty state.
		runner.waitForPreviousTick();
		runner.startNextTick();
		TickSnapshot snap = runner.waitForPreviousTick();
		Assert.assertNotNull(snap.cuboids().get(address));
		
		// Load the furnace with fuel and material.
		AbsoluteLocation location = new AbsoluteLocation(0, 0, 0);
		BlockAddress block = location.getBlockAddress();
		runner.enqueueEntityChange(entityId1, _wrapSubAction(entity1, new MutationEntityPushItems(location, logKey, 3, Inventory.INVENTORY_ASPECT_INVENTORY)), 1L);
		runner.enqueueEntityChange(entityId2, _wrapSubAction(entity2, new MutationEntityPushItems(location, plankKey, 2, Inventory.INVENTORY_ASPECT_FUEL)), 2L);
		runner.startNextTick();
		snap = runner.waitForPreviousTick();
		Assert.assertEquals(2, snap.stats().committedEntityMutationCount());
		// We should see the two calls to accept the items.
		Assert.assertTrue(snap.cuboids().get(address).scheduledBlockMutations().get(0).mutation() instanceof MutationBlockStoreItems);
		Assert.assertTrue(snap.cuboids().get(address).scheduledBlockMutations().get(1).mutation() instanceof MutationBlockStoreItems);
		
		// Run the next tick to see the craft scheduled.
		runner.startNextTick();
		snap = runner.waitForPreviousTick();
		Assert.assertEquals(2, snap.stats().committedCuboidMutationCount());
		// We should see the two calls to accept the items.
		Assert.assertTrue(snap.cuboids().get(address).scheduledBlockMutations().get(0).mutation() instanceof MutationBlockFurnaceCraft);
		BlockProxy proxy = new BlockProxy(block, snap.cuboids().get(address).completed());
		Assert.assertEquals(3, proxy.getInventory().getCount(LOG_ITEM));
		Assert.assertEquals(2, proxy.getFuel().fuelInventory().getCount(PLANK_ITEM));
		
		// Loop until 1 tick before the end of the craft.
		int burnedMillis = 0;
		int craftedMillis = 0;
		int logCount = 3;
		int plankCount = 1;
		for (int i = 1; i < (3 * craftCharcoalTicks); ++i)
		{
			if (0 == (i % craftCharcoalTicks))
			{
				logCount -= 1;
			}
			if ((burnPlankTicks + 1) == i)
			{
				plankCount -= 1;
			}
			burnedMillis = (burnedMillis + (int)MILLIS_PER_TICK) % burnPlankMillis;
			craftedMillis = (craftedMillis + (int)MILLIS_PER_TICK) % 1000;
			runner.startNextTick();
			snap = runner.waitForPreviousTick();
			
			Assert.assertEquals(1, snap.stats().committedCuboidMutationCount());
			Assert.assertTrue(snap.cuboids().get(address).scheduledBlockMutations().get(0).mutation() instanceof MutationBlockFurnaceCraft);
			proxy = new BlockProxy(block, snap.cuboids().get(address).completed());
			Assert.assertEquals(logCount, proxy.getInventory().getCount(LOG_ITEM));
			Assert.assertEquals(plankCount, proxy.getFuel().fuelInventory().getCount(PLANK_ITEM));
			if (0 != (i % burnPlankTicks))
			{
				Assert.assertEquals(burnedMillis, burnPlankMillis - proxy.getFuel().millisFuelled());
				Assert.assertEquals(PLANK_ITEM, proxy.getFuel().currentFuel());
			}
			else
			{
				Assert.assertNull(proxy.getFuel().currentFuel());
			}
			if (0 != (i % craftCharcoalTicks))
			{
				Assert.assertEquals(craftedMillis, proxy.getCrafting().completedMillis());
			}
		}
		// Run the last tick to finish things.
		burnedMillis += MILLIS_PER_TICK;
		runner.startNextTick();
		snap = runner.waitForPreviousTick();
		
		Assert.assertEquals(1, snap.stats().committedCuboidMutationCount());
		Assert.assertTrue(snap.cuboids().get(address).scheduledBlockMutations().get(0).mutation() instanceof MutationBlockFurnaceCraft);
		proxy = new BlockProxy(block, snap.cuboids().get(address).completed());
		Assert.assertEquals(0, proxy.getInventory().getCount(LOG_ITEM));
		Assert.assertEquals(3, proxy.getInventory().getCount(CHARCOAL_ITEM));
		Assert.assertEquals(burnedMillis, burnPlankMillis - proxy.getFuel().millisFuelled());
		Assert.assertEquals(PLANK_ITEM, proxy.getFuel().currentFuel());
		Assert.assertNull(proxy.getCrafting());
		
		// Now, wait for the fuel to finish.
		int ticksToFinishBurn = (int)((burnPlankMillis - burnedMillis) / MILLIS_PER_TICK);
		for (int i = 0; i < ticksToFinishBurn; ++i)
		{
			burnedMillis += MILLIS_PER_TICK;
			runner.startNextTick();
			snap = runner.waitForPreviousTick();
			
			Assert.assertEquals(1, snap.stats().committedCuboidMutationCount());
			proxy = new BlockProxy(block, snap.cuboids().get(address).completed());
			Assert.assertEquals(burnedMillis, burnPlankMillis - proxy.getFuel().millisFuelled());
		}
		Assert.assertEquals(0, proxy.getFuel().millisFuelled());
		Assert.assertNull(proxy.getFuel().currentFuel());
		
		// Note that we no longer see block update events in the scheduled mutations and nothing else was scheduled.
		Assert.assertEquals(0, snap.cuboids().values().iterator().next().scheduledBlockMutations().size());
		runner.startNextTick();
		snap = runner.waitForPreviousTick();
		Assert.assertEquals(0, snap.cuboids().values().iterator().next().scheduledBlockMutations().size());
		
		runner.shutdown();
	}

	@Test
	public void waterFlowsIntoAirCuboid()
	{
		// Create a cuboid of water sources, run a tick, then load a cuboud of air below it and observe the water flow.
		WorldConfig config = new WorldConfig();
		config.shouldSynthesizeUpdatesOnLoad = true;
		TickRunner runner = _createTestRunnerWithConfig(config);
		runner.start();
		
		// Load the initial cuboid and run a tick to verify nothing happens.
		CuboidAddress address0 = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid0 = CuboidGenerator.createFilledCuboid(address0, WATER_SOURCE);
		runner.setupChangesForTick(List.of(new SuspendedCuboid<IReadOnlyCuboidData>(cuboid0, HeightMapHelpers.buildHeightMap(cuboid0), List.of(), List.of(), Map.of(), List.of()))
				, null
				, null
				, null
		);
		runner.startNextTick();
		TickSnapshot snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(1, snapshot.cuboids().size());
		Assert.assertEquals(0, snapshot.cuboids().values().iterator().next().scheduledBlockMutations().size());
		
		// Now, load an air cuboid below this and verify that the water start falling.
		CuboidAddress address1 = CuboidAddress.fromInt(0, 0, -1);
		CuboidData cuboid1 = CuboidGenerator.createFilledCuboid(address1, ENV.special.AIR);
		runner.setupChangesForTick(List.of(new SuspendedCuboid<IReadOnlyCuboidData>(cuboid1, HeightMapHelpers.buildHeightMap(cuboid1), List.of(), List.of(), Map.of(), List.of()))
				, null
				, null
				, null
		);
		
		// We should see a layer modified (1024 = 32 * 32) for each of the 32 layers.
		long millisToFlow = ENV.liquids.flowDelayMillis(WATER_SOURCE);
		int ticksToPass = (int)(millisToFlow / MILLIS_PER_TICK);
		for (int i = 0; i < 32; ++i)
		{
			// Run the update events.
			runner.startNextTick();
			snapshot = runner.waitForPreviousTick();
			
			// Wait for the movement.
			for (int j = 0; j < ticksToPass; ++j)
			{
				runner.startNextTick();
				snapshot = runner.waitForPreviousTick();
				Assert.assertTrue(snapshot.cuboids().values().stream().filter((TickSnapshot.SnapshotCuboid cuboid) -> (null != cuboid.blockChanges())).toList().isEmpty());
			}
			
			// Apply the actual movement.
			runner.startNextTick();
			snapshot = runner.waitForPreviousTick();
			Assert.assertFalse(snapshot.cuboids().values().stream().filter((TickSnapshot.SnapshotCuboid cuboid) -> (null != cuboid.blockChanges())).toList().isEmpty());
			Assert.assertEquals(2, snapshot.cuboids().size());
			Assert.assertNull(snapshot.cuboids().get(address0).blockChanges());
			Assert.assertEquals(1024, snapshot.cuboids().get(address1).blockChanges().size());
		}
		
		// Now here should be none and there should be strong flowing water at the bottom.
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(2, snapshot.cuboids().size());
		Assert.assertNull(snapshot.cuboids().get(address0).blockChanges());
		Assert.assertNull(snapshot.cuboids().get(address1).blockChanges());
		Assert.assertEquals(WATER_STRONG.number(), snapshot.cuboids().get(address1).completed().getData15(AspectRegistry.BLOCK, BlockAddress.fromInt(0, 0, 0)));
		
		runner.shutdown();
	}

	@Test
	public void waterCascade1()
	{
		// Create a single cascade cuboid, add a dirt block and water source in the top level, break the block, wait until the water completes flowing.
		WorldConfig config = new WorldConfig();
		TickRunner runner = _createTestRunnerWithConfig(config);
		runner.start();
		
		CuboidAddress address = CuboidAddress.fromInt(-3, -4, -5);
		CuboidData cascade = _buildCascade(address);
		AbsoluteLocation plug = cascade.getCuboidAddress().getBase().getRelative(16, 16, 30);
		cascade.setData15(AspectRegistry.BLOCK, plug.getBlockAddress(), DIRT_ITEM.number());
		cascade.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(16, 16, 31), WATER_SOURCE.item().number());
		
		int entityId = 1;
		MutableEntity mutable = MutableEntity.createForTest(entityId);
		mutable.newLocation = new EntityLocation(plug.x(), plug.y(), plug.z() + 1);
		mutable.newInventory.addAllItems(STONE_ITEM, 2);
		mutable.isCreativeMode = true;
		mutable.setSelectedKey(mutable.newInventory.getIdOfStackableType(STONE_ITEM));
		Entity entity = mutable.freeze();
		runner.setupChangesForTick(List.of(new SuspendedCuboid<IReadOnlyCuboidData>(cascade, HeightMapHelpers.buildHeightMap(cascade), List.of(), List.of(), Map.of(), List.of()))
				, null
				, List.of(new SuspendedEntity(entity, List.of()))
				, null
		);
		runner.startNextTick();
		TickSnapshot snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(1, snapshot.cuboids().size());
		Assert.assertEquals(0, snapshot.cuboids().values().iterator().next().scheduledBlockMutations().size());
		
		// Now, break the plug.
		runner.enqueueEntityChange(entityId, _wrapSubAction(entity, new EntityChangeIncrementalBlockBreak(plug)), 1L);
		// Apply a tick for the entity mutation.
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		Assert.assertNull(snapshot.cuboids().get(address).blockChanges());
		Assert.assertEquals(1, snapshot.stats().committedEntityMutationCount());
		
		// Wait for this to trickle through the cuboid.
		// This will take 33 steps, with some ticks between to allow flow - found experimentally.
		long millisToFlow = ENV.liquids.flowDelayMillis(WATER_SOURCE);
		int ticksToPass = (int)(millisToFlow / MILLIS_PER_TICK);
		for (int i = 0; i < 33; ++i)
		{
			// Allow the break of update to happen.
			runner.startNextTick();
			snapshot = runner.waitForPreviousTick();
			
			// Wait for the movement.
			for (int j = 0; j < ticksToPass; ++j)
			{
				runner.startNextTick();
				snapshot = runner.waitForPreviousTick();
				Assert.assertNull(snapshot.cuboids().get(address).blockChanges());
			}
			
			// We should now see the flow.
			runner.startNextTick();
			snapshot = runner.waitForPreviousTick();
			Assert.assertFalse(snapshot.cuboids().get(address).blockChanges().isEmpty());
		}
		
		// We should now be done.
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		Assert.assertNull(snapshot.cuboids().get(address).blockChanges());
		
		runner.shutdown();
	}

	@Test
	public void waterCascade8()
	{
		// Create 8 cascade cuboids in a cube, add a dirt block and water source at the top-centre of these, break the block and wait until the water completes flowing.
		WorldConfig config = new WorldConfig();
		TickRunner runner = _createTestRunnerWithConfig(config);
		runner.start();
		
		CuboidAddress startAddress = CuboidAddress.fromInt(-3, -4, -5);
		CuboidData topNorthEast = _buildCascade(startAddress);
		AbsoluteLocation plug = topNorthEast.getCuboidAddress().getBase().getRelative(0, 0, 30);
		AbsoluteLocation topOfFalls = plug.getRelative(0, 0, 1);
		topNorthEast.setData15(AspectRegistry.BLOCK, plug.getBlockAddress(), DIRT_ITEM.number());
		topNorthEast.setData15(AspectRegistry.BLOCK, topOfFalls.getBlockAddress(), WATER_SOURCE.item().number());
		
		int entityId = 1;
		MutableEntity mutable = MutableEntity.createForTest(entityId);
		mutable.newLocation = topOfFalls.toEntityLocation();
		mutable.newInventory.addAllItems(STONE_ITEM, 2);
		mutable.isCreativeMode = true;
		mutable.setSelectedKey(mutable.newInventory.getIdOfStackableType(STONE_ITEM));
		Entity entity = mutable.freeze();
		runner.setupChangesForTick(List.of(_packageCuboid(topNorthEast)
				, _packageCuboid(_buildCascade(startAddress.getRelative(0, 0, -1)))
				, _packageCuboid(_buildCascade(startAddress.getRelative(0, -1, 0)))
				, _packageCuboid(_buildCascade(startAddress.getRelative(0, -1, -1)))
				, _packageCuboid(_buildCascade(startAddress.getRelative(-1, 0, 0)))
				, _packageCuboid(_buildCascade(startAddress.getRelative(-1, 0, -1)))
				, _packageCuboid(_buildCascade(startAddress.getRelative(-1, -1, 0)))
				, _packageCuboid(_buildCascade(startAddress.getRelative(-1, -1, -1)))
		)
				, null
				, List.of(new SuspendedEntity(entity, List.of()))
				, null
		);
		runner.startNextTick();
		TickSnapshot snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(8, snapshot.cuboids().size());
		Assert.assertEquals(0, snapshot.cuboids().values().stream().filter((TickSnapshot.SnapshotCuboid cuboid) -> !cuboid.scheduledBlockMutations().isEmpty()).count());
		
		// Now, break the plug.
		runner.enqueueEntityChange(entityId, _wrapSubAction(entity, new EntityChangeIncrementalBlockBreak(plug)), 1L);
		// Apply a tick for the entity mutation.
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		Assert.assertTrue(snapshot.cuboids().values().stream().filter((TickSnapshot.SnapshotCuboid cuboid) -> (null != cuboid.blockChanges())).toList().isEmpty());
		Assert.assertEquals(1, snapshot.stats().committedEntityMutationCount());
		
		// Wait for this to trickle through the cuboid.
		// This will take 65 steps, with some ticks between to allow flow - found experimentally.
		long millisToFlow = ENV.liquids.flowDelayMillis(WATER_SOURCE);
		int ticksToPass = (int)(millisToFlow / MILLIS_PER_TICK);
		for (int i = 0; i < 65; ++i)
		{
			// Allow the break of update to happen.
			runner.startNextTick();
			snapshot = runner.waitForPreviousTick();
			
			// Wait for the movement.
			for (int j = 0; j < ticksToPass; ++j)
			{
				runner.startNextTick();
				snapshot = runner.waitForPreviousTick();
				Assert.assertTrue(snapshot.cuboids().values().stream().filter((TickSnapshot.SnapshotCuboid cuboid) -> (null != cuboid.blockChanges())).toList().isEmpty());
			}
			
			// We should now see the flow.
			runner.startNextTick();
			snapshot = runner.waitForPreviousTick();
			Assert.assertFalse(snapshot.cuboids().values().stream().filter((TickSnapshot.SnapshotCuboid cuboid) -> (null != cuboid.blockChanges())).toList().isEmpty());
		}
		
		// We should now be done.
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		Assert.assertTrue(snapshot.cuboids().values().stream().filter((TickSnapshot.SnapshotCuboid cuboid) -> (null != cuboid.blockChanges())).toList().isEmpty());
		
		runner.shutdown();
	}

	@Test
	public void simpleLighting()
	{
		// Just show a relatively simple lighting case - add a lantern and an opaque block and verify the lighting pattern across 3 cuboids (diagonally).
		CuboidAddress address = CuboidAddress.fromInt(7, 8, 9);
		CuboidAddress otherAddress0 = address.getRelative(-1, 0, 0);
		CuboidAddress otherAddress1 = address.getRelative(-1, -1, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		CuboidData otherCuboid0 = CuboidGenerator.createFilledCuboid(otherAddress0, ENV.special.AIR);
		CuboidData otherCuboid1 = CuboidGenerator.createFilledCuboid(otherAddress1, ENV.special.AIR);
		AbsoluteLocation lanternLocation = address.getBase().getRelative(5, 6, 7);
		AbsoluteLocation stoneLocation = address.getBase().getRelative(6, 6, 7);
		
		TickRunner runner = _createTestRunner();
		int entityId1 = 1;
		int entityId2 = 2;
		MutableEntity mutable1 = MutableEntity.createForTest(entityId1);
		mutable1.newLocation = lanternLocation.getRelative(1, 0, 0).toEntityLocation();
		MutableEntity mutable2 = MutableEntity.createForTest(entityId2);
		mutable2.newLocation = stoneLocation.getRelative(1, 0, 0).toEntityLocation();
		SuspendedEntity entity1 = new SuspendedEntity(mutable1.freeze(), List.of());
		SuspendedEntity entity2 = new SuspendedEntity(mutable2.freeze(), List.of());
		runner.setupChangesForTick(List.of(_packageCuboid(cuboid)
					, _packageCuboid(otherCuboid0)
					, _packageCuboid(otherCuboid1)
				)
				, null
				, List.of(entity1, entity2)
				, null
		);
		runner.start();
		runner.waitForPreviousTick();
		// Enqueue the mutations to replace these 2 blocks (this mutation is just for testing and doesn't use the inventory or location).
		// The mutation will be run in the next tick since there isn't one running.
		runner.enqueueEntityChange(entityId1, _wrapForEntity(entity1.entity(), new ReplaceBlockMutation(lanternLocation, ENV.special.AIR.item().number(), LANTERN_ITEM.number())), 1L);
		runner.enqueueEntityChange(entityId2, _wrapForEntity(entity2.entity(), new ReplaceBlockMutation(stoneLocation, ENV.special.AIR.item().number(), STONE_ITEM.number())), 1L);
		runner.startNextTick();
		
		// (run an extra tick to unwrap the entity change)
		TickSnapshot snapshot = runner.startNextTick();
		Assert.assertEquals(2, snapshot.stats().committedEntityMutationCount());
		
		snapshot = runner.waitForPreviousTick();
		// Here, we should see the block types changed but not yet the light.
		Assert.assertEquals(2, snapshot.stats().committedCuboidMutationCount());
		Assert.assertEquals(2, snapshot.cuboids().get(address).blockChanges().size());
		Assert.assertEquals(LANTERN_ITEM.number(), snapshot.cuboids().get(address).completed().getData15(AspectRegistry.BLOCK, lanternLocation.getBlockAddress()));
		Assert.assertEquals(STONE_ITEM.number(), snapshot.cuboids().get(address).completed().getData15(AspectRegistry.BLOCK, stoneLocation.getBlockAddress()));
		Assert.assertEquals(0, snapshot.cuboids().get(address).completed().getData7(AspectRegistry.LIGHT, lanternLocation.getBlockAddress()));
		
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		// Here, we should see the light changes.
		Assert.assertEquals(3028, snapshot.cuboids().get(address).blockChanges().size());
		Assert.assertEquals(483, snapshot.cuboids().get(otherAddress0).blockChanges().size());
		Assert.assertEquals(5, snapshot.cuboids().get(otherAddress1).blockChanges().size());
		Assert.assertEquals(LANTERN_ITEM.number(), snapshot.cuboids().get(address).completed().getData15(AspectRegistry.BLOCK, lanternLocation.getBlockAddress()));
		Assert.assertEquals(LightAspect.MAX_LIGHT, snapshot.cuboids().get(address).completed().getData7(AspectRegistry.LIGHT, lanternLocation.getBlockAddress()));
		Assert.assertEquals(LightAspect.MAX_LIGHT - 1, snapshot.cuboids().get(address).completed().getData7(AspectRegistry.LIGHT, lanternLocation.getRelative(0, 1, 0).getBlockAddress()));
		Assert.assertEquals(STONE_ITEM.number(), snapshot.cuboids().get(address).completed().getData15(AspectRegistry.BLOCK, stoneLocation.getBlockAddress()));
		Assert.assertEquals(0, snapshot.cuboids().get(address).completed().getData7(AspectRegistry.LIGHT, stoneLocation.getBlockAddress()));
		Assert.assertEquals(11, snapshot.cuboids().get(address).completed().getData7(AspectRegistry.LIGHT, stoneLocation.getRelative(1, 0, 0).getBlockAddress()));
		Assert.assertEquals(9, snapshot.cuboids().get(otherAddress0).completed().getData7(AspectRegistry.LIGHT, BlockAddress.fromInt(31, 6, 7)));
		Assert.assertEquals(2, snapshot.cuboids().get(otherAddress1).completed().getData7(AspectRegistry.LIGHT, BlockAddress.fromInt(31, 31, 7)));
		
		runner.shutdown();
	}

	@Test
	public void treeGrowth()
	{
		// We just want to see what happens when we plant a sapling.
		CuboidAddress address = CuboidAddress.fromInt(7, 8, 9);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		AbsoluteLocation location = address.getBase().getRelative(0, 6, 7);
		cuboid.setData15(AspectRegistry.BLOCK, location.getRelative(0, 0, -1).getBlockAddress(), DIRT_ITEM.number());
		cuboid.setData15(AspectRegistry.BLOCK, location.getRelative(1, 0, -1).getBlockAddress(), DIRT_ITEM.number());
		
		int[] randomHolder = new int[] {0};
		TickRunner runner = new TickRunner(ServerRunner.TICK_RUNNER_THREAD_COUNT
				, MILLIS_PER_TICK
				, null
				, null
				, (int bound) -> randomHolder[0] % bound
				, (TickSnapshot completed) -> {}
				, new WorldConfig()
		);
		int entityId = 1;
		MutableEntity mutable = MutableEntity.createForTest(entityId);
		mutable.newLocation = new EntityLocation(location.x() + 1, location.y(), location.z());
		mutable.newInventory.addAllItems(SAPLING_ITEM, 1);
		mutable.setSelectedKey(mutable.newInventory.getIdOfStackableType(SAPLING_ITEM));
		Entity entity = mutable.freeze();
		runner.setupChangesForTick(List.of(new SuspendedCuboid<IReadOnlyCuboidData>(cuboid, HeightMapHelpers.buildHeightMap(cuboid), List.of(), List.of(), Map.of(), List.of())
				)
				, null
				, List.of(new SuspendedEntity(entity, List.of()))
				, null
		);
		runner.start();
		runner.waitForPreviousTick();
		runner.enqueueEntityChange(entityId, _wrapSubAction(entity, new MutationPlaceSelectedBlock(location, location)), 1L);
		runner.startNextTick();
		
		// (run an extra tick to unwrap the entity change)
		TickSnapshot snapshot = runner.startNextTick();
		Assert.assertEquals(1, snapshot.stats().committedEntityMutationCount());
		
		snapshot = runner.waitForPreviousTick();
		// We should see the sapling for one tick before it grows.
		Assert.assertEquals(1, snapshot.stats().committedCuboidMutationCount());
		Assert.assertEquals(SAPLING_ITEM.number(), snapshot.cuboids().get(address).completed().getData15(AspectRegistry.BLOCK, location.getBlockAddress()));
		
		// The last call will have enqueued a growth tick so we want to skip ahead 500 ticks to see the growth.
		// Then, there will be 1 growth attempt, but we set the random provider to 0 so it will fail.  Then we will see another 100 ticks pass.
		randomHolder[0] = 0;
		int ticksBetweenGrowthCalls = (int)(MutationBlockPeriodic.MILLIS_BETWEEN_GROWTH_CALLS / MILLIS_PER_TICK);
		for (int i = 0; i < (2 * ticksBetweenGrowthCalls + 1); ++i)
		{
			runner.startNextTick();
			snapshot = runner.waitForPreviousTick();
		}
		Assert.assertEquals(SAPLING_ITEM.number(), snapshot.cuboids().get(address).completed().getData15(AspectRegistry.BLOCK, location.getBlockAddress()));
		
		// This time, set the provider to 1 so that it matches (it takes the number mod some constant and compares it to 1).
		randomHolder[0] = 1;
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		// Here, we should see the sapling replaced with a log but the leaves are only placed next tick.
		Assert.assertEquals(1, snapshot.cuboids().get(address).blockChanges().size());
		Assert.assertEquals(LOG_ITEM.number(), snapshot.cuboids().get(address).completed().getData15(AspectRegistry.BLOCK, location.getBlockAddress()));
		
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		// Now, we should see the leaf blocks which could be placed in the cuboid.
		Assert.assertEquals(4, snapshot.cuboids().get(address).blockChanges().size());
		Assert.assertEquals(LOG_ITEM.number(), snapshot.cuboids().get(address).completed().getData15(AspectRegistry.BLOCK, location.getRelative(0, 0, 1).getBlockAddress()));
		Assert.assertEquals(LEAF_ITEM.number(), snapshot.cuboids().get(address).completed().getData15(AspectRegistry.BLOCK, location.getRelative(1, 0, 1).getBlockAddress()));
		
		runner.shutdown();
	}

	@Test
	public void wheatGrowth()
	{
		// Plant a seed and watch it grow.
		int ticksBetweenGrowthCalls = (int)(MutationBlockPeriodic.MILLIS_BETWEEN_GROWTH_CALLS / MILLIS_PER_TICK);
		CuboidAddress address = CuboidAddress.fromInt(7, 8, 9);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		AbsoluteLocation location = address.getBase().getRelative(0, 6, 7);
		cuboid.setData15(AspectRegistry.BLOCK, location.getRelative(0, 0, -1).getBlockAddress(), TILLED_SOIL_ITEM.number());
		
		int[] randomHolder = new int[] {0};
		WorldConfig config = new WorldConfig();
		config.difficulty = Difficulty.PEACEFUL;
		TickRunner runner = new TickRunner(ServerRunner.TICK_RUNNER_THREAD_COUNT
				, MILLIS_PER_TICK
				, null
				, null
				, (int bound) -> randomHolder[0] % bound
				, (TickSnapshot completed) -> {}
				, config
		);
		int entityId = 1;
		MutableEntity mutable = MutableEntity.createForTest(entityId);
		mutable.newLocation = location.toEntityLocation();
		mutable.newInventory.addAllItems(WHEAT_SEED_ITEM, 1);
		mutable.setSelectedKey(mutable.newInventory.getIdOfStackableType(WHEAT_SEED_ITEM));
		Entity entity = mutable.freeze();
		runner.setupChangesForTick(List.of(new SuspendedCuboid<IReadOnlyCuboidData>(cuboid, HeightMapHelpers.buildHeightMap(cuboid), List.of(), List.of(), Map.of(), List.of())
				)
				, null
				, List.of(new SuspendedEntity(entity, List.of()))
				, null
		);
		runner.start();
		runner.waitForPreviousTick();
		runner.enqueueEntityChange(entityId, _wrapSubAction(entity, new MutationPlaceSelectedBlock(location, location)), 1L);
		runner.startNextTick();
		
		// (run an extra tick to unwrap the entity change)
		TickSnapshot snapshot = runner.startNextTick();
		Assert.assertEquals(1, snapshot.stats().committedEntityMutationCount());
		
		snapshot = runner.waitForPreviousTick();
		// We should see the seed for one tick before it grows.
		Assert.assertEquals(1, snapshot.stats().committedCuboidMutationCount());
		Assert.assertEquals(WHEAT_SEEDLING_ITEM.number(), snapshot.cuboids().get(address).completed().getData15(AspectRegistry.BLOCK, location.getBlockAddress()));
		
		// The last call will have enqueued a growth tick so we want to skip ahead 500 ticks to see the growth.
		// We will just set the random number to 1 to easily watch it go through all phases.
		randomHolder[0] = 1;
		for (int i = 0; i < ticksBetweenGrowthCalls; ++i)
		{
			runner.startNextTick();
			snapshot = runner.waitForPreviousTick();
		}
		Assert.assertEquals(WHEAT_SEEDLING_ITEM.number(), snapshot.cuboids().get(address).completed().getData15(AspectRegistry.BLOCK, location.getBlockAddress()));
		
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		// Here, we should see the sapling replaced with a log but the leaves are only placed next tick.
		Assert.assertEquals(1, snapshot.cuboids().get(address).blockChanges().size());
		Assert.assertEquals(WHEAT_YOUNG_ITEM.number(), snapshot.cuboids().get(address).completed().getData15(AspectRegistry.BLOCK, location.getBlockAddress()));
		
		// Wait another 500 ticks to see the next growth.
		for (int i = 0; i < ticksBetweenGrowthCalls; ++i)
		{
			runner.startNextTick();
			snapshot = runner.waitForPreviousTick();
		}
		Assert.assertEquals(WHEAT_YOUNG_ITEM.number(), snapshot.cuboids().get(address).completed().getData15(AspectRegistry.BLOCK, location.getBlockAddress()));
		
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		// Here, we should see the sapling replaced with a log but the leaves are only placed next tick.
		Assert.assertEquals(1, snapshot.cuboids().get(address).blockChanges().size());
		Assert.assertEquals(WHEAT_MATURE_ITEM.number(), snapshot.cuboids().get(address).completed().getData15(AspectRegistry.BLOCK, location.getBlockAddress()));
		
		// Break the mature crop and check the inventory dropped.
		_applyIncrementalBreaks(runner, 1L, entityId, entity, location, ENV.damage.getToughness(ENV.blocks.fromItem(WHEAT_MATURE_ITEM)));
		
		// Finish the tick for the unwrap, another to break the block, then a third to save to the entity, before checking the inventories.
		snapshot = runner.waitForPreviousTick();
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(1, snapshot.cuboids().get(address).blockChanges().size());
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(ENV.special.AIR.item().number(), snapshot.cuboids().get(address).completed().getData15(AspectRegistry.BLOCK, location.getRelative(0, 0, 1).getBlockAddress()));
		
		// We should see these items in the entity inventory, not the ground.
		Inventory blockInventory = snapshot.cuboids().get(address).completed().getDataSpecial(AspectRegistry.INVENTORY, location.getBlockAddress());
		Assert.assertNull(blockInventory);
		Inventory entityInventory = snapshot.entities().get(entityId).completed().inventory();
		Assert.assertEquals(2, entityInventory.sortedKeys().size());
		Assert.assertEquals(2, entityInventory.getCount(WHEAT_SEED_ITEM));
		Assert.assertEquals(2, entityInventory.getCount(WHEAT_ITEM_ITEM));
		
		runner.shutdown();
	}

	@Test
	public void liquidConflict()
	{
		// Place a water and lava source near each other on a platform and observe what happens after they finish flowing.
		Block waterSource = ENV.blocks.fromItem(ENV.items.getItemById("op.water_source"));
		Block lavaSource = ENV.blocks.fromItem(ENV.items.getItemById("op.lava_source"));
		WorldConfig config = new WorldConfig();
		TickRunner runner = _createTestRunnerWithConfig(config);
		runner.start();
		
		CuboidData platform = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(-3, -4, -5), STONE);
		CuboidData openSpace = CuboidGenerator.createFilledCuboid(platform.getCuboidAddress().getRelative(0, 0, 1), ENV.special.AIR);
		
		AbsoluteLocation centre = openSpace.getCuboidAddress().getBase().getRelative(15, 15, 0);
		AbsoluteLocation waterPlace = centre.getRelative(1, 1, 0);
		AbsoluteLocation lavaPlace = centre.getRelative(-1, -1, 0);
		MutationBlockReplace placeWater = new MutationBlockReplace(waterPlace, ENV.special.AIR, waterSource);
		MutationBlockReplace placeLava = new MutationBlockReplace(lavaPlace, ENV.special.AIR, lavaSource);
		runner.setupChangesForTick(List.of(
					new SuspendedCuboid<IReadOnlyCuboidData>(platform, HeightMapHelpers.buildHeightMap(platform), List.of(), List.of(), Map.of(), List.of())
					, new SuspendedCuboid<IReadOnlyCuboidData>(openSpace, HeightMapHelpers.buildHeightMap(openSpace), List.of(), List.of(
							new ScheduledMutation(placeWater, 0L)
							, new ScheduledMutation(placeLava, 0L)
					), Map.of(), List.of())
				)
				, null
				, null
				, null
		);
		runner.startNextTick();
		TickSnapshot snapshot = runner.waitForPreviousTick();
		
		// Wait for lava to flow twice.
		long millisToFlow = ENV.liquids.flowDelayMillis(lavaSource);
		int ticksToPass = (int)(2 * millisToFlow / MILLIS_PER_TICK) + 4;
		for (int j = 0; j < ticksToPass; ++j)
		{
			runner.startNextTick();
			snapshot = runner.waitForPreviousTick();
		}
		
		// Assess the blocks in this cuboid.
		IReadOnlyCuboidData topCuboid = snapshot.cuboids().get(openSpace.getCuboidAddress()).completed();
		// Make sure that the sources were applied.
		Assert.assertEquals(waterSource.item().number(), topCuboid.getData15(AspectRegistry.BLOCK, waterPlace.getBlockAddress()));
		Assert.assertEquals(lavaSource.item().number(), topCuboid.getData15(AspectRegistry.BLOCK, lavaPlace.getBlockAddress()));
		// Check a walk through the space, based on what we experimentally verified to see (since the lava arrives later, it doesn't flow).
		Assert.assertEquals(ENV.items.getItemById("op.lava_weak").number(), topCuboid.getData15(AspectRegistry.BLOCK, centre.getRelative(-2, 0, 0).getBlockAddress()));
		Assert.assertEquals(ENV.items.getItemById("op.basalt").number(), topCuboid.getData15(AspectRegistry.BLOCK, centre.getRelative(-1, 0, 0).getBlockAddress()));
		Assert.assertEquals(ENV.items.getItemById("op.water_weak").number(), topCuboid.getData15(AspectRegistry.BLOCK, centre.getRelative(0, 0, 0).getBlockAddress()));
		Assert.assertEquals(ENV.items.getItemById("op.water_strong").number(), topCuboid.getData15(AspectRegistry.BLOCK, centre.getRelative(1, 0, 0).getBlockAddress()));
		Assert.assertEquals(ENV.items.getItemById("op.water_weak").number(), topCuboid.getData15(AspectRegistry.BLOCK, centre.getRelative(2, 0, 0).getBlockAddress()));
		
		// We also want to verify that the lava is providing light.
		Assert.assertEquals(3, topCuboid.getData7(AspectRegistry.LIGHT, centre.getBlockAddress()));
		Assert.assertEquals(7, topCuboid.getData7(AspectRegistry.LIGHT, lavaPlace.getRelative(0, 0, 1).getBlockAddress()));
		
		runner.shutdown();
	}

	@Test
	public void liquidStacking()
	{
		// We will show what happens when a single source of one liquid is placed over a pool of another.
		Block waterSource = ENV.blocks.fromItem(ENV.items.getItemById("op.water_source"));
		Block waterStrong = ENV.blocks.fromItem(ENV.items.getItemById("op.water_strong"));
		Block waterWeak = ENV.blocks.fromItem(ENV.items.getItemById("op.water_weak"));
		Block lavaSource = ENV.blocks.fromItem(ENV.items.getItemById("op.lava_source"));
		Block lavaStrong = ENV.blocks.fromItem(ENV.items.getItemById("op.lava_strong"));
		Block lavaWeak = ENV.blocks.fromItem(ENV.items.getItemById("op.lava_weak"));
		WorldConfig config = new WorldConfig();
		TickRunner runner = _createTestRunnerWithConfig(config);
		runner.start();
		
		// We want to build the pool with different liquid flow rates so we can observe how a source falling onto them impacts them.
		CuboidData waterPool = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(-1, -1, 0), STONE);
		CuboidData lavaBlob = CuboidGenerator.createFilledCuboid(waterPool.getCuboidAddress().getRelative(0, 0, 1), ENV.special.AIR);
		CuboidData lavaPool = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(1, 1, 0), STONE);
		CuboidData waterBlob = CuboidGenerator.createFilledCuboid(lavaPool.getCuboidAddress().getRelative(0, 0, 1), ENV.special.AIR);
		
		// Determine where we want to place the sources above here and then build out the pools below them.
		AbsoluteLocation waterPlace = waterBlob.getCuboidAddress().getBase().getRelative(15, 15, 0);
		AbsoluteLocation lavaPlace = lavaBlob.getCuboidAddress().getBase().getRelative(15, 15, 0);
		waterPool.setData15(AspectRegistry.BLOCK, lavaPlace.getRelative(0, 0, -1).getBlockAddress(), waterSource.item().number());
		waterPool.setData15(AspectRegistry.BLOCK, lavaPlace.getRelative(0, 1, -1).getBlockAddress(), waterSource.item().number());
		waterPool.setData15(AspectRegistry.BLOCK, lavaPlace.getRelative(0, 2, -1).getBlockAddress(), waterSource.item().number());
		waterPool.setData15(AspectRegistry.BLOCK, lavaPlace.getRelative(1, 0, -1).getBlockAddress(), waterStrong.item().number());
		waterPool.setData15(AspectRegistry.BLOCK, lavaPlace.getRelative(1, 1, -1).getBlockAddress(), waterStrong.item().number());
		waterPool.setData15(AspectRegistry.BLOCK, lavaPlace.getRelative(1, 2, -1).getBlockAddress(), waterStrong.item().number());
		waterPool.setData15(AspectRegistry.BLOCK, lavaPlace.getRelative(2, 0, -1).getBlockAddress(), waterWeak.item().number());
		waterPool.setData15(AspectRegistry.BLOCK, lavaPlace.getRelative(2, 1, -1).getBlockAddress(), waterWeak.item().number());
		waterPool.setData15(AspectRegistry.BLOCK, lavaPlace.getRelative(2, 2, -1).getBlockAddress(), waterWeak.item().number());
		
		lavaPool.setData15(AspectRegistry.BLOCK, waterPlace.getRelative(0, 0, -1).getBlockAddress(), lavaSource.item().number());
		lavaPool.setData15(AspectRegistry.BLOCK, waterPlace.getRelative(0, 1, -1).getBlockAddress(), lavaSource.item().number());
		lavaPool.setData15(AspectRegistry.BLOCK, waterPlace.getRelative(0, 2, -1).getBlockAddress(), lavaSource.item().number());
		lavaPool.setData15(AspectRegistry.BLOCK, waterPlace.getRelative(1, 0, -1).getBlockAddress(), lavaStrong.item().number());
		lavaPool.setData15(AspectRegistry.BLOCK, waterPlace.getRelative(1, 1, -1).getBlockAddress(), lavaStrong.item().number());
		lavaPool.setData15(AspectRegistry.BLOCK, waterPlace.getRelative(1, 2, -1).getBlockAddress(), lavaStrong.item().number());
		lavaPool.setData15(AspectRegistry.BLOCK, waterPlace.getRelative(2, 0, -1).getBlockAddress(), lavaWeak.item().number());
		lavaPool.setData15(AspectRegistry.BLOCK, waterPlace.getRelative(2, 1, -1).getBlockAddress(), lavaWeak.item().number());
		lavaPool.setData15(AspectRegistry.BLOCK, waterPlace.getRelative(2, 2, -1).getBlockAddress(), lavaWeak.item().number());
		
		MutationBlockReplace placeWater = new MutationBlockReplace(waterPlace, ENV.special.AIR, waterSource);
		MutationBlockReplace placeLava = new MutationBlockReplace(lavaPlace, ENV.special.AIR, lavaSource);
		runner.setupChangesForTick(List.of(
					new SuspendedCuboid<IReadOnlyCuboidData>(waterPool, HeightMapHelpers.buildHeightMap(waterPool), List.of(), List.of(), Map.of(), List.of())
					, new SuspendedCuboid<IReadOnlyCuboidData>(lavaBlob, HeightMapHelpers.buildHeightMap(lavaBlob), List.of(), List.of(
							new ScheduledMutation(placeLava, 0L)
					), Map.of(), List.of())
					, new SuspendedCuboid<IReadOnlyCuboidData>(lavaPool, HeightMapHelpers.buildHeightMap(lavaPool), List.of(), List.of(), Map.of(), List.of())
					, new SuspendedCuboid<IReadOnlyCuboidData>(waterBlob, HeightMapHelpers.buildHeightMap(waterBlob), List.of(), List.of(
							new ScheduledMutation(placeWater, 0L)
					), Map.of(), List.of())
				)
				, null
				, null
				, null
		);
		runner.startNextTick();
		TickSnapshot snapshot = runner.waitForPreviousTick();
		
		// Wait for lava to flow twice, then another iteration for solidification, another 2 to let liquid fade, and a few more for final lava flow.
		long millisToFlow = ENV.liquids.flowDelayMillis(lavaSource);
		int ticksForOneLavaFlow = (int)(millisToFlow / MILLIS_PER_TICK) + 2;
		int ticksToPass = 8 * ticksForOneLavaFlow;
		for (int j = 0; j < ticksToPass; ++j)
		{
			runner.startNextTick();
			snapshot = runner.waitForPreviousTick();
		}
		
		// Check the cuboids.
		IReadOnlyCuboidData waterPoolCuboid = snapshot.cuboids().get(waterPool.getCuboidAddress()).completed();
		IReadOnlyCuboidData lavaPoolCuboid = snapshot.cuboids().get(lavaPool.getCuboidAddress()).completed();
		IReadOnlyCuboidData waterBlobCuboid = snapshot.cuboids().get(waterBlob.getCuboidAddress()).completed();
		IReadOnlyCuboidData lavaBlobCuboid = snapshot.cuboids().get(lavaBlob.getCuboidAddress()).completed();
		
		// Make sure that the sources were applied.
		Assert.assertEquals(waterSource.item().number(), waterBlobCuboid.getData15(AspectRegistry.BLOCK, waterPlace.getBlockAddress()));
		Assert.assertEquals(lavaSource.item().number(), lavaBlobCuboid.getData15(AspectRegistry.BLOCK, lavaPlace.getBlockAddress()));
		
		// Check that these have flowed "out" from the sources.
		Assert.assertEquals(ENV.items.getItemById("op.water_strong").number(), waterBlobCuboid.getData15(AspectRegistry.BLOCK, waterPlace.getRelative(1, 0, 0).getBlockAddress()));
		Assert.assertEquals(ENV.items.getItemById("op.water_weak").number(), waterBlobCuboid.getData15(AspectRegistry.BLOCK, waterPlace.getRelative(2, 0, 0).getBlockAddress()));
		Assert.assertEquals(ENV.items.getItemById("op.lava_strong").number(), lavaBlobCuboid.getData15(AspectRegistry.BLOCK, lavaPlace.getRelative(1, 0, 0).getBlockAddress()));
		Assert.assertEquals(ENV.items.getItemById("op.lava_weak").number(), lavaBlobCuboid.getData15(AspectRegistry.BLOCK, lavaPlace.getRelative(2, 0, 0).getBlockAddress()));
		
		// Check what appears under these sources, where the old sources were.
		// We expect all the water blocks to turn to stone, all the lava blocks to turn to basalt, and anything not covered to be air or delayed lava flow.
		// The lava ends up flowing down in cases where the water doesn't since the water takes longer to flow so the water is already gone.
		// NOTE:  Sources are currently unchanged by liquid flows, while other conflicts turn to air, but this will change.
		short stoneNumber = ENV.items.getItemById("op.stone").number();
		short basaltNumber = ENV.items.getItemById("op.basalt").number();
		short airNumber = ENV.special.AIR.item().number();
		short lavaStrongNumber = lavaStrong.item().number();
		short lavaWeakNumber = lavaWeak.item().number();
		
		Assert.assertEquals(stoneNumber, waterPoolCuboid.getData15(AspectRegistry.BLOCK, lavaPlace.getRelative(0, 0, -1).getBlockAddress()));
		Assert.assertEquals(stoneNumber, waterPoolCuboid.getData15(AspectRegistry.BLOCK, lavaPlace.getRelative(0, 1, -1).getBlockAddress()));
		Assert.assertEquals(stoneNumber, waterPoolCuboid.getData15(AspectRegistry.BLOCK, lavaPlace.getRelative(0, 2, -1).getBlockAddress()));
		Assert.assertEquals(stoneNumber, waterPoolCuboid.getData15(AspectRegistry.BLOCK, lavaPlace.getRelative(1, 0, -1).getBlockAddress()));
		Assert.assertEquals(stoneNumber, waterPoolCuboid.getData15(AspectRegistry.BLOCK, lavaPlace.getRelative(1, 1, -1).getBlockAddress()));
		Assert.assertEquals(airNumber, waterPoolCuboid.getData15(AspectRegistry.BLOCK, lavaPlace.getRelative(1, 2, -1).getBlockAddress()));
		Assert.assertEquals(lavaStrongNumber, waterPoolCuboid.getData15(AspectRegistry.BLOCK, lavaPlace.getRelative(2, 0, -1).getBlockAddress()));
		Assert.assertEquals(lavaWeakNumber, waterPoolCuboid.getData15(AspectRegistry.BLOCK, lavaPlace.getRelative(2, 1, -1).getBlockAddress()));
		Assert.assertEquals(airNumber, waterPoolCuboid.getData15(AspectRegistry.BLOCK, lavaPlace.getRelative(2, 2, -1).getBlockAddress()));
		Assert.assertEquals(basaltNumber, lavaPoolCuboid.getData15(AspectRegistry.BLOCK, waterPlace.getRelative(0, 0, -1).getBlockAddress()));
		Assert.assertEquals(basaltNumber, lavaPoolCuboid.getData15(AspectRegistry.BLOCK, waterPlace.getRelative(0, 1, -1).getBlockAddress()));
		Assert.assertEquals(basaltNumber, lavaPoolCuboid.getData15(AspectRegistry.BLOCK, waterPlace.getRelative(0, 2, -1).getBlockAddress()));
		Assert.assertEquals(basaltNumber, lavaPoolCuboid.getData15(AspectRegistry.BLOCK, waterPlace.getRelative(1, 0, -1).getBlockAddress()));
		Assert.assertEquals(basaltNumber, lavaPoolCuboid.getData15(AspectRegistry.BLOCK, waterPlace.getRelative(1, 1, -1).getBlockAddress()));
		Assert.assertEquals(airNumber, lavaPoolCuboid.getData15(AspectRegistry.BLOCK, waterPlace.getRelative(1, 2, -1).getBlockAddress()));
		Assert.assertEquals(basaltNumber, lavaPoolCuboid.getData15(AspectRegistry.BLOCK, waterPlace.getRelative(2, 0, -1).getBlockAddress()));
		Assert.assertEquals(airNumber, lavaPoolCuboid.getData15(AspectRegistry.BLOCK, waterPlace.getRelative(2, 1, -1).getBlockAddress()));
		Assert.assertEquals(airNumber, lavaPoolCuboid.getData15(AspectRegistry.BLOCK, waterPlace.getRelative(2, 2, -1).getBlockAddress()));
		
		runner.shutdown();
	}

	@Test
	public void logicGates()
	{
		// We will assume that the logic gates handle the direct/indirect signals correctly (as diode test already checks this) so we just check that the AND, OR, and NOT work as expected.
		Item itemAnd = ENV.items.getItemById("op.and_gate");
		Item itemOr = ENV.items.getItemById("op.or_gate");
		Item itemNot = ENV.items.getItemById("op.not_gate");
		Item itemGate = ENV.items.getItemById("op.gate");
		Item itemSwitch = ENV.items.getItemById("op.switch");
		CuboidData cuboid = _zeroAirCuboidWithBase();
		
		// We will create an area for each gate, then place the gates and flip the switches to observe the logic changes.
		// AND
		AbsoluteLocation andGate = cuboid.getCuboidAddress().getBase().getRelative(2, 2, 1);
		_placeItemAsBlock(cuboid, andGate.getRelative(1, 0, 0), itemGate, null, false);
		_placeItemAsBlock(cuboid, andGate.getRelative(0, 1, 0), itemSwitch, null, false);
		_placeItemAsBlock(cuboid, andGate.getRelative(0, -1, 0), itemSwitch, null, false);
		// OR
		AbsoluteLocation orGate = cuboid.getCuboidAddress().getBase().getRelative(2, 12, 1);
		_placeItemAsBlock(cuboid, orGate.getRelative(1, 0, 0), itemGate, null, false);
		_placeItemAsBlock(cuboid, orGate.getRelative(0, 1, 0), itemSwitch, null, false);
		_placeItemAsBlock(cuboid, orGate.getRelative(0, -1, 0), itemSwitch, null, false);
		// NOT
		AbsoluteLocation notGate = cuboid.getCuboidAddress().getBase().getRelative(2, 22, 1);
		_placeItemAsBlock(cuboid, notGate.getRelative(1, 0, 0), itemGate, null, false);
		_placeItemAsBlock(cuboid, notGate.getRelative(-1, 0, 0), itemSwitch, null, false);
		
		// Since these are spaced out and we want this to happen in fewer ticks, we will use 3 entities.
		MutableEntity mutable1 = MutableEntity.createForTest(1);
		mutable1.newLocation = andGate.getRelative(1, 1, 0).toEntityLocation();
		mutable1.newInventory.addAllItems(itemAnd, 2);
		mutable1.setSelectedKey(1);
		Entity entity1 = mutable1.freeze();
		MutableEntity mutable2 = MutableEntity.createForTest(2);
		mutable2.newLocation = orGate.getRelative(1, 1, 0).toEntityLocation();
		mutable2.newInventory.addAllItems(itemOr, 2);
		mutable2.setSelectedKey(1);
		Entity entity2 = mutable2.freeze();
		MutableEntity mutable3 = MutableEntity.createForTest(3);
		mutable3.newLocation = notGate.getRelative(1, 1, 0).toEntityLocation();
		mutable3.newInventory.addAllItems(itemNot, 2);
		mutable3.setSelectedKey(1);
		Entity entity3 = mutable3.freeze();
		
		// Create the runner and load all test data.
		TickRunner runner = _createTestRunner();
		runner.setupChangesForTick(List.of(new SuspendedCuboid<IReadOnlyCuboidData>(cuboid, HeightMapHelpers.buildHeightMap(cuboid), List.of(), List.of(), Map.of(), List.of()))
				, null
				, List.of(new SuspendedEntity(entity1, List.of())
						, new SuspendedEntity(entity2, List.of())
						, new SuspendedEntity(entity3, List.of())
				)
				, null
		);
		runner.start();
		runner.waitForPreviousTick();
		
		// We will run these changes in 3 batches:  (1) place gates, (2) flip one switch, (3) flip second switch.
		// Run phase1.
		runner.enqueueEntityChange(1, _wrapSubAction(entity1, new MutationPlaceSelectedBlock(andGate, andGate.getRelative(1, 0, 0))), 1L);
		runner.enqueueEntityChange(2, _wrapSubAction(entity2, new MutationPlaceSelectedBlock(orGate, orGate.getRelative(1, 0, 0))), 1L);
		runner.enqueueEntityChange(3, _wrapSubAction(entity3, new MutationPlaceSelectedBlock(notGate, notGate.getRelative(1, 0, 0))), 1L);
		
		// Now, run enough ticks that this first batch is complete (this is how many ticks it takes for the NOT to propagate).
		// 1) Run MutationPlaceSelectedBlock.
		runner.startNextTick();
		runner.waitForPreviousTick();
		// 2) Run MutationBlockOverwriteByEntity.
		runner.startNextTick();
		runner.waitForPreviousTick();
		// 3) Run logic update.
		runner.startNextTick();
		runner.waitForPreviousTick();
		// 4) Run MutationBlockLogicChange.
		runner.startNextTick();
		runner.waitForPreviousTick();
		// 5) Run MutationBlockInternalSetLogicState.
		runner.startNextTick();
		TickSnapshot snapshot = runner.waitForPreviousTick();
		IReadOnlyCuboidData phase1 = snapshot.cuboids().get(cuboid.getCuboidAddress()).completed();
		
		// Check the gate and door states.
		_checkBlock(phase1, andGate, itemAnd, FacingDirection.EAST, false);
		_checkBlock(phase1, andGate.getRelative(1, 0, 0), itemGate, null, false);
		_checkBlock(phase1, orGate, itemOr, FacingDirection.EAST, false);
		_checkBlock(phase1, orGate.getRelative(1, 0, 0), itemGate, null, false);
		_checkBlock(phase1, notGate, itemNot, FacingDirection.EAST, true);
		_checkBlock(phase1, notGate.getRelative(1, 0, 0), itemGate, null, true);
		
		// Run phase2 - we flip the switches and should see OR and NOT change.
		runner.enqueueEntityChange(1, _wrapSubAction(entity1, new EntityChangeSetBlockLogicState(andGate.getRelative(0, -1, 0), true)), 2L);
		runner.enqueueEntityChange(2, _wrapSubAction(entity2, new EntityChangeSetBlockLogicState(orGate.getRelative(0, -1, 0), true)), 2L);
		runner.enqueueEntityChange(3, _wrapSubAction(entity3, new EntityChangeSetBlockLogicState(notGate.getRelative(-1, 0, 0), true)), 2L);
		
		// Run enough ticks to observe the output from the or gate.
		// 1) EntityChangeSetBlockLogicState
		runner.startNextTick();
		runner.waitForPreviousTick();
		// 2) MutationBlockSetLogicState
		runner.startNextTick();
		runner.waitForPreviousTick();
		// 3) Logic propagate
		runner.startNextTick();
		runner.waitForPreviousTick();
		// 4) Run MutationBlockLogicChange.
		runner.startNextTick();
		runner.waitForPreviousTick();
		// 5) Run MutationBlockInternalSetLogicState.
		runner.startNextTick();
		runner.waitForPreviousTick();
		// 6) Logic propagate
		runner.startNextTick();
		runner.waitForPreviousTick();
		// 7) Run MutationBlockLogicChange.
		runner.startNextTick();
		runner.waitForPreviousTick();
		// 8) Run MutationBlockInternalSetLogicState.
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		IReadOnlyCuboidData phase2 = snapshot.cuboids().get(cuboid.getCuboidAddress()).completed();
		
		// Check that the OR gate and door changed, same with NOT, but not the AND.
		_checkBlock(phase2, andGate, itemAnd, FacingDirection.EAST, false);
		_checkBlock(phase2, andGate.getRelative(1, 0, 0), itemGate, null, false);
		_checkBlock(phase2, orGate, itemOr, FacingDirection.EAST, true);
		_checkBlock(phase2, orGate.getRelative(1, 0, 0), itemGate, null, true);
		_checkBlock(phase2, notGate, itemNot, FacingDirection.EAST, false);
		_checkBlock(phase2, notGate.getRelative(1, 0, 0), itemGate, null, false);
		
		// Run phase3 - we flip the switches and should see AND change.
		runner.enqueueEntityChange(1, _wrapSubAction(entity1, new EntityChangeSetBlockLogicState(andGate.getRelative(0, 1, 0), true)), 3L);
		runner.enqueueEntityChange(2, _wrapSubAction(entity2, new EntityChangeSetBlockLogicState(orGate.getRelative(0, 1, 0), true)), 3L);
		
		// Run enough ticks to observe the output from the or gate.
		// 1) EntityChangeSetBlockLogicState
		runner.startNextTick();
		runner.waitForPreviousTick();
		// 2) MutationBlockSetLogicState
		runner.startNextTick();
		runner.waitForPreviousTick();
		// 3) Logic propagate
		runner.startNextTick();
		runner.waitForPreviousTick();
		// 4) Run MutationBlockLogicChange.
		runner.startNextTick();
		runner.waitForPreviousTick();
		// 5) Run MutationBlockInternalSetLogicState.
		runner.startNextTick();
		runner.waitForPreviousTick();
		// 6) Logic propagate
		runner.startNextTick();
		runner.waitForPreviousTick();
		// 7) Run MutationBlockLogicChange.
		runner.startNextTick();
		runner.waitForPreviousTick();
		// 8) Run MutationBlockInternalSetLogicState.
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		IReadOnlyCuboidData phase3 = snapshot.cuboids().get(cuboid.getCuboidAddress()).completed();
		
		// We should now see the final AND change.
		_checkBlock(phase3, andGate, itemAnd, FacingDirection.EAST, true);
		_checkBlock(phase3, andGate.getRelative(1, 0, 0), itemGate, null, true);
		_checkBlock(phase3, orGate, itemOr, FacingDirection.EAST, true);
		_checkBlock(phase3, orGate.getRelative(1, 0, 0), itemGate, null, true);
		_checkBlock(phase3, notGate, itemNot, FacingDirection.EAST, false);
		_checkBlock(phase3, notGate.getRelative(1, 0, 0), itemGate, null, false);
		
		runner.shutdown();
	}


	private CuboidData _buildCascade(CuboidAddress address)
	{
		// A "cascade" cuboid is one designed to force water to split as it fall through the cuboid.
		// This means that the bottom layer is air but every odd-numbered layer is a checker-board of stone, with the
		// opposite pattern of the last layer.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		for (byte z = 1; z < 32; z += 2)
		{
			byte offset = (byte)((z % 4) / 2);
			for (byte y = offset; y < 32; y += 2)
			{
				for (byte x = offset; x < 32; x += 2)
				{
					cuboid.setData15(AspectRegistry.BLOCK, new BlockAddress(x, y, z), STONE_ITEM.number());
				}
			}
		}
		return cuboid;
	}

	private TickRunner _createTestRunner()
	{
		// We use the default config for most tests.
		return _createTestRunnerWithConfig(new WorldConfig());
	}

	private TickRunner _createTestRunnerWithConfig(WorldConfig config)
	{
		// We want to disable spawning for most of these tests.
		config.difficulty = Difficulty.PEACEFUL;
		Consumer<TickSnapshot> snapshotListener = (TickSnapshot completed) -> {};
		Random random = new Random();
		TickRunner runner = new TickRunner(ServerRunner.TICK_RUNNER_THREAD_COUNT
				, MILLIS_PER_TICK
				, new CreatureIdAssigner()
				, new PassiveIdAssigner()
				, (int bound) -> random.nextInt(bound)
				, snapshotListener
				, config
		);
		return runner;
	}

	private SuspendedCuboid<IReadOnlyCuboidData> _packageCuboid(CuboidData cuboid)
	{
		return new SuspendedCuboid<IReadOnlyCuboidData>(cuboid, HeightMapHelpers.buildHeightMap(cuboid), List.of(), List.of(), Map.of(), List.of());
	}

	private long _applyIncrementalBreaks(TickRunner runner, long nextCommit, int entityId, Entity entity, AbsoluteLocation changeLocation, int millisOfBreak)
	{
		int millisRemaining = millisOfBreak;
		while (millisRemaining > 0)
		{
			EntityChangeIncrementalBlockBreak break1 = new EntityChangeIncrementalBlockBreak(changeLocation);
			runner.enqueueEntityChange(entityId, _wrapSubAction(entity, break1), nextCommit);
			nextCommit += 1L;
			runner.startNextTick();
			millisRemaining -= MILLIS_PER_TICK;
		}
		return nextCommit;
	}

	private static CuboidData _zeroAirCuboidWithBase()
	{
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		for (int y = 0; y < Encoding.CUBOID_EDGE_SIZE; ++y)
		{
			for (int x = 0; x < Encoding.CUBOID_EDGE_SIZE; ++x)
			{
				cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(x, y, 0), STONE.item().number());
			}
		}
		return cuboid;
	}

	private static void _placeItemAsBlock(CuboidData cuboid, AbsoluteLocation location, Item item, FacingDirection orientation, boolean active)
	{
		cuboid.setData15(AspectRegistry.BLOCK, location.getBlockAddress(), item.number());
		if (null != orientation)
		{
			cuboid.setData7(AspectRegistry.ORIENTATION, location.getBlockAddress(), FacingDirection.directionToByte(orientation));
		}
		if (active)
		{
			cuboid.setData7(AspectRegistry.FLAGS, location.getBlockAddress(), FlagsAspect.FLAG_ACTIVE);
		}
	}

	private static void _checkBlock(IReadOnlyCuboidData cuboid, AbsoluteLocation location, Item item, FacingDirection orientation, boolean active)
	{
		Assert.assertEquals(item.number(), cuboid.getData15(AspectRegistry.BLOCK, location.getBlockAddress()));
		if (null != orientation)
		{
			Assert.assertEquals(FacingDirection.directionToByte(orientation), cuboid.getData7(AspectRegistry.ORIENTATION, location.getBlockAddress()));
		}
		if (active)
		{
			Assert.assertEquals(FlagsAspect.FLAG_ACTIVE, cuboid.getData7(AspectRegistry.FLAGS, location.getBlockAddress()));
		}
	}

	private static EntityActionSimpleMove<IMutablePlayerEntity> _wrapForEntity(Entity entity, IMutationBlock next)
	{
		EntityChangeMutation wrapper = new EntityChangeMutation(next);
		return _wrapSubAction(entity, wrapper);
	}

	private static EntityActionSimpleMove<IMutablePlayerEntity> _wrapSubAction(Entity entity, IEntitySubAction<IMutablePlayerEntity> subAction)
	{
		return new EntityActionSimpleMove<>(0.0f
			, 0.0f
			, EntityActionSimpleMove.Intensity.STANDING
			, OrientationHelpers.YAW_NORTH
			, OrientationHelpers.PITCH_FLAT
			, subAction
		);
	}
}
