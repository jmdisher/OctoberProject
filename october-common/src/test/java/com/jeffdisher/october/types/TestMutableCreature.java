package com.jeffdisher.october.types;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.aspects.CreatureRegistry;
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
				, CreatureRegistry.COW
				, new EntityLocation(0.0f, 0.0f, 0.0f)
				, new EntityLocation(0.0f, 0.0f, 0.0f)
				, (byte)0
				, (byte)0
				, (byte)50
				, EntityConstants.MAX_BREATH
				
				, null
				, 0L
				, 0L
				, CreatureEntity.NO_TARGET_ENTITY_ID
				, null
				, CowStateMachine.encodeExtendedData(new CowStateMachine.Test_ExtendedData(false, null))
		);
		
		MutableCreature mutable = MutableCreature.existing(middle);
		mutable.setHealth((byte)20);
		CreatureEntity output = mutable.freeze();
		
		Assert.assertNull(CowStateMachine.decodeExtendedData(output.extendedData()));
	}


	private static CreatureEntity _buildTestEntity()
	{
		return CreatureEntity.create(-1
				, CreatureRegistry.COW
				, new EntityLocation(0.0f, 0.0f, 0.0f)
				, (byte)50
		);
	}
}
