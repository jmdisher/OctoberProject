package com.jeffdisher.october.logic;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.mutations.EntityChangeJump;
import com.jeffdisher.october.mutations.EntityChangeSwim;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.IMutableCreatureEntity;


public class TestCreatureMovementHelpers
{
	@Test
	public void noMoveCentre()
	{
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 1.0f);
		CreatureEntity creature = _createCow(location);
		IMutationEntity<IMutableCreatureEntity> change = CreatureMovementHelpers.prepareForMove(creature.location(), creature.type(), location.getBlockLocation(), 100L, 0.0f, false);
		Assert.assertNull(change);
		
		location = new EntityLocation(-1.0f, -1.0f, 1.0f);
		creature = _createCow(location);
		change = CreatureMovementHelpers.prepareForMove(creature.location(), creature.type(), location.getBlockLocation(), 100L, 0.0f, false);
		Assert.assertNull(change);
	}

	@Test
	public void multiMoveCentre()
	{
		EntityLocation location = new EntityLocation(1.9f, 1.9f, 1.0f);
		CreatureEntity creature = _createCow(location);
		IMutationEntity<IMutableCreatureEntity> change = CreatureMovementHelpers.prepareForMove(creature.location(), creature.type(), location.getBlockLocation(), 100L, 0.0f, false);
		Assert.assertNotNull(change);
		
		location = new EntityLocation(-1.1f, -1.1f, 1.0f);
		creature = _createCow(location);
		change = CreatureMovementHelpers.prepareForMove(creature.location(), creature.type(), location.getBlockLocation(), 100L, 0.0f, false);
		Assert.assertNotNull(change);
	}

	@Test
	public void walkOne()
	{
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 1.0f);
		CreatureEntity creature = _createCow(location);
		AbsoluteLocation target = new AbsoluteLocation(1, 2, 1);
		IMutationEntity<IMutableCreatureEntity> change = CreatureMovementHelpers.moveToNextLocation(creature.location(), creature.type(), target, 100L, 0.0f, false, false);
		Assert.assertNotNull(change);
	}

	@Test
	public void walkMinusOneDeliberate()
	{
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 1.0f);
		CreatureEntity creature = _createCow(location);
		AbsoluteLocation target = new AbsoluteLocation(0, 1, 1);
		IMutationEntity<IMutableCreatureEntity> change = CreatureMovementHelpers.moveToNextLocation(creature.location(), creature.type(), target, 100L, 0.0f, false, false);
		// Deliberate movement is 0.2/move.
		Assert.assertNotNull(change);
	}

	@Test
	public void walkMinusOneIdle()
	{
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 1.0f);
		CreatureEntity creature = _createCow(location);
		AbsoluteLocation target = new AbsoluteLocation(0, 1, 1);
		IMutationEntity<IMutableCreatureEntity> change = CreatureMovementHelpers.moveToNextLocation(creature.location(), creature.type(), target, 100L, 0.0f, true, false);
		// Idle movement is 0.1/move and we are moving 0.8 (width) plus a fudge factor (which is rounded down, here).
		Assert.assertNotNull(change);
	}

	@Test
	public void jump()
	{
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 1.0f);
		CreatureEntity creature = _createCow(location);
		AbsoluteLocation target = new AbsoluteLocation(1, 1, 2);
		IMutationEntity<IMutableCreatureEntity> change = CreatureMovementHelpers.moveToNextLocation(creature.location(), creature.type(), target, 100L, 0.0f, true, false);
		Assert.assertNotNull(change);
		Assert.assertTrue(change instanceof EntityChangeJump);
	}

	@Test
	public void failJump()
	{
		// In this case, we will still be rising so we want to jump but can't since we aren't on the ground.
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 1.5f);
		CreatureEntity creature = _createCow(location);
		AbsoluteLocation target = new AbsoluteLocation(1, 1, 2);
		IMutationEntity<IMutableCreatureEntity> change = CreatureMovementHelpers.moveToNextLocation(creature.location(), creature.type(), target, 100L, 0.0f, true, false);
		Assert.assertNull(change);
	}

	@Test
	public void walkAirborn()
	{
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 2.2f);
		CreatureEntity creature = _createCow(location);
		AbsoluteLocation target = new AbsoluteLocation(1, 2, 2);
		IMutationEntity<IMutableCreatureEntity> change = CreatureMovementHelpers.moveToNextLocation(creature.location(), creature.type(), target, 100L, 0.0f, false, false);
		Assert.assertNotNull(change);
	}

	@Test
	public void fall()
	{
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 1.0f);
		CreatureEntity creature = _createCow(location);
		AbsoluteLocation target = new AbsoluteLocation(1, 1, 0);
		IMutationEntity<IMutableCreatureEntity> change = CreatureMovementHelpers.moveToNextLocation(creature.location(), creature.type(), target, 100L, 0.0f, true, false);
		Assert.assertNull(change);
	}

	@Test
	public void swim()
	{
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 1.1f);
		CreatureEntity creature = _createCow(location);
		AbsoluteLocation target = new AbsoluteLocation(1, 1, 2);
		IMutationEntity<IMutableCreatureEntity> change = CreatureMovementHelpers.moveToNextLocation(creature.location(), creature.type(), target, 100L, 0.5f, true, true);
		Assert.assertNotNull(change);
		Assert.assertTrue(change instanceof EntityChangeSwim);
	}

	@Test
	public void centreFromBetweenBlocks()
	{
		// Create a cow which is intersecting with a bunch of blocks.
		EntityLocation location = new EntityLocation(1.8f, 1.8f, 1.0f);
		CreatureEntity creature = _createCow(location);
		
		// Now, check that we can handle hints in all 6 directions for centring.
		// NORTH
		AbsoluteLocation directionHint = new AbsoluteLocation(1, 2, 1);
		IMutationEntity<IMutableCreatureEntity> change = CreatureMovementHelpers.prepareForMove(creature.location(), creature.type(), directionHint, 100L, 0.0f, false);
		// 3 WEST
		Assert.assertNotNull(change);
		
		// EAST
		directionHint = new AbsoluteLocation(2, 1, 1);
		change = CreatureMovementHelpers.prepareForMove(creature.location(), creature.type(), directionHint, 100L, 0.0f, false);
		// 3 SOUTH
		Assert.assertNotNull(change);
		
		// SOUTH
		directionHint = new AbsoluteLocation(1, 0, 1);
		change = CreatureMovementHelpers.prepareForMove(creature.location(), creature.type(), directionHint, 100L, 0.0f, false);
		// 3 WEST, 4 SOUTH
		Assert.assertNotNull(change);
		
		// WEST
		directionHint = new AbsoluteLocation(0, 1, 1);
		change = CreatureMovementHelpers.prepareForMove(creature.location(), creature.type(), directionHint, 100L, 0.0f, false);
		// 4 WEST, 3 SOUTH
		Assert.assertNotNull(change);
		
		// UP
		directionHint = new AbsoluteLocation(1, 1, 2);
		change = CreatureMovementHelpers.prepareForMove(creature.location(), creature.type(), directionHint, 100L, 0.0f, false);
		// 3 WEST, 3 SOUTH
		Assert.assertNotNull(change);
		
		// DOWN
		directionHint = new AbsoluteLocation(1, 1, 0);
		change = CreatureMovementHelpers.prepareForMove(creature.location(), creature.type(), directionHint, 100L, 0.0f, false);
		// 3 WEST, 3 SOUTH
		Assert.assertNotNull(change);
	}


	private static CreatureEntity _createCow(EntityLocation location)
	{
		return CreatureEntity.create(-1, EntityType.COW, location, (byte)10);
	}
}
