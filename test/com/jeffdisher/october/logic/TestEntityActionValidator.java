package com.jeffdisher.october.logic;

import java.util.function.Function;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;


public class TestEntityActionValidator
{
	@Test
	public void flatPlaneGood()
	{
		// The default location is 0,0,0 so say that the floor is -1.
		int floor = -1;
		Function<AbsoluteLocation, Short> blockTypeReader = (AbsoluteLocation l) -> (floor == l.z()) ? BlockAspect.STONE : BlockAspect.AIR;
		EntityActionValidator validator = new EntityActionValidator(blockTypeReader);
		Entity start = validator.buildNewlyJoinedEntity(1);
		
		// We can walk .5 blocks per tick (by default), so test that - since the algorithm only checks to the base of blocks, we can move almost another half block.
		EntityLocation loc = start.location;
		EntityLocation target = new EntityLocation(loc.x() + 5.49f, loc.y(), loc.z());
		Entity updated = validator.moveEntity(start, target, 10);
		Assert.assertNotNull(updated);
		Assert.assertEquals(target, updated.location);
	}

	// Broken until partial-blocks are properly supported - this test was depending on a bogus rounding behaviour.
	@Ignore
	@Test
	public void flatPlaneBad()
	{
		// The default location is 0,0,0 so say that the floor is -1.
		int floor = -1;
		Function<AbsoluteLocation, Short> blockTypeReader = (AbsoluteLocation l) -> (floor == l.z()) ? BlockAspect.STONE : BlockAspect.AIR;
		EntityActionValidator validator = new EntityActionValidator(blockTypeReader);
		Entity start = validator.buildNewlyJoinedEntity(1);
		
		// We can walk .5 blocks per tick (by default), so test that - walking half a block will fail.
		EntityLocation loc = start.location;
		Entity updated = validator.moveEntity(start, new EntityLocation(loc.x() + 5.5f, loc.y(), loc.z()), 10);
		Assert.assertNull(updated);
	}
}
