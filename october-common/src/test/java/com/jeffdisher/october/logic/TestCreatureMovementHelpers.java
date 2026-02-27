package com.jeffdisher.october.logic;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.actions.EntityActionSimpleMove;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.subactions.EntityChangeJump;
import com.jeffdisher.october.subactions.EntityChangeSwim;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.ContextBuilder;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutableCreatureEntity;
import com.jeffdisher.october.types.MutableCreature;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestCreatureMovementHelpers
{
	private static Environment ENV;
	private static EntityType COW;
	private static Block STONE;
	private static Block WATER_SOURCE;
	@BeforeClass
	public static void setup() throws Throwable
	{
		ENV = Environment.createSharedInstance();
		COW = ENV.creatures.getTypeById("op.cow");
		STONE = ENV.blocks.fromItem(ENV.items.getItemById("op.stone"));
		WATER_SOURCE = ENV.blocks.fromItem(ENV.items.getItemById("op.water_source"));
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void noMoveCentre()
	{
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 1.0f);
		CreatureEntity creature = _createCow(location);
		IEntityAction<IMutableCreatureEntity> change = CreatureMovementHelpers.prepareForMove(creature.location(), creature.velocity(), creature.type(), location.getBlockLocation(), 100L, 0.0f, false);
		Assert.assertNull(change);
		
		location = new EntityLocation(-1.0f, -1.0f, 1.0f);
		creature = _createCow(location);
		change = CreatureMovementHelpers.prepareForMove(creature.location(), creature.velocity(), creature.type(), location.getBlockLocation(), 100L, 0.0f, false);
		Assert.assertNull(change);
	}

	@Test
	public void multiMoveCentre()
	{
		EntityLocation location = new EntityLocation(1.9f, 1.9f, 1.0f);
		CreatureEntity creature = _createCow(location);
		IEntityAction<IMutableCreatureEntity> change = CreatureMovementHelpers.prepareForMove(creature.location(), creature.velocity(), creature.type(), location.getBlockLocation(), 100L, 0.0f, false);
		Assert.assertNotNull(change);
		
		location = new EntityLocation(-1.1f, -1.1f, 1.0f);
		creature = _createCow(location);
		change = CreatureMovementHelpers.prepareForMove(creature.location(), creature.velocity(), creature.type(), location.getBlockLocation(), 100L, 0.0f, false);
		Assert.assertNotNull(change);
	}

	@Test
	public void walkOne()
	{
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 1.0f);
		CreatureEntity creature = _createCow(location);
		AbsoluteLocation target = new AbsoluteLocation(1, 2, 1);
		ViscosityReader reader = _getFixedBlockReader(ENV.special.AIR);
		IEntityAction<IMutableCreatureEntity> change = CreatureMovementHelpers.moveToNextLocation(reader, creature.location(), creature.velocity(), (byte)0, (byte)0, creature.type(), target, 100L, 0.0f, false, false);
		Assert.assertNotNull(change);
	}

	@Test
	public void walkMinusOneDeliberate()
	{
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 1.0f);
		CreatureEntity creature = _createCow(location);
		AbsoluteLocation target = new AbsoluteLocation(0, 1, 1);
		ViscosityReader reader = _getFixedBlockReader(ENV.special.AIR);
		IEntityAction<IMutableCreatureEntity> change = CreatureMovementHelpers.moveToNextLocation(reader, creature.location(), creature.velocity(), (byte)0, (byte)0, creature.type(), target, 100L, 0.0f, false, false);
		// Deliberate movement is 0.2/move.
		Assert.assertNotNull(change);
	}

	@Test
	public void walkMinusOneIdle()
	{
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 1.0f);
		CreatureEntity creature = _createCow(location);
		AbsoluteLocation target = new AbsoluteLocation(0, 1, 1);
		ViscosityReader reader = _getFixedBlockReader(ENV.special.AIR);
		IEntityAction<IMutableCreatureEntity> change = CreatureMovementHelpers.moveToNextLocation(reader, creature.location(), creature.velocity(), (byte)0, (byte)0, creature.type(), target, 100L, 0.0f, true, false);
		// Idle movement is 0.1/move and we are moving 0.8 (width) plus a fudge factor (which is rounded down, here).
		Assert.assertNotNull(change);
	}

	@Test
	public void jump()
	{
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 0.0f);
		CreatureEntity creature = _createCow(location);
		AbsoluteLocation target = new AbsoluteLocation(1, 1, 2);
		ViscosityReader reader = _getSplitBlockReader(ENV.special.AIR, STONE);
		EntityActionSimpleMove<IMutableCreatureEntity> change = CreatureMovementHelpers.moveToNextLocation(reader, creature.location(), creature.velocity(), (byte)0, (byte)0, creature.type(), target, 100L, 0.0f, true, false);
		Assert.assertNotNull(change);
		Assert.assertTrue(change.getSubAction() instanceof EntityChangeJump);
	}

	@Test
	public void failJump()
	{
		// In this case, we will still be rising so we want to jump but can't since we aren't on the ground.
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 1.5f);
		CreatureEntity creature = _createCow(location);
		AbsoluteLocation target = new AbsoluteLocation(1, 1, 2);
		ViscosityReader reader = _getFixedBlockReader(ENV.special.AIR);
		EntityActionSimpleMove<IMutableCreatureEntity> change = CreatureMovementHelpers.moveToNextLocation(reader, creature.location(), creature.velocity(), (byte)0, (byte)0, creature.type(), target, 100L, 0.0f, true, false);
		Assert.assertNotNull(change);
		Assert.assertNull(change.getSubAction());
	}

	@Test
	public void walkAirborn()
	{
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 2.2f);
		CreatureEntity creature = _createCow(location);
		AbsoluteLocation target = new AbsoluteLocation(1, 2, 2);
		ViscosityReader reader = _getFixedBlockReader(ENV.special.AIR);
		IEntityAction<IMutableCreatureEntity> change = CreatureMovementHelpers.moveToNextLocation(reader, creature.location(), creature.velocity(), (byte)0, (byte)0, creature.type(), target, 100L, 0.0f, false, false);
		Assert.assertNotNull(change);
	}

	@Test
	public void fall()
	{
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 1.0f);
		CreatureEntity creature = _createCow(location);
		AbsoluteLocation target = new AbsoluteLocation(1, 1, 0);
		ViscosityReader reader = _getFixedBlockReader(ENV.special.AIR);
		IEntityAction<IMutableCreatureEntity> change = CreatureMovementHelpers.moveToNextLocation(reader, creature.location(), creature.velocity(), (byte)0, (byte)0, creature.type(), target, 100L, 0.0f, true, false);
		Assert.assertNull(change);
	}

	@Test
	public void swim()
	{
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 1.1f);
		CreatureEntity creature = _createCow(location);
		AbsoluteLocation target = new AbsoluteLocation(1, 1, 2);
		ViscosityReader reader = _getFixedBlockReader(WATER_SOURCE);
		EntityActionSimpleMove<IMutableCreatureEntity> change = CreatureMovementHelpers.moveToNextLocation(reader, creature.location(), creature.velocity(), (byte)0, (byte)0, creature.type(), target, 100L, 0.5f, true, true);
		Assert.assertNotNull(change);
		Assert.assertTrue(change.getSubAction() instanceof EntityChangeSwim);
	}

	@Test
	public void centreFromBetweenBlocks()
	{
		// Create a cow which is intersecting with a bunch of blocks.
		EntityLocation location = new EntityLocation(1.9f, 1.9f, 1.0f);
		CreatureEntity creature = _createCow(location);
		
		// Now, check that we can handle hints in all 6 directions for centring.
		// NORTH
		AbsoluteLocation directionHint = new AbsoluteLocation(1, 2, 1);
		IEntityAction<IMutableCreatureEntity> change = CreatureMovementHelpers.prepareForMove(creature.location(), creature.velocity(), creature.type(), directionHint, 100L, 0.0f, false);
		// 3 WEST
		Assert.assertNotNull(change);
		
		// EAST
		directionHint = new AbsoluteLocation(2, 1, 1);
		change = CreatureMovementHelpers.prepareForMove(creature.location(), creature.velocity(), creature.type(), directionHint, 100L, 0.0f, false);
		// 3 SOUTH
		Assert.assertNotNull(change);
		
		// SOUTH
		directionHint = new AbsoluteLocation(1, 0, 1);
		change = CreatureMovementHelpers.prepareForMove(creature.location(), creature.velocity(), creature.type(), directionHint, 100L, 0.0f, false);
		// 3 WEST, 4 SOUTH
		Assert.assertNotNull(change);
		
		// WEST
		directionHint = new AbsoluteLocation(0, 1, 1);
		change = CreatureMovementHelpers.prepareForMove(creature.location(), creature.velocity(), creature.type(), directionHint, 100L, 0.0f, false);
		// 4 WEST, 3 SOUTH
		Assert.assertNotNull(change);
		
		// UP
		directionHint = new AbsoluteLocation(1, 1, 2);
		change = CreatureMovementHelpers.prepareForMove(creature.location(), creature.velocity(), creature.type(), directionHint, 100L, 0.0f, false);
		// 3 WEST, 3 SOUTH
		Assert.assertNotNull(change);
		
		// DOWN
		directionHint = new AbsoluteLocation(1, 1, 0);
		change = CreatureMovementHelpers.prepareForMove(creature.location(), creature.velocity(), creature.type(), directionHint, 100L, 0.0f, false);
		// 3 WEST, 3 SOUTH
		Assert.assertNotNull(change);
	}

	@Test
	public void noVelocity()
	{
		// Shows that our motion is straight-forward when starting with no velocity.
		EntityLocation creatureLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		MutableCreature mutable = MutableCreature.existing(_createCow(creatureLocation));
		AbsoluteLocation directionHint = new AbsoluteLocation(1, 0, 0);
		long timeLimitMillis = 50L;
		float viscosityFraction = 0.0f;
		boolean isIdleMovement = true;
		EntityActionSimpleMove<IMutableCreatureEntity> change = CreatureMovementHelpers.prepareForMove(mutable.newLocation, mutable.newVelocity, mutable.newType, directionHint, timeLimitMillis, viscosityFraction, isIdleMovement);
		Assert.assertEquals("SimpleMove(WALKING), by 0.03, 0.00, Sub: null", change.toString());
		
		// Make sure that this actually applies correctly.
		TickProcessingContext context = _buildLayeredContext(timeLimitMillis);
		boolean didApply = change.applyChange(context, mutable);
		Assert.assertTrue(didApply);
		Assert.assertEquals(new EntityLocation(0.03f, 0.0f, 0.0f), mutable.newLocation);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), mutable.newVelocity);
	}

	@Test
	public void reverseDirection()
	{
		// Shows that we don't overrun our movement limits when changing direction.
		EntityLocation creatureLocation = new EntityLocation(143.11f, -106.94f, 0.0f);
		MutableCreature mutable = MutableCreature.existing(_createCow(creatureLocation));
		mutable.newVelocity = new EntityLocation(0.54f, 0.2f, 0.0f);
		AbsoluteLocation directionHint = new AbsoluteLocation(142, -107, 0);
		long timeLimitMillis = 50L;
		float viscosityFraction = 0.0f;
		boolean isIdleMovement = true;
		EntityActionSimpleMove<IMutableCreatureEntity> change = CreatureMovementHelpers.prepareForMove(mutable.newLocation, mutable.newVelocity, mutable.newType, directionHint, timeLimitMillis, viscosityFraction, isIdleMovement);
		Assert.assertEquals("SimpleMove(WALKING), by -0.03, 0.00, Sub: null", change.toString());
		
		// Make sure that this actually applies correctly.
		TickProcessingContext context = _buildLayeredContext(timeLimitMillis);
		boolean didApply = change.applyChange(context, mutable);
		Assert.assertTrue(didApply);
		Assert.assertEquals(new EntityLocation(143.11f, -106.93f, 0.0f), mutable.newLocation);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), mutable.newVelocity);
	}

	@Test
	public void sumDirection()
	{
		// Shows that our motion makes sense when we want to move in the direction of existing velocity.
		EntityLocation creatureLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		MutableCreature mutable = MutableCreature.existing(_createCow(creatureLocation));
		mutable.newVelocity = new EntityLocation(5.0f, 0.0f, 0.0f);
		AbsoluteLocation directionHint = new AbsoluteLocation(1, 0, 0);
		long timeLimitMillis = 50L;
		float viscosityFraction = 0.0f;
		boolean isIdleMovement = true;
		EntityActionSimpleMove<IMutableCreatureEntity> change = CreatureMovementHelpers.prepareForMove(mutable.newLocation, mutable.newVelocity, mutable.newType, directionHint, timeLimitMillis, viscosityFraction, isIdleMovement);
		Assert.assertEquals("SimpleMove(WALKING), by 0.03, 0.00, Sub: null", change.toString());
		
		// Make sure that this actually applies correctly.
		TickProcessingContext context = _buildLayeredContext(timeLimitMillis);
		boolean didApply = change.applyChange(context, mutable);
		Assert.assertTrue(didApply);
		Assert.assertEquals(new EntityLocation(0.25f, 0.0f, 0.0f), mutable.newLocation);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), mutable.newVelocity);
	}

	@Test
	public void coastToLocation()
	{
		// Shows that our motion makes sense when we want to move in the direction of existing velocity.
		ViscosityReader reader = _getFixedBlockReader(ENV.special.AIR);
		EntityLocation creatureLocation = new EntityLocation(0.99f, 0.0f, 0.0f);
		MutableCreature mutable = MutableCreature.existing(_createCow(creatureLocation));
		mutable.newVelocity = new EntityLocation(10.0f, 0.0f, 0.0f);
		AbsoluteLocation directionHint = new AbsoluteLocation(1, 0, 0);
		long timeLimitMillis = 50L;
		float viscosityFraction = 0.0f;
		boolean isIdleMovement = true;
		EntityActionSimpleMove<IMutableCreatureEntity> change = CreatureMovementHelpers.prepareForMove(mutable.newLocation, mutable.newVelocity, mutable.newType, directionHint, timeLimitMillis, viscosityFraction, isIdleMovement);
		Assert.assertNull(change);
		
		boolean isBlockSwimmable = false;
		change = CreatureMovementHelpers.moveToNextLocation(reader
			, mutable.newLocation
			, mutable.newVelocity
			, (byte)0
			, (byte)0
			, mutable.newType
			, directionHint
			, timeLimitMillis
			, viscosityFraction
			, isIdleMovement
			, isBlockSwimmable
		);
		Assert.assertEquals("SimpleMove(WALKING), by -0.03, 0.00, Sub: null", change.toString());
		
		// Make sure that this actually applies correctly.
		TickProcessingContext context = _buildLayeredContext(timeLimitMillis);
		boolean didApply = change.applyChange(context, mutable);
		Assert.assertTrue(didApply);
		Assert.assertEquals(new EntityLocation(1.49f, 0.0f, 0.0f), mutable.newLocation);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), mutable.newVelocity);
	}

	@Test
	public void noJumpWhileRising()
	{
		// We want to show a fix to the bug where we would attempt to jump while rising in water, just because we were block-aligned.
		EntityLocation location = new EntityLocation(-60.41f, -215.41f, -1.0f);
		EntityLocation velocity = new EntityLocation(0.0f, 0.0f, 1.08f);
		MutableCreature mutable = MutableCreature.existing(_createCow(location));
		mutable.newVelocity = velocity;
		CreatureEntity creature = mutable.freeze();
		AbsoluteLocation target = new AbsoluteLocation(-60, -215, 0);
		ViscosityReader reader = _getFixedBlockReader(WATER_SOURCE);
		EntityActionSimpleMove<IMutableCreatureEntity> change = CreatureMovementHelpers.moveToNextLocation(reader, creature.location(), creature.velocity(), (byte)0, (byte)0, creature.type(), target, 100L, 0.0f, true, false);
		Assert.assertNotNull(change);
		Assert.assertNull(change.getSubAction());
		
		CuboidAddress cuboidAddress = location.getBlockLocation().getCuboidAddress();
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(cuboidAddress, WATER_SOURCE);
		TickProcessingContext context = ContextBuilder.build()
			.millisPerTick(50L)
			.lookups(ContextBuilder.buildFetcher((AbsoluteLocation l) -> {
				return (l.getCuboidAddress().equals(cuboidAddress))
					? BlockProxy.load(l.getBlockAddress(), cuboid)
					: null
				;
			}), null, null)
			.finish()
		;
		boolean didApply = change.applyChange(context, mutable);
		Assert.assertTrue(didApply);
		Assert.assertEquals(new EntityLocation(-60.41f, -215.41f, -0.98f), mutable.newLocation);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.3f), mutable.newVelocity);
	}


	private static CreatureEntity _createCow(EntityLocation location)
	{
		return CreatureEntity.create(-1, COW, location, 0L);
	}

	private static ViscosityReader _getFixedBlockReader(Block block)
	{
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), block);
		return new ViscosityReader(ENV, ContextBuilder.buildFetcher((AbsoluteLocation location) -> {
			return BlockProxy.load(location.getBlockAddress(), cuboid);
		}));
	}

	private static ViscosityReader _getSplitBlockReader(Block positive, Block negative)
	{
		CuboidData high = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), positive);
		CuboidData low = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), negative);
		return new ViscosityReader(ENV, ContextBuilder.buildFetcher((AbsoluteLocation location) -> {
			return (location.z() >= 0)
				? BlockProxy.load(location.getBlockAddress(), high)
				: BlockProxy.load(location.getBlockAddress(), low)
			;
		}));
	}

	private static TickProcessingContext _buildLayeredContext(long millisPerTick)
	{
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidData stoneCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), STONE);
		TickProcessingContext context = ContextBuilder.build()
			.millisPerTick(millisPerTick)
			.lookups(ContextBuilder.buildFetcher((AbsoluteLocation location) -> {
				return (location.getCuboidAddress().z() >= 0)
					? BlockProxy.load(location.getBlockAddress(), airCuboid)
					: BlockProxy.load(location.getBlockAddress(), stoneCuboid)
				;
			}), null, null)
			.finish()
		;
		return context;
	}
}
