package com.jeffdisher.october.data;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.registries.AspectRegistry;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.worldgen.CuboidGenerator;


public class TestMutableBlockProxy
{
	@Test
	public void noChange()
	{
		AbsoluteLocation location = new AbsoluteLocation(1, 1, 1);
		CuboidAddress cuboidAddress = location.getCuboidAddress();
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ItemRegistry.AIR);
		BlockAddress address = location.getBlockAddress();
		
		MutableBlockProxy proxy = new MutableBlockProxy(location, address, input);
		
		Assert.assertFalse(proxy.didChange());
	}

	@Test
	public void simpleChange()
	{
		AbsoluteLocation location = new AbsoluteLocation(1, 1, 1);
		CuboidAddress cuboidAddress = location.getCuboidAddress();
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ItemRegistry.AIR);
		BlockAddress address = location.getBlockAddress();
		
		MutableBlockProxy proxy = new MutableBlockProxy(location, address, input);
		proxy.setItemAndClear(ItemRegistry.STONE);
		
		CuboidData updated = CuboidData.mutableClone(input);
		Assert.assertTrue(proxy.didChange());
		proxy.writeBack(updated);
		Assert.assertEquals(ItemRegistry.STONE.number(), updated.getData15(AspectRegistry.BLOCK, address));
	}

	@Test
	public void revertedChange()
	{
		AbsoluteLocation location = new AbsoluteLocation(1, 1, 1);
		CuboidAddress cuboidAddress = location.getCuboidAddress();
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ItemRegistry.AIR);
		BlockAddress address = location.getBlockAddress();
		
		MutableBlockProxy proxy = new MutableBlockProxy(location, address, input);
		proxy.setItemAndClear(ItemRegistry.STONE);
		proxy.setItemAndClear(ItemRegistry.AIR);
		
		Assert.assertFalse(proxy.didChange());
	}
}
