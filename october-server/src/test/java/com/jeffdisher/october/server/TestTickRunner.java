package com.jeffdisher.october.server;

import java.util.Map;
import java.util.function.Consumer;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.aspects.InventoryAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.EntityActionValidator;
import com.jeffdisher.october.logic.EntityChangeSendItem;
import com.jeffdisher.october.logic.ShockwaveMutation;
import com.jeffdisher.october.mutations.DropItemMutation;
import com.jeffdisher.october.mutations.EndBreakBlockChange;
import com.jeffdisher.october.mutations.EntityChangeMutation;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.mutations.PickUpItemMutation;
import com.jeffdisher.october.mutations.ReplaceBlockMutation;
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
		TickRunner runner = new TickRunner(1, ServerRunner.DEFAULT_MILLIS_PER_TICK, (TickRunner.Snapshot completed) -> {});
		runner.cuboidWasLoaded(cuboid);
		runner.start();
		runner.waitForPreviousTick();
		// The mutation will be run in the next tick since there isn't one running.
		runner.enqueueMutation(new ReplaceBlockMutation(new AbsoluteLocation(0, 0, 0), BlockAspect.AIR, BlockAspect.STONE));
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
		TickRunner runner = new TickRunner(1, ServerRunner.DEFAULT_MILLIS_PER_TICK, (TickRunner.Snapshot completed) -> {});
		runner.cuboidWasLoaded(cuboid);
		runner.start();
		runner.waitForPreviousTick();
		// We enqueue a single shockwave in the centre of the cuboid and allow it to replicate 2 times.
		runner.enqueueMutation(new ShockwaveMutation(new AbsoluteLocation(16, 16, 16), 2));
		runner.startNextTick();
		TickRunner.Snapshot snap1 = runner.startNextTick();
		TickRunner.Snapshot snap2 =runner.waitForPreviousTick();
		runner.startNextTick();
		TickRunner.Snapshot snap3 = runner.startNextTick();
		runner.shutdown();
		
		// 1 + 6 + 36 = 43.
		Assert.assertEquals(1, snap1.committedCuboidMutationCount());
		Assert.assertEquals(6, snap2.committedCuboidMutationCount());
		Assert.assertEquals(36, snap3.committedCuboidMutationCount());
	}

	@Test
	public void shockwaveMultiCuboids()
	{
		TickRunner runner = new TickRunner(8, ServerRunner.DEFAULT_MILLIS_PER_TICK, (TickRunner.Snapshot completed) -> {});
		runner.cuboidWasLoaded(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ItemRegistry.AIR));
		runner.cuboidWasLoaded(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)-1), ItemRegistry.AIR));
		runner.cuboidWasLoaded(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)-1, (short)0), ItemRegistry.AIR));
		runner.cuboidWasLoaded(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)-1, (short)-1), ItemRegistry.AIR));
		runner.cuboidWasLoaded(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)-1, (short)0, (short)0), ItemRegistry.AIR));
		runner.cuboidWasLoaded(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)-1, (short)0, (short)-1), ItemRegistry.AIR));
		runner.cuboidWasLoaded(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)-1, (short)-1, (short)0), ItemRegistry.AIR));
		runner.cuboidWasLoaded(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)-1, (short)-1, (short)-1), ItemRegistry.AIR));
		runner.start();
		runner.waitForPreviousTick();
		// We enqueue a single shockwave in the centre of the cuboid and allow it to replicate 2 times.
		runner.enqueueMutation(new ShockwaveMutation(new AbsoluteLocation(0, 0, 0), 2));
		runner.startNextTick();
		TickRunner.Snapshot snap1 = runner.startNextTick();
		TickRunner.Snapshot snap2 = runner.startNextTick();
		TickRunner.Snapshot snap3 = runner.startNextTick();
		runner.shutdown();
		
		// 1 + 6 + 36 = 43.
		Assert.assertEquals(1, snap1.committedCuboidMutationCount());
		Assert.assertEquals(6, snap2.committedCuboidMutationCount());
		Assert.assertEquals(36, snap3.committedCuboidMutationCount());
	}

	@Test
	public void basicBlockRead()
	{
		Aspect<Short, ?> aspectShort = AspectRegistry.BLOCK;
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ItemRegistry.AIR);
		TickRunner runner = new TickRunner(1, ServerRunner.DEFAULT_MILLIS_PER_TICK, (TickRunner.Snapshot completed) -> {});
		runner.cuboidWasLoaded(cuboid);
		runner.start();
		TickRunner.Snapshot startState = runner.waitForPreviousTick();
		
		// Before we run a tick, the cuboid shouldn't yet be loaded (it is added to the new world during a tick) so we should see a null block.
		Assert.assertNull(_getBlockProxy(startState, new AbsoluteLocation(0, 0, 0)));
		
		// Run the tick so that it applies the new load.
		runner.startNextTick();
		TickRunner.Snapshot snapshot = runner.waitForPreviousTick();
		// Now, we should see a block with default properties.
		BlockProxy block = _getBlockProxy(snapshot, new AbsoluteLocation(0, 0, 0));
		Assert.assertEquals(BlockAspect.AIR, block.getData15(aspectShort));
		
		// Note that the mutation will not be enqueued in the next tick, but the following one (they are queued and picked up when the threads finish).
		runner.enqueueMutation(new ReplaceBlockMutation(new AbsoluteLocation(0, 0, 0), BlockAspect.AIR, BlockAspect.STONE));
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		runner.shutdown();
		
		// We should now see the new data.
		block = _getBlockProxy(snapshot, new AbsoluteLocation(0, 0, 0));
		Assert.assertEquals(BlockAspect.STONE, block.getData15(aspectShort));
	}

	@Test
	public void basicInventoryOperations()
	{
		// Just add, add, and remove some inventory items.
		Aspect<Inventory, ?> aspectInventory = AspectRegistry.INVENTORY;
		AbsoluteLocation testBlock = new AbsoluteLocation(0, 0, 0);
		Item stoneItem = ItemRegistry.STONE;
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ItemRegistry.AIR);
		
		// Create a tick runner with a single cuboid and get it running.
		TickRunner runner = new TickRunner(1, ServerRunner.DEFAULT_MILLIS_PER_TICK, (TickRunner.Snapshot completed) -> {});
		runner.cuboidWasLoaded(cuboid);
		runner.start();
		runner.startNextTick();
		TickRunner.Snapshot snapshot = runner.waitForPreviousTick();
		
		// Make sure that we see the null inventory.
		BlockProxy block = _getBlockProxy(snapshot, testBlock);
		Assert.assertEquals(null, block.getDataSpecial(aspectInventory));
		
		// Apply the first mutation to add data.
		snapshot = _runTickLockStep(runner, new DropItemMutation(testBlock, stoneItem, 1));
		block = _getBlockProxy(snapshot, testBlock);
		Assert.assertEquals(1, block.getDataSpecial(aspectInventory).items.get(stoneItem).count());
		
		// Try to drop too much to fit and verify that nothing changes.
		snapshot = _runTickLockStep(runner, new DropItemMutation(testBlock, stoneItem, InventoryAspect.CAPACITY_AIR / 2));
		block = _getBlockProxy(snapshot, testBlock);
		Assert.assertEquals(1, block.getDataSpecial(aspectInventory).items.get(stoneItem).count());
		
		// Add a little more data and make sure that it updates.
		snapshot = _runTickLockStep(runner, new DropItemMutation(testBlock, stoneItem, 2));
		block = _getBlockProxy(snapshot, testBlock);
		Assert.assertEquals(3, block.getDataSpecial(aspectInventory).items.get(stoneItem).count());
		
		// Remove everything and make sure that we end up with a null inventory.
		snapshot = _runTickLockStep(runner, new PickUpItemMutation(testBlock, stoneItem, 3));
		block = _getBlockProxy(snapshot, testBlock);
		Assert.assertEquals(null, block.getDataSpecial(aspectInventory));
		
		// Test is done.
		runner.shutdown();
	}

	@Test
	public void deliverWithEntity()
	{
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ItemRegistry.AIR);
		TickRunner runner = new TickRunner(1, ServerRunner.DEFAULT_MILLIS_PER_TICK, (TickRunner.Snapshot completed) -> {});
		runner.cuboidWasLoaded(cuboid);
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
		runner.enqueueEntityChange(entityId, new EntityChangeMutation(new ReplaceBlockMutation(changeLocation, BlockAspect.AIR, BlockAspect.STONE)), commit1);
		
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
		
		Assert.assertEquals(BlockAspect.STONE, _getBlockProxy(snapshot, changeLocation).getData15(AspectRegistry.BLOCK));
	}

	@Test
	public void dependentEntityChanges()
	{
		TickRunner runner = new TickRunner(1, ServerRunner.DEFAULT_MILLIS_PER_TICK, (TickRunner.Snapshot completed) -> {});
		runner.start();
		
		// We need 2 entities for this but we will give one some items.
		int entityId = 1;
		Inventory startInventory = Inventory.start(10).add(ItemRegistry.STONE, 2).finish();
		runner.entityDidJoin(new Entity(0, EntityActionValidator.DEFAULT_LOCATION, 0.0f, EntityActionValidator.DEFAULT_VOLUME, EntityActionValidator.DEFAULT_BLOCKS_PER_TICK_SPEED, startInventory, null));
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
		// (run a tick to run the final change)
		runner.startNextTick();
		TickRunner.Snapshot finalSnapshot = runner.waitForPreviousTick();
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
	public void phasedChangeBlockBreak()
	{
		// NOTE:  Technically, multi-phase changes are managed by the component above the TickRunner but this test has
		// been adapted to show how they would use TickRunner to accomplish this.
		
		// We will show how the TickRunner's caller would schedule a normal change as well as a multi-phase change, to highlight the differences.
		TickRunner runner = new TickRunner(1, ServerRunner.DEFAULT_MILLIS_PER_TICK, (TickRunner.Snapshot completed) -> {});
		
		// Create a cuboid of stone.
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ItemRegistry.STONE);
		runner.cuboidWasLoaded(cuboid);
		
		// We can use the default entity since we don't yet check that they are standing in solid rock.
		int entityId = 1;
		Entity entity = EntityActionValidator.buildDefaultEntity(entityId);
		runner.entityDidJoin(entity);
		
		// Start up and run the first tick so that these get loaded.
		runner.start();
		runner.startNextTick();
		runner.waitForPreviousTick();
		
		// We will now show how to schedule the multi-phase change.
		AbsoluteLocation changeLocation1 = new AbsoluteLocation(0, 0, 0);
		EndBreakBlockChange longRunningChange = new EndBreakBlockChange(changeLocation1, ItemRegistry.STONE);
		long commit1 = 1L;
		runner.enqueueEntityChange(entityId, longRunningChange, commit1);
		
		// We now run the tick.
		runner.startNextTick();
		TickRunner.Snapshot snapshot = runner.waitForPreviousTick();
		// The change didn't commit yet.
		Assert.assertEquals(0L, snapshot.commitLevels().get(entityId).longValue());
		
		// Nothing should have changed.
		BlockProxy proxy1 = _getBlockProxy(snapshot, changeLocation1);
		Assert.assertEquals(BlockAspect.STONE, proxy1.getData15(AspectRegistry.BLOCK));
		Assert.assertNull(proxy1.getDataSpecial(AspectRegistry.INVENTORY));
		
		// So long as we don't enqueue a cancel, we should see this run in the next tick.
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(commit1, snapshot.commitLevels().get(entityId).longValue());
		Assert.assertEquals(1, snapshot.committedEntityMutationCount());
		
		// The mutation has been scheduled but not run, so the block should be the same.
		proxy1 = _getBlockProxy(snapshot, changeLocation1);
		Assert.assertEquals(BlockAspect.STONE, proxy1.getData15(AspectRegistry.BLOCK));
		Assert.assertNull(proxy1.getDataSpecial(AspectRegistry.INVENTORY));
		
		// Run another tick for the final mutation this scheduled to take effect.
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(commit1, snapshot.commitLevels().get(entityId).longValue());
		Assert.assertEquals(1, snapshot.committedCuboidMutationCount());
		
		// We should see the result.
		proxy1 = _getBlockProxy(snapshot, changeLocation1);
		Assert.assertEquals(BlockAspect.AIR, proxy1.getData15(AspectRegistry.BLOCK));
		Inventory inv = proxy1.getDataSpecial(AspectRegistry.INVENTORY);
		Assert.assertEquals(1, inv.items.size());
		Assert.assertEquals(1, inv.items.get(ItemRegistry.STONE).count());
		
		
		// Now that the multi-phase has completed, here is how a caller would schedule a normal change (just to see how it completed).
		// Create the change to deliver a basic mutation.
		AbsoluteLocation changeLocation2 = new AbsoluteLocation(0, 0, 2);
		EntityChangeMutation singleChange = new EntityChangeMutation(new ReplaceBlockMutation(changeLocation2, BlockAspect.STONE, BlockAspect.AIR));
		long commit2 = 2L;
		runner.enqueueEntityChange(entityId, singleChange, commit2);
		
		// Run the tick.
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(commit2, snapshot.commitLevels().get(entityId).longValue());
		Assert.assertEquals(1, snapshot.committedEntityMutationCount());
		
		// When the change completes, the caller would use that stored commit level to update its per-client commit level.
		// In our case, we will just proceed to run another tick to see the mutation change the block value.
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		Assert.assertEquals(commit2, snapshot.commitLevels().get(entityId).longValue());
		Assert.assertEquals(1, snapshot.committedCuboidMutationCount());
		BlockProxy proxy2 = _getBlockProxy(snapshot, changeLocation2);
		Assert.assertEquals(BlockAspect.AIR, proxy2.getData15(AspectRegistry.BLOCK));
		
		
		// Shutdown and observe expected results.
		runner.shutdown();
	}

	@Test
	public void checkSnapshotDelta()
	{
		TickRunner.Snapshot[] snapshotRef = new TickRunner.Snapshot[1];
		Consumer<TickRunner.Snapshot> snapshotListener = (TickRunner.Snapshot completed) -> {
			snapshotRef[0] = completed;
		};
		TickRunner runner = new TickRunner(1, ServerRunner.DEFAULT_MILLIS_PER_TICK, snapshotListener);
		CuboidAddress targetAddress = new CuboidAddress((short)0, (short)0, (short)0);
		runner.cuboidWasLoaded(CuboidGenerator.createFilledCuboid(targetAddress, ItemRegistry.AIR));
		CuboidAddress constantAddress = new CuboidAddress((short)0, (short)0, (short)1);
		runner.cuboidWasLoaded(CuboidGenerator.createFilledCuboid(constantAddress, ItemRegistry.AIR));
		
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
		runner.enqueueMutation(new ReplaceBlockMutation(new AbsoluteLocation(0, 0, 0), BlockAspect.AIR, BlockAspect.STONE));
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
		TickRunner runner = new TickRunner(1, ServerRunner.DEFAULT_MILLIS_PER_TICK, snapshotListener);
		
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


	private TickRunner.Snapshot _runTickLockStep(TickRunner runner, IMutationBlock mutation)
	{
		// This helper is useful when a test wants to be certain that a mutation has completed before checking state.
		// 1) Wait for any in-flight tick to complete.
		runner.waitForPreviousTick();
		// 2) Enqueue the mutation to be picked up by the next tick.
		runner.enqueueMutation(mutation);
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
