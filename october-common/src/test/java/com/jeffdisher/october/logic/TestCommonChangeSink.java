package com.jeffdisher.october.logic;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.actions.EntityActionNudge;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutableCreatureEntity;
import com.jeffdisher.october.types.IMutablePlayerEntity;


public class TestCommonChangeSink
{
	@Test
	public void basicCalls()
	{
		Set<Integer> loadedEntities = Set.of(1);
		Set<Integer> loadedCreatures = Set.of(-2);
		CommonChangeSink sink = new CommonChangeSink(loadedEntities, loadedCreatures);
		
		
		EntityActionNudge<IMutablePlayerEntity> next = new EntityActionNudge<>(new EntityLocation(1.0f, 2.0f, 3.0f));
		EntityActionNudge<IMutablePlayerEntity> future = new EntityActionNudge<>(new EntityLocation(1.0f, 2.0f, 3.0f));
		EntityActionNudge<IMutableCreatureEntity> creature = new EntityActionNudge<>(new EntityLocation(1.0f, 2.0f, 3.0f));
		
		sink.future(1, future, 1000L);
		sink.next(1, next);
		sink.creature(-2, creature);
		sink.future(2, new EntityActionNudge<>(new EntityLocation(1.0f, 2.0f, 3.0f)), 1000L);
		sink.next(2, new EntityActionNudge<>(new EntityLocation(1.0f, 2.0f, 3.0f)));
		sink.creature(-1, new EntityActionNudge<>(new EntityLocation(1.0f, 2.0f, 3.0f)));
		
		Map<Integer, List<ScheduledChange>> playerChanges = sink.takeExportedChanges();
		Assert.assertEquals(1, playerChanges.size());
		List<ScheduledChange> changes = playerChanges.get(1);
		Assert.assertEquals(2, changes.size());
		Assert.assertEquals(1000L, changes.get(0).millisUntilReady());
		Assert.assertEquals(future, changes.get(0).change());
		Assert.assertEquals(0L, changes.get(1).millisUntilReady());
		Assert.assertEquals(next, changes.get(1).change());
		
		Map<Integer, List<IEntityAction<IMutableCreatureEntity>>> creatureChanges = sink.takeExportedCreatureChanges();
		Assert.assertEquals(1, creatureChanges.size());
		List<IEntityAction<IMutableCreatureEntity>> actions = creatureChanges.get(-2);
		Assert.assertEquals(1, actions.size());
		Assert.assertEquals(creature, actions.get(0));
	}
}
