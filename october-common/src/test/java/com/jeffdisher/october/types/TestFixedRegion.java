package com.jeffdisher.october.types;

import org.junit.Assert;
import org.junit.Test;


public class TestFixedRegion
{
	@Test
	public void centreOfRegionWithCorners()
	{
		EntityLocation base = new EntityLocation(5.1f, -6.4f, 7.2f);
		EntityLocation edge = new EntityLocation(7.5f, -2.3f, 8.1f);
		
		EntityLocation centre = FixedRegion.fromBaseAndEdge(base, edge).getCentre();
		Assert.assertEquals(new EntityLocation(6.3f, -4.35f, 7.65f), centre);
	}

	@Test
	public void centreOfRegionByVolume()
	{
		EntityLocation base = new EntityLocation(5.1f, -6.4f, 7.2f);
		EntityVolume volume = new EntityVolume(0.5f, 0.4f);
		
		EntityLocation centre = FixedRegion.fromBaseAndVolume(base, volume).getCentre();
		Assert.assertEquals(new EntityLocation(5.3f, -6.2f, 7.45f), centre);
	}
}
