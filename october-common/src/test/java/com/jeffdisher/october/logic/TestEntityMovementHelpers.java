package com.jeffdisher.october.logic;

import java.util.function.Function;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.mutations.EntityChangeSwim;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.ContextBuilder;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestEntityMovementHelpers
{
	private static Environment ENV;
	private static Block AIR;
	private static Block STONE;
	private static EntityType ORC;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		AIR = ENV.blocks.fromItem(ENV.items.getItemById("op.air"));
		STONE = ENV.blocks.fromItem(ENV.items.getItemById("op.stone"));
		ORC = ENV.creatures.getTypeById("op.orc");
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void standOnGround()
	{
		TickProcessingContext context = _createContext();
		_Entity entity = new _Entity();
		EntityLocation startLocation = entity.location;
		EntityLocation startVector = entity.vector;
		boolean didMove = _allowMovement(context, entity, 100L);
		Assert.assertFalse(didMove);
		Assert.assertEquals(startLocation, entity.location);
		Assert.assertEquals(startVector, entity.vector);
		Assert.assertEquals(0, entity.cost);
	}

	@Test
	public void fallThroughAir()
	{
		TickProcessingContext context = _createContext();
		_Entity entity = new _Entity();
		entity.location = new EntityLocation(0.0f, 0.0f, 20.0f);
		EntityLocation startLocation = entity.location;
		EntityLocation startVector = entity.vector;
		boolean didMove = _allowMovement(context, entity, 100L);
		Assert.assertTrue(didMove);
		Assert.assertNotEquals(startLocation, entity.location);
		Assert.assertNotEquals(startVector, entity.vector);
		Assert.assertEquals(0, entity.cost);
	}

	@Test
	public void walkIntoWall()
	{
		TickProcessingContext context = _createContext();
		_Entity entity = new _Entity();
		EntityLocation startLocation = entity.location;
		EntityLocation startVector = entity.vector;
		long millisInMotion = 100L;
		float blocksPerSecond = 2.0f;
		EntityMovementHelpers.accelerate(entity, blocksPerSecond, millisInMotion, -1.0f, 0.0f);
		boolean didMove = _allowMovement(context, entity, millisInMotion);
		Assert.assertFalse(didMove);
		Assert.assertEquals(startLocation, entity.location);
		Assert.assertEquals(startVector, entity.vector);
		// Even though we hit a wall, we should still pay for the acceleration.
		Assert.assertEquals(20, entity.cost);
	}

	@Test
	public void walkOnGround()
	{
		TickProcessingContext context = _createContext();
		_Entity entity = new _Entity();
		EntityLocation startVector = entity.vector;
		long millisInMotion = 100L;
		float blocksPerSecond = 2.0f;
		EntityMovementHelpers.accelerate(entity, blocksPerSecond, millisInMotion, 1.0f, 0.0f);
		boolean didMove = _allowMovement(context, entity, millisInMotion);
		// We should move but still have no vector since we aren't falling.
		Assert.assertTrue(didMove);
		Assert.assertEquals(0.2f, entity.location.x(), 0.001f);
		Assert.assertEquals(startVector, entity.vector);
		Assert.assertEquals(20, entity.cost);
	}

	@Test
	public void multiStepWalk()
	{
		TickProcessingContext context = _createContext();
		_Entity entity = new _Entity();
		EntityLocation startVector = entity.vector;
		long millisInMotion = 100L;
		float blocksPerSecond = 2.0f;
		long millisInStep = millisInMotion / 2L;
		EntityMovementHelpers.accelerate(entity, blocksPerSecond, millisInStep, 1.0f, 0.0f);
		EntityMovementHelpers.accelerate(entity, blocksPerSecond, millisInStep, 1.0f, 0.0f);
		boolean didMove = _allowMovement(context, entity, millisInMotion);
		// We should move but still have no vector since we aren't falling.
		Assert.assertTrue(didMove);
		Assert.assertEquals(0.2f, entity.location.x(), 0.001f);
		Assert.assertEquals(startVector, entity.vector);
		Assert.assertEquals(20, entity.cost);
	}

	@Test
	public void fallingThroughAir()
	{
		TickProcessingContext context = _createContext();
		_Entity entity = new _Entity();
		EntityLocation startLocation = new EntityLocation(5, 5, 30);
		entity.location = startLocation;
		long millisInMotion = 100L;
		for (int i = 0; i < 10; ++i)
		{
			Assert.assertTrue(_allowMovement(context, entity, millisInMotion));
		}
		// We know that this should be -9.8 and the acceleration is linear so the distance is half.
		Assert.assertEquals(-9.8f, entity.vector.z(), 0.01f);
		Assert.assertEquals(-4.9f, entity.location.z() - startLocation.z(), 0.01f);
	}

	@Test
	public void fallingThroughWater()
	{
		Block waterSource = ENV.blocks.fromItem(ENV.items.getItemById("op.water_source"));
		TickProcessingContext context = _createContextWithCuboidType(waterSource);
		_Entity entity = new _Entity();
		EntityLocation startLocation = new EntityLocation(5, 5, 30);
		entity.location = startLocation;
		long millisInMotion = 100L;
		for (int i = 0; i < 10; ++i)
		{
			Assert.assertTrue(_allowMovement(context, entity, millisInMotion));
		}
		// We know that the drag of the water will slow this down but these are experimentally derived.
		Assert.assertEquals(-0.97f, entity.vector.z(), 0.01f);
		Assert.assertEquals(-0.42f, entity.location.z() - startLocation.z(), 0.01f);
	}

	@Test
	public void swimmingUp()
	{
		Block waterSource = ENV.blocks.fromItem(ENV.items.getItemById("op.water_source"));
		TickProcessingContext context = _createContextWithCuboidType(waterSource);
		_Entity entity = new _Entity();
		EntityLocation startLocation = new EntityLocation(5.0f, 5.0f, 5.0f);
		entity.location = startLocation;
		long millisInMotion = 100L;
		for (int i = 0; i < 10; ++i)
		{
			entity.vector = new EntityLocation(0.0f, 0.0f, EntityChangeSwim.SWIM_FORCE);
			Assert.assertTrue(_allowMovement(context, entity, millisInMotion));
		}
		// We know that the drag of the water will slow this down but these are experimentally derived.
		Assert.assertEquals(1.96f, entity.vector.z(), 0.01f);
		Assert.assertEquals(6.70f, entity.location.z(), 0.01f);
	}

	@Test
	public void fallingCollision()
	{
		// Show that we account for falling under each block and stop completely when we collide.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(16, 16, 15), STONE.item().number());
		_Entity entity = new _Entity();
		entity.location = new EntityLocation(16.8f, 16.8f, 16.1f);
		entity.vector = new EntityLocation(0.0f, 0.0f, MotionHelpers.FALLING_TERMINAL_VELOCITY_PER_SECOND / 2.0f);
		EntityMovementHelpers.allowMovement((AbsoluteLocation location) -> {
			return new BlockProxy(location.getBlockAddress(), cuboid);
		}, entity, 100L);
		Assert.assertEquals(new EntityLocation(16.8f, 16.8f, 16.0f), entity.location);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), entity.vector);
	}

	@Test
	public void fallThroughGround()
	{
		// A test to show that the issue of falling through the ground (from the old movement helper) has been fixed.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(16, 16, 0), STONE.item().number());
		_Entity entity = new _Entity();
		entity.type = ORC;
		entity.location = new EntityLocation(16.8f, 16.8f, 1.11f);
		entity.vector = new EntityLocation(0.0f, 0.0f, -17.64f);
		EntityMovementHelpers.allowMovement((AbsoluteLocation location) -> {
			return new BlockProxy(location.getBlockAddress(), cuboid);
		}, entity, 100L);
		Assert.assertEquals(new EntityLocation(16.8f, 16.8f, 1.0f), entity.location);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), entity.vector);
	}

	@Test
	public void walkIntoCorner()
	{
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), STONE);
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(16, 16, 1), AIR.item().number());
		_Entity entity = new _Entity();
		entity.location = new EntityLocation(16.0f, 16.0f, 1.0f);
		entity.vector = new EntityLocation(8.0f, 7.0f, 0.0f);
		long millisInMotion = 100L;
		EntityMovementHelpers.allowMovement((AbsoluteLocation location) -> {
			return new BlockProxy(location.getBlockAddress(), cuboid);
		}, entity, millisInMotion);
		Assert.assertEquals(new EntityLocation(16.59f, 16.59f, 1.0f), entity.location);
	}

	@Test
	public void walkAlongWall()
	{
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), STONE);
		for (int i = 1; i < 31; ++i)
		{
			cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(16, i, 1), AIR.item().number());
		}
		EntityLocation velocity = new EntityLocation(8.0f, 7.0f, 0.0f);
		_Entity entity = new _Entity();
		entity.location = new EntityLocation(16.0f, 16.0f, 1.0f);
		entity.vector = velocity;
		long millisInMotion = 100L;
		EntityMovementHelpers.allowMovement((AbsoluteLocation location) -> {
			return new BlockProxy(location.getBlockAddress(), cuboid);
		}, entity, millisInMotion);
		Assert.assertEquals(new EntityLocation(16.59f, 16.7f, 1.0f), entity.location);
		entity.vector = velocity;
		EntityMovementHelpers.allowMovement((AbsoluteLocation location) -> {
			return new BlockProxy(location.getBlockAddress(), cuboid);
		}, entity, millisInMotion);
		Assert.assertEquals(new EntityLocation(16.59f, 17.40f, 1.0f), entity.location);
	}

	@Test
	public void boxTopRight()
	{
		EntityLocation location = new EntityLocation(1.3f, 1.4f, 1.5f);
		EntityVolume volume = new EntityVolume(1.2f, 1.2f);
		EntityLocation velocity = new EntityLocation(1.0f, 1.0f, 1.0f);
		EntityMovementHelpers.interactiveEntityMove(location, volume, velocity, new EntityMovementHelpers.InteractiveHelper() {
			@Override
			public void setLocationAndViscosity(EntityLocation finalLocation, float viscosity, boolean cancelX, boolean cancelY, boolean cancelZ)
			{
				Assert.assertEquals(new EntityLocation(1.79f, 1.79f, 1.79f), finalLocation);
				Assert.assertTrue(cancelX);
				Assert.assertTrue(cancelY);
				Assert.assertTrue(cancelZ);
			}
			@Override
			public float getViscosityForBlockAtLocation(AbsoluteLocation location)
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
		EntityMovementHelpers.interactiveEntityMove(location, volume, velocity, new EntityMovementHelpers.InteractiveHelper() {
			@Override
			public void setLocationAndViscosity(EntityLocation finalLocation, float viscosity, boolean cancelX, boolean cancelY, boolean cancelZ)
			{
				Assert.assertEquals(new EntityLocation(1.0f, 1.0f, 1.0f), finalLocation);
				Assert.assertTrue(cancelX);
				Assert.assertTrue(cancelY);
				Assert.assertTrue(cancelZ);
			}
			@Override
			public float getViscosityForBlockAtLocation(AbsoluteLocation location)
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
		EntityMovementHelpers.interactiveEntityMove(location, volume, velocity, new EntityMovementHelpers.InteractiveHelper() {
			@Override
			public void setLocationAndViscosity(EntityLocation finalLocation, float viscosity, boolean cancelX, boolean cancelY, boolean cancelZ)
			{
				Assert.assertEquals(velocity, finalLocation);
				Assert.assertFalse(cancelX);
				Assert.assertFalse(cancelY);
				Assert.assertFalse(cancelZ);
			}
			@Override
			public float getViscosityForBlockAtLocation(AbsoluteLocation location)
			{
				return 0.0f;
			}
		});
	}


	private static TickProcessingContext _createContext()
	{
		// The common context is air.
		return _createContextWithCuboidType(AIR);
	}

	private static TickProcessingContext _createContextWithCuboidType(Block fillBlock)
	{
		// We will treat the 0x0x0 cuboid as the fill type, but all others as stone.
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), fillBlock);
		Function<AbsoluteLocation, BlockProxy> previousBlockLookUp = (AbsoluteLocation location) -> {
			CuboidAddress address = location.getCuboidAddress();
			BlockAddress block = location.getBlockAddress();
			return (address.equals(airCuboid.getCuboidAddress()))
					? new BlockProxy(block, airCuboid)
					: new BlockProxy(block, CuboidGenerator.createFilledCuboid(address, STONE))
			;
		};
		return ContextBuilder.build()
				.lookups(previousBlockLookUp, null)
				.finish()
		;
	}

	private boolean _allowMovement(TickProcessingContext context, _Entity entity, long millisInMotion)
	{
		EntityLocation oldLocation = entity.getLocation();
		EntityMovementHelpers.allowMovement(context.previousBlockLookUp, entity, millisInMotion);
		return !oldLocation.equals(entity.getLocation());
	}

	private static class _Entity implements IMutableMinimalEntity
	{
		public EntityType type = ENV.creatures.PLAYER;
		public EntityLocation location = new EntityLocation(0.0f, 0.0f, 0.0f);
		public EntityLocation vector = new EntityLocation(0.0f, 0.0f, 0.0f);
		public int cost = 0;
		
		@Override
		public int getId()
		{
			throw new AssertionError("Not in test");
		}
		@Override
		public EntityType getType()
		{
			return this.type;
		}
		@Override
		public EntityLocation getLocation()
		{
			return this.location;
		}
		@Override
		public void setLocation(EntityLocation location)
		{
			this.location = location;
		}
		@Override
		public EntityLocation getVelocityVector()
		{
			return this.vector;
		}
		@Override
		public void setVelocityVector(EntityLocation vector)
		{
			this.vector = vector;
		}
		@Override
		public byte getHealth()
		{
			throw new AssertionError("Not in test");
		}
		@Override
		public void setHealth(byte health)
		{
			throw new AssertionError("Not in test");
		}
		@Override
		public byte getBreath()
		{
			throw new AssertionError("Not in test");
		}
		@Override
		public void setBreath(byte breath)
		{
			throw new AssertionError("Not in test");
		}
		@Override
		public void setOrientation(byte yaw, byte pitch)
		{
			throw new AssertionError("Not in test");
		}
		@Override
		public byte getYaw()
		{
			throw new AssertionError("Not in test");
		}
		@Override
		public byte getPitch()
		{
			throw new AssertionError("Not in test");
		}
		@Override
		public NonStackableItem getArmour(BodyPart part)
		{
			throw new AssertionError("Not in test");
		}
		@Override
		public void setArmour(BodyPart part, NonStackableItem item)
		{
			throw new AssertionError("Not in test");
		}
		@Override
		public void resetLongRunningOperations()
		{
			throw new AssertionError("Not in test");
		}
		@Override
		public void handleEntityDeath(TickProcessingContext context)
		{
			throw new AssertionError("Not in test");
		}
		@Override
		public void applyEnergyCost(int cost)
		{
			this.cost += cost;
		}
		@Override
		public boolean updateDamageTimeoutIfValid(long currentTickMillis)
		{
			throw new AssertionError("Not in test");
		}
	}
}
