package com.jeffdisher.october.logic;

import java.util.function.Function;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.worldgen.CuboidGenerator;


public class TestSpatialHelpers
{
	private static final EntityVolume VOLUME = new EntityVolume(1.8f, 0.5f);
	private static Environment ENV;
	private static Block STONE;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		STONE = ENV.blocks.fromItem(ENV.items.getItemById("op.stone"));
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void canExistInAir()
	{
		// Just ask if they can exist when only air is present.
		EntityLocation location = new EntityLocation(0.0f, 0.0f, 0.0f);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.special.AIR);
		Function<AbsoluteLocation, BlockProxy> blockTypeReader = (AbsoluteLocation l) -> new BlockProxy(l.getBlockAddress(), cuboid);
		boolean canExist = SpatialHelpers.canExistInLocation(blockTypeReader, location, VOLUME);
		Assert.assertTrue(canExist);
	}

	@Test
	public void cantExistInStone()
	{
		// Just ask if they can exist when only stone is present.
		EntityLocation location = new EntityLocation(0.0f, 0.0f, 0.0f);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), STONE);
		Function<AbsoluteLocation, BlockProxy> blockTypeReader = (AbsoluteLocation l) -> new BlockProxy(l.getBlockAddress(), cuboid);
		boolean canExist = SpatialHelpers.canExistInLocation(blockTypeReader, location, VOLUME);
		Assert.assertFalse(canExist);
	}

	@Test
	public void cantStandInAir()
	{
		// Just ask if they can stand when only air is present (as this doesn't check the blocks they occupy).
		EntityLocation location = new EntityLocation(0.0f, 0.0f, 0.0f);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.special.AIR);
		Function<AbsoluteLocation, BlockProxy> blockTypeReader = (AbsoluteLocation l) -> new BlockProxy(l.getBlockAddress(), cuboid);
		boolean isStanding = SpatialHelpers.isStandingOnGround(blockTypeReader, location, VOLUME);
		Assert.assertFalse(isStanding);
	}

	@Test
	public void canStandInStone()
	{
		// Just ask if they can stand when only stone is present (as this doesn't check the blocks they occupy).
		EntityLocation location = new EntityLocation(0.0f, 0.0f, 0.0f);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), STONE);
		Function<AbsoluteLocation, BlockProxy> blockTypeReader = (AbsoluteLocation l) -> new BlockProxy(l.getBlockAddress(), cuboid);
		boolean isStanding = SpatialHelpers.isStandingOnGround(blockTypeReader, location, VOLUME);
		Assert.assertTrue(isStanding);
	}

	@Test
	public void isNotTouchingCeiling()
	{
		// Ask if they are touching the ceiling when clearly not.
		EntityLocation location = new EntityLocation(0.0f, 0.0f, 0.0f);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), STONE);
		Function<AbsoluteLocation, BlockProxy> blockTypeReader = (AbsoluteLocation l) -> new BlockProxy(l.getBlockAddress(), cuboid);
		boolean isTouching = SpatialHelpers.isTouchingCeiling(blockTypeReader, location, VOLUME);
		Assert.assertFalse(isTouching);
	}

	@Test
	public void isTouchingCeiling()
	{
		// Ask if they are touching the ceiling when they are right against it.
		EntityLocation location = new EntityLocation(0.0f, 0.0f, 0.2f);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), STONE);
		Function<AbsoluteLocation, BlockProxy> blockTypeReader = (AbsoluteLocation l) -> new BlockProxy(l.getBlockAddress(), cuboid);
		boolean isTouching = SpatialHelpers.isTouchingCeiling(blockTypeReader, location, VOLUME);
		Assert.assertTrue(isTouching);
	}

	@Test
	public void locationTouchingCeiling()
	{
		// Try to place the entity a little too high into the ceiling and observe the corrected location.
		EntityLocation location = new EntityLocation(0.0f, 0.0f, -1.5f);
		CuboidData air = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)-1), ENV.special.AIR);
		CuboidData stone = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), STONE);
		Function<AbsoluteLocation, BlockProxy> blockTypeReader = (AbsoluteLocation l) -> (l.z() >= 0)
				? new BlockProxy(l.getBlockAddress(), stone)
				: new BlockProxy(l.getBlockAddress(), air)
		;
		EntityLocation touching = SpatialHelpers.locationTouchingCeiling(blockTypeReader, location, VOLUME, -2.0f);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, -1.8f), touching);
	}

	@Test
	public void locationTouchingGround()
	{
		// Try to place the entity a little too low into the ground and observe the corrected location.
		EntityLocation location = new EntityLocation(0.0f, 0.0f, -0.5f);
		CuboidData air = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.special.AIR);
		CuboidData stone = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)-1), STONE);
		Function<AbsoluteLocation, BlockProxy> blockTypeReader = (AbsoluteLocation l) -> (l.z() >= 0)
				? new BlockProxy(l.getBlockAddress(), air)
				: new BlockProxy(l.getBlockAddress(), stone)
		;
		EntityLocation touching = SpatialHelpers.locationTouchingGround(blockTypeReader, location, VOLUME, 1.0f);
		// Note that we will need to treat -0.0 and 0.0 as the same in various places in the system.
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, -0.0f), touching);
	}

	@Test
	public void locationTouchingEast()
	{
		// Try to place the entity a little too for into the wall and observe the corrected location.
		EntityLocation location = new EntityLocation(-0.3f, 0.0f, 0.0f);
		CuboidData air = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)-1, (short)0, (short)0), ENV.special.AIR);
		CuboidData stone = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), STONE);
		Function<AbsoluteLocation, BlockProxy> blockTypeReader = (AbsoluteLocation l) -> (l.x() >= 0)
				? new BlockProxy(l.getBlockAddress(), stone)
				: new BlockProxy(l.getBlockAddress(), air)
		;
		EntityLocation touching = SpatialHelpers.locationTouchingEastWall(blockTypeReader, location, VOLUME, -1.0f);
		Assert.assertEquals(new EntityLocation(-0.5f, 0.0f, 0.0f), touching);
	}

	@Test
	public void locationTouchingWest()
	{
		// Try to place the entity a little too for into the wall and observe the corrected location.
		EntityLocation location = new EntityLocation(-0.3f, 0.0f, 0.0f);
		CuboidData air = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.special.AIR);
		CuboidData stone = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)-1, (short)0, (short)0), STONE);
		Function<AbsoluteLocation, BlockProxy> blockTypeReader = (AbsoluteLocation l) -> (l.x() >= 0)
				? new BlockProxy(l.getBlockAddress(), air)
				: new BlockProxy(l.getBlockAddress(), stone)
		;
		EntityLocation touching = SpatialHelpers.locationTouchingWestWall(blockTypeReader, location, VOLUME, 1.0f);
		// Note that we will need to treat -0.0 and 0.0 as the same in various places in the system.
		Assert.assertEquals(new EntityLocation(-0.0f, 0.0f, 0.0f), touching);
	}

	@Test
	public void locationTouchingNorth()
	{
		// Try to place the entity a little too for into the wall and observe the corrected location.
		EntityLocation location = new EntityLocation(0.0f, -0.3f, 0.0f);
		CuboidData air = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)-1, (short)0), ENV.special.AIR);
		CuboidData stone = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), STONE);
		Function<AbsoluteLocation, BlockProxy> blockTypeReader = (AbsoluteLocation l) -> (l.y() >= 0)
				? new BlockProxy(l.getBlockAddress(), stone)
				: new BlockProxy(l.getBlockAddress(), air)
		;
		EntityLocation touching = SpatialHelpers.locationTouchingNorthWall(blockTypeReader, location, VOLUME, -1.0f);
		Assert.assertEquals(new EntityLocation(0.0f, -0.5f, 0.0f), touching);
	}

	@Test
	public void locationTouchingSouth()
	{
		// Try to place the entity a little too for into the wall and observe the corrected location.
		EntityLocation location = new EntityLocation(0.0f, -0.3f, 0.0f);
		CuboidData air = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.special.AIR);
		CuboidData stone = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)-1, (short)0), STONE);
		Function<AbsoluteLocation, BlockProxy> blockTypeReader = (AbsoluteLocation l) -> (l.y() >= 0)
				? new BlockProxy(l.getBlockAddress(), air)
				: new BlockProxy(l.getBlockAddress(), stone)
		;
		EntityLocation touching = SpatialHelpers.locationTouchingSouthWall(blockTypeReader, location, VOLUME, 1.0f);
		// Note that we will need to treat -0.0 and 0.0 as the same in various places in the system.
		Assert.assertEquals(new EntityLocation(0.0f, -0.0f, 0.0f), touching);
	}

	@Test
	public void blockAlignment()
	{
		// Test the basics of block alignment.
		Assert.assertTrue(SpatialHelpers.isBlockAligned(0.00f));
		Assert.assertTrue(SpatialHelpers.isBlockAligned(-1.00f));
		Assert.assertFalse(SpatialHelpers.isBlockAligned(0.005f));
		Assert.assertFalse(SpatialHelpers.isBlockAligned(-0.005f));
	}
}
