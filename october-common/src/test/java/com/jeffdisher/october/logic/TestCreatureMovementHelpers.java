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
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutableCreatureEntity;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestCreatureMovementHelpers
{
	private static Environment ENV;
	private static EntityType COW;
	private static Block WATER_SOURCE;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		COW = ENV.creatures.getTypeById("op.cow");
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
		ViscosityReader reader = _getFixedBlockReader(ENV.special.AIR);
		IEntityAction<IMutableCreatureEntity> change = CreatureMovementHelpers.prepareForMove(reader, creature.location(), creature.velocity(), creature.type(), location.getBlockLocation(), 100L, 0.0f, false);
		Assert.assertNull(change);
		
		location = new EntityLocation(-1.0f, -1.0f, 1.0f);
		creature = _createCow(location);
		change = CreatureMovementHelpers.prepareForMove(reader, creature.location(), creature.velocity(), creature.type(), location.getBlockLocation(), 100L, 0.0f, false);
		Assert.assertNull(change);
	}

	@Test
	public void multiMoveCentre()
	{
		EntityLocation location = new EntityLocation(1.9f, 1.9f, 1.0f);
		CreatureEntity creature = _createCow(location);
		ViscosityReader reader = _getFixedBlockReader(ENV.special.AIR);
		IEntityAction<IMutableCreatureEntity> change = CreatureMovementHelpers.prepareForMove(reader, creature.location(), creature.velocity(), creature.type(), location.getBlockLocation(), 100L, 0.0f, false);
		Assert.assertNotNull(change);
		
		location = new EntityLocation(-1.1f, -1.1f, 1.0f);
		creature = _createCow(location);
		change = CreatureMovementHelpers.prepareForMove(reader, creature.location(), creature.velocity(), creature.type(), location.getBlockLocation(), 100L, 0.0f, false);
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
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 1.0f);
		CreatureEntity creature = _createCow(location);
		AbsoluteLocation target = new AbsoluteLocation(1, 1, 2);
		ViscosityReader reader = _getFixedBlockReader(ENV.special.AIR);
		EntityActionSimpleMove<IMutableCreatureEntity> change = CreatureMovementHelpers.moveToNextLocation(reader, creature.location(), creature.velocity(), (byte)0, (byte)0, creature.type(), target, 100L, 0.0f, true, false);
		Assert.assertNotNull(change);
		Assert.assertTrue(change.test_getSubAction() instanceof EntityChangeJump);
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
		Assert.assertNull(change.test_getSubAction());
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
		Assert.assertTrue(change.test_getSubAction() instanceof EntityChangeSwim);
	}

	@Test
	public void centreFromBetweenBlocks()
	{
		// Create a cow which is intersecting with a bunch of blocks.
		EntityLocation location = new EntityLocation(1.8f, 1.8f, 1.0f);
		CreatureEntity creature = _createCow(location);
		ViscosityReader reader = _getFixedBlockReader(ENV.special.AIR);
		
		// Now, check that we can handle hints in all 6 directions for centring.
		// NORTH
		AbsoluteLocation directionHint = new AbsoluteLocation(1, 2, 1);
		IEntityAction<IMutableCreatureEntity> change = CreatureMovementHelpers.prepareForMove(reader, creature.location(), creature.velocity(), creature.type(), directionHint, 100L, 0.0f, false);
		// 3 WEST
		Assert.assertNotNull(change);
		
		// EAST
		directionHint = new AbsoluteLocation(2, 1, 1);
		change = CreatureMovementHelpers.prepareForMove(reader, creature.location(), creature.velocity(), creature.type(), directionHint, 100L, 0.0f, false);
		// 3 SOUTH
		Assert.assertNotNull(change);
		
		// SOUTH
		directionHint = new AbsoluteLocation(1, 0, 1);
		change = CreatureMovementHelpers.prepareForMove(reader, creature.location(), creature.velocity(), creature.type(), directionHint, 100L, 0.0f, false);
		// 3 WEST, 4 SOUTH
		Assert.assertNotNull(change);
		
		// WEST
		directionHint = new AbsoluteLocation(0, 1, 1);
		change = CreatureMovementHelpers.prepareForMove(reader, creature.location(), creature.velocity(), creature.type(), directionHint, 100L, 0.0f, false);
		// 4 WEST, 3 SOUTH
		Assert.assertNotNull(change);
		
		// UP
		directionHint = new AbsoluteLocation(1, 1, 2);
		change = CreatureMovementHelpers.prepareForMove(reader, creature.location(), creature.velocity(), creature.type(), directionHint, 100L, 0.0f, false);
		// 3 WEST, 3 SOUTH
		Assert.assertNotNull(change);
		
		// DOWN
		directionHint = new AbsoluteLocation(1, 1, 0);
		change = CreatureMovementHelpers.prepareForMove(reader, creature.location(), creature.velocity(), creature.type(), directionHint, 100L, 0.0f, false);
		// 3 WEST, 3 SOUTH
		Assert.assertNotNull(change);
	}


	private static CreatureEntity _createCow(EntityLocation location)
	{
		return CreatureEntity.create(-1, COW, location, (byte)10);
	}

	private static ViscosityReader _getFixedBlockReader(Block block)
	{
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), block);
		return new ViscosityReader(ENV, (AbsoluteLocation location) -> {
			return new BlockProxy(location.getBlockAddress(), cuboid);
		});
	}
}
