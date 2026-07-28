package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.actions.EntityActionNudge;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.types.ContextBuilder;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.IPassiveAction;
import com.jeffdisher.october.types.MutableCreature;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;


public class TestNudgeHelpers
{
	private static Environment ENV;
	private static EntityType COW;
	@BeforeClass
	public static void setup() throws Throwable
	{
		ENV = Environment.createSharedInstance();
		COW = ENV.creatures.getTypeById("op.cow");
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void zeroNudgeAsPlayer()
	{
		// Show that we don't generate nudge actions for the cases where we colliding but not within each others' width radii.
		int playerId = 1;
		int playerInRange = 2;
		int playerOutOfRange = 3;
		int creatureInRange = -1;
		int creatureOutOfRange = -2;
		int creatureFarOutOfRange = -3;
		List<EntityActionNudge<IMutablePlayerEntity>> outPlayerChanges = new ArrayList<>();
		List<EntityActionNudge<MutableCreature>> outCreatureChanges = new ArrayList<>();
		TickProcessingContext context = ContextBuilder.build()
			.tick(19L)
			.sinks(null, new TickProcessingContext.IChangeSink() {
				@Override
				public boolean next(int targetEntityId, IEntityAction<IMutablePlayerEntity> change)
				{
					Assert.assertEquals(playerInRange, targetEntityId);
					outPlayerChanges.add((EntityActionNudge<IMutablePlayerEntity>) change);
					return true;
				}
				@Override
				public boolean future(int targetEntityId, IEntityAction<IMutablePlayerEntity> change, long millisToDelay)
				{
					throw new AssertionError("Not in test");
				}
				@Override
				public boolean creature(int targetCreatureId, IEntityAction<MutableCreature> change)
				{
					Assert.assertEquals(creatureInRange, targetCreatureId);
					outCreatureChanges.add((EntityActionNudge<MutableCreature>) change);
					return true;
				}
				@Override
				public boolean passive(int targetPassiveId, IPassiveAction action)
				{
					throw new AssertionError("Not in test");
				}
			})
			.finish()
		;
		
		Map<Integer, Entity> players = Map.of(playerId, _buildPlayer(playerId, new EntityLocation(1.0f, -1.0f, 1.0f))
			, playerInRange, _buildPlayer(playerInRange, new EntityLocation(1.1f, -1.1f, 1.0f))
			, playerOutOfRange, _buildPlayer(playerOutOfRange, new EntityLocation(1.3f, -1.3f, 1.0f))
		);
		Map<Integer, CreatureEntity> creatures = Map.of(creatureInRange, _buildCreature(creatureInRange, new EntityLocation(1.1f, -1.1f, 1.0f))
			, creatureOutOfRange, _buildCreature(creatureOutOfRange, new EntityLocation(1.4f, -2.2f, 1.0f))
			, creatureFarOutOfRange, _buildCreature(creatureFarOutOfRange, new EntityLocation(-10.0f, 1.0f, 1.0f))
		);
		EntityCollection collection = EntityCollection.fromMaps(players, creatures);
		NudgeHelpers.nudgeAsPlayer(ENV, context, collection, players.get(playerId));
		
		// We nudge by the maximum radius, so we reach the cow from a greater distance.
		Assert.assertEquals(1, outPlayerChanges.size());
		Assert.assertEquals(1, outCreatureChanges.size());
	}

	@Test
	public void zeroNudgeAsCreature()
	{
		// Show that we don't generate nudge actions for the cases where we colliding but not within each others' width radii.
		int playerInRange = 1;
		int playerOutOfRange = 2;
		int playerFarOutOfRange = 3;
		int creatureId = -1;
		int creatureInRange = -2;
		int creatureOutOfRange = -3;
		List<EntityActionNudge<IMutablePlayerEntity>> outPlayerChanges = new ArrayList<>();
		List<EntityActionNudge<MutableCreature>> outCreatureChanges = new ArrayList<>();
		TickProcessingContext context = ContextBuilder.build()
			.tick(99L)
			.sinks(null, new TickProcessingContext.IChangeSink() {
				@Override
				public boolean next(int targetEntityId, IEntityAction<IMutablePlayerEntity> change)
				{
					Assert.assertEquals(playerInRange, targetEntityId);
					outPlayerChanges.add((EntityActionNudge<IMutablePlayerEntity>) change);
					return true;
				}
				@Override
				public boolean future(int targetEntityId, IEntityAction<IMutablePlayerEntity> change, long millisToDelay)
				{
					throw new AssertionError("Not in test");
				}
				@Override
				public boolean creature(int targetCreatureId, IEntityAction<MutableCreature> change)
				{
					Assert.assertEquals(creatureInRange, targetCreatureId);
					outCreatureChanges.add((EntityActionNudge<MutableCreature>) change);
					return true;
				}
				@Override
				public boolean passive(int targetPassiveId, IPassiveAction action)
				{
					throw new AssertionError("Not in test");
				}
			})
			.finish()
		;
		
		Map<Integer, Entity> players = Map.of(playerInRange, _buildPlayer(playerInRange, new EntityLocation(1.1f, -1.1f, 1.0f))
			, playerOutOfRange, _buildPlayer(playerOutOfRange, new EntityLocation(2.2f, 0.2f, 1.0f))
			, playerFarOutOfRange, _buildPlayer(playerFarOutOfRange, new EntityLocation(10.0f, -13.0f, 1.0f))
		);
		Map<Integer, CreatureEntity> creatures = Map.of(creatureId, _buildCreature(creatureId, new EntityLocation(1.0f, -1.0f, 1.0f))
			, creatureInRange, _buildCreature(creatureInRange, new EntityLocation(1.1f, -1.1f, 1.0f))
			, creatureOutOfRange, _buildCreature(creatureOutOfRange, new EntityLocation(1.6f, -0.4f, 1.0f))
		);
		EntityCollection collection = EntityCollection.fromMaps(players, creatures);
		NudgeHelpers.nudgeAsCreature(ENV, context, collection, creatures.get(creatureId));
		
		Assert.assertEquals(1, outPlayerChanges.size());
		Assert.assertEquals(1, outCreatureChanges.size());
	}


	private static Entity _buildPlayer(int id, EntityLocation location)
	{
		return new Entity(id
			, ENV.creatures.PLAYER
			, false
			, location
			, new EntityLocation(0.0f, 0.0f, 0.0f)
			, (byte)0
			, (byte)0
			, null
			, null
			, 0
			, null
			, (byte)0
			, (byte)0
			, MiscConstants.MAX_BREATH
			, MutableEntity.TESTING_LOCATION
			, Entity.EMPTY_SHARED
			, Entity.EMPTY_LOCAL
		);
	}

	private static CreatureEntity _buildCreature(int id, EntityLocation location)
	{
		return CreatureEntity.create(id, COW, location, 0L);
	}
}
