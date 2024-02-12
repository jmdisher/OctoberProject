package com.jeffdisher.october.persistence;

import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.worldgen.CuboidGenerator;


public class TestCuboidLoader
{
	@Test
	public void empty()
	{
		CuboidLoader loader = new CuboidLoader();
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
		CuboidLoader loader = new CuboidLoader();
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
}
