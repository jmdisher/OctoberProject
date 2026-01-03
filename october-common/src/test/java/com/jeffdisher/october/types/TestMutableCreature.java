package com.jeffdisher.october.types;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.CreatureExtendedData;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.MiscConstants;


public class TestMutableCreature
{
	private static Environment ENV;
	private static EntityType COW;
	@BeforeClass
	public static void setup() throws Throwable
	{
		ENV = Environment.createSharedInstance();
		COW = ENV.creatures.getTypeById("op.cow");
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

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
				, COW
				, new EntityLocation(0.0f, 0.0f, 0.0f)
				, new EntityLocation(0.0f, 0.0f, 0.0f)
				, (byte)0
				, (byte)0
				, (byte)50
				, MiscConstants.MAX_BREATH
				, COW.extendedCodec().buildDefault(0L)
				
				, CreatureEntity.createEmptyEphemeral(0L)
		);
		
		MutableCreature mutable = MutableCreature.existing(middle);
		mutable.setHealth((byte)20);
		CreatureEntity output = mutable.freeze();
		Assert.assertNotEquals(middle, output);
	}

	@Test
	public void growToAdult() throws Throwable
	{
		EntityType cowBaby = ENV.creatures.getTypeById("op.cow_baby");
		CreatureEntity input = CreatureEntity.create(-1
			, cowBaby
			, new EntityLocation(0.0f, 0.0f, 0.0f)
			, 0L
		);
		Assert.assertEquals(cowBaby.maxHealth(), input.health());
		Assert.assertTrue(input.extendedData() instanceof CreatureExtendedData.BabyData);
		
		MutableCreature mutable = MutableCreature.existing(input);
		mutable.changeEntityType(COW, 0L);
		CreatureEntity output = mutable.freeze();
		
		Assert.assertEquals(COW, output.type());
		Assert.assertEquals(COW.maxHealth(), output.health());
		Assert.assertTrue(output.extendedData() instanceof CreatureExtendedData.LivestockData);
	}


	private static CreatureEntity _buildTestEntity()
	{
		return CreatureEntity.create(-1
				, COW
				, new EntityLocation(0.0f, 0.0f, 0.0f)
				, 0L
		);
	}
}
