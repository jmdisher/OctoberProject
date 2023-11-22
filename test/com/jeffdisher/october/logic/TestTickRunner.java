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
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;


public class TestTickRunner
{
	@Test
	public void basicOneCuboid()
	{
		Aspect<Short> aspectShort = AspectRegistry.BLOCK;
		OctreeShort data = OctreeShort.create(BlockAspect.AIR);
		int[] changeData = new int[2];
		TickRunner runner = new TickRunner(1, new WorldState.IBlockChangeListener() {
			@Override
			public void blockChanged(AbsoluteLocation location)
			{
				changeData[0] += 1;
			}
			@Override
			public void mutationDropped(IMutation mutation)
			{
				changeData[1] += 1;
			}});
		runner.cuboidWasLoaded(new CuboidState(CuboidData.createNew(new CuboidAddress((short)0, (short)0, (short)0), new IOctree[] { data })));
		runner.start();
		runner.runTick();
		// Note that the mutation will not be enqueued in the next tick, but the following one (they are queued and picked up when the threads finish).
		runner.enqueueMutation(new PlaceBlockMutation(new AbsoluteLocation(0, 0, 0), aspectShort, BlockAspect.STONE));
		runner.runTick();
		runner.runTick();
		runner.shutdown();
		
		Assert.assertEquals(1, changeData[0]);
		Assert.assertEquals(0, changeData[1]);
	}

	@Test
	public void shockwaveOneCuboid()
	{
		OctreeShort data = OctreeShort.create(BlockAspect.AIR);
		int[] changeData = new int[2];
		TickRunner runner = new TickRunner(1, new WorldState.IBlockChangeListener() {
			@Override
			public void blockChanged(AbsoluteLocation location)
			{
				changeData[0] += 1;
			}
			@Override
			public void mutationDropped(IMutation mutation)
			{
				changeData[1] += 1;
			}});
		runner.cuboidWasLoaded(new CuboidState(CuboidData.createNew(new CuboidAddress((short)0, (short)0, (short)0), new IOctree[] { data })));
		runner.start();
		runner.runTick();
		// Note that the mutation will not be enqueued in the next tick, but the following one (they are queued and picked up when the threads finish).
		// We enqueue a single shockwave in the centre of the cuboid and allow it to replicate 2 times.
		runner.enqueueMutation(new ShockwaveMutation(new AbsoluteLocation(16, 16, 16), true, 2));
		runner.runTick();
		runner.runTick();
		runner.runTick();
		runner.runTick();
		runner.runTick();
		runner.shutdown();
		
		// 1 + 6 + 36 = 43.
		Assert.assertEquals(43, changeData[0]);
		Assert.assertEquals(0, changeData[1]);
	}

	@Test
	public void shockwaveMultiCuboids()
	{
		OctreeShort data = OctreeShort.create(BlockAspect.AIR);
		AtomicInteger[] changeData = new AtomicInteger[] { new AtomicInteger(0), new AtomicInteger(0) };
		TickRunner runner = new TickRunner(8, new WorldState.IBlockChangeListener() {
			@Override
			public void blockChanged(AbsoluteLocation location)
			{
				changeData[0].incrementAndGet();
			}
			@Override
			public void mutationDropped(IMutation mutation)
			{
				changeData[1].incrementAndGet();
			}});
		runner.cuboidWasLoaded(new CuboidState(CuboidData.createNew(new CuboidAddress((short)0, (short)0, (short)0), new IOctree[] { data })));
		runner.cuboidWasLoaded(new CuboidState(CuboidData.createNew(new CuboidAddress((short)0, (short)0, (short)-1), new IOctree[] { data })));
		runner.cuboidWasLoaded(new CuboidState(CuboidData.createNew(new CuboidAddress((short)0, (short)-1, (short)0), new IOctree[] { data })));
		runner.cuboidWasLoaded(new CuboidState(CuboidData.createNew(new CuboidAddress((short)0, (short)-1, (short)-1), new IOctree[] { data })));
		runner.cuboidWasLoaded(new CuboidState(CuboidData.createNew(new CuboidAddress((short)-1, (short)0, (short)0), new IOctree[] { data })));
		runner.cuboidWasLoaded(new CuboidState(CuboidData.createNew(new CuboidAddress((short)-1, (short)0, (short)-1), new IOctree[] { data })));
		runner.cuboidWasLoaded(new CuboidState(CuboidData.createNew(new CuboidAddress((short)-1, (short)-1, (short)0), new IOctree[] { data })));
		runner.cuboidWasLoaded(new CuboidState(CuboidData.createNew(new CuboidAddress((short)-1, (short)-1, (short)-1), new IOctree[] { data })));
		runner.start();
		runner.runTick();
		// Note that the mutation will not be enqueued in the next tick, but the following one (they are queued and picked up when the threads finish).
		// We enqueue a single shockwave in the centre of the cuboid and allow it to replicate 2 times.
		runner.enqueueMutation(new ShockwaveMutation(new AbsoluteLocation(0, 0, 0), true, 2));
		runner.runTick();
		runner.runTick();
		runner.runTick();
		runner.runTick();
		runner.runTick();
		runner.shutdown();
		
		// 1 + 6 + 36 = 43.
		Assert.assertEquals(43, changeData[0].get());
		Assert.assertEquals(0, changeData[1].get());
	}

	@Test
	public void basicBlockRead()
	{
		Aspect<Short> aspectShort = AspectRegistry.BLOCK;
		OctreeShort data = OctreeShort.create(BlockAspect.AIR);
		TickRunner runner = new TickRunner(1, new WorldState.IBlockChangeListener() {
			@Override
			public void blockChanged(AbsoluteLocation location)
			{
			}
			@Override
			public void mutationDropped(IMutation mutation)
			{
			}});
		runner.cuboidWasLoaded(new CuboidState(CuboidData.createNew(new CuboidAddress((short)0, (short)0, (short)0), new IOctree[] { data })));
		runner.start();
		
		// Before we run a tick, the cuboid shouldn't yet be loaded (it is added to the new world during a tick) so we should see a null block.
		Assert.assertNull(runner.getBlockProxy(new AbsoluteLocation(0, 0, 0)));
		
		// We need to run the tick twice to make sure that we wait for the first to finish before the query.
		runner.runTick();
		runner.runTick();
		// Now, we should see a block with default properties.
		BlockProxy block = runner.getBlockProxy(new AbsoluteLocation(0, 0, 0));
		Assert.assertEquals((short)0, block.getData15(aspectShort));
		
		// Note that the mutation will not be enqueued in the next tick, but the following one (they are queued and picked up when the threads finish).
		runner.enqueueMutation(new PlaceBlockMutation(new AbsoluteLocation(0, 0, 0), aspectShort, BlockAspect.STONE));
		runner.runTick();
		runner.runTick();
		runner.shutdown();
		
		// We should now see the new data.
		block = runner.getBlockProxy(new AbsoluteLocation(0, 0, 0));
		Assert.assertEquals(BlockAspect.STONE, block.getData15(aspectShort));
	}

	@Test
	public void basicInventoryOperations()
	{
		// Just add, add, and remove some inventory items.
		Aspect<Inventory> aspectInventory = AspectRegistry.INVENTORY;
		OctreeShort blockData = OctreeShort.create((short)0);
		OctreeObject inventoryData = OctreeObject.create();
		AbsoluteLocation testBlock = new AbsoluteLocation(0, 0, 0);
		Item stoneItem = ItemRegistry.STONE;
		
		// Create a tick runner with a single cuboid and get it running.
		TickRunner runner = new TickRunner(1, new WorldState.IBlockChangeListener() {
			@Override
			public void blockChanged(AbsoluteLocation location)
			{
			}
			@Override
			public void mutationDropped(IMutation mutation)
			{
			}});
		runner.cuboidWasLoaded(new CuboidState(CuboidData.createNew(new CuboidAddress((short)0, (short)0, (short)0), new IOctree[] { blockData, inventoryData })));
		runner.start();
		runner.runTick();
		runner.runTick();
		
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


	private void _runTickLockStep(TickRunner runner, IMutation mutation)
	{
		// This helper is useful when a test wants to be certain that a mutation has completed before checking state.
		// Enqueue the mutation to be picked up by the next tick.
		runner.enqueueMutation(mutation);
		// 1) Tick verifies that the previous one has completed (which _might_ have picked up the mutation).
		runner.runTick();
		// 2) Tick verifies that another tick has run (this or the above will pick up the mutation).
		runner.runTick();
		// 3) Tick which will run the mutation (may have been run in (2) if picked up in (1)).
		runner.runTick();
		// This means that the third tick may be redundant depending on whether tick 1 picked up the mutation.
	}
}
