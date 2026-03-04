package com.jeffdisher.october.ticks;

import java.util.List;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestBlockFetcher
{
	private static Environment ENV;
	@BeforeClass
	public static void setup() throws Throwable
	{
		ENV = Environment.createSharedInstance();
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void emptyCache()
	{
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		BlockFetcher fetcher = new BlockFetcher(Map.of(), Map.of(address, cuboid));
		
		AbsoluteLocation mix = new AbsoluteLocation(1, 2, 3);
		AbsoluteLocation one = new AbsoluteLocation(1, 1, 1);
		AbsoluteLocation two = new AbsoluteLocation(2, 2, 2);
		Assert.assertEquals(ENV.special.AIR, fetcher.readBlock(mix).getBlock());
		Map<AbsoluteLocation, BlockProxy> map = fetcher.readBlockBatch(List.of(one, two));
		Assert.assertEquals(ENV.special.AIR, map.get(one).getBlock());
		Assert.assertEquals(ENV.special.AIR, map.get(two).getBlock());
		Assert.assertNull(fetcher.readBlock(new AbsoluteLocation(100, 4, 5)));
	}

	@Test
	public void preLoadedCache()
	{
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		AbsoluteLocation one = new AbsoluteLocation(1, 1, 1);
		BlockProxy fake = BlockProxy.init(one.getBlockAddress(), cuboid, (short)1);
		BlockFetcher fetcher = new BlockFetcher(Map.of(one, fake), Map.of(address, cuboid));
		
		AbsoluteLocation mix = new AbsoluteLocation(1, 2, 3);
		AbsoluteLocation two = new AbsoluteLocation(2, 2, 2);
		Assert.assertEquals(ENV.special.AIR, fetcher.readBlock(mix).getBlock());
		Map<AbsoluteLocation, BlockProxy> map = fetcher.readBlockBatch(List.of(one, two));
		Assert.assertEquals(1, map.get(one).getBlock().item().number());
		Assert.assertEquals(ENV.special.AIR, map.get(two).getBlock());
		Assert.assertNull(fetcher.readBlock(new AbsoluteLocation(100, 4, 5)));
	}
}
