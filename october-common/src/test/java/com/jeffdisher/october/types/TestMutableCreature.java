package com.jeffdisher.october.types;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;


public class TestMutableCreature
{
	@Test
	public void noChange() throws Throwable
	{
		CreatureEntity input = _buildTestEntity();
		MutableCreature mutable = MutableCreature.existing(input);
		CreatureEntity output = mutable.freeze();
		
		Assert.assertTrue(input == output);
	}

	@Test
	public void healthClearsPlan()
	{
		CreatureEntity input = _buildTestEntity();
		MutableCreature mutable = MutableCreature.existing(input);
		mutable.newMovementPlan = List.of();
		mutable.newStepsToNextMove = List.of();
		CreatureEntity middle = mutable.freeze();
		Assert.assertNotNull(middle.movementPlan());
		Assert.assertNotNull(middle.stepsToNextMove());
		
		mutable = MutableCreature.existing(middle);
		mutable.setHealth((byte)20);
		CreatureEntity output = mutable.freeze();
		
		Assert.assertNull(output.movementPlan());
		Assert.assertNull(output.stepsToNextMove());
	}


	private static CreatureEntity _buildTestEntity()
	{
		return new CreatureEntity(-1
				, EntityType.COW
				, new EntityLocation(0.0f, 0.0f, 0.0f)
				, 0.0f
				, (byte)50
				, 0L
				, null
				, null
				, null
		);
	}
}
