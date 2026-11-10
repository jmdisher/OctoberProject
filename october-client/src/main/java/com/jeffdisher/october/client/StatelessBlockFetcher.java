package com.jeffdisher.october.client;

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
 * This implementation of IBlockFetcher exists for the client-specific use-case where the data state is externally
 * managed (and cached) while this only adapts the interface for that data.
 * The reason why something more generic, like BlockFetcher, cannot be used is 2-fold:
 * 1) BlockFetcher is designed to be used within a single tick so it internally manages its cache, assuming that the
 * world isn't changing underneath it.  Instances of this class, on the other hand, are treated as long-lived state-less
 * objects where the data underneath them is externally changed.
 * 2) As client-side data is typically invalidated per-cuboid, the underlying data source Function assumes that cached
 * or fresh data is fetched per-cuboid.
 * 
 * Due to its internal caching, BlockFetcher is the preferred implementation, where applicable.
 */
public class StatelessBlockFetcher implements TickProcessingContext.IBlockFetcher
{
	private final Function<CuboidAddress, Pair<IReadOnlyCuboidData, Map<BlockAddress, BlockProxy>>> _dataFetcher;

	public StatelessBlockFetcher(Function<CuboidAddress, Pair<IReadOnlyCuboidData, Map<BlockAddress, BlockProxy>>> dataFetcher)
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
