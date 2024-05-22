package com.jeffdisher.october.logic;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.IMutableMinimalEntity;


public class TestCreatureMovementHelpers
{
	@Test
	public void noMoveCentre()
	{
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 1.0f);
		CreatureEntity creature = new CreatureEntity(1, EntityType.COW, location, 0.0f, (byte)0, 0L, null, null);
		List<IMutationEntity<IMutableMinimalEntity>> list = CreatureMovementHelpers.centreOnCurrentBlock(creature);
		Assert.assertEquals(0, list.size());
		
		location = new EntityLocation(-1.0f, -1.0f, 1.0f);
		creature = new CreatureEntity(1, EntityType.COW, location, 0.0f, (byte)0, 0L, null, null);
		list = CreatureMovementHelpers.centreOnCurrentBlock(creature);
		Assert.assertEquals(0, list.size());
	}

	@Test
	public void multiMoveCentre()
	{
		EntityLocation location = new EntityLocation(1.9f, 1.9f, 1.0f);
		CreatureEntity creature = new CreatureEntity(1, EntityType.COW, location, 0.0f, (byte)0, 0L, null, null);
		List<IMutationEntity<IMutableMinimalEntity>> list = CreatureMovementHelpers.centreOnCurrentBlock(creature);
		Assert.assertEquals(4, list.size());
		
		location = new EntityLocation(-1.1f, -1.1f, 1.0f);
		creature = new CreatureEntity(1, EntityType.COW, location, 0.0f, (byte)0, 0L, null, null);
		list = CreatureMovementHelpers.centreOnCurrentBlock(creature);
		Assert.assertEquals(4, list.size());
	}

	@Test
	public void walkOne()
	{
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 1.0f);
		CreatureEntity creature = new CreatureEntity(1, EntityType.COW, location, 0.0f, (byte)0, 0L, null, null);
		AbsoluteLocation target = new AbsoluteLocation(1, 2, 1);
		List<IMutationEntity<IMutableMinimalEntity>> list = CreatureMovementHelpers.moveToNextLocation(creature, target);
		Assert.assertEquals(3, list.size());
	}

	@Test
	public void walkMinusOne()
	{
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 1.0f);
		CreatureEntity creature = new CreatureEntity(1, EntityType.COW, location, 0.0f, (byte)0, 0L, null, null);
		AbsoluteLocation target = new AbsoluteLocation(0, 1, 1);
		List<IMutationEntity<IMutableMinimalEntity>> list = CreatureMovementHelpers.moveToNextLocation(creature, target);
		Assert.assertEquals(3, list.size());
	}

	@Test
	public void jump()
	{
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 1.0f);
		CreatureEntity creature = new CreatureEntity(1, EntityType.COW, location, 0.0f, (byte)0, 0L, null, null);
		AbsoluteLocation target = new AbsoluteLocation(1, 1, 2);
		List<IMutationEntity<IMutableMinimalEntity>> list = CreatureMovementHelpers.moveToNextLocation(creature, target);
		Assert.assertEquals(1, list.size());
	}

	@Test
	public void failJump()
	{
		// In this case, we will still be rising so we want to jump but can't since we aren't on the ground.
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 1.5f);
		CreatureEntity creature = new CreatureEntity(1, EntityType.COW, location, 0.0f, (byte)0, 0L, null, null);
		AbsoluteLocation target = new AbsoluteLocation(1, 1, 2);
		List<IMutationEntity<IMutableMinimalEntity>> list = CreatureMovementHelpers.moveToNextLocation(creature, target);
		Assert.assertEquals(0, list.size());
	}

	@Test
	public void walkAirborn()
	{
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 2.2f);
		CreatureEntity creature = new CreatureEntity(1, EntityType.COW, location, 0.0f, (byte)0, 0L, null, null);
		AbsoluteLocation target = new AbsoluteLocation(1, 2, 2);
		List<IMutationEntity<IMutableMinimalEntity>> list = CreatureMovementHelpers.moveToNextLocation(creature, target);
		Assert.assertEquals(3, list.size());
	}

	@Test
	public void fall()
	{
		EntityLocation location = new EntityLocation(1.0f, 1.0f, 1.0f);
		CreatureEntity creature = new CreatureEntity(1, EntityType.COW, location, 0.0f, (byte)0, 0L, null, null);
		AbsoluteLocation target = new AbsoluteLocation(1, 1, 0);
		List<IMutationEntity<IMutableMinimalEntity>> list = CreatureMovementHelpers.moveToNextLocation(creature, target);
		Assert.assertEquals(0, list.size());
	}
}
