package com.jeffdisher.october.persistence;

import java.io.File;
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

	@Test
	public void writeThenRead() throws Throwable
	{
		File worldDirectory = DIRECTORY.newFolder();
		CuboidLoader loader = new CuboidLoader(worldDirectory, new FlatWorldGenerator());
		CuboidAddress airAddress = new CuboidAddress((short)0, (short)0, (short)0);
		
		// We should see this satisfied, but not on the first call (we will use 10 tries, with yields).
		Collection<CuboidData> results = loader.getResultsAndIssueRequest(List.of(airAddress));
		Assert.assertNull(results);
		CuboidData loaded = _waitForOne(loader);
		BlockAddress block = new BlockAddress((byte)0, (byte)0, (byte)0);
		// Modify a block and write this back.
		loaded.setData15(AspectRegistry.BLOCK, block, ItemRegistry.STONE.number());
		loader.writeBackToDisk(List.of(loaded));
		// (the shutdown will wait for the queue to drain)
		loader.shutdown();
		
		// Make sure that we see this written back.
		String fileName = "cuboid_" + airAddress.x() + "_" + airAddress.y() + "_" + airAddress.z() + ".cuboid";
		Assert.assertTrue(new File(worldDirectory, fileName).isFile());
		
		// Now, create a new loader to verify that we can read this.
		loader = new CuboidLoader(worldDirectory, null);
		results = loader.getResultsAndIssueRequest(List.of(airAddress));
		Assert.assertNull(results);
		loaded = _waitForOne(loader);
		Assert.assertEquals(ItemRegistry.STONE.number(), loaded.getData15(AspectRegistry.BLOCK, block));
		loader.shutdown();
	}


	private static CuboidData _waitForOne(CuboidLoader loader) throws InterruptedException
	{
		CuboidData loaded = null;
		for (int i = 0; (null == loaded) && (i < 10); ++i)
		{
			Thread.sleep(10L);
			Collection<CuboidData> results = loader.getResultsAndIssueRequest(List.of());
			if (null != results)
			{
				Assert.assertTrue(1 == results.size());
				loaded = results.iterator().next();
			}
		}
		// We expect that this should be loaded.
		Assert.assertNotNull(loaded);
		return loaded;
	}
}
