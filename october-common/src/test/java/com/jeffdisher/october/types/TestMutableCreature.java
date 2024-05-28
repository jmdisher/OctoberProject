package com.jeffdisher.october.types;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.creatures.CreatureLogic;


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
				, CreatureLogic.test_packageMovementPlanCow(List.of())
		);
		Assert.assertNotNull(CreatureLogic.test_unwrapMovementPlanCow(middle));
		Assert.assertNotNull(middle.stepsToNextMove());
		
		MutableCreature mutable = MutableCreature.existing(middle);
		mutable.setHealth((byte)20);
		CreatureEntity output = mutable.freeze();
		
		Assert.assertNull(CreatureLogic.test_unwrapMovementPlanCow(output));
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
