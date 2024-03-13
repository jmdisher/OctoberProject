package com.jeffdisher.october.server;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.aspects.FuelAspect;
import com.jeffdisher.october.aspects.InventoryAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.EntityActionValidator;
import com.jeffdisher.october.logic.EntityChangeSendItem;
import com.jeffdisher.october.logic.ShockwaveMutation;
import com.jeffdisher.october.mutations.DropItemMutation;
import com.jeffdisher.october.mutations.EntityChangeIncrementalBlockBreak;
import com.jeffdisher.october.mutations.EntityChangeMutation;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.mutations.MutationBlockFurnaceCraft;
import com.jeffdisher.october.mutations.MutationBlockIncrementalBreak;
import com.jeffdisher.october.mutations.MutationBlockStoreItems;
import com.jeffdisher.october.mutations.MutationEntityPushItems;
import com.jeffdisher.october.mutations.PickUpItemMutation;
import com.jeffdisher.october.mutations.ReplaceBlockMutation;
import com.jeffdisher.october.mutations.SaturatingDamage;
import com.jeffdisher.october.persistence.SuspendedCuboid;
import com.jeffdisher.october.registries.AspectRegistry;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.worldgen.CuboidGenerator;


public class TestTickRunner
{
	@Test
	public void basicOneCuboid()
	{
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ItemRegistry.AIR);
		TickRunner runner = new TickRunner(ServerRunner.TICK_RUNNER_THREAD_COUNT, ServerRunner.DEFAULT_MILLIS_PER_TICK, (TickRunner.Snapshot completed) -> {});
		runner.cuboidsWereLoaded(List.of(new SuspendedCuboid<CuboidData>(cuboid, List.of())));
		int entityId = 1;
		runner.entityDidJoin(EntityActionValidator.buildDefaultEntity(entityId));
		runner.start();
		runner.waitForPreviousTick();
		// The mutation will be run in the next tick since there isn't one running.
		runner.enqueueEntityChange(entityId, new EntityChangeMutation(new ReplaceBlockMutation(new AbsoluteLocation(0, 0, 0), ItemRegistry.AIR.number(), ItemRegistry.STONE.number())), 1L);
		// (run an extra tick to unwrap the entity change)
		runner.startNextTick();
		runner.startNextTick();
		TickRunner.Snapshot snapshot = runner.waitForPreviousTick();
		runner.shutdown();
		
		Assert.assertEquals(1, snapshot.committedCuboidMutationCount());
	}

	@Test
	public void shockwaveOneCuboid()
	{
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ItemRegistry.AIR);
		TickRunner runner = new TickRunner(ServerRunner.TICK_RUNNER_THREAD_COUNT, ServerRunner.DEFAULT_MILLIS_PER_TICK, (TickRunner.Snapshot completed) -> {});
		runner.cuboidsWereLoaded(List.of(new SuspendedCuboid<CuboidData>(cuboid, List.of())));
		int entityId = 1;
		runner.entityDidJoin(EntityActionValidator.buildDefaultEntity(entityId));
		runner.start();
		runner.waitForPreviousTick();
		// We enqueue a single shockwave in the centre of the cuboid and allow it to replicate 2 times.
		runner.enqueueEntityChange(entityId, new EntityChangeMutation(new ShockwaveMutation(new AbsoluteLocation(16, 16, 16), 2)), 1L);
		// (run an extra tick to unwrap the entity change)
		runner.startNextTick();
		runner.startNextTick();
		TickRunner.Snapshot snap1 = runner.startNextTick();
		Assert.assertEquals(1, snap1.scheduledBlockMutations().size());
		Assert.assertEquals(6, snap1.scheduledBlockMutations().get(address).size());
		TickRunner.Snapshot snap2 =runner.waitForPreviousTick();
		Assert.assertEquals(1, snap2.scheduledBlockMutations().size());
		Assert.assertEquals(36, snap2.scheduledBlockMutations().get(address).size());
		runner.startNextTick();
		TickRunner.Snapshot snap3 = runner.startNextTick();
		Assert.assertEquals(0, snap3.scheduledBlockMutations().size());
		runner.shutdown();
		
		// 1 + 6 + 36 = 43.
		Assert.assertEquals(1, snap1.committedCuboidMutationCount());
		Assert.assertEquals(6, snap2.committedCuboidMutationCount());
		Assert.assertEquals(36, snap3.committedCuboidMutationCount());
	}

	@Test
	public void shockwaveMultiCuboids()
	{
		// Use extra threads here to stress further.
		TickRunner runner = new TickRunner(8, ServerRunner.DEFAULT_MILLIS_PER_TICK, (TickRunner.Snapshot completed) -> {});
		runner.cuboidsWereLoaded(List.of(new SuspendedCuboid<CuboidData>(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ItemRegistry.AIR), List.of())
				, new SuspendedCuboid<CuboidData>(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)-1), ItemRegistry.AIR), List.of())
				, new SuspendedCuboid<CuboidData>(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)-1, (short)0), ItemRegistry.AIR), List.of())
				, new SuspendedCuboid<CuboidData>(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)-1, (short)-1), ItemRegistry.AIR), List.of())
				, new SuspendedCuboid<CuboidData>(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)-1, (short)0, (short)0), ItemRegistry.AIR), List.of())
				, new SuspendedCuboid<CuboidData>(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)-1, (short)0, (short)-1), ItemRegistry.AIR), List.of())
				, new SuspendedCuboid<CuboidData>(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)-1, (short)-1, (short)0), ItemRegistry.AIR), List.of())
				, new SuspendedCuboid<CuboidData>(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)-1, (short)-1, (short)-1), ItemRegistry.AIR), List.of())
		));
		int entityId = 1;
		runner.entityDidJoin(EntityActionValidator.buildDefaultEntity(entityId));
		runner.start();
		runner.waitForPreviousTick();
		// We enqueue a single shockwave in the centre of the cuboid and allow it to replicate 2 times.
		runner.enqueueEntityChange(entityId, new EntityChangeMutation(new ShockwaveMutation(new AbsoluteLocation(0, 0, 0), 2)), 1L);
		// (run an extra tick to unwrap the entity change)
		runner.startNextTick();
		runner.startNextTick();
		TickRunner.Snapshot snap1 = runner.startNextTick();
		Assert.assertEquals(4, snap1.scheduledBlockMutations().size());
		TickRunner.Snapshot snap2 = runner.startNextTick();
		Assert.assertEquals(7, snap2.scheduledBlockMutations().size());
		TickRunner.Snapshot snap3 = runner.startNextTick();
		Assert.assertEquals(0, snap3.scheduledBlockMutations().size());
		runner.shutdown();
		
		// 1 + 6 + 36 = 43.
		Assert.assertEquals(1, snap1.committedCuboidMutationCount());
		Assert.assertEquals(6, snap2.committedCuboidMutationCount());
		Assert.assertEquals(36, snap3.committedCuboidMutationCount());
	}

	@Test
	public void basicBlockRead()
	{
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ItemRegistry.AIR);
		TickRunner runner = new TickRunner(ServerRunner.TICK_RUNNER_THREAD_COUNT, ServerRunner.DEFAULT_MILLIS_PER_TICK, (TickRunner.Snapshot completed) -> {});
		runner.cuboidsWereLoaded(List.of(new SuspendedCuboid<CuboidData>(cuboid, List.of())));
		int entityId = 1;
		runner.entityDidJoin(EntityActionValidator.buildDefaultEntity(entityId));
		runner.start();
		TickRunner.Snapshot startState = runner.waitForPreviousTick();
		
		// Before we run a tick, the cuboid shouldn't yet be loaded (it is added to the new world during a tick) so we should see a null block.
		Assert.assertNull(_getBlockProxy(startState, new AbsoluteLocation(0, 0, 0)));
		
		// Run the tick so that it applies the new load.
		runner.startNextTick();
		TickRunner.Snapshot snapshot = runner.waitForPreviousTick();
		// Now, we should see a block with default properties.
		BlockProxy block = _getBlockProxy(snapshot, new AbsoluteLocation(0, 0, 0));
		Assert.assertEquals(ItemRegistry.AIR, block.getBlock().asItem());
		
		// Note that the mutation will not be enqueued in the next tick, but the following one (they are queued and picked up when the threads finish).
		runner.enqueueEntityChange(entityId, new EntityChangeMutation(new ReplaceBlockMutation(new AbsoluteLocation(0, 0, 0), ItemRegistry.AIR.number(), ItemRegistry.STONE.number())), 1L);
		// (run an extra tick to unwrap the entity change)
		runner.startNextTick();
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		runner.shutdown();
		
		// We should now see the new data.
		block = _getBlockProxy(snapshot, new AbsoluteLocation(0, 0, 0));
		Assert.assertEquals(ItemRegistry.STONE, block.getBlock().asItem());
	}

	@Test
	public void basicInventoryOperations()
	{
		// Just add, add, and remove some inventory items.
		AbsoluteLocation testBlock = new AbsoluteLocation(0, 0, 0);
		Item stoneItem = ItemRegistry.STONE;
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ItemRegistry.AIR);
		
		// Create a tick runner with a single cuboid and get it running.
		TickRunner runner = new TickRunner(ServerRunner.TICK_RUNNER_THREAD_COUNT, ServerRunner.DEFAULT_MILLIS_PER_TICK, (TickRunner.Snapshot completed) -> {});
		runner.cuboidsWereLoaded(List.of(new SuspendedCuboid<CuboidData>(cuboid, List.of())));
		int entityId = 1;
		runner.entityDidJoin(EntityActionValidator.buildDefaultEntity(entityId));
		runner.start();
		runner.startNextTick();
		TickRunner.Snapshot snapshot = runner.waitForPreviousTick();
		
		// Make sure that we see the empty inventory.
		BlockProxy block = _getBlockProxy(snapshot, testBlock);
		Assert.assertEquals(0, block.getInventory().currentEncumbrance);
		
		// Apply the first mutation to add data.
		snapshot = _runTickLockStep(runner, new DropItemMutation(testBlock, stoneItem, 1));
		block = _getBlockProxy(snapshot, testBlock);
		Assert.assertEquals(1, block.getInventory().items.get(stoneItem).count());
		
		// Try to drop too much to fit and verify that nothing changes.
		snapshot = _runTickLockStep(runner, new DropItemMutation(testBlock, stoneItem, InventoryAspect.CAPACITY_AIR / 2));
		block = _getBlockProxy(snapshot, testBlock);
		Assert.assertEquals(1, block.getInventory().items.get(stoneItem).count());
		
		// Add a little more data and make sure that it updates.
		snapshot = _runTickLockStep(runner, new DropItemMutation(testBlock, stoneItem, 2));
		block = _getBlockProxy(snapshot, testBlock);
		Assert.assertEquals(3, block.getInventory().items.get(stoneItem).count());
		
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
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ItemRegistry.AIR);
		TickRunner runner = new TickRunner(ServerRunner.TICK_RUNNER_THREAD_COUNT, ServerRunner.DEFAULT_MILLIS_PER_TICK, (TickRunner.Snapshot completed) -> {});
		runner.cuboidsWereLoaded(List.of(new SuspendedCuboid<CuboidData>(cuboid, List.of())));
		runner.start();
		
		// Have a new entity join and wait for them to be added.
		int entityId = 1;
		Entity entity = EntityActionValidator.buildDefaultEntity(entityId);
		runner.entityDidJoin(entity);
		runner.startNextTick();
		runner.waitForPreviousTick();
		
		// Now, add a mutation from this entity to deliver the block replacement mutation.
		AbsoluteLocation changeLocation = new AbsoluteLocation(0, 0, 0);
		long commit1 = 1L;
		runner.enqueueEntityChange(entityId, new EntityChangeMutation(new ReplaceBlockMutation(changeLocation, ItemRegistry.AIR.number(), ItemRegistry.STONE.number())), commit1);
		
		// This will take a few ticks to be observable:
		// -after tick 1, the change will have been run and the mutation enqueued
		runner.startNextTick();
		TickRunner.Snapshot snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(commit1, snapshot.commitLevels().get(entityId).longValue());
		Assert.assertEquals(1, snapshot.committedEntityMutationCount());
		// -after tick 2, the mutation will have been committed
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(1, snapshot.committedCuboidMutationCount());
		
		// Shutdown and observe expected results.
		runner.shutdown();
		
		Assert.assertEquals(ItemRegistry.STONE, _getBlockProxy(snapshot, changeLocation).getBlock().asItem());
	}

	@Test
	public void dependentEntityChanges()
	{
		TickRunner runner = new TickRunner(ServerRunner.TICK_RUNNER_THREAD_COUNT, ServerRunner.DEFAULT_MILLIS_PER_TICK, (TickRunner.Snapshot completed) -> {});
		runner.start();
		
		// We need 2 entities for this but we will give one some items.
		int entityId = 1;
		Inventory startInventory = Inventory.start(10).add(ItemRegistry.STONE, 2).finish();
		runner.entityDidJoin(new Entity(0, EntityActionValidator.DEFAULT_LOCATION, 0.0f, EntityActionValidator.DEFAULT_VOLUME, EntityActionValidator.DEFAULT_BLOCKS_PER_TICK_SPEED, startInventory, null, null));
		runner.entityDidJoin(EntityActionValidator.buildDefaultEntity(entityId));
		// (run a tick to pick up the users)
		runner.startNextTick();
		runner.waitForPreviousTick();
		
		// Try to pass the items to the other entity.
		IMutationEntity send = new EntityChangeSendItem(entityId, ItemRegistry.STONE);
		long commit1 = 1L;
		runner.enqueueEntityChange(0, send, commit1);
		// (run a tick to run the change and enqueue the next)
		runner.startNextTick();
		TickRunner.Snapshot snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(commit1, snapshot.commitLevels().get(0).longValue());
		Assert.assertEquals(1, snapshot.resultantMutationsById().get(0).size());
		Assert.assertEquals(1, snapshot.scheduledEntityMutations().size());
		Assert.assertEquals(1, snapshot.scheduledEntityMutations().get(entityId).size());
		// (run a tick to run the final change)
		runner.startNextTick();
		TickRunner.Snapshot finalSnapshot = runner.waitForPreviousTick();
		Assert.assertEquals(0, finalSnapshot.scheduledEntityMutations().size());
		runner.shutdown();
		
		// Now, check for results.
		Assert.assertEquals(1, finalSnapshot.resultantMutationsById().get(entityId).size());
		Entity sender = finalSnapshot.completedEntities().get(0);
		Entity receiver = finalSnapshot.completedEntities().get(1);
		Assert.assertTrue(sender.inventory().items.isEmpty());
		Assert.assertEquals(1, receiver.inventory().items.size());
		Items update = receiver.inventory().items.get(ItemRegistry.STONE);
		Assert.assertEquals(2, update.count());
	}

	@Test
	public void multiStepBlockBreak()
	{
		// Show what happens if we break a block in 2 steps.
		TickRunner runner = new TickRunner(ServerRunner.TICK_RUNNER_THREAD_COUNT, ServerRunner.DEFAULT_MILLIS_PER_TICK, (TickRunner.Snapshot completed) -> {});
		
		// Create a cuboid of stone.
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ItemRegistry.STONE);
		runner.cuboidsWereLoaded(List.of(new SuspendedCuboid<CuboidData>(cuboid, List.of())));
		
		// We can use the default entity since we don't yet check that they are standing in solid rock.
		int entityId = 1;
		Entity entity = EntityActionValidator.buildDefaultEntity(entityId);
		runner.entityDidJoin(entity);
		
		// Start up and run the first tick so that these get loaded.
		runner.start();
		runner.startNextTick();
		runner.waitForPreviousTick();
		
		// Schedule the first step.
		// We will now show how to schedule the multi-phase change.
		AbsoluteLocation changeLocation1 = new AbsoluteLocation(0, 0, 0);
		EntityChangeIncrementalBlockBreak break1 = new EntityChangeIncrementalBlockBreak(changeLocation1, (short) 100);
		long commit1 = 1L;
		runner.enqueueEntityChange(entityId, break1, commit1);
		
		// We now run the tick.
		// (note that this will commit the entity change but not the block change)
		runner.startNextTick();
		TickRunner.Snapshot snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(commit1, snapshot.commitLevels().get(entityId).longValue());
		Assert.assertEquals(1, snapshot.committedEntityMutationCount());
		
		// Run another tick to see the underlying block change applied.
		// We should see the commit and the change to the damage value.
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(1, snapshot.committedCuboidMutationCount());
		BlockProxy proxy1 = _getBlockProxy(snapshot, changeLocation1);
		Assert.assertEquals(ItemRegistry.STONE, proxy1.getBlock().asItem());
		Assert.assertEquals((short) 1000, proxy1.getDamage());
		Assert.assertNull(proxy1.getInventory());
		
		// Now, enqueue the second hit to finish the break.
		EntityChangeIncrementalBlockBreak break2 = new EntityChangeIncrementalBlockBreak(changeLocation1, (short) 100);
		long commit2 = 2L;
		runner.enqueueEntityChange(entityId, break2, commit2);
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(commit2, snapshot.commitLevels().get(entityId).longValue());
		Assert.assertEquals(1, snapshot.committedEntityMutationCount());
		
		// Run the second tick to see the block change.
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(1, snapshot.committedCuboidMutationCount());
		BlockProxy proxy2 = _getBlockProxy(snapshot, changeLocation1);
		Assert.assertEquals(ItemRegistry.AIR, proxy2.getBlock().asItem());
		Assert.assertEquals((short) 0, proxy2.getDamage());
		Inventory inv = proxy2.getInventory();
		Assert.assertEquals(1, inv.items.size());
		Assert.assertEquals(1, inv.items.get(ItemRegistry.STONE).count());
		
		runner.shutdown();
	}

	@Test
	public void checkSnapshotDelta()
	{
		TickRunner.Snapshot[] snapshotRef = new TickRunner.Snapshot[1];
		Consumer<TickRunner.Snapshot> snapshotListener = (TickRunner.Snapshot completed) -> {
			snapshotRef[0] = completed;
		};
		TickRunner runner = new TickRunner(ServerRunner.TICK_RUNNER_THREAD_COUNT, ServerRunner.DEFAULT_MILLIS_PER_TICK, snapshotListener);
		CuboidAddress targetAddress = new CuboidAddress((short)0, (short)0, (short)0);
		runner.cuboidsWereLoaded(List.of(
				new SuspendedCuboid<CuboidData>(CuboidGenerator.createFilledCuboid(targetAddress, ItemRegistry.AIR), List.of())
		));
		CuboidAddress constantAddress = new CuboidAddress((short)0, (short)0, (short)1);
		runner.cuboidsWereLoaded(List.of(
				new SuspendedCuboid<CuboidData>(CuboidGenerator.createFilledCuboid(constantAddress, ItemRegistry.AIR), List.of())
		));
		int entityId = 1;
		runner.entityDidJoin(EntityActionValidator.buildDefaultEntity(entityId));
		
		// Verify that there is no snapshot until we start.
		Assert.assertNull(snapshotRef[0]);
		runner.start();
		
		// Wait for the start-up to complete and verify that we have the empty initial snapshot (since the start doesn't pick up any cuboids).
		runner.waitForPreviousTick();
		Assert.assertNotNull(snapshotRef[0]);
		Assert.assertEquals(0, snapshotRef[0].completedCuboids().size());
		
		// Run the tick so that it applies the new load.
		runner.startNextTick();
		runner.waitForPreviousTick();
		Assert.assertNotNull(snapshotRef[0]);
		// We should see 2 cuboids.
		Map<CuboidAddress, IReadOnlyCuboidData> initialCuboids = snapshotRef[0].completedCuboids();
		Assert.assertEquals(2, initialCuboids.size());
		
		// Run a mutation and notice that only the changed cuboid isn't an instance match.
		runner.enqueueEntityChange(1, new EntityChangeMutation(new ReplaceBlockMutation(new AbsoluteLocation(0, 0, 0), ItemRegistry.AIR.number(), ItemRegistry.STONE.number())), 1L);
		// (run an extra tick to unwrap the entity change)
		runner.startNextTick();
		runner.startNextTick();
		runner.waitForPreviousTick();
		Assert.assertNotNull(snapshotRef[0]);
		// This should be the same size.
		Map<CuboidAddress, IReadOnlyCuboidData> laterCuboids = snapshotRef[0].completedCuboids();
		Assert.assertEquals(2, laterCuboids.size());
		
		runner.shutdown();
		
		// Verify that the target cuboid is a new instance.
		Assert.assertTrue(initialCuboids.get(targetAddress) != laterCuboids.get(targetAddress));
		// Verify that the unchanged cuboid is the same instance.
		Assert.assertTrue(initialCuboids.get(constantAddress) == laterCuboids.get(constantAddress));
	}

	@Test
	public void joinAndDepart()
	{
		// Create an entity and have it join and then depart, verifying that we can observe this add and removal in the snapshot.
		TickRunner.Snapshot[] snapshotRef = new TickRunner.Snapshot[1];
		Consumer<TickRunner.Snapshot> snapshotListener = (TickRunner.Snapshot completed) -> {
			snapshotRef[0] = completed;
		};
		TickRunner runner = new TickRunner(ServerRunner.TICK_RUNNER_THREAD_COUNT, ServerRunner.DEFAULT_MILLIS_PER_TICK, snapshotListener);
		
		// Verify that there is no snapshot until we start.
		Assert.assertNull(snapshotRef[0]);
		runner.start();
		
		// Wait for the start-up to complete and verify that there are no entities in the snapshot.
		runner.waitForPreviousTick();
		Assert.assertNotNull(snapshotRef[0]);
		Assert.assertEquals(0, snapshotRef[0].completedEntities().size());
		
		// Add the new entity and run a tick.
		runner.entityDidJoin(EntityActionValidator.buildDefaultEntity(1));
		runner.startNextTick();
		runner.waitForPreviousTick();
		// We should see the entity.
		Assert.assertEquals(1, snapshotRef[0].completedEntities().size());
		
		// Now, leave and verify that the entity has disappeared from the snapshot.
		runner.entityDidLeave(1);
		runner.startNextTick();
		runner.waitForPreviousTick();
		Assert.assertEquals(0, snapshotRef[0].completedEntities().size());
		
		runner.shutdown();
	}

	@Test
	public void startStopReloadWithSuspendedMutations()
	{
		// We will start a runner, attach an entity, have it send an action with block mutation consequences, wait until we see this in the snapshot, shut it down, then restart it and see that these resume.
		TickRunner runner = new TickRunner(ServerRunner.TICK_RUNNER_THREAD_COUNT, ServerRunner.DEFAULT_MILLIS_PER_TICK, (TickRunner.Snapshot completed) -> {});
		runner.start();
		
		// Wait for the start-up to complete and then load the entity and some cuboids.
		runner.waitForPreviousTick();
		int entityId = 1;
		runner.entityDidJoin(EntityActionValidator.buildDefaultEntity(entityId));
		CuboidAddress airAddress = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidAddress stoneAddress = new CuboidAddress((short)0, (short)0, (short)-1);
		runner.cuboidsWereLoaded(List.of(
				new SuspendedCuboid<CuboidData>(CuboidGenerator.createFilledCuboid(airAddress, ItemRegistry.AIR), List.of()),
				new SuspendedCuboid<CuboidData>(CuboidGenerator.createFilledCuboid(stoneAddress, ItemRegistry.STONE), List.of())
		));
		runner.startNextTick();
		TickRunner.Snapshot snapshot = runner.waitForPreviousTick();
		// Verify that we see the entity and cuboids.
		Assert.assertEquals(1, snapshot.completedEntities().size());
		Assert.assertEquals(2, snapshot.completedCuboids().size());
		
		// Tell them to start breaking a stone block.
		short damage = 10;
		runner.enqueueEntityChange(entityId, new EntityChangeIncrementalBlockBreak(new AbsoluteLocation(1, 1, -1), damage), 1L);
		// Run a tick and verify that we see the cuboid mutation from this in the snapshot.
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(0, snapshot.completedCuboids().get(stoneAddress).getData15(AspectRegistry.DAMAGE, new BlockAddress((byte)1, (byte)1, (byte)31)));
		Assert.assertEquals(1, snapshot.scheduledBlockMutations().size());
		Assert.assertEquals(1, snapshot.scheduledBlockMutations().get(stoneAddress).size());
		MutationBlockIncrementalBreak mutation = (MutationBlockIncrementalBreak) snapshot.scheduledBlockMutations().get(stoneAddress).get(0);
		
		// Shut down the runner, start a new one, and load the cuboids back in.
		runner.shutdown();
		runner = new TickRunner(ServerRunner.TICK_RUNNER_THREAD_COUNT, ServerRunner.DEFAULT_MILLIS_PER_TICK, (TickRunner.Snapshot completed) -> {});
		runner.start();
		runner.waitForPreviousTick();
		runner.cuboidsWereLoaded(List.of(
				new SuspendedCuboid<CuboidData>(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ItemRegistry.AIR), List.of()),
				new SuspendedCuboid<CuboidData>(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)-1), ItemRegistry.STONE), List.of(mutation))
		));
		runner.startNextTick();
		// Verify that this mutation has been run.
		snapshot = runner.waitForPreviousTick();
		// We should see block updates for 2 cuboids (since that is all that is loaded).  In total, this is the size adjacent blocks.
		Assert.assertEquals(2, snapshot.scheduledBlockMutations().size());
		Assert.assertEquals(6, snapshot.scheduledBlockMutations().values().stream().map((List<IMutationBlock> list) -> list.size()).mapToInt((Integer i) -> i.intValue()).sum());
		// Remember that there is a 10x damage multiplier until tools are added.
		Assert.assertEquals(10 * damage, snapshot.completedCuboids().get(stoneAddress).getData15(AspectRegistry.DAMAGE, new BlockAddress((byte)1, (byte)1, (byte)31)));
		
		runner.shutdown();
	}

	@Test
	public void saturatingMutations()
	{
		// Apply a few mutations which saturate within one tick.
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ItemRegistry.STONE);
		TickRunner runner = new TickRunner(ServerRunner.TICK_RUNNER_THREAD_COUNT, ServerRunner.DEFAULT_MILLIS_PER_TICK, (TickRunner.Snapshot completed) -> {});
		runner.cuboidsWereLoaded(List.of(new SuspendedCuboid<CuboidData>(cuboid, List.of())));
		int entityId = 1;
		runner.entityDidJoin(EntityActionValidator.buildDefaultEntity(entityId));
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
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ItemRegistry.FURNACE);
		TickRunner runner = new TickRunner(ServerRunner.TICK_RUNNER_THREAD_COUNT, ServerRunner.DEFAULT_MILLIS_PER_TICK, (TickRunner.Snapshot completed) -> {});
		runner.cuboidsWereLoaded(List.of(new SuspendedCuboid<CuboidData>(cuboid, List.of())));
		int entityId = 1;
		Inventory inventory = Inventory.start(InventoryAspect.CAPACITY_PLAYER).add(ItemRegistry.STONE_BRICK, 3).add(ItemRegistry.PLANK, 2).finish();
		Entity entity = new Entity(entityId
				, EntityActionValidator.DEFAULT_LOCATION
				, 0.0f
				, EntityActionValidator.DEFAULT_VOLUME
				, EntityActionValidator.DEFAULT_BLOCKS_PER_TICK_SPEED
				, inventory
				, null
				, null
		);
		runner.entityDidJoin(entity);
		runner.start();
		// Remember that the first tick is just returning the empty state.
		runner.waitForPreviousTick();
		runner.startNextTick();
		TickRunner.Snapshot snap = runner.waitForPreviousTick();
		Assert.assertNotNull(snap.completedCuboids().get(address));
		
		// Load the furnace with fuel and material.
		AbsoluteLocation location = new AbsoluteLocation(0, 0, 0);
		BlockAddress block = location.getBlockAddress();
		runner.enqueueEntityChange(entityId, new MutationEntityPushItems(location, new Items(ItemRegistry.STONE_BRICK, 3), Inventory.INVENTORY_ASPECT_INVENTORY), 1L);
		runner.enqueueEntityChange(entityId, new MutationEntityPushItems(location, new Items(ItemRegistry.PLANK, 2), Inventory.INVENTORY_ASPECT_FUEL), 2L);
		runner.startNextTick();
		snap = runner.waitForPreviousTick();
		Assert.assertEquals(2, snap.committedEntityMutationCount());
		// We should see the two calls to accept the items.
		Assert.assertTrue(snap.scheduledBlockMutations().get(address).get(0) instanceof MutationBlockStoreItems);
		Assert.assertTrue(snap.scheduledBlockMutations().get(address).get(1) instanceof MutationBlockStoreItems);
		
		// Run the next tick to see the craft scheduled.
		runner.startNextTick();
		snap = runner.waitForPreviousTick();
		Assert.assertEquals(2, snap.committedCuboidMutationCount());
		// We should see the two calls to accept the items.
		Assert.assertTrue(snap.scheduledBlockMutations().get(address).get(0) instanceof MutationBlockFurnaceCraft);
		BlockProxy proxy = new BlockProxy(block, snap.completedCuboids().get(address));
		Assert.assertEquals(3, proxy.getInventory().getCount(ItemRegistry.STONE_BRICK));
		Assert.assertEquals(2, proxy.getFuel().fuelInventory().getCount(ItemRegistry.PLANK));
		
		// Loop until the craft is done.
		int burnedMillis = 0;
		int craftedMillis = 0;
		int brickCount = 3;
		int plankCount = 1;
		for (int i = 1; i < 30; ++i)
		{
			if (0 == (i % 10))
			{
				brickCount -= 1;
			}
			if (21 == i)
			{
				plankCount -= 1;
			}
			burnedMillis = (burnedMillis + (int)ServerRunner.DEFAULT_MILLIS_PER_TICK) % FuelAspect.BURN_MILLIS_PLANK;
			craftedMillis = (craftedMillis + (int)ServerRunner.DEFAULT_MILLIS_PER_TICK) % 1000;
			runner.startNextTick();
			snap = runner.waitForPreviousTick();
			
			Assert.assertEquals(1, snap.committedCuboidMutationCount());
			Assert.assertTrue(snap.scheduledBlockMutations().get(address).get(0) instanceof MutationBlockFurnaceCraft);
			proxy = new BlockProxy(block, snap.completedCuboids().get(address));
			Assert.assertEquals(brickCount, proxy.getInventory().getCount(ItemRegistry.STONE_BRICK));
			Assert.assertEquals(plankCount, proxy.getFuel().fuelInventory().getCount(ItemRegistry.PLANK));
			if (0 != (i % 20))
			{
				Assert.assertEquals(burnedMillis, FuelAspect.BURN_MILLIS_PLANK - proxy.getFuel().millisFueled());
				Assert.assertEquals(ItemRegistry.PLANK, proxy.getFuel().currentFuel());
			}
			else
			{
				Assert.assertNull(proxy.getFuel().currentFuel());
			}
			if (0 != (i % 10))
			{
				Assert.assertEquals(craftedMillis, proxy.getCrafting().completedMillis());
			}
		}
		burnedMillis += ServerRunner.DEFAULT_MILLIS_PER_TICK;
		runner.startNextTick();
		snap = runner.waitForPreviousTick();
		
		Assert.assertEquals(1, snap.committedCuboidMutationCount());
		Assert.assertTrue(snap.scheduledBlockMutations().get(address).get(0) instanceof MutationBlockFurnaceCraft);
		proxy = new BlockProxy(block, snap.completedCuboids().get(address));
		Assert.assertEquals(0, proxy.getInventory().getCount(ItemRegistry.STONE_BRICK));
		Assert.assertEquals(3, proxy.getInventory().getCount(ItemRegistry.LOG));
		Assert.assertEquals(burnedMillis, FuelAspect.BURN_MILLIS_PLANK - proxy.getFuel().millisFueled());
		Assert.assertEquals(ItemRegistry.PLANK, proxy.getFuel().currentFuel());
		Assert.assertNull(proxy.getCrafting());
		
		// Now, wait for the fuel to finish.
		for (int i = 0; i < 10; ++i)
		{
			burnedMillis += ServerRunner.DEFAULT_MILLIS_PER_TICK;
			runner.startNextTick();
			snap = runner.waitForPreviousTick();
			
			Assert.assertEquals(1, snap.committedCuboidMutationCount());
			proxy = new BlockProxy(block, snap.completedCuboids().get(address));
			Assert.assertEquals(burnedMillis, FuelAspect.BURN_MILLIS_PLANK - proxy.getFuel().millisFueled());
		}
		Assert.assertEquals(0, proxy.getFuel().millisFueled());
		Assert.assertNull(proxy.getFuel().currentFuel());
		
		// We should see the block update events for the 3 loaded blocks so running another tick should drain those.
		Assert.assertEquals(1, snap.scheduledBlockMutations().size());
		Assert.assertEquals(3, snap.scheduledBlockMutations().values().stream().map((List<IMutationBlock> list) -> list.size()).mapToInt((Integer i) -> i.intValue()).sum());
		runner.startNextTick();
		snap = runner.waitForPreviousTick();
		Assert.assertTrue(snap.scheduledBlockMutations().isEmpty());
		
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
		IReadOnlyCuboidData cuboid = snapshot.completedCuboids().get(address);
		
		BlockProxy block = null;
		if (null != cuboid)
		{
			BlockAddress blockAddress = location.getBlockAddress();
			block = new BlockProxy(blockAddress, cuboid);
		}
		return block;
	}
}
