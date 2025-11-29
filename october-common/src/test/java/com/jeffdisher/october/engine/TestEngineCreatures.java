package com.jeffdisher.october.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.actions.EntityActionNudge;
import com.jeffdisher.october.actions.EntityActionApplyItemToCreature;
import com.jeffdisher.october.actions.EntityActionImpregnateCreature;
import com.jeffdisher.october.actions.EntityActionTakeDamageFromEntity;
import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.creatures.CreatureLogic;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.logic.CreatureIdAssigner;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.logic.NudgeHelpers;
import com.jeffdisher.october.logic.PropertyHelpers;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.TickUtils;
import com.jeffdisher.october.properties.PropertyRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.ContextBuilder;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Difficulty;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutableCreatureEntity;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.IPassiveAction;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MinimalEntity;
import com.jeffdisher.october.types.MutableCreature;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.PassiveEntity;
import com.jeffdisher.october.types.PassiveType;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.types.WorldConfig;
import com.jeffdisher.october.types.TickProcessingContext.IChangeSink;
import com.jeffdisher.october.types.TickProcessingContext.IMutationSink;
import com.jeffdisher.october.utils.CuboidGenerator;
import com.jeffdisher.october.utils.Encoding;


public class TestEngineCreatures
{
	private static Environment ENV;
	private static Block AIR;
	private static Block STONE;
	private static Block WATER_SOURCE;
	private static Block WATER_STRONG;
	private static Block WATER_WEAK;
	private static EntityType COW;
	private static EntityType COW_BABY;
	private static EntityType ORC;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		AIR = ENV.blocks.fromItem(ENV.items.getItemById("op.air"));
		STONE = ENV.blocks.fromItem(ENV.items.getItemById("op.stone"));
		WATER_SOURCE = ENV.blocks.fromItem(ENV.items.getItemById("op.water_source"));
		WATER_STRONG = ENV.blocks.fromItem(ENV.items.getItemById("op.water_strong"));
		WATER_WEAK = ENV.blocks.fromItem(ENV.items.getItemById("op.water_weak"));
		COW = ENV.creatures.getTypeById("op.cow");
		COW_BABY = ENV.creatures.getTypeById("op.cow_baby");
		ORC = ENV.creatures.getTypeById("op.orc");
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void singleChange()
	{
		CreatureEntity creature = CreatureEntity.create(-1, COW, new EntityLocation(0.0f, 0.0f, 0.0f), 0L);
		_Events events = new _Events();
		TickProcessingContext context = _createContextWithEvents(events);
		int sourceId = 1;
		EntityActionTakeDamageFromEntity<IMutableCreatureEntity> change = new EntityActionTakeDamageFromEntity<>(BodyPart.FEET, 10, sourceId);
		events.expected(new EventRecord(EventRecord.Type.ENTITY_HURT, EventRecord.Cause.ATTACKED, creature.location().getBlockLocation(), creature.id(), sourceId));
		EngineCreatures.SingleCreatureResult result = EngineCreatures.processOneCreature(context
			, EntityCollection.emptyCollection()
			, creature
			, List.of(change)
		);
		
		CreatureEntity updated = result.updatedEntity();
		Assert.assertEquals((byte)30, updated.health());
	}

	@Test
	public void killEntity()
	{
		CreatureEntity creature = CreatureEntity.create(-1, COW, new EntityLocation(0.0f, 0.0f, 0.0f), 0L);
		IMutationBlock[] mutationHolder = new IMutationBlock[1];
		CuboidData fakeCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), STONE);
		_Events events = new _Events();
		List<PassiveEntity> out_passives = new ArrayList<>();
		TickProcessingContext context = ContextBuilder.build()
				.tick(MiscConstants.DAMAGE_TAKEN_TIMEOUT_MILLIS / ContextBuilder.DEFAULT_MILLIS_PER_TICK)
				.lookups((AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), fakeCuboid), null, null)
				.sinks(new IMutationSink() {
					@Override
					public boolean next(IMutationBlock mutation)
					{
						Assert.assertNull(mutationHolder[0]);
						mutationHolder[0] = mutation;
						return true;
					}
					@Override
					public boolean future(IMutationBlock mutation, long millisToDelay)
					{
						throw new AssertionError("Not used in test");
					}
				}, null)
				.eventSink(events)
				.passive((PassiveType type, EntityLocation location, EntityLocation velocity, Object extendedData) -> {
					out_passives.add(new PassiveEntity(out_passives.size() + 1
						, type
						, location
						, velocity
						, extendedData
						, 1000L
					));
				})
				.finish()
		;
		int sourceId = 1;
		EntityActionTakeDamageFromEntity<IMutableCreatureEntity> change = new EntityActionTakeDamageFromEntity<>(BodyPart.FEET, 120, sourceId);
		events.expected(new EventRecord(EventRecord.Type.ENTITY_KILLED, EventRecord.Cause.ATTACKED, creature.location().getBlockLocation(), creature.id(), sourceId));
		EngineCreatures.SingleCreatureResult result = EngineCreatures.processOneCreature(context
			, EntityCollection.emptyCollection()
			, creature
			, List.of(change)
		);
		
		Assert.assertNull(result.updatedEntity());
		// This is a cow so we should see it drop an item.
		Assert.assertEquals(1, out_passives.size());
	}

	@Test
	public void decideOnMovement()
	{
		EntityLocation startLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		CreatureEntity creature = CreatureEntity.create(-1, COW, startLocation, 0L);
		TickProcessingContext context = _createContext();
		EngineCreatures.SingleCreatureResult result = EngineCreatures.processOneCreature(context
			, EntityCollection.emptyCollection()
			, creature
			, List.of()
		);
		
		CreatureEntity updated = result.updatedEntity();
		Assert.assertNotEquals(startLocation, updated.location());
		Assert.assertNotNull(updated.ephemeral().movementPlan());
	}

	@Test
	public void despawnOrcOnMovementDecision()
	{
		EntityLocation startLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		CreatureEntity creature = CreatureEntity.create(-1, ORC, startLocation, 0L);
		TickProcessingContext context = _createContextWithOptions(Difficulty.PEACEFUL, null);
		EngineCreatures.SingleCreatureResult result = EngineCreatures.processOneCreature(context
			, EntityCollection.emptyCollection()
			, creature
			, List.of()
		);
		
		Assert.assertNull(result.updatedEntity());
	}

	@Test
	public void startNextStep()
	{
		EntityLocation startLocation = new EntityLocation(0.0f, 1.0f, 0.0f);
		EntityLocation velocity = new EntityLocation(0.0f, 0.0f, 0.0f);
		List<AbsoluteLocation> movementPlan = List.of(new AbsoluteLocation(0, 1, 0)
			, new AbsoluteLocation(0, 1, 1)
		);
		CreatureEntity creature = new CreatureEntity(-1
				, COW
				, startLocation
				, velocity
				, (byte)0
				, (byte)0
				, (byte)100
				, MiscConstants.MAX_BREATH
				, COW.extendedCodec().buildDefault(0L)
				
				, new CreatureEntity.Ephemeral(
					movementPlan
					, 0L
					, false
					, 0L
					, CreatureEntity.NO_TARGET_ENTITY_ID
					, null
					, 0L
					, 0L
				)
		);
		TickProcessingContext context = _createContext();
		EngineCreatures.SingleCreatureResult result = EngineCreatures.processOneCreature(context
			, EntityCollection.emptyCollection()
			, creature
			, List.of()
		);
		
		CreatureEntity updated = result.updatedEntity();
		Assert.assertNotEquals(startLocation, updated.location());
		Assert.assertEquals(4.9f, updated.velocity().z(), 0.001f);
		Assert.assertEquals(1, updated.ephemeral().movementPlan().size());
	}

	@Test
	public void waitForJump()
	{
		// In this test, we should just be waiting for a jump to have an effect.
		EntityLocation startLocation = new EntityLocation(0.0f, 1.0f, 0.441f);
		EntityLocation velocity = new EntityLocation(0.0f, 0.0f, 3.92f);
		List<AbsoluteLocation> movementPlan = List.of(new AbsoluteLocation(0, 1, 1)
		);
		CreatureEntity creature = new CreatureEntity(-1
				, COW
				, startLocation
				, velocity
				, (byte)0
				, (byte)0
				, (byte)100
				, MiscConstants.MAX_BREATH
				, COW.extendedCodec().buildDefault(0L)
				
				, new CreatureEntity.Ephemeral(
					movementPlan
					, 0L
					, false
					, 0L
					, CreatureEntity.NO_TARGET_ENTITY_ID
					, null
					, 0L
					, 0L
				)
		);
		TickProcessingContext context = _createContext();
		EngineCreatures.SingleCreatureResult result = EngineCreatures.processOneCreature(context
			, EntityCollection.emptyCollection()
			, creature
			, List.of()
		);
		
		CreatureEntity updated = result.updatedEntity();
		Assert.assertNotEquals(startLocation, updated.location());
		Assert.assertEquals(2.94f, updated.velocity().z(), 0.001f);
		Assert.assertEquals(1, updated.ephemeral().movementPlan().size());
	}

	@Test
	public void moveAfterJump()
	{
		EntityLocation startLocation = new EntityLocation(0.0f, 0.0f, 1.2f);
		EntityLocation velocity = new EntityLocation(0.0f, 0.0f, 0.0f);
		List<AbsoluteLocation> movementPlan = List.of(new AbsoluteLocation(0, 0, 1)
			, new AbsoluteLocation(0, 1, 1)
		);
		int creatureId = -1;
		CreatureEntity creature = new CreatureEntity(creatureId
				, COW
				, startLocation
				, velocity
				, (byte)0
				, (byte)0
				, (byte)100
				, MiscConstants.MAX_BREATH
				, COW.extendedCodec().buildDefault(0L)
				
				, new CreatureEntity.Ephemeral(
					movementPlan
					, 0L
					, false
					, 0L
					, CreatureEntity.NO_TARGET_ENTITY_ID
					, null
					, 0L
					, 0L
				)
		);
		
		// We will create a stone platform for the context so that the entity will fall into the expected block.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), AIR);
		_setCuboidLayer(cuboid, (byte)0, STONE.item().number());
		TickProcessingContext context = _createSingleCuboidContext(cuboid);
		
		// We expect that there will be 2 moves to make to get into the right place to _begin_ moving into the new block.
		for (int i = 0; i < 2; ++i)
		{
			EngineCreatures.SingleCreatureResult result = EngineCreatures.processOneCreature(context
				, EntityCollection.emptyCollection()
				, creature
				, List.of()
			);
			creature = result.updatedEntity();
			Assert.assertNotNull(creature);
		}
		Assert.assertEquals(new EntityLocation(0.0f, 0.15f, 1.0f), creature.location());
		
		// There should now be just 1 step in the movement plan and the next invocation will cause us to convert it into steps, 19 in total.
		for (int i = 0; i < 19; ++i)
		{
			EngineCreatures.SingleCreatureResult result = EngineCreatures.processOneCreature(context
				, EntityCollection.emptyCollection()
				, creature
				, List.of()
			);
			creature = result.updatedEntity();
			Assert.assertNotNull(creature);
		}
		// We should be in the final location with a cleared movement plan by this point.
		Assert.assertEquals(new EntityLocation(0.0f, 1.01f, 1.0f), creature.location());
	}

	@Test
	public void resetPlanOnDamage()
	{
		EntityLocation startLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		byte startHealth = COW.maxHealth();
		CreatureEntity creature = CreatureEntity.create(-1, COW, startLocation, 0L);
		_Events events = new _Events();
		TickProcessingContext context = _createContextWithEvents(events);
		EngineCreatures.SingleCreatureResult result = EngineCreatures.processOneCreature(context
			, EntityCollection.emptyCollection()
			, creature
			, List.of()
		);
		
		CreatureEntity updated = result.updatedEntity();
		Assert.assertEquals(startHealth, updated.health());
		Assert.assertNotEquals(startLocation, updated.location());
		Assert.assertNotNull(updated.ephemeral().movementPlan());
		Assert.assertEquals(context.currentTickTimeMillis, updated.ephemeral().lastActionMillis());
		
		// Now, hit them and see this clears their movement plan so we should see a plan with new timers.
		context = _updateContextForTick(context);
		int damage = 10;
		int sourceId = 1;
		events.expected(new EventRecord(EventRecord.Type.ENTITY_HURT, EventRecord.Cause.ATTACKED, creature.location().getBlockLocation(), creature.id(), sourceId));
		result = EngineCreatures.processOneCreature(context
			, EntityCollection.emptyCollection()
			, creature
			, List.of(new EntityActionTakeDamageFromEntity<>(BodyPart.FEET, damage, sourceId))
		);
		
		updated = result.updatedEntity();
		Assert.assertEquals(startHealth - damage, updated.health());
		Assert.assertEquals(context.currentTickTimeMillis, updated.ephemeral().lastActionMillis());
	}

	@Test
	public void followTheWheat()
	{
		// Create 3 entities, 2 holding wheat and one holding a tool, to show that we always path to the closest with wheat.
		EntityLocation startLocation = new EntityLocation(0.19f, 0.0f, 0.0f);
		CreatureEntity creature = CreatureEntity.create(-1, COW, startLocation, 0L);
		Map<Integer, CreatureEntity> creaturesById = Map.of(creature.id(), creature);
		Entity farWheat = _createEntity(1, new EntityLocation(5.0f, 0.0f, 0.0f), new Items(ENV.items.getItemById("op.wheat_item"), 2), null);
		Entity closeWheat = _createEntity(2, new EntityLocation(3.0f, 0.0f, 0.0f), new Items(ENV.items.getItemById("op.wheat_item"), 2), null);
		Entity nonWheat = _createEntity(3, new EntityLocation(2.0f, 0.0f, 0.0f), null, PropertyHelpers.newItemWithDefaults(ENV, ENV.items.getItemById("op.iron_pickaxe")));
		TickProcessingContext context = _createContext();
		EngineCreatures.SingleCreatureResult result = EngineCreatures.processOneCreature(context
			, EntityCollection.fromMaps(Map.of(farWheat.id(), farWheat, closeWheat.id(), closeWheat, nonWheat.id(), nonWheat), creaturesById)
			, creature
			, List.of()
		);
		
		CreatureEntity updated = result.updatedEntity();
		Assert.assertNotEquals(startLocation, updated.location());
		
		// Make sure that the movement plan ends at the close wheat.
		List<AbsoluteLocation> movementPlan = updated.ephemeral().movementPlan();
		AbsoluteLocation endPoint = movementPlan.get(movementPlan.size() - 1);
		Assert.assertEquals(closeWheat.location().getBlockLocation(), endPoint);
		
		// Move the target entity and observe that the plan changes.
		closeWheat = _createEntity(closeWheat.id(), new EntityLocation(2.0f, 1.0f, 0.0f), new Items(ENV.items.getItemById("op.wheat_item"), 2), null);
		
		// Run the processor to observe the movement of the target entity.
		context = _updateContextWithPlayerAndCreatures(context, closeWheat, creaturesById);
		creature = updated;
		result = EngineCreatures.processOneCreature(context
			, EntityCollection.fromMaps(Map.of(farWheat.id(), farWheat, closeWheat.id(), closeWheat, nonWheat.id(), nonWheat), creaturesById)
			, creature
			, List.of()
		);
		updated = result.updatedEntity();
		Assert.assertNotNull(updated);
		
		// Make sure that the movement plan ends at the NEW close wheat location.
		movementPlan = updated.ephemeral().movementPlan();
		endPoint = movementPlan.get(movementPlan.size() - 1);
		Assert.assertEquals(closeWheat.location().getBlockLocation(), endPoint);
	}

	@Test
	public void orcTrackTarget()
	{
		// Create an orc and a player, showing that the orc follows the player when they move.
		EntityLocation startLocation = new EntityLocation(1.0f, 1.0f, 0.0f);
		CreatureEntity creature = CreatureEntity.create(-1, ORC, startLocation, 0L);
		Map<Integer, CreatureEntity> creaturesById = Map.of(creature.id(), creature);
		Entity player = _createEntity(1, new EntityLocation(5.0f, 1.0f, 0.0f), null, null);
		TickProcessingContext context = _createContext();
		context = _updateContextWithPlayerAndCreatures(context, player, creaturesById);
		EngineCreatures.SingleCreatureResult result = EngineCreatures.processOneCreature(context
			, EntityCollection.fromMaps(Map.of(player.id(), player), creaturesById)
			, creature
			, List.of()
		);
		
		// We should see that the orc has targeted the player and started moving toward them.
		CreatureEntity updated = result.updatedEntity();
		Assert.assertNotEquals(startLocation, updated.location());
		Assert.assertEquals(1, updated.ephemeral().targetEntityId());
		
		// Make sure that the movement plan ends at the player.
		List<AbsoluteLocation> movementPlan = updated.ephemeral().movementPlan();
		AbsoluteLocation endPoint = movementPlan.get(movementPlan.size() - 1);
		Assert.assertEquals(player.location().getBlockLocation(), endPoint);
		
		// Move the player and observe that the plan changes.
		player = _createEntity(1, new EntityLocation(3.0f, 3.0f, 0.0f), null, null);
		
		// We will run the processor to see them update their target location.
		context = _updateContextWithPlayerAndCreatures(context, player, creaturesById);
		creature = updated;
		result = EngineCreatures.processOneCreature(context
			, EntityCollection.fromMaps(Map.of(player.id(), player), creaturesById)
			, creature
			, List.of()
		);
		updated = result.updatedEntity();
		Assert.assertNotNull(updated);
		
		// Make sure that the movement plan ends at the NEW player location.
		movementPlan = updated.ephemeral().movementPlan();
		endPoint = movementPlan.get(movementPlan.size() - 1);
		Assert.assertEquals(player.location().getBlockLocation(), endPoint);
	}

	@Test
	public void cowsInLoveMode()
	{
		// Create 1 player holding wheat and 2 cows:  a distant one in love mode and a closer not in love mode.  Show that a cow priorizes the love cow.
		EntityLocation startLocation = new EntityLocation(0.19f, 0.0f, 0.0f);
		Item wheat_item = ENV.items.getItemById("op.wheat_item");
		MutableCreature mutable = MutableCreature.existing(CreatureEntity.create(-1, COW, startLocation, 0L));
		CreatureLogic.applyItemToCreature(wheat_item, mutable, 1000L);
		CreatureEntity fedCow = mutable.freeze();
		CreatureEntity otherCow = CreatureEntity.create(-2, COW, new EntityLocation(2.0f, 0.0f, 0.0f), 0L);
		mutable = MutableCreature.existing(CreatureEntity.create(-3, COW, new EntityLocation(5.0f, 0.0f, 0.0f), 0L));
		CreatureLogic.applyItemToCreature(wheat_item, mutable, 1000L);
		CreatureEntity targetCow = mutable.freeze();
		Map<Integer, CreatureEntity> creaturesById = Map.of(fedCow.id(), fedCow
				, otherCow.id(), otherCow
				, targetCow.id(), targetCow
		);
		Entity closeWheat = _createEntity(1, new EntityLocation(3.0f, 0.0f, 0.0f), new Items(wheat_item, 2), null);
		TickProcessingContext context = _createContext();
		context = _updateContextWithPlayerAndCreatures(context, null, creaturesById);
		EngineCreatures.SingleCreatureResult result = EngineCreatures.processOneCreature(context
			, EntityCollection.fromMaps(Map.of(closeWheat.id(), closeWheat), creaturesById)
			, fedCow
			, List.of()
		);
		
		CreatureEntity updated = result.updatedEntity();
		Assert.assertNotEquals(startLocation, updated.location());
		
		// Make sure that the movement plan ends at the close target cow.
		List<AbsoluteLocation> movementPlan = updated.ephemeral().movementPlan();
		AbsoluteLocation endPoint = movementPlan.get(movementPlan.size() - 1);
		Assert.assertEquals(targetCow.location().getBlockLocation(), endPoint);
	}

	@Test
	public void cowsBreeding()
	{
		// Create 2 cows close to each other, feed both of them, and observe the messages related to how they breed.
		CreatureIdAssigner idAssigner = new CreatureIdAssigner();
		EntityLocation location1 = new EntityLocation(0.0f, 0.0f, 0.0f);
		EntityLocation location2 = new EntityLocation(1.9f, 0.0f, 0.0f);
		EntityLocation playerLocation = new EntityLocation(0.5f, 0.5f, 0.0f);
		Item wheat_item = ENV.items.getItemById("op.wheat_item");
		MutableEntity mutablePlayer = MutableEntity.createWithLocation(1, playerLocation, playerLocation);
		mutablePlayer.newInventory.addAllItems(wheat_item, 3);
		mutablePlayer.setSelectedKey(1);
		Entity player = mutablePlayer.freeze();
		int cowId1 = idAssigner.next();
		int cowId2 = idAssigner.next();
		
		// First step, we should see the cows both take notice of the player since it has wheat in its hand.
		Map<Integer, CreatureEntity> creaturesById = new HashMap<>(Map.of(cowId1, CreatureEntity.create(cowId1, COW, location1, 0L)
			, cowId2, CreatureEntity.create(cowId2, COW, location2, 0L)
		));
		TickProcessingContext context = _createContext();
		context = _updateContextWithPlayerAndCreatures(context, player, creaturesById);
		creaturesById = _runGroup(context
			, List.of(player)
			, creaturesById
			, Map.of()
		);
		
		// Verify that they have set the targets.
		Assert.assertEquals(player.id(), creaturesById.get(cowId1).ephemeral().targetEntityId());
		Assert.assertEquals(player.id(), creaturesById.get(cowId2).ephemeral().targetEntityId());
		
		// Now, feed them using the mutations we would expect.
		context = _updateContextWithPlayerAndCreatures(context, player, creaturesById);
		creaturesById = _runGroup(context
			, List.of(player)
			, creaturesById
			, Map.of(cowId1, List.of(new EntityActionApplyItemToCreature(wheat_item))
				, cowId2, List.of(new EntityActionApplyItemToCreature(wheat_item))
			)
		);
		
		// The feeding will reset them but they can't yet see each other so they will pick an idle wander.
		Assert.assertFalse(creaturesById.get(cowId1).ephemeral().shouldTakeImmediateAction());
		Assert.assertFalse(creaturesById.get(cowId1).ephemeral().movementPlan().isEmpty());
		Assert.assertFalse(creaturesById.get(cowId2).ephemeral().shouldTakeImmediateAction());
		Assert.assertFalse(creaturesById.get(cowId2).ephemeral().movementPlan().isEmpty());
		// We want to clear this to observe the rest of the behaviour (otherwise, we would need to wait for them to move).
		creaturesById.put(cowId1, _takeAction(creaturesById.get(cowId1)));
		creaturesById.put(cowId2, _takeAction(creaturesById.get(cowId2)));
		
		// Run another tick so that they see each other in love mode.
		context = _updateContextWithPlayerAndCreatures(context, player, creaturesById);
		creaturesById = _runGroup(context
			, List.of(player)
			, creaturesById
			, Map.of()
		);
		
		// Verify that they now target each other.
		Assert.assertEquals(cowId2, creaturesById.get(cowId1).ephemeral().targetEntityId());
		Assert.assertEquals(cowId1, creaturesById.get(cowId2).ephemeral().targetEntityId());
		
		// Nothing should have happened yet, as they just found their mating partners so run the next tick.
		Map<Integer, IEntityAction<IMutableCreatureEntity>> changes = new HashMap<>();
		context = _updateContextWithCreatures(context, creaturesById.values(), new IChangeSink() {
			@Override
			public boolean next(int targetEntityId, IEntityAction<IMutablePlayerEntity> change)
			{
				throw new AssertionError("Not in test");
			}
			@Override
			public boolean future(int targetEntityId, IEntityAction<IMutablePlayerEntity> change, long millisToDelay)
			{
				throw new AssertionError("Not in test");
			}
			@Override
			public boolean creature(int targetCreatureId, IEntityAction<IMutableCreatureEntity> change)
			{
				Assert.assertFalse(changes.containsKey(targetCreatureId));
				changes.put(targetCreatureId, change);
				return true;
			}
			@Override
			public boolean passive(int targetPassiveId, IPassiveAction action)
			{
				throw new AssertionError();
			}
		}, null, null);
		creaturesById = _runGroup(context
			, List.of(player)
			, creaturesById
			, Map.of()
		);
		
		// Verify that cow1 sent a message to cow2.
		Assert.assertEquals(1, changes.size());
		Assert.assertTrue(changes.get(cowId2) instanceof EntityActionImpregnateCreature);
		
		// Run another tick to see the mother receive this request and spawn the offspring.
		CreatureEntity[] out = new CreatureEntity[1];
		context = _updateContextWithCreatures(context, creaturesById.values(), null, out, idAssigner);
		creaturesById = _runGroup(context
			, List.of()
			, creaturesById
			, Map.of(cowId2, List.of(changes.get(cowId2)))
		);
		Assert.assertNotNull(out[0]);
		CreatureEntity offspring = out[0];
		Assert.assertEquals(-3, offspring.id());
		Assert.assertEquals(COW_BABY, offspring.type());
		
		// Run another tick to observe that nothing special happens.
		Assert.assertNull(creaturesById.get(cowId2).ephemeral().movementPlan());
		creaturesById.put(offspring.id(), offspring);
		context = _updateContextWithCreatures(context, creaturesById.values(), null, null, null);
		creaturesById = _runGroup(context
			, List.of()
			, creaturesById
			, Map.of()
		);
	}

	@Test
	public void climbFromHole()
	{
		// Create an entity in a hole and see it position itself correctly and jump out.
		// Make an air cuboid with the bottom layer stone and the layer above stone except for one air block.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), AIR);
		for (byte z = 0; z < 2; ++z)
		{
			_setCuboidLayer(cuboid, z, STONE.item().number());
		}
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(8, 8, 1), AIR.item().number());
		
		EntityLocation startLocation = new EntityLocation(8.5f, 8.0f, 1.0f);
		CreatureEntity creature = CreatureEntity.create(-1, ORC, startLocation, 0L);
		Map<Integer, CreatureEntity> creaturesById = Map.of(creature.id(), creature);
		TickProcessingContext context = _createSingleCuboidContext(cuboid);
		EngineCreatures.SingleCreatureResult result = EngineCreatures.processOneCreature(context
			, EntityCollection.fromMaps(Map.of(), creaturesById)
			, creature
			, List.of()
		);
		
		CreatureEntity updated = result.updatedEntity();
		// The cow should first position itself against the wall before making the jump.
		// Note that it will take 5 steps, instead of 3, since this is an idle movement (half-speed but also ends in a jump).
		for (int i = 0; i < 5; ++i)
		{
			Assert.assertEquals(0.0f, updated.velocity().z(), 0.001f);
			creaturesById = Map.of(updated.id(), updated);
			result = EngineCreatures.processOneCreature(context
				, EntityCollection.fromMaps(Map.of(), creaturesById)
				, updated
				, List.of()
			);
			updated = result.updatedEntity();
		}
		// We should now be against the wall.
		Assert.assertEquals(8.01f, updated.location().x(), 0.01f);
		
		// The cow should have jumped, so verify the location, z-velocity, and no plan to the next step.
		Assert.assertNotEquals(startLocation, updated.location());
		Assert.assertNotEquals(0.0f, updated.velocity().z());
		
		// Verify that the movement plan is the one we expected (since we depend on knowing which direction we are moving for the test).
		List<AbsoluteLocation> movementPlan = updated.ephemeral().movementPlan();
		Assert.assertEquals(3, movementPlan.size());
		Assert.assertEquals(new AbsoluteLocation(8, 8, 2), movementPlan.get(0));
		Assert.assertEquals(new AbsoluteLocation(7, 8, 2), movementPlan.get(1));
		Assert.assertEquals(new AbsoluteLocation(7, 9, 2), movementPlan.get(2));
		
		// Now, allow the entity to continue on its path and verify that it reaches the end of its path.
		CreatureEntity justUpdated = updated;
		while (null != justUpdated)
		{
			updated = justUpdated;
			// Apply the next step.
			creaturesById = Map.of(updated.id(), updated);
			result = EngineCreatures.processOneCreature(context
				, EntityCollection.fromMaps(Map.of(), creaturesById)
				, updated
				, List.of()
			);
			justUpdated = result.updatedEntity();
			if (justUpdated == updated)
			{
				// No change so null this.
				justUpdated = null;
			}
		}
		
		// By this point we should be on the ground, in the right block, with no plan.
		Assert.assertEquals(7.3f, updated.location().x(), 0.01f);
		Assert.assertEquals(9.01f, updated.location().y(), 0.01f);
		Assert.assertEquals(2.0f, updated.location().z(), 0.01f);
		Assert.assertEquals(0.0f, updated.velocity().x(), 0.01f);
		Assert.assertEquals(0.0f, updated.velocity().y(), 0.01f);
		Assert.assertEquals(0.0f, updated.velocity().z(), 0.01f);
		Assert.assertEquals(new AbsoluteLocation(7, 9, 2), updated.location().getBlockLocation());
	}

	@Test
	public void walkThroughWaterOrAir()
	{
		// Demonstrate that the walking speed through water and air is different.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), AIR);
		_setCuboidLayer(cuboid, (byte)0, STONE.item().number());
		_setCuboidLayer(cuboid, (byte)1, WATER_SOURCE.item().number());
		_setCuboidLayer(cuboid, (byte)16, STONE.item().number());
		
		EntityLocation waterStart = new EntityLocation(2.0f, 2.0f, 1.0f);
		EntityLocation airStart = new EntityLocation(2.0f, 2.0f, 17.0f);
		CreatureEntity waterCreature = CreatureEntity.create(-1, ORC, waterStart, 0L);
		CreatureEntity airCreature = CreatureEntity.create(-2, ORC, airStart, 0L);
		float targetDistance = 4.0f;
		Entity waterTarget = _createEntity(1, new EntityLocation(waterStart.x() + targetDistance, waterStart.y(), waterStart.z()), null, null);
		Entity airTarget = _createEntity(2, new EntityLocation(airStart.x() + targetDistance, airStart.y(), airStart.z()), null, null);
		
		Map<Integer, CreatureEntity> creaturesById = Map.of(waterCreature.id(), waterCreature
				, airCreature.id(), airCreature
		);
		long millisPerTick = 100L;
		
		for (int i = 0; i < 10; ++i)
		{
			Map<Integer, MinimalEntity> minimalEntitiesById = Map.of(waterTarget.id(), MinimalEntity.fromEntity(waterTarget)
					, airTarget.id(), MinimalEntity.fromEntity(airTarget)
			);
			TickProcessingContext context = ContextBuilder.build()
					.tick(CreatureLogic.MINIMUM_MILLIS_TO_ACTION / millisPerTick)
					.lookups((AbsoluteLocation location) -> {
							return (cuboid.getCuboidAddress().equals(location.getCuboidAddress()))
								? new BlockProxy(location.getBlockAddress(), cuboid)
								: null
							;
						}, (Integer id) -> minimalEntitiesById.get(id), null)
					// We return a fixed "1" for the random generator to make sure that we select a reasonable plan for all tests.
					.fixedRandom(1)
					.finish()
			;
			creaturesById = _runGroup(context, List.of(waterTarget, airTarget), creaturesById, Map.of());
			float oldWater = waterCreature.location().x();
			float oldAir = waterCreature.location().x();
			waterCreature = creaturesById.get(waterCreature.id());
			airCreature = creaturesById.get(airCreature.id());
			// Make sure that both are making progress.
			Assert.assertTrue(waterCreature.location().x() > oldWater);
			Assert.assertTrue(airCreature.location().x() > oldAir);
			// Make sure that the air creature is further ahead.
			Assert.assertTrue(airCreature.location().x() > waterCreature.location().x());
		}
		// Note that these final numbers are sensitive to not only the resistance of the water but also the details of
		// intra-step planning (lining up against an edge before moving to the next step).
		Assert.assertEquals(3.0f, waterCreature.location().x(), 0.01f);
		Assert.assertEquals(3.79f, airCreature.location().x(), 0.01f);
	}

	@Test
	public void swimToSurface()
	{
		// Show a creature swimming to the surface of a body of water.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), AIR);
		short waterSourceNumber = WATER_SOURCE.item().number();
		_setCuboidLayer(cuboid, (byte)0, STONE.item().number());
		_setCuboidLayer(cuboid, (byte)1, waterSourceNumber);
		_setCuboidLayer(cuboid, (byte)2, waterSourceNumber);
		
		EntityLocation startLocation = new EntityLocation(8.0f, 8.0f, 1.0f);
		CreatureEntity creature = CreatureEntity.create(-1, ORC, startLocation, 0L);
		// We need to reduce their breath to trigger this response.
		MutableCreature mutable = MutableCreature.existing(creature);
		mutable.newBreath -= 1;
		creature = mutable.freeze();
		
		long millisPerTick = 100L;
		
		// This threshold where we finish climbing is determined experimentally.
		int stepsRequired = 19;
		for (int i = 0; i <= stepsRequired; ++i)
		{
			TickProcessingContext context = ContextBuilder.build()
					.tick(CreatureLogic.MINIMUM_MILLIS_TO_ACTION / millisPerTick)
					.lookups((AbsoluteLocation location) -> {
							return (cuboid.getCuboidAddress().equals(location.getCuboidAddress()))
								? new BlockProxy(location.getBlockAddress(), cuboid)
								: null
							;
						}, null, null)
					// We return a fixed "0" for the random generator to make sure that we select a reasonable plan for all tests.
					.fixedRandom(0)
					.finish()
			;
			EngineCreatures.SingleCreatureResult result = EngineCreatures.processOneCreature(context
				, EntityCollection.fromMaps(Map.of(), Map.of(creature.id(), creature))
				, creature
				, List.of()
			);
			creature = result.updatedEntity();
			
			if (i < stepsRequired)
			{
				Assert.assertNotNull(creature.ephemeral().movementPlan());
			}
			else
			{
				Assert.assertNull(creature.ephemeral().movementPlan());
			}
		}
		// We should be in the same column but higher.
		Assert.assertEquals(startLocation.x(), creature.location().x(), 0.01f);
		Assert.assertEquals(startLocation.y(), creature.location().y(), 0.01f);
		Assert.assertEquals(3.19f, creature.location().z(), 0.01f);
	}

	@Test
	public void fallDamage()
	{
		// Show that a creature takes damage when falling.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(16, 16, 15), STONE.item().number());
		
		float fallingVelocity = TickUtils.DECELERATION_DAMAGE_THRESHOLD + (TickUtils.DECELERATION_DAMAGE_RANGE / 2.0f);
		EntityLocation startLocation = new EntityLocation(16.8f, 16.8f, 16.1f);
		EntityLocation startVelocity = new EntityLocation(0.0f, 0.0f, fallingVelocity);
		CreatureEntity creature = CreatureEntity.create(-1, ORC, startLocation, 0L);
		MutableCreature mutable = MutableCreature.existing(creature);
		mutable.newVelocity = startVelocity;
		mutable.newHealth = (byte)100;
		creature = mutable.freeze();
		
		_Events events = new _Events();
		TickProcessingContext context = ContextBuilder.build()
				.tick(MiscConstants.DAMAGE_TAKEN_TIMEOUT_MILLIS / ContextBuilder.DEFAULT_MILLIS_PER_TICK)
				.lookups((AbsoluteLocation location) -> {
						return (cuboid.getCuboidAddress().equals(location.getCuboidAddress()))
							? new BlockProxy(location.getBlockAddress(), cuboid)
							: null
						;
					}, null, null)
				.eventSink(events)
				.finish()
		;
		events.expected(new EventRecord(EventRecord.Type.ENTITY_HURT, EventRecord.Cause.FALL, creature.location().getBlockLocation(), creature.id(), 0));
		EngineCreatures.SingleCreatureResult result = EngineCreatures.processOneCreature(context
			, EntityCollection.fromMaps(Map.of(), Map.of(creature.id(), creature))
			, creature
			, List.of()
		);
		creature = result.updatedEntity();
		
		Assert.assertEquals(new EntityLocation(16.8f, 16.8f, 16.0f), creature.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), creature.velocity());
		Assert.assertEquals((byte)37, creature.health());
	}

	@Test
	public void changeTargetMidStep()
	{
		// Shows what happens when a creature changes its planned direction mid-step.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		_setCuboidLayer(cuboid, (byte)0, STONE.item().number());
		
		// We need to give it a target so it will move at full speed.
		Entity target = _createEntity(1, new EntityLocation(14.0f, 16.0f, 1.0f), new Items(ENV.items.getItemById("op.wheat_item"), 2), null);
		EntityLocation startLocation = new EntityLocation(16.0f, 16.0f, 1.0f);
		CreatureEntity creature = CreatureEntity.create(-1, COW, startLocation, 0L);
		MutableCreature mutable = MutableCreature.existing(creature);
		mutable.newMovementPlan = List.of(new AbsoluteLocation(15, 16, 1)
			, new AbsoluteLocation(14, 16, 1)
		);
		mutable.newTargetEntityId = target.id();
		creature = mutable.freeze();
		
		Entity[] indirect = new Entity[] { target };
		TickProcessingContext context = ContextBuilder.build()
			.lookups((AbsoluteLocation location) -> {
				return (cuboid.getCuboidAddress().equals(location.getCuboidAddress()))
					? new BlockProxy(location.getBlockAddress(), cuboid)
					: null
				;
			}, (Integer id) -> {
				return MinimalEntity.fromEntity(indirect[0]);
			}, null)
			.finish()
		;
		boolean didChange = false;
		while (null != creature.ephemeral().movementPlan())
		{
			AbsoluteLocation blockLocation = creature.location().getBlockLocation();
			if (!didChange && blockLocation.equals(new AbsoluteLocation(15, 16, 1)))
			{
				target = _createEntity(1, new EntityLocation(18.0f, 16.0f, 1.0f), new Items(ENV.items.getItemById("op.wheat_item"), 2), null);
				indirect[0] = target;
				didChange = true;
			}
			EngineCreatures.SingleCreatureResult result = EngineCreatures.processOneCreature(context
				, EntityCollection.fromMaps(Map.of(target.id(), target), Map.of(creature.id(), creature))
				, creature
				, List.of()
			);
			creature = result.updatedEntity();
		}
		
		// At this point, we will be close enough that we have stopped moving but we still see some "coasting" residual velocity.
		Assert.assertEquals(new EntityLocation(16.5f, 16.0f, 1.0f), creature.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), creature.velocity());
	}

	@Test
	public void cowsNudge()
	{
		// Put 2 cows intersecting and show that one will use a nudge to push the other away based on ID and tick number.
		EntityLocation location1 = new EntityLocation(1.0f, 1.0f, 0.0f);
		EntityLocation location2 = new EntityLocation(1.2f, 0.9f, 0.1f);
		CreatureEntity cow1 = CreatureEntity.create(-1, COW, location1, 0L);
		CreatureEntity cow2 = CreatureEntity.create(-2, COW, location2, 0L);
		
		// First step, we should see the cows both take notice of the player since it has wheat in its hand.
		Map<Integer, CreatureEntity> creaturesById = new HashMap<>(Map.of(cow1.id(), cow1
				, cow2.id(), cow2
		));
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), AIR);
		CuboidData stoneCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), STONE);
		Object[] out = new Object[1];
		TickProcessingContext context = ContextBuilder.build()
				.tick(NudgeHelpers.CREATURE_NUDGE_TICK_FREQUENCY - Math.abs(cow2.id()))
				.lookups((AbsoluteLocation location) -> {
					return ((short)-1 == location.z())
						? new BlockProxy(location.getBlockAddress(), stoneCuboid)
						: new BlockProxy(location.getBlockAddress(), airCuboid)
					;
				} , null, null)
				.sinks(null, new TickProcessingContext.IChangeSink() {
					@Override
					public boolean next(int targetEntityId, IEntityAction<IMutablePlayerEntity> change)
					{
						throw new AssertionError("Not in test");
					}
					@Override
					public boolean future(int targetEntityId, IEntityAction<IMutablePlayerEntity> change, long millisToDelay)
					{
						throw new AssertionError("Not in test");
					}
					@Override
					public boolean creature(int targetCreatureId, IEntityAction<IMutableCreatureEntity> change)
					{
						Assert.assertEquals(-1, targetCreatureId);
						Assert.assertTrue(change instanceof EntityActionNudge);
						out[0] = change;
						return true;
					}
					@Override
					public boolean passive(int targetPassiveId, IPassiveAction action)
					{
						throw new AssertionError();
					}
				})
				.finish()
		;
		creaturesById = _runGroup(context, List.of(), creaturesById, Map.of());
		Assert.assertTrue(out[0] instanceof EntityActionNudge);
		
		// We will just run this against the cow, manually, to show the velocity change.
		MutableCreature mutable = MutableCreature.existing(cow1);
		@SuppressWarnings("unchecked")
		EntityActionNudge<IMutableCreatureEntity> nudge = (EntityActionNudge<IMutableCreatureEntity>) out[0];
		Assert.assertTrue(nudge.applyChange(context, mutable));
		Assert.assertEquals(new EntityLocation(-2.0f, 2.5f, -2.5f), mutable.newVelocity);
	}

	@Test
	public void popOut()
	{
		CreatureEntity creature = CreatureEntity.create(-1, COW, new EntityLocation(0.0f, 0.0f, -0.2f), 0L);
		_Events events = new _Events();
		TickProcessingContext context = _createContextWithEvents(events);
		EngineCreatures.SingleCreatureResult result = EngineCreatures.processOneCreature(context
			, EntityCollection.emptyCollection()
			, creature
			, List.of()
		);
		
		CreatureEntity updated = result.updatedEntity();
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), updated.location());
	}

	@Test
	public void waterStream()
	{
		// Show that a water stream will push a creature.
		EntityLocation creatureLocation = new EntityLocation(0.8f, 0.8f, 0.0f);
		CreatureEntity creature = CreatureEntity.create(-1, COW, creatureLocation, 0L);
		
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(0, 0, 0), WATER_SOURCE.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(0, 1, 0), WATER_STRONG.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(1, 0, 0), WATER_STRONG.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(1, 1, 0), WATER_WEAK.item().number());
		long tickNumber = 1L;
		TickProcessingContext context = ContextBuilder.build()
			.tick(tickNumber)
			.lookups((AbsoluteLocation location) -> {
				return cuboid.getCuboidAddress().equals(location.getCuboidAddress())
					? new BlockProxy(location.getBlockAddress(), cuboid)
					: null
				;
			} , null, null)
			.finish()
		;
		EntityCollection entityCollection = EntityCollection.emptyCollection();
		EngineCreatures.SingleCreatureResult result = EngineCreatures.processOneCreature(context
			, entityCollection
			, creature
			, List.of()
		);
		
		CreatureEntity updated = result.updatedEntity();
		Assert.assertEquals(new EntityLocation(0.85f, 0.85f, 0.0f), updated.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), updated.velocity());
	}

	@Test
	public void killSkeleton()
	{
		// Show that the skeleton can drop a non-stackable.
		CreatureEntity creature = CreatureEntity.create(-1, ENV.creatures.getTypeById("op.skeleton"), new EntityLocation(0.0f, 0.0f, 0.0f), 0L);
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		_Events events = new _Events();
		PassiveEntity[] out = new PassiveEntity[1];
		TickProcessingContext context = ContextBuilder.build()
			.tick(MiscConstants.DAMAGE_TAKEN_TIMEOUT_MILLIS / ContextBuilder.DEFAULT_MILLIS_PER_TICK)
			.fixedRandom(0)
			.lookups((AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), airCuboid), null, null)
			.eventSink(events)
			.passive((PassiveType type, EntityLocation location, EntityLocation velocity, Object extendedData) -> {
				Assert.assertNull(out[0]);
				PassiveEntity passive = new PassiveEntity(1
					, type
					, location
					, velocity
					, extendedData
					, 1000L
				);
				out[0] = passive;
			})
			.finish()
		;
		int sourceId = 1;
		EntityActionTakeDamageFromEntity<IMutableCreatureEntity> action = new EntityActionTakeDamageFromEntity<>(BodyPart.FEET, 120, sourceId);
		events.expected(new EventRecord(EventRecord.Type.ENTITY_KILLED, EventRecord.Cause.ATTACKED, creature.location().getBlockLocation(), creature.id(), sourceId));
		EngineCreatures.SingleCreatureResult result = EngineCreatures.processOneCreature(context
			, EntityCollection.emptyCollection()
			, creature
			, List.of(action)
		);
		
		Assert.assertNull(result.updatedEntity());
		
		Assert.assertEquals(1, out[0].id());
		Assert.assertEquals(new EntityLocation(0.3f, 0.3f, 0.0f), out[0].location());
		NonStackableItem nonStack = ((ItemSlot)out[0].extendedData()).nonStackable;
		Item bow = ENV.items.getItemById("op.bow");
		Assert.assertEquals(bow, nonStack.type());
		Assert.assertEquals(1, nonStack.properties().size());
		Assert.assertEquals(ENV.durability.getDurability(bow), nonStack.properties().get(PropertyRegistry.DURABILITY));
	}


	private static TickProcessingContext _createContext()
	{
		return _createContextWithOptions(Difficulty.HOSTILE, null);
	}

	private static TickProcessingContext _createContextWithEvents(_Events events)
	{
		return _createContextWithOptions(Difficulty.HOSTILE, events);
	}

	private static TickProcessingContext _createContextWithOptions(Difficulty difficulty, _Events events)
	{
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), AIR);
		CuboidData stoneCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), STONE);
		long millisPerTick = 100L;
		WorldConfig config = new WorldConfig();
		config.difficulty = difficulty;
		TickProcessingContext context = ContextBuilder.build()
				.tick((CreatureLogic.MINIMUM_MILLIS_TO_ACTION / millisPerTick) + 1L)
				.lookups((AbsoluteLocation location) -> {
						return ((short)-1 == location.z())
							? new BlockProxy(location.getBlockAddress(), stoneCuboid)
							: new BlockProxy(location.getBlockAddress(), airCuboid)
						;
					} , null, null)
				// We return a fixed "1" for the random generator to make sure that we select a reasonable plan for all tests.
				.fixedRandom(1)
				.eventSink(events)
				.config(config)
				.finish()
		;
		return context;
	}

	private static TickProcessingContext _createSingleCuboidContext(CuboidData cuboid)
	{
		long millisPerTick = 100L;
		TickProcessingContext context = ContextBuilder.build()
				.tick(CreatureLogic.MINIMUM_MILLIS_TO_ACTION / millisPerTick)
				.lookups((AbsoluteLocation location) -> {
					return (cuboid.getCuboidAddress().equals(location.getCuboidAddress()))
							? new BlockProxy(location.getBlockAddress(), cuboid)
							: null
						;
					} , null, null)
				// We return a fixed "1" for the random generator to make sure that we select a reasonable plan for all tests.
				.fixedRandom(1)
				.finish()
		;
		return context;
	}

	private static TickProcessingContext _updateContextWithCreatures(TickProcessingContext existing
			, Collection<CreatureEntity> creatures
			, IChangeSink newChangeSink
			, CreatureEntity[] outSpawnedCreature
			, CreatureIdAssigner idAssigner
	)
	{
		TickProcessingContext.ICreatureSpawner spawner = (EntityType type, EntityLocation location) -> {
			Assert.assertNull(outSpawnedCreature[0]);
			outSpawnedCreature[0] = CreatureEntity.create(idAssigner.next(), type, location, 0L);
		};
		Map<Integer, MinimalEntity> minimal = creatures.stream().collect(Collectors.toMap((CreatureEntity creature) -> creature.id(), (CreatureEntity creature) -> MinimalEntity.fromCreature(creature)));
		TickProcessingContext context = ContextBuilder.nextTick(existing, 1L)
				.lookups(existing.previousBlockLookUp, (Integer id) -> minimal.get(id), null)
				.sinks(null, newChangeSink)
				.spawner(spawner)
				.finish()
		;
		return context;
	}

	private static TickProcessingContext _updateContextWithPlayerAndCreatures(TickProcessingContext existing, Entity player, Map<Integer, CreatureEntity> creatures)
	{
		TickProcessingContext context = ContextBuilder.nextTick(existing, (CreatureLogic.MINIMUM_MILLIS_TO_ACTION / existing.millisPerTick))
				.lookups(existing.previousBlockLookUp, (Integer id) -> {
					MinimalEntity ret;
					if ((null != player) && (id == player.id()))
					{
						ret = MinimalEntity.fromEntity(player);
					}
					else
					{
						ret = creatures.containsKey(id)
								? MinimalEntity.fromCreature(creatures.get(id))
								: null
						;
					}
					return ret;
				}, null)
				.sinks(null, null)
				.spawner(null)
				.finish()
		;
		return context;
	}

	private static TickProcessingContext _updateContextForTick(TickProcessingContext existing)
	{
		TickProcessingContext context = ContextBuilder.nextTick(existing, (CreatureLogic.MINIMUM_MILLIS_TO_ACTION / existing.millisPerTick))
				.finish()
		;
		return context;
	}

	private Entity _createEntity(int id, EntityLocation location, Items stack, NonStackableItem nonStack)
	{
		Inventory.Builder builder = Inventory.start(100);
		if (null != stack)
		{
			builder.addStackable(stack.type(), stack.count());
		}
		else if (null != nonStack)
		{
			builder.addNonStackable(nonStack);
		}
		Inventory inventory = builder.finish();
		int key = 1;
		return new Entity(id
				, false
				, location
				, new EntityLocation(0.0f, 0.0f, 0.0f)
				, (byte)0
				, (byte)0
				, inventory
				, new int[] { key }
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

	private void _setCuboidLayer(CuboidData cuboid, byte z, short number)
	{
		for (byte y = 0; y < Encoding.CUBOID_EDGE_SIZE; ++y)
		{
			for (byte x = 0; x < Encoding.CUBOID_EDGE_SIZE; ++x)
			{
				cuboid.setData15(AspectRegistry.BLOCK, new BlockAddress(x, y, z), number);
			}
		}
	}

	private static CreatureEntity _takeAction(CreatureEntity entity)
	{
		List<AbsoluteLocation> movementPlan = null;
		boolean shouldTakeImmediateAction = true;
		int targetEntityId = CreatureEntity.NO_TARGET_ENTITY_ID;
		AbsoluteLocation targetPreviousLocation = null;
		
		return new CreatureEntity(entity.id()
				, entity.type()
				, entity.location()
				, entity.velocity()
				, entity.yaw()
				, entity.pitch()
				, entity.health()
				, entity.breath()
				, entity.extendedData()
				
				, new CreatureEntity.Ephemeral(
					movementPlan
					, entity.ephemeral().lastActionMillis()
					, shouldTakeImmediateAction
					, entity.ephemeral().despawnKeepAliveMillis()
					, targetEntityId
					, targetPreviousLocation
					, entity.ephemeral().lastAttackMillis()
					, entity.ephemeral().lastDamageTakenMillis()
				)
		);
	}

	private static Map<Integer, CreatureEntity> _runGroup(TickProcessingContext context
		, List<Entity> players
		, Map<Integer, CreatureEntity> creatures
		, Map<Integer, List<IEntityAction<IMutableCreatureEntity>>> changesToRun
	)
	{
		Map<Integer, Entity> playerMap = new HashMap<>();
		for (Entity player : players)
		{
			playerMap.put(player.id(), player);
		}
		EntityCollection entityCollection = EntityCollection.fromMaps(playerMap, creatures);
		Map<Integer, CreatureEntity> updated = new HashMap<>(creatures);
		for (Integer key : creatures.keySet())
		{
			List<IEntityAction<IMutableCreatureEntity>> toRun = changesToRun.get(key);
			if (null == toRun)
			{
				toRun = List.of();
			}
			EngineCreatures.SingleCreatureResult result = EngineCreatures.processOneCreature(context
				, entityCollection
				, creatures.get(key)
				, toRun
			);
			if (null != result.updatedEntity())
			{
				// This is either changed or not, but not deleted.
				updated.put(key, result.updatedEntity());
			}
			else
			{
				updated.remove(key);
			}
		}
		return updated;
	}

	private static class _Events implements TickProcessingContext.IEventSink
	{
		private EventRecord _expected;
		public void expected(EventRecord expected)
		{
			Assert.assertNull(_expected);
			_expected = expected;
		}
		@Override
		public void post(EventRecord event)
		{
			Assert.assertEquals(_expected, event);
			_expected = null;
		}
	}
}
