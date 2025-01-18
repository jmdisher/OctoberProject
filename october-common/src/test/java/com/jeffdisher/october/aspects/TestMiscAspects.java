package com.jeffdisher.october.aspects;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.types.Block;
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

	@Test
	public void dropProbabilities() throws Throwable
	{
		Block leaf = ENV.blocks.fromItem(ENV.items.getItemById("op.leaf"));
		Block matureWheat = ENV.blocks.fromItem(ENV.items.getItemById("op.wheat_mature"));
		
		Item[] leafDrop0 = ENV.blocks.droppedBlocksOnBreak(leaf, 0);
		Item[] leafDrop99 = ENV.blocks.droppedBlocksOnBreak(leaf, 99);
		Item[] wheatDrop0 = ENV.blocks.droppedBlocksOnBreak(matureWheat, 0);
		Item[] wheatDrop99 = ENV.blocks.droppedBlocksOnBreak(matureWheat, 99);
		
		Assert.assertEquals(3, leafDrop0.length);
		Assert.assertEquals(0, leafDrop99.length);
		Assert.assertEquals(4, wheatDrop0.length);
		Assert.assertEquals(3, wheatDrop99.length);
	}
}
