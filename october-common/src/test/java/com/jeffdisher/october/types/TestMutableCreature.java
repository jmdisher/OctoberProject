package com.jeffdisher.october.types;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.creatures.CowStateMachine;


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
		CreatureEntity middle = new CreatureEntity(-1
				, EntityType.COW
				, new EntityLocation(0.0f, 0.0f, 0.0f)
				, 0.0f
				, (byte)50
				, 0L
				, List.of()
				, CowStateMachine.test_packageMovementPlan(List.of())
		);
		Assert.assertNotNull(CowStateMachine.test_unwrapMovementPlan(middle.extendedData()));
		Assert.assertNotNull(middle.stepsToNextMove());
		
		MutableCreature mutable = MutableCreature.existing(middle);
		mutable.setHealth((byte)20);
		CreatureEntity output = mutable.freeze();
		
		Assert.assertNull(CowStateMachine.test_unwrapMovementPlan(output.extendedData()));
		Assert.assertNull(output.stepsToNextMove());
	}


	private static CreatureEntity _buildTestEntity()
	{
		return CreatureEntity.create(-1
				, EntityType.COW
				, new EntityLocation(0.0f, 0.0f, 0.0f)
				, (byte)50
		);
	}
}
