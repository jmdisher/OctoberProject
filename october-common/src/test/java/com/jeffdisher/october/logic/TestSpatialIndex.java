package com.jeffdisher.october.logic;

import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;


public class TestSpatialIndex
{
	@Test
	public void checkEmpty()
	{
		EntityVolume volume = new EntityVolume(1.0f, 0.5f);
		SpatialIndex index = new SpatialIndex.Builder().finish(volume);
		Set<Integer> ids = index.idsIntersectingRegion(new EntityLocation(-100.0f, -100.0f, -100.0f), new EntityLocation(100.0f, 100.0f, 100.0f));
		Assert.assertTrue(ids.isEmpty());
	}

	@Test
	public void noneInRange()
	{
		EntityVolume volume = new EntityVolume(1.0f, 0.5f);
		SpatialIndex index = new SpatialIndex.Builder()
			.add(1, new EntityLocation(0.0f, 0.0f, 200.0f))
			.finish(volume);
		Set<Integer> ids = index.idsIntersectingRegion(new EntityLocation(-100.0f, -100.0f, -100.0f), new EntityLocation(100.0f, 100.0f, 100.0f));
		Assert.assertTrue(ids.isEmpty());
	}

	@Test
	public void someInRange()
	{
		EntityVolume volume = new EntityVolume(1.0f, 0.5f);
		SpatialIndex index = new SpatialIndex.Builder()
			.add(1, new EntityLocation(0.0f, 0.0f, 200.0f))
			.add(2, new EntityLocation(5.0f, -7.0f, 20.0f))
			.add(3, new EntityLocation(-100.1f, 50.0f, -50.0f))
			.add(4, new EntityLocation(0.0f, 0.0f, 200.0f))
			.finish(volume);
		Set<Integer> ids = index.idsIntersectingRegion(new EntityLocation(-100.0f, -100.0f, -100.0f), new EntityLocation(100.0f, 100.0f, 100.0f));
		Assert.assertEquals(2, ids.size());
		Assert.assertTrue(ids.contains(2));
		Assert.assertTrue(ids.contains(3));
	}

	@Test
	public void inclusiveBase()
	{
		EntityVolume volume = new EntityVolume(1.0f, 0.5f);
		SpatialIndex index = new SpatialIndex.Builder()
			.add(1, new EntityLocation(- volume.width(), - volume.width(), - volume.height()))
			.finish(volume);
		Set<Integer> ids = index.idsIntersectingRegion(new EntityLocation(0.0f, 0.0f, 0.0f), new EntityLocation(100.0f, 100.0f, 100.0f));
		Assert.assertEquals(1, ids.size());
	}

	@Test
	public void inclusiveEdge()
	{
		EntityVolume volume = new EntityVolume(1.0f, 0.5f);
		SpatialIndex index = new SpatialIndex.Builder()
			.add(1, new EntityLocation(100.0f, 100.0f, 100.0f))
			.finish(volume);
		Set<Integer> ids = index.idsIntersectingRegion(new EntityLocation(0.0f, 0.0f, 0.0f), new EntityLocation(100.0f, 100.0f, 100.0f));
		Assert.assertEquals(1, ids.size());
	}
}
