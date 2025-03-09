package com.jeffdisher.october.worldgen;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.utils.CuboidGenerator;
import com.jeffdisher.october.utils.Encoding;


public class TestPathDigger
{
	private static Environment ENV;
	private static Block STONE;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		STONE = ENV.blocks.fromItem(ENV.items.getItemById("op.stone"));
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void sphere()
	{
		AbsoluteLocation centre = new AbsoluteLocation(5, 5, 5);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(centre.getCuboidAddress(), STONE);
		PathDigger.hollowOutSphere(cuboid, centre, 7, STONE.item().number(), ENV.special.AIR.item().number());
		int stoneCount = 0;
		int airCount = 0;
		for (int y = 0; y < Encoding.CUBOID_EDGE_SIZE; ++y)
		{
			for (int x = 0; x < Encoding.CUBOID_EDGE_SIZE; ++x)
			{
				short type = cuboid.getData15(AspectRegistry.BLOCK, BlockAddress.fromInt(x, y, 0));
				if (STONE.item().number() == type)
				{
					stoneCount += 1;
				}
				else if (ENV.special.AIR.item().number() == type)
				{
					airCount += 1;
				}
				else
				{
					Assert.fail();
				}
			}
		}
		Assert.assertEquals(939, stoneCount);
		Assert.assertEquals(85, airCount);
	}
}
