package com.jeffdisher.october.ticks;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.utils.BlockFetcher;
import com.jeffdisher.october.utils.CuboidGenerator;
import com.jeffdisher.october.utils.Encoding;


public class TestBlockFetcher
{
	private static Environment ENV;
	private static Block STONE;
	@BeforeClass
	public static void setup() throws Throwable
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
	public void emptyCache()
	{
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		BlockFetcher fetcher = new BlockFetcher(Map.of(), Set.of(), _buildSingleLookup(cuboid));
		
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
		BlockFetcher fetcher = new BlockFetcher(Map.of(one, fake), Set.of(), _buildSingleLookup(cuboid));
		
		AbsoluteLocation mix = new AbsoluteLocation(1, 2, 3);
		AbsoluteLocation two = new AbsoluteLocation(2, 2, 2);
		Assert.assertEquals(ENV.special.AIR, fetcher.readBlock(mix).getBlock());
		Map<AbsoluteLocation, BlockProxy> map = fetcher.readBlockBatch(List.of(one, two));
		Assert.assertEquals(1, map.get(one).getBlock().item().number());
		Assert.assertEquals(ENV.special.AIR, map.get(two).getBlock());
		Assert.assertNull(fetcher.readBlock(new AbsoluteLocation(100, 4, 5)));
	}

	@Test
	public void failPreviousCacheWhenUnloaded()
	{
		// We will put something in the previous cache from a not-loaded cuboid and show that we don't see it.
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		AbsoluteLocation one = new AbsoluteLocation(32, 32, 32);
		BlockProxy fake = BlockProxy.init(one.getBlockAddress(), cuboid, (short)1);
		BlockFetcher fetcher = new BlockFetcher(Map.of(one, fake), Set.of(), _buildSingleLookup(cuboid));
		
		AbsoluteLocation two = new AbsoluteLocation(2, 2, 2);
		Map<AbsoluteLocation, BlockProxy> map = fetcher.readBlockBatch(List.of(one, two));
		Assert.assertEquals(1, map.size());
		Assert.assertNull(map.get(one));
		Assert.assertEquals(ENV.special.AIR, map.get(two).getBlock());
		Assert.assertNull(fetcher.readBlock(new AbsoluteLocation(100, 4, 5)));
	}

	@Test
	public void failPreviousCacheWhenChanged()
	{
		// We will load a value into the previous cache which we will mark as changed (stale) so we should avoid the previous cache.
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		AbsoluteLocation one = new AbsoluteLocation(1, 1, 1);
		BlockProxy fake = BlockProxy.init(one.getBlockAddress(), cuboid, (short)1);
		BlockFetcher fetcher = new BlockFetcher(Map.of(one, fake), Set.of(one), _buildSingleLookup(cuboid));
		
		AbsoluteLocation mix = new AbsoluteLocation(1, 2, 3);
		AbsoluteLocation two = new AbsoluteLocation(2, 2, 2);
		Assert.assertEquals(ENV.special.AIR, fetcher.readBlock(mix).getBlock());
		Map<AbsoluteLocation, BlockProxy> map = fetcher.readBlockBatch(List.of(one, two));
		Assert.assertEquals(ENV.special.AIR, map.get(one).getBlock());
		Assert.assertEquals(ENV.special.AIR, map.get(two).getBlock());
		Assert.assertNull(fetcher.readBlock(new AbsoluteLocation(100, 4, 5)));
	}

	@Test
	public void failFindSingle()
	{
		// Fail to find a value in a single cuboid.
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		BlockFetcher fetcher = new BlockFetcher(Map.of(), Set.of(), _buildSingleLookup(cuboid));
		
		AbsoluteLocation centre = new AbsoluteLocation(13, 14, 15);
		AbsoluteLocation match = fetcher.findFirstMatch(centre, 5, 3, STONE);
		Assert.assertNull(match);
	}

	@Test
	public void failFindMulti()
	{
		// Fail to find a value in a bunch of cuboids.
		IReadOnlyCuboidData[] cuboids = new IReadOnlyCuboidData[27];
		for (int z = -1; z <= 1; ++z)
		{
			for (int y = -1; y <= 1; ++y)
			{
				for (int x = -1; x <= 1; ++x)
				{
					CuboidAddress address = CuboidAddress.fromInt(x, y, z);
					int index = 9 * (z + 1) + 3 * (y + 1) + (x + 1);
					cuboids[index] = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
				}
			}
		}
		BlockFetcher fetcher = new BlockFetcher(Map.of(), Set.of(), _buildMultiLookup(cuboids));
		
		AbsoluteLocation centre = new AbsoluteLocation(13, 14, 15);
		AbsoluteLocation match = fetcher.findFirstMatch(centre, Encoding.CUBOID_EDGE_SIZE, Encoding.CUBOID_EDGE_SIZE, STONE);
		Assert.assertNull(match);
	}

	@Test
	public void succeedFindSingle()
	{
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(8, 19, 12), STONE.item().number());
		BlockFetcher fetcher = new BlockFetcher(Map.of(), Set.of(), _buildSingleLookup(cuboid));
		
		AbsoluteLocation centre = new AbsoluteLocation(13, 14, 15);
		AbsoluteLocation match = fetcher.findFirstMatch(centre, 5, 3, STONE);
		Assert.assertEquals(new AbsoluteLocation(8, 19, 12), match);
	}

	@Test
	public void succeedFindMulti()
	{
		IReadOnlyCuboidData[] cuboids = new IReadOnlyCuboidData[27];
		for (int z = -1; z <= 1; ++z)
		{
			for (int y = -1; y <= 1; ++y)
			{
				for (int x = -1; x <= 1; ++x)
				{
					CuboidAddress address = CuboidAddress.fromInt(x, y, z);
					int index = 9 * (z + 1) + 3 * (y + 1) + (x + 1);
					CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
					cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(27, 28, 29), STONE.item().number());
					cuboids[index] = cuboid;
				}
			}
		}
		BlockFetcher fetcher = new BlockFetcher(Map.of(), Set.of(), _buildMultiLookup(cuboids));
		
		AbsoluteLocation centre = new AbsoluteLocation(13, 14, 15);
		AbsoluteLocation match = fetcher.findFirstMatch(centre, Encoding.CUBOID_EDGE_SIZE, Encoding.CUBOID_EDGE_SIZE, STONE);
		Assert.assertEquals(new AbsoluteLocation(-5, -4, -3), match);
	}


	private final Function<CuboidAddress, IReadOnlyCuboidData> _buildSingleLookup(IReadOnlyCuboidData cuboid)
	{
		CuboidAddress cuboidAddress = cuboid.getCuboidAddress();
		Function<CuboidAddress, IReadOnlyCuboidData> cuboidLookup = (CuboidAddress address) -> {
			return address.equals(cuboidAddress)
				? cuboid
				: null
			;
		};
		return cuboidLookup;
	}

	private final Function<CuboidAddress, IReadOnlyCuboidData> _buildMultiLookup(IReadOnlyCuboidData... cuboid)
	{
		Map<CuboidAddress, IReadOnlyCuboidData> map = Arrays.stream(cuboid)
			.collect(Collectors.toMap((IReadOnlyCuboidData one) -> one.getCuboidAddress(), (IReadOnlyCuboidData one) -> one))
		;
		Function<CuboidAddress, IReadOnlyCuboidData> cuboidLookup = (CuboidAddress address) -> map.get(address);
		return cuboidLookup;
	}
}
