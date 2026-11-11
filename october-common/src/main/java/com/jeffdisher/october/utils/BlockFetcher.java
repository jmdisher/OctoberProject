package com.jeffdisher.october.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
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

	private final Function<CuboidAddress, IReadOnlyCuboidData> _cuboidLookup;
	private final Map<AbsoluteLocation, BlockProxy> _cache;
	private final Set<CuboidAddress> _misses;

	public BlockFetcher(Map<AbsoluteLocation, BlockProxy> previousCache
		, Set<AbsoluteLocation> forceMissBlocksPreviousCache
		, Function<CuboidAddress, IReadOnlyCuboidData> cuboidLookup
	)
	{
		_previousCache = previousCache;
		_forceMissBlocksPreviousCache = forceMissBlocksPreviousCache;
		_cuboidLookup = cuboidLookup;
		
		_cache = new HashMap<>();
		_misses = new HashSet<>();
	}

	@Override
	public BlockProxy readBlock(AbsoluteLocation location)
	{
		// We can always short-circuit around the local cache.
		BlockProxy proxy = _cache.get(location);
		if (null == proxy)
		{
			// If that doesn't work, check the cuboid address to make sure we haven't already failed to load from that one.
			CuboidAddress address = location.getCuboidAddress();
			if (_misses.contains(address))
			{
				// Explicitly fail to find this.
			}
			else
			{
				// We need to check that this is actually loaded before even checking the previous cache (it can contain unloaded locations).
				IReadOnlyCuboidData cuboid = _cuboidLookup.apply(address);
				if (null == cuboid)
				{
					// Force miss so add this to our misses.
					_misses.add(address);
				}
				else
				{
					// Check the previous cache.
					if (!_forceMissBlocksPreviousCache.contains(location))
					{
						proxy = _previousCache.get(location);
					}
					
					if (null == proxy)
					{
						// This is valid but not in any caching layer so load it and add it to the local cache.
						proxy = BlockProxy.load(location.getBlockAddress(), cuboid);
						_cache.put(location, proxy);
					}
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
		Map<CuboidAddress, _BatchTuple> toBatch = new HashMap<>();
		
		for (AbsoluteLocation location : locations)
		{
			// We first want to check our local cache since we only populate it with things we can load.
			if (_cache.containsKey(location))
			{
				// We can satisfy this one directly from the local cache.
				BlockProxy proxy = _cache.get(location);
				completed.put(location, proxy);
			}
			else
			{
				// We need to check that this is actually loaded before even checking the previous cache (it can contain unloaded locations).
				CuboidAddress address = location.getCuboidAddress();
				if (_misses.contains(address))
				{
					// Fail this case since we already know that this cuboid isn't loaded.
				}
				else if (toBatch.containsKey(address))
				{
					// We are already batching something from this cuboid so we know it is valid.  We can check previous cache or add to existing tuple.
					if (!_forceMissBlocksPreviousCache.contains(location) && _previousCache.containsKey(location))
					{
						// This can be satisfied from the previous cache.
						BlockProxy proxy = _previousCache.get(location);
						
						_cache.put(location, proxy);
						completed.put(location, proxy);
					}
					else
					{
						// Not in the previous cache so add it to the current batch for this cuboid.
						_BatchTuple tuple = toBatch.get(address);
						tuple.toFetch.add(location.getBlockAddress());
					}
				}
				else
				{
					// We haven't tried to access this cuboid yet so see if it is loaded.
					IReadOnlyCuboidData cuboid = _cuboidLookup.apply(address);
					if (null == cuboid)
					{
						// Not loaded so just force-miss this cuboid.
						_misses.add(address);
					}
					else
					{
						// The cuboid is loaded so now we can check the previous cache.
						if (!_forceMissBlocksPreviousCache.contains(location) && _previousCache.containsKey(location))
						{
							// This can be satisfied from the previous cache.
							BlockProxy proxy = _previousCache.get(location);
							
							_cache.put(location, proxy);
							completed.put(location, proxy);
						}
						else
						{
							// We will need to actually load this from the cuboid so create a new batch tuple.
							_BatchTuple newTuple = new _BatchTuple(cuboid);
							newTuple.toFetch.add(location.getBlockAddress());
							toBatch.put(address, newTuple);
						}
					}
				}
			}
		}
		
		// For the batches, we use the basic case for single-elements but the batch interface for multiple.
		for (Map.Entry<CuboidAddress, _BatchTuple> req : toBatch.entrySet())
		{
			CuboidAddress cuboidAddress = req.getKey();
			AbsoluteLocation base = cuboidAddress.getBase();
			_BatchTuple tuple = req.getValue();
			IReadOnlyCuboidData cuboid = tuple.cuboid;
			List<BlockAddress> list = tuple.toFetch;
			
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
				// Sort these for a batch.
				BlockAddress[] array = list.toArray((int size) -> new BlockAddress[size]);
				IReadOnlyCuboidData.BlockAddressBatchComparator comparator = new IReadOnlyCuboidData.BlockAddressBatchComparator();
				Arrays.sort(array, comparator);
				
				// Get the raw block values, directly.
				short[] blocks = cuboid.batchReadData15(AspectRegistry.BLOCK, array);
				
				// Initialize the proxies directly, update the local cache, and complete these.
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

	@Override
	public AbsoluteLocation findFirstMatch(AbsoluteLocation centre, int horizontal, int vertical, Block type)
	{
		// Note that these "edge" cases, here, are inclusive.
		AbsoluteLocation base = centre.getRelative(-horizontal, -horizontal, -vertical);
		AbsoluteLocation edge = centre.getRelative(horizontal, horizontal, vertical);
		CuboidAddress lowCuboid = base.getCuboidAddress();
		CuboidAddress highCuboid = edge.getCuboidAddress();
		BlockAddress baseBlock = base.getBlockAddress();
		BlockAddress edgeBlock = edge.getBlockAddress();
		short blockValue = type.item().number();
		
		AbsoluteLocation result = null;
		for (int z = lowCuboid.z(); (null == result) && (z <= highCuboid.z()); ++z)
		{
			byte lowZ = (z == lowCuboid.z())
				? baseBlock.z()
				: 0
			;
			byte highZ = (z == highCuboid.z())
				? edgeBlock.z()
				: (Encoding.CUBOID_EDGE_SIZE - 1)
			;
			for (int y = lowCuboid.y(); (null == result) && (y <= highCuboid.y()); ++y)
			{
				byte lowY = (y == lowCuboid.y())
					? baseBlock.y()
					: 0
				;
				byte highY = (y == highCuboid.y())
					? edgeBlock.y()
					: (Encoding.CUBOID_EDGE_SIZE - 1)
				;
				for (int x = lowCuboid.x(); (null == result) && (x <= highCuboid.x()); ++x)
				{
					byte lowX = (x == lowCuboid.x())
						? baseBlock.x()
						: 0
					;
					byte highX = (x == highCuboid.x())
						? edgeBlock.x()
						: (Encoding.CUBOID_EDGE_SIZE - 1)
					;
					
					CuboidAddress address = CuboidAddress.fromInt(x, y, z);
					IReadOnlyCuboidData cuboid = _cuboidLookup.apply(address);
					if (null != cuboid)
					{
						BlockAddress low = new BlockAddress(lowX, lowY, lowZ);
						BlockAddress high = new BlockAddress(highX, highY, highZ);
						BlockAddress match = cuboid.findFirstData15(AspectRegistry.BLOCK, low, high, blockValue);
						if (null != match)
						{
							result = cuboid.getCuboidAddress().getBase().relativeForBlock(match);
						}
					}
				}
			}
		}
		return result;
	}

	public Map<AbsoluteLocation, BlockProxy> extractCache()
	{
		return _cache;
	}


	private static final class _BatchTuple
	{
		public final IReadOnlyCuboidData cuboid;
		public final List<BlockAddress> toFetch;
		
		public _BatchTuple(IReadOnlyCuboidData cuboid)
		{
			this.cuboid = cuboid;
			this.toFetch = new ArrayList<>();
		}
	}
}
