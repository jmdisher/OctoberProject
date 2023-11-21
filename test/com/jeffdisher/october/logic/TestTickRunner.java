package com.jeffdisher.october.logic;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IOctree;
import com.jeffdisher.october.data.OctreeShort;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CuboidAddress;


public class TestTickRunner
{
	@Test
	public void basicOneCuboid()
	{
		AspectRegistry registry = new AspectRegistry();
		Aspect<Short>  aspectShort = registry.registerAspect("Short", Short.class);
		OctreeShort data = OctreeShort.create((short)0);
		int[] changeData = new int[2];
		TickRunner runner = new TickRunner(registry, 1, new WorldState.IBlockChangeListener() {
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
		runner.enqueueMutation(new PlaceBlockMutation(new AbsoluteLocation(0, 0, 0), aspectShort, (short)1));
		runner.runTick();
		runner.runTick();
		runner.shutdown();
		
		Assert.assertEquals(1, changeData[0]);
		Assert.assertEquals(0, changeData[1]);
	}

	@Test
	public void shockwaveOneCuboid()
	{
		AspectRegistry registry = new AspectRegistry();
		registry.registerAspect("Short", Short.class);
		OctreeShort data = OctreeShort.create((short)0);
		int[] changeData = new int[2];
		TickRunner runner = new TickRunner(registry, 1, new WorldState.IBlockChangeListener() {
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
		AspectRegistry registry = new AspectRegistry();
		registry.registerAspect("Short", Short.class);
		OctreeShort data = OctreeShort.create((short)0);
		AtomicInteger[] changeData = new AtomicInteger[] { new AtomicInteger(0), new AtomicInteger(0) };
		TickRunner runner = new TickRunner(registry, 8, new WorldState.IBlockChangeListener() {
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
		AspectRegistry registry = new AspectRegistry();
		Aspect<Short>  aspectShort = registry.registerAspect("Short", Short.class);
		OctreeShort data = OctreeShort.create((short)0);
		TickRunner runner = new TickRunner(registry, 1, new WorldState.IBlockChangeListener() {
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
		runner.enqueueMutation(new PlaceBlockMutation(new AbsoluteLocation(0, 0, 0), aspectShort, (short)1));
		runner.runTick();
		runner.runTick();
		runner.shutdown();
		
		// We should now see the new data.
		block = runner.getBlockProxy(new AbsoluteLocation(0, 0, 0));
		Assert.assertEquals((short)1, block.getData15(aspectShort));
	}
}
