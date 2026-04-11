package com.jeffdisher.october.ticks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.utils.Encoding;


/**
 * TickOutput is very simple but there are some helpers there we could test and we do want to check the performance of
 * some paths as some are in the single-threaded critical path.
 */
public class TestTickOutput
{
	@BeforeClass
	public static void setup() throws Throwable
	{
		Environment.createSharedInstance();
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void perf_mergeWithLargeProxyCache()
	{
		boolean infiniteLoopForProfiler = false;
		boolean longLoopForObjectiveScore = false;
		
		// We want to create a large number of TickOutput objects, all with large proxy caches, and then time how long it takes to merge them.
		TickOutput[] partials = new TickOutput[64];
		byte[] zLevels = new byte[] { 0, 7, 15, 30 };
		for (int i = 0; i < partials.length; ++i)
		{
			Map<AbsoluteLocation, BlockProxy> populatedProxyCache = new HashMap<>();
			CuboidAddress cuboidAddress = CuboidAddress.fromInt(i - 2, 0, 0);
			AbsoluteLocation base = cuboidAddress.getBase();
			for (byte z : zLevels)
			{
				for (byte y = 0; y < Encoding.CUBOID_EDGE_SIZE; ++y)
				{
					for (byte x = 0; x < Encoding.CUBOID_EDGE_SIZE; ++x)
					{
						BlockAddress blockAddress = new BlockAddress(x, y, z);
						BlockProxy proxy = BlockProxy.init(blockAddress, null, (short)0);
						AbsoluteLocation absolute = base.relativeForBlock(blockAddress);
						populatedProxyCache.put(absolute, proxy);
					}
				}
				
				// Also, add a few overlapping.
				AbsoluteLocation absolute = base.getRelative(-1, 0, z);
				BlockProxy proxy = BlockProxy.init(absolute.getBlockAddress(), null, (short)0);
				populatedProxyCache.put(absolute, proxy);
				absolute = base.getRelative(32, 0, z);
				proxy = BlockProxy.init(absolute.getBlockAddress(), null, (short)0);
				populatedProxyCache.put(absolute, proxy);
			}
			partials[i] = new TickOutput(TickOutput.WorldOutput.empty()
				, TickOutput.EntitiesOutput.empty()
				, TickOutput.CreaturesOutput.empty()
				, TickOutput.PassivesOutput.empty()
				, List.of()
				, List.of()
				, List.of()
				, List.of()
				, List.of()
				, List.of()
				, List.of()
				, Set.of()
				, populatedProxyCache
			);
		}
		
		if (infiniteLoopForProfiler)
		{
			while (true)
			{
				TickOutput[] test = partials.clone();
				TickOutput.mergeAndClearPartialFragments(test);
			}
		}
		else if (longLoopForObjectiveScore)
		{
			int iterationCount = 1_000;
			long startNanos = System.nanoTime();
			for (int i = 0; i < iterationCount; ++i)
			{
				TickOutput[] test = partials.clone();
				TickOutput.mergeAndClearPartialFragments(test);
			}
			long endNanos = System.nanoTime();
			System.out.println("Nanos per: " + ((endNanos - startNanos) / iterationCount));
		}
		else
		{
			// Merge and we expect to see to see 64 * 32 * 32 * 4 + 4 * 2;
			TickOutput merged = TickOutput.mergeAndClearPartialFragments(partials);
			Assert.assertEquals(partials.length * zLevels.length * Encoding.CUBOID_EDGE_SIZE * Encoding.CUBOID_EDGE_SIZE + 2 * zLevels.length, merged.populatedProxyCache().size());
		}
	}
}
