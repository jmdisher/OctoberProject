package com.jeffdisher.october.logic;

import java.util.List;

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
		List<IMutationEntity<IMutableCreatureEntity>> list = CreatureMovementHelpers.centreOnCurrentBlock(creature, location.getBlockLocation());
		Assert.assertEquals(0, list.size());
		
		location = new EntityLocation(-1.0f, -1.0f, 1.0f);
		creature = _createCow(location);
		list = CreatureMovementHelpers.centreOnCurrentBlock(creature, location.getBlockLocation());
		Assert.assertEquals(0, list.size());
	}

	@Test
	public void multiMoveCentre()
	{
		EntityLocation location = new EntityLocation(1.9f, 1.9f, 1.0f);
		CreatureEntity creature = _createCow(location);
		List<IMutationEntity<IMutableCreatureEntity>> list = CreatureMovementHelpers.centreOnCurrentBlock(creature, location.getBlockLocation());
		Assert.assertEquals(8, list.size());
		
		location = new EntityLocation(-1.1f, -1.1f, 1.0f);
		creature = _createCow(location);
		list = CreatureMovementHelpers.centreOnCurrentBlock(creature, location.getBlockLocation());
		Assert.assertEquals(8, list.size());
	}

	@Test
	public void walkOne()
	{
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 1.0f);
		CreatureEntity creature = _createCow(location);
		AbsoluteLocation target = new AbsoluteLocation(1, 2, 1);
		List<IMutationEntity<IMutableCreatureEntity>> list = CreatureMovementHelpers.moveToNextLocation(creature, target, false, false);
		Assert.assertEquals(6, list.size());
	}

	@Test
	public void walkMinusOneDeliberate()
	{
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 1.0f);
		CreatureEntity creature = _createCow(location);
		AbsoluteLocation target = new AbsoluteLocation(0, 1, 1);
		List<IMutationEntity<IMutableCreatureEntity>> list = CreatureMovementHelpers.moveToNextLocation(creature, target, false, false);
		// Deliberate movement is 0.2/move.
		Assert.assertEquals(5, list.size());
	}

	@Test
	public void walkMinusOneIdle()
	{
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 1.0f);
		CreatureEntity creature = _createCow(location);
		AbsoluteLocation target = new AbsoluteLocation(0, 1, 1);
		List<IMutationEntity<IMutableCreatureEntity>> list = CreatureMovementHelpers.moveToNextLocation(creature, target, true, false);
		// Idle movement is 0.1/move.
		Assert.assertEquals(9, list.size());
	}

	@Test
	public void jump()
	{
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 1.0f);
		CreatureEntity creature = _createCow(location);
		AbsoluteLocation target = new AbsoluteLocation(1, 1, 2);
		List<IMutationEntity<IMutableCreatureEntity>> list = CreatureMovementHelpers.moveToNextLocation(creature, target, true, false);
		Assert.assertEquals(1, list.size());
		Assert.assertTrue(list.get(0) instanceof EntityChangeJump);
	}

	@Test
	public void failJump()
	{
		// In this case, we will still be rising so we want to jump but can't since we aren't on the ground.
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 1.5f);
		CreatureEntity creature = _createCow(location);
		AbsoluteLocation target = new AbsoluteLocation(1, 1, 2);
		List<IMutationEntity<IMutableCreatureEntity>> list = CreatureMovementHelpers.moveToNextLocation(creature, target, true, false);
		Assert.assertEquals(0, list.size());
	}

	@Test
	public void walkAirborn()
	{
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 2.2f);
		CreatureEntity creature = _createCow(location);
		AbsoluteLocation target = new AbsoluteLocation(1, 2, 2);
		List<IMutationEntity<IMutableCreatureEntity>> list = CreatureMovementHelpers.moveToNextLocation(creature, target, false, false);
		Assert.assertEquals(6, list.size());
	}

	@Test
	public void fall()
	{
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 1.0f);
		CreatureEntity creature = _createCow(location);
		AbsoluteLocation target = new AbsoluteLocation(1, 1, 0);
		List<IMutationEntity<IMutableCreatureEntity>> list = CreatureMovementHelpers.moveToNextLocation(creature, target, true, false);
		Assert.assertEquals(0, list.size());
	}

	@Test
	public void swim()
	{
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 1.1f);
		CreatureEntity creature = _createCow(location);
		AbsoluteLocation target = new AbsoluteLocation(1, 1, 2);
		List<IMutationEntity<IMutableCreatureEntity>> list = CreatureMovementHelpers.moveToNextLocation(creature, target, true, true);
		Assert.assertEquals(1, list.size());
		Assert.assertTrue(list.get(0) instanceof EntityChangeSwim);
	}


	private static CreatureEntity _createCow(EntityLocation location)
	{
		return CreatureEntity.create(-1, EntityType.COW, location, (byte)10);
	}
}
