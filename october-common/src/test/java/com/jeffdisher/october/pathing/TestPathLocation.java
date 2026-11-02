package com.jeffdisher.october.pathing;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.FacingDirection;


public class TestPathLocation
{
	@Test
	public void basics()
	{
		AbsoluteLocation location = new AbsoluteLocation(-1, 5, 8);
		PathLocation start = PathLocation.test_raw(location, 0, 0, 0);
		PathLocation west = start.getRelative(-1, 0, 0);
		PathLocation down = start.getRelative(0, 0, -1);
		PathLocation farAway = start.getRelative(5, -90, 41);
		
		Assert.assertEquals(FacingDirection.DOWN, PathLocation.adjacentDirection(start, down));
		Assert.assertEquals(new EntityLocation(-1.0f, 5.0f, 8.0f), start.getBaseLocation());
		Assert.assertEquals(new EntityLocation(-1.5f, 5.0f, 8.0f), west.getBaseLocation());
		Assert.assertEquals(new EntityLocation(-1.0f, 5.0f, 7.5f), down.getBaseLocation());
		Assert.assertEquals(new EntityLocation(1.5f, -40.0f, 28.5f), farAway.getBaseLocation());
	}

	@Test
	public void baseAndDistance()
	{
		AbsoluteLocation location = new AbsoluteLocation(-1, 5, 8);
		PathLocation one = PathLocation.test_raw(location, 0, 0, 0);
		PathLocation two = PathLocation.test_raw(location, 1, 1, 1);
		PathLocation three = PathLocation.forEntityBase(new EntityLocation(-1.8f, 6.9f, -10.1f));
		
		Assert.assertEquals(3, PathLocation.pathStepDistance(one, two));
		Assert.assertEquals(new EntityLocation(-1.0f, 5.0f, 8.0f), one.getBaseLocation());
		Assert.assertEquals(new EntityLocation(-0.5f, 5.5f, 8.5f), two.getBaseLocation());
		Assert.assertEquals(new EntityLocation(-2.0f, 6.5f, -10.5f), three.getBaseLocation());
	}

	@Test
	public void relativeCalculations()
	{
		AbsoluteLocation negativeBase = new AbsoluteLocation(-1, -2, -3);
		AbsoluteLocation positiveBase = new AbsoluteLocation(4, 5, 6);
		PathLocation nz = PathLocation.test_raw(negativeBase, 0, 0, 0);
		PathLocation no = PathLocation.test_raw(negativeBase, 1, 1, 1);
		PathLocation pz = PathLocation.test_raw(positiveBase, 0, 0, 0);
		PathLocation po = PathLocation.test_raw(positiveBase, 1, 1, 1);
		
		Assert.assertEquals(new EntityLocation(-1.0f, -2.0f, -3.0f), nz.getBaseLocation());
		Assert.assertEquals(new EntityLocation(-0.5f, -1.5f, -2.5f), no.getBaseLocation());
		Assert.assertEquals(new EntityLocation(4.0f, 5.0f, 6.0f), pz.getBaseLocation());
		Assert.assertEquals(new EntityLocation(4.5f, 5.5f, 6.5f), po.getBaseLocation());
		
		Assert.assertEquals(new EntityLocation(-6.0f, -7.5f, -9.0f), nz.getRelative(-10, -11, -12).getBaseLocation());
		Assert.assertEquals(new EntityLocation(5.5f, 5.0f, 4.5f), nz.getRelative(13, 14, 15).getBaseLocation());
		Assert.assertEquals(new EntityLocation(-5.5f, -7.0f, -8.5f), no.getRelative(-10, -11, -12).getBaseLocation());
		Assert.assertEquals(new EntityLocation(6.0f, 5.5f, 5.0f), no.getRelative(13, 14, 15).getBaseLocation());
		Assert.assertEquals(new EntityLocation(-1.0f, -0.5f, 0.0f), pz.getRelative(-10, -11, -12).getBaseLocation());
		Assert.assertEquals(new EntityLocation(10.5f, 12.0f, 13.5f), pz.getRelative(13, 14, 15).getBaseLocation());
		Assert.assertEquals(new EntityLocation(-0.5f, 0.0f, 0.5f), po.getRelative(-10, -11, -12).getBaseLocation());
		Assert.assertEquals(new EntityLocation(11.0f, 12.5f, 14.0f), po.getRelative(13, 14, 15).getBaseLocation());
	}
}
