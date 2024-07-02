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
		boolean didMove = EntityMovementHelpers.allowMovement(context, entity, 100L);
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
		boolean didMove = EntityMovementHelpers.allowMovement(context, entity, 100L);
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
		boolean didMove = EntityMovementHelpers.moveEntity(context, entity, 100L, -0.2f, 0.0f);
		Assert.assertFalse(didMove);
		Assert.assertEquals(startLocation, entity.location);
		Assert.assertEquals(startVector, entity.vector);
		Assert.assertEquals(0, entity.cost);
	}

	@Test
	public void walkOnGround()
	{
		TickProcessingContext context = _createContext();
		_Entity entity = new _Entity();
		EntityLocation startLocation = entity.location;
		EntityLocation startVector = entity.vector;
		boolean didMove = EntityMovementHelpers.moveEntity(context, entity, 100L, 0.2f, 0.0f);
		// We should move but still have no vector since we aren't falling.
		Assert.assertTrue(didMove);
		Assert.assertNotEquals(startLocation, entity.location);
		Assert.assertEquals(startVector, entity.vector);
		Assert.assertNotEquals(0, entity.cost);
	}


	private static TickProcessingContext _createContext()
	{
		// We will treat the 0x0x0 cuboid as air, but all others as stone.
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), AIR);
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
		);
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
