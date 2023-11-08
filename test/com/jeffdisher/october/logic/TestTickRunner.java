package com.jeffdisher.october.logic;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IOctree;
import com.jeffdisher.october.data.OctreeShort;


public class TestTickRunner
{
	public static final Aspect<Short> ASPECT_SHORT = new Aspect<>("Short", 0, Short.class);

	@Test
	public void basicOneCuboid()
	{
		OctreeShort data = OctreeShort.create((short)0);
		int[] changeData = new int[2];
		TickRunner runner = new TickRunner(1, new WorldState.IBlockChangeListener() {
			@Override
			public void blockChanged(int[] absoluteLocation)
			{
				changeData[0] += 1;
			}
			@Override
			public void mutationDropped(IMutation mutation)
			{
				changeData[1] += 1;
			}});
		runner.cuboidWasLoaded(new CuboidState(CuboidData.createNew(new short[] {(short)0, (short)0, (short)0}, new IOctree[] { data })));
		runner.start();
		runner.runTick();
		// Note that the mutation will not be enqueued in the next tick, but the following one (they are queued and picked up when the threads finish).
		runner.enqueueMutation(new PlaceBlockMutation(0, 0, 0, ASPECT_SHORT, (short)1));
		runner.runTick();
		runner.runTick();
		runner.shutdown();
		
		Assert.assertEquals(1, changeData[0]);
		Assert.assertEquals(0, changeData[1]);
	}

	@Test
	public void shockwaveOneCuboid()
	{
		OctreeShort data = OctreeShort.create((short)0);
		int[] changeData = new int[2];
		TickRunner runner = new TickRunner(1, new WorldState.IBlockChangeListener() {
			@Override
			public void blockChanged(int[] absoluteLocation)
			{
				changeData[0] += 1;
			}
			@Override
			public void mutationDropped(IMutation mutation)
			{
				changeData[1] += 1;
			}});
		runner.cuboidWasLoaded(new CuboidState(CuboidData.createNew(new short[] {(short)0, (short)0, (short)0}, new IOctree[] { data })));
		runner.start();
		runner.runTick();
		// Note that the mutation will not be enqueued in the next tick, but the following one (they are queued and picked up when the threads finish).
		// We enqueue a single shockwave in the centre of the cuboid and allow it to replicate 2 times.
		runner.enqueueMutation(new ShockwaveMutation(16, 16, 16, 2));
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
		OctreeShort data = OctreeShort.create((short)0);
		AtomicInteger[] changeData = new AtomicInteger[] { new AtomicInteger(0), new AtomicInteger(0) };
		TickRunner runner = new TickRunner(8, new WorldState.IBlockChangeListener() {
			@Override
			public void blockChanged(int[] absoluteLocation)
			{
				changeData[0].incrementAndGet();
			}
			@Override
			public void mutationDropped(IMutation mutation)
			{
				changeData[1].incrementAndGet();
			}});
		runner.cuboidWasLoaded(new CuboidState(CuboidData.createNew(new short[] {(short)0, (short)0, (short)0}, new IOctree[] { data })));
		runner.cuboidWasLoaded(new CuboidState(CuboidData.createNew(new short[] {(short)0, (short)0, (short)-1}, new IOctree[] { data })));
		runner.cuboidWasLoaded(new CuboidState(CuboidData.createNew(new short[] {(short)0, (short)-1, (short)0}, new IOctree[] { data })));
		runner.cuboidWasLoaded(new CuboidState(CuboidData.createNew(new short[] {(short)0, (short)-1, (short)-1}, new IOctree[] { data })));
		runner.cuboidWasLoaded(new CuboidState(CuboidData.createNew(new short[] {(short)-1, (short)0, (short)0}, new IOctree[] { data })));
		runner.cuboidWasLoaded(new CuboidState(CuboidData.createNew(new short[] {(short)-1, (short)0, (short)-1}, new IOctree[] { data })));
		runner.cuboidWasLoaded(new CuboidState(CuboidData.createNew(new short[] {(short)-1, (short)-1, (short)0}, new IOctree[] { data })));
		runner.cuboidWasLoaded(new CuboidState(CuboidData.createNew(new short[] {(short)-1, (short)-1, (short)-1}, new IOctree[] { data })));
		runner.start();
		runner.runTick();
		// Note that the mutation will not be enqueued in the next tick, but the following one (they are queued and picked up when the threads finish).
		// We enqueue a single shockwave in the centre of the cuboid and allow it to replicate 2 times.
		runner.enqueueMutation(new ShockwaveMutation(0, 0, 0, 2));
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
}
