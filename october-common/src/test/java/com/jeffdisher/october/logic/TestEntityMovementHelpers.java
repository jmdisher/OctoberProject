package com.jeffdisher.october.logic;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestEntityMovementHelpers
{
	private static Environment ENV;
	private static Block STONE;
	private static Block WATER_SOURCE;
	private static Block LADDER;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		STONE = ENV.blocks.fromItem(ENV.items.getItemById("op.stone"));
		WATER_SOURCE = ENV.blocks.fromItem(ENV.items.getItemById("op.water_source"));
		LADDER = ENV.blocks.fromItem(ENV.items.getItemById("op.ladder"));
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void boxTopRight()
	{
		EntityLocation location = new EntityLocation(1.3f, 1.4f, 1.5f);
		EntityVolume volume = new EntityVolume(1.2f, 1.2f);
		EntityLocation velocity = new EntityLocation(1.0f, 1.0f, 1.0f);
		EntityMovementHelpers.interactiveEntityMove(location, volume, velocity, new EntityMovementHelpers.IInteractiveHelper() {
			@Override
			public void setLocationAndCancelVelocity(EntityLocation finalLocation, boolean cancelX, boolean cancelY, boolean cancelZ)
			{
				Assert.assertEquals(new EntityLocation(1.79f, 1.79f, 1.79f), finalLocation);
				Assert.assertTrue(cancelX);
				Assert.assertTrue(cancelY);
				Assert.assertTrue(cancelZ);
			}
			@Override
			public float getViscosityForBlockAtLocation(AbsoluteLocation location, boolean fromAbove)
			{
				int x = location.x();
				int y = location.y();
				int z = location.z();
				return (((1 == x) || (2 == x))
						&& ((1 == y) || (2 == y))
						&& ((1 == z) || (2 == z))
				) ? 0.0f : 1.0f;
			}
		});
	}

	@Test
	public void boxBottomLeft()
	{
		EntityLocation location = new EntityLocation(1.3f, 1.4f, 1.5f);
		EntityVolume volume = new EntityVolume(1.2f, 1.2f);
		EntityLocation velocity = new EntityLocation(-1.0f, -1.0f, -1.0f);
		EntityMovementHelpers.interactiveEntityMove(location, volume, velocity, new EntityMovementHelpers.IInteractiveHelper() {
			@Override
			public void setLocationAndCancelVelocity(EntityLocation finalLocation, boolean cancelX, boolean cancelY, boolean cancelZ)
			{
				Assert.assertEquals(new EntityLocation(1.0f, 1.0f, 1.0f), finalLocation);
				Assert.assertTrue(cancelX);
				Assert.assertTrue(cancelY);
				Assert.assertTrue(cancelZ);
			}
			@Override
			public float getViscosityForBlockAtLocation(AbsoluteLocation location, boolean fromAbove)
			{
				int x = location.x();
				int y = location.y();
				int z = location.z();
				return (((1 == x) || (2 == x))
						&& ((1 == y) || (2 == y))
						&& ((1 == z) || (2 == z))
				) ? 0.0f : 1.0f;
			}
		});
	}

	@Test
	public void emptyFalling()
	{
		EntityLocation location = new EntityLocation(0.0f, 0.0f, 0.0f);
		EntityVolume volume = new EntityVolume(0.8f, 1.7f);
		EntityLocation velocity = new EntityLocation(-1.0f, 2.0f, -3.0f);
		EntityMovementHelpers.interactiveEntityMove(location, volume, velocity, new EntityMovementHelpers.IInteractiveHelper() {
			@Override
			public void setLocationAndCancelVelocity(EntityLocation finalLocation, boolean cancelX, boolean cancelY, boolean cancelZ)
			{
				Assert.assertEquals(velocity, finalLocation);
				Assert.assertFalse(cancelX);
				Assert.assertFalse(cancelY);
				Assert.assertFalse(cancelZ);
			}
			@Override
			public float getViscosityForBlockAtLocation(AbsoluteLocation location, boolean fromAbove)
			{
				return 0.0f;
			}
		});
	}

	@Test
	public void viscosity()
	{
		// Check the viscosity of a few different cuboids.
		CuboidData topCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 1), ENV.special.AIR);
		CuboidData middleCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), WATER_SOURCE);
		CuboidData bottomCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), STONE);
		Map<CuboidAddress, CuboidData> map = Map.of(topCuboid.getCuboidAddress(), topCuboid
			, middleCuboid.getCuboidAddress(), middleCuboid
			, bottomCuboid.getCuboidAddress(), bottomCuboid
		);
		Function<AbsoluteLocation, BlockProxy> lookup = (AbsoluteLocation location) -> {
			CuboidData cuboid = map.get(location.getCuboidAddress());
			return (null != cuboid)
				? new BlockProxy(location.getBlockAddress(), cuboid)
				: null
			;
		};
		
		EntityVolume volume = new EntityVolume(2.2f, 1.7f);
		EntityLocation airLocation = new EntityLocation(0.0f, 0.0f, 40.0f);
		EntityLocation waterLocation = new EntityLocation(0.0f, 0.0f, 20.0f);
		EntityLocation stoneLocation = new EntityLocation(0.0f, 0.0f, -20.0f);
		EntityLocation airWaterLocation = new EntityLocation(0.0f, 0.0f, 31.0f);
		EntityLocation waterStoneLocation = new EntityLocation(0.0f, 0.0f, -1.0f);
		EntityLocation airEdgeLocation = new EntityLocation(0.0f, 31.0f, 40.0f);
		
		Assert.assertEquals(0.0f, EntityMovementHelpers.maxViscosityInEntityBlocks(airLocation, volume, lookup), 0.01f);
		Assert.assertEquals(0.5f, EntityMovementHelpers.maxViscosityInEntityBlocks(waterLocation, volume, lookup), 0.01f);
		Assert.assertEquals(1.0f, EntityMovementHelpers.maxViscosityInEntityBlocks(stoneLocation, volume, lookup), 0.01f);
		Assert.assertEquals(0.5f, EntityMovementHelpers.maxViscosityInEntityBlocks(airWaterLocation, volume, lookup), 0.01f);
		Assert.assertEquals(1.0f, EntityMovementHelpers.maxViscosityInEntityBlocks(waterStoneLocation, volume, lookup), 0.01f);
		Assert.assertEquals(1.0f, EntityMovementHelpers.maxViscosityInEntityBlocks(airEdgeLocation, volume, lookup), 0.01f);
	}

	@Test
	public void stuckInBlock()
	{
		EntityLocation location = new EntityLocation(0.0f, 0.0f, 0.0f);
		EntityVolume volume = new EntityVolume(0.8f, 1.7f);
		EntityLocation velocity = new EntityLocation(-1.0f, 2.0f, -3.0f);
		EntityMovementHelpers.interactiveEntityMove(location, volume, velocity, new EntityMovementHelpers.IInteractiveHelper() {
			@Override
			public void setLocationAndCancelVelocity(EntityLocation finalLocation, boolean cancelX, boolean cancelY, boolean cancelZ)
			{
				Assert.assertEquals(location, finalLocation);
				Assert.assertTrue(cancelX);
				Assert.assertTrue(cancelY);
				Assert.assertTrue(cancelZ);
			}
			@Override
			public float getViscosityForBlockAtLocation(AbsoluteLocation location, boolean fromAbove)
			{
				return 1.0f;
			}
		});
	}

	@Test
	public void walkOnLadders()
	{
		EntityLocation location = new EntityLocation(0.1f, 0.2f, 0.3f);
		EntityVolume volume = new EntityVolume(0.8f, 1.7f);
		EntityLocation velocity = new EntityLocation(-1.0f, 2.0f, -3.0f);
		EntityMovementHelpers.interactiveEntityMove(location, volume, velocity, new EntityMovementHelpers.IInteractiveHelper() {
			@Override
			public void setLocationAndCancelVelocity(EntityLocation finalLocation, boolean cancelX, boolean cancelY, boolean cancelZ)
			{
				Assert.assertEquals(new EntityLocation(-0.9f, 2.2f, 0.3f), finalLocation);
				Assert.assertFalse(cancelX);
				Assert.assertFalse(cancelY);
				Assert.assertTrue(cancelZ);
			}
			@Override
			public float getViscosityForBlockAtLocation(AbsoluteLocation location, boolean fromAbove)
			{
				return fromAbove ? 1.0f : 0.0f;
			}
		});
	}

	@Test
	public void isLadder()
	{
		// Check the intersection rules with a ladder.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(5, 5, 5), LADDER.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(5, 6, 5), STONE.item().number());
		Predicate<AbsoluteLocation> lookup = (AbsoluteLocation location) -> {
			Block block = new BlockProxy(location.getBlockAddress(), cuboid).getBlock();
			return ENV.blocks.isLadderType(block);
		};
		
		EntityVolume volume = new EntityVolume(2.2f, 1.7f);
		EntityLocation airLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		EntityLocation ladderAirLocation = new EntityLocation(5.0f, 3.5f, 4.5f);
		EntityLocation ladderStoneLocation = new EntityLocation(5.0f, 5.0f, 4.5f);
		
		Assert.assertNull(EntityMovementHelpers.checkTypeIntersection(airLocation, volume, lookup));
		Assert.assertNotNull(EntityMovementHelpers.checkTypeIntersection(ladderAirLocation, volume, lookup));
		Assert.assertNotNull(EntityMovementHelpers.checkTypeIntersection(ladderStoneLocation, volume, lookup));
	}

	@Test
	public void commonMoveWalk()
	{
		// A basic test of the commonMovementIdiom - walking on the ground.
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidData stoneCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), STONE);
		Function<AbsoluteLocation, BlockProxy> previousBlockLookUp = (AbsoluteLocation location) -> {
			return location.getCuboidAddress().equals(airCuboid.getCuboidAddress())
				? new BlockProxy(location.getBlockAddress(), airCuboid)
				: new BlockProxy(location.getBlockAddress(), stoneCuboid)
			;
		};
		
		EntityLocation startLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		EntityLocation startVelocity = new EntityLocation(0.0f, 0.0f, 0.0f);
		EntityVolume volume = new EntityVolume(2.2f, 1.7f);
		float activeXMovement = 0.4f;
		float activeYMovement = 0.0f;
		float maxVelocityPerSecond = 4.0f;
		float seconds = 0.1f;
		
		EntityMovementHelpers.HighLevelMovementResult result = EntityMovementHelpers.commonMovementIdiom(previousBlockLookUp
			, startLocation
			, startVelocity
			, volume
			, activeXMovement
			, activeYMovement
			, maxVelocityPerSecond
			, seconds
		);
		
		Assert.assertEquals(new EntityLocation(0.4f, 0.0f, 0.0f), result.location());
		Assert.assertEquals(0.0f, result.vX(), 0.01f);
		Assert.assertEquals(0.0f, result.vY(), 0.01f);
		Assert.assertEquals(0.0f, result.vZ(), 0.01f);
	}

	@Test
	public void commonMoveFall()
	{
		// A basic test of the commonMovementIdiom - falling through air.
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		Function<AbsoluteLocation, BlockProxy> previousBlockLookUp = (AbsoluteLocation location) -> {
			return new BlockProxy(location.getBlockAddress(), airCuboid);
		};
		
		EntityLocation startLocation = new EntityLocation(10.0f, 10.0f, 10.0f);
		EntityLocation startVelocity = new EntityLocation(0.0f, 0.0f, 0.0f);
		EntityVolume volume = new EntityVolume(2.2f, 1.7f);
		float activeXMovement = 0.0f;
		float activeYMovement = 0.0f;
		float maxVelocityPerSecond = 4.0f;
		float seconds = 0.1f;
		
		EntityMovementHelpers.HighLevelMovementResult result = EntityMovementHelpers.commonMovementIdiom(previousBlockLookUp
			, startLocation
			, startVelocity
			, volume
			, activeXMovement
			, activeYMovement
			, maxVelocityPerSecond
			, seconds
		);
		
		Assert.assertEquals(new EntityLocation(10.0f, 10.0f, 9.9f), result.location());
		Assert.assertEquals(0.0f, result.vX(), 0.01f);
		Assert.assertEquals(0.0f, result.vY(), 0.01f);
		Assert.assertEquals(-0.98f, result.vZ(), 0.01f);
	}
}
