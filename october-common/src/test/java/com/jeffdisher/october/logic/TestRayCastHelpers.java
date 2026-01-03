package com.jeffdisher.october.logic;

import java.util.List;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.MutableEntity;


public class TestRayCastHelpers
{
	private static Environment ENV;
	@BeforeClass
	public static void setup() throws Throwable
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
	public void intersectPlayers() throws Throwable
	{
		// Create some players in a collection and intersect 
		Map<Integer, Entity> players = Map.of(1, _buildPlayer(1, new EntityLocation(1.0f, 1.0f, 1.0f))
			, 2, _buildPlayer(2, new EntityLocation(2.0f, 2.0f, 2.0f))
			, 3, _buildPlayer(3, new EntityLocation(-1.0f, -1.0f, -1.0f))
		);
		EntityCollection collection = EntityCollection.fromMaps(players, Map.of());
		
		int id = RayCastHelpers.findFirstCollisionInCollection(ENV, new EntityLocation(3.0f, 3.0f, 3.0f), new EntityLocation(-5.0f, -5.0f, -5.0f), collection);
		Assert.assertEquals(2, id);
		
		id = RayCastHelpers.findFirstCollisionInCollection(ENV, new EntityLocation(3.0f, 3.0f, 3.0f), new EntityLocation(5.0f, 5.0f, 5.0f), collection);
		Assert.assertEquals(0, id);
		
		id = RayCastHelpers.findFirstCollisionInCollection(ENV, new EntityLocation(0.0f, 0.0f, 0.0f), new EntityLocation(5.0f, 5.0f, 5.0f), collection);
		Assert.assertEquals(1, id);
		
		id = RayCastHelpers.findFirstCollisionInCollection(ENV, new EntityLocation(-5.0f, -5.0f, -5.0f), new EntityLocation(5.0f, 5.0f, 5.0f), collection);
		Assert.assertEquals(3, id);
		
		id = RayCastHelpers.findFirstCollisionInCollection(ENV, new EntityLocation(2.0f, -3.0f, -1.5f), new EntityLocation(-2.0f, 3.0f, 1.5f), collection);
		Assert.assertEquals(0, id);
	}

	@Test
	public void rayDirectionInIntersection() throws Throwable
	{
		// We show that the intersection logic is directional, such that the intersection will only be considered when "entering" the player.
		// Create some players in a collection and intersect 
		Map<Integer, Entity> players = Map.of(1, _buildPlayer(1, new EntityLocation(0.0f, 0.0f, 0.0f))
		);
		EntityCollection collection = EntityCollection.fromMaps(players, Map.of());
		
		// Leave the player to negative (does NOT count as intersection since it isn't "entering").
		int id = RayCastHelpers.findFirstCollisionInCollection(ENV, new EntityLocation(0.1f, 0.1f, 0.1f), new EntityLocation(-1.0f, -1.0f, -1.0f), collection);
		Assert.assertEquals(0, id);
		
		// Leave the player to positive (does NOT count as intersection since it isn't "entering").
		id = RayCastHelpers.findFirstCollisionInCollection(ENV, new EntityLocation(0.1f, 0.1f, 0.1f), new EntityLocation(2.0f, 2.0f, 2.0f), collection);
		Assert.assertEquals(0, id);
		
		// Entering the player from negative.
		id = RayCastHelpers.findFirstCollisionInCollection(ENV, new EntityLocation(-1.0f, -1.0f, -1.0f), new EntityLocation(0.1f, 0.1f, 0.1f), collection);
		Assert.assertEquals(1, id);
		
		// Entering the player from positive.
		id = RayCastHelpers.findFirstCollisionInCollection(ENV, new EntityLocation(2.0f, 2.0f, 2.0f), new EntityLocation(0.1f, 0.1f, 0.1f), collection);
		Assert.assertEquals(1, id);
		
		// Staying inside the player to negative (does NOT count as intersection since it isn't "entering").
		id = RayCastHelpers.findFirstCollisionInCollection(ENV, new EntityLocation(0.2f, 0.2f, 0.2f), new EntityLocation(0.1f, 0.1f, 0.1f), collection);
		Assert.assertEquals(0, id);
		
		// Staying inside the player to positive (does NOT count as intersection since it isn't "entering").
		id = RayCastHelpers.findFirstCollisionInCollection(ENV, new EntityLocation(0.1f, 0.1f, 0.1f), new EntityLocation(0.2f, 0.2f, 0.2f), collection);
		Assert.assertEquals(0, id);
		
		// Entering on one axis from negative while inside, from the others.
		id = RayCastHelpers.findFirstCollisionInCollection(ENV, new EntityLocation(-1.0f, 0.1f, 0.2f), new EntityLocation(0.1f, 0.1f, 0.1f), collection);
		Assert.assertEquals(1, id);
		
		// Entering on one axis from positive while inside, from the others.
		id = RayCastHelpers.findFirstCollisionInCollection(ENV, new EntityLocation(2.0f, 0.1f, 0.2f), new EntityLocation(0.1f, 0.1f, 0.1f), collection);
		Assert.assertEquals(1, id);
		
		// Pass right through from negative.
		id = RayCastHelpers.findFirstCollisionInCollection(ENV, new EntityLocation(-1.0f, -1.0f, -1.0f), new EntityLocation(2.0f, 2.0f, 2.0f), collection);
		Assert.assertEquals(1, id);
		
		// Pass right through from positive.
		id = RayCastHelpers.findFirstCollisionInCollection(ENV, new EntityLocation(2.0f, 2.0f, 2.0f), new EntityLocation(-1.0f, -1.0f, -1.0f), collection);
		Assert.assertEquals(1, id);
		
		// Pass over from negative.
		id = RayCastHelpers.findFirstCollisionInCollection(ENV, new EntityLocation(-1.0f, -1.0f, 2.0f), new EntityLocation(2.0f, 2.0f, 2.0f), collection);
		Assert.assertEquals(0, id);
		
		// Pass over from positive.
		id = RayCastHelpers.findFirstCollisionInCollection(ENV, new EntityLocation(2.0f, 2.0f, 2.0f), new EntityLocation(-1.0f, -1.0f, 2.0f), collection);
		Assert.assertEquals(0, id);
		
		// Fall short from negative.
		id = RayCastHelpers.findFirstCollisionInCollection(ENV, new EntityLocation(-1.0f, -1.0f, -1.0f), new EntityLocation(-0.1f, -0.1f, -0.1f), collection);
		Assert.assertEquals(0, id);
		
		// Fall short from positive.
		id = RayCastHelpers.findFirstCollisionInCollection(ENV, new EntityLocation(2.0f, 2.0f, 2.0f), new EntityLocation(0.5f, 0.5f, 1.75f), collection);
		Assert.assertEquals(0, id);
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

	private static Entity _buildPlayer(int id, EntityLocation location)
	{
		return new Entity(id
			, false
			, location
			, new EntityLocation(0.0f, 0.0f, 0.0f)
			, (byte)0
			, (byte)0
			, null
			, null
			, 0
			, null
			, (byte)0
			, (byte)0
			, MiscConstants.MAX_BREATH
			, MutableEntity.TESTING_LOCATION
			, Entity.EMPTY_SHARED
			, Entity.EMPTY_LOCAL
		);
	}
}
