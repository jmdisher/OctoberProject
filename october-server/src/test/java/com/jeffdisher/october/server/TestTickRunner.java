package com.jeffdisher.october.server;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.LightAspect;
import com.jeffdisher.october.aspects.LogicAspect;
import com.jeffdisher.october.aspects.StationRegistry;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.EntityChangeSendItem;
import com.jeffdisher.october.logic.HeightMapHelpers;
import com.jeffdisher.october.logic.ScheduledMutation;
import com.jeffdisher.october.logic.ShockwaveMutation;
import com.jeffdisher.october.mutations.DropItemMutation;
import com.jeffdisher.october.mutations.EntityChangeAttackEntity;
import com.jeffdisher.october.mutations.EntityChangeFutureBlock;
import com.jeffdisher.october.mutations.EntityChangeIncrementalBlockBreak;
import com.jeffdisher.october.mutations.EntityChangeMutation;
import com.jeffdisher.october.mutations.EntityChangeOperatorSetCreative;
import com.jeffdisher.october.mutations.EntityChangeSetBlockLogicState;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.MutationBlockFurnaceCraft;
import com.jeffdisher.october.mutations.MutationBlockIncrementalBreak;
import com.jeffdisher.october.mutations.MutationBlockOverwriteByEntity;
import com.jeffdisher.october.mutations.MutationBlockPeriodic;
import com.jeffdisher.october.mutations.MutationBlockReplace;
import com.jeffdisher.october.mutations.MutationBlockStoreItems;
import com.jeffdisher.october.mutations.MutationEntityPushItems;
import com.jeffdisher.october.mutations.MutationPlaceSelectedBlock;
import com.jeffdisher.october.mutations.PickUpItemMutation;
import com.jeffdisher.october.mutations.ReplaceBlockMutation;
import com.jeffdisher.october.mutations.SaturatingDamage;
import com.jeffdisher.october.persistence.SuspendedCuboid;
import com.jeffdisher.october.persistence.SuspendedEntity;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.CuboidColumnAddress;
import com.jeffdisher.october.types.Difficulty;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.WorldConfig;
import com.jeffdisher.october.utils.Encoding;
import com.jeffdisher.october.worldgen.CuboidGenerator;


public class TestTickRunner
{
	public static final long MILLIS_PER_TICK = 10L;
	private static Environment ENV;
	private static Item STONE_ITEM;
	private static Item STONE_BRICK_ITEM;
	private static Item LOG_ITEM;
	private static Item PLANK_ITEM;
	private static Item CHARCOAL_ITEM;
	private static Item DIRT_ITEM;
	private static Item SAPLING_ITEM;
	private static Item LANTERN_ITEM;
	private static Item LEAF_ITEM;
	private static Item WHEAT_SEED_ITEM;
	private static Item WHEAT_ITEM_ITEM;
	private static Item WHEAT_SEEDLING_ITEM;
	private static Item WHEAT_YOUNG_ITEM;
	private static Item WHEAT_MATURE_ITEM;
	private static Item WATER_STRONG;
	private static Block STONE;
	private static Block WATER_SOURCE;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		STONE_ITEM = ENV.items.getItemById("op.stone");
		STONE_BRICK_ITEM = ENV.items.getItemById("op.stone_brick");
		LOG_ITEM = ENV.items.getItemById("op.log");
		PLANK_ITEM = ENV.items.getItemById("op.plank");
		CHARCOAL_ITEM = ENV.items.getItemById("op.charcoal");
		DIRT_ITEM = ENV.items.getItemById("op.dirt");
		SAPLING_ITEM = ENV.items.getItemById("op.sapling");
		LANTERN_ITEM = ENV.items.getItemById("op.lantern");
		LEAF_ITEM = ENV.items.getItemById("op.leaf");
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
	public void basicOneCuboid()
	{
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		TickRunner runner = _createTestRunner();
		int entityId = 1;
		runner.setupChangesForTick(List.of(new SuspendedCuboid<IReadOnlyCuboidData>(cuboid, HeightMapHelpers.buildHeightMap(cuboid), List.of(), List.of(), Map.of()))
				, null
				, List.of(_createFreshEntity(entityId))
				, null
		);
		runner.start();
		runner.waitForPreviousTick();
		// The mutation will be run in the next tick since there isn't one running.
		runner.enqueueEntityChange(entityId, new EntityChangeMutation(new ReplaceBlockMutation(new AbsoluteLocation(0, 0, 0), ENV.special.AIR.item().number(), STONE_ITEM.number())), 1L);
		// (run an extra tick to unwrap the entity change)
		runner.startNextTick();
		runner.startNextTick();
		TickRunner.Snapshot snapshot = runner.waitForPreviousTick();
		runner.shutdown();
		
		Assert.assertEquals(1, snapshot.stats().committedCuboidMutationCount());
	}

	@Test
	public void shockwaveOneCuboid()
	{
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, STONE);
		TickRunner runner = _createTestRunner();
		int entityId = 1;
		runner.setupChangesForTick(List.of(new SuspendedCuboid<IReadOnlyCuboidData>(cuboid, HeightMapHelpers.buildHeightMap(cuboid), List.of(), List.of(), Map.of()))
				, null
				, List.of(_createFreshEntity(entityId))
				, null
		);
		runner.start();
		runner.waitForPreviousTick();
		// We enqueue a single shockwave in the centre of the cuboid and allow it to replicate 2 times.
		runner.enqueueEntityChange(entityId, new EntityChangeMutation(new ShockwaveMutation(new AbsoluteLocation(16, 16, 16), 2)), 1L);
		// (run an extra tick to unwrap the entity change)
		runner.startNextTick();
		runner.startNextTick();
		TickRunner.Snapshot snap1 = runner.startNextTick();
		Assert.assertEquals(6, snap1.cuboids().get(address).scheduledBlockMutations().size());
		TickRunner.Snapshot snap2 =runner.waitForPreviousTick();
		Assert.assertEquals(36, snap2.cuboids().get(address).scheduledBlockMutations().size());
		runner.startNextTick();
		TickRunner.Snapshot snap3 = runner.startNextTick();
		Assert.assertEquals(0, snap3.cuboids().get(address).scheduledBlockMutations().size());
		runner.shutdown();
		
		// 1 + 6 + 36 = 43.
		Assert.assertEquals(1, snap1.stats().committedCuboidMutationCount());
		Assert.assertEquals(6, snap2.stats().committedCuboidMutationCount());
		Assert.assertEquals(36, snap3.stats().committedCuboidMutationCount());
	}

	@Test
	public void shockwaveMultiCuboids()
	{
		// Use extra threads here to stress further.
		TickRunner runner = new TickRunner(8, MILLIS_PER_TICK
				, null
				, (int bound) -> 0
				, (TickRunner.Snapshot completed) -> {}
				, new WorldConfig()
		);
		int entityId = 1;
		runner.setupChangesForTick(List.of(_buildStoneCuboid(CuboidAddress.fromInt(0, 0, 0))
					, _buildStoneCuboid(CuboidAddress.fromInt(0, 0, -1))
					, _buildStoneCuboid(CuboidAddress.fromInt(0, -1, 0))
					, _buildStoneCuboid(CuboidAddress.fromInt(0, -1, -1))
					, _buildStoneCuboid(CuboidAddress.fromInt(-1, 0, 0))
					, _buildStoneCuboid(CuboidAddress.fromInt(-1, 0, -1))
					, _buildStoneCuboid(CuboidAddress.fromInt(-1, -1, 0))
					, _buildStoneCuboid(CuboidAddress.fromInt(-1, -1, -1))
				)
				, null
				, List.of(_createFreshEntity(entityId))
				, null
		);
		runner.start();
		runner.waitForPreviousTick();
		// We enqueue a single shockwave in the centre of the cuboid and allow it to replicate 2 times.
		runner.enqueueEntityChange(entityId, new EntityChangeMutation(new ShockwaveMutation(new AbsoluteLocation(0, 0, 0), 2)), 1L);
		// (run an extra tick to unwrap the entity change)
		runner.startNextTick();
		runner.startNextTick();
		TickRunner.Snapshot snap1 = runner.startNextTick();
		Assert.assertEquals(4, snap1.cuboids().values().stream().filter((TickRunner.SnapshotCuboid cuboid) -> !cuboid.scheduledBlockMutations().isEmpty()).count());
		TickRunner.Snapshot snap2 = runner.startNextTick();
		Assert.assertEquals(7, snap2.cuboids().values().stream().filter((TickRunner.SnapshotCuboid cuboid) -> !cuboid.scheduledBlockMutations().isEmpty()).count());
		TickRunner.Snapshot snap3 = runner.startNextTick();
		Assert.assertEquals(0, snap3.cuboids().values().stream().filter((TickRunner.SnapshotCuboid cuboid) -> !cuboid.scheduledBlockMutations().isEmpty()).count());
		runner.shutdown();
		
		// 1 + 6 + 36 = 43.
		Assert.assertEquals(1, snap1.stats().committedCuboidMutationCount());
		Assert.assertEquals(6, snap2.stats().committedCuboidMutationCount());
		Assert.assertEquals(36, snap3.stats().committedCuboidMutationCount());
	}

	@Test
	public void basicBlockRead()
	{
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		TickRunner runner = _createTestRunner();
		int entityId = 1;
		runner.setupChangesForTick(List.of(new SuspendedCuboid<IReadOnlyCuboidData>(cuboid, HeightMapHelpers.buildHeightMap(cuboid), List.of(), List.of(), Map.of()))
				, null
				, List.of(_createFreshEntity(entityId))
				, null
		);
		runner.start();
		TickRunner.Snapshot startState = runner.waitForPreviousTick();
		
		// Before we run a tick, the cuboid shouldn't yet be loaded (it is added to the new world during a tick) so we should see a null block.
		Assert.assertNull(_getBlockProxy(startState, new AbsoluteLocation(0, 0, 0)));
		
		// Run the tick so that it applies the new load.
		runner.startNextTick();
		TickRunner.Snapshot snapshot = runner.waitForPreviousTick();
		// Now, we should see a block with default properties.
		BlockProxy block = _getBlockProxy(snapshot, new AbsoluteLocation(0, 0, 0));
		Assert.assertEquals(ENV.special.AIR, block.getBlock());
		
		// Note that the mutation will not be enqueued in the next tick, but the following one (they are queued and picked up when the threads finish).
		runner.enqueueEntityChange(entityId, new EntityChangeMutation(new ReplaceBlockMutation(new AbsoluteLocation(0, 0, 0), ENV.special.AIR.item().number(), STONE_ITEM.number())), 1L);
		// (run an extra tick to unwrap the entity change)
		runner.startNextTick();
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		runner.shutdown();
		
		// We should now see the new data.
		block = _getBlockProxy(snapshot, new AbsoluteLocation(0, 0, 0));
		Assert.assertEquals(STONE, block.getBlock());
	}

	@Test
	public void basicInventoryOperations()
	{
		// Just add, add, and remove some inventory items.
		AbsoluteLocation testBlock = new AbsoluteLocation(0, 0, 0);
		Item stoneItem = STONE_ITEM;
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		
		// Create a tick runner with a single cuboid and get it running.
		TickRunner runner = _createTestRunner();
		int entityId = 1;
		runner.setupChangesForTick(List.of(new SuspendedCuboid<IReadOnlyCuboidData>(cuboid, HeightMapHelpers.buildHeightMap(cuboid), List.of(), List.of(), Map.of()))
				, null
				, List.of(_createFreshEntity(entityId))
				, null
		);
		runner.start();
		runner.startNextTick();
		TickRunner.Snapshot snapshot = runner.waitForPreviousTick();
		
		// Make sure that we see the empty inventory.
		BlockProxy block = _getBlockProxy(snapshot, testBlock);
		Assert.assertEquals(0, block.getInventory().currentEncumbrance);
		
		// Apply the first mutation to add data.
		snapshot = _runTickLockStep(runner, new DropItemMutation(testBlock, stoneItem, 1));
		block = _getBlockProxy(snapshot, testBlock);
		Assert.assertEquals(1, block.getInventory().getCount(stoneItem));
		
		// Try to drop too much to fit and verify that nothing changes.
		snapshot = _runTickLockStep(runner, new DropItemMutation(testBlock, stoneItem, StationRegistry.CAPACITY_BLOCK_EMPTY / 4));
		block = _getBlockProxy(snapshot, testBlock);
		Assert.assertEquals(1, block.getInventory().getCount(stoneItem));
		
		// Add a little more data and make sure that it updates.
		snapshot = _runTickLockStep(runner, new DropItemMutation(testBlock, stoneItem, 2));
		block = _getBlockProxy(snapshot, testBlock);
		Assert.assertEquals(3, block.getInventory().getCount(stoneItem));
		
		// Remove everything and make sure that we end up with an empty inventory.
		snapshot = _runTickLockStep(runner, new PickUpItemMutation(testBlock, stoneItem, 3));
		block = _getBlockProxy(snapshot, testBlock);
		Assert.assertEquals(0, block.getInventory().currentEncumbrance);
		
		// Test is done.
		runner.shutdown();
	}

	@Test
	public void deliverWithEntity()
	{
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		TickRunner runner = _createTestRunner();
		runner.setupChangesForTick(List.of(new SuspendedCuboid<IReadOnlyCuboidData>(cuboid, HeightMapHelpers.buildHeightMap(cuboid), List.of(), List.of(), Map.of()))
				, null
				, null
				, null
		);
		runner.start();
		runner.startNextTick();
		runner.waitForPreviousTick();
		
		// Have a new entity join and wait for them to be added.
		int entityId = 1;
		runner.setupChangesForTick(null
				, null
				, List.of(_createFreshEntity(entityId))
				, null
		);
		runner.startNextTick();
		runner.waitForPreviousTick();
		
		// Now, add a mutation from this entity to deliver the block replacement mutation.
		AbsoluteLocation changeLocation = new AbsoluteLocation(0, 0, 0);
		long commit1 = 1L;
		runner.enqueueEntityChange(entityId, new EntityChangeMutation(new ReplaceBlockMutation(changeLocation, ENV.special.AIR.item().number(), STONE_ITEM.number())), commit1);
		
		// This will take a few ticks to be observable:
		// -after tick 1, the change will have been run and the mutation enqueued
		runner.startNextTick();
		TickRunner.Snapshot snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(commit1, snapshot.entities().get(entityId).commitLevel());
		Assert.assertEquals(1, snapshot.stats().committedEntityMutationCount());
		// -after tick 2, the mutation will have been committed
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(1, snapshot.stats().committedCuboidMutationCount());
		
		// Shutdown and observe expected results.
		runner.shutdown();
		
		Assert.assertEquals(STONE, _getBlockProxy(snapshot, changeLocation).getBlock());
	}

	@Test
	public void dependentEntityChanges()
	{
		TickRunner runner = _createTestRunner();
		runner.start();
		
		// We need 2 entities for this but we will give one some items.
		int entityId1 = 1;
		int entityId2 = 2;
		MutableEntity mutable = MutableEntity.createForTest(entityId1);
		mutable.newInventory.addAllItems(STONE_ITEM, 2);
		runner.setupChangesForTick(null
				, null
				, List.of(new SuspendedEntity(mutable.freeze(), List.of())
						, _createFreshEntity(entityId2)
				)
				, null
		);
		// (run a tick to pick up the users)
		runner.startNextTick();
		runner.waitForPreviousTick();
		
		// Try to pass the items to the other entity.
		EntityChangeSendItem send = new EntityChangeSendItem(entityId2, STONE_ITEM);
		long commit1 = 1L;
		runner.enqueueEntityChange(entityId1, send, commit1);
		// (run a tick to run the change and enqueue the next)
		runner.startNextTick();
		TickRunner.Snapshot snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(commit1, snapshot.entities().get(entityId1).commitLevel());
		Assert.assertNotNull(snapshot.entities().get(entityId1).updated());
		Assert.assertEquals(0, snapshot.entities().get(entityId1).scheduledMutations().size());
		Assert.assertEquals(1, snapshot.entities().get(entityId2).scheduledMutations().size());
		// (run a tick to run the final change)
		runner.startNextTick();
		TickRunner.Snapshot finalSnapshot = runner.waitForPreviousTick();
		Assert.assertEquals(0, finalSnapshot.entities().get(entityId2).scheduledMutations().size());
		runner.shutdown();
		
		// Now, check for results.
		Assert.assertNotNull(finalSnapshot.entities().get(entityId2).updated());
		Entity sender = finalSnapshot.entities().get(entityId1).completed();
		Entity receiver = finalSnapshot.entities().get(entityId2).completed();
		Assert.assertEquals(0, sender.inventory().sortedKeys().size());
		Assert.assertEquals(1, receiver.inventory().sortedKeys().size());
		Assert.assertEquals(2, receiver.inventory().getCount(STONE_ITEM));
	}

	@Test
	public void multiStepBlockBreak()
	{
		// Show what happens if we break a block in 2 steps.
		TickRunner runner = _createTestRunner();
		
		// Create a cuboid of stone.
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, STONE);
		// We will load a pickaxe into the entity so that it can do this in only a few small hits.
		int entityId = 1;
		MutableEntity mutable = MutableEntity.createForTest(entityId);
		Item pickaxe = ENV.items.getItemById("op.iron_pickaxe");
		int startDurability = ENV.durability.getDurability(pickaxe);
		mutable.newInventory.addNonStackableBestEfforts(new NonStackableItem(pickaxe, startDurability));
		mutable.setSelectedKey(1);
		runner.setupChangesForTick(List.of(new SuspendedCuboid<IReadOnlyCuboidData>(cuboid, HeightMapHelpers.buildHeightMap(cuboid), List.of(), List.of(), Map.of()))
				, null
				, List.of(new SuspendedEntity(mutable.freeze(), List.of()))
				, null
		);
		
		// Start up and run the first tick so that these get loaded.
		runner.start();
		runner.startNextTick();
		runner.waitForPreviousTick();
		
		// Schedule the first step.
		// We will now show how to schedule the multi-phase change.
		AbsoluteLocation changeLocation1 = new AbsoluteLocation(0, 0, 0);
		long nextCommit = 1L;
		nextCommit = _applyIncrementalBreaks(runner, nextCommit, entityId, changeLocation1, (short)100);
		
		// Wait for the tick and observe the results.
		TickRunner.Snapshot snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(nextCommit - 1L, snapshot.entities().get(entityId).commitLevel());
		Assert.assertEquals(1, snapshot.stats().committedEntityMutationCount());
		
		// Run another tick to see the underlying block change applied.
		// We should see the commit and the change to the damage value.
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(1, snapshot.stats().committedCuboidMutationCount());
		BlockProxy proxy1 = _getBlockProxy(snapshot, changeLocation1);
		Assert.assertEquals(STONE, proxy1.getBlock());
		Assert.assertEquals((short)1000, proxy1.getDamage());
		Assert.assertNull(proxy1.getInventory());
		
		// Now, enqueue the remaining hits to finish the break.
		nextCommit = _applyIncrementalBreaks(runner, nextCommit, entityId, changeLocation1, (short)100);
		
		snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(nextCommit - 1L, snapshot.entities().get(entityId).commitLevel());
		Assert.assertEquals(1, snapshot.stats().committedEntityMutationCount());
		
		// Run the second tick to see the block change.
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(1, snapshot.stats().committedCuboidMutationCount());
		BlockProxy proxy2 = _getBlockProxy(snapshot, changeLocation1);
		Assert.assertEquals(ENV.special.AIR, proxy2.getBlock());
		Assert.assertEquals((short) 0, proxy2.getDamage());
		
		// Run another tick to see the item move to the entity inventory (the item should be in the entity inventory, not the ground).
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		Inventory blockInventory = proxy2.getInventory();
		Assert.assertEquals(0, blockInventory.sortedKeys().size());
		Entity entity = snapshot.entities().get(entityId).completed();
		Inventory entityInventory = entity.inventory();
		Assert.assertEquals(1, entityInventory.getCount(STONE_ITEM));
		
		// We should also see the durability loss on our tool (2 x 100ms).
		int updatedDurability = entityInventory.getNonStackableForKey(entity.hotbarItems()[entity.hotbarIndex()]).durability();
		Assert.assertEquals(2 * 100, (startDurability - updatedDurability));
		
		runner.shutdown();
	}

	@Test
	public void checkSnapshotDelta()
	{
		TickRunner.Snapshot[] snapshotRef = new TickRunner.Snapshot[1];
		Consumer<TickRunner.Snapshot> snapshotListener = (TickRunner.Snapshot completed) -> {
			snapshotRef[0] = completed;
		};
		TickRunner runner = new TickRunner(ServerRunner.TICK_RUNNER_THREAD_COUNT
				, MILLIS_PER_TICK
				, null
				, (int bound) -> 0
				, snapshotListener
				, new WorldConfig()
		);
		CuboidAddress targetAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidAddress constantAddress = CuboidAddress.fromInt(0, 0, 1);
		CuboidColumnAddress column = targetAddress.getColumn();
		Assert.assertEquals(column, constantAddress.getColumn());
		int entityId = 1;
		runner.setupChangesForTick(List.of(_buildAirCuboid(targetAddress)
					, _buildAirCuboid(constantAddress)
				)
				, null
				, List.of(_createFreshEntity(entityId))
				, null
		);
		
		// Verify that there is no snapshot until we start.
		Assert.assertNull(snapshotRef[0]);
		runner.start();
		
		// Wait for the start-up to complete and verify that we have the empty initial snapshot (since the start doesn't pick up any cuboids).
		runner.waitForPreviousTick();
		Assert.assertNotNull(snapshotRef[0]);
		Assert.assertEquals(0, snapshotRef[0].cuboids().size());
		Assert.assertEquals(0, snapshotRef[0].completedHeightMaps().size());
		
		// Run the tick so that it applies the new load.
		runner.startNextTick();
		runner.waitForPreviousTick();
		Assert.assertNotNull(snapshotRef[0]);
		// We should see 2 cuboids.
		Map<CuboidAddress, TickRunner.SnapshotCuboid> initialCuboids = snapshotRef[0].cuboids();
		Map<CuboidColumnAddress, ColumnHeightMap> initialHeights = snapshotRef[0].completedHeightMaps();
		Assert.assertEquals(2, initialCuboids.size());
		Assert.assertEquals(1, initialHeights.size());
		
		// Run a mutation and notice that only the changed cuboid isn't an instance match.
		runner.enqueueEntityChange(1, new EntityChangeMutation(new ReplaceBlockMutation(new AbsoluteLocation(0, 0, 0), ENV.special.AIR.item().number(), STONE_ITEM.number())), 1L);
		// (run an extra tick to unwrap the entity change)
		runner.startNextTick();
		runner.startNextTick();
		runner.waitForPreviousTick();
		Assert.assertNotNull(snapshotRef[0]);
		// This should be the same size.
		Map<CuboidAddress, TickRunner.SnapshotCuboid> laterCuboids = snapshotRef[0].cuboids();
		Map<CuboidColumnAddress, ColumnHeightMap> laterHeights = snapshotRef[0].completedHeightMaps();
		Assert.assertEquals(2, laterCuboids.size());
		Assert.assertEquals(1, laterHeights.size());
		
		runner.shutdown();
		
		// Verify that the target cuboid is a new instance.
		Assert.assertTrue(initialCuboids.get(targetAddress) != laterCuboids.get(targetAddress));
		Assert.assertTrue(1 == _mismatchCount(initialHeights.get(column), laterHeights.get(column)));
		// Verify that the unchanged cuboid is the same instance.
		Assert.assertTrue(initialCuboids.get(constantAddress).completed() == laterCuboids.get(constantAddress).completed());
	}

	@Test
	public void joinAndDepart()
	{
		// Create an entity and have it join and then depart, verifying that we can observe this add and removal in the snapshot.
		TickRunner.Snapshot[] snapshotRef = new TickRunner.Snapshot[1];
		Consumer<TickRunner.Snapshot> snapshotListener = (TickRunner.Snapshot completed) -> {
			snapshotRef[0] = completed;
		};
		TickRunner runner = new TickRunner(ServerRunner.TICK_RUNNER_THREAD_COUNT
				, MILLIS_PER_TICK
				, null
				, null
				, snapshotListener
				, new WorldConfig()
		);
		
		// Verify that there is no snapshot until we start.
		Assert.assertNull(snapshotRef[0]);
		runner.start();
		
		// Wait for the start-up to complete and verify that there are no entities in the snapshot.
		runner.waitForPreviousTick();
		Assert.assertNotNull(snapshotRef[0]);
		Assert.assertEquals(0, snapshotRef[0].entities().size());
		
		// Add the new entity and run a tick.
		int entityId = 1;
		runner.setupChangesForTick(null
				, null
				, List.of(_createFreshEntity(entityId))
				, null
		);
		runner.startNextTick();
		runner.waitForPreviousTick();
		// We should see the entity.
		Assert.assertEquals(1, snapshotRef[0].entities().size());
		
		// Now, leave and verify that the entity has disappeared from the snapshot.
		runner.setupChangesForTick(null
				, null
				, null
				, List.of(entityId)
		);
		runner.startNextTick();
		runner.waitForPreviousTick();
		Assert.assertEquals(0, snapshotRef[0].entities().size());
		
		runner.shutdown();
	}

	@Test
	public void startStopReloadWithSuspendedMutations()
	{
		// We will start a runner, attach an entity, have it send an action with block mutation consequences, wait until we see this in the snapshot, shut it down, then restart it and see that these resume.
		TickRunner runner = _createTestRunner();
		runner.start();
		
		// Wait for the start-up to complete and then load the entity and some cuboids.
		runner.waitForPreviousTick();
		CuboidAddress airAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidAddress stoneAddress = CuboidAddress.fromInt(0, 0, -1);
		int entityId = 1;
		runner.setupChangesForTick(List.of(
				_buildAirCuboid(airAddress),
				_packageCuboid(CuboidGenerator.createFilledCuboid(stoneAddress, STONE))
				)
				, null
				, List.of(_createFreshEntity(entityId))
				, null
		);
		runner.startNextTick();
		TickRunner.Snapshot snapshot = runner.waitForPreviousTick();
		// Verify that we see the entity and cuboids.
		Assert.assertEquals(1, snapshot.entities().size());
		Assert.assertEquals(2, snapshot.cuboids().size());
		
		// Tell them to start breaking a stone block.
		short damage = 10;
		runner.enqueueEntityChange(entityId, new EntityChangeIncrementalBlockBreak(new AbsoluteLocation(1, 1, -1), damage), 1L);
		// Run a tick and verify that we see the cuboid mutation from this in the snapshot.
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(0, snapshot.cuboids().get(stoneAddress).completed().getData15(AspectRegistry.DAMAGE, BlockAddress.fromInt(1, 1, 31)));
		Assert.assertEquals(1, snapshot.cuboids().get(stoneAddress).scheduledBlockMutations().size());
		MutationBlockIncrementalBreak mutation = (MutationBlockIncrementalBreak) snapshot.cuboids().get(stoneAddress).scheduledBlockMutations().get(0).mutation();
		
		// Shut down the runner, start a new one, and load the cuboids back in.
		runner.shutdown();
		runner = _createTestRunner();
		runner.start();
		runner.waitForPreviousTick();
		CuboidData stoneCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), STONE);
		runner.setupChangesForTick(List.of(
				_buildAirCuboid(CuboidAddress.fromInt(0, 0, 0)),
				new SuspendedCuboid<IReadOnlyCuboidData>(stoneCuboid, HeightMapHelpers.buildHeightMap(stoneCuboid), List.of(), List.of(new ScheduledMutation(mutation, 0L)), Map.of())
			)
			, null
			, List.of(_createFreshEntity(entityId))
			, null
		);
		runner.startNextTick();
		// Verify that this mutation has been run.
		snapshot = runner.waitForPreviousTick();
		// Note that we no longer see block update events in the scheduled mutations and nothing else was scheduled.
		Assert.assertEquals(0, snapshot.cuboids().values().iterator().next().scheduledBlockMutations().size());
		Assert.assertEquals(damage, snapshot.cuboids().get(stoneAddress).completed().getData15(AspectRegistry.DAMAGE, BlockAddress.fromInt(1, 1, 31)));
		
		runner.shutdown();
	}

	@Test
	public void saturatingMutations()
	{
		// Apply a few mutations which saturate within one tick.
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, STONE);
		TickRunner runner = _createTestRunner();
		int entityId = 1;
		runner.setupChangesForTick(List.of(new SuspendedCuboid<IReadOnlyCuboidData>(cuboid, HeightMapHelpers.buildHeightMap(cuboid), List.of(), List.of(), Map.of()))
				, null
				, List.of(_createFreshEntity(entityId))
				, null
		);
		runner.start();
		runner.waitForPreviousTick();
		
		// Apply the saturating mutations to a few blocks - duplicating in one case.
		short damage = 50;
		AbsoluteLocation location0 = new AbsoluteLocation(0, 0, 0);
		IMutationBlock mutation0 = new SaturatingDamage(location0, damage);
		runner.enqueueEntityChange(entityId, new EntityChangeMutation(mutation0), 1L);
		runner.enqueueEntityChange(entityId, new EntityChangeMutation(mutation0), 2L);
		AbsoluteLocation location1 = new AbsoluteLocation(1, 0, 0);
		IMutationBlock mutation1 = new SaturatingDamage(location1, damage);
		runner.enqueueEntityChange(entityId, new EntityChangeMutation(mutation1), 3L);
		
		// Run these and observe that the same damage was applied, no matter the number of mutations.
		runner.startNextTick();
		runner.startNextTick();
		TickRunner.Snapshot snap1 = runner.waitForPreviousTick();
		BlockProxy proxy0 = _getBlockProxy(snap1, location0);
		BlockProxy proxy1 = _getBlockProxy(snap1, location1);
		Assert.assertEquals(damage, proxy0.getDamage());
		Assert.assertEquals(damage, proxy1.getDamage());
		
		// But this shouldn't prevent another attempt in a later tick.
		runner.enqueueEntityChange(entityId, new EntityChangeMutation(mutation0), 4L);
		runner.startNextTick();
		runner.startNextTick();
		TickRunner.Snapshot snap2 = runner.waitForPreviousTick();
		proxy0 = _getBlockProxy(snap2, location0);
		proxy1 = _getBlockProxy(snap2, location1);
		Assert.assertEquals(2 * damage, proxy0.getDamage());
		Assert.assertEquals(damage, proxy1.getDamage());
		
		runner.shutdown();
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
		int entityId = 1;
		MutableEntity mutable = MutableEntity.createForTest(entityId);
		mutable.newInventory.addAllItems(LOG_ITEM, 3);
		int logKey = mutable.newInventory.getIdOfStackableType(LOG_ITEM);
		mutable.newInventory.addAllItems(PLANK_ITEM, 2);
		int plankKey = mutable.newInventory.getIdOfStackableType(PLANK_ITEM);
		Entity entity = mutable.freeze();
		runner.setupChangesForTick(List.of(new SuspendedCuboid<IReadOnlyCuboidData>(cuboid, HeightMapHelpers.buildHeightMap(cuboid), List.of(), List.of(), Map.of()))
				, null
				, List.of(new SuspendedEntity(entity, List.of()))
				, null
		);
		
		runner.start();
		// Remember that the first tick is just returning the empty state.
		runner.waitForPreviousTick();
		runner.startNextTick();
		TickRunner.Snapshot snap = runner.waitForPreviousTick();
		Assert.assertNotNull(snap.cuboids().get(address));
		
		// Load the furnace with fuel and material.
		AbsoluteLocation location = new AbsoluteLocation(0, 0, 0);
		BlockAddress block = location.getBlockAddress();
		runner.enqueueEntityChange(entityId, new MutationEntityPushItems(location, logKey, 3, Inventory.INVENTORY_ASPECT_INVENTORY), 1L);
		runner.enqueueEntityChange(entityId, new MutationEntityPushItems(location, plankKey, 2, Inventory.INVENTORY_ASPECT_FUEL), 2L);
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
	public void loadAndUnload()
	{
		// We will load a few cuboids, verify that they are in the snapshot, then unload one, and verify that it is gone from the snapshot.
		TickRunner runner = _createTestRunner();
		runner.start();
		
		// Run the first tick and verify the empty cuboid set.
		runner.startNextTick();
		TickRunner.Snapshot snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(0, snapshot.cuboids().size());
		
		// Add the new cuboids, run a tick, and verify they are in the snapshot.
		CuboidAddress address0 = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid0 = CuboidGenerator.createFilledCuboid(address0, ENV.special.AIR);
		CuboidAddress address1 = CuboidAddress.fromInt(0, 0, -1);
		CuboidData cuboid1 = CuboidGenerator.createFilledCuboid(address1, STONE);
		runner.setupChangesForTick(List.of(new SuspendedCuboid<IReadOnlyCuboidData>(cuboid0, HeightMapHelpers.buildHeightMap(cuboid0), List.of(), List.of(), Map.of())
					, new SuspendedCuboid<IReadOnlyCuboidData>(cuboid1, HeightMapHelpers.buildHeightMap(cuboid1), List.of(), List.of(), Map.of())
				)
				, null
				, null
				, null
		);
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(2, snapshot.cuboids().size());
		
		// Request that one of the cuboids be unloaded, run a tick, and verify that it is missing from the snapshot.
		runner.setupChangesForTick(null
			, List.of(address0)
			, null
			, null
		);
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(1, snapshot.cuboids().size());
		Assert.assertTrue(snapshot.cuboids().containsKey(address1));
		
		// Now, unload the last cuboid and check it is empty (also show that redundant requests are ignored).
		runner.setupChangesForTick(null
				, List.of(address1, address1)
				, null
				, null
		);
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		Assert.assertTrue(snapshot.cuboids().isEmpty());
		
		runner.shutdown();
	}

	@Test
	public void fallOnUpdate()
	{
		// Create a single cuboid and load some items into a block.
		// Verify that nothing happens until we change a block adjacent to it, and then it updates.
		WorldConfig config = new WorldConfig();
		TickRunner runner = _createTestRunnerWithConfig(config);
		runner.start();
		
		// We need an entity to generate the change which will trigger the update.
		int entityId = 1;
		MutableEntity mutable = MutableEntity.createForTest(entityId);
		mutable.newInventory.addAllItems(STONE_BRICK_ITEM, 3);
		mutable.setSelectedKey(mutable.newInventory.getIdOfStackableType(STONE_BRICK_ITEM));
		Entity entity = mutable.freeze();
		
		// Load the initial cuboid and run a tick to verify nothing happens.
		AbsoluteLocation startLocation = new AbsoluteLocation(0, 0, 2);
		CuboidAddress address0 = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid0 = CuboidGenerator.createFilledCuboid(address0, ENV.special.AIR);
		cuboid0.setDataSpecial(AspectRegistry.INVENTORY, startLocation.getBlockAddress(), Inventory.start(StationRegistry.CAPACITY_BLOCK_EMPTY).addStackable(STONE_ITEM, 2).finish());
		runner.setupChangesForTick(List.of(new SuspendedCuboid<IReadOnlyCuboidData>(cuboid0, HeightMapHelpers.buildHeightMap(cuboid0), List.of(), List.of(), Map.of()))
				, null
				, List.of(new SuspendedEntity(entity, List.of()))
				, null
		);
		runner.startNextTick();
		TickRunner.Snapshot snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(1, snapshot.cuboids().size());
		Assert.assertEquals(0, snapshot.cuboids().values().iterator().next().scheduledBlockMutations().size());
		Assert.assertEquals(2, snapshot.cuboids().get(address0).completed().getDataSpecial(AspectRegistry.INVENTORY, startLocation.getBlockAddress()).getCount(STONE_ITEM));
		
		// Now, apply a mutation to an adjacent block.
		AbsoluteLocation targetLocation = startLocation.getRelative(1, 0, 0);
		runner.enqueueEntityChange(entityId, new MutationPlaceSelectedBlock(targetLocation, targetLocation), 1L);
		
		// Run a tick and see MutationBlockOverwrite enqueued.
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(1, snapshot.cuboids().size());
		Assert.assertEquals(1, snapshot.cuboids().values().iterator().next().scheduledBlockMutations().size());
		Assert.assertTrue(snapshot.cuboids().get(address0).scheduledBlockMutations().get(0).mutation() instanceof MutationBlockOverwriteByEntity);
		Assert.assertEquals(2, snapshot.cuboids().get(address0).completed().getDataSpecial(AspectRegistry.INVENTORY, startLocation.getBlockAddress()).getCount(STONE_ITEM));
		
		// Run another tick and we shouldn't see anything scheduled (since updated don't go through that path).
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(1, snapshot.cuboids().size());
		Assert.assertEquals(0, snapshot.cuboids().values().iterator().next().scheduledBlockMutations().size());
		Assert.assertEquals(2, snapshot.cuboids().get(address0).completed().getDataSpecial(AspectRegistry.INVENTORY, startLocation.getBlockAddress()).getCount(STONE_ITEM));
		
		// Run another tick where we should see the items start falling.
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(1, snapshot.cuboids().size());
		Assert.assertEquals(1, snapshot.cuboids().values().iterator().next().scheduledBlockMutations().size());
		Assert.assertTrue(snapshot.cuboids().get(address0).scheduledBlockMutations().get(0).mutation() instanceof MutationBlockStoreItems);
		Assert.assertNull(snapshot.cuboids().get(address0).completed().getDataSpecial(AspectRegistry.INVENTORY, startLocation.getBlockAddress()));
		
		runner.shutdown();
	}

	@Test
	public void fallAcrossUnload()
	{
		// We will load a cuboid which already has a falling item and verify that it continues falling across ticks while being unloaded and reloaded.
		TickRunner runner = _createTestRunner();
		runner.start();
		
		// Run the first tick and verify the empty cuboid set.
		runner.startNextTick();
		TickRunner.Snapshot snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(0, snapshot.cuboids().size());
		
		// Load in a cuboid with a suspended mutation to represent the falling.
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		runner.setupChangesForTick(List.of(new SuspendedCuboid<IReadOnlyCuboidData>(cuboid
					, HeightMapHelpers.buildHeightMap(cuboid)
					, List.of()
					, List.of(new ScheduledMutation(new MutationBlockStoreItems(new AbsoluteLocation(10, 10, 30), new Items(STONE_ITEM, 1), null, Inventory.INVENTORY_ASPECT_INVENTORY), 0L))
					, Map.of()
				))
				, null
				, null
				, null
		);
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(1, snapshot.cuboids().size());
		Assert.assertEquals(1, snapshot.cuboids().values().iterator().next().scheduledBlockMutations().size());
		// (since the block was never modified, we won't see the updates, only the next mutation)
		Assert.assertTrue(snapshot.cuboids().get(address).scheduledBlockMutations().get(0).mutation() instanceof MutationBlockStoreItems);
		TickRunner.Snapshot saved = snapshot;
		
		// Unload this cuboid, capturing what is in-progress.
		runner.setupChangesForTick(null
				, List.of(address)
				, null
				, null
		);
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(0, snapshot.cuboids().size());
		
		// Load it back in with the suspended mutation and verify that the item continues to fall.
		runner.setupChangesForTick(List.of(new SuspendedCuboid<IReadOnlyCuboidData>(saved.cuboids().get(address).completed()
					, HeightMapHelpers.buildHeightMap(saved.cuboids().get(address).completed())
					, List.of()
					, saved.cuboids().get(address).scheduledBlockMutations()
					, saved.cuboids().get(address).periodicMutationMillis()
				))
				, null
				, null
				, null
		);
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(1, snapshot.cuboids().size());
		Assert.assertEquals(1, snapshot.cuboids().values().iterator().next().scheduledBlockMutations().size());
		// (since the block was never modified, we won't see the updates, only the next mutation)
		Assert.assertTrue(snapshot.cuboids().values().iterator().next().scheduledBlockMutations().get(0).mutation() instanceof MutationBlockStoreItems);
		
		runner.shutdown();
	}

	@Test
	public void fallIntoLoadedCuboid()
	{
		// Create a world with a single cuboid with some items in the bottom block and run a tick.
		// Then, load another cuboid below it and observe that the items fall into the new cuboid.
		WorldConfig config = new WorldConfig();
		config.shouldSynthesizeUpdatesOnLoad = true;
		TickRunner runner = _createTestRunnerWithConfig(config);
		runner.start();
		
		// Load the initial cuboid and run a tick to verify nothing happens.
		AbsoluteLocation startLocation = new AbsoluteLocation(32, 32, 32);
		CuboidAddress address0 = startLocation.getCuboidAddress();
		CuboidData cuboid0 = CuboidGenerator.createFilledCuboid(address0, ENV.special.AIR);
		cuboid0.setDataSpecial(AspectRegistry.INVENTORY, startLocation.getBlockAddress(), Inventory.start(StationRegistry.CAPACITY_BLOCK_EMPTY).addStackable(STONE_ITEM, 2).finish());
		runner.setupChangesForTick(List.of(new SuspendedCuboid<IReadOnlyCuboidData>(cuboid0, HeightMapHelpers.buildHeightMap(cuboid0), List.of(), List.of(), Map.of()))
				, null
				, null
				, null
		);
		runner.startNextTick();
		TickRunner.Snapshot snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(1, snapshot.cuboids().size());
		Assert.assertEquals(0, snapshot.cuboids().values().iterator().next().scheduledBlockMutations().size());
		Assert.assertEquals(2, snapshot.cuboids().get(address0).completed().getDataSpecial(AspectRegistry.INVENTORY, startLocation.getBlockAddress()).getCount(STONE_ITEM));
		
		// Now, load an air cuboid below this and verify that the items start falling.
		CuboidAddress address1 = address0.getRelative(0, 0, -1);
		CuboidData cuboid1 = CuboidGenerator.createFilledCuboid(address1, ENV.special.AIR);
		runner.setupChangesForTick(List.of(new SuspendedCuboid<IReadOnlyCuboidData>(cuboid1, HeightMapHelpers.buildHeightMap(cuboid1), List.of(), List.of(), Map.of()))
				, null
				, null
				, null
		);
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(2, snapshot.cuboids().size());
		Assert.assertEquals(1, snapshot.cuboids().values().iterator().next().scheduledBlockMutations().size());
		Assert.assertTrue(snapshot.cuboids().get(address1).scheduledBlockMutations().get(0).mutation() instanceof MutationBlockStoreItems);
		Assert.assertNull(snapshot.cuboids().get(address0).completed().getDataSpecial(AspectRegistry.INVENTORY, startLocation.getBlockAddress()));
		
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
		runner.setupChangesForTick(List.of(new SuspendedCuboid<IReadOnlyCuboidData>(cuboid0, HeightMapHelpers.buildHeightMap(cuboid0), List.of(), List.of(), Map.of()))
				, null
				, null
				, null
		);
		runner.startNextTick();
		TickRunner.Snapshot snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(1, snapshot.cuboids().size());
		Assert.assertEquals(0, snapshot.cuboids().values().iterator().next().scheduledBlockMutations().size());
		
		// Now, load an air cuboid below this and verify that the water start falling.
		CuboidAddress address1 = CuboidAddress.fromInt(0, 0, -1);
		CuboidData cuboid1 = CuboidGenerator.createFilledCuboid(address1, ENV.special.AIR);
		runner.setupChangesForTick(List.of(new SuspendedCuboid<IReadOnlyCuboidData>(cuboid1, HeightMapHelpers.buildHeightMap(cuboid1), List.of(), List.of(), Map.of()))
				, null
				, null
				, null
		);
		
		// We should see a layer modified (1024 = 32 * 32) for each of the 32 layers.
		long millisToFlow = ENV.liquids.flowDelayMillis(ENV, WATER_SOURCE);
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
				Assert.assertTrue(snapshot.cuboids().values().stream().filter((TickRunner.SnapshotCuboid cuboid) -> (null != cuboid.blockChanges())).toList().isEmpty());
			}
			
			// Apply the actual movement.
			runner.startNextTick();
			snapshot = runner.waitForPreviousTick();
			Assert.assertFalse(snapshot.cuboids().values().stream().filter((TickRunner.SnapshotCuboid cuboid) -> (null != cuboid.blockChanges())).toList().isEmpty());
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
		runner.setupChangesForTick(List.of(new SuspendedCuboid<IReadOnlyCuboidData>(cascade, HeightMapHelpers.buildHeightMap(cascade), List.of(), List.of(), Map.of()))
				, null
				, List.of(new SuspendedEntity(mutable.freeze(), List.of()))
				, null
		);
		runner.startNextTick();
		TickRunner.Snapshot snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(1, snapshot.cuboids().size());
		Assert.assertEquals(0, snapshot.cuboids().values().iterator().next().scheduledBlockMutations().size());
		
		// Now, break the plug.
		runner.enqueueEntityChange(entityId, new EntityChangeIncrementalBlockBreak(plug, (short)10), 1L);
		// Apply a tick for the entity mutation.
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		Assert.assertNull(snapshot.cuboids().get(address).blockChanges());
		Assert.assertEquals(1, snapshot.stats().committedEntityMutationCount());
		
		// Wait for this to trickle through the cuboid.
		// This will take 33 steps, with some ticks between to allow flow - found experimentally.
		long millisToFlow = ENV.liquids.flowDelayMillis(ENV, WATER_SOURCE);
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
				, List.of(new SuspendedEntity(mutable.freeze(), List.of()))
				, null
		);
		runner.startNextTick();
		TickRunner.Snapshot snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(8, snapshot.cuboids().size());
		Assert.assertEquals(0, snapshot.cuboids().values().stream().filter((TickRunner.SnapshotCuboid cuboid) -> !cuboid.scheduledBlockMutations().isEmpty()).count());
		
		// Now, break the plug.
		runner.enqueueEntityChange(entityId, new EntityChangeIncrementalBlockBreak(plug, (short)10), 1L);
		// Apply a tick for the entity mutation.
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		Assert.assertTrue(snapshot.cuboids().values().stream().filter((TickRunner.SnapshotCuboid cuboid) -> (null != cuboid.blockChanges())).toList().isEmpty());
		Assert.assertEquals(1, snapshot.stats().committedEntityMutationCount());
		
		// Wait for this to trickle through the cuboid.
		// This will take 65 steps, with some ticks between to allow flow - found experimentally.
		long millisToFlow = ENV.liquids.flowDelayMillis(ENV, WATER_SOURCE);
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
				Assert.assertTrue(snapshot.cuboids().values().stream().filter((TickRunner.SnapshotCuboid cuboid) -> (null != cuboid.blockChanges())).toList().isEmpty());
			}
			
			// We should now see the flow.
			runner.startNextTick();
			snapshot = runner.waitForPreviousTick();
			Assert.assertFalse(snapshot.cuboids().values().stream().filter((TickRunner.SnapshotCuboid cuboid) -> (null != cuboid.blockChanges())).toList().isEmpty());
		}
		
		// We should now be done.
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		Assert.assertTrue(snapshot.cuboids().values().stream().filter((TickRunner.SnapshotCuboid cuboid) -> (null != cuboid.blockChanges())).toList().isEmpty());
		
		runner.shutdown();
	}

	@Test
	public void waterFlowOnBlockBreakOnly()
	{
		// We want to verify that block updates don't happen for things like damage updates so we place a water source,
		// a gap, and a stone, then incrementally break it.  We should only see the update once the block breaks.
		WorldConfig config = new WorldConfig();
		TickRunner runner = _createTestRunnerWithConfig(config);
		runner.start();
		CuboidAddress address = CuboidAddress.fromInt(-3, -4, -5);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		AbsoluteLocation stoneLocation = address.getBase().getRelative(5, 5, 0);
		AbsoluteLocation waterLocation = stoneLocation.getRelative(-2, 0, 0);
		AbsoluteLocation emptyLocation = stoneLocation.getRelative(-1, 0, 0);
		cuboid.setData15(AspectRegistry.BLOCK, stoneLocation.getBlockAddress(), PLANK_ITEM.number());
		cuboid.setData15(AspectRegistry.BLOCK, waterLocation.getBlockAddress(), WATER_SOURCE.item().number());
		
		int entityId = 1;
		MutableEntity mutable = MutableEntity.createForTest(entityId);
		mutable.newLocation = new EntityLocation(stoneLocation.x(), stoneLocation.y() - 1, stoneLocation.z());
		Entity entity = mutable.freeze();
		runner.setupChangesForTick(List.of(new SuspendedCuboid<IReadOnlyCuboidData>(cuboid, HeightMapHelpers.buildHeightMap(cuboid), List.of(), List.of(), Map.of()))
				, null
				, List.of(new SuspendedEntity(entity, List.of()))
				, null
		);
		runner.startNextTick();
		TickRunner.Snapshot snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(1, snapshot.cuboids().size());
		Assert.assertEquals(0, snapshot.cuboids().values().iterator().next().scheduledBlockMutations().size());
		
		// Send an incremental update to break the stone, but only partially.
		long nextCommit = _applyIncrementalBreaks(runner, 1L, entityId, stoneLocation, (short)100);
		snapshot = runner.waitForPreviousTick();
		// (we should see the update scheduled and previous tick damage change (assuming this was multiple ticks to break)).
		Assert.assertEquals(1, snapshot.cuboids().values().iterator().next().scheduledBlockMutations().size());
		Assert.assertEquals(1, snapshot.cuboids().values().iterator().next().blockChanges().size());
		Assert.assertEquals(ENV.special.AIR.item().number(), snapshot.cuboids().get(address).completed().getData15(AspectRegistry.BLOCK, emptyLocation.getBlockAddress()));
		
		// Let that mutation apply and verify the updated damage but no other change.
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		// (we should see the damage change go through).
		Assert.assertEquals(0, snapshot.cuboids().values().iterator().next().scheduledBlockMutations().size());
		Assert.assertEquals(1, snapshot.cuboids().values().iterator().next().blockChanges().size());
		Assert.assertEquals(ENV.special.AIR.item().number(), snapshot.cuboids().get(address).completed().getData15(AspectRegistry.BLOCK, emptyLocation.getBlockAddress()));
		Assert.assertEquals((short)100, snapshot.cuboids().get(address).completed().getData15(AspectRegistry.DAMAGE, stoneLocation.getBlockAddress()));
		
		// Apply the second break attempt, which should break it.
		_applyIncrementalBreaks(runner, nextCommit, entityId, stoneLocation, (short)100);
		snapshot = runner.waitForPreviousTick();
		// (we should see the update scheduled and previous tick damage change (assuming this was multiple ticks to break)).
		Assert.assertEquals(1, snapshot.cuboids().values().iterator().next().scheduledBlockMutations().size());
		Assert.assertEquals(1, snapshot.cuboids().values().iterator().next().blockChanges().size());
		Assert.assertEquals(ENV.special.AIR.item().number(), snapshot.cuboids().get(address).completed().getData15(AspectRegistry.BLOCK, emptyLocation.getBlockAddress()));
		
		// Let that mutation apply and verify that the block is broken (we won't see the update apply until the next tick).
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		// (we should see the update scheduled, but no change).
		Assert.assertEquals(0, snapshot.cuboids().values().iterator().next().scheduledBlockMutations().size());
		Assert.assertEquals(1, snapshot.cuboids().values().iterator().next().blockChanges().size());
		Assert.assertEquals(ENV.special.AIR.item().number(), snapshot.cuboids().get(address).completed().getData15(AspectRegistry.BLOCK, emptyLocation.getBlockAddress()));
		Assert.assertEquals(ENV.special.AIR.item().number(), snapshot.cuboids().get(address).completed().getData15(AspectRegistry.BLOCK, stoneLocation.getBlockAddress()));
		
		// Run the tick which will trigger the block update, thus causing the water to flow.
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		
		// We need to wait for some number of ticks before the flow is ready to happen.
		long millisToFlow = ENV.liquids.flowDelayMillis(ENV, WATER_SOURCE);
		int ticksToPass = (int)(millisToFlow / MILLIS_PER_TICK);
		for (int j = 0; j < ticksToPass; ++j)
		{
			// (we should be waiting for the update to run on each pass)
			Assert.assertEquals(1, snapshot.cuboids().values().iterator().next().scheduledBlockMutations().size());
			runner.startNextTick();
			snapshot = runner.waitForPreviousTick();
			Assert.assertTrue(snapshot.cuboids().values().stream().filter((TickRunner.SnapshotCuboid snapCuboid) -> (null != snapCuboid.blockChanges())).toList().isEmpty());
			Assert.assertEquals(ENV.special.AIR.item().number(), snapshot.cuboids().get(address).completed().getData15(AspectRegistry.BLOCK, emptyLocation.getBlockAddress()));
		}
		
		// We now allow the update to finally happen.
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		// (we should see the update scheduled, but no change).
		Assert.assertEquals(0, snapshot.cuboids().values().iterator().next().scheduledBlockMutations().size());
		Assert.assertEquals(1, snapshot.cuboids().values().iterator().next().blockChanges().size());
		Assert.assertEquals(WATER_STRONG.number(), snapshot.cuboids().get(address).completed().getData15(AspectRegistry.BLOCK, emptyLocation.getBlockAddress()));
		Assert.assertEquals(ENV.special.AIR.item().number(), snapshot.cuboids().get(address).completed().getData15(AspectRegistry.BLOCK, stoneLocation.getBlockAddress()));
		
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
		int entityId = 1;
		runner.setupChangesForTick(List.of(_packageCuboid(cuboid)
					, _packageCuboid(otherCuboid0)
					, _packageCuboid(otherCuboid1)
				)
				, null
				, List.of(_createFreshEntity(entityId))
				, null
		);
		runner.start();
		runner.waitForPreviousTick();
		// Enqueue the mutations to replace these 2 blocks (this mutation is just for testing and doesn't use the inventory or location).
		// The mutation will be run in the next tick since there isn't one running.
		runner.enqueueEntityChange(entityId, new EntityChangeMutation(new ReplaceBlockMutation(lanternLocation, ENV.special.AIR.item().number(), LANTERN_ITEM.number())), 1L);
		runner.enqueueEntityChange(entityId, new EntityChangeMutation(new ReplaceBlockMutation(stoneLocation, ENV.special.AIR.item().number(), STONE_ITEM.number())), 1L);
		runner.startNextTick();
		
		// (run an extra tick to unwrap the entity change)
		TickRunner.Snapshot snapshot = runner.startNextTick();
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
		
		int[] randomHolder = new int[] {0};
		TickRunner runner = new TickRunner(ServerRunner.TICK_RUNNER_THREAD_COUNT
				, MILLIS_PER_TICK
				, null
				, (int bound) -> randomHolder[0] % bound
				, (TickRunner.Snapshot completed) -> {}
				, new WorldConfig()
		);
		int entityId = 1;
		MutableEntity mutable = MutableEntity.createForTest(entityId);
		mutable.newLocation = new EntityLocation(location.x() + 1, location.y(), location.z());
		mutable.newInventory.addAllItems(SAPLING_ITEM, 1);
		mutable.setSelectedKey(mutable.newInventory.getIdOfStackableType(SAPLING_ITEM));
		Entity entity = mutable.freeze();
		runner.setupChangesForTick(List.of(new SuspendedCuboid<IReadOnlyCuboidData>(cuboid, HeightMapHelpers.buildHeightMap(cuboid), List.of(), List.of(), Map.of())
				)
				, null
				, List.of(new SuspendedEntity(entity, List.of()))
				, null
		);
		runner.start();
		runner.waitForPreviousTick();
		runner.enqueueEntityChange(entityId, new MutationPlaceSelectedBlock(location, location), 1L);
		runner.startNextTick();
		
		// (run an extra tick to unwrap the entity change)
		TickRunner.Snapshot snapshot = runner.startNextTick();
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
		cuboid.setData15(AspectRegistry.BLOCK, location.getRelative(0, 0, -1).getBlockAddress(), DIRT_ITEM.number());
		
		int[] randomHolder = new int[] {0};
		WorldConfig config = new WorldConfig();
		config.difficulty = Difficulty.PEACEFUL;
		TickRunner runner = new TickRunner(ServerRunner.TICK_RUNNER_THREAD_COUNT
				, MILLIS_PER_TICK
				, null
				, (int bound) -> randomHolder[0] % bound
				, (TickRunner.Snapshot completed) -> {}
				, config
		);
		int entityId = 1;
		MutableEntity mutable = MutableEntity.createForTest(entityId);
		mutable.newLocation = location.toEntityLocation();
		mutable.newInventory.addAllItems(WHEAT_SEED_ITEM, 1);
		mutable.setSelectedKey(mutable.newInventory.getIdOfStackableType(WHEAT_SEED_ITEM));
		Entity entity = mutable.freeze();
		runner.setupChangesForTick(List.of(new SuspendedCuboid<IReadOnlyCuboidData>(cuboid, HeightMapHelpers.buildHeightMap(cuboid), List.of(), List.of(), Map.of())
				)
				, null
				, List.of(new SuspendedEntity(entity, List.of()))
				, null
		);
		runner.start();
		runner.waitForPreviousTick();
		runner.enqueueEntityChange(entityId, new MutationPlaceSelectedBlock(location, location), 1L);
		runner.startNextTick();
		
		// (run an extra tick to unwrap the entity change)
		TickRunner.Snapshot snapshot = runner.startNextTick();
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
		_applyIncrementalBreaks(runner, 1L, entityId, location, (short)20);
		
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
	public void breakGrowingWheat()
	{
		// Plant a seed and then break the block under it, see that a seed drops, and run for a few cycles to make sure the delayed growth tick is ok.
		CuboidAddress address = CuboidAddress.fromInt(7, 8, 9);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		AbsoluteLocation location = address.getBase().getRelative(0, 6, 7);
		AbsoluteLocation dirtLocation = location.getRelative(0, 0, -1);
		cuboid.setData15(AspectRegistry.BLOCK, dirtLocation.getBlockAddress(), DIRT_ITEM.number());
		cuboid.setData15(AspectRegistry.BLOCK, dirtLocation.getRelative(0, 0, -1).getBlockAddress(), STONE_ITEM.number());
		
		WorldConfig config = new WorldConfig();
		TickRunner runner = _createTestRunnerWithConfig(config);
		int entityId = 1;
		MutableEntity mutable = MutableEntity.createForTest(entityId);
		mutable.newLocation = new EntityLocation(location.x() + 1, location.y(), location.z());
		mutable.newInventory.addAllItems(WHEAT_SEED_ITEM, 1);
		mutable.setSelectedKey(mutable.newInventory.getIdOfStackableType(WHEAT_SEED_ITEM));
		Entity entity = mutable.freeze();
		runner.setupChangesForTick(List.of(new SuspendedCuboid<IReadOnlyCuboidData>(cuboid, HeightMapHelpers.buildHeightMap(cuboid), List.of(), List.of(), Map.of())
				)
				, null
				, List.of(new SuspendedEntity(entity, List.of()))
				, null
		);
		runner.start();
		runner.waitForPreviousTick();
		long nextCommit = 1L;
		runner.enqueueEntityChange(entityId, new MutationPlaceSelectedBlock(location, location), nextCommit);
		nextCommit += 1L;
		runner.startNextTick();
		
		// (run an extra tick to unwrap the entity change)
		TickRunner.Snapshot snapshot = runner.startNextTick();
		Assert.assertEquals(1, snapshot.stats().committedEntityMutationCount());
		
		snapshot = runner.waitForPreviousTick();
		// We should see the seed for one tick before it grows.
		Assert.assertEquals(1, snapshot.stats().committedCuboidMutationCount());
		Assert.assertEquals(WHEAT_SEEDLING_ITEM.number(), snapshot.cuboids().get(address).completed().getData15(AspectRegistry.BLOCK, location.getBlockAddress()));
		
		// Now, creak the dirt block.
		nextCommit = _applyIncrementalBreaks(runner, nextCommit, entityId, dirtLocation, (short)200);
		snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(1, snapshot.stats().committedEntityMutationCount());
		
		// Run another tick and see the dirt break but not yet the seedling.
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(1, snapshot.stats().committedCuboidMutationCount());
		Assert.assertEquals(ENV.special.AIR.item().number(), snapshot.cuboids().get(address).completed().getData15(AspectRegistry.BLOCK, dirtLocation.getBlockAddress()));
		Assert.assertEquals(WHEAT_SEEDLING_ITEM.number(), snapshot.cuboids().get(address).completed().getData15(AspectRegistry.BLOCK, location.getBlockAddress()));
		
		// Run another tick and see the seedling break.
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(1, snapshot.stats().committedCuboidMutationCount());
		Assert.assertEquals(ENV.special.AIR.item().number(), snapshot.cuboids().get(address).completed().getData15(AspectRegistry.BLOCK, location.getBlockAddress()));
		
		// The item should be in the entity inventory, not the ground.
		Inventory blockInventory = snapshot.cuboids().get(address).completed().getDataSpecial(AspectRegistry.INVENTORY, dirtLocation.getBlockAddress());
		Assert.assertNull(blockInventory);
		Inventory entityInventory = snapshot.entities().get(entityId).completed().inventory();
		Assert.assertEquals(1, entityInventory.sortedKeys().size());
		Assert.assertEquals(1, entityInventory.getCount(DIRT_ITEM));
		
		// Run another tick to see the seed drop into this inventory.
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(1, snapshot.stats().committedCuboidMutationCount());
		blockInventory = snapshot.cuboids().get(address).completed().getDataSpecial(AspectRegistry.INVENTORY, dirtLocation.getBlockAddress());
		Assert.assertEquals(1, blockInventory.sortedKeys().size());
		Assert.assertEquals(1, blockInventory.getCount(WHEAT_SEED_ITEM));
		
		// Now, just run for another hundred ticks to make sure nothing goes wrong.
		for (int i = 0; i < 100; ++i)
		{
			runner.startNextTick();
			snapshot = runner.waitForPreviousTick();
		}
		blockInventory = snapshot.cuboids().get(address).completed().getDataSpecial(AspectRegistry.INVENTORY, dirtLocation.getBlockAddress());
		Assert.assertEquals(1, blockInventory.sortedKeys().size());
		Assert.assertEquals(1, blockInventory.getCount(WHEAT_SEED_ITEM));
		
		runner.shutdown();
	}

	@Test
	public void attackCreature()
	{
		// Load a cuboid with a creature on it and an entity.  Verify that the entity can hit the creature multiple times and that the despawn is correctly reflected.
		CuboidAddress address = CuboidAddress.fromInt(7, 8, 9);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		AbsoluteLocation spawn = address.getBase().getRelative(0, 6, 7);
		cuboid.setData15(AspectRegistry.BLOCK, spawn.getRelative(0, 0, -1).getBlockAddress(), DIRT_ITEM.number());
		EntityLocation entityLocation = spawn.toEntityLocation();
		int creatureId = -1;
		CreatureEntity creature = CreatureEntity.create(creatureId, ENV.creatures.COW, entityLocation, (byte)15);
		
		TickRunner runner = _createTestRunner();
		int entityId = 1;
		MutableEntity mutable = MutableEntity.createForTest(entityId);
		mutable.newLocation = entityLocation;
		mutable.newInventory.addNonStackableAllowingOverflow(new NonStackableItem(ENV.items.getItemById("op.iron_sword"), 1000));
		mutable.setSelectedKey(1);
		Entity entity = mutable.freeze();
		runner.setupChangesForTick(List.of(new SuspendedCuboid<IReadOnlyCuboidData>(cuboid, HeightMapHelpers.buildHeightMap(cuboid), List.of(creature), List.of(), Map.of())
				)
				, null
				, List.of(new SuspendedEntity(entity, List.of()))
				, null
		);
		runner.start();
		// Skip a few ticks until the system has warmed up to the point where we can use a weapon.
		for (int i = 0; i < (EntityChangeAttackEntity.ATTACK_COOLDOWN_MILLIS / MILLIS_PER_TICK); ++i)
		{
			runner.startNextTick();
		}
		runner.waitForPreviousTick();
		runner.enqueueEntityChange(entityId, new EntityChangeAttackEntity(creatureId), 1L);
		runner.startNextTick();
		
		// (run an extra tick to unwrap the entity change)
		TickRunner.Snapshot snapshot = runner.startNextTick();
		Assert.assertEquals(1, snapshot.stats().committedEntityMutationCount());
		snapshot = runner.waitForPreviousTick();
		
		// We should see the creature take damage.
		CreatureEntity updated = snapshot.creatures().get(creatureId).visiblyChanged();
		Assert.assertEquals((byte)5, updated.health());
		
		// Hitting them again should cause them to de-spawn and drop items.
		for (int i = 0; i < (EntityChangeAttackEntity.ATTACK_COOLDOWN_MILLIS / MILLIS_PER_TICK); ++i)
		{
			runner.startNextTick();
		}
		runner.waitForPreviousTick();
		runner.enqueueEntityChange(entityId, new EntityChangeAttackEntity(creatureId), 2L);
		runner.startNextTick();
		
		// (run an extra tick to unwrap the entity change)
		snapshot = runner.startNextTick();
		Assert.assertEquals(1, snapshot.stats().committedEntityMutationCount());
		snapshot = runner.waitForPreviousTick();
		
		// We should see the creature is now gone.
		Assert.assertNull(snapshot.creatures().get(creatureId));
		
		// Run another tick and we should see their drops.
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(1, snapshot.stats().committedCuboidMutationCount());
		Inventory blockInventory = snapshot.cuboids().get(address).completed().getDataSpecial(AspectRegistry.INVENTORY, spawn.getBlockAddress());
		Assert.assertEquals(1, blockInventory.sortedKeys().size());
		Assert.assertEquals(5, blockInventory.getCount(ENV.items.getItemById("op.beef")));
		
		runner.shutdown();
	}

	@Test
	public void fallingCreature()
	{
		// Load anair cuboid with a creature in it and verify that it falls as time passes.
		CuboidAddress address = CuboidAddress.fromInt(7, 8, 9);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		AbsoluteLocation spawn = address.getBase().getRelative(0, 6, 7);
		EntityLocation entityLocation = spawn.toEntityLocation();
		int creatureId = -1;
		CreatureEntity creature = CreatureEntity.create(creatureId, ENV.creatures.COW, entityLocation, (byte)15);
		
		TickRunner runner = _createTestRunner();
		runner.setupChangesForTick(List.of(new SuspendedCuboid<IReadOnlyCuboidData>(cuboid, HeightMapHelpers.buildHeightMap(cuboid), List.of(creature), List.of(), Map.of())
				)
				, null
				, null
				, null
		);
		runner.start();
		runner.waitForPreviousTick();
		runner.startNextTick();
		
		// Pass some time and observe the creature movement.
		TickRunner.Snapshot snapshot = _passSomeTime(runner, 100L);
		
		// See where the entity is.
		CreatureEntity updated = snapshot.creatures().get(creatureId).completed();
		Assert.assertEquals(new EntityLocation(224.0f, 262.0f, 294.951f), updated.location());
		Assert.assertEquals(-1.0f, updated.velocity().z(), 0.01f);
		
		// Pass some more time and see it move.
		snapshot = _passSomeTime(runner, 100L);
		updated = snapshot.creatures().get(creatureId).completed();
		Assert.assertEquals(new EntityLocation(224.0f, 262.0f, 294.804f), updated.location());
		Assert.assertEquals(-2.0f, updated.velocity().z(), 0.01f);
		
		runner.shutdown();
	}

	@Test
	public void lampSwitch()
	{
		// Place a lamp and switch in the world, activate the switch, then observe the lamp change of state and lighting update.
		CuboidAddress address = CuboidAddress.fromInt(7, 8, 9);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		Item lampOff = ENV.items.getItemById("op.lamp_off");
		Item lampOn = ENV.items.getItemById("op.lamp_on");
		Item switchOff = ENV.items.getItemById("op.switch_off");
		Item switchOn = ENV.items.getItemById("op.switch_on");
		Item logicWire = ENV.items.getItemById("op.logic_wire");
		AbsoluteLocation lampLocation = address.getBase().getRelative(5, 6, 6);
		AbsoluteLocation wireLocation = lampLocation.getRelative(0, 0, 1);
		AbsoluteLocation switchLocation = wireLocation.getRelative(0, 0, 1);
		AbsoluteLocation stoneLocation = address.getBase().getRelative(6, 6, 6);
		cuboid.setData15(AspectRegistry.BLOCK, lampLocation.getBlockAddress(), lampOff.number());
		cuboid.setData15(AspectRegistry.BLOCK, wireLocation.getBlockAddress(), logicWire.number());
		cuboid.setData15(AspectRegistry.BLOCK, switchLocation.getBlockAddress(), switchOff.number());
		cuboid.setData15(AspectRegistry.BLOCK, stoneLocation.getBlockAddress(), STONE_ITEM.number());
		
		TickRunner runner = _createTestRunner();
		int entityId = 1;
		MutableEntity mutable = MutableEntity.createForTest(entityId);
		mutable.newLocation = new EntityLocation(stoneLocation.x(), stoneLocation.y(), stoneLocation.z() + 1.0f);
		Entity entity = mutable.freeze();
		runner.setupChangesForTick(List.of(new SuspendedCuboid<IReadOnlyCuboidData>(cuboid, HeightMapHelpers.buildHeightMap(cuboid), List.of(), List.of(), Map.of())
				)
				, null
				, List.of(new SuspendedEntity(entity, List.of()))
				, null
		);
		runner.start();
		runner.waitForPreviousTick();
		
		// Enqueue the mutation to change the state of the switch.
		EntityChangeSetBlockLogicState setSwitch = new EntityChangeSetBlockLogicState(switchLocation, true);
		runner.enqueueEntityChange(entityId, setSwitch, 1L);
		runner.startNextTick();
		
		// (run an extra tick to apply the change to the block)
		TickRunner.Snapshot snapshot = runner.startNextTick();
		Assert.assertEquals(1, snapshot.stats().committedEntityMutationCount());
		
		// At the end of this next tick, we should see the switch state changed, but not yet the lamp.
		snapshot = runner.startNextTick();
		Assert.assertEquals(lampOff.number(), snapshot.cuboids().get(address).completed().getData15(AspectRegistry.BLOCK, lampLocation.getBlockAddress()));
		Assert.assertEquals(switchOn.number(), snapshot.cuboids().get(address).completed().getData15(AspectRegistry.BLOCK, switchLocation.getBlockAddress()));
		Assert.assertEquals(0, snapshot.cuboids().get(address).completed().getData7(AspectRegistry.LOGIC, wireLocation.getBlockAddress()));
		
		// After another tick, the logic update should go through but not yet the replacement call.
		snapshot = runner.startNextTick();
		Assert.assertEquals(lampOff.number(), snapshot.cuboids().get(address).completed().getData15(AspectRegistry.BLOCK, lampLocation.getBlockAddress()));
		Assert.assertEquals(switchOn.number(), snapshot.cuboids().get(address).completed().getData15(AspectRegistry.BLOCK, switchLocation.getBlockAddress()));
		Assert.assertEquals(LogicAspect.MAX_LEVEL, snapshot.cuboids().get(address).completed().getData7(AspectRegistry.LOGIC, switchLocation.getBlockAddress()));
		Assert.assertEquals(LogicAspect.MAX_LEVEL - 1, snapshot.cuboids().get(address).completed().getData7(AspectRegistry.LOGIC, wireLocation.getBlockAddress()));
		
		// After the next tick, the lamp should have turned on but the lighting won't yet change.
		snapshot = runner.startNextTick();
		// (this takes 2 ticks:  One to turn the switch on and one to detect the update).
		Assert.assertEquals(lampOff.number(), snapshot.cuboids().get(address).completed().getData15(AspectRegistry.BLOCK, lampLocation.getBlockAddress()));
		snapshot = runner.startNextTick();
		Assert.assertEquals(lampOn.number(), snapshot.cuboids().get(address).completed().getData15(AspectRegistry.BLOCK, lampLocation.getBlockAddress()));
		Assert.assertEquals(switchOn.number(), snapshot.cuboids().get(address).completed().getData15(AspectRegistry.BLOCK, switchLocation.getBlockAddress()));
		Assert.assertEquals(0, snapshot.cuboids().get(address).completed().getData7(AspectRegistry.LIGHT, lampLocation.getBlockAddress()));
		
		// After one more tick, we should see the lighting update.
		snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(15, snapshot.cuboids().get(address).completed().getData7(AspectRegistry.LIGHT, lampLocation.getBlockAddress()));
		
		runner.shutdown();
	}

	@Test
	public void breakLogicWire()
	{
		// Place an activated switch and some wire in the world, verify that the logic propagates, then break the wire and show that the wire goes dead.
		CuboidAddress address = CuboidAddress.fromInt(7, 8, 9);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		Item switchOff = ENV.items.getItemById("op.switch_off");
		Item switchOn = ENV.items.getItemById("op.switch_on");
		Item logicWire = ENV.items.getItemById("op.logic_wire");
		AbsoluteLocation stoneLocation = address.getBase().getRelative(6, 6, 6);
		AbsoluteLocation switchLocation = stoneLocation.getRelative(0, 0, 1);
		AbsoluteLocation wire1Location = switchLocation.getRelative(0, 0, 1);
		AbsoluteLocation wire2Location = switchLocation.getRelative(0, 0, 2);
		AbsoluteLocation wire3Location = switchLocation.getRelative(0, 0, 3);
		cuboid.setData15(AspectRegistry.BLOCK, switchLocation.getBlockAddress(), switchOff.number());
		cuboid.setData15(AspectRegistry.BLOCK, wire1Location.getBlockAddress(), logicWire.number());
		cuboid.setData15(AspectRegistry.BLOCK, wire2Location.getBlockAddress(), logicWire.number());
		cuboid.setData15(AspectRegistry.BLOCK, wire3Location.getBlockAddress(), logicWire.number());
		cuboid.setData15(AspectRegistry.BLOCK, stoneLocation.getBlockAddress(), STONE_ITEM.number());
		
		TickRunner runner = _createTestRunner();
		int entityId = 1;
		MutableEntity mutable = MutableEntity.createForTest(entityId);
		mutable.newLocation = new EntityLocation(stoneLocation.x(), stoneLocation.y(), stoneLocation.z() + 1.0f);
		mutable.isCreativeMode = true;
		Entity entity = mutable.freeze();
		runner.setupChangesForTick(List.of(new SuspendedCuboid<IReadOnlyCuboidData>(cuboid, HeightMapHelpers.buildHeightMap(cuboid), List.of(), List.of(), Map.of())
				)
				, null
				, List.of(new SuspendedEntity(entity, List.of()))
				, null
		);
		runner.start();
		runner.waitForPreviousTick();
		
		// Enqueue the mutation to change the state of the switch.
		EntityChangeSetBlockLogicState setSwitch = new EntityChangeSetBlockLogicState(switchLocation, true);
		runner.enqueueEntityChange(entityId, setSwitch, 1L);
		runner.startNextTick();
		
		// (run an extra tick to apply the change to the block)
		TickRunner.Snapshot snapshot = runner.startNextTick();
		Assert.assertEquals(1, snapshot.stats().committedEntityMutationCount());
		
		// At the end of this next tick, we should see the switch state changed.
		snapshot = runner.startNextTick();
		Assert.assertEquals(switchOn.number(), snapshot.cuboids().get(address).completed().getData15(AspectRegistry.BLOCK, switchLocation.getBlockAddress()));
		Assert.assertEquals(0, snapshot.cuboids().get(address).completed().getData7(AspectRegistry.LOGIC, wire1Location.getBlockAddress()));
		
		// After another tick, the logic update should go through the wires.
		snapshot = runner.startNextTick();
		Assert.assertEquals(switchOn.number(), snapshot.cuboids().get(address).completed().getData15(AspectRegistry.BLOCK, switchLocation.getBlockAddress()));
		Assert.assertEquals(LogicAspect.MAX_LEVEL, snapshot.cuboids().get(address).completed().getData7(AspectRegistry.LOGIC, switchLocation.getBlockAddress()));
		Assert.assertEquals(LogicAspect.MAX_LEVEL - 1, snapshot.cuboids().get(address).completed().getData7(AspectRegistry.LOGIC, wire1Location.getBlockAddress()));
		Assert.assertEquals(LogicAspect.MAX_LEVEL - 2, snapshot.cuboids().get(address).completed().getData7(AspectRegistry.LOGIC, wire2Location.getBlockAddress()));
		Assert.assertEquals(LogicAspect.MAX_LEVEL - 3, snapshot.cuboids().get(address).completed().getData7(AspectRegistry.LOGIC, wire3Location.getBlockAddress()));
		
		// Break one of these wires.
		runner.waitForPreviousTick();
		EntityChangeIncrementalBlockBreak break1 = new EntityChangeIncrementalBlockBreak(wire1Location, (short) 10);
		runner.enqueueEntityChange(entityId, break1, 2L);
		runner.startNextTick();
		
		// (run an extra tick to apply the change to the block)
		snapshot = runner.startNextTick();
		Assert.assertEquals(1, snapshot.stats().committedEntityMutationCount());
		
		// At the end of this next tick, we should see the wire broken but no logic changes.
		snapshot = runner.startNextTick();
		Assert.assertEquals(ENV.special.AIR.item().number(), snapshot.cuboids().get(address).completed().getData15(AspectRegistry.BLOCK, wire1Location.getBlockAddress()));
		Assert.assertEquals(LogicAspect.MAX_LEVEL, snapshot.cuboids().get(address).completed().getData7(AspectRegistry.LOGIC, switchLocation.getBlockAddress()));
		Assert.assertEquals(LogicAspect.MAX_LEVEL - 1, snapshot.cuboids().get(address).completed().getData7(AspectRegistry.LOGIC, wire1Location.getBlockAddress()));
		Assert.assertEquals(LogicAspect.MAX_LEVEL - 2, snapshot.cuboids().get(address).completed().getData7(AspectRegistry.LOGIC, wire2Location.getBlockAddress()));
		Assert.assertEquals(LogicAspect.MAX_LEVEL - 3, snapshot.cuboids().get(address).completed().getData7(AspectRegistry.LOGIC, wire3Location.getBlockAddress()));
		
		// After another tick, the logic wire should be dead.
		snapshot = runner.startNextTick();
		Assert.assertEquals(LogicAspect.MAX_LEVEL, snapshot.cuboids().get(address).completed().getData7(AspectRegistry.LOGIC, switchLocation.getBlockAddress()));
		Assert.assertEquals(0, snapshot.cuboids().get(address).completed().getData7(AspectRegistry.LOGIC, wire1Location.getBlockAddress()));
		Assert.assertEquals(0, snapshot.cuboids().get(address).completed().getData7(AspectRegistry.LOGIC, wire2Location.getBlockAddress()));
		Assert.assertEquals(0, snapshot.cuboids().get(address).completed().getData7(AspectRegistry.LOGIC, wire3Location.getBlockAddress()));
		
		runner.shutdown();
	}

	@Test
	public void operatorSetCreative()
	{
		// Define an entity and show that we can set their creative flag with an operator command.
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		
		// Create a tick runner with a single cuboid and get it running.
		TickRunner runner = _createTestRunner();
		int entityId = 1;
		runner.setupChangesForTick(List.of(new SuspendedCuboid<IReadOnlyCuboidData>(cuboid, HeightMapHelpers.buildHeightMap(cuboid), List.of(), List.of(), Map.of()))
				, null
				, List.of(_createFreshEntity(entityId))
				, null
		);
		runner.start();
		
		runner.startNextTick();
		TickRunner.Snapshot snapshot = runner.waitForPreviousTick();
		Assert.assertFalse(snapshot.entities().get(entityId).completed().isCreativeMode());
		
		// Enqueue the operator command, run the tick, and observe this change.
		runner.enqueueOperatorMutation(entityId, new EntityChangeOperatorSetCreative(true));
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		Assert.assertTrue(snapshot.entities().get(entityId).completed().isCreativeMode());
		
		// Verify that it can be cleared.
		runner.enqueueOperatorMutation(entityId, new EntityChangeOperatorSetCreative(false));
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		Assert.assertFalse(snapshot.entities().get(entityId).completed().isCreativeMode());
		
		runner.shutdown();
	}

	@Test
	public void growAfterFirstLoad()
	{
		// Verifies that we don't NPE when running a growth tick on a cuboid which was just loaded and doesn't yet have its height map.
		CuboidAddress address = CuboidAddress.fromInt(7, 8, 9);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		AbsoluteLocation location = address.getBase().getRelative(0, 6, 7);
		AbsoluteLocation dirtLocation = location.getRelative(0, 0, -1);
		cuboid.setData15(AspectRegistry.BLOCK, dirtLocation.getBlockAddress(), DIRT_ITEM.number());
		cuboid.setData15(AspectRegistry.BLOCK, location.getBlockAddress(), WHEAT_SEEDLING_ITEM.number());
		
		WorldConfig config = new WorldConfig();
		TickRunner runner = _createTestRunnerWithConfig(config);
		runner.setupChangesForTick(List.of(new SuspendedCuboid<IReadOnlyCuboidData>(cuboid, HeightMapHelpers.buildHeightMap(cuboid), List.of(), List.of(), Map.of(location.getBlockAddress(), 0L))
				)
				, null
				, null
				, null
		);
		runner.start();
		runner.waitForPreviousTick();
		runner.startNextTick();
		TickRunner.Snapshot snapshot = runner.waitForPreviousTick();
		
		// If there was a problem, the tick runner would have crashed.
		Assert.assertEquals(1, snapshot.stats().committedCuboidMutationCount());
		runner.shutdown();
	}

	@Test
	public void futureMutation()
	{
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, STONE);
		TickRunner runner = _createTestRunner();
		int entityId = 1;
		runner.setupChangesForTick(List.of(new SuspendedCuboid<IReadOnlyCuboidData>(cuboid, HeightMapHelpers.buildHeightMap(cuboid), List.of(), List.of(), Map.of()))
				, null
				, List.of(_createFreshEntity(entityId))
				, null
		);
		runner.start();
		runner.waitForPreviousTick();
		
		// We just enqueue a future mutation to do basic damage.
		AbsoluteLocation target = new AbsoluteLocation(16, 16, 16);
		short damage = 100;
		long delayMillis = 2L * MILLIS_PER_TICK - 1L;
		MutationBlockIncrementalBreak takeDamage = new MutationBlockIncrementalBreak(target, damage, MutationBlockIncrementalBreak.NO_STORAGE_ENTITY);
		runner.enqueueEntityChange(entityId, new EntityChangeFutureBlock(takeDamage, delayMillis), 1L);
		
		// We enqueued this before the tick started so it will run immediately
		// Run the tick to receive the change, then another to apply it.
		runner.startNextTick();
		TickRunner.Snapshot snap = runner.waitForPreviousTick();
		Assert.assertEquals(1, snap.stats().committedEntityMutationCount());
		
		// We need to pass time until the remaining delay reaches 0 and it is set for just under 2 ticks so we expect 2 ticks with nothing happening.
		runner.startNextTick();
		snap = runner.waitForPreviousTick();
		Assert.assertEquals(0, snap.stats().committedCuboidMutationCount());
		runner.startNextTick();
		snap = runner.waitForPreviousTick();
		Assert.assertEquals(0, snap.stats().committedCuboidMutationCount());
		Assert.assertEquals(0, snap.cuboids().get(address).completed().getData15(AspectRegistry.DAMAGE, target.getBlockAddress()));
		runner.startNextTick();
		snap = runner.waitForPreviousTick();
		Assert.assertEquals(1, snap.stats().committedCuboidMutationCount());
		Assert.assertEquals(damage, snap.cuboids().get(address).completed().getData15(AspectRegistry.DAMAGE, target.getBlockAddress()));
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
					new SuspendedCuboid<IReadOnlyCuboidData>(platform, HeightMapHelpers.buildHeightMap(platform), List.of(), List.of(), Map.of())
					, new SuspendedCuboid<IReadOnlyCuboidData>(openSpace, HeightMapHelpers.buildHeightMap(openSpace), List.of(), List.of(
							new ScheduledMutation(placeWater, 0L)
							, new ScheduledMutation(placeLava, 0L)
					), Map.of())
				)
				, null
				, null
				, null
		);
		runner.startNextTick();
		TickRunner.Snapshot snapshot = runner.waitForPreviousTick();
		
		// Wait for lava to flow twice.
		long millisToFlow = ENV.liquids.flowDelayMillis(ENV, lavaSource);
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
					new SuspendedCuboid<IReadOnlyCuboidData>(waterPool, HeightMapHelpers.buildHeightMap(waterPool), List.of(), List.of(), Map.of())
					, new SuspendedCuboid<IReadOnlyCuboidData>(lavaBlob, HeightMapHelpers.buildHeightMap(lavaBlob), List.of(), List.of(
							new ScheduledMutation(placeLava, 0L)
					), Map.of())
					, new SuspendedCuboid<IReadOnlyCuboidData>(lavaPool, HeightMapHelpers.buildHeightMap(lavaPool), List.of(), List.of(), Map.of())
					, new SuspendedCuboid<IReadOnlyCuboidData>(waterBlob, HeightMapHelpers.buildHeightMap(waterBlob), List.of(), List.of(
							new ScheduledMutation(placeWater, 0L)
					), Map.of())
				)
				, null
				, null
				, null
		);
		runner.startNextTick();
		TickRunner.Snapshot snapshot = runner.waitForPreviousTick();
		
		// Wait for lava to flow twice, then another iteration for solidification, another 2 to let liquid fade, and a few more for final lava flow.
		long millisToFlow = ENV.liquids.flowDelayMillis(ENV, lavaSource);
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


	private TickRunner.Snapshot _runTickLockStep(TickRunner runner, IMutationBlock mutation)
	{
		// This helper is useful when a test wants to be certain that a mutation has completed before checking state.
		// 1) Wait for any in-flight tick to complete.
		runner.waitForPreviousTick();
		// 2) Enqueue the mutation to be picked up by the next tick.
		runner.enqueueEntityChange(1, new EntityChangeMutation(mutation), 1L);
		// (run an extra tick to unwrap the entity change)
		runner.startNextTick();
		// 3) Run the tick which will execute the mutation.
		runner.startNextTick();
		// 4) Wait for this tick to complete in order to rely on the result being observable.
		return runner.waitForPreviousTick();
	}

	private BlockProxy _getBlockProxy(TickRunner.Snapshot snapshot, AbsoluteLocation location)
	{
		CuboidAddress address = location.getCuboidAddress();
		TickRunner.SnapshotCuboid snapData = snapshot.cuboids().get(address);
		
		BlockProxy block = null;
		if (null != snapData)
		{
			BlockAddress blockAddress = location.getBlockAddress();
			block = new BlockProxy(blockAddress, snapData.completed());
		}
		return block;
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

	private SuspendedEntity _createFreshEntity(int entityId)
	{
		return new SuspendedEntity(MutableEntity.createForTest(entityId).freeze(), List.of());
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
		Consumer<TickRunner.Snapshot> snapshotListener = (TickRunner.Snapshot completed) -> {};
		Random random = new Random();
		TickRunner runner = new TickRunner(ServerRunner.TICK_RUNNER_THREAD_COUNT
				, MILLIS_PER_TICK
				, null
				, (int bound) -> random.nextInt(bound)
				, snapshotListener
				, config
		);
		return runner;
	}

	private SuspendedCuboid<IReadOnlyCuboidData> _buildAirCuboid(CuboidAddress address)
	{
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		return _packageCuboid(cuboid);
	}

	private SuspendedCuboid<IReadOnlyCuboidData> _buildStoneCuboid(CuboidAddress address)
	{
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, STONE);
		return _packageCuboid(cuboid);
	}

	private SuspendedCuboid<IReadOnlyCuboidData> _packageCuboid(CuboidData cuboid)
	{
		return new SuspendedCuboid<IReadOnlyCuboidData>(cuboid, HeightMapHelpers.buildHeightMap(cuboid), List.of(), List.of(), Map.of());
	}

	private int _mismatchCount(ColumnHeightMap one, ColumnHeightMap two)
	{
		int count = 0;
		for (int y = 0; y < Encoding.CUBOID_EDGE_SIZE; ++y)
		{
			for (int x = 0; x < Encoding.CUBOID_EDGE_SIZE; ++x)
			{
				int heightOne = one.getHeight(x, y);
				int heightTwo = two.getHeight(x, y);
				if (heightOne != heightTwo)
				{
					count += 1;
				}
			}
		}
		return count;
	}

	private long _applyIncrementalBreaks(TickRunner runner, long nextCommit, int entityId, AbsoluteLocation changeLocation, short millisOfBreak)
	{
		short millisRemaining = millisOfBreak;
		while (millisRemaining > 0)
		{
			short timeToApply = (short) Math.min(MILLIS_PER_TICK, millisRemaining);
			EntityChangeIncrementalBlockBreak break1 = new EntityChangeIncrementalBlockBreak(changeLocation, timeToApply);
			runner.enqueueEntityChange(entityId, break1, nextCommit);
			nextCommit += 1L;
			runner.startNextTick();
			millisRemaining -= timeToApply;
		}
		return nextCommit;
	}

	private TickRunner.Snapshot _passSomeTime(TickRunner runner, long minMillisToPass)
	{
		TickRunner.Snapshot snapshot = null;
		long millisRemaining = minMillisToPass;
		while (millisRemaining > 0L)
		{
			snapshot = runner.startNextTick();
			millisRemaining -= MILLIS_PER_TICK;
		}
		return snapshot;
	}
}
