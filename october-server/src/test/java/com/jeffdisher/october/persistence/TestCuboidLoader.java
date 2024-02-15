package com.jeffdisher.october.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.registries.AspectRegistry;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.worldgen.CuboidGenerator;


public class TestCuboidLoader
{
	@ClassRule
	public static TemporaryFolder DIRECTORY = new TemporaryFolder();

	@Test
	public void empty() throws Throwable
	{
		CuboidLoader loader = new CuboidLoader(DIRECTORY.newFolder(), null);
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		
		// We should see nothing come back, not matter how many times we issue the request.
		Assert.assertNull(loader.getResultsAndIssueRequest(List.of(address)));
		Assert.assertNull(loader.getResultsAndIssueRequest(List.of(address)));
		Assert.assertNull(loader.getResultsAndIssueRequest(List.of(address)));
		loader.shutdown();
	}

	@Test
	public void basic() throws Throwable
	{
		CuboidLoader loader = new CuboidLoader(DIRECTORY.newFolder(), null);
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ItemRegistry.STONE);
		loader.preload(cuboid);
		
		// We should see this satisfied, but not on the first call (we will use 10 tries, with yields).
		Collection<CuboidData> results = loader.getResultsAndIssueRequest(List.of(address));
		Assert.assertNull(results);
		for (int i = 0; (null == results) && (i < 10); ++i)
		{
			Thread.sleep(10L);
			results = loader.getResultsAndIssueRequest(List.of(address));
		}
		Assert.assertEquals(1, results.size());
		loader.shutdown();
	}

	@Test
	public void flatWorld() throws Throwable
	{
		CuboidLoader loader = new CuboidLoader(DIRECTORY.newFolder(), new FlatWorldGenerator());
		CuboidAddress stoneAddress = new CuboidAddress((short)0, (short)0, (short)-1);
		CuboidAddress airAddress = new CuboidAddress((short)0, (short)0, (short)0);
		
		// We should see this satisfied, but not on the first call (we will use 10 tries, with yields).
		Collection<CuboidData> results = loader.getResultsAndIssueRequest(List.of(stoneAddress, airAddress));
		Assert.assertNull(results);
		List<CuboidData> loaded = new ArrayList<>();
		for (int i = 0; (2 != loaded.size()) && (i < 10); ++i)
		{
			Thread.sleep(10L);
			results = loader.getResultsAndIssueRequest(List.of());
			if (null != results)
			{
				loaded.addAll(results);
			}
		}
		Assert.assertEquals(2, loaded.size());
		BlockAddress block = new BlockAddress((byte)0, (byte)0, (byte)0);
		short test0 = loaded.get(0).getData15(AspectRegistry.BLOCK, block);
		short test1 = loaded.get(1).getData15(AspectRegistry.BLOCK, block);
		Assert.assertTrue((ItemRegistry.AIR.number() == test0) || (ItemRegistry.AIR.number() == test1));
		Assert.assertTrue((ItemRegistry.STONE.number() == test0) || (ItemRegistry.STONE.number() == test1));
		loader.shutdown();
	}
}
