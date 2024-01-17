package com.jeffdisher.october.mutations;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.logic.EntityActionValidator;
import com.jeffdisher.october.registries.AspectRegistry;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.worldgen.CuboidGenerator;


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
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(target.getCuboidAddress(), ItemRegistry.STONE);
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
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(target.getCuboidAddress(), ItemRegistry.AIR);
		BreakBlockMutation mutation = new BreakBlockMutation(target, BlockAspect.STONE);
		MutableBlockProxy proxy = new MutableBlockProxy(target.getBlockAddress(), cuboid);
		boolean didApply = mutation.applyMutation(null, proxy);
		Assert.assertFalse(didApply);
		Assert.assertEquals(BlockAspect.AIR, proxy.getData15(AspectRegistry.BLOCK));
		Inventory inv = proxy.getDataSpecial(AspectRegistry.INVENTORY);
		Assert.assertNull(inv);
	}

	@Test
	public void endBlockBreakSuccess()
	{
		AbsoluteLocation target = new AbsoluteLocation(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(target.getCuboidAddress(), ItemRegistry.STONE);
		ProcessingSinks sinks = new ProcessingSinks();
		TickProcessingContext context = sinks.createBoundContext(cuboid);
		EndBreakBlockChange longRunningChange = new EndBreakBlockChange(target, ItemRegistry.STONE.number());
		
		// We will need an entity so that phase1 can ask to schedule the follow-up against it.
		Entity entity = EntityActionValidator.buildDefaultEntity(0);
		
		// Check that once we run this change, it requests the appropriate mutation.
		boolean didApply = longRunningChange.applyChange(context, new MutableEntity(entity));
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


	private static class ProcessingSinks
	{
		public IMutationBlock nextMutation;
		
		public TickProcessingContext createBoundContext(CuboidData cuboid)
		{
			return new TickProcessingContext(1L
					, (AbsoluteLocation blockLocation) -> {
						return new BlockProxy(blockLocation.getBlockAddress(), cuboid);
					}
					, (IMutationBlock mutation) -> {
						ProcessingSinks.this.nextMutation = mutation;
					}
					, null
			);
		}
	}
}
