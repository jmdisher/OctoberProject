package com.jeffdisher.october.creatures;

import java.util.List;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.actions.EntityActionSimpleMove;
import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.ViscosityReader;
import com.jeffdisher.october.pathing.PathLocation;
import com.jeffdisher.october.pathing.WalkingNavigator;
import com.jeffdisher.october.subactions.EntitySubActionJump;
import com.jeffdisher.october.subactions.EntitySubActionSwim;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.ContextBuilder;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.MutableCreature;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestCreatureMovementHelpers
{
	private static Environment ENV;
	private static EntityType COW;
	private static EntityType ORC;
	private static Block STONE;
	private static Block WATER_SOURCE;
	@BeforeClass
	public static void setup() throws Throwable
	{
		ENV = Environment.createSharedInstance();
		COW = ENV.creatures.getTypeById("op.cow");
		ORC = ENV.creatures.getTypeById("op.orc");
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
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidGenerator.fillPlane(cuboid, (byte)0, STONE);
		TickProcessingContext.IBlockFetcher previousBlockLookUp = ContextBuilder.buildSingleFetcher(cuboid);
		
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 1.0f);
		CreatureEntity creature = _createCow(location);
		IEntityAction<MutableCreature> change = CreatureMovementHelpers.prepareForMove(previousBlockLookUp, creature.location(), creature.velocity(), creature.type(), FacingDirection.DOWN, 100L, EntityActionSimpleMove.Intensity.WALKING);
		Assert.assertNull(change);
		
		cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(-1, -1, 0), ENV.special.AIR);
		CuboidGenerator.fillPlane(cuboid, (byte)0, STONE);
		previousBlockLookUp = ContextBuilder.buildSingleFetcher(cuboid);
		
		location = new EntityLocation(-10.0f, -10.0f, 1.0f);
		creature = _createCow(location);
		change = CreatureMovementHelpers.prepareForMove(previousBlockLookUp, creature.location(), creature.velocity(), creature.type(), FacingDirection.DOWN, 100L, EntityActionSimpleMove.Intensity.WALKING);
		Assert.assertNull(change);
	}

	@Test
	public void multiMoveCentre()
	{
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidGenerator.fillPlane(cuboid, (byte)0, STONE);
		TickProcessingContext.IBlockFetcher previousBlockLookUp = ContextBuilder.buildSingleFetcher(cuboid);
		
		EntityLocation location = new EntityLocation(1.9f, 1.9f, 1.0f);
		CreatureEntity creature = _createCow(location);
		IEntityAction<MutableCreature> change = CreatureMovementHelpers.prepareForMove(previousBlockLookUp, creature.location(), creature.velocity(), creature.type(), FacingDirection.DOWN, 100L, EntityActionSimpleMove.Intensity.WALKING);
		Assert.assertNotNull(change);
		
		cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(-1, -1, 0), ENV.special.AIR);
		CuboidGenerator.fillPlane(cuboid, (byte)0, STONE);
		previousBlockLookUp = ContextBuilder.buildSingleFetcher(cuboid);
		
		location = new EntityLocation(-10.1f, -10.1f, 1.0f);
		creature = _createCow(location);
		change = CreatureMovementHelpers.prepareForMove(previousBlockLookUp, creature.location(), creature.velocity(), creature.type(), FacingDirection.DOWN, 100L, EntityActionSimpleMove.Intensity.WALKING);
		Assert.assertNotNull(change);
	}

	@Test
	public void walkOne()
	{
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 1.0f);
		CreatureEntity creature = _createCow(location);
		PathLocation target = PathLocation.test_raw(new AbsoluteLocation(1, 2, 1), 0, 0, 0);
		ViscosityReader reader = _getFixedBlockReader(ENV.special.AIR);
		IEntityAction<MutableCreature> change = CreatureMovementHelpers.moveToNextLocation(reader, creature.location(), creature.velocity(), (byte)0, (byte)0, creature.type(), target, null, 100L, 0.0f, EntityActionSimpleMove.Intensity.WALKING, false);
		Assert.assertNotNull(change);
	}

	@Test
	public void walkMinusOneDeliberate()
	{
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 1.0f);
		CreatureEntity creature = _createCow(location);
		PathLocation target = PathLocation.test_raw(new AbsoluteLocation(0, 1, 1), 0, 0, 0);
		ViscosityReader reader = _getFixedBlockReader(ENV.special.AIR);
		IEntityAction<MutableCreature> change = CreatureMovementHelpers.moveToNextLocation(reader, creature.location(), creature.velocity(), (byte)0, (byte)0, creature.type(), target, null, 100L, 0.0f, EntityActionSimpleMove.Intensity.WALKING, false);
		// Deliberate movement is 0.2/move.
		Assert.assertNotNull(change);
	}

	@Test
	public void walkMinusOneIdle()
	{
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 1.0f);
		CreatureEntity creature = _createCow(location);
		PathLocation target = PathLocation.test_raw(new AbsoluteLocation(0, 1, 1), 0, 0, 0);
		ViscosityReader reader = _getFixedBlockReader(ENV.special.AIR);
		IEntityAction<MutableCreature> change = CreatureMovementHelpers.moveToNextLocation(reader, creature.location(), creature.velocity(), (byte)0, (byte)0, creature.type(), target, null, 100L, 0.0f, EntityActionSimpleMove.Intensity.IDLE_CREATURE, false);
		// Idle movement is 0.1/move and we are moving 0.8 (width) plus a fudge factor (which is rounded down, here).
		Assert.assertNotNull(change);
	}

	@Test
	public void jump()
	{
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 0.0f);
		CreatureEntity creature = _createCow(location);
		PathLocation target = PathLocation.test_raw(new AbsoluteLocation(1, 1, 1), 0, 0, 1);
		PathLocation target2 = PathLocation.test_raw(new AbsoluteLocation(1, 1, 2), 0, 0, 0);
		ViscosityReader reader = _getSplitBlockReader(ENV.special.AIR, STONE);
		EntityActionSimpleMove<MutableCreature> change = CreatureMovementHelpers.moveToNextLocation(reader, creature.location(), creature.velocity(), (byte)0, (byte)0, creature.type(), target, target2, 100L, 0.0f, EntityActionSimpleMove.Intensity.IDLE_CREATURE, false);
		Assert.assertNotNull(change);
		Assert.assertTrue(change.getSubAction() instanceof EntitySubActionJump);
	}

	@Test
	public void failJump()
	{
		// In this case, we will still be rising so we want to jump but can't since we aren't on the ground.
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 1.5f);
		CreatureEntity creature = _createCow(location);
		PathLocation target = PathLocation.test_raw(new AbsoluteLocation(1, 1, 2), 0, 0, 0);
		ViscosityReader reader = _getFixedBlockReader(ENV.special.AIR);
		EntityActionSimpleMove<MutableCreature> change = CreatureMovementHelpers.moveToNextLocation(reader, creature.location(), creature.velocity(), (byte)0, (byte)0, creature.type(), target, null, 100L, 0.0f, EntityActionSimpleMove.Intensity.IDLE_CREATURE, false);
		Assert.assertNull(change);
	}

	@Test
	public void walkAirborn()
	{
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 2.2f);
		CreatureEntity creature = _createCow(location);
		PathLocation target = PathLocation.test_raw(new AbsoluteLocation(1, 1, 2), 0, 1, 0);
		ViscosityReader reader = _getFixedBlockReader(ENV.special.AIR);
		IEntityAction<MutableCreature> change = CreatureMovementHelpers.moveToNextLocation(reader, creature.location(), creature.velocity(), (byte)0, (byte)0, creature.type(), target, null, 100L, 0.0f, EntityActionSimpleMove.Intensity.WALKING, false);
		Assert.assertNotNull(change);
	}

	@Test
	public void fall()
	{
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 1.0f);
		CreatureEntity creature = _createCow(location);
		PathLocation target = PathLocation.test_raw(new AbsoluteLocation(1, 1, 0), 0, 0, 1);
		ViscosityReader reader = _getFixedBlockReader(ENV.special.AIR);
		IEntityAction<MutableCreature> change = CreatureMovementHelpers.moveToNextLocation(reader, creature.location(), creature.velocity(), (byte)0, (byte)0, creature.type(), target, null, 100L, 0.0f, EntityActionSimpleMove.Intensity.IDLE_CREATURE, false);
		Assert.assertNull(change);
	}

	@Test
	public void swim()
	{
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 1.1f);
		CreatureEntity creature = _createCow(location);
		PathLocation startLocation = PathLocation.forEntityBase(location);
		PathLocation target = startLocation.getRelative(0, 0, 1);
		PathLocation target2 = startLocation.getRelative(0, 0, 2);
		ViscosityReader reader = _getFixedBlockReader(WATER_SOURCE);
		EntityActionSimpleMove<MutableCreature> change = CreatureMovementHelpers.moveToNextLocation(reader, creature.location(), creature.velocity(), (byte)0, (byte)0, creature.type(), target, target2, 100L, 0.5f, EntityActionSimpleMove.Intensity.IDLE_CREATURE, true);
		Assert.assertNotNull(change);
		Assert.assertTrue(change.getSubAction() instanceof EntitySubActionSwim);
	}

	@Test
	public void centreFromBetweenBlocks()
	{
		// Create a cow which is intersecting with a bunch of blocks.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidGenerator.fillPlane(cuboid, (byte)0, STONE);
		TickProcessingContext.IBlockFetcher previousBlockLookUp = ContextBuilder.buildSingleFetcher(cuboid);
		
		EntityLocation location = new EntityLocation(1.9f, 1.9f, 1.0f);
		CreatureEntity creature = _createCow(location);
		
		// Now, check that we can handle hints in all 6 directions for centring.
		// NORTH
		FacingDirection directionHint = FacingDirection.NORTH;
		IEntityAction<MutableCreature> change = CreatureMovementHelpers.prepareForMove(previousBlockLookUp, creature.location(), creature.velocity(), creature.type(), directionHint, 100L, EntityActionSimpleMove.Intensity.WALKING);
		// 3 WEST
		Assert.assertNotNull(change);
		
		// EAST
		directionHint = FacingDirection.EAST;
		change = CreatureMovementHelpers.prepareForMove(previousBlockLookUp, creature.location(), creature.velocity(), creature.type(), directionHint, 100L, EntityActionSimpleMove.Intensity.WALKING);
		// 3 SOUTH
		Assert.assertNotNull(change);
		
		// SOUTH
		directionHint = FacingDirection.SOUTH;
		change = CreatureMovementHelpers.prepareForMove(previousBlockLookUp, creature.location(), creature.velocity(), creature.type(), directionHint, 100L, EntityActionSimpleMove.Intensity.WALKING);
		// 3 WEST, 4 SOUTH
		Assert.assertNotNull(change);
		
		// WEST
		directionHint = FacingDirection.WEST;
		change = CreatureMovementHelpers.prepareForMove(previousBlockLookUp, creature.location(), creature.velocity(), creature.type(), directionHint, 100L, EntityActionSimpleMove.Intensity.WALKING);
		// 4 WEST, 3 SOUTH
		Assert.assertNotNull(change);
		
		// UP
		directionHint = FacingDirection.UP;
		change = CreatureMovementHelpers.prepareForMove(previousBlockLookUp, creature.location(), creature.velocity(), creature.type(), directionHint, 100L, EntityActionSimpleMove.Intensity.WALKING);
		// 3 WEST, 3 SOUTH
		Assert.assertNotNull(change);
		
		// DOWN
		directionHint = FacingDirection.DOWN;
		change = CreatureMovementHelpers.prepareForMove(previousBlockLookUp, creature.location(), creature.velocity(), creature.type(), directionHint, 100L, EntityActionSimpleMove.Intensity.WALKING);
		// 3 WEST, 3 SOUTH
		Assert.assertNotNull(change);
	}

	@Test
	public void noVelocity()
	{
		// Shows that our motion is straight-forward when starting with no velocity.
		long timeLimitMillis = 50L;
		TickProcessingContext context = _buildLayeredContext(timeLimitMillis);
		TickProcessingContext.IBlockFetcher previousBlockLookUp = context.previousBlockLookUp;
		
		EntityLocation creatureLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		MutableCreature mutable = MutableCreature.existing(_createCow(creatureLocation));
		FacingDirection directionHint = FacingDirection.EAST;
		EntityActionSimpleMove<MutableCreature> change = CreatureMovementHelpers.prepareForMove(previousBlockLookUp, mutable.newLocation, mutable.newVelocity, mutable.newType, directionHint, timeLimitMillis, EntityActionSimpleMove.Intensity.IDLE_CREATURE);
		Assert.assertEquals("SimpleMove(IDLE_CREATURE), by 0.03, 0.00, Sub: null", change.toString());
		
		// Make sure that this actually applies correctly.
		boolean didApply = change.applyChange(context, mutable);
		Assert.assertTrue(didApply);
		Assert.assertEquals(new EntityLocation(0.03f, 0.0f, 0.0f), mutable.newLocation);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), mutable.newVelocity);
	}

	@Test
	public void reverseDirection()
	{
		// Shows that we don't overrun our movement limits when changing direction.
		long timeLimitMillis = 50L;
		TickProcessingContext context = _buildLayeredContext(timeLimitMillis);
		TickProcessingContext.IBlockFetcher previousBlockLookUp = context.previousBlockLookUp;
		
		EntityLocation creatureLocation = new EntityLocation(143.11f, -106.94f, 0.0f);
		MutableCreature mutable = MutableCreature.existing(_createCow(creatureLocation));
		mutable.newVelocity = new EntityLocation(0.54f, 0.2f, 0.0f);
		FacingDirection directionHint = FacingDirection.WEST;
		EntityActionSimpleMove<MutableCreature> change = CreatureMovementHelpers.prepareForMove(previousBlockLookUp, mutable.newLocation, mutable.newVelocity, mutable.newType, directionHint, timeLimitMillis, EntityActionSimpleMove.Intensity.IDLE_CREATURE);
		Assert.assertEquals("SimpleMove(IDLE_CREATURE), by -0.03, 0.00, Sub: null", change.toString());
		
		// Make sure that this actually applies correctly.
		boolean didApply = change.applyChange(context, mutable);
		Assert.assertTrue(didApply);
		Assert.assertEquals(new EntityLocation(143.11f, -106.93f, 0.0f), mutable.newLocation);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), mutable.newVelocity);
	}

	@Test
	public void sumDirection()
	{
		// Shows that our motion makes sense when we want to move in the direction of existing velocity.
		long timeLimitMillis = 50L;
		TickProcessingContext context = _buildLayeredContext(timeLimitMillis);
		TickProcessingContext.IBlockFetcher previousBlockLookUp = context.previousBlockLookUp;
		
		EntityLocation creatureLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		MutableCreature mutable = MutableCreature.existing(_createCow(creatureLocation));
		mutable.newVelocity = new EntityLocation(5.0f, 0.0f, 0.0f);
		FacingDirection directionHint = FacingDirection.EAST;
		EntityActionSimpleMove<MutableCreature> change = CreatureMovementHelpers.prepareForMove(previousBlockLookUp, mutable.newLocation, mutable.newVelocity, mutable.newType, directionHint, timeLimitMillis, EntityActionSimpleMove.Intensity.IDLE_CREATURE);
		Assert.assertEquals("SimpleMove(IDLE_CREATURE), by 0.03, 0.00, Sub: null", change.toString());
		
		// Make sure that this actually applies correctly.
		boolean didApply = change.applyChange(context, mutable);
		Assert.assertTrue(didApply);
		Assert.assertEquals(new EntityLocation(0.25f, 0.0f, 0.0f), mutable.newLocation);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), mutable.newVelocity);
	}

	@Test
	public void coastToLocation()
	{
		// Shows that our motion makes sense when we want to move in the direction of existing velocity.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidGenerator.fillPlane(cuboid, (byte)0, STONE);
		TickProcessingContext.IBlockFetcher previousBlockLookUp = ContextBuilder.buildSingleFetcher(cuboid);
		
		ViscosityReader reader = new ViscosityReader(ENV, previousBlockLookUp);
		EntityLocation creatureLocation = new EntityLocation(0.99f, 0.0f, 0.0f);
		MutableCreature mutable = MutableCreature.existing(_createCow(creatureLocation));
		mutable.newVelocity = new EntityLocation(10.0f, 0.0f, 0.0f);
		PathLocation target = PathLocation.test_raw(new AbsoluteLocation(1, 0, 0), 0, 0, 0);
		FacingDirection directionHint = PathLocation.adjacentDirection(PathLocation.forEntityBase(creatureLocation), target);
		Assert.assertEquals(FacingDirection.EAST, directionHint);
		long timeLimitMillis = 50L;
		float viscosityFraction = 0.0f;
		EntityActionSimpleMove<MutableCreature> change = CreatureMovementHelpers.prepareForMove(previousBlockLookUp, mutable.newLocation, mutable.newVelocity, mutable.newType, directionHint, timeLimitMillis, EntityActionSimpleMove.Intensity.IDLE_CREATURE);
		Assert.assertNull(change);
		
		boolean isBlockSwimmable = false;
		change = CreatureMovementHelpers.moveToNextLocation(reader
			, mutable.newLocation
			, mutable.newVelocity
			, (byte)0
			, (byte)0
			, mutable.newType
			, target
			, null
			, timeLimitMillis
			, viscosityFraction
			, EntityActionSimpleMove.Intensity.IDLE_CREATURE
			, isBlockSwimmable
		);
		Assert.assertEquals("SimpleMove(IDLE_CREATURE), by -0.03, 0.00, Sub: null", change.toString());
		
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
		PathLocation target = PathLocation.test_raw(new AbsoluteLocation(-61, -216, -1), 1, 1, 1);
		ViscosityReader reader = _getFixedBlockReader(WATER_SOURCE);
		long millisPerTick = 50L;
		EntityActionSimpleMove<MutableCreature> change = CreatureMovementHelpers.moveToNextLocation(reader, creature.location(), creature.velocity(), (byte)0, (byte)0, creature.type(), target, null, millisPerTick, 0.0f, EntityActionSimpleMove.Intensity.IDLE_CREATURE, false);
		Assert.assertNull(change);
	}

	@Test
	public void walkDiagonalOnFlat()
	{
		EntityLocation location = new EntityLocation(1.1f, 1.2f, 0.0f);
		PathLocation pathBase = PathLocation.test_raw(new AbsoluteLocation(1, 1, 0), 0, 1, 0);
		List<PathLocation> path = List.of(pathBase
			, pathBase.getRelative(0, 1, 0)
			, pathBase.getRelative(0, 2, 0)
			, pathBase.getRelative(0, 3, 0)
			, pathBase.getRelative(0, 4, 0)
			, pathBase.getRelative(0, 5, 0)
			, pathBase.getRelative(0, 6, 0)
			, pathBase.getRelative(0, 7, 0)
			, pathBase.getRelative(1, 7, 0)
			, pathBase.getRelative(2, 7, 0)
			, pathBase.getRelative(3, 7, 0)
			, pathBase.getRelative(4, 7, 0)
			, pathBase.getRelative(5, 7, 0)
			, pathBase.getRelative(6, 7, 0)
			, pathBase.getRelative(7, 7, 0)
			, pathBase.getRelative(8, 7, 0)
			, pathBase.getRelative(8, 7, 1)
			, pathBase.getRelative(8, 7, 2)
		);
		ViscosityReader reader = _getSplitBlockReader(ENV.special.AIR, STONE);
		EntityLocation directLocation = CreatureMovementHelpers.findDirectTarget(reader, location, COW, path);
		Assert.assertEquals(new EntityLocation(5.1f, 5.2f, 0.0f), directLocation);
	}

	@Test
	public void walkDiagonalAroundHole()
	{
		EntityLocation location = new EntityLocation(1.1f, 1.2f, 1.0f);
		PathLocation pathBase = PathLocation.test_raw(new AbsoluteLocation(1, 1, 1), 0, 1, 0);
		List<PathLocation> path = List.of(pathBase
			, pathBase.getRelative(0, 1, 0)
			, pathBase.getRelative(0, 2, 0)
			, pathBase.getRelative(0, 3, 0)
			, pathBase.getRelative(0, 4, 0)
			, pathBase.getRelative(0, 5, 0)
			, pathBase.getRelative(0, 6, 0)
			, pathBase.getRelative(0, 7, 0)
			, pathBase.getRelative(1, 7, 0)
			, pathBase.getRelative(2, 7, 0)
			, pathBase.getRelative(3, 7, 0)
			, pathBase.getRelative(4, 7, 0)
			, pathBase.getRelative(5, 7, 0)
			, pathBase.getRelative(6, 7, 0)
			, pathBase.getRelative(7, 7, 0)
			, pathBase.getRelative(8, 7, 0)
		);
		
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidGenerator.fillPlane(cuboid, (byte)0, STONE);
		cuboid.setData15(AspectRegistry.BLOCK, new AbsoluteLocation(3, 5, 0).getBlockAddress(), (short)0);
		
		ViscosityReader reader = new ViscosityReader(ENV, ContextBuilder.buildSingleFetcher(cuboid));
		EntityLocation directLocation = CreatureMovementHelpers.findDirectTarget(reader, location, COW, path);
		Assert.assertEquals(new EntityLocation(2.6f, 5.2f, 1.0f), directLocation);
	}

	@Test
	public void directWalkSmallDistance()
	{
		// Show that diagonal walk doesn't stop just because we enter the same block.
		EntityLocation location = new EntityLocation(1.02f, 1.2f, 1.0f);
		EntityLocation end = new EntityLocation(1.0f, 1.21f, 1.0f);
		long millisPerTick = 50L;
		
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidGenerator.fillPlane(cuboid, (byte)0, STONE);
		
		EntityActionSimpleMove<MutableCreature> shortMove = CreatureMovementHelpers.moveAlongDiagonalPath(ContextBuilder.buildSingleFetcher(cuboid)
			, location
			, COW
			, end
			, millisPerTick
			, EntityActionSimpleMove.Intensity.WALKING
		);
		Assert.assertEquals("SimpleMove(WALKING), by -0.02, 0.01, Sub: null", shortMove.toString());
	}

	@Test
	public void directWalkNoDistance()
	{
		// The diagonal walk will return null if the start and end of the walk equal.
		EntityLocation location = new EntityLocation(1.02f, 1.2f, 1.0f);
		long millisPerTick = 50L;
		
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidGenerator.fillPlane(cuboid, (byte)0, STONE);
		
		EntityActionSimpleMove<MutableCreature> noMove = CreatureMovementHelpers.moveAlongDiagonalPath(ContextBuilder.buildSingleFetcher(cuboid)
			, location
			, COW
			, location
			, millisPerTick
			, EntityActionSimpleMove.Intensity.WALKING
		);
		Assert.assertNull(noMove);
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
		
		EntityActionSimpleMove<MutableCreature> change = CreatureMovementHelpers.moveAlongDiagonalPath(ContextBuilder.buildSingleFetcher(cuboid)
			, location
			, COW
			, end
			, millisPerTick
			, EntityActionSimpleMove.Intensity.WALKING
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
		
		EntityActionSimpleMove<MutableCreature> change = CreatureMovementHelpers.moveAlongDiagonalPath(ContextBuilder.buildSingleFetcher(cuboid)
			, location
			, COW
			, end
			, millisPerTick
			, EntityActionSimpleMove.Intensity.WALKING
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
		
		EntityActionSimpleMove<MutableCreature> change = CreatureMovementHelpers.moveAlongDiagonalPath(ContextBuilder.buildSingleFetcher(cuboid)
			, location
			, COW
			, end
			, millisPerTick
			, EntityActionSimpleMove.Intensity.WALKING
		);
		Assert.assertEquals("SimpleMove(WALKING), by -0.02, 0.01, Sub: null", change.toString());
		
		// Make sure that this actually applies correctly.
		TickProcessingContext context = ContextBuilder.build()
			.millisPerTick(millisPerTick)
			.lookups(ContextBuilder.buildSingleFetcher(cuboid), null, null)
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
		
		EntityActionSimpleMove<MutableCreature> change = CreatureMovementHelpers.moveAlongDiagonalPath(ContextBuilder.buildSingleFetcher(cuboid)
			, location
			, COW
			, end
			, millisPerTick
			, EntityActionSimpleMove.Intensity.WALKING
		);
		Assert.assertEquals("SimpleMove(WALKING), by -0.04, 0.02, Sub: null", change.toString());
		
		// Make sure that this actually applies correctly.
		TickProcessingContext context = ContextBuilder.build()
			.millisPerTick(millisPerTick)
			.lookups(ContextBuilder.buildSingleFetcher(cuboid), null, null)
			.finish()
		;
		MutableCreature mutable = MutableCreature.existing(_createCow(location));
		boolean didApply = change.applyChange(context, mutable);
		Assert.assertTrue(didApply);
		Assert.assertEquals(new EntityLocation(0.96f, 1.23f, 1.0f), mutable.newLocation);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), mutable.newVelocity);
	}

	@Test
	public void walkDirectly()
	{
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 1.0f);
		EntityLocation target = new EntityLocation(1.5f, 2.2f, 1.0f);
		
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidGenerator.fillPlane(cuboid, (byte)0, STONE);
		EntityActionSimpleMove<MutableCreature> change = CreatureMovementHelpers.moveAlongDiagonalPath(ContextBuilder.buildSingleFetcher(cuboid)
			, location
			, COW
			, target
			, 100L
			, EntityActionSimpleMove.Intensity.WALKING
		);
		Assert.assertEquals("SimpleMove(WALKING), by 0.04, 0.09, Sub: null", change.toString());
	}

	@Test
	public void walkCompare()
	{
		// Compare moveToNextLocation and moveAlongDiagonalPath.
		EntityLocation location = new EntityLocation(1.2f, 1.2f, 0.0f);
		EntityLocation velocity = new EntityLocation(0.0f, 0.0f, 0.0f);
		TickProcessingContext.IBlockFetcher airFetcher = _buildSplitFetcher(ENV.special.AIR, STONE);
		TickProcessingContext.IBlockFetcher waterFetcher = _buildSplitFetcher(WATER_SOURCE, STONE);
		ViscosityReader airReader = new ViscosityReader(ENV, airFetcher);
		ViscosityReader waterReader = new ViscosityReader(ENV, waterFetcher);
		byte yaw = 0;
		byte pitch = 0;
		float airViscosity = 0.0f;
		float waterViscosity = 0.5f;
		EntityLocation east = location.getRelative(2.5f, 0.0f, 0.0f);
		EntityLocation west = location.getRelative(-2.5f, 0.0f, 0.0f);
		PathLocation pathEast = PathLocation.forEntityBase(east);
		PathLocation pathWest = PathLocation.forEntityBase(west);
		
		// Check movement through air.
		EntityActionSimpleMove<MutableCreature> next = CreatureMovementHelpers.moveToNextLocation(airReader, location, velocity, yaw, pitch, COW, pathEast, null, 100L, airViscosity, EntityActionSimpleMove.Intensity.WALKING, false);
		EntityActionSimpleMove<MutableCreature> diagonal = CreatureMovementHelpers.moveAlongDiagonalPath(airFetcher, location, COW, east, 100L, EntityActionSimpleMove.Intensity.WALKING);
		Assert.assertEquals("SimpleMove(WALKING), by 0.10, 0.00, Sub: null", next.toString());
		Assert.assertEquals(next.toString(), diagonal.toString());
		
		next = CreatureMovementHelpers.moveToNextLocation(airReader, location, velocity, yaw, pitch, COW, pathWest, null, 100L, airViscosity, EntityActionSimpleMove.Intensity.WALKING, false);
		diagonal = CreatureMovementHelpers.moveAlongDiagonalPath(airFetcher, location, COW, west, 100L, EntityActionSimpleMove.Intensity.WALKING);
		Assert.assertEquals("SimpleMove(WALKING), by -0.10, 0.00, Sub: null", next.toString());
		Assert.assertEquals(next.toString(), diagonal.toString());
		
		// Check movement through water.
		next = CreatureMovementHelpers.moveToNextLocation(waterReader, location, velocity, yaw, pitch, COW, pathEast, null, 100L, waterViscosity, EntityActionSimpleMove.Intensity.WALKING, false);
		diagonal = CreatureMovementHelpers.moveAlongDiagonalPath(waterFetcher, location, COW, east, 100L, EntityActionSimpleMove.Intensity.WALKING);
		Assert.assertEquals("SimpleMove(WALKING), by 0.10, 0.00, Sub: null", next.toString());
		Assert.assertEquals(next.toString(), diagonal.toString());
		
		next = CreatureMovementHelpers.moveToNextLocation(waterReader, location, velocity, yaw, pitch, COW, pathWest, null, 100L, waterViscosity, EntityActionSimpleMove.Intensity.WALKING, false);
		diagonal = CreatureMovementHelpers.moveAlongDiagonalPath(waterFetcher, location, COW, west, 100L, EntityActionSimpleMove.Intensity.WALKING);
		Assert.assertEquals("SimpleMove(WALKING), by -0.10, 0.00, Sub: null", next.toString());
		Assert.assertEquals(next.toString(), diagonal.toString());
		
		// Check a close location in water.
		EntityLocation closeLocation = new EntityLocation(1.95f, 1.2f, 0.0f);
		EntityLocation closeEast = closeLocation.getRelative(0.05f, 0.0f, 0.0f);
		PathLocation pathCloseEast = PathLocation.forEntityBase(closeEast);
		next = CreatureMovementHelpers.moveToNextLocation(waterReader, closeLocation, velocity, yaw, pitch, COW, pathCloseEast, null, 100L, waterViscosity, EntityActionSimpleMove.Intensity.WALKING, false);
		diagonal = CreatureMovementHelpers.moveAlongDiagonalPath(waterFetcher, closeLocation, COW, closeEast, 100L, EntityActionSimpleMove.Intensity.WALKING);
		Assert.assertEquals("SimpleMove(WALKING), by 0.10, 0.00, Sub: null", next.toString());
		Assert.assertEquals(next.toString(), diagonal.toString());
	}

	@Test
	public void moveStepUp()
	{
		Block stair = ENV.blocks.fromItem(ENV.items.getItemById("op.stone_brick_stair"));
		Block slab = ENV.blocks.fromItem(ENV.items.getItemById("op.stone_brick_slab"));
		AbsoluteLocation centre = new AbsoluteLocation(10, 10, 1);
		AbsoluteLocation slabBlock = centre.getRelative(-1, 0, 0);
		AbsoluteLocation stairBlock = centre.getRelative(1, 0, 0);
		
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		CuboidGenerator.fillPlane(cuboid, (byte)0, STONE);
		cuboid.setData15(AspectRegistry.BLOCK, slabBlock.getBlockAddress(), slab.item().number());
		cuboid.setData7(AspectRegistry.ORIENTATION, slabBlock.getBlockAddress(), FacingDirection.directionToByte(FacingDirection.DOWN));
		cuboid.setData15(AspectRegistry.BLOCK, stairBlock.getBlockAddress(), stair.item().number());
		cuboid.setData7(AspectRegistry.ORIENTATION, stairBlock.getBlockAddress(), FacingDirection.directionToByte(FacingDirection.EAST));
		
		ViscosityReader reader = new ViscosityReader(ENV, ContextBuilder.buildSingleFetcher(cuboid));
		EntityType player = ENV.creatures.PLAYER;
		EntityLocation velocity = new EntityLocation(0.0f, 0.0f, 0.0f);
		byte yaw = 0;
		byte pitch = 0;
		
		EntityLocation slabStart = centre.toEntityLocation();
		PathLocation slabStartLocation = PathLocation.forEntityBase(slabStart);
		PathLocation slabUpLocation = slabStartLocation.getRelative(0, 0, 1);
		PathLocation slabOverLocation = slabStartLocation.getRelative(-1, 0, 1);
		EntityActionSimpleMove<MutableCreature> slabMove = CreatureMovementHelpers.moveToNextLocation(reader, slabStart, velocity, yaw, pitch, player, slabUpLocation, slabOverLocation, 100L, 0.0f, EntityActionSimpleMove.Intensity.WALKING, false);
		Assert.assertEquals("SimpleMove(WALKING), by -0.40, 0.00, Sub: Step-up", slabMove.toString());
		
		float offset = player.volume().width() + 0.01f;
		EntityLocation stairStart = centre.toEntityLocation().getRelative(1.0f-offset, 1.0f-offset, 0.0f);
		PathLocation stairStartLocation = PathLocation.forEntityBase(stairStart);
		PathLocation stairUpLocation = stairStartLocation.getRelative(0, 0, 1);
		PathLocation stairOverLocation = stairStartLocation.getRelative(1, 0, 1);
		EntityActionSimpleMove<MutableCreature> stairMove = CreatureMovementHelpers.moveToNextLocation(reader, stairStart, velocity, yaw, pitch, player, stairUpLocation, stairOverLocation, 100L, 0.0f, EntityActionSimpleMove.Intensity.WALKING, false);
		Assert.assertEquals("SimpleMove(WALKING), by 0.40, 0.00, Sub: Step-up", stairMove.toString());
	}

	@Test
	public void avoidDrowning()
	{
		// Verify that idle movement will avoid stopping in the water (these should all jump).
		EntityLocation entityLocation = new EntityLocation(16.0f, 16.0f, 1.0f);
		CreatureEntity entity = CreatureEntity.create(-1, ORC, entityLocation, 0L);
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		CuboidGenerator.fillPlane(input, (byte)0, STONE);
		CuboidGenerator.fillPlane(input, (byte)1, WATER_SOURCE);
		
		
		// We should see 13 possible locations, all at z-level 2 as their final destination.
		for (int i = 0; i < 13; ++i)
		{
			TickProcessingContext context = _createContext(input, i);
			WalkingNavigator navigator = new WalkingNavigator(context.previousBlockLookUp
				, entity.type().volume()
				, CreatureMovementHelpers.RANDOM_MOVEMENT_DISTANCE
				, true
				, true
				, true
				, true
			);
			List<PathLocation> path = CreatureMovementHelpers.findPathToRandomSpot(context, navigator, entity.location());
			PathLocation target = path.get(path.size() - 1);
			Assert.assertEquals(2.0f, target.getBaseLocation().z(), 0.01f);
		}
		// Verify that there are only 13.
		try
		{
			TickProcessingContext context = _createContext(input, 13);
			WalkingNavigator navigator = new WalkingNavigator(context.previousBlockLookUp
				, entity.type().volume()
				, CreatureMovementHelpers.RANDOM_MOVEMENT_DISTANCE
				, true
				, true
				, true
				, true
			);
			CreatureMovementHelpers.findPathToRandomSpot(context, navigator, entity.location());
		}
		catch (AssertionError e)
		{
			// Expected from test.
		}
	}

	@Test
	public void idleNoPath()
	{
		// Verify that we will choose to do nothing if there are no idle movement targets.
		EntityLocation entityLocation = new EntityLocation(16.0f, 16.0f, 1.0f);
		CreatureEntity entity = CreatureEntity.create(-1, ORC, entityLocation, 0L);
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		short stoneNumber = ENV.items.getItemById("op.stone").number();
		CuboidGenerator.fillPlane(input, (byte)0, STONE);
		CuboidGenerator.fillPlane(input, (byte)1, STONE);
		CuboidGenerator.fillPlane(input, (byte)2, STONE);
		input.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(16, 16, 1), stoneNumber);
		input.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(16, 16, 2), stoneNumber);
		
		// We should see 0 possible locations.
		TickProcessingContext context = _createContext(input, 0);
		WalkingNavigator navigator = new WalkingNavigator(context.previousBlockLookUp
			, entity.type().volume()
			, CreatureMovementHelpers.RANDOM_MOVEMENT_DISTANCE
			, true
			, true
			, true
			, true
		);
		List<PathLocation> path = CreatureMovementHelpers.findPathToRandomSpot(context, navigator, entity.location());
		Assert.assertNull(path);
	}

	@Test
	public void idleWithoutJumping()
	{
		// Verify that all possible idle paths end on the ground.
		EntityLocation entityLocation = new EntityLocation(16.0f, 16.0f, 1.0f);
		CreatureEntity entity = CreatureEntity.create(-1, ORC, entityLocation, 0L);
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		CuboidGenerator.fillPlane(input, (byte)0, STONE);
		
		// We should see 24 possible locations, all at z-level 1 as their final destination, minus where we started.
		for (int i = 0; i < 24; ++i)
		{
			TickProcessingContext context = _createContext(input, i);
			WalkingNavigator navigator = new WalkingNavigator(context.previousBlockLookUp
				, entity.type().volume()
				, CreatureMovementHelpers.RANDOM_MOVEMENT_DISTANCE
				, true
				, true
				, true
				, true
			);
			List<PathLocation> path = CreatureMovementHelpers.findPathToRandomSpot(context, navigator, entity.location());
			PathLocation target = path.get(path.size() - 1);
			Assert.assertEquals(1.0f, target.getBaseLocation().z(), 0.01f);
		}
		// Verify that there are only 24.
		try
		{
			TickProcessingContext context = _createContext(input, 24);
			WalkingNavigator navigator = new WalkingNavigator(context.previousBlockLookUp
				, entity.type().volume()
				, CreatureMovementHelpers.RANDOM_MOVEMENT_DISTANCE
				, true
				, true
				, true
				, true
			);
			CreatureMovementHelpers.findPathToRandomSpot(context, navigator, entity.location());
		}
		catch (AssertionError e)
		{
			// Expected from test.
		}
	}

	@Test
	public void findStepLocation()
	{
		// Just some basic tests around findStepLocation.
		EntityLocation creatureLocation = new EntityLocation(2.3f, 4.55f, 1.0f);
		float width = 0.6f;
		PathLocation targetBlock = PathLocation.test_raw(new AbsoluteLocation(1, 4, 1), 1, 1, 0);
		Assert.assertEquals(1, PathLocation.pathStepDistance(targetBlock, PathLocation.forEntityBase(creatureLocation)));
		
		FacingDirection direction = FacingDirection.WEST;
		EntityLocation result = CreatureMovementHelpers.findStepLocation(creatureLocation
			, width
			, targetBlock
			, direction
		);
		Assert.assertEquals(new EntityLocation(1.51f, 4.55f, 1.0f), result);
		Assert.assertEquals(targetBlock, PathLocation.forEntityBase(result));
		
		direction = FacingDirection.EAST;
		result = CreatureMovementHelpers.findStepLocation(creatureLocation
			, width
			, targetBlock
			, direction
		);
		Assert.assertEquals(new EntityLocation(2.3f, 4.55f, 1.0f), result);
		
		direction = FacingDirection.SOUTH;
		result = CreatureMovementHelpers.findStepLocation(creatureLocation
			, width
			, targetBlock
			, direction
		);
		Assert.assertEquals(new EntityLocation(1.5f, 4.51f, 1.0f), result);
		Assert.assertEquals(targetBlock, PathLocation.forEntityBase(result));
		
		direction = FacingDirection.NORTH;
		result = CreatureMovementHelpers.findStepLocation(creatureLocation
			, width
			, targetBlock
			, direction
		);
		Assert.assertEquals(new EntityLocation(1.5f, 4.89f, 1.0f), result);
		Assert.assertEquals(targetBlock, PathLocation.forEntityBase(result));
		
		width = 1.8f;
		direction = FacingDirection.WEST;
		result = CreatureMovementHelpers.findStepLocation(creatureLocation
			, width
			, targetBlock
			, direction
		);
		Assert.assertEquals(new EntityLocation(1.51f, 4.55f, 1.0f), result);
		Assert.assertEquals(targetBlock, PathLocation.forEntityBase(result));
		
		direction = FacingDirection.EAST;
		result = CreatureMovementHelpers.findStepLocation(creatureLocation
			, width
			, targetBlock
			, direction
		);
		Assert.assertEquals(new EntityLocation(2.3f, 4.55f, 1.0f), result);
		
		direction = FacingDirection.SOUTH;
		result = CreatureMovementHelpers.findStepLocation(creatureLocation
			, width
			, targetBlock
			, direction
		);
		Assert.assertEquals(new EntityLocation(1.5f, 4.51f, 1.0f), result);
		Assert.assertEquals(targetBlock, PathLocation.forEntityBase(result));
		
		direction = FacingDirection.NORTH;
		result = CreatureMovementHelpers.findStepLocation(creatureLocation
			, width
			, targetBlock
			, direction
		);
		Assert.assertEquals(new EntityLocation(1.5f, 4.69f, 1.0f), result);
		Assert.assertEquals(targetBlock, PathLocation.forEntityBase(result));
	}

	@Test
	public void noRedundantStepLocations()
	{
		// Show that findStepLocation doesn't change the location of the creature if is already in the correct direction from the target block.
		EntityLocation centreLocation = new EntityLocation(2.3f, 4.55f, 1.0f);
		EntityLocation westLocation = centreLocation.getRelative(-2.0f, 0.0f, 0.0f);
		EntityLocation eastLocation = centreLocation.getRelative(2.0f, 0.0f, 0.0f);
		EntityLocation southLocation = centreLocation.getRelative(0.0f, -2.0f, 0.0f);
		EntityLocation northLocation = centreLocation.getRelative(0.0f, 2.0f, 0.0f);
		
		float width = 0.6f;
		PathLocation centreBlock = PathLocation.forEntityBase(centreLocation);
		
		EntityLocation result = CreatureMovementHelpers.findStepLocation(westLocation
			, width
			, centreBlock
			, FacingDirection.WEST
		);
		Assert.assertEquals(westLocation, result);
		
		result = CreatureMovementHelpers.findStepLocation(eastLocation
			, width
			, centreBlock
			, FacingDirection.EAST
		);
		Assert.assertEquals(eastLocation, result);
		
		result = CreatureMovementHelpers.findStepLocation(southLocation
			, width
			, centreBlock
			, FacingDirection.SOUTH
		);
		Assert.assertEquals(southLocation, result);
		
		result = CreatureMovementHelpers.findStepLocation(northLocation
			, width
			, centreBlock
			, FacingDirection.NORTH
		);
		Assert.assertEquals(northLocation, result);
	}


	private static CreatureEntity _createCow(EntityLocation location)
	{
		return CreatureEntity.create(-1, COW, location, 0L);
	}

	private static ViscosityReader _getFixedBlockReader(Block block)
	{
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), block);
		return new ViscosityReader(ENV, ContextBuilder.buildSingleFetcher(cuboid));
	}

	private static ViscosityReader _getSplitBlockReader(Block positive, Block negative)
	{
		TickProcessingContext.IBlockFetcher fecther = _buildSplitFetcher(positive, negative);
		return new ViscosityReader(ENV, fecther);
	}

	private static TickProcessingContext.IBlockFetcher _buildSplitFetcher(Block positive, Block negative)
	{
		CuboidData high = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), positive);
		CuboidData low = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), negative);
		return ContextBuilder.buildFunctionFetcher((CuboidAddress address) -> {
			return (address.z() >= 0)
				? high
				: low
			;
		});
	}

	private static TickProcessingContext _buildLayeredContext(long millisPerTick)
	{
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidData stoneCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), STONE);
		TickProcessingContext context = ContextBuilder.build()
			.millisPerTick(millisPerTick)
			.lookups(ContextBuilder.buildFunctionFetcher((CuboidAddress address) -> {
				return (address.z() >= 0)
					? airCuboid
					: stoneCuboid
				;
			}), null, null)
			.finish()
		;
		return context;
	}

	private static TickProcessingContext _createContext(IReadOnlyCuboidData cuboid, int random)
	{
		TickProcessingContext.IBlockFetcher previousBlockLookUp = ContextBuilder.buildSingleFetcher(cuboid);
		TickProcessingContext context = ContextBuilder.build()
			.lookups(previousBlockLookUp, null, null)
			.fixedRandom(random)
			.finish()
		;
		return context;
	}
}
