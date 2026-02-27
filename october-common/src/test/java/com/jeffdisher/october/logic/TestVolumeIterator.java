package com.jeffdisher.october.logic;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;


public class TestVolumeIterator
{
	@Test
	public void inBlock()
	{
		EntityLocation base = new EntityLocation(0.2f, 0.1f, 0.3f);
		float height = 0.5f;
		float width = 0.6f;
		EntityVolume volume = new EntityVolume(height, width);
		
		Set<AbsoluteLocation> set = new HashSet<>();
		for (AbsoluteLocation location : new VolumeIterator(base, volume))
		{
			boolean didAdd = set.add(location);
			Assert.assertTrue(didAdd);
		}
		Assert.assertEquals(1, set.size());
	}

	@Test
	public void crossManyBlocks()
	{
		EntityLocation base = new EntityLocation(-0.2f, -0.1f, -0.3f);
		float height = 1.5f;
		float width = 1.6f;
		EntityVolume volume = new EntityVolume(height, width);
		
		Set<AbsoluteLocation> set = new HashSet<>();
		for (AbsoluteLocation location : new VolumeIterator(base, volume))
		{
			boolean didAdd = set.add(location);
			Assert.assertTrue(didAdd);
		}
		Assert.assertEquals(27, set.size());
	}

	@Test
	public void getAllManyBlocks()
	{
		EntityLocation base = new EntityLocation(-0.2f, -0.1f, -0.3f);
		float height = 1.5f;
		float width = 1.6f;
		EntityVolume volume = new EntityVolume(height, width);
		
		List<AbsoluteLocation> locations = VolumeIterator.getAllInVolume(base, volume);
		Set<AbsoluteLocation> set = new HashSet<>();
		for (AbsoluteLocation location : locations)
		{
			boolean didAdd = set.add(location);
			Assert.assertTrue(didAdd);
		}
		Assert.assertEquals(27, set.size());
	}
}
