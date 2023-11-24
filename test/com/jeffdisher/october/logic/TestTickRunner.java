package com.jeffdisher.october.logic;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.aspects.InventoryAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IOctree;
import com.jeffdisher.october.data.OctreeObject;
import com.jeffdisher.october.data.OctreeShort;
import com.jeffdisher.october.registries.AspectRegistry;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;


public class TestTickRunner
{
	@Test
	public void basicOneCuboid()
	{
		OctreeShort data = OctreeShort.create(BlockAspect.AIR);
		CountingWorldListener blockListener = new CountingWorldListener();
		TickRunner runner = new TickRunner(1, blockListener, new CountingEntityListener());
		runner.cuboidWasLoaded(new CuboidState(CuboidData.createNew(new CuboidAddress((short)0, (short)0, (short)0), new IOctree[] { data })));
		runner.start();
		runner.startNextTick();
		// Note that the mutation will not be enqueued in the next tick, but the following one (they are queued and picked up when the threads finish).
		runner.enqueueMutation(new ReplaceBlockMutation(new AbsoluteLocation(0, 0, 0), BlockAspect.AIR, BlockAspect.STONE));
		runner.startNextTick();
		runner.startNextTick();
		runner.shutdown();
		
		Assert.assertEquals(1, blockListener.blockChanged.get());
		Assert.assertEquals(0, blockListener.mutationDropped.get());
	}

	@Test
	public void shockwaveOneCuboid()
	{
		OctreeShort data = OctreeShort.create(BlockAspect.AIR);
		CountingWorldListener blockListener = new CountingWorldListener();
		TickRunner runner = new TickRunner(1, blockListener, new CountingEntityListener());
		runner.cuboidWasLoaded(new CuboidState(CuboidData.createNew(new CuboidAddress((short)0, (short)0, (short)0), new IOctree[] { data })));
		runner.start();
		runner.startNextTick();
		// Note that the mutation will not be enqueued in the next tick, but the following one (they are queued and picked up when the threads finish).
		// We enqueue a single shockwave in the centre of the cuboid and allow it to replicate 2 times.
		runner.enqueueMutation(new ShockwaveMutation(new AbsoluteLocation(16, 16, 16), true, 2));
		runner.waitForPreviousTick();
		runner.startNextTick();
		runner.startNextTick();
		runner.startNextTick();
		runner.startNextTick();
		runner.waitForPreviousTick();
		runner.startNextTick();
		runner.shutdown();
		
		// 1 + 6 + 36 = 43.
		Assert.assertEquals(43, blockListener.blockChanged.get());
		Assert.assertEquals(0, blockListener.mutationDropped.get());
	}

	@Test
	public void shockwaveMultiCuboids()
	{
		OctreeShort data = OctreeShort.create(BlockAspect.AIR);
		CountingWorldListener blockListener = new CountingWorldListener();
		TickRunner runner = new TickRunner(8, blockListener, new CountingEntityListener());
		runner.cuboidWasLoaded(new CuboidState(CuboidData.createNew(new CuboidAddress((short)0, (short)0, (short)0), new IOctree[] { data })));
		runner.cuboidWasLoaded(new CuboidState(CuboidData.createNew(new CuboidAddress((short)0, (short)0, (short)-1), new IOctree[] { data })));
		runner.cuboidWasLoaded(new CuboidState(CuboidData.createNew(new CuboidAddress((short)0, (short)-1, (short)0), new IOctree[] { data })));
		runner.cuboidWasLoaded(new CuboidState(CuboidData.createNew(new CuboidAddress((short)0, (short)-1, (short)-1), new IOctree[] { data })));
		runner.cuboidWasLoaded(new CuboidState(CuboidData.createNew(new CuboidAddress((short)-1, (short)0, (short)0), new IOctree[] { data })));
		runner.cuboidWasLoaded(new CuboidState(CuboidData.createNew(new CuboidAddress((short)-1, (short)0, (short)-1), new IOctree[] { data })));
		runner.cuboidWasLoaded(new CuboidState(CuboidData.createNew(new CuboidAddress((short)-1, (short)-1, (short)0), new IOctree[] { data })));
		runner.cuboidWasLoaded(new CuboidState(CuboidData.createNew(new CuboidAddress((short)-1, (short)-1, (short)-1), new IOctree[] { data })));
		runner.start();
		runner.startNextTick();
		// Note that the mutation will not be enqueued in the next tick, but the following one (they are queued and picked up when the threads finish).
		// We enqueue a single shockwave in the centre of the cuboid and allow it to replicate 2 times.
		runner.enqueueMutation(new ShockwaveMutation(new AbsoluteLocation(0, 0, 0), true, 2));
		runner.startNextTick();
		runner.startNextTick();
		runner.waitForPreviousTick();
		runner.startNextTick();
		runner.startNextTick();
		runner.startNextTick();
		runner.shutdown();
		
		// 1 + 6 + 36 = 43.
		Assert.assertEquals(43, blockListener.blockChanged.get());
		Assert.assertEquals(0, blockListener.mutationDropped.get());
	}

	@Test
	public void basicBlockRead()
	{
		Aspect<Short, ?> aspectShort = AspectRegistry.BLOCK;
		OctreeShort data = OctreeShort.create(BlockAspect.AIR);
		TickRunner runner = new TickRunner(1, new CountingWorldListener(), new CountingEntityListener());
		runner.cuboidWasLoaded(new CuboidState(CuboidData.createNew(new CuboidAddress((short)0, (short)0, (short)0), new IOctree[] { data })));
		runner.start();
		
		// Before we run a tick, the cuboid shouldn't yet be loaded (it is added to the new world during a tick) so we should see a null block.
		Assert.assertNull(runner.getBlockProxy(new AbsoluteLocation(0, 0, 0)));
		
		// We need to run the tick twice to make sure that we wait for the first to finish before the query.
		runner.startNextTick();
		runner.startNextTick();
		// Now, we should see a block with default properties.
		BlockProxy block = runner.getBlockProxy(new AbsoluteLocation(0, 0, 0));
		Assert.assertEquals(BlockAspect.AIR, block.getData15(aspectShort));
		
		// Note that the mutation will not be enqueued in the next tick, but the following one (they are queued and picked up when the threads finish).
		runner.enqueueMutation(new ReplaceBlockMutation(new AbsoluteLocation(0, 0, 0), BlockAspect.AIR, BlockAspect.STONE));
		runner.startNextTick();
		runner.startNextTick();
		runner.waitForPreviousTick();
		runner.shutdown();
		
		// We should now see the new data.
		block = runner.getBlockProxy(new AbsoluteLocation(0, 0, 0));
		Assert.assertEquals(BlockAspect.STONE, block.getData15(aspectShort));
	}

	@Test
	public void basicInventoryOperations()
	{
		// Just add, add, and remove some inventory items.
		Aspect<Inventory, ?> aspectInventory = AspectRegistry.INVENTORY;
		OctreeShort blockData = OctreeShort.create((short)0);
		OctreeObject inventoryData = OctreeObject.create();
		AbsoluteLocation testBlock = new AbsoluteLocation(0, 0, 0);
		Item stoneItem = ItemRegistry.STONE;
		
		// Create a tick runner with a single cuboid and get it running.
		TickRunner runner = new TickRunner(1, new CountingWorldListener(), new CountingEntityListener());
		runner.cuboidWasLoaded(new CuboidState(CuboidData.createNew(new CuboidAddress((short)0, (short)0, (short)0), new IOctree[] { blockData, inventoryData })));
		runner.start();
		runner.startNextTick();
		runner.startNextTick();
		
		// Make sure that we see the null inventory.
		BlockProxy block = runner.getBlockProxy(testBlock);
		Assert.assertEquals(null, block.getDataSpecial(aspectInventory));
		
		// Apply the first mutation to add data.
		_runTickLockStep(runner, new DropItemMutation(testBlock, stoneItem, 1));
		block = runner.getBlockProxy(testBlock);
		Assert.assertEquals(1, block.getDataSpecial(aspectInventory).items.get(0).count());
		
		// Try to drop too much to fit and verify that nothing changes.
		_runTickLockStep(runner, new DropItemMutation(testBlock, stoneItem, InventoryAspect.CAPACITY_AIR / 2));
		block = runner.getBlockProxy(testBlock);
		Assert.assertEquals(1, block.getDataSpecial(aspectInventory).items.get(0).count());
		
		// Add a little more data and make sure that it updates.
		_runTickLockStep(runner, new DropItemMutation(testBlock, stoneItem, 2));
		block = runner.getBlockProxy(testBlock);
		Assert.assertEquals(3, block.getDataSpecial(aspectInventory).items.get(0).count());
		
		// Remove everything and make sure that we end up with a null inventory.
		_runTickLockStep(runner, new PickUpItemMutation(testBlock, stoneItem, 3));
		block = runner.getBlockProxy(testBlock);
		Assert.assertEquals(null, block.getDataSpecial(aspectInventory));
		
		// Test is done.
		runner.shutdown();
	}

	@Test
	public void deliverWithEntity()
	{
		OctreeShort data = OctreeShort.create(BlockAspect.AIR);
		CountingWorldListener blockListener = new CountingWorldListener();
		CountingEntityListener entityListener = new CountingEntityListener();
		TickRunner runner = new TickRunner(1, blockListener, entityListener);
		runner.cuboidWasLoaded(new CuboidState(CuboidData.createNew(new CuboidAddress((short)0, (short)0, (short)0), new IOctree[] { data })));
		runner.start();
		
		// Have a new entity join and wait for them to be added.
		int entityId = 1;
		Entity entity = EntityActionValidator.buildDefaultEntity(entityId);
		runner.entityDidJoin(entity);
		runner.startNextTick();
		runner.waitForPreviousTick();
		
		// Now, add a mutation from this entity to deliver the block replacement mutation.
		AbsoluteLocation changeLocation = new AbsoluteLocation(0, 0, 0);
		runner.enqueueEntityChange(new EntityChangeMutation(entityId, new ReplaceBlockMutation(changeLocation, BlockAspect.AIR, BlockAspect.STONE)));
		
		// This will take a few ticks to be observable:
		// -after tick 1, the change will be queued
		runner.startNextTick();
		// -after tick 2, the change will have been run and the mutation enqueued
		runner.startNextTick();
		// -after tick 3, the mutation will have been committed
		runner.startNextTick();
		
		// Shutdown and observe expected results.
		runner.shutdown();
		
		Assert.assertEquals(1, blockListener.blockChanged.get());
		Assert.assertEquals(0, blockListener.mutationDropped.get());
		Assert.assertEquals(1, entityListener.entityChanged.get());
		Assert.assertEquals(0, entityListener.changeDropped.get());
		Assert.assertEquals(BlockAspect.STONE, runner.getBlockProxy(changeLocation).getData15(AspectRegistry.BLOCK));
	}


	private void _runTickLockStep(TickRunner runner, IMutation mutation)
	{
		// This helper is useful when a test wants to be certain that a mutation has completed before checking state.
		// 1) Wait for any in-flight tick to complete.
		runner.waitForPreviousTick();
		// 2) Enqueue the mutation to be picked up by the next tick.
		runner.enqueueMutation(mutation);
		// 3) Run a tick to pick up the new mutation and schedule it.
		runner.startNextTick();
		// 4) Run the tick which will execute the mutation.
		runner.startNextTick();
		// 5) Wait for this tick to complete in order to rely on the result being observable.
		runner.waitForPreviousTick();
	}


	private static class CountingWorldListener implements WorldState.IBlockChangeListener
	{
		public AtomicInteger blockChanged = new AtomicInteger(0);
		public AtomicInteger mutationDropped = new AtomicInteger(0);
		
		@Override
		public void blockChanged(AbsoluteLocation location)
		{
			blockChanged.incrementAndGet();
		}
		@Override
		public void mutationDropped(IMutation mutation)
		{
			mutationDropped.incrementAndGet();
		}
	}

	private static class CountingEntityListener implements CrowdState.IEntityChangeListener
	{
		public AtomicInteger entityChanged = new AtomicInteger(0);
		public AtomicInteger changeDropped = new AtomicInteger(0);
		
		@Override
		public void entityChanged(int id)
		{
			entityChanged.incrementAndGet();
		}
		@Override
		public void changeDropped(IEntityChange change)
		{
			changeDropped.incrementAndGet();
		}
	}
}
