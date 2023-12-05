package com.jeffdisher.october.mutations;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.aspects.BlockAspect;
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


	private static CuboidData _createSolidCuboid(CuboidAddress address, short blockType)
	{
		OctreeShort blockTypes = OctreeShort.create(blockType);
		OctreeObject inventories = OctreeObject.create();
		return CuboidData.createNew(address, new IOctree[] { blockTypes, inventories });
	}
}
