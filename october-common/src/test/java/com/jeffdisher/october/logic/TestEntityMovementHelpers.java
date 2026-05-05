package com.jeffdisher.october.logic;

import java.util.List;
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
import com.jeffdisher.october.types.ContextBuilder;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.CuboidGenerator;
import com.jeffdisher.october.utils.Encoding;


public class TestEntityMovementHelpers
{
	private static Environment ENV;
	private static Block STONE;
	private static Block WATER_SOURCE;
	private static Block LADDER;
	@BeforeClass
	public static void setup() throws Throwable
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
			public boolean isSolid(EntityLocation base, EntityVolume volume, boolean fromAbove)
			{
				List<AbsoluteLocation> locations = VolumeIterator.getAllInVolume(base, volume);
				
				boolean isSolid = false;
				for (AbsoluteLocation location : locations)
				{
					int x = location.x();
					int y = location.y();
					int z = location.z();
					if (((1 == x) || (2 == x))
						&& ((1 == y) || (2 == y))
						&& ((1 == z) || (2 == z))
					)
					{
						// This is our non-solid block.
					}
					else
					{
						isSolid = true;
						break;
					}
				}
				return isSolid;
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
			public boolean isSolid(EntityLocation base, EntityVolume volume, boolean fromAbove)
			{
				List<AbsoluteLocation> locations = VolumeIterator.getAllInVolume(base, volume);
				
				boolean isSolid = false;
				for (AbsoluteLocation location : locations)
				{
					int x = location.x();
					int y = location.y();
					int z = location.z();
					if (((1 == x) || (2 == x))
						&& ((1 == y) || (2 == y))
						&& ((1 == z) || (2 == z))
					)
					{
						// This is our non-solid block.
					}
					else
					{
						isSolid = true;
						break;
					}
				}
				return isSolid;
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
			public boolean isSolid(EntityLocation base, EntityVolume volume, boolean fromAbove)
			{
				return false;
			}
		});
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
			public boolean isSolid(EntityLocation base, EntityVolume volume, boolean fromAbove)
			{
				return true;
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
			public boolean isSolid(EntityLocation base, EntityVolume volume, boolean fromAbove)
			{
				// Solid if entering the ladder from above.
				return fromAbove;
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
			Block block = BlockProxy.load(location.getBlockAddress(), cuboid).getBlock();
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
		TickProcessingContext.IBlockFetcher previousBlockLookUp = ContextBuilder.buildFetcher((AbsoluteLocation location) -> {
			return location.getCuboidAddress().equals(airCuboid.getCuboidAddress())
				? BlockProxy.load(location.getBlockAddress(), airCuboid)
				: BlockProxy.load(location.getBlockAddress(), stoneCuboid)
			;
		});
		
		EntityLocation startLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		EntityLocation startVelocity = new EntityLocation(0.0f, 0.0f, 0.0f);
		EntityVolume volume = new EntityVolume(2.2f, 1.7f);
		float activeXMovement = 0.4f;
		float activeYMovement = 0.0f;
		float maxVelocityPerSecond = 4.0f;
		float seconds = 0.1f;
		
		ViscosityReader reader = new ViscosityReader(ENV, previousBlockLookUp);
		EntityMovementHelpers.HighLevelMovementResult result = EntityMovementHelpers.commonMovementIdiom(reader
			, startLocation
			, startVelocity
			, volume
			, activeXMovement
			, activeYMovement
			, maxVelocityPerSecond
			, seconds
		);
		
		Assert.assertEquals(new EntityLocation(0.4f, 0.0f, 0.0f), result.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), result.velocity());
		Assert.assertTrue(result.isOnGround());
	}

	@Test
	public void commonMoveFall()
	{
		// A basic test of the commonMovementIdiom - falling through air.
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		TickProcessingContext.IBlockFetcher previousBlockLookUp = ContextBuilder.buildFetcher((AbsoluteLocation location) -> {
			return BlockProxy.load(location.getBlockAddress(), airCuboid);
		});
		
		EntityLocation startLocation = new EntityLocation(10.0f, 10.0f, 10.0f);
		EntityLocation startVelocity = new EntityLocation(0.0f, 0.0f, 0.0f);
		EntityVolume volume = new EntityVolume(2.2f, 1.7f);
		float activeXMovement = 0.0f;
		float activeYMovement = 0.0f;
		float maxVelocityPerSecond = 4.0f;
		float seconds = 0.1f;
		
		ViscosityReader reader = new ViscosityReader(ENV, previousBlockLookUp);
		EntityMovementHelpers.HighLevelMovementResult result = EntityMovementHelpers.commonMovementIdiom(reader
			, startLocation
			, startVelocity
			, volume
			, activeXMovement
			, activeYMovement
			, maxVelocityPerSecond
			, seconds
		);
		
		Assert.assertEquals(new EntityLocation(10.0f, 10.0f, 9.9f), result.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, -0.98f), result.velocity());
		Assert.assertFalse(result.isOnGround());
	}

	@Test
	public void bigStuckInBlock()
	{
		EntityLocation location = new EntityLocation(0.0f, 0.0f, 0.0f);
		EntityVolume volume = new EntityVolume(2.1f, 2.2f);
		EntityLocation velocity = new EntityLocation(0.0f, 2.0f, 0.0f);
		AbsoluteLocation block = new AbsoluteLocation(1, 3, 1);
		EntityLocation expected = new EntityLocation(0.0f, 0.79f, 0.0f);
		EntityMovementHelpers.interactiveEntityMove(location, volume, velocity, new EntityMovementHelpers.IInteractiveHelper() {
			@Override
			public void setLocationAndCancelVelocity(EntityLocation finalLocation, boolean cancelX, boolean cancelY, boolean cancelZ)
			{
				Assert.assertEquals(expected, finalLocation);
				Assert.assertFalse(cancelX);
				Assert.assertTrue(cancelY);
				Assert.assertFalse(cancelZ);
			}
			@Override
			public boolean isSolid(EntityLocation base, EntityVolume volume, boolean fromAbove)
			{
				List<AbsoluteLocation> locations = VolumeIterator.getAllInVolume(base, volume);
				
				// We are solid if this volume contains the block.
				return locations.contains(block);
			}
		});
	}

	@Test
	public void moveByLongDistanceVector() throws Throwable
	{
		// Show that we move the full distance of the velocity vector when we don't hit anything.
		EntityLocation start = new EntityLocation(-1.2f, -2.3f, -3.4f);
		EntityLocation vector = new EntityLocation(7.8f, 6.7f, 5.6f);
		EntityVolume volume = ENV.creatures.PLAYER.volume();
		EntityLocation expected = new EntityLocation(start.x() + vector.x(), start.y() + vector.y(), start.z() + vector.z());
		EntityMovementHelpers.interactiveEntityMove(start, volume, vector, new EntityMovementHelpers.IInteractiveHelper() {
			@Override
			public void setLocationAndCancelVelocity(EntityLocation finalLocation, boolean cancelX, boolean cancelY, boolean cancelZ)
			{
				Assert.assertEquals(expected, finalLocation);
				Assert.assertFalse(cancelX);
				Assert.assertFalse(cancelY);
				Assert.assertFalse(cancelZ);
			}
			@Override
			public boolean isSolid(EntityLocation base, EntityVolume volume, boolean fromAbove)
			{
				return false;
			}
		});
	}

	@Test
	public void moveHitCeiling() throws Throwable
	{
		// Show that we coast along the ceiling after hitting it.
		EntityLocation start = new EntityLocation(-1.2f, -2.3f, -3.4f);
		EntityLocation vector = new EntityLocation(7.8f, 6.7f, 5.6f);
		EntityVolume volume = ENV.creatures.PLAYER.volume();
		EntityLocation expected = new EntityLocation(6.6f, 4.4f, -volume.height() - 0.01f);
		EntityMovementHelpers.interactiveEntityMove(start, volume, vector, new EntityMovementHelpers.IInteractiveHelper() {
			@Override
			public void setLocationAndCancelVelocity(EntityLocation finalLocation, boolean cancelX, boolean cancelY, boolean cancelZ)
			{
				Assert.assertEquals(expected, finalLocation);
				Assert.assertFalse(cancelX);
				Assert.assertFalse(cancelY);
				Assert.assertTrue(cancelZ);
			}
			@Override
			public boolean isSolid(EntityLocation base, EntityVolume volume, boolean fromAbove)
			{
				List<AbsoluteLocation> locations = VolumeIterator.getAllInVolume(base, volume);
				
				boolean isSolid = false;
				for (AbsoluteLocation location : locations)
				{
					if (0 == location.z())
					{
						isSolid = true;
						break;
					}
				}
				return isSolid;
			}
		});
	}

	@Test
	public void diagonalCoastingAtTerminal()
	{
		// Test how we coast diagonally through the air with commonMovementIdiom when at terminal velocity.  We expect
		// that we can't accelerate above terminal velocity and it should be the same, no matter the facing direction.
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		TickProcessingContext.IBlockFetcher previousBlockLookUp = ContextBuilder.buildFetcher((AbsoluteLocation location) -> {
			return BlockProxy.load(location.getBlockAddress(), airCuboid);
		});
		ViscosityReader reader = new ViscosityReader(ENV, previousBlockLookUp);
		EntityVolume volume = new EntityVolume(2.2f, 1.7f);
		float maxVelocityPerSecond = 4.0f;
		float seconds = 0.05f;
		
		EntityLocation location = new EntityLocation(10.0f, 10.0f, 10.0f);
		EntityLocation velocity = new EntityLocation(0.0f, 0.0f, 0.0f);
		
		EntityMovementHelpers.HighLevelMovementResult result = EntityMovementHelpers.commonMovementIdiom(reader
			, location
			, velocity
			, volume
			, 0.19f
			, 0.05f
			, maxVelocityPerSecond
			, seconds
		);
		location = result.location();
		velocity = result.velocity();
		Assert.assertEquals(new EntityLocation(10.19f, 10.05f, 9.98f), location);
		Assert.assertEquals(new EntityLocation(3.8f, 1.0f, -0.49f), velocity);
		Assert.assertFalse(result.isOnGround());
		
		result = EntityMovementHelpers.commonMovementIdiom(reader
			, location
			, velocity
			, volume
			, 0.01f
			, 0.0f
			, maxVelocityPerSecond
			, seconds
		);
		location = result.location();
		velocity = result.velocity();
		Assert.assertEquals(new EntityLocation(10.38f, 10.1f, 9.93f), location);
		Assert.assertEquals(new EntityLocation(3.88f, 0.97f, -0.98f), velocity);
		Assert.assertFalse(result.isOnGround());
	}

	@Test
	public void environmentVector()
	{
		// Tests the velocity vector added due to flowing water.
		Block waterStrong = ENV.blocks.fromItem(ENV.items.getItemById("op.water_strong"));
		Block waterWeak = ENV.blocks.fromItem(ENV.items.getItemById("op.water_weak"));
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(0, 0, 1), WATER_SOURCE.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(0, 0, 0), waterStrong.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(0, 1, 0), waterWeak.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(1, 0, 0), waterWeak.item().number());
		TickProcessingContext.IBlockFetcher previousBlockLookUp = ContextBuilder.buildFetcher((AbsoluteLocation location) -> {
			return BlockProxy.load(location.getBlockAddress(), cuboid);
		});
		EntityVolume volume = new EntityVolume(2.2f, 1.7f);
		
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), EntityMovementHelpers.getEnvironmentalVector(ENV, previousBlockLookUp, new EntityLocation(1.6f, 2.1f, 3.0f), volume));
		Assert.assertEquals(new EntityLocation(0.5f, 0.5f, -0.5f), EntityMovementHelpers.getEnvironmentalVector(ENV, previousBlockLookUp, new EntityLocation(0.0f, 0.0f, 0.0f), volume));
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), EntityMovementHelpers.getEnvironmentalVector(ENV, previousBlockLookUp, new EntityLocation(0.0f, 0.0f, 1.0f), volume));
		Assert.assertEquals(new EntityLocation(0.5f, 0.0f, 0.0f), EntityMovementHelpers.getEnvironmentalVector(ENV, previousBlockLookUp, new EntityLocation(1.0f, 0.0f, 0.0f), volume));
	}

	@Test
	public void saturatingAddition()
	{
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), EntityMovementHelpers.saturateVectorAddition(new EntityLocation(0.0f, 0.0f, 0.0f), new EntityLocation(0.0f, 0.0f, 0.0f)));
		Assert.assertEquals(new EntityLocation(1.0f, -2.0f, 3.0f), EntityMovementHelpers.saturateVectorAddition(new EntityLocation(0.0f, 0.0f, 0.0f), new EntityLocation(1.0f, -2.0f, 3.0f)));
		Assert.assertEquals(new EntityLocation(1.0f, -2.0f, 3.0f), EntityMovementHelpers.saturateVectorAddition(new EntityLocation(1.0f, -2.0f, 3.0f), new EntityLocation(1.0f, -2.0f, 3.0f)));
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), EntityMovementHelpers.saturateVectorAddition(new EntityLocation(-1.0f, 2.0f, -3.0f), new EntityLocation(1.0f, -2.0f, 3.0f)));
	}

	@Test
	public void walkOverSlabs()
	{
		Block slab = ENV.blocks.fromItem(ENV.items.getItemById("op.stone_brick_slab"));
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		
		// We will show that we can walk on top of slabs and under slabs in the same test.
		byte slabFloorZ = 0;
		byte slabCeilingZ = 2;
		CuboidGenerator.fillPlane(cuboid, slabFloorZ, slab);
		CuboidGenerator.fillPlane(cuboid, slabCeilingZ, slab);
		for (byte y = 0; y < Encoding.CUBOID_EDGE_SIZE; ++y)
		{
			for (byte x = 0; x < Encoding.CUBOID_EDGE_SIZE; ++x)
			{
				cuboid.setData7(AspectRegistry.ORIENTATION, new BlockAddress(x, y, slabFloorZ), FacingDirection.directionToByte(FacingDirection.DOWN));
				cuboid.setData7(AspectRegistry.ORIENTATION, new BlockAddress(x, y, slabCeilingZ), FacingDirection.directionToByte(FacingDirection.UP));
			}
		}
		
		TickProcessingContext.IBlockFetcher previousBlockLookUp = ContextBuilder.buildFetcher((AbsoluteLocation location) -> {
			return location.getCuboidAddress().equals(cuboid.getCuboidAddress())
				? BlockProxy.load(location.getBlockAddress(), cuboid)
				: null
			;
		});
		
		EntityLocation startLocation = new EntityLocation(1.0f, 1.0f, 0.5f);
		EntityLocation startVelocity = new EntityLocation(0.0f, 0.0f, 0.0f);
		EntityVolume volume = new EntityVolume(1.9f, 1.4f);
		float activeXMovement = 0.4f;
		float activeYMovement = 0.0f;
		float maxVelocityPerSecond = 4.0f;
		float seconds = 0.1f;
		
		ViscosityReader reader = new ViscosityReader(ENV, previousBlockLookUp);
		EntityMovementHelpers.HighLevelMovementResult result = EntityMovementHelpers.commonMovementIdiom(reader
			, startLocation
			, startVelocity
			, volume
			, activeXMovement
			, activeYMovement
			, maxVelocityPerSecond
			, seconds
		);
		
		Assert.assertEquals(new EntityLocation(1.4f, 1.0f, 0.5f), result.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), result.velocity());
		Assert.assertTrue(result.isOnGround());
	}

	@Test
	public void walkNearSlabs()
	{
		Block slab = ENV.blocks.fromItem(ENV.items.getItemById("op.stone_brick_slab"));
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidGenerator.fillPlane(cuboid, (byte)0, slab);
		
		// Add a few slabs as a wall.
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(0, 0, 1), slab.item().number());
		cuboid.setData7(AspectRegistry.ORIENTATION, BlockAddress.fromInt(0, 0, 1), FacingDirection.directionToByte(FacingDirection.WEST));
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(0, 1, 1), slab.item().number());
		cuboid.setData7(AspectRegistry.ORIENTATION, BlockAddress.fromInt(0, 1, 1), FacingDirection.directionToByte(FacingDirection.WEST));
		
		TickProcessingContext.IBlockFetcher previousBlockLookUp = ContextBuilder.buildFetcher((AbsoluteLocation location) -> {
			return location.getCuboidAddress().equals(cuboid.getCuboidAddress())
				? BlockProxy.load(location.getBlockAddress(), cuboid)
				: null
			;
		});
		
		EntityLocation startLocation = new EntityLocation(0.8f, 0.8f, 1.0f);
		EntityLocation startVelocity = new EntityLocation(0.0f, 0.0f, 0.0f);
		EntityVolume volume = new EntityVolume(2.2f, 1.7f);
		float activeXMovement = -0.4f;
		float activeYMovement = 0.0f;
		float maxVelocityPerSecond = 4.0f;
		float seconds = 0.1f;
		
		ViscosityReader reader = new ViscosityReader(ENV, previousBlockLookUp);
		EntityMovementHelpers.HighLevelMovementResult result = EntityMovementHelpers.commonMovementIdiom(reader
			, startLocation
			, startVelocity
			, volume
			, activeXMovement
			, activeYMovement
			, maxVelocityPerSecond
			, seconds
		);
		
		Assert.assertEquals(new EntityLocation(0.5f, 0.8f, 1.0f), result.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), result.velocity());
		Assert.assertTrue(result.isOnGround());
	}
}
