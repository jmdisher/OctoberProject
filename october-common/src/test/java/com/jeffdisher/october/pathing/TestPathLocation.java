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
		PathLocation start = PathLocation.test_raw(location);
		PathLocation west = start.getRelative(-1, 0, 0);
		PathLocation down = start.getRelative(0, 0, -1);
		PathLocation farAway = start.getRelative(5, -90, 41);
		
		Assert.assertEquals(FacingDirection.DOWN, PathLocation.adjacentDirection(start, down));
		Assert.assertEquals(new EntityLocation(-1.0f, 5.0f, 8.0f), start.getBaseLocation());
		Assert.assertEquals(new EntityLocation(-2.0f, 5.0f, 8.0f), west.getBaseLocation());
		Assert.assertEquals(new EntityLocation(-1.0f, 5.0f, 7.0f), down.getBaseLocation());
		Assert.assertEquals(new EntityLocation(4.0f, -85.0f, 49.0f), farAway.getBaseLocation());
	}

	@Test
	public void baseAndDistance()
	{
		AbsoluteLocation location = new AbsoluteLocation(-1, 5, 8);
		PathLocation one = PathLocation.test_raw(location);
		PathLocation two = PathLocation.test_raw(location.getRelative(1, 1, 1));
		PathLocation three = PathLocation.forEntityBase(new EntityLocation(-1.8f, 6.9f, -10.1f));
		
		Assert.assertEquals(3, PathLocation.pathStepDistance(one, two));
		Assert.assertEquals(new EntityLocation(-1.0f, 5.0f, 8.0f), one.getBaseLocation());
		Assert.assertEquals(new EntityLocation(0.0f, 6.0f, 9.0f), two.getBaseLocation());
		Assert.assertEquals(new EntityLocation(-2.0f, 6.0f, -11.0f), three.getBaseLocation());
	}
}
