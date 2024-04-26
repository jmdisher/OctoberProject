package com.jeffdisher.october.aspects;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.Item;


public class TestMiscAspects
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
	public void armourRegistry() throws Throwable
	{
		Item helmet = ENV.items.getItemById("op.iron_helmet");
		Item dirt = ENV.items.getItemById("op.dirt");
		Item bucket = ENV.items.getItemById("op.bucket");
		
		Assert.assertEquals(BodyPart.HEAD, ENV.armour.getBodyPart(helmet));
		Assert.assertNull(ENV.armour.getBodyPart(dirt));
		Assert.assertNull(ENV.armour.getBodyPart(bucket));
		
		Assert.assertEquals(10, ENV.armour.getDamageReduction(helmet));
	}
}
