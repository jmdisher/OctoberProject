package com.jeffdisher.october.persistence;

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
		Assert.assertTrue(loader.getResultsAndIssueRequest(List.of(address)).isEmpty());
		Assert.assertTrue(loader.getResultsAndIssueRequest(List.of(address)).isEmpty());
		Assert.assertTrue(loader.getResultsAndIssueRequest(List.of(address)).isEmpty());
	}

	@Test
	public void basic()
	{
		CuboidLoader loader = new CuboidLoader();
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ItemRegistry.STONE);
		loader.preload(cuboid);
		
		// We should see this satisfied.
		Assert.assertEquals(1, loader.getResultsAndIssueRequest(List.of(address)).size());
	}
}
