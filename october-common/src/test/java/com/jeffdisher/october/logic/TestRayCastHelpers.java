package com.jeffdisher.october.logic;

import java.util.List;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;


public class TestRayCastHelpers
{
	private static Environment ENV;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

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
		RayCastHelpers.RayMovement result = RayCastHelpers.applyMovement(start, ENV.creatures.PLAYER.volume(), vector, (AbsoluteLocation l) -> {
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
		EntityVolume volume = ENV.creatures.PLAYER.volume();
		RayCastHelpers.RayMovement result = RayCastHelpers.applyMovement(start, volume, vector, (AbsoluteLocation l) -> {
			return 0 == l.z();
		});
		Assert.assertNotNull(result);
		Assert.assertEquals(new EntityLocation(2.28f, 0.69f, -volume.height()), result.location());
		Assert.assertEquals(5.23f, result.rayDistance(), 0.01f);
		Assert.assertEquals(RayCastHelpers.Axis.Z, result.collisionAxis());
	}

	@Test
	public void lineSegment() throws Throwable
	{
		// Show that we can find a line passing through every block between start and end blocks.
		AbsoluteLocation start = new AbsoluteLocation(-1, -2, 0);
		AbsoluteLocation end = new AbsoluteLocation(1, 1, 1);
		List<AbsoluteLocation> path = RayCastHelpers.findFullLine(start, end);
		// We just want to make sure that the path starts and ends at the right places and each step differs by only one block.
		Assert.assertEquals(7, path.size());
		Assert.assertEquals(start, path.get(0));
		Assert.assertEquals(end, path.get(path.size() - 1));
		_checkPathOneBlockStep(path);
		
		// Check 1.
		path = RayCastHelpers.findFullLine(start, start);
		Assert.assertEquals(1, path.size());
		Assert.assertEquals(start, path.get(0));
	}

	@Test
	public void lineError() throws Throwable
	{
		// A test to fix a bug in the DDA algorithm we found when building a line.
		AbsoluteLocation start = new AbsoluteLocation(-303, 273, 11);
		AbsoluteLocation end = new AbsoluteLocation(-281, 264, 26);
		List<AbsoluteLocation> path = RayCastHelpers.findFullLine(start, end);
		// We just want to make sure that the path starts and ends at the right places and each step differs by only one block.
		Assert.assertEquals(47, path.size());
		Assert.assertEquals(start, path.get(0));
		Assert.assertEquals(end, path.get(path.size() - 1));
		_checkPathOneBlockStep(path);
	}

	@Test
	public void stuckInBlock() throws Throwable
	{
		// Show that we can't move at all if stuck in a block.
		EntityLocation start = new EntityLocation(-1.2f, -2.3f, -3.4f);
		EntityLocation vector = new EntityLocation(7.8f, 6.7f, 5.6f);
		EntityVolume volume = ENV.creatures.PLAYER.volume();
		RayCastHelpers.RayMovement result = RayCastHelpers.applyMovement(start, volume, vector, (AbsoluteLocation l) -> {
			return true;
		});
		Assert.assertNotNull(result);
		Assert.assertEquals(start, result.location());
		Assert.assertEquals(0.0f, result.rayDistance(), 0.01f);
		Assert.assertEquals(RayCastHelpers.Axis.INTERNAL, result.collisionAxis());
	}


	private static void _checkPathOneBlockStep(List<AbsoluteLocation> path)
	{
		for (int i = 1; i < path.size(); ++i)
		{
			AbsoluteLocation prev = path.get(i - 1);
			AbsoluteLocation current = path.get(i);
			int dX = Math.abs(current.x() - prev.x());
			int dY = Math.abs(current.y() - prev.y());
			int dZ = Math.abs(current.z() - prev.z());
			Assert.assertEquals(1, dX + dY + dZ);
		}
	}
}
