package com.jeffdisher.october.aspects;

import java.util.List;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockVolume;
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

	@Test
	public void blockDamage() throws Throwable
	{
		Block air = ENV.special.AIR;
		Block waterSource = ENV.blocks.fromItem(ENV.items.getItemById("op.water_source"));
		Block lavaWeak = ENV.blocks.fromItem(ENV.items.getItemById("op.lava_weak"));
		
		Assert.assertEquals(0, ENV.blocks.getBlockDamage(air));
		Assert.assertEquals(0, ENV.blocks.getBlockDamage(waterSource));
		Assert.assertEquals(10, ENV.blocks.getBlockDamage(lavaWeak));
	}

	@Test
	public void orientationRotate() throws Throwable
	{
		AbsoluteLocation start = new AbsoluteLocation(1, 2, 3);
		Assert.assertEquals(start, OrientationAspect.Direction.NORTH.rotateAboutZ(start));
		Assert.assertEquals(new AbsoluteLocation(-2, 1, 3), OrientationAspect.Direction.WEST.rotateAboutZ(start));
		Assert.assertEquals(new AbsoluteLocation(-1, -2, 3), OrientationAspect.Direction.SOUTH.rotateAboutZ(start));
		Assert.assertEquals(new AbsoluteLocation(2, -1, 3), OrientationAspect.Direction.EAST.rotateAboutZ(start));
		Assert.assertArrayEquals(new float[] { -2.4f, -1.1f }, OrientationAspect.Direction.EAST.rotateXYTupleAboutZ(new float[] { 1.1f, -2.4f }), 0.01f);
	}

	@Test
	public void multiBlock() throws Throwable
	{
		Block doorClosed = ENV.blocks.fromItem(ENV.items.getItemById("op.double_door_base"));
		AbsoluteLocation start = new AbsoluteLocation(0, 0, 0);
		List<AbsoluteLocation> extensions = ENV.multiBlocks.getExtensions(doorClosed, start, OrientationAspect.Direction.NORTH);
		Assert.assertEquals(3, extensions.size());
		Assert.assertEquals(new AbsoluteLocation(0, 0, 1), extensions.get(0));
		Assert.assertEquals(new AbsoluteLocation(1, 0, 1), extensions.get(1));
		Assert.assertEquals(new AbsoluteLocation(1, 0, 0), extensions.get(2));
		Assert.assertEquals(new BlockVolume(2, 1, 2), ENV.multiBlocks.getDefaultVolume(doorClosed));
	}
}
