package com.jeffdisher.october.logic;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.FacingDirection;


public class TestOrientationHelpers
{
	@Test
	public void north()
	{
		byte yaw = OrientationHelpers.YAW_NORTH;
		Assert.assertEquals((byte)0, yaw);
		float radians = OrientationHelpers.getYawRadians(yaw);
		Assert.assertEquals(0.0f, radians, 0.01f);
		Assert.assertEquals(0.0f, OrientationHelpers.getEastYawComponent(radians), 0.01f);
		Assert.assertEquals(1.0f, OrientationHelpers.getNorthYawComponent(radians), 0.01f);
		Assert.assertEquals(yaw, OrientationHelpers.yawFromRadians(radians));
	}

	@Test
	public void west()
	{
		byte yaw = OrientationHelpers.YAW_WEST;
		Assert.assertEquals((byte)64, yaw);
		float radians = OrientationHelpers.getYawRadians(yaw);
		Assert.assertEquals((float)(Math.PI / 2.0), radians, 0.01f);
		Assert.assertEquals(-1.0f, OrientationHelpers.getEastYawComponent(radians), 0.01f);
		Assert.assertEquals(0.0f, OrientationHelpers.getNorthYawComponent(radians), 0.01f);
		Assert.assertEquals(yaw, OrientationHelpers.yawFromRadians(radians));
	}

	@Test
	public void south()
	{
		byte yaw = OrientationHelpers.YAW_SOUTH;
		Assert.assertEquals((byte)-128, yaw);
		float radians = OrientationHelpers.getYawRadians(yaw);
		Assert.assertEquals((float)Math.PI, radians, 0.01f);
		Assert.assertEquals(0.0f, OrientationHelpers.getEastYawComponent(radians), 0.01f);
		Assert.assertEquals(-1.0f, OrientationHelpers.getNorthYawComponent(radians), 0.01f);
		Assert.assertEquals(yaw, OrientationHelpers.yawFromRadians(radians));
	}

	@Test
	public void east()
	{
		byte yaw = OrientationHelpers.YAW_EAST;
		Assert.assertEquals((byte)-64, yaw);
		float radians = OrientationHelpers.getYawRadians(yaw);
		Assert.assertEquals((float)(3.0 * Math.PI / 2.0), radians, 0.01f);
		Assert.assertEquals(1.0f, OrientationHelpers.getEastYawComponent(radians), 0.01f);
		Assert.assertEquals(0.0f, OrientationHelpers.getNorthYawComponent(radians), 0.01f);
		Assert.assertEquals(yaw, OrientationHelpers.yawFromRadians(radians));
	}

	@Test
	public void pitch()
	{
		float radFlat = OrientationHelpers.getPitchRadians(OrientationHelpers.PITCH_FLAT);
		float radUp = OrientationHelpers.getPitchRadians(OrientationHelpers.PITCH_UP);
		float radDown = OrientationHelpers.getPitchRadians(OrientationHelpers.PITCH_DOWN);
		
		Assert.assertEquals(0.0f, radFlat, 0.01f);
		Assert.assertEquals((float)(Math.PI / 2.0), radUp, 0.01f);
		Assert.assertEquals((float)(3.0 * Math.PI / 2.0), radDown, 0.01f);
		
		Assert.assertEquals(OrientationHelpers.PITCH_FLAT, OrientationHelpers.pitchFromRadians(radFlat));
		Assert.assertEquals(OrientationHelpers.PITCH_UP, OrientationHelpers.pitchFromRadians(radUp));
		Assert.assertEquals(OrientationHelpers.PITCH_DOWN, OrientationHelpers.pitchFromRadians(radDown));
	}

	@Test
	public void componentDistanceOne()
	{
		// Make sure that our component calculations always give us a unit radius.
		float radians = 0.0f;
		while (radians < 7.0f)
		{
			float x = OrientationHelpers.getEastYawComponent(radians);
			float y = OrientationHelpers.getNorthYawComponent(radians);
			float distance = (float)Math.sqrt(x * x + y * y);
			Assert.assertEquals(1.0f, distance, 0.01f);
			radians += 0.1f;
		}
	}

	@Test
	public void directionsFromYaw()
	{
		Assert.assertEquals(FacingDirection.NORTH, OrientationHelpers.getYawDirection((byte)10));
		Assert.assertEquals(FacingDirection.WEST, OrientationHelpers.getYawDirection((byte)50));
		Assert.assertEquals(FacingDirection.SOUTH, OrientationHelpers.getYawDirection((byte)140));
		Assert.assertEquals(FacingDirection.EAST, OrientationHelpers.getYawDirection((byte)200));
	}

	@Test
	public void yawBetweenPoints()
	{
		EntityLocation source = new EntityLocation(1.2f, -2.3f, 0.5f);
		
		// Show cardinal directions.
		Assert.assertEquals(OrientationHelpers.YAW_NORTH, OrientationHelpers.getYawBetweenPoints(source, new EntityLocation(1.2f, 5.6f, 0.5f)));
		Assert.assertEquals(OrientationHelpers.YAW_WEST, OrientationHelpers.getYawBetweenPoints(source, new EntityLocation(0.2f, -2.3f, 0.5f)));
		Assert.assertEquals(OrientationHelpers.YAW_SOUTH, OrientationHelpers.getYawBetweenPoints(source, new EntityLocation(1.2f, -2.4f, 0.5f)));
		Assert.assertEquals(OrientationHelpers.YAW_EAST, OrientationHelpers.getYawBetweenPoints(source, new EntityLocation(15.2f, -2.3f, 0.5f)));
		
		// Show slight deviations from cardinal directions to show the limits are expected.
		Assert.assertEquals((byte)1, OrientationHelpers.getYawBetweenPoints(source, new EntityLocation(1.1f, 5.6f, 0.5f)));
		Assert.assertEquals((byte)-1, OrientationHelpers.getYawBetweenPoints(source, new EntityLocation(1.3f, 5.6f, 0.5f)));
		Assert.assertEquals((byte)68, OrientationHelpers.getYawBetweenPoints(source, new EntityLocation(0.2f, -2.4f, 0.5f)));
		Assert.assertEquals((byte)60, OrientationHelpers.getYawBetweenPoints(source, new EntityLocation(0.2f, -2.2f, 0.5f)));
		Assert.assertEquals((byte)96, OrientationHelpers.getYawBetweenPoints(source, new EntityLocation(1.1f, -2.4f, 0.5f)));
		Assert.assertEquals((byte)-96, OrientationHelpers.getYawBetweenPoints(source, new EntityLocation(1.3f, -2.4f, 0.5f)));
		Assert.assertEquals((byte)-65, OrientationHelpers.getYawBetweenPoints(source, new EntityLocation(15.2f, -2.6f, 0.5f)));
		Assert.assertEquals((byte)-63, OrientationHelpers.getYawBetweenPoints(source, new EntityLocation(15.2f, -2.0f, 0.5f)));
	}
}
