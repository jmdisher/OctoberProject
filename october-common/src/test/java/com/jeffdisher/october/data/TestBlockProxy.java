package com.jeffdisher.october.data;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestBlockProxy
{
	private static Environment ENV;
	private static Item STONE_ITEM;
	@BeforeClass
	public static void setup() throws Throwable
	{
		ENV = Environment.createSharedInstance();
		STONE_ITEM = ENV.items.getItemById("op.stone");
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void compareLoadAndInit()
	{
		// Show that we can force a BlockProxy to see a specific block type even if the underlying cuboid does not hold that type.
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		
		BlockAddress blockAddress = BlockAddress.fromInt(1, 2, 3);
		BlockProxy load = BlockProxy.load(blockAddress, input);
		BlockProxy load2 = BlockProxy.load(blockAddress, input);
		BlockProxy init = BlockProxy.init(blockAddress, input, STONE_ITEM.number());
		BlockProxy init2 = BlockProxy.init(blockAddress, input, STONE_ITEM.number());
		
		// This causes a block mismatch.
		Assert.assertNotEquals(load.getBlock(), init.getBlock());
		Assert.assertEquals(load.getBlock(), load2.getBlock());
		Assert.assertEquals(init.getBlock(), init2.getBlock());
		
		// But not other data elements.
		Assert.assertEquals(load.getFlags(), init.getFlags());
	}
}
