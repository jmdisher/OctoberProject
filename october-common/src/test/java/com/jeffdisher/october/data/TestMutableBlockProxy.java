package com.jeffdisher.october.data;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.registries.AspectRegistry;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;


public class TestMutableBlockProxy
{
	@Test
	public void noChange()
	{
		CuboidAddress cuboidAddress = new CuboidAddress((short) 0, (short) 0, (short) 0);
		OctreeShort blockData = OctreeShort.create(ItemRegistry.AIR.number());
		OctreeObject inventoryData = OctreeObject.create();
		CuboidData input = CuboidData.createNew(cuboidAddress, new IOctree[] { blockData, inventoryData });
		BlockAddress address = new BlockAddress((byte) 1, (byte) 1, (byte) 1);
		
		MutableBlockProxy proxy = new MutableBlockProxy(address, input);
		
		Assert.assertFalse(proxy.didChange());
	}

	@Test
	public void simpleChange()
	{
		CuboidAddress cuboidAddress = new CuboidAddress((short) 0, (short) 0, (short) 0);
		OctreeShort blockData = OctreeShort.create(ItemRegistry.AIR.number());
		OctreeObject inventoryData = OctreeObject.create();
		CuboidData input = CuboidData.createNew(cuboidAddress, new IOctree[] { blockData, inventoryData });
		BlockAddress address = new BlockAddress((byte) 1, (byte) 1, (byte) 1);
		
		MutableBlockProxy proxy = new MutableBlockProxy(address, input);
		proxy.setData15(AspectRegistry.BLOCK, ItemRegistry.STONE.number());
		
		CuboidData updated = CuboidData.mutableClone(input);
		Assert.assertTrue(proxy.didChange());
		proxy.writeBack(updated);
		Assert.assertEquals(ItemRegistry.STONE.number(), updated.getData15(AspectRegistry.BLOCK, address));
	}

	@Test
	public void revertedChange()
	{
		CuboidAddress cuboidAddress = new CuboidAddress((short) 0, (short) 0, (short) 0);
		OctreeShort blockData = OctreeShort.create(ItemRegistry.AIR.number());
		OctreeObject inventoryData = OctreeObject.create();
		CuboidData input = CuboidData.createNew(cuboidAddress, new IOctree[] { blockData, inventoryData });
		BlockAddress address = new BlockAddress((byte) 1, (byte) 1, (byte) 1);
		
		MutableBlockProxy proxy = new MutableBlockProxy(address, input);
		proxy.setData15(AspectRegistry.BLOCK, ItemRegistry.STONE.number());
		proxy.setData15(AspectRegistry.BLOCK, ItemRegistry.AIR.number());
		
		Assert.assertFalse(proxy.didChange());
	}
}
