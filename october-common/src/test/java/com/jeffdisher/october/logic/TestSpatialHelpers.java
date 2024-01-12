package com.jeffdisher.october.logic;

import java.util.function.Function;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.worldgen.CuboidGenerator;


public class TestSpatialHelpers
{
	private static final EntityVolume VOLUME = new EntityVolume(1.8f, 0.5f);

	@Test
	public void canExistInAir()
	{
		// Just ask if they can exist when only air is present.
		EntityLocation location = new EntityLocation(0.0f, 0.0f, 0.0f);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ItemRegistry.AIR);
		Function<AbsoluteLocation, BlockProxy> blockTypeReader = (AbsoluteLocation l) -> new BlockProxy(l.getBlockAddress(), cuboid);
		boolean canExist = SpatialHelpers.canExistInLocation(blockTypeReader, location, VOLUME);
		Assert.assertTrue(canExist);
	}

	@Test
	public void cantExistInStone()
	{
		// Just ask if they can exist when only stone is present.
		EntityLocation location = new EntityLocation(0.0f, 0.0f, 0.0f);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ItemRegistry.STONE);
		Function<AbsoluteLocation, BlockProxy> blockTypeReader = (AbsoluteLocation l) -> new BlockProxy(l.getBlockAddress(), cuboid);
		boolean canExist = SpatialHelpers.canExistInLocation(blockTypeReader, location, VOLUME);
		Assert.assertFalse(canExist);
	}

	@Test
	public void cantStandInAir()
	{
		// Just ask if they can stand when only air is present (as this doesn't check the blocks they occupy).
		EntityLocation location = new EntityLocation(0.0f, 0.0f, 0.0f);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ItemRegistry.AIR);
		Function<AbsoluteLocation, BlockProxy> blockTypeReader = (AbsoluteLocation l) -> new BlockProxy(l.getBlockAddress(), cuboid);
		boolean isStanding = SpatialHelpers.isStandingOnGround(blockTypeReader, location, VOLUME);
		Assert.assertFalse(isStanding);
	}

	@Test
	public void canStandInStone()
	{
		// Just ask if they can stand when only stone is present (as this doesn't check the blocks they occupy).
		EntityLocation location = new EntityLocation(0.0f, 0.0f, 0.0f);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ItemRegistry.STONE);
		Function<AbsoluteLocation, BlockProxy> blockTypeReader = (AbsoluteLocation l) -> new BlockProxy(l.getBlockAddress(), cuboid);
		boolean isStanding = SpatialHelpers.isStandingOnGround(blockTypeReader, location, VOLUME);
		Assert.assertTrue(isStanding);
	}

	@Test
	public void isNotTouchingCeiling()
	{
		// Ask if they are touching the ceiling when clearly not.
		EntityLocation location = new EntityLocation(0.0f, 0.0f, 0.0f);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ItemRegistry.STONE);
		Function<AbsoluteLocation, BlockProxy> blockTypeReader = (AbsoluteLocation l) -> new BlockProxy(l.getBlockAddress(), cuboid);
		boolean isTouching = SpatialHelpers.isTouchingCeiling(blockTypeReader, location, VOLUME);
		Assert.assertFalse(isTouching);
	}

	@Test
	public void isTouchingCeiling()
	{
		// Ask if they are touching the ceiling when they are right against it.
		EntityLocation location = new EntityLocation(0.0f, 0.0f, 0.2f);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ItemRegistry.STONE);
		Function<AbsoluteLocation, BlockProxy> blockTypeReader = (AbsoluteLocation l) -> new BlockProxy(l.getBlockAddress(), cuboid);
		boolean isTouching = SpatialHelpers.isTouchingCeiling(blockTypeReader, location, VOLUME);
		Assert.assertTrue(isTouching);
	}
}
