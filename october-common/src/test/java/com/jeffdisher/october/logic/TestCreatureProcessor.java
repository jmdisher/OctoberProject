package com.jeffdisher.october.logic;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.creatures.CreatureLogic;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.mutations.EntityChangeApplyItemToCreature;
import com.jeffdisher.october.mutations.EntityChangeImpregnateCreature;
import com.jeffdisher.october.mutations.EntityChangeTakeDamageFromEntity;
import com.jeffdisher.october.mutations.IEntityAction;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.MutationBlockStoreItems;
import com.jeffdisher.october.mutations.TickUtils;
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
import com.jeffdisher.october.types.IMutableCreatureEntity;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MinimalEntity;
import com.jeffdisher.october.types.MutableCreature;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.types.WorldConfig;
import com.jeffdisher.october.types.TickProcessingContext.IChangeSink;
import com.jeffdisher.october.types.TickProcessingContext.IMutationSink;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestCreatureProcessor
{
	private static Environment ENV;
	private static Block AIR;
	private static Block STONE;
	private static EntityType COW;
	private static EntityType ORC;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		AIR = ENV.blocks.fromItem(ENV.items.getItemById("op.air"));
		STONE = ENV.blocks.fromItem(ENV.items.getItemById("op.stone"));
		COW = ENV.creatures.getTypeById("op.cow");
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
		ProcessorElement thread = new ProcessorElement(0, new SyncPoint(1), new AtomicInteger(0));
		CreatureEntity creature = CreatureEntity.create(-1, COW, new EntityLocation(0.0f, 0.0f, 0.0f), (byte)100);
		Map<Integer, CreatureEntity> creaturesById = Map.of(creature.id(), creature);
		_Events events = new _Events();
		TickProcessingContext context = _createContextWithEvents(events);
		int sourceId = 1;
		EntityChangeTakeDamageFromEntity<IMutableCreatureEntity> change = new EntityChangeTakeDamageFromEntity<>(BodyPart.FEET, 10, sourceId);
		Map<Integer, List<IEntityAction<IMutableCreatureEntity>>> changesToRun = Map.of(creature.id(), List.of(change));
		events.expected(new EventRecord(EventRecord.Type.ENTITY_HURT, EventRecord.Cause.ATTACKED, creature.location().getBlockLocation(), creature.id(), sourceId));
		CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Map.of(), Map.of())
				, changesToRun
		);
		
		Assert.assertEquals(1, group.updatedCreatures().size());
		Assert.assertEquals(0, group.deadCreatureIds().size());
		CreatureEntity updated = group.updatedCreatures().get(creature.id());
		Assert.assertEquals((byte)90, updated.health());
	}

	@Test
	public void killEntity()
	{
		ProcessorElement thread = new ProcessorElement(0, new SyncPoint(1), new AtomicInteger(0));
		CreatureEntity creature = CreatureEntity.create(-1, COW, new EntityLocation(0.0f, 0.0f, 0.0f), (byte)50);
		Map<Integer, CreatureEntity> creaturesById = Map.of(creature.id(), creature);
		IMutationBlock[] mutationHolder = new IMutationBlock[1];
		CuboidData fakeCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), STONE);
		_Events events = new _Events();
		TickProcessingContext context = ContextBuilder.build()
				.tick(MiscConstants.DAMAGE_TAKEN_TIMEOUT_MILLIS / ContextBuilder.DEFAULT_MILLIS_PER_TICK)
				.lookups((AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), fakeCuboid), null)
				.sinks(new IMutationSink() {
					@Override
					public void next(IMutationBlock mutation)
					{
						Assert.assertNull(mutationHolder[0]);
						mutationHolder[0] = mutation;
					}
					@Override
					public void future(IMutationBlock mutation, long millisToDelay)
					{
						Assert.fail("Not used in test");
					}
				}, null)
				.eventSink(events)
				.finish()
		;
		int sourceId = 1;
		EntityChangeTakeDamageFromEntity<IMutableCreatureEntity> change = new EntityChangeTakeDamageFromEntity<>(BodyPart.FEET, 120, sourceId);
		Map<Integer, List<IEntityAction<IMutableCreatureEntity>>> changesToRun = Map.of(creature.id(), List.of(change));
		events.expected(new EventRecord(EventRecord.Type.ENTITY_KILLED, EventRecord.Cause.ATTACKED, creature.location().getBlockLocation(), creature.id(), sourceId));
		CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Map.of(), Map.of())
				, changesToRun
		);
		
		Assert.assertEquals(0, group.updatedCreatures().size());
		Assert.assertEquals(1, group.deadCreatureIds().size());
		Assert.assertEquals(creature.id(), group.deadCreatureIds().get(0).intValue());
		// This is a cow so we should see it drop an item.
		Assert.assertTrue(mutationHolder[0] instanceof MutationBlockStoreItems);
	}

	@Test
	public void decideOnMovement()
	{
		ProcessorElement thread = new ProcessorElement(0, new SyncPoint(1), new AtomicInteger(0));
		EntityLocation startLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		CreatureEntity creature = CreatureEntity.create(-1, COW, startLocation, (byte)100);
		Map<Integer, CreatureEntity> creaturesById = Map.of(creature.id(), creature);
		TickProcessingContext context = _createContext();
		Map<Integer, List<IEntityAction<IMutableCreatureEntity>>> changesToRun = Map.of();
		CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Map.of(), Map.of())
				, changesToRun
		);
		
		CreatureEntity updated = group.updatedCreatures().get(creature.id());
		Assert.assertNotEquals(startLocation, updated.location());
		Assert.assertNotNull(updated.ephemeral().movementPlan());
	}

	@Test
	public void despawnOrcOnMovementDecision()
	{
		ProcessorElement thread = new ProcessorElement(0, new SyncPoint(1), new AtomicInteger(0));
		EntityLocation startLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		CreatureEntity creature = CreatureEntity.create(-1, ORC, startLocation, (byte)50);
		Map<Integer, CreatureEntity> creaturesById = Map.of(creature.id(), creature);
		TickProcessingContext context = _createContextWithOptions(Difficulty.PEACEFUL, null);
		Map<Integer, List<IEntityAction<IMutableCreatureEntity>>> changesToRun = Map.of();
		CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Map.of(), Map.of())
				, changesToRun
		);
		
		Assert.assertTrue(group.updatedCreatures().isEmpty());
	}

	@Test
	public void startNextStep()
	{
		ProcessorElement thread = new ProcessorElement(0, new SyncPoint(1), new AtomicInteger(0));
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
				
				, new CreatureEntity.Ephemeral(
					movementPlan
					, 0L
					, false
					, 0L
					, CreatureEntity.NO_TARGET_ENTITY_ID
					, null
					, 0L
					, false
					, null
					, 0L
				)
		);
		Map<Integer, CreatureEntity> creaturesById = Map.of(creature.id(), creature);
		TickProcessingContext context = _createContext();
		Map<Integer, List<IEntityAction<IMutableCreatureEntity>>> changesToRun = Map.of();
		CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Map.of(), Map.of())
				, changesToRun
		);
		
		CreatureEntity updated = group.updatedCreatures().get(creature.id());
		Assert.assertNotEquals(startLocation, updated.location());
		Assert.assertEquals(4.9f, updated.velocity().z(), 0.001f);
		Assert.assertEquals(1, updated.ephemeral().movementPlan().size());
	}

	@Test
	public void waitForJump()
	{
		// In this test, we should just be waiting for a jump to have an effect.
		ProcessorElement thread = new ProcessorElement(0, new SyncPoint(1), new AtomicInteger(0));
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
				
				, new CreatureEntity.Ephemeral(
					movementPlan
					, 0L
					, false
					, 0L
					, CreatureEntity.NO_TARGET_ENTITY_ID
					, null
					, 0L
					, false
					, null
					, 0L
				)
		);
		Map<Integer, CreatureEntity> creaturesById = Map.of(creature.id(), creature);
		TickProcessingContext context = _createContext();
		Map<Integer, List<IEntityAction<IMutableCreatureEntity>>> changesToRun = Map.of();
		CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Map.of(), Map.of())
				, changesToRun
		);
		
		CreatureEntity updated = group.updatedCreatures().get(creature.id());
		Assert.assertNotEquals(startLocation, updated.location());
		Assert.assertEquals(2.94f, updated.velocity().z(), 0.001f);
		Assert.assertEquals(1, updated.ephemeral().movementPlan().size());
	}

	@Test
	public void moveAfterJump()
	{
		ProcessorElement thread = new ProcessorElement(0, new SyncPoint(1), new AtomicInteger(0));
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
				
				, new CreatureEntity.Ephemeral(
					movementPlan
					, 0L
					, false
					, 0L
					, CreatureEntity.NO_TARGET_ENTITY_ID
					, null
					, 0L
					, false
					, null
					, 0L
				)
		);
		
		// We will create a stone platform for the context so that the entity will fall into the expected block.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), AIR);
		_setCuboidLayer(cuboid, (byte)0, STONE.item().number());
		TickProcessingContext context = _createSingleCuboidContext(cuboid);
		Map<Integer, List<IEntityAction<IMutableCreatureEntity>>> changesToRun = Map.of();
		
		// We expect that there will be 2 moves to make to get into the right place to _begin_ moving into the new block.
		for (int i = 0; i < 2; ++i)
		{
			Map<Integer, CreatureEntity> creaturesById = Map.of(creatureId, creature);
			CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
					, creaturesById
					, context
					, new EntityCollection(Map.of(), Map.of())
					, changesToRun
			);
			creature = group.updatedCreatures().get(creatureId);
			Assert.assertNotNull(creature);
		}
		Assert.assertEquals(new EntityLocation(0.0f, 0.1f, 1.0f), creature.location());
		
		// There should now be just 1 step in the movement plan and the next invocation will cause us to convert it into steps, 19 in total.
		for (int i = 0; i < 19; ++i)
		{
			Map<Integer, CreatureEntity> creaturesById = Map.of(creatureId, creature);
			CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
					, creaturesById
					, context
					, new EntityCollection(Map.of(), Map.of())
					, changesToRun
			);
			creature = group.updatedCreatures().get(creatureId);
			Assert.assertNotNull(creature);
		}
		// We should be in the final location with a cleared movement plan by this point.
		Assert.assertEquals(new EntityLocation(0.0f, 1.0f, 1.0f), creature.location());
	}

	@Test
	public void resetPlanOnDamage()
	{
		ProcessorElement thread = new ProcessorElement(0, new SyncPoint(1), new AtomicInteger(0));
		EntityLocation startLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		byte startHealth = 100;
		CreatureEntity creature = CreatureEntity.create(-1, COW, startLocation, startHealth);
		Map<Integer, CreatureEntity> creaturesById = Map.of(creature.id(), creature);
		_Events events = new _Events();
		TickProcessingContext context = _createContextWithEvents(events);
		Map<Integer, List<IEntityAction<IMutableCreatureEntity>>> changesToRun = Map.of();
		CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Map.of(), Map.of())
				, changesToRun
		);
		
		CreatureEntity updated = group.updatedCreatures().get(creature.id());
		Assert.assertEquals(startHealth, updated.health());
		Assert.assertNotEquals(startLocation, updated.location());
		Assert.assertNotNull(updated.ephemeral().movementPlan());
		Assert.assertEquals(context.currentTick, updated.ephemeral().lastActionTick());
		
		// Now, hit them and see this clears their movement plan so we should see a plan with new timers.
		creaturesById = group.updatedCreatures();
		context = _updateContextForTick(context);
		int damage = 10;
		int sourceId = 1;
		changesToRun = Map.of(creature.id(), List.of(new EntityChangeTakeDamageFromEntity<>(BodyPart.FEET, damage, sourceId)));
		events.expected(new EventRecord(EventRecord.Type.ENTITY_HURT, EventRecord.Cause.ATTACKED, creature.location().getBlockLocation(), creature.id(), sourceId));
		group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Map.of(), Map.of())
				, changesToRun
		);
		
		updated = group.updatedCreatures().get(creature.id());
		Assert.assertEquals(startHealth - damage, updated.health());
		Assert.assertEquals(context.currentTick, updated.ephemeral().lastActionTick());
	}

	@Test
	public void followTheWheat()
	{
		// Create 3 entities, 2 holding wheat and one holding a tool, to show that we always path to the closest with wheat.
		ProcessorElement thread = new ProcessorElement(0, new SyncPoint(1), new AtomicInteger(0));
		EntityLocation startLocation = new EntityLocation(0.19f, 0.0f, 0.0f);
		CreatureEntity creature = CreatureEntity.create(-1, COW, startLocation, (byte)100);
		Map<Integer, CreatureEntity> creaturesById = Map.of(creature.id(), creature);
		Entity farWheat = _createEntity(1, new EntityLocation(5.0f, 0.0f, 0.0f), new Items(ENV.items.getItemById("op.wheat_item"), 2), null);
		Entity closeWheat = _createEntity(2, new EntityLocation(3.0f, 0.0f, 0.0f), new Items(ENV.items.getItemById("op.wheat_item"), 2), null);
		Entity nonWheat = _createEntity(3, new EntityLocation(2.0f, 0.0f, 0.0f), null, new NonStackableItem(ENV.items.getItemById("op.iron_pickaxe"), 100));
		TickProcessingContext context = _createContext();
		Map<Integer, List<IEntityAction<IMutableCreatureEntity>>> changesToRun = Map.of();
		CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Map.of(farWheat.id(), farWheat, closeWheat.id(), closeWheat, nonWheat.id(), nonWheat), creaturesById)
				, changesToRun
		);
		
		CreatureEntity updated = group.updatedCreatures().get(creature.id());
		Assert.assertNotEquals(startLocation, updated.location());
		
		// Make sure that the movement plan ends at the close wheat.
		List<AbsoluteLocation> movementPlan = updated.ephemeral().movementPlan();
		AbsoluteLocation endPoint = movementPlan.get(movementPlan.size() - 1);
		Assert.assertEquals(closeWheat.location().getBlockLocation(), endPoint);
		
		// Move the target entity and observe that the plan changes.
		closeWheat = _createEntity(closeWheat.id(), new EntityLocation(2.0f, 1.0f, 0.0f), new Items(ENV.items.getItemById("op.wheat_item"), 2), null);
		
		// Run the processor to observe the movement of the target entity.
		context = _updateContextWithPlayerAndCreatures(context, closeWheat, creaturesById);
		creaturesById = group.updatedCreatures();
		group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Map.of(farWheat.id(), farWheat, closeWheat.id(), closeWheat, nonWheat.id(), nonWheat), creaturesById)
				, changesToRun
		);
		updated = group.updatedCreatures().get(creature.id());
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
		ProcessorElement thread = new ProcessorElement(0, new SyncPoint(1), new AtomicInteger(0));
		EntityLocation startLocation = new EntityLocation(1.0f, 1.0f, 0.0f);
		CreatureEntity creature = CreatureEntity.create(-1, ORC, startLocation, (byte)100);
		Map<Integer, CreatureEntity> creaturesById = Map.of(creature.id(), creature);
		Entity player = _createEntity(1, new EntityLocation(5.0f, 1.0f, 0.0f), null, null);
		TickProcessingContext context = _createContext();
		context = _updateContextWithPlayerAndCreatures(context, player, creaturesById);
		Map<Integer, List<IEntityAction<IMutableCreatureEntity>>> changesToRun = Map.of();
		CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Map.of(player.id(), player), creaturesById)
				, changesToRun
		);
		
		// We should see that the orc has targeted the player and started moving toward them.
		CreatureEntity updated = group.updatedCreatures().get(creature.id());
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
		creaturesById = group.updatedCreatures();
		group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Map.of(player.id(), player), creaturesById)
				, changesToRun
		);
		updated = group.updatedCreatures().get(creature.id());
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
		ProcessorElement thread = new ProcessorElement(0, new SyncPoint(1), new AtomicInteger(0));
		EntityLocation startLocation = new EntityLocation(0.19f, 0.0f, 0.0f);
		Item wheat_item = ENV.items.getItemById("op.wheat_item");
		MutableCreature mutable = MutableCreature.existing(CreatureEntity.create(-1, COW, startLocation, (byte)100));
		CreatureLogic.applyItemToCreature(wheat_item, mutable);
		CreatureEntity fedCow = mutable.freeze();
		CreatureEntity otherCow = CreatureEntity.create(-2, COW, new EntityLocation(2.0f, 0.0f, 0.0f),(byte)100);
		mutable = MutableCreature.existing(CreatureEntity.create(-3, COW, new EntityLocation(5.0f, 0.0f, 0.0f), (byte)100));
		CreatureLogic.applyItemToCreature(wheat_item, mutable);
		CreatureEntity targetCow = mutable.freeze();
		Map<Integer, CreatureEntity> creaturesById = Map.of(fedCow.id(), fedCow
				, otherCow.id(), otherCow
				, targetCow.id(), targetCow
		);
		Entity closeWheat = _createEntity(1, new EntityLocation(3.0f, 0.0f, 0.0f), new Items(wheat_item, 2), null);
		TickProcessingContext context = _createContext();
		context = _updateContextWithPlayerAndCreatures(context, null, creaturesById);
		Map<Integer, List<IEntityAction<IMutableCreatureEntity>>> changesToRun = Map.of();
		CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
				, Map.of(fedCow.id(), fedCow)
				, context
				, new EntityCollection(Map.of(closeWheat.id(), closeWheat), creaturesById)
				, changesToRun
		);
		
		CreatureEntity updated = group.updatedCreatures().get(fedCow.id());
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
		ProcessorElement thread = new ProcessorElement(0, new SyncPoint(1), new AtomicInteger(0));
		CreatureIdAssigner idAssigner = new CreatureIdAssigner();
		EntityLocation location1 = new EntityLocation(0.0f, 0.0f, 0.0f);
		EntityLocation location2 = new EntityLocation(1.7f, 0.0f, 0.0f);
		EntityLocation playerLocation = new EntityLocation(0.5f, 0.5f, 0.0f);
		Item wheat_item = ENV.items.getItemById("op.wheat_item");
		MutableEntity mutablePlayer = MutableEntity.createWithLocation(1, playerLocation, playerLocation);
		mutablePlayer.newInventory.addAllItems(wheat_item, 3);
		mutablePlayer.setSelectedKey(1);
		Entity player = mutablePlayer.freeze();
		CreatureEntity cow1 = CreatureEntity.create(idAssigner.next(), COW, location1, (byte)100);
		CreatureEntity cow2 = CreatureEntity.create(idAssigner.next(), COW, location2, (byte)100);
		
		// First step, we should see the cows both take notice of the player since it has wheat in its hand.
		Map<Integer, CreatureEntity> creaturesById = new HashMap<>(Map.of(cow1.id(), cow1
				, cow2.id(), cow2
		));
		TickProcessingContext context = _createContext();
		context = _updateContextWithPlayerAndCreatures(context, player, creaturesById);
		CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Map.of(player.id(), player), creaturesById)
				, Map.of()
		);
		creaturesById.putAll(group.updatedCreatures());
		
		// Verify that they have set the targets.
		Assert.assertEquals(player.id(), creaturesById.get(cow1.id()).ephemeral().targetEntityId());
		Assert.assertEquals(player.id(), creaturesById.get(cow2.id()).ephemeral().targetEntityId());
		
		// Now, feed them using the mutations we would expect.
		context = _updateContextWithPlayerAndCreatures(context, player, creaturesById);
		group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Map.of(player.id(), player), creaturesById)
				, Map.of(cow1.id(), List.of(new EntityChangeApplyItemToCreature(wheat_item))
						, cow2.id(), List.of(new EntityChangeApplyItemToCreature(wheat_item))
				)
		);
		creaturesById.putAll(group.updatedCreatures());
		
		// The feeding will reset them but they can't yet see each other so they will pick an idle wander.
		Assert.assertFalse(creaturesById.get(cow1.id()).ephemeral().shouldTakeImmediateAction());
		Assert.assertFalse(creaturesById.get(cow1.id()).ephemeral().movementPlan().isEmpty());
		Assert.assertFalse(creaturesById.get(cow2.id()).ephemeral().shouldTakeImmediateAction());
		Assert.assertFalse(creaturesById.get(cow2.id()).ephemeral().movementPlan().isEmpty());
		// We want to clear this to observe the rest of the behaviour (otherwise, we would need to wait for them to move).
		creaturesById.put(cow1.id(), _takeAction(creaturesById.get(cow1.id())));
		creaturesById.put(cow2.id(), _takeAction(creaturesById.get(cow2.id())));
		
		// Run another tick so that they see each other in love mode.
		context = _updateContextWithPlayerAndCreatures(context, player, creaturesById);
		group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Map.of(player.id(), player), creaturesById)
				, Map.of(cow1.id(), List.of()
						, cow2.id(), List.of()
				)
		);
		creaturesById.putAll(group.updatedCreatures());
		
		// Verify that they now target each other.
		Assert.assertEquals(cow2.id(), creaturesById.get(cow1.id()).ephemeral().targetEntityId());
		Assert.assertEquals(cow1.id(), creaturesById.get(cow2.id()).ephemeral().targetEntityId());
		
		// Nothing should have happened yet, as they just found their mating partners so run the next tick.
		Map<Integer, IEntityAction<IMutableCreatureEntity>> changes = new HashMap<>();
		context = _updateContextWithCreatures(context, creaturesById.values(), new IChangeSink() {
			@Override
			public void next(int targetEntityId, IEntityAction<IMutablePlayerEntity> change)
			{
				throw new AssertionError("Not in test");
			}
			@Override
			public void future(int targetEntityId, IEntityAction<IMutablePlayerEntity> change, long millisToDelay)
			{
				throw new AssertionError("Not in test");
			}
			@Override
			public void creature(int targetCreatureId, IEntityAction<IMutableCreatureEntity> change)
			{
				Assert.assertFalse(changes.containsKey(targetCreatureId));
				changes.put(targetCreatureId, change);
			}
		}, null, null);
		group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Map.of(), creaturesById)
				, Map.of()
		);
		
		// Verify that cow1 sent a message to cow2.
		Assert.assertEquals(1, changes.size());
		Assert.assertTrue(changes.get(cow2.id()) instanceof EntityChangeImpregnateCreature);
		
		// Run another tick to see the mother receive this request and spawn the offspring.
		creaturesById.putAll(group.updatedCreatures());
		CreatureEntity[] out = new CreatureEntity[1];
		context = _updateContextWithCreatures(context, creaturesById.values(), null, out, idAssigner);
		group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Map.of(), creaturesById)
				, Map.of(cow2.id(), List.of(changes.get(cow2.id())))
		);
		Assert.assertNotNull(out[0]);
		CreatureEntity offspring = out[0];
		Assert.assertEquals(-3, offspring.id());
		Assert.assertEquals(COW, offspring.type());
		
		// Run another tick to observe that nothing special happens.
		creaturesById.putAll(group.updatedCreatures());
		Assert.assertNull(creaturesById.get(cow2.id()).ephemeral().movementPlan());
		creaturesById.put(offspring.id(), offspring);
		context = _updateContextWithCreatures(context, creaturesById.values(), null, null, null);
		group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Map.of(), creaturesById)
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
		
		ProcessorElement thread = new ProcessorElement(0, new SyncPoint(1), new AtomicInteger(0));
		EntityLocation startLocation = new EntityLocation(8.5f, 8.0f, 1.0f);
		CreatureEntity creature = CreatureEntity.create(-1, ORC, startLocation, (byte)100);
		Map<Integer, CreatureEntity> creaturesById = Map.of(creature.id(), creature);
		TickProcessingContext context = _createSingleCuboidContext(cuboid);
		Map<Integer, List<IEntityAction<IMutableCreatureEntity>>> changesToRun = Map.of();
		CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Map.of(), creaturesById)
				, changesToRun
		);
		
		CreatureEntity updated = group.updatedCreatures().get(creature.id());
		// The cow should first position itself against the wall before making the jump.
		// Note that it will take 5 steps, instead of 3, since this is an idle movement (half-speed but also ends in a jump).
		for (int i = 0; i < 5; ++i)
		{
			Assert.assertEquals(0.0f, updated.velocity().z(), 0.001f);
			creaturesById = group.updatedCreatures();
			group = CreatureProcessor.processCreatureGroupParallel(thread
					, creaturesById
					, context
					, new EntityCollection(Map.of(), creaturesById)
					, changesToRun
			);
			updated = group.updatedCreatures().get(creature.id());
		}
		// We should now be against the wall.
		Assert.assertEquals(8.0f, updated.location().x(), 0.01f);
		
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
			creaturesById = group.updatedCreatures();
			group = CreatureProcessor.processCreatureGroupParallel(thread
					, creaturesById
					, context
					, new EntityCollection(Map.of(), creaturesById)
					, changesToRun
			);
			justUpdated = group.updatedCreatures().get(creature.id());
		}
		
		// By this point we should be on the ground, in the right block, with no plan.
		Assert.assertEquals(7.5f, updated.location().x(), 0.01f);
		Assert.assertEquals(9.05f, updated.location().y(), 0.01f);
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
		_setCuboidLayer(cuboid, (byte)1, ENV.items.getItemById("op.water_source").number());
		_setCuboidLayer(cuboid, (byte)16, STONE.item().number());
		ProcessorElement thread = new ProcessorElement(0, new SyncPoint(1), new AtomicInteger(0));
		
		EntityLocation waterStart = new EntityLocation(2.0f, 2.0f, 1.0f);
		EntityLocation airStart = new EntityLocation(2.0f, 2.0f, 17.0f);
		CreatureEntity waterCreature = CreatureEntity.create(-1, ORC, waterStart, (byte)100);
		CreatureEntity airCreature = CreatureEntity.create(-2, ORC, airStart, (byte)100);
		float targetDistance = 4.0f;
		Entity waterTarget = _createEntity(1, new EntityLocation(waterStart.x() + targetDistance, waterStart.y(), waterStart.z()), null, null);
		Entity airTarget = _createEntity(2, new EntityLocation(airStart.x() + targetDistance, airStart.y(), airStart.z()), null, null);
		
		Map<Integer, CreatureEntity> creaturesById = Map.of(waterCreature.id(), waterCreature
				, airCreature.id(), airCreature
		);
		long millisPerTick = 100L;
		
		Map<Integer, List<IEntityAction<IMutableCreatureEntity>>> changesToRun = Map.of();
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
						}, (Integer id) -> minimalEntitiesById.get(id))
					// We return a fixed "1" for the random generator to make sure that we select a reasonable plan for all tests.
					.fixedRandom(1)
					.finish()
			;
			CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
					, creaturesById
					, context
					, new EntityCollection(Map.of(waterTarget.id(), waterTarget, airTarget.id(), airTarget), creaturesById)
					, changesToRun
			);
			creaturesById = group.updatedCreatures();
			float oldWater = waterCreature.location().x();
			float oldAir = waterCreature.location().x();
			waterCreature = group.updatedCreatures().get(waterCreature.id());
			airCreature = group.updatedCreatures().get(airCreature.id());
			// Make sure that both are making progress.
			Assert.assertTrue(waterCreature.location().x() > oldWater);
			Assert.assertTrue(airCreature.location().x() > oldAir);
			// Make sure that the air creature is further ahead.
			Assert.assertTrue(airCreature.location().x() > waterCreature.location().x());
		}
		Assert.assertEquals(3.0f, waterCreature.location().x(), 0.01f);
		Assert.assertEquals(4.0f, airCreature.location().x(), 0.01f);
	}

	@Test
	public void swimToSurface()
	{
		// Show a creature swimming to the surface of a body of water.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), AIR);
		short waterSourceNumber = ENV.items.getItemById("op.water_source").number();
		_setCuboidLayer(cuboid, (byte)0, STONE.item().number());
		_setCuboidLayer(cuboid, (byte)1, waterSourceNumber);
		_setCuboidLayer(cuboid, (byte)2, waterSourceNumber);
		ProcessorElement thread = new ProcessorElement(0, new SyncPoint(1), new AtomicInteger(0));
		
		EntityLocation startLocation = new EntityLocation(8.0f, 8.0f, 1.0f);
		CreatureEntity creature = CreatureEntity.create(-1, ORC, startLocation, (byte)100);
		// We need to reduce their breath to trigger this response.
		MutableCreature mutable = MutableCreature.existing(creature);
		mutable.newBreath -= 1;
		creature = mutable.freeze();
		
		Map<Integer, CreatureEntity> creaturesById = Map.of(creature.id(), creature);
		long millisPerTick = 100L;
		
		Map<Integer, List<IEntityAction<IMutableCreatureEntity>>> changesToRun = Map.of();
		for (int i = 0; i < 5; ++i)
		{
			TickProcessingContext context = ContextBuilder.build()
					.tick(CreatureLogic.MINIMUM_MILLIS_TO_ACTION / millisPerTick)
					.lookups((AbsoluteLocation location) -> {
							return (cuboid.getCuboidAddress().equals(location.getCuboidAddress()))
								? new BlockProxy(location.getBlockAddress(), cuboid)
								: null
							;
						}, null)
					// We return a fixed "0" for the random generator to make sure that we select a reasonable plan for all tests.
					.fixedRandom(0)
					.finish()
			;
			CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
					, creaturesById
					, context
					, new EntityCollection(Map.of(), creaturesById)
					, changesToRun
			);
			creaturesById = group.updatedCreatures();
			float oldZ = creature.location().z();
			creature = group.updatedCreatures().get(creature.id());
			// Make sure that we are rising.
			Assert.assertTrue(creature.location().z() > oldZ);
			
			// We expect the 6th iteration to be the final one (since we now swim up quickly).
			if (i < 5)
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
		Assert.assertEquals(3.35f, creature.location().z(), 0.01f);
	}

	@Test
	public void fallDamage()
	{
		// Show that a creature takes damage when falling.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(16, 16, 15), STONE.item().number());
		
		byte health = 100;
		float fallingVelocity = TickUtils.DECELERATION_DAMAGE_THRESHOLD + (TickUtils.DECELERATION_DAMAGE_RANGE / 2.0f);
		EntityLocation startLocation = new EntityLocation(16.8f, 16.8f, 16.1f);
		EntityLocation startVelocity = new EntityLocation(0.0f, 0.0f, fallingVelocity);
		CreatureEntity creature = CreatureEntity.create(-1, ORC, startLocation, health);
		MutableCreature mutable = MutableCreature.existing(creature);
		mutable.newVelocity = startVelocity;
		creature = mutable.freeze();
		
		ProcessorElement thread = new ProcessorElement(0, new SyncPoint(1), new AtomicInteger(0));
		_Events events = new _Events();
		TickProcessingContext context = ContextBuilder.build()
				.tick(MiscConstants.DAMAGE_TAKEN_TIMEOUT_MILLIS / ContextBuilder.DEFAULT_MILLIS_PER_TICK)
				.lookups((AbsoluteLocation location) -> {
						return (cuboid.getCuboidAddress().equals(location.getCuboidAddress()))
							? new BlockProxy(location.getBlockAddress(), cuboid)
							: null
						;
					}, null)
				.eventSink(events)
				.finish()
		;
		events.expected(new EventRecord(EventRecord.Type.ENTITY_HURT, EventRecord.Cause.FALL, creature.location().getBlockLocation(), creature.id(), 0));
		CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
				, Map.of(creature.id(), creature)
				, context
				, new EntityCollection(Map.of(), Map.of(creature.id(), creature))
				, Map.of()
		);
		creature = group.updatedCreatures().get(creature.id());
		
		Assert.assertEquals(new EntityLocation(16.8f, 16.8f, 16.0f), creature.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), creature.velocity());
		Assert.assertEquals((byte)37, creature.health());
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
					} , null)
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
					} , null)
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
		TickProcessingContext.ICreatureSpawner spawner = (EntityType type, EntityLocation location, byte health) -> {
			Assert.assertNull(outSpawnedCreature[0]);
			outSpawnedCreature[0] = CreatureEntity.create(idAssigner.next(), type, location, health);
		};
		Map<Integer, MinimalEntity> minimal = creatures.stream().collect(Collectors.toMap((CreatureEntity creature) -> creature.id(), (CreatureEntity creature) -> MinimalEntity.fromCreature(creature)));
		TickProcessingContext context = ContextBuilder.nextTick(existing, 1L)
				.lookups(existing.previousBlockLookUp, (Integer id) -> minimal.get(id))
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
				})
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
				, null
				, (byte)0
				, (byte)0
				, MiscConstants.MAX_BREATH
				, 0
				, MutableEntity.TESTING_LOCATION
				, Entity.EMPTY_DATA
		);
	}

	private void _setCuboidLayer(CuboidData cuboid, byte z, short number)
	{
		for (byte y = 0; y < 16; ++y)
		{
			for (byte x = 0; x < 16; ++x)
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
				
				, new CreatureEntity.Ephemeral(
					movementPlan
					, entity.ephemeral().lastActionTick()
					, shouldTakeImmediateAction
					, entity.ephemeral().despawnKeepAliveTick()
					, targetEntityId
					, targetPreviousLocation
					, entity.ephemeral().lastAttackTick()
					, entity.ephemeral().inLoveMode()
					, entity.ephemeral().offspringLocation()
					, entity.ephemeral().lastDamageTakenMillis()
				)
		);
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
