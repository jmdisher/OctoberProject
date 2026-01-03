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
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.MinimalEntity;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestSpatialHelpers
{
	private static final EntityVolume VOLUME = new EntityVolume(1.8f, 0.5f);
	private static Environment ENV;
	private static Block STONE;
	private static EntityType COW;
	@BeforeClass
	public static void setup() throws Throwable
	{
		ENV = Environment.createSharedInstance();
		STONE = ENV.blocks.fromItem(ENV.items.getItemById("op.stone"));
		COW = ENV.creatures.getTypeById("op.cow");
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
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		Function<AbsoluteLocation, BlockProxy> blockTypeReader = (AbsoluteLocation l) -> new BlockProxy(l.getBlockAddress(), cuboid);
		ViscosityReader reader = new ViscosityReader(ENV, blockTypeReader);
		boolean canExist = SpatialHelpers.canExistInLocation(reader, location, VOLUME);
		Assert.assertTrue(canExist);
	}

	@Test
	public void cantExistInStone()
	{
		// Just ask if they can exist when only stone is present.
		EntityLocation location = new EntityLocation(0.0f, 0.0f, 0.0f);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), STONE);
		Function<AbsoluteLocation, BlockProxy> blockTypeReader = (AbsoluteLocation l) -> new BlockProxy(l.getBlockAddress(), cuboid);
		ViscosityReader reader = new ViscosityReader(ENV, blockTypeReader);
		boolean canExist = SpatialHelpers.canExistInLocation(reader, location, VOLUME);
		Assert.assertFalse(canExist);
	}

	@Test
	public void cantStandInAir()
	{
		// Just ask if they can stand when only air is present (as this doesn't check the blocks they occupy).
		EntityLocation location = new EntityLocation(0.0f, 0.0f, 0.0f);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		Function<AbsoluteLocation, BlockProxy> blockTypeReader = (AbsoluteLocation l) -> new BlockProxy(l.getBlockAddress(), cuboid);
		ViscosityReader reader = new ViscosityReader(ENV, blockTypeReader);
		boolean isStanding = SpatialHelpers.isStandingOnGround(reader, location, VOLUME);
		Assert.assertFalse(isStanding);
	}

	@Test
	public void canStandInStone()
	{
		// Just ask if they can stand when only stone is present (as this doesn't check the blocks they occupy).
		EntityLocation location = new EntityLocation(0.0f, 0.0f, 0.0f);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), STONE);
		Function<AbsoluteLocation, BlockProxy> blockTypeReader = (AbsoluteLocation l) -> new BlockProxy(l.getBlockAddress(), cuboid);
		ViscosityReader reader = new ViscosityReader(ENV, blockTypeReader);
		boolean isStanding = SpatialHelpers.isStandingOnGround(reader, location, VOLUME);
		Assert.assertTrue(isStanding);
	}

	@Test
	public void isNotTouchingCeiling()
	{
		// Ask if they are touching the ceiling when clearly not.
		EntityLocation location = new EntityLocation(0.0f, 0.0f, 0.0f);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		Function<AbsoluteLocation, BlockProxy> blockTypeReader = (AbsoluteLocation l) -> new BlockProxy(l.getBlockAddress(), cuboid);
		ViscosityReader reader = new ViscosityReader(ENV, blockTypeReader);
		boolean isTouching = SpatialHelpers.isTouchingCeiling(reader, location, VOLUME);
		Assert.assertFalse(isTouching);
	}

	@Test
	public void isTouchingCeiling()
	{
		// Ask if they are touching the ceiling when they are right against it.
		EntityLocation location = new EntityLocation(0.0f, 0.0f, 0.2f);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), STONE);
		Function<AbsoluteLocation, BlockProxy> blockTypeReader = (AbsoluteLocation l) -> new BlockProxy(l.getBlockAddress(), cuboid);
		ViscosityReader reader = new ViscosityReader(ENV, blockTypeReader);
		boolean isTouching = SpatialHelpers.isTouchingCeiling(reader, location, VOLUME);
		Assert.assertTrue(isTouching);
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

	@Test
	public void entityPartLocations()
	{
		EntityLocation location1 = new EntityLocation(1.0f, -1.0f, 12.0f);
		MutableEntity entity1 = MutableEntity.createWithLocation(1, location1, location1);
		EntityLocation location2 = new EntityLocation(1.8f, -1.8f, 12.6f);
		MutableEntity entity2 = MutableEntity.createWithLocation(2, location2, location2);
		
		EntityLocation feet1 = SpatialHelpers.getCentreFeetLocation(entity1);
		Assert.assertEquals(new EntityLocation(1.2f, -0.8f, 12.0f), feet1);
		AbsoluteLocation block1 = SpatialHelpers.getBlockAtFeet(entity1);
		Assert.assertEquals(new AbsoluteLocation(1, -1, 12), block1);
		EntityLocation eye1 = SpatialHelpers.getEyeLocation(entity1);
		Assert.assertEquals(new EntityLocation(1.2f, -0.8f, 13.53f), eye1);
		EntityLocation feet2 = SpatialHelpers.getCentreFeetLocation(entity2);
		Assert.assertEquals(new EntityLocation(2.0f, -1.6f, 12.6f), feet2);
		AbsoluteLocation block2 = SpatialHelpers.getBlockAtFeet(entity2);
		Assert.assertEquals(new AbsoluteLocation(2, -2, 12), block2);
		EntityLocation eye2 = SpatialHelpers.getEyeLocation(entity2);
		Assert.assertEquals(new EntityLocation(2.0f, -1.6f, 14.13f), eye2);
	}

	@Test
	public void entityEyeDistances()
	{
		EntityLocation location1 = new EntityLocation(1.0f, -1.0f, 12.0f);
		MutableEntity entity1 = MutableEntity.createWithLocation(1, location1, location1);
		EntityLocation location2 = new EntityLocation(1.8f, -1.8f, 12.6f);
		MinimalEntity entity2 = new MinimalEntity(2, COW, location2);
		AbsoluteLocation block1 = new AbsoluteLocation(-3, 2, -1);
		
		float entityDistance = SpatialHelpers.distanceFromMutableEyeToEntitySurface(entity1, entity2);
		Assert.assertEquals(0.6f, entityDistance, 0.01f);
		float blockDistanceMutable = SpatialHelpers.distanceFromMutableEyeToBlockSurface(entity1, block1);
		Assert.assertEquals(14.18f, blockDistanceMutable, 0.01f);
		float blockDistancePlayer = SpatialHelpers.distanceFromPlayerEyeToBlockSurface(entity1.newLocation, entity1.getType(), block1);
		Assert.assertEquals(14.18f, blockDistancePlayer, 0.01f);
	}

	@Test
	public void sourceToRegionDistance()
	{
		EntityLocation location1 = new EntityLocation(1.0f, -1.0f, 12.0f);
		EntityLocation base1 = location1;
		EntityVolume volume1 = ENV.creatures.PLAYER.volume();
		EntityLocation location2 = new EntityLocation(1.8f, -1.8f, 12.6f);
		EntityLocation base2 = location2;
		EntityVolume volume2 = COW.volume();
		
		float distance1 = SpatialHelpers.distanceFromLocationToVolume(location1, base2, volume2);
		float distance2 = SpatialHelpers.distanceFromLocationToVolume(location2, base1, volume1);
		Assert.assertEquals(1.00f, distance1, 0.01f);
		Assert.assertEquals(0.89f, distance2, 0.01f);
	}

	@Test
	public void groundCollision()
	{
		CuboidData stoneCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), STONE);
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		Function<AbsoluteLocation, BlockProxy> blockTypeReader = (AbsoluteLocation l) -> {
			BlockProxy proxy;
			if (((1 == l.x()) && (1 == l.y())) || (l.z() >= 2))
			{
				proxy = new BlockProxy(l.getBlockAddress(), airCuboid);
			}
			else
			{
				proxy = new BlockProxy(l.getBlockAddress(), stoneCuboid);
			}
			return proxy;
		};
		ViscosityReader reader = new ViscosityReader(ENV, blockTypeReader);
		
		EntityLocation onLedge = new EntityLocation(1.6f, 1.6f, 2.0f);
		EntityLocation offLedge = new EntityLocation(1.59f, 1.59f, 2.0f);
		EntityVolume volume = new EntityVolume(0.8f, 0.4f);
		
		// First, verify that the interactive helper gives the expected result.
		EntityMovementHelpers.interactiveEntityMove(onLedge, volume, new EntityLocation(0.0f, 0.0f, -1.0f), new _EndpointHelper(onLedge, true));
		EntityMovementHelpers.interactiveEntityMove(offLedge, volume, new EntityLocation(0.0f, 0.0f, -1.0f), new _EndpointHelper(new EntityLocation(1.59f, 1.59f, 1.0f), false));
		
		// Now, verify that the spatial helper is consistent.
		Assert.assertTrue(SpatialHelpers.isStandingOnGround(reader, onLedge, volume));
		Assert.assertFalse(SpatialHelpers.isStandingOnGround(reader, offLedge, volume));
	}

	@Test
	public void stuckInBlocks()
	{
		// Show how these helpers respond when we are stuck in a block.
		EntityLocation location = new EntityLocation(5.1f, -6.4f, 7.2f);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, -1, 0), STONE);
		Function<AbsoluteLocation, BlockProxy> blockTypeReader = (AbsoluteLocation l) -> new BlockProxy(l.getBlockAddress(), cuboid);
		ViscosityReader reader = new ViscosityReader(ENV, blockTypeReader);
		
		// We are considered standing on ground, since we can't move down.
		Assert.assertTrue(SpatialHelpers.isStandingOnGround(reader, location, VOLUME));
		
		// We are considered touching a ceiling, since we can't move up.
		Assert.assertTrue(SpatialHelpers.isTouchingCeiling(reader, location, VOLUME));
		
		// We can't exist in this location since it intersects with solid blocks.
		Assert.assertFalse(SpatialHelpers.canExistInLocation(reader, location, VOLUME));
	}

	@Test
	public void floatFraction()
	{
		Assert.assertEquals(0.4f, SpatialHelpers.getPositiveFractionalComponent(1.4f), 0.01f);
		Assert.assertEquals(0.8f, SpatialHelpers.getPositiveFractionalComponent(10.8f), 0.01f);
		Assert.assertEquals(0.8f, SpatialHelpers.getPositiveFractionalComponent(-1.2f), 0.01f);
		Assert.assertEquals(0.6f, SpatialHelpers.getPositiveFractionalComponent(-10.4f), 0.01f);
	}

	@Test
	public void centreOfRegion()
	{
		EntityLocation base = new EntityLocation(5.1f, -6.4f, 7.2f);
		EntityVolume volume = new EntityVolume(0.5f, 0.4f);
		
		EntityLocation centre = SpatialHelpers.getCentreOfRegion(base, volume);
		Assert.assertEquals(new EntityLocation(5.3f, -6.2f, 7.45f), centre);
	}

	@Test
	public void distanceFromPlayerToEntity()
	{
		// Test these helpers as one is mostly just a convenience on the other.
		EntityLocation targetLocation = new EntityLocation(1.2f, -2.3f, 3.4f);
		CreatureEntity creature = new CreatureEntity(-1
			, COW
			, targetLocation
			, new EntityLocation(0.0f, 0.0f, 0.0f)
			, (byte)0
			, (byte)0
			, (byte)50
			, (byte)50
			, null
			, null
		);
		MinimalEntity target = MinimalEntity.fromCreature(creature);
		
		EntityLocation playerBase = new EntityLocation(5.1f, -6.4f, 7.2f);
		float distanceEntity = SpatialHelpers.distanceFromPlayerEyeToEntitySurface(playerBase, ENV.creatures.PLAYER, target);
		float distanceManual = SpatialHelpers.distanceFromPlayerEyeToVolume(playerBase, ENV.creatures.PLAYER, targetLocation, COW.volume());
		
		Assert.assertEquals(6.58f, distanceEntity, 0.01f);
		Assert.assertEquals(6.58f, distanceManual, 0.01f);
	}

	@Test
	public void facingVectors()
	{
		Assert.assertEquals(new EntityLocation(0.0f, 1.0f, 0.0f), SpatialHelpers.getUnitFacingVector((byte)0, (byte)0));
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 1.0f), SpatialHelpers.getUnitFacingVector((byte)0, (byte)64));
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, -1.0f), SpatialHelpers.getUnitFacingVector((byte)0, (byte)-64));
		Assert.assertEquals(new EntityLocation(0.0f, -1.0f, 0.0f), SpatialHelpers.getUnitFacingVector((byte)-128, (byte)0));
		Assert.assertEquals(new EntityLocation(-1.0f, 0.0f, 0.0f), SpatialHelpers.getUnitFacingVector((byte)64, (byte)0));
		Assert.assertEquals(new EntityLocation(0.71f, 0.71f, 0.0f), SpatialHelpers.getUnitFacingVector((byte)-32, (byte)0));
		Assert.assertEquals(new EntityLocation(0.5f, 0.5f, 0.71f), SpatialHelpers.getUnitFacingVector((byte)-32, (byte)32));
	}

	@Test
	public void ballisticVector()
	{
		Assert.assertEquals(new EntityLocation(0.0f, 9.95f, 0.98f), SpatialHelpers.getBallisticVector(new EntityLocation(0.0f, 0.0f, 0.0f), new EntityLocation(0.0f, 2.0f, 0.0f), 10.0f));
		Assert.assertEquals(new EntityLocation(0.0f, -9.95f, 0.98f), SpatialHelpers.getBallisticVector(new EntityLocation(0.0f, 0.0f, 0.0f), new EntityLocation(0.0f, -2.0f, 0.0f), 10.0f));
		Assert.assertEquals(new EntityLocation(0.0f, 7.74f, 6.33f), SpatialHelpers.getBallisticVector(new EntityLocation(0.0f, 0.0f, 0.0f), new EntityLocation(0.0f, 10.0f, 0.0f), 10.0f));
		Assert.assertEquals(null, SpatialHelpers.getBallisticVector(new EntityLocation(0.0f, 0.0f, 0.0f), new EntityLocation(0.0f, 10.0f, 5.0f), 10.0f));
		Assert.assertEquals(new EntityLocation(0.0f, 10.0f, -0.1f), SpatialHelpers.getBallisticVector(new EntityLocation(0.0f, 0.0f, 0.0f), new EntityLocation(0.0f, 10.0f, -5.0f), 10.0f));
		Assert.assertEquals(new EntityLocation(4.46f, 4.46f, 7.76f), SpatialHelpers.getBallisticVector(new EntityLocation(0.0f, 0.0f, 0.0f), new EntityLocation(3.0f, 3.0f, 3.0f), 10.0f));
	}


	private static class _EndpointHelper implements EntityMovementHelpers.IInteractiveHelper
	{
		private final EntityLocation _expected;
		private final boolean _isOnGround;
		public _EndpointHelper(EntityLocation expected, boolean isOnGround)
		{
			_expected = expected;
			_isOnGround = isOnGround;
		}
		@Override
		public void setLocationAndCancelVelocity(EntityLocation finalLocation, boolean cancelX, boolean cancelY, boolean cancelZ)
		{
			Assert.assertEquals(_expected, finalLocation);
			Assert.assertFalse(cancelX);
			Assert.assertFalse(cancelY);
			Assert.assertEquals(_isOnGround, cancelZ);
		}
		@Override
		public float getViscosityForBlockAtLocation(AbsoluteLocation l, boolean fromAbove)
		{
			float viscosity;
			if (((1 == l.x()) && (1 == l.y())) || (l.z() >= 2))
			{
				viscosity = 0.0f;
			}
			else
			{
				viscosity = 1.0f;
			}
			return viscosity;
		}
	}
}
