package com.jeffdisher.october.logic;

import java.util.Collections;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IOctree;
import com.jeffdisher.october.data.OctreeShort;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;


public class TestSpeculativeProjection
{
	private static final AspectRegistry ASPECT_REGISTRY = new AspectRegistry();
	private static final Aspect<Short> BLOCK_TYPE_ASPECT = ASPECT_REGISTRY.registerAspect("BlockType", Short.class);
	private static final short BLOCK_AIR = (short)0;
	private static final short BLOCK_STONE = (short)1;

	@Test
	public void basicApplyMatching()
	{
		// We want to test that adding a few mutations as speculative, but then adding them as "committed" causes no problem.
		CountingListener listener = new CountingListener();
		SpeculativeProjection projector = new SpeculativeProjection(listener);
		
		// Create and add an empty cuboid.
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		OctreeShort data = OctreeShort.create(BLOCK_AIR);
		CuboidData cuboid = CuboidData.createNew(address, new IOctree[] { data });
		projector.loadedCuboid(address, cuboid);
		Assert.assertEquals(1, listener.loadCount);
		Assert.assertEquals(0, _countBlocks(listener.lastData, BLOCK_STONE));
		
		// Apply a few local mutations.
		IMutation lone1 = new PlaceBlockMutation(new AbsoluteLocation(0, 1, 0), BLOCK_TYPE_ASPECT, BLOCK_STONE);
		IMutation lone2 = new PlaceBlockMutation(new AbsoluteLocation(0, 0, 1), BLOCK_TYPE_ASPECT, BLOCK_STONE);
		long commit1 = projector.applyLocalMutation(lone1);
		long commit2 = projector.applyLocalMutation(lone2);
		IMutation[] mutations = new IMutation[5];
		long[] commitNumbers = new long[mutations.length];
		for (int i = 0; i < mutations.length; ++i)
		{
			AbsoluteLocation location = new AbsoluteLocation(i, 0, 0);
			mutations[i] = new PlaceBlockMutation(location, BLOCK_TYPE_ASPECT, BLOCK_STONE);
			commitNumbers[i] = projector.applyLocalMutation(mutations[i]);
		}
		Assert.assertEquals(7, listener.changeCount);
		Assert.assertEquals(7, _countBlocks(listener.lastData, BLOCK_STONE));
		
		// Commit the first 2, one at a time, and then the last ones at the same time.
		int speculativeCount = projector.applyCommittedMutations(Collections.emptySet(), new IMutation[] { lone1 }, commit1);
		Assert.assertEquals(6, speculativeCount);
		Assert.assertEquals(8, listener.changeCount);
		speculativeCount = projector.applyCommittedMutations(Collections.emptySet(), new IMutation[] { lone2 }, commit2);
		Assert.assertEquals(5, speculativeCount);
		Assert.assertEquals(9, listener.changeCount);
		Assert.assertEquals(7, _countBlocks(listener.lastData, BLOCK_STONE));
		speculativeCount = projector.applyCommittedMutations(Collections.emptySet(), mutations, commitNumbers[commitNumbers.length - 1]);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(10, listener.changeCount);
		Assert.assertEquals(7, _countBlocks(listener.lastData, BLOCK_STONE));
		
		// Now, unload.
		speculativeCount = projector.applyCommittedMutations(Set.of(address), new IMutation[0], commitNumbers[commitNumbers.length - 1]);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(1, listener.unloadCount);
	}

	@Test
	public void unloadWithMutations()
	{
		// Test that unloading a cuboid with local mutations correctly purges them but can go on to commit other things.
		CountingListener listener = new CountingListener();
		SpeculativeProjection projector = new SpeculativeProjection(listener);
		
		// Create and add an empty cuboid.
		CuboidAddress address0 = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidAddress address1 = new CuboidAddress((short)0, (short)0, (short)1);
		OctreeShort data0 = OctreeShort.create(BLOCK_AIR);
		OctreeShort data1 = OctreeShort.create(BLOCK_AIR);
		CuboidData cuboid0 = CuboidData.createNew(address0, new IOctree[] { data0 });
		CuboidData cuboid1 = CuboidData.createNew(address1, new IOctree[] { data1 });
		projector.loadedCuboid(address0, cuboid0);
		projector.loadedCuboid(address1, cuboid1);
		Assert.assertEquals(2, listener.loadCount);
		
		// Apply a few local mutations.
		IMutation lone0 = new PlaceBlockMutation(new AbsoluteLocation(1, 0, 0), BLOCK_TYPE_ASPECT, BLOCK_STONE);
		IMutation lone1 = new PlaceBlockMutation(new AbsoluteLocation(0, 1, 32), BLOCK_TYPE_ASPECT, BLOCK_STONE);
		projector.applyLocalMutation(lone0);
		Assert.assertEquals(1, _countBlocks(listener.lastData, BLOCK_STONE));
		long commit1 = projector.applyLocalMutation(lone1);
		Assert.assertEquals(2, listener.changeCount);
		Assert.assertEquals(1, _countBlocks(listener.lastData, BLOCK_STONE));
		
		// Commit the other one.
		int speculativeCount = projector.applyCommittedMutations(Set.of(address1), new IMutation[] { lone0 }, commit1);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(3, listener.changeCount);
		Assert.assertEquals(1, listener.unloadCount);
		Assert.assertEquals(1, _countBlocks(listener.lastData, BLOCK_STONE));
		
		// Unload the other.
		speculativeCount = projector.applyCommittedMutations(Set.of(address0), new IMutation[0], commit1);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(2, listener.unloadCount);
	}

	@Test
	public void applyWithConflicts()
	{
		// We want to test that adding a few mutations as speculative, and then committing a few conflicts to make sure that we drop the speculative mutaions which fail.
		CountingListener listener = new CountingListener();
		SpeculativeProjection projector = new SpeculativeProjection(listener);
		
		// Create and add an empty cuboid.
		CuboidAddress address0 = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidAddress address1 = new CuboidAddress((short)0, (short)0, (short)1);
		OctreeShort data0 = OctreeShort.create(BLOCK_AIR);
		OctreeShort data1 = OctreeShort.create(BLOCK_AIR);
		CuboidData cuboid0 = CuboidData.createNew(address0, new IOctree[] { data0 });
		CuboidData cuboid1 = CuboidData.createNew(address1, new IOctree[] { data1 });
		projector.loadedCuboid(address0, cuboid0);
		projector.loadedCuboid(address1, cuboid1);
		Assert.assertEquals(2, listener.loadCount);
		
		// Apply a few local mutations.
		IMutation lone0 = new PlaceBlockMutation(new AbsoluteLocation(1, 0, 0), BLOCK_TYPE_ASPECT, BLOCK_STONE);
		IMutation lone1 = new PlaceBlockMutation(new AbsoluteLocation(0, 1, 32), BLOCK_TYPE_ASPECT, BLOCK_STONE);
		projector.applyLocalMutation(lone0);
		Assert.assertEquals(1, _countBlocks(listener.lastData, BLOCK_STONE));
		long commit1 = projector.applyLocalMutation(lone1);
		Assert.assertEquals(2, listener.changeCount);
		Assert.assertEquals(1, _countBlocks(listener.lastData, BLOCK_STONE));
		
		// Commit a mutation which invalidates lone0 (we do that by passing in lone0 and just not changing the commit level - that makes it appear like a conflict).
		int speculativeCount = projector.applyCommittedMutations(Collections.emptySet(), new IMutation[] { lone0 }, 0L);
		// We should still see the other one.
		Assert.assertEquals(1, speculativeCount);
		// We see another 2 changes due to the reverses.
		Assert.assertEquals(4, listener.changeCount);
		Assert.assertEquals(1, _countBlocks(listener.lastData, BLOCK_STONE));
		
		// Commit the other one normally.
		speculativeCount = projector.applyCommittedMutations(Collections.emptySet(), new IMutation[] { lone1 }, commit1);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(5, listener.changeCount);
		Assert.assertEquals(1, _countBlocks(listener.lastData, BLOCK_STONE));
		
		speculativeCount = projector.applyCommittedMutations(Set.of(address0, address1), new IMutation[0], commit1);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(2, listener.unloadCount);
	}

	@Test
	public void applySecondaryMutations()
	{
		// We want to apply a few mutations which themselves cause secondary mutations, and observe what happens when some commit versus conflict.
		CountingListener listener = new CountingListener();
		SpeculativeProjection projector = new SpeculativeProjection(listener);
		
		// Create and add an empty cuboid.
		CuboidAddress address0 = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidAddress address1 = new CuboidAddress((short)0, (short)0, (short)1);
		OctreeShort data0 = OctreeShort.create(BLOCK_AIR);
		OctreeShort data1 = OctreeShort.create(BLOCK_AIR);
		CuboidData cuboid0 = CuboidData.createNew(address0, new IOctree[] { data0 });
		CuboidData cuboid1 = CuboidData.createNew(address1, new IOctree[] { data1 });
		projector.loadedCuboid(address0, cuboid0);
		projector.loadedCuboid(address1, cuboid1);
		Assert.assertEquals(2, listener.loadCount);
		
		// Apply a few local mutations.
		IMutation lone0 = new ShockwaveMutation(new AbsoluteLocation(5, 5, 5), true, 2);
		IMutation lone1 = new ShockwaveMutation(new AbsoluteLocation(5, 5, 37), true, 2);
		projector.applyLocalMutation(lone0);
		long commit1 = projector.applyLocalMutation(lone1);
		Assert.assertEquals(2, listener.changeCount);
		
		// Commit a mutation which invalidates lone0 (we do that by passing in lone0 and just not changing the commit level - that makes it appear like a conflict).
		int speculativeCount = projector.applyCommittedMutations(Collections.emptySet(), new IMutation[] { lone0 }, 0L);
		// We should still see both initial mutations and all of their secondaries, since shockwaves don't actually conflict.
		int mutationsPerShockwave = 1 + 6 + 36;
		Assert.assertEquals(2 * mutationsPerShockwave, speculativeCount);
		// We see another 2 changes due to the reverses.
		Assert.assertEquals(4, listener.changeCount);
		
		// Commit the other one normally.
		speculativeCount = projector.applyCommittedMutations(Collections.emptySet(), new IMutation[] { lone1 }, commit1);
		// This commit level change should cause them all to be retired.
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(6, listener.changeCount);
		
		speculativeCount = projector.applyCommittedMutations(Set.of(address0, address1), new IMutation[0], commit1);
		Assert.assertEquals(0, speculativeCount);
		Assert.assertEquals(2, listener.unloadCount);
	}


	private int _countBlocks(CuboidData cuboid, short blockType)
	{
		int count = 0;
		for (int x = 0; x < 32; ++x)
		{
			for (int y = 0; y < 32; ++y)
			{
				for (int z = 0; z < 32; ++z)
				{
					short value = cuboid.getData15(BLOCK_TYPE_ASPECT, new BlockAddress((byte)x, (byte)y, (byte)z));
					if (blockType == value)
					{
						count += 1;
					}
				}
			}
		}
		return count;
	}

	private static class CountingListener implements SpeculativeProjection.IProjectionListener
	{
		public int loadCount = 0;
		public int changeCount = 0;
		public int unloadCount = 0;
		public CuboidData lastData = null;
		
		@Override
		public void cuboidDidLoad(CuboidAddress address, CuboidData cuboid)
		{
			this.loadCount += 1;
			this.lastData = cuboid;
		}
		@Override
		public void cuboidDidChange(CuboidAddress address, CuboidData cuboid)
		{
			this.changeCount += 1;
			this.lastData = cuboid;
		}
		@Override
		public void cuboidDidUnload(CuboidAddress address, CuboidData cuboid)
		{
			this.unloadCount += 1;
		}
	}
}