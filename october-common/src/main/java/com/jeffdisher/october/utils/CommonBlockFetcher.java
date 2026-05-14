package com.jeffdisher.october.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Pair;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * A utility class to handle a common IBlockFetcher implementation pattern where the higher-level logic is all the same
 * and only how the cache is managed or how the cuboids are resolved in specialized.
 * This per-cuboid cache and authoritative cuboid data is provided by a given Function.
 */
public class CommonBlockFetcher implements TickProcessingContext.IBlockFetcher
{
	private final Function<CuboidAddress, Pair<IReadOnlyCuboidData, Map<BlockAddress, BlockProxy>>> _dataFetcher;

	public CommonBlockFetcher(Function<CuboidAddress, Pair<IReadOnlyCuboidData, Map<BlockAddress, BlockProxy>>> dataFetcher)
	{
		_dataFetcher = dataFetcher;
	}

	@Override
	public BlockProxy readBlock(AbsoluteLocation location)
	{
		BlockProxy proxy = null;
		
		Pair<IReadOnlyCuboidData, Map<BlockAddress, BlockProxy>> pair = _dataFetcher.apply(location.getCuboidAddress());
		if (null != pair)
		{
			Map<BlockAddress, BlockProxy> cache = pair.two();
			BlockAddress blockAddress = location.getBlockAddress();
			proxy = cache.get(blockAddress);
			if (null == proxy)
			{
				proxy = BlockProxy.load(blockAddress, pair.one());
				cache.put(blockAddress, proxy);
			}
		}
		return proxy;
	}

	@Override
	public Map<AbsoluteLocation, BlockProxy> readBlockBatch(Collection<AbsoluteLocation> locations)
	{
		// Collect the per-cuboid lookups, first.
		Map<CuboidAddress, List<BlockAddress>> split = _splitLocationsByCuboid(locations);
		return _fetchProxiesByCuboid(split);
	}


	private Map<CuboidAddress, List<BlockAddress>> _splitLocationsByCuboid(Collection<AbsoluteLocation> locations)
	{
		Map<CuboidAddress, List<BlockAddress>> split = new HashMap<>();
		for (AbsoluteLocation loc : locations)
		{
			CuboidAddress cuboidAddress = loc.getCuboidAddress();
			List<BlockAddress> list = split.get(cuboidAddress);
			if (null == list)
			{
				list = new ArrayList<>();
				split.put(cuboidAddress, list);
			}
			list.add(loc.getBlockAddress());
		}
		return split;
	}

	private Map<AbsoluteLocation, BlockProxy> _fetchProxiesByCuboid(Map<CuboidAddress, List<BlockAddress>> split)
	{
		Map<AbsoluteLocation, BlockProxy> result = new HashMap<>();
		for (Map.Entry<CuboidAddress, List<BlockAddress>> elt : split.entrySet())
		{
			CuboidAddress cuboidAddress = elt.getKey();
			Pair<IReadOnlyCuboidData, Map<BlockAddress, BlockProxy>> pair = _dataFetcher.apply(cuboidAddress);
			if (null != pair)
			{
				AbsoluteLocation base = cuboidAddress.getBase();
				List<BlockAddress> toLoad = new ArrayList<>();
				Map<BlockAddress, BlockProxy> cache = pair.two();
				for (BlockAddress blockAddress : elt.getValue())
				{
					BlockProxy proxy = cache.get(blockAddress);
					if (null != proxy)
					{
						AbsoluteLocation location = base.relativeForBlock(blockAddress);
						result.put(location, proxy);
					}
					else
					{
						toLoad.add(blockAddress);
					}
				}
				if (!toLoad.isEmpty())
				{
					BlockAddress[] array = toLoad.toArray((int size) -> new BlockAddress[size]);
					IReadOnlyCuboidData.BlockAddressBatchComparator comparator = new IReadOnlyCuboidData.BlockAddressBatchComparator();
					Arrays.sort(array, comparator);
					IReadOnlyCuboidData cuboid = pair.one();
					short[] blocks = cuboid.batchReadData15(AspectRegistry.BLOCK, array);
					for (int i = 0; i < array.length; ++i)
					{
						short number = blocks[i];
						BlockAddress blockAddress = array[i];
						BlockProxy proxy = BlockProxy.init(blockAddress, cuboid, number);
						cache.put(blockAddress, proxy);
						
						AbsoluteLocation location = base.relativeForBlock(blockAddress);
						result.put(location, proxy);
					}
				}
			}
		}
		return result;
	}
}
