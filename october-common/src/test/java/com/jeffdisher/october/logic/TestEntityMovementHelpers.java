package com.jeffdisher.october.logic;

import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.mutations.EntityChangeSwim;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.worldgen.CuboidGenerator;


public class TestEntityMovementHelpers
{
	private static Environment ENV;
	private static Block AIR;
	private static Block STONE;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		AIR = ENV.blocks.fromItem(ENV.items.getItemById("op.air"));
		STONE = ENV.blocks.fromItem(ENV.items.getItemById("op.stone"));
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
		EntityMovementHelpers.accelerate(context, entity, blocksPerSecond, millisInMotion, -1.0f, 0.0f);
		boolean didMove = _allowMovement(context, entity, millisInMotion);
		Assert.assertFalse(didMove);
		Assert.assertEquals(startLocation, entity.location);
		Assert.assertEquals(startVector, entity.vector);
		// Even though we hit a wall, we should still pay for the acceleration.
		Assert.assertEquals(40, entity.cost);
	}

	@Test
	public void walkOnGround()
	{
		TickProcessingContext context = _createContext();
		_Entity entity = new _Entity();
		EntityLocation startVector = entity.vector;
		long millisInMotion = 100L;
		float blocksPerSecond = 2.0f;
		EntityMovementHelpers.accelerate(context, entity, blocksPerSecond, millisInMotion, 1.0f, 0.0f);
		boolean didMove = _allowMovement(context, entity, millisInMotion);
		// We should move but still have no vector since we aren't falling.
		Assert.assertTrue(didMove);
		Assert.assertEquals(0.2f, entity.location.x(), 0.001f);
		Assert.assertEquals(startVector, entity.vector);
		Assert.assertEquals(40, entity.cost);
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
		EntityMovementHelpers.accelerate(context, entity, blocksPerSecond, millisInStep, 1.0f, 0.0f);
		EntityMovementHelpers.accelerate(context, entity, blocksPerSecond, millisInStep, 1.0f, 0.0f);
		boolean didMove = _allowMovement(context, entity, millisInMotion);
		// We should move but still have no vector since we aren't falling.
		Assert.assertTrue(didMove);
		Assert.assertEquals(0.2f, entity.location.x(), 0.001f);
		Assert.assertEquals(startVector, entity.vector);
		Assert.assertEquals(40, entity.cost);
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
		Assert.assertEquals(-7.86f, entity.vector.z(), 0.01f);
		Assert.assertEquals(-4.36f, entity.location.z() - startLocation.z(), 0.01f);
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
		Assert.assertEquals(3.68f, entity.vector.z(), 0.0001f);
		Assert.assertEquals(9.40f, entity.location.z(), 0.0001f);
	}


	private static TickProcessingContext _createContext()
	{
		// The common context is air.
		return _createContextWithCuboidType(AIR);
	}

	private static TickProcessingContext _createContextWithCuboidType(Block fillBlock)
	{
		// We will treat the 0x0x0 cuboid as the fill type, but all others as stone.
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), fillBlock);
		Function<AbsoluteLocation, BlockProxy> previousBlockLookUp = (AbsoluteLocation location) -> {
			CuboidAddress address = location.getCuboidAddress();
			BlockAddress block = location.getBlockAddress();
			return (address.equals(airCuboid.getCuboidAddress()))
					? new BlockProxy(block, airCuboid)
					: new BlockProxy(block, CuboidGenerator.createFilledCuboid(address, STONE))
			;
		};
		return new TickProcessingContext(1L
				, previousBlockLookUp
				, null
				, null
				, null
				, null
				, null
				, null
				, 100L
		);
	}

	private boolean _allowMovement(TickProcessingContext context, _Entity entity, long millisInMotion)
	{
		EntityLocation oldLocation = entity.getLocation();
		EntityMovementHelpers.allowMovement(context.previousBlockLookUp, entity, millisInMotion);
		return !oldLocation.equals(entity.getLocation());
	}

	private static class _Entity implements IMutableMinimalEntity
	{
		public EntityType type = EntityType.PLAYER;
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
		public int getBreath()
		{
			throw new AssertionError("Not in test");
		}
		@Override
		public void setBreath(int breath)
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
		public void handleEntityDeath(Consumer<IMutationBlock> mutationConsumer)
		{
			throw new AssertionError("Not in test");
		}
		@Override
		public void applyEnergyCost(TickProcessingContext context, int cost)
		{
			this.cost += cost;
		}
	}
}
