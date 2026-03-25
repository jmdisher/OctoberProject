package com.jeffdisher.october.ticks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * A block fetcher implementation used by TickRunner.  It uses and internal cache, can fall-back to a prebuilt cache
 * (typically from the previous tick), or load data directly from cuboids.  In all cases, it will populate its cache
 * with the results it finds.
 */
public class BlockFetcher implements TickProcessingContext.IBlockFetcher
{
	private final Map<AbsoluteLocation, BlockProxy> _previousCache;
	private final Set<AbsoluteLocation> _forceMissBlocksPreviousCache;

	private final Map<CuboidAddress, IReadOnlyCuboidData> _cuboids;
	private final Map<AbsoluteLocation, BlockProxy> _cache;
	private final Set<AbsoluteLocation> _misses;

	public BlockFetcher(Map<AbsoluteLocation, BlockProxy> previousCache
		, Set<AbsoluteLocation> forceMissBlocksPreviousCache
		, Map<CuboidAddress, IReadOnlyCuboidData> cuboids
	)
	{
		_previousCache = previousCache;
		_forceMissBlocksPreviousCache = forceMissBlocksPreviousCache;
		
		_cuboids = cuboids;
		_cache = new HashMap<>();
		_misses = new HashSet<>();
	}

	@Override
	public BlockProxy readBlock(AbsoluteLocation location)
	{
		BlockProxy proxy = _cache.get(location);
		if (null == proxy)
		{
			if (_misses.contains(location))
			{
				// Explicitly fail to find this.
			}
			else
			{
				CuboidAddress address = location.getCuboidAddress();
				if (!_cuboids.containsKey(address))
				{
					// Force miss so add this to our misses.
					_misses.add(location);
				}
				else
				{
					// Try actually looking for this.
					if (!_forceMissBlocksPreviousCache.contains(location))
					{
						proxy = _previousCache.get(location);
					}
					if (null == proxy)
					{
						IReadOnlyCuboidData cuboid = _cuboids.get(address);
						proxy = BlockProxy.load(location.getBlockAddress(), cuboid);
					}
					_cache.put(location, proxy);
				}
			}
		}
		return proxy;
	}

	@Override
	public Map<AbsoluteLocation, BlockProxy> readBlockBatch(Collection<AbsoluteLocation> locations)
	{
		// We will filter by what is currently in cache, splitting the rest by cuboid for batching.
		Map<AbsoluteLocation, BlockProxy> completed = new HashMap<>();
		Map<CuboidAddress, List<BlockAddress>> toBatch = new HashMap<>();
		IReadOnlyCuboidData.BlockAddressBatchComparator comparator = new IReadOnlyCuboidData.BlockAddressBatchComparator();
		
		for (AbsoluteLocation location : locations)
		{
			if (_cache.containsKey(location))
			{
				BlockProxy proxy = _cache.get(location);
				completed.put(location, proxy);
			}
			else if (_misses.contains(location))
			{
				// Just do nothing since  we don't include nulls.
			}
			else
			{
				CuboidAddress cuboidAddress = location.getCuboidAddress();
				if (!_cuboids.containsKey(cuboidAddress))
				{
					// Force miss so add this to our misses.
					_misses.add(location);
				}
				else if (!_forceMissBlocksPreviousCache.contains(location) && _previousCache.containsKey(location))
				{
					BlockProxy proxy = _previousCache.get(location);
					
					_cache.put(location, proxy);
					completed.put(location, proxy);
				}
				else
				{
					List<BlockAddress> batch = toBatch.get(cuboidAddress);
					if (null == batch)
					{
						batch = new ArrayList<>();
						toBatch.put(cuboidAddress, batch);
					}
					batch.add(location.getBlockAddress());
				}
			}
		}
		
		// For the batches, we use the basic case for single-elements but the batch interface for multiple.
		for (Map.Entry<CuboidAddress, List<BlockAddress>> req : toBatch.entrySet())
		{
			CuboidAddress cuboidAddress = req.getKey();
			AbsoluteLocation base = cuboidAddress.getBase();
			IReadOnlyCuboidData cuboid = _cuboids.get(cuboidAddress);
			List<BlockAddress> list = req.getValue();
			
			if (1 == list.size())
			{
				// This is the only block in this cuboid so just use the single-read path.
				BlockAddress address = list.get(0);
				BlockProxy proxy = BlockProxy.load(address, cuboid);
				
				AbsoluteLocation location = base.relativeForBlock(address);
				_cache.put(location, proxy);
				completed.put(location, proxy);
			}
			else
			{
				BlockAddress[] array = list.toArray((int size) -> new BlockAddress[size]);
				Arrays.sort(array, comparator);
				
				short[] blocks = cuboid.batchReadData15(AspectRegistry.BLOCK, array);
				
				for (int i = 0; i < array.length; ++i)
				{
					BlockAddress address = array[i];
					short type = blocks[i];
					BlockProxy proxy = BlockProxy.init(address, cuboid, type);
					
					AbsoluteLocation location = base.relativeForBlock(address);
					_cache.put(location, proxy);
					completed.put(location, proxy);
				}
			}
		}
		return completed;
	}

	public Map<AbsoluteLocation, BlockProxy> extractCache()
	{
		return _cache;
	}
}
