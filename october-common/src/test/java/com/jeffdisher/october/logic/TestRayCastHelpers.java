package com.jeffdisher.october.logic;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.EntityConstants;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;


public class TestRayCastHelpers
{
	@Test
	public void positiveRayNoMatch() throws Throwable
	{
		EntityLocation start = new EntityLocation(-2.0f, -3.0f, -4.0f);
		EntityLocation end = new EntityLocation(2.0f, 3.0f, 4.0f);
		AbsoluteLocation[] holder = new AbsoluteLocation[1];
		RayCastHelpers.RayBlock result = RayCastHelpers.findFirstCollision(start, end, (AbsoluteLocation l) -> {
			if (null != holder[0])
			{
				Assert.assertEquals(1, Math.abs(holder[0].x() - l.x())
						+ Math.abs(holder[0].y() - l.y())
						+ Math.abs(holder[0].z() - l.z())
				);
			}
			holder[0] = l;
			return false;
		});
		Assert.assertNull(result);
	}

	@Test
	public void negativeAlignedRayMatch() throws Throwable
	{
		EntityLocation start = new EntityLocation(0.0f, 5.0f, 0.0f);
		EntityLocation end = new EntityLocation(0.0f, -2.0f, 0.0f);
		AbsoluteLocation[] holder = new AbsoluteLocation[1];
		RayCastHelpers.RayBlock result = RayCastHelpers.findFirstCollision(start, end, (AbsoluteLocation l) -> {
			if (null != holder[0])
			{
				Assert.assertEquals(1, Math.abs(holder[0].x() - l.x())
						+ Math.abs(holder[0].y() - l.y())
						+ Math.abs(holder[0].z() - l.z())
				);
			}
			holder[0] = l;
			return l.equals(end.getBlockLocation());
		});
		Assert.assertEquals(new AbsoluteLocation(0, -2, 0), result.stopBlock());
		Assert.assertEquals(new AbsoluteLocation(0, -1, 0), result.preStopBlock());
		Assert.assertEquals(6.0f, result.rayDistance(), 0.01f);
		Assert.assertEquals(RayCastHelpers.Axis.Y, result.collisionAxis());
	}

	@Test
	public void shortVector() throws Throwable
	{
		EntityLocation start = new EntityLocation(29.34f, -1.26f, 10.9f);
		EntityLocation end = new EntityLocation(30.24f, -0.97f, 10.59f);
		AbsoluteLocation[] holder = new AbsoluteLocation[1];
		RayCastHelpers.RayBlock result = RayCastHelpers.findFirstCollision(start, end, (AbsoluteLocation l) -> {
			if (null != holder[0])
			{
				Assert.assertEquals(1, Math.abs(holder[0].x() - l.x())
						+ Math.abs(holder[0].y() - l.y())
						+ Math.abs(holder[0].z() - l.z())
				);
			}
			holder[0] = l;
			return false;
		});
		Assert.assertNull(result);
	}

	@Test
	public void checkShortDistanceVector() throws Throwable
	{
		EntityLocation start = new EntityLocation(-1.2f, -2.3f, -3.4f);
		EntityLocation end = new EntityLocation(-0.8f, -1.9f, -3.2f);
		AbsoluteLocation[] holder = new AbsoluteLocation[1];
		RayCastHelpers.RayBlock result = RayCastHelpers.findFirstCollision(start, end, (AbsoluteLocation l) -> {
			if (null != holder[0])
			{
				Assert.assertEquals(1, Math.abs(holder[0].x() - l.x())
						+ Math.abs(holder[0].y() - l.y())
						+ Math.abs(holder[0].z() - l.z())
				);
			}
			holder[0] = l;
			return l.equals(end.getBlockLocation());
		});
		Assert.assertNotNull(result);
		Assert.assertEquals(end.getBlockLocation(), result.stopBlock());
		Assert.assertEquals(end.getBlockLocation().getRelative(0, -1, 0), result.preStopBlock());
		Assert.assertEquals(0.45f, result.rayDistance(), 0.01f);
		Assert.assertEquals(RayCastHelpers.Axis.Y, result.collisionAxis());
	}

	@Test
	public void checkLongDistanceVector() throws Throwable
	{
		EntityLocation start = new EntityLocation(-1.2f, -2.3f, -3.4f);
		EntityLocation end = new EntityLocation(6.7f, 7.8f, 8.9f);
		AbsoluteLocation[] holder = new AbsoluteLocation[1];
		RayCastHelpers.RayBlock result = RayCastHelpers.findFirstCollision(start, end, (AbsoluteLocation l) -> {
			if (null != holder[0])
			{
				Assert.assertEquals(1, Math.abs(holder[0].x() - l.x())
						+ Math.abs(holder[0].y() - l.y())
						+ Math.abs(holder[0].z() - l.z())
				);
			}
			holder[0] = l;
			return l.equals(end.getBlockLocation());
		});
		Assert.assertNotNull(result);
		Assert.assertEquals(end.getBlockLocation(), result.stopBlock());
		Assert.assertEquals(end.getBlockLocation().getRelative(0, 0, -1), result.preStopBlock());
		Assert.assertEquals(16.47f, result.rayDistance(), 0.01f);
		Assert.assertEquals(RayCastHelpers.Axis.Z, result.collisionAxis());
	}

	@Test
	public void moveByLongDistanceVector() throws Throwable
	{
		// Show that we move the full distance of the velocity vector when we don't hit anything.
		EntityLocation start = new EntityLocation(-1.2f, -2.3f, -3.4f);
		EntityLocation vector = new EntityLocation(7.8f, 6.7f, 5.6f);
		RayCastHelpers.RayMovement result = RayCastHelpers.applyMovement(start, EntityConstants.VOLUME_PLAYER, vector, (AbsoluteLocation l) -> {
			return false;
		});
		Assert.assertNotNull(result);
		Assert.assertEquals(new EntityLocation(start.x() + vector.x(), start.y() + vector.y(), start.z() + vector.z()), result.location());
		Assert.assertNull(result.collisionAxis());
		Assert.assertEquals(11.71f, result.rayDistance(), 0.01f);
	}

	@Test
	public void moveHitCeiling() throws Throwable
	{
		// Show that we move as far as being fully pressed against the ceiling, if that is what we hit.
		EntityLocation start = new EntityLocation(-1.2f, -2.3f, -3.4f);
		EntityLocation vector = new EntityLocation(7.8f, 6.7f, 5.6f);
		EntityVolume volume = EntityConstants.VOLUME_PLAYER;
		RayCastHelpers.RayMovement result = RayCastHelpers.applyMovement(start, volume, vector, (AbsoluteLocation l) -> {
			return 0 == l.z();
		});
		Assert.assertNotNull(result);
		Assert.assertEquals(new EntityLocation(2.28f, 0.69f, -volume.height()), result.location());
		Assert.assertEquals(5.23f, result.rayDistance(), 0.01f);
		Assert.assertEquals(RayCastHelpers.Axis.Z, result.collisionAxis());
	}
}
