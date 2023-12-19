package com.jeffdisher.october.server;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.aspects.InventoryAspect;
import com.jeffdisher.october.changes.BeginBreakBlockChange;
import com.jeffdisher.october.changes.EndBreakBlockChange;
import com.jeffdisher.october.changes.EntityChangeMutation;
import com.jeffdisher.october.changes.IEntityChange;
import com.jeffdisher.october.changes.MetaChangePhase1;
import com.jeffdisher.october.changes.MetaChangePhase2;
import com.jeffdisher.october.changes.MetaChangeStandard;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.CrowdProcessor;
import com.jeffdisher.october.logic.EntityActionValidator;
import com.jeffdisher.october.logic.EntityChangeSendItem;
import com.jeffdisher.october.logic.ShockwaveMutation;
import com.jeffdisher.october.logic.WorldProcessor;
import com.jeffdisher.october.mutations.DropItemMutation;
import com.jeffdisher.october.mutations.IMutation;
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


public class TestTickRunner
{
	@Test
	public void basicOneCuboid()
	{
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ItemRegistry.AIR);
		CountingWorldListener blockListener = new CountingWorldListener();
		TickRunner runner = new TickRunner(1, 100L, blockListener, new CountingEntityListener(), (TickRunner.Snapshot completed) -> {});
		runner.cuboidWasLoaded(cuboid);
		runner.start();
		runner.waitForPreviousTick();
		// The mutation will be run in the next tick since there isn't one running.
		runner.enqueueMutation(new ReplaceBlockMutation(new AbsoluteLocation(0, 0, 0), BlockAspect.AIR, BlockAspect.STONE));
		runner.startNextTick();
		runner.shutdown();
		
		Assert.assertEquals(1, blockListener.mutationApplied.get());
		Assert.assertEquals(0, blockListener.mutationDropped.get());
	}

	@Test
	public void shockwaveOneCuboid()
	{
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ItemRegistry.AIR);
		CountingWorldListener blockListener = new CountingWorldListener();
		TickRunner runner = new TickRunner(1, 100L, blockListener, new CountingEntityListener(), (TickRunner.Snapshot completed) -> {});
		runner.cuboidWasLoaded(cuboid);
		runner.start();
		runner.waitForPreviousTick();
		// We enqueue a single shockwave in the centre of the cuboid and allow it to replicate 2 times.
		runner.enqueueMutation(new ShockwaveMutation(new AbsoluteLocation(16, 16, 16), 2));
		runner.startNextTick();
		runner.startNextTick();
		runner.waitForPreviousTick();
		runner.startNextTick();
		runner.shutdown();
		
		// 1 + 6 + 36 = 43.
		Assert.assertEquals(43, blockListener.mutationApplied.get());
		Assert.assertEquals(0, blockListener.mutationDropped.get());
	}

	@Test
	public void shockwaveMultiCuboids()
	{
		CountingWorldListener blockListener = new CountingWorldListener();
		TickRunner runner = new TickRunner(8, 100L, blockListener, new CountingEntityListener(), (TickRunner.Snapshot completed) -> {});
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
		runner.startNextTick();
		runner.startNextTick();
		runner.startNextTick();
		runner.shutdown();
		
		// 1 + 6 + 36 = 43.
		Assert.assertEquals(43, blockListener.mutationApplied.get());
		Assert.assertEquals(0, blockListener.mutationDropped.get());
	}

	@Test
	public void basicBlockRead()
	{
		Aspect<Short, ?> aspectShort = AspectRegistry.BLOCK;
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ItemRegistry.AIR);
		TickRunner runner = new TickRunner(1, 100L, new CountingWorldListener(), new CountingEntityListener(), (TickRunner.Snapshot completed) -> {});
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
		TickRunner runner = new TickRunner(1, 100L, new CountingWorldListener(), new CountingEntityListener(), (TickRunner.Snapshot completed) -> {});
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
		CountingWorldListener blockListener = new CountingWorldListener();
		CountingEntityListener entityListener = new CountingEntityListener();
		TickRunner runner = new TickRunner(1, 100L, blockListener, entityListener, (TickRunner.Snapshot completed) -> {});
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
		runner.enqueueEntityChange(entityId, new EntityChangeMutation(new ReplaceBlockMutation(changeLocation, BlockAspect.AIR, BlockAspect.STONE)));
		
		// This will take a few ticks to be observable:
		// -after tick 1, the change will have been run and the mutation enqueued
		runner.startNextTick();
		// -after tick 2, the mutation will have been committed
		runner.startNextTick();
		TickRunner.Snapshot snapshot = runner.waitForPreviousTick();
		
		// Shutdown and observe expected results.
		runner.shutdown();
		
		Assert.assertEquals(1, blockListener.mutationApplied.get());
		Assert.assertEquals(0, blockListener.mutationDropped.get());
		Assert.assertEquals(1, entityListener.changeApplied.get());
		Assert.assertEquals(0, entityListener.changeDropped.get());
		Assert.assertEquals(BlockAspect.STONE, _getBlockProxy(snapshot, changeLocation).getData15(AspectRegistry.BLOCK));
	}

	@Test
	public void dependentEntityChanges()
	{
		CountingEntityListener entityListener = new CountingEntityListener();
		TickRunner runner = new TickRunner(1, 100L, new CountingWorldListener(), entityListener, (TickRunner.Snapshot completed) -> {});
		runner.start();
		
		// We need 2 entities for this but we will give one some items.
		Inventory startInventory = new Inventory(10, Map.of(ItemRegistry.STONE, new Items(ItemRegistry.STONE, 2)), 2 * ItemRegistry.STONE.encumbrance());
		runner.entityDidJoin(new Entity(0, EntityActionValidator.DEFAULT_LOCATION, EntityActionValidator.DEFAULT_VOLUME, EntityActionValidator.DEFAULT_BLOCKS_PER_TICK_SPEED, startInventory));
		runner.entityDidJoin(EntityActionValidator.buildDefaultEntity(1));
		// (run a tick to pick up the users)
		runner.startNextTick();
		runner.waitForPreviousTick();
		
		// Try to pass the items to the other entity.
		IEntityChange send = new EntityChangeSendItem(1, ItemRegistry.STONE);
		runner.enqueueEntityChange(0, send);
		// (run a tick to run the change and enqueue the next)
		runner.startNextTick();
		// (run a tick to run the final change)
		runner.startNextTick();
		TickRunner.Snapshot finalSnapshot = runner.waitForPreviousTick();
		runner.shutdown();
		
		// Now, check for results.
		Assert.assertEquals(2, entityListener.changeApplied.get());
		Assert.assertEquals(0, entityListener.changeDropped.get());
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
		CountingWorldListener blockListener = new CountingWorldListener();
		CountingEntityListener entityListener = new CountingEntityListener();
		TickRunner runner = new TickRunner(1, 100L, blockListener, entityListener, (TickRunner.Snapshot completed) -> {});
		
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
		BeginBreakBlockChange firstPhase = new BeginBreakBlockChange(changeLocation1);
		
		// Note that we use the commit level _as_ the activity ID since it is a monotonic counter (if there could be 2
		// overlapping multi-phase changes, which could finish out of order, this wouldn't work).
		long activityId1 = 1;
		
		// The first thing is that the first phase change must be wrapped in a MetaChangePhase1.
		// In actual integration, the caller will use this to scoop out the second-phase request and delay.
		MetaChangePhase1 meta1 = new MetaChangePhase1(firstPhase, entityId, activityId1);
		runner.enqueueEntityChange(entityId, meta1);
		
		// We now run the tick.
		runner.startNextTick();
		TickRunner.Snapshot snapshot = runner.waitForPreviousTick();
		
		// Nothing should have changed.
		BlockProxy proxy1 = _getBlockProxy(snapshot, changeLocation1);
		Assert.assertEquals(BlockAspect.STONE, proxy1.getData15(AspectRegistry.BLOCK));
		Assert.assertNull(proxy1.getDataSpecial(AspectRegistry.INVENTORY));
		
		// Scoop out the phase2 and verify it is what the implementation requests.
		// The caller tracks this information and schedules the phase2 if nothing else comes in from this entity before the delay expires.
		IEntityChange secondPhase = meta1.phase2;
		Assert.assertTrue(secondPhase instanceof EndBreakBlockChange);
		Assert.assertEquals(100L, meta1.phase2DelayMillis);
		
		// Now, for the second phase, we wrap that and schedule, as well.
		MetaChangePhase2 meta2 = new MetaChangePhase2(meta1.phase2, entityId, activityId1);
		runner.enqueueEntityChange(entityId, meta2);
		
		// We now run the tick.
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		
		// This should have succeeded.  The caller would use this to determine that the latest activityId has advanced.
		Assert.assertTrue(meta2.wasSuccess);
		
		// The mutation has been scheduled but not run, so the block should be the same.
		proxy1 = _getBlockProxy(snapshot, changeLocation1);
		Assert.assertEquals(BlockAspect.STONE, proxy1.getData15(AspectRegistry.BLOCK));
		Assert.assertNull(proxy1.getDataSpecial(AspectRegistry.INVENTORY));
		
		// Run another tick for the final mutation this scheduled to take effect.
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		
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
		
		// We wrap it in a "standard" meta-change.
		long activityId2 = activityId1 + 1;
		MetaChangeStandard standard = new MetaChangeStandard(singleChange, entityId, activityId2);
		runner.enqueueEntityChange(entityId, standard);
		
		// Run the tick.
		runner.startNextTick();
		runner.waitForPreviousTick();
		
		// When the change completes, the caller would use that stored commit level to update its per-client commit level.
		// In our case, we will just proceed to run another tick to see the mutation change the block value.
		runner.startNextTick();
		snapshot = runner.waitForPreviousTick();
		BlockProxy proxy2 = _getBlockProxy(snapshot, changeLocation2);
		Assert.assertEquals(BlockAspect.AIR, proxy2.getData15(AspectRegistry.BLOCK));
		
		
		// Shutdown and observe expected results.
		runner.shutdown();
		
		// Verify remaining counts.
		Assert.assertEquals(2, blockListener.mutationApplied.get());
		Assert.assertEquals(0, blockListener.mutationDropped.get());
		Assert.assertEquals(3, entityListener.changeApplied.get());
		Assert.assertEquals(0, entityListener.changeDropped.get());
	}

	@Test
	public void checkSnapshotDelta()
	{
		TickRunner.Snapshot[] snapshotRef = new TickRunner.Snapshot[1];
		Consumer<TickRunner.Snapshot> snapshotListener = (TickRunner.Snapshot completed) -> {
			snapshotRef[0] = completed;
		};
		TickRunner runner = new TickRunner(1, 100L, new CountingWorldListener(), new CountingEntityListener(), snapshotListener);
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


	private TickRunner.Snapshot _runTickLockStep(TickRunner runner, IMutation mutation)
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


	private static class CountingWorldListener implements WorldProcessor.IBlockChangeListener
	{
		public AtomicInteger mutationApplied = new AtomicInteger(0);
		public AtomicInteger mutationDropped = new AtomicInteger(0);
		
		@Override
		public void mutationApplied(IMutation mutation)
		{
			mutationApplied.incrementAndGet();
		}
		@Override
		public void mutationDropped(IMutation mutation)
		{
			mutationDropped.incrementAndGet();
		}
	}

	private static class CountingEntityListener implements CrowdProcessor.IEntityChangeListener
	{
		public AtomicInteger changeApplied = new AtomicInteger(0);
		public AtomicInteger changeDropped = new AtomicInteger(0);
		
		@Override
		public void changeApplied(int targetEntityId, IEntityChange change)
		{
			changeApplied.incrementAndGet();
		}
		@Override
		public void changeDropped(int targetEntityId, IEntityChange change)
		{
			changeDropped.incrementAndGet();
		}
	}
}
