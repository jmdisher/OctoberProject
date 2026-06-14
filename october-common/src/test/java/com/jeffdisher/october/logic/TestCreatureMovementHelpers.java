package com.jeffdisher.october.logic;

import java.util.List;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.actions.EntityActionSimpleMove;
import com.jeffdisher.october.aspects.AspectRegistry;
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
		IEntityAction<MutableCreature> change = CreatureMovementHelpers.prepareForMove(creature.location(), creature.velocity(), creature.type(), location.getBlockLocation(), 100L, 0.0f, false);
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
		IEntityAction<MutableCreature> change = CreatureMovementHelpers.prepareForMove(creature.location(), creature.velocity(), creature.type(), location.getBlockLocation(), 100L, 0.0f, false);
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
		IEntityAction<MutableCreature> change = CreatureMovementHelpers.moveToNextLocation(reader, creature.location(), creature.velocity(), (byte)0, (byte)0, creature.type(), target, 100L, 0.0f, false, false);
		Assert.assertNotNull(change);
	}

	@Test
	public void walkMinusOneDeliberate()
	{
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 1.0f);
		CreatureEntity creature = _createCow(location);
		AbsoluteLocation target = new AbsoluteLocation(0, 1, 1);
		ViscosityReader reader = _getFixedBlockReader(ENV.special.AIR);
		IEntityAction<MutableCreature> change = CreatureMovementHelpers.moveToNextLocation(reader, creature.location(), creature.velocity(), (byte)0, (byte)0, creature.type(), target, 100L, 0.0f, false, false);
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
		IEntityAction<MutableCreature> change = CreatureMovementHelpers.moveToNextLocation(reader, creature.location(), creature.velocity(), (byte)0, (byte)0, creature.type(), target, 100L, 0.0f, true, false);
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
		EntityActionSimpleMove<MutableCreature> change = CreatureMovementHelpers.moveToNextLocation(reader, creature.location(), creature.velocity(), (byte)0, (byte)0, creature.type(), target, 100L, 0.0f, true, false);
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
		EntityActionSimpleMove<MutableCreature> change = CreatureMovementHelpers.moveToNextLocation(reader, creature.location(), creature.velocity(), (byte)0, (byte)0, creature.type(), target, 100L, 0.0f, true, false);
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
		IEntityAction<MutableCreature> change = CreatureMovementHelpers.moveToNextLocation(reader, creature.location(), creature.velocity(), (byte)0, (byte)0, creature.type(), target, 100L, 0.0f, false, false);
		Assert.assertNotNull(change);
	}

	@Test
	public void fall()
	{
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 1.0f);
		CreatureEntity creature = _createCow(location);
		AbsoluteLocation target = new AbsoluteLocation(1, 1, 0);
		ViscosityReader reader = _getFixedBlockReader(ENV.special.AIR);
		IEntityAction<MutableCreature> change = CreatureMovementHelpers.moveToNextLocation(reader, creature.location(), creature.velocity(), (byte)0, (byte)0, creature.type(), target, 100L, 0.0f, true, false);
		Assert.assertNull(change);
	}

	@Test
	public void swim()
	{
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 1.1f);
		CreatureEntity creature = _createCow(location);
		AbsoluteLocation target = new AbsoluteLocation(1, 1, 2);
		ViscosityReader reader = _getFixedBlockReader(WATER_SOURCE);
		EntityActionSimpleMove<MutableCreature> change = CreatureMovementHelpers.moveToNextLocation(reader, creature.location(), creature.velocity(), (byte)0, (byte)0, creature.type(), target, 100L, 0.5f, true, true);
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
		IEntityAction<MutableCreature> change = CreatureMovementHelpers.prepareForMove(creature.location(), creature.velocity(), creature.type(), directionHint, 100L, 0.0f, false);
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
		EntityActionSimpleMove<MutableCreature> change = CreatureMovementHelpers.prepareForMove(mutable.newLocation, mutable.newVelocity, mutable.newType, directionHint, timeLimitMillis, viscosityFraction, isIdleMovement);
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
		EntityActionSimpleMove<MutableCreature> change = CreatureMovementHelpers.prepareForMove(mutable.newLocation, mutable.newVelocity, mutable.newType, directionHint, timeLimitMillis, viscosityFraction, isIdleMovement);
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
		EntityActionSimpleMove<MutableCreature> change = CreatureMovementHelpers.prepareForMove(mutable.newLocation, mutable.newVelocity, mutable.newType, directionHint, timeLimitMillis, viscosityFraction, isIdleMovement);
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
		EntityActionSimpleMove<MutableCreature> change = CreatureMovementHelpers.prepareForMove(mutable.newLocation, mutable.newVelocity, mutable.newType, directionHint, timeLimitMillis, viscosityFraction, isIdleMovement);
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
		EntityActionSimpleMove<MutableCreature> change = CreatureMovementHelpers.moveToNextLocation(reader, creature.location(), creature.velocity(), (byte)0, (byte)0, creature.type(), target, 100L, 0.0f, true, false);
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

	@Test
	public void walkDiagonalOnFlat()
	{
		EntityLocation location = new EntityLocation(1.1f, 1.2f, 0.0f);
		List<AbsoluteLocation> path = List.of(new AbsoluteLocation(1, 2, 0)
			, new AbsoluteLocation(1, 3, 0)
			, new AbsoluteLocation(1, 4, 0)
			, new AbsoluteLocation(1, 5, 0)
			, new AbsoluteLocation(2, 5, 0)
			, new AbsoluteLocation(3, 5, 0)
			, new AbsoluteLocation(4, 5, 0)
			, new AbsoluteLocation(5, 5, 0)
			, new AbsoluteLocation(5, 5, 1)
		);
		ViscosityReader reader = _getSplitBlockReader(ENV.special.AIR, STONE);
		EntityLocation directLocation = CreatureMovementHelpers.findDirectTarget(reader, location, COW, path);
		Assert.assertEquals(new EntityLocation(5.1f, 5.2f, 0.0f), directLocation);
	}

	@Test
	public void walkDiagonalAroundHole()
	{
		EntityLocation location = new EntityLocation(1.1f, 1.2f, 1.0f);
		List<AbsoluteLocation> path = List.of(new AbsoluteLocation(1, 2, 1)
			, new AbsoluteLocation(1, 3, 1)
			, new AbsoluteLocation(1, 4, 1)
			, new AbsoluteLocation(1, 5, 1)
			, new AbsoluteLocation(2, 5, 1)
			, new AbsoluteLocation(3, 5, 1)
			, new AbsoluteLocation(4, 5, 1)
			, new AbsoluteLocation(5, 5, 1)
		);
		
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidGenerator.fillPlane(cuboid, (byte)0, STONE);
		cuboid.setData15(AspectRegistry.BLOCK, new AbsoluteLocation(3, 5, 0).getBlockAddress(), (short)0);
		
		ViscosityReader reader = new ViscosityReader(ENV, ContextBuilder.buildFetcher((AbsoluteLocation l) -> {
			return BlockProxy.load(l.getBlockAddress(), cuboid);
		}));
		EntityLocation directLocation = CreatureMovementHelpers.findDirectTarget(reader, location, COW, path);
		Assert.assertEquals(new EntityLocation(2.1f, 5.2f, 1.0f), directLocation);
	}

	@Test
	public void directWalkNoMove()
	{
		EntityLocation location = new EntityLocation(1.02f, 1.2f, 1.0f);
		EntityLocation end = new EntityLocation(1.0f, 1.21f, 1.0f);
		long millisPerTick = 50L;
		
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidGenerator.fillPlane(cuboid, (byte)0, STONE);
		
		ViscosityReader reader = new ViscosityReader(ENV, ContextBuilder.buildFetcher((AbsoluteLocation l) -> {
			return BlockProxy.load(l.getBlockAddress(), cuboid);
		}));
		EntityActionSimpleMove<MutableCreature> change = CreatureMovementHelpers.moveAlongDiagonalPath(reader
			, location
			, (byte)5
			, (byte)6
			, COW
			, end
			, millisPerTick
			, 0.0f
			, false
		);
		Assert.assertNull(change);
	}

	@Test
	public void directWalkCollide()
	{
		EntityLocation location = new EntityLocation(1.00f, 1.2f, 1.0f);
		EntityLocation end = new EntityLocation(0.98f, 1.21f, 1.0f);
		long millisPerTick = 50L;
		
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidGenerator.fillPlane(cuboid, (byte)0, STONE);
		cuboid.setData15(AspectRegistry.BLOCK, end.getBlockLocation().getBlockAddress(), STONE.item().number());
		
		ViscosityReader reader = new ViscosityReader(ENV, ContextBuilder.buildFetcher((AbsoluteLocation l) -> {
			return BlockProxy.load(l.getBlockAddress(), cuboid);
		}));
		EntityActionSimpleMove<MutableCreature> change = CreatureMovementHelpers.moveAlongDiagonalPath(reader
			, location
			, (byte)5
			, (byte)6
			, COW
			, end
			, millisPerTick
			, 0.0f
			, false
		);
		Assert.assertNull(change);
	}

	@Test
	public void directWalkFall()
	{
		EntityLocation location = new EntityLocation(1.99f, 1.99f, 1.0f);
		EntityLocation end = new EntityLocation(2.01f, 2.01f, 1.0f);
		long millisPerTick = 50L;
		
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, location.getBlockLocation().getRelative(0, 0, -1).getBlockAddress(), STONE.item().number());
		
		ViscosityReader reader = new ViscosityReader(ENV, ContextBuilder.buildFetcher((AbsoluteLocation l) -> {
			return BlockProxy.load(l.getBlockAddress(), cuboid);
		}));
		EntityActionSimpleMove<MutableCreature> change = CreatureMovementHelpers.moveAlongDiagonalPath(reader
			, location
			, (byte)5
			, (byte)6
			, COW
			, end
			, millisPerTick
			, 0.0f
			, false
		);
		Assert.assertNull(change);
	}

	@Test
	public void directWalkShort()
	{
		EntityLocation location = new EntityLocation(1.00f, 1.2f, 1.0f);
		EntityLocation end = new EntityLocation(0.98f, 1.21f, 1.0f);
		long millisPerTick = 50L;
		
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidGenerator.fillPlane(cuboid, (byte)0, STONE);
		
		ViscosityReader reader = new ViscosityReader(ENV, ContextBuilder.buildFetcher((AbsoluteLocation l) -> {
			return BlockProxy.load(l.getBlockAddress(), cuboid);
		}));
		EntityActionSimpleMove<MutableCreature> change = CreatureMovementHelpers.moveAlongDiagonalPath(reader
			, location
			, (byte)5
			, (byte)6
			, COW
			, end
			, millisPerTick
			, 0.0f
			, false
		);
		Assert.assertEquals("SimpleMove(WALKING), by -0.02, 0.01, Sub: null", change.toString());
		
		// Make sure that this actually applies correctly.
		TickProcessingContext context = ContextBuilder.build()
			.millisPerTick(millisPerTick)
			.lookups(ContextBuilder.buildFetcher(
				(AbsoluteLocation l) -> BlockProxy.load(l.getBlockAddress(), cuboid)
			), null, null)
			.finish()
		;
		MutableCreature mutable = MutableCreature.existing(_createCow(location));
		boolean didApply = change.applyChange(context, mutable);
		Assert.assertTrue(didApply);
		Assert.assertEquals(end, mutable.newLocation);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), mutable.newVelocity);
	}

	@Test
	public void directWalkLong()
	{
		EntityLocation location = new EntityLocation(1.00f, 1.2f, 1.0f);
		EntityLocation end = new EntityLocation(-2.5f, 3.21f, 1.0f);
		long millisPerTick = 50L;
		
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidGenerator.fillPlane(cuboid, (byte)0, STONE);
		
		ViscosityReader reader = new ViscosityReader(ENV, ContextBuilder.buildFetcher((AbsoluteLocation l) -> {
			return BlockProxy.load(l.getBlockAddress(), cuboid);
		}));
		EntityActionSimpleMove<MutableCreature> change = CreatureMovementHelpers.moveAlongDiagonalPath(reader
			, location
			, (byte)5
			, (byte)6
			, COW
			, end
			, millisPerTick
			, 0.0f
			, false
		);
		Assert.assertEquals("SimpleMove(WALKING), by -0.04, 0.02, Sub: null", change.toString());
		
		// Make sure that this actually applies correctly.
		TickProcessingContext context = ContextBuilder.build()
			.millisPerTick(millisPerTick)
			.lookups(ContextBuilder.buildFetcher(
				(AbsoluteLocation l) -> BlockProxy.load(l.getBlockAddress(), cuboid)
			), null, null)
			.finish()
		;
		MutableCreature mutable = MutableCreature.existing(_createCow(location));
		boolean didApply = change.applyChange(context, mutable);
		Assert.assertTrue(didApply);
		Assert.assertEquals(new EntityLocation(0.96f, 1.22f, 1.0f), mutable.newLocation);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), mutable.newVelocity);
	}

	@Test
	public void walkDirectly()
	{
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 1.0f);
		EntityLocation target = new EntityLocation(1.5f, 2.2f, 1.0f);
		
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidGenerator.fillPlane(cuboid, (byte)0, STONE);
		ViscosityReader reader = new ViscosityReader(ENV, ContextBuilder.buildFetcher((AbsoluteLocation l) -> {
			return BlockProxy.load(l.getBlockAddress(), cuboid);
		}));
		
		byte yaw = OrientationHelpers.getYawBetweenPoints(location, target);
		EntityActionSimpleMove<MutableCreature> change = CreatureMovementHelpers.moveAlongDiagonalPath(reader
			, location
			, yaw
			, (byte)6
			, COW
			, target
			, 100L
			, 0.5f
			, false
		);
		// Ideally, this would be 0.04 x 0.09 since that is still within speed limit but our rounding limiter causes this to be lower.
		Assert.assertEquals("SimpleMove(WALKING), by 0.03, 0.09, Sub: null", change.toString());
	}

	@Test
	public void walkCompare()
	{
		// Compare moveToNextLocation and moveAlongDiagonalPath.
		EntityLocation location = new EntityLocation(1.2f, 1.2f, 0.0f);
		EntityLocation velocity = new EntityLocation(0.0f, 0.0f, 0.0f);
		ViscosityReader reader = _getSplitBlockReader(ENV.special.AIR, STONE);
		byte yaw = 0;
		byte pitch = 0;
		float airViscosity = 0.0f;
		float waterViscosity = 0.5f;
		EntityLocation east = location.getRelative(2.5f, 0.0f, 0.0f);
		EntityLocation west = location.getRelative(-2.5f, 0.0f, 0.0f);
		
		// Check movement through air.
		EntityActionSimpleMove<MutableCreature> next = CreatureMovementHelpers.moveToNextLocation(reader, location, velocity, yaw, pitch, COW, east.getBlockLocation(), 100L, airViscosity, false, false);
		EntityActionSimpleMove<MutableCreature> diagonal = CreatureMovementHelpers.moveAlongDiagonalPath(reader, location, yaw, pitch, COW, east, 100L, airViscosity, false);
		Assert.assertEquals("SimpleMove(WALKING), by 0.10, 0.00, Sub: null", next.toString());
		Assert.assertEquals(next.toString(), diagonal.toString());
		
		next = CreatureMovementHelpers.moveToNextLocation(reader, location, velocity, yaw, pitch, COW, west.getBlockLocation(), 100L, airViscosity, false, false);
		diagonal = CreatureMovementHelpers.moveAlongDiagonalPath(reader, location, yaw, pitch, COW, west, 100L, airViscosity, false);
		Assert.assertEquals("SimpleMove(WALKING), by -0.10, 0.00, Sub: null", next.toString());
		Assert.assertEquals(next.toString(), diagonal.toString());
		
		// Check movement through water.
		next = CreatureMovementHelpers.moveToNextLocation(reader, location, velocity, yaw, pitch, COW, east.getBlockLocation(), 100L, waterViscosity, false, false);
		diagonal = CreatureMovementHelpers.moveAlongDiagonalPath(reader, location, yaw, pitch, COW, east, 100L, waterViscosity, false);
		Assert.assertEquals("SimpleMove(WALKING), by 0.10, 0.00, Sub: null", next.toString());
		Assert.assertEquals(next.toString(), diagonal.toString());
		
		next = CreatureMovementHelpers.moveToNextLocation(reader, location, velocity, yaw, pitch, COW, west.getBlockLocation(), 100L, waterViscosity, false, false);
		diagonal = CreatureMovementHelpers.moveAlongDiagonalPath(reader, location, yaw, pitch, COW, west, 100L, waterViscosity, false);
		Assert.assertEquals("SimpleMove(WALKING), by -0.10, 0.00, Sub: null", next.toString());
		Assert.assertEquals(next.toString(), diagonal.toString());
		
		// Check a close location in water.
		EntityLocation closeLocation = new EntityLocation(1.95f, 1.2f, 0.0f);
		EntityLocation closeEast = closeLocation.getRelative(0.05f, 0.0f, 0.0f);
		next = CreatureMovementHelpers.moveToNextLocation(reader, closeLocation, velocity, yaw, pitch, COW, closeEast.getBlockLocation(), 100L, waterViscosity, false, false);
		diagonal = CreatureMovementHelpers.moveAlongDiagonalPath(reader, closeLocation, yaw, pitch, COW, closeEast, 100L, waterViscosity, false);
		Assert.assertEquals("SimpleMove(WALKING), by 0.10, 0.00, Sub: null", next.toString());
		Assert.assertEquals(next.toString(), diagonal.toString());
	}

	@Test
	public void directWalkRoundingLimit()
	{
		// Show how we avoid overflowing our speed limit.
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 1.0f);
		EntityLocation end = new EntityLocation(5.0f, 2.8f, 1.0f);
		long millisPerTick = 50L;
		
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidGenerator.fillPlane(cuboid, (byte)0, STONE);
		
		ViscosityReader reader = new ViscosityReader(ENV, ContextBuilder.buildFetcher((AbsoluteLocation l) -> {
			return BlockProxy.load(l.getBlockAddress(), cuboid);
		}));
		EntityActionSimpleMove<MutableCreature> change = CreatureMovementHelpers.moveAlongDiagonalPath(reader
			, location
			, (byte)5
			, (byte)6
			, COW
			, end
			, millisPerTick
			, 0.0f
			, false
		);
		// Without applying this limit, the rounding gives us 0.05 x 0.02, which is beyond the fudge factor over 0.05.
		Assert.assertEquals("SimpleMove(WALKING), by 0.04, 0.02, Sub: null", change.toString());
		
		// Make sure that this actually applies correctly.
		TickProcessingContext context = ContextBuilder.build()
			.millisPerTick(millisPerTick)
			.lookups(ContextBuilder.buildFetcher(
				(AbsoluteLocation l) -> BlockProxy.load(l.getBlockAddress(), cuboid)
			), null, null)
			.finish()
		;
		MutableCreature mutable = MutableCreature.existing(_createCow(location));
		boolean didApply = change.applyChange(context, mutable);
		Assert.assertTrue(didApply);
		Assert.assertEquals(new EntityLocation(1.04f, 1.02f, 1.0f), mutable.newLocation);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), mutable.newVelocity);
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
