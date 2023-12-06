package com.jeffdisher.october.mutations;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.changes.BeginBreakBlockChange;
import com.jeffdisher.october.changes.IEntityChange;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IOctree;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.data.OctreeObject;
import com.jeffdisher.october.data.OctreeShort;
import com.jeffdisher.october.registries.AspectRegistry;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * A test suite for the basic behaviours of the common mutations (those which are part of the system).
 * The tests are combined here since each mutation is fundamentally simple and this is mostly just to demonstrate that
 * they can be called.
 */
public class TestCommonMutations
{
	@Test
	public void breakBlockSuccess()
	{
		AbsoluteLocation target = new AbsoluteLocation(0, 0, 0);
		CuboidData cuboid = _createSolidCuboid(target.getCuboidAddress(), BlockAspect.STONE);
		BreakBlockMutation mutation = new BreakBlockMutation(target, BlockAspect.STONE);
		MutableBlockProxy proxy = new MutableBlockProxy(target.getBlockAddress(), cuboid);
		boolean didApply = mutation.applyMutation(null, proxy);
		Assert.assertTrue(didApply);
		Assert.assertEquals(BlockAspect.AIR, proxy.getData15(AspectRegistry.BLOCK));
		Inventory inv = proxy.getDataSpecial(AspectRegistry.INVENTORY);
		Assert.assertEquals(1, inv.items.size());
		Assert.assertEquals(1, inv.items.get(ItemRegistry.STONE).count());
	}

	@Test
	public void breakBlockFailure()
	{
		AbsoluteLocation target = new AbsoluteLocation(0, 0, 0);
		CuboidData cuboid = _createSolidCuboid(target.getCuboidAddress(), BlockAspect.AIR);
		BreakBlockMutation mutation = new BreakBlockMutation(target, BlockAspect.STONE);
		MutableBlockProxy proxy = new MutableBlockProxy(target.getBlockAddress(), cuboid);
		boolean didApply = mutation.applyMutation(null, proxy);
		Assert.assertFalse(didApply);
		Assert.assertEquals(BlockAspect.AIR, proxy.getData15(AspectRegistry.BLOCK));
		Inventory inv = proxy.getDataSpecial(AspectRegistry.INVENTORY);
		Assert.assertNull(inv);
	}

	@Test
	public void beginBlockBreakSuccess()
	{
		AbsoluteLocation target = new AbsoluteLocation(0, 0, 0);
		CuboidData cuboid = _createSolidCuboid(target.getCuboidAddress(), BlockAspect.STONE);
		ProcessingSinks sinks = new ProcessingSinks();
		TickProcessingContext context = sinks.createBoundContext(cuboid);
		BeginBreakBlockChange phase1 = new BeginBreakBlockChange(0, target);
		
		// Phase1 should request that phase2 be scheduled later.
		boolean didApply = phase1.applyChange(context, null);
		Assert.assertTrue(didApply);
		
		// Check that phase2 is what we expected and then run it.
		Assert.assertNotNull(sinks.nextDelayedChange);
		Assert.assertEquals(100L, sinks.nextDelayedMillis);
		didApply = sinks.nextDelayedChange.applyChange(context, null);
		Assert.assertTrue(didApply);
		
		// Check that the final mutation to actually break the block is as expected and then run it.
		MutableBlockProxy proxy = new MutableBlockProxy(target.getBlockAddress(), cuboid);
		Assert.assertNotNull(sinks.nextMutation);
		didApply = sinks.nextMutation.applyMutation(context, proxy);
		Assert.assertTrue(didApply);
		
		Assert.assertEquals(BlockAspect.AIR, proxy.getData15(AspectRegistry.BLOCK));
		Inventory inv = proxy.getDataSpecial(AspectRegistry.INVENTORY);
		Assert.assertEquals(1, inv.items.size());
		Assert.assertEquals(1, inv.items.get(ItemRegistry.STONE).count());
	}


	private static CuboidData _createSolidCuboid(CuboidAddress address, short blockType)
	{
		OctreeShort blockTypes = OctreeShort.create(blockType);
		OctreeObject inventories = OctreeObject.create();
		return CuboidData.createNew(address, new IOctree[] { blockTypes, inventories });
	}


	private static class ProcessingSinks
	{
		public IMutation nextMutation;
		public IEntityChange nextDelayedChange;
		public long nextDelayedMillis;
		
		public TickProcessingContext createBoundContext(CuboidData cuboid)
		{
			return new TickProcessingContext(1L
					, (AbsoluteLocation blockLocation) -> {
						return new BlockProxy(blockLocation.getBlockAddress(), cuboid);
					}
					, (IMutation mutation) -> {
						ProcessingSinks.this.nextMutation = mutation;
					}
					, null
					, (int targetEntityId, IEntityChange change, long delayMillis) -> {
						ProcessingSinks.this.nextDelayedChange = change;
						ProcessingSinks.this.nextDelayedMillis = delayMillis;
					}
			);
		}
	}
}
