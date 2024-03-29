package com.jeffdisher.october.logic;

import java.util.function.Function;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;


public class TestEntityActionValidator
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
	public void flatPlaneGood()
	{
		// The default location is 0,0,0 so say that the floor is -1.
		int floor = -1;
		Function<AbsoluteLocation, Short> blockTypeReader = (AbsoluteLocation l) -> (floor == l.z()) ? ENV.items.STONE.number() : ENV.items.AIR.number();
		Entity start = EntityActionValidator.buildDefaultEntity(1);
		
		// We can walk .5 blocks per tick (by default), so test that.
		EntityLocation loc = start.location();
		EntityLocation target = new EntityLocation(loc.x() + 5.0f, loc.y(), loc.z());
		Entity updated = EntityActionValidator.moveEntity(blockTypeReader, start, target, 10);
		Assert.assertNotNull(updated);
		Assert.assertEquals(target, updated.location());
	}

	@Test
	public void flatPlaneBad()
	{
		// The default location is 0,0,0 so say that the floor is -1.
		int floor = -1;
		Function<AbsoluteLocation, Short> blockTypeReader = (AbsoluteLocation l) -> (floor == l.z()) ? ENV.items.STONE.number() : ENV.items.AIR.number();
		Entity start = EntityActionValidator.buildDefaultEntity(1);
		
		// We can walk .5 blocks per tick (by default), so test that - walking half a block will fail.
		EntityLocation loc = start.location();
		Entity updated = EntityActionValidator.moveEntity(blockTypeReader, start, new EntityLocation(loc.x() + 5.5f, loc.y(), loc.z()), 10);
		Assert.assertNull(updated);
	}

	@Test
	public void flatPlaneTipToe()
	{
		// The default location is 0,0,0 so say that the floor is -1.
		int floor = -1;
		Function<AbsoluteLocation, Short> blockTypeReader = (AbsoluteLocation l) -> (floor == l.z()) ? ENV.items.STONE.number() : ENV.items.AIR.number();
		Entity start = EntityActionValidator.buildDefaultEntity(1);
		
		// We can walk .5 blocks per tick (by default), show that we can tip-toe around.
		for (int i = 0; i < 10; ++i)
		{
			EntityLocation loc = start.location();
			EntityLocation target = new EntityLocation(loc.x() + 0.5f, loc.y(), loc.z());
			Entity updated = EntityActionValidator.moveEntity(blockTypeReader, start, target, 1);
			Assert.assertNotNull(updated);
			Assert.assertEquals(target, updated.location());
			start = updated;
		}
	}
}
