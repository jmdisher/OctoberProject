package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.creatures.CowStateMachine;
import com.jeffdisher.october.creatures.CreatureLogic;
import com.jeffdisher.october.creatures.OrcStateMachine;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.mutations.EntityChangeImpregnateCreature;
import com.jeffdisher.october.mutations.EntityChangeTakeDamageFromEntity;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.mutations.MutationBlockStoreItems;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.ContextBuilder;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Difficulty;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityConstants;
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
import com.jeffdisher.october.worldgen.CuboidGenerator;


public class TestCreatureProcessor
{
	private static Environment ENV;
	private static Block AIR;
	private static Block STONE;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		AIR = ENV.blocks.fromItem(ENV.items.getItemById("op.air"));
		STONE = ENV.blocks.fromItem(ENV.items.getItemById("op.stone"));
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
		CreatureEntity creature = CreatureEntity.create(-1, EntityType.COW, new EntityLocation(0.0f, 0.0f, 0.0f), (byte)100);
		Map<Integer, CreatureEntity> creaturesById = Map.of(creature.id(), creature);
		_Events events = new _Events();
		TickProcessingContext context = _createContextWithEvents(events);
		int sourceId = 1;
		EntityChangeTakeDamageFromEntity<IMutableCreatureEntity> change = new EntityChangeTakeDamageFromEntity<>(BodyPart.FEET, 10, sourceId);
		Map<Integer, List<IMutationEntity<IMutableCreatureEntity>>> changesToRun = Map.of(creature.id(), List.of(change));
		events.expected(new EventRecord(EventRecord.Type.ENTITY_HURT, EventRecord.Cause.ATTACKED, creature.location().getBlockLocation(), creature.id(), sourceId));
		CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Set.of(), Set.of())
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
		CreatureEntity creature = CreatureEntity.create(-1, EntityType.COW, new EntityLocation(0.0f, 0.0f, 0.0f), (byte)50);
		Map<Integer, CreatureEntity> creaturesById = Map.of(creature.id(), creature);
		IMutationBlock[] mutationHolder = new IMutationBlock[1];
		CuboidData fakeCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), STONE);
		_Events events = new _Events();
		TickProcessingContext context = ContextBuilder.build()
				.lookups((AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), fakeCuboid), null)
				.sinks(new IMutationSink() {
					@Override
					public void next(IMutationBlock mutation)
					{
						Assert.assertNull(mutationHolder[0]);
						mutationHolder[0] = mutation;
					}
				}, null)
				.eventSink(events)
				.finish()
		;
		int sourceId = 1;
		EntityChangeTakeDamageFromEntity<IMutableCreatureEntity> change = new EntityChangeTakeDamageFromEntity<>(BodyPart.FEET, 120, sourceId);
		Map<Integer, List<IMutationEntity<IMutableCreatureEntity>>> changesToRun = Map.of(creature.id(), List.of(change));
		events.expected(new EventRecord(EventRecord.Type.ENTITY_KILLED, EventRecord.Cause.ATTACKED, creature.location().getBlockLocation(), creature.id(), sourceId));
		CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Set.of(), Set.of())
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
		CreatureEntity creature = CreatureEntity.create(-1, EntityType.COW, startLocation, (byte)100);
		Map<Integer, CreatureEntity> creaturesById = Map.of(creature.id(), creature);
		TickProcessingContext context = _createContext();
		Map<Integer, List<IMutationEntity<IMutableCreatureEntity>>> changesToRun = Map.of();
		CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Set.of(), Set.of())
				, changesToRun
		);
		
		CreatureEntity updated = group.updatedCreatures().get(creature.id());
		Assert.assertNotEquals(startLocation, updated.location());
		Assert.assertNotNull(CowStateMachine.decodeExtendedData(updated.extendedData()).movementPlan());
	}

	@Test
	public void despawnOrcOnMovementDecision()
	{
		ProcessorElement thread = new ProcessorElement(0, new SyncPoint(1), new AtomicInteger(0));
		EntityLocation startLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		CreatureEntity creature = CreatureEntity.create(-1, EntityType.ORC, startLocation, (byte)50);
		Map<Integer, CreatureEntity> creaturesById = Map.of(creature.id(), creature);
		TickProcessingContext context = _createContextWithOptions(Difficulty.PEACEFUL, null);
		Map<Integer, List<IMutationEntity<IMutableCreatureEntity>>> changesToRun = Map.of();
		CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Set.of(), Set.of())
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
				, EntityType.COW
				, startLocation
				, velocity
				, (byte)0
				, (byte)0
				, (byte)100
				, EntityConstants.MAX_BREATH
				, CowStateMachine.encodeExtendedData(new CowStateMachine.Test_ExtendedData(false, movementPlan, 0, null, null, 0L, 0L))
		);
		Map<Integer, CreatureEntity> creaturesById = Map.of(creature.id(), creature);
		TickProcessingContext context = _createContext();
		Map<Integer, List<IMutationEntity<IMutableCreatureEntity>>> changesToRun = Map.of();
		CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Set.of(), Set.of())
				, changesToRun
		);
		
		CreatureEntity updated = group.updatedCreatures().get(creature.id());
		Assert.assertNotEquals(startLocation, updated.location());
		Assert.assertEquals(3.92f, updated.velocity().z(), 0.001f);
		Assert.assertEquals(1, CowStateMachine.decodeExtendedData(updated.extendedData()).movementPlan().size());
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
				, EntityType.COW
				, startLocation
				, velocity
				, (byte)0
				, (byte)0
				, (byte)100
				, EntityConstants.MAX_BREATH
				, CowStateMachine.encodeExtendedData(new CowStateMachine.Test_ExtendedData(false, movementPlan, 0, null, null, 0L, 0L))
		);
		Map<Integer, CreatureEntity> creaturesById = Map.of(creature.id(), creature);
		TickProcessingContext context = _createContext();
		Map<Integer, List<IMutationEntity<IMutableCreatureEntity>>> changesToRun = Map.of();
		CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Set.of(), Set.of())
				, changesToRun
		);
		
		CreatureEntity updated = group.updatedCreatures().get(creature.id());
		Assert.assertNotEquals(startLocation, updated.location());
		Assert.assertEquals(2.94f, updated.velocity().z(), 0.001f);
		Assert.assertEquals(1, CowStateMachine.decodeExtendedData(updated.extendedData()).movementPlan().size());
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
				, EntityType.COW
				, startLocation
				, velocity
				, (byte)0
				, (byte)0
				, (byte)100
				, EntityConstants.MAX_BREATH
				, CowStateMachine.encodeExtendedData(new CowStateMachine.Test_ExtendedData(false, movementPlan, 0, null, null, 0L, 0L))
		);
		
		// We will create a stone platform for the context so that the entity will fall into the expected block.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), AIR);
		_setCuboidLayer(cuboid, (byte)0, STONE.item().number());
		TickProcessingContext context = _createSingleCuboidContext(cuboid);
		Map<Integer, List<IMutationEntity<IMutableCreatureEntity>>> changesToRun = Map.of();
		
		// We expect that there will be 2 moves to make to get into the right place to _begin_ moving into the new block.
		for (int i = 0; i < 2; ++i)
		{
			Map<Integer, CreatureEntity> creaturesById = Map.of(creatureId, creature);
			CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
					, creaturesById
					, context
					, new EntityCollection(Set.of(), Set.of())
					, changesToRun
			);
			creature = group.updatedCreatures().get(creatureId);
			Assert.assertNotNull(creature);
		}
		Assert.assertEquals(new EntityLocation(0.0f, 0.2f, 1.0f), creature.location());
		
		// There should now be just 1 step in the movement plan and the next invocation will cause us to convert it into steps, 9 in total.
		for (int i = 0; i < 9; ++i)
		{
			Map<Integer, CreatureEntity> creaturesById = Map.of(creatureId, creature);
			CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
					, creaturesById
					, context
					, new EntityCollection(Set.of(), Set.of())
					, changesToRun
			);
			creature = group.updatedCreatures().get(creatureId);
			Assert.assertNotNull(creature);
		}
		// We should be in the final location with a cleared movement plan by this point.
		Assert.assertEquals(new EntityLocation(0.0f, 1.0f, 1.0f), creature.location());
		Assert.assertNull(creature.extendedData());
	}

	@Test
	public void resetPlanOnDamage()
	{
		ProcessorElement thread = new ProcessorElement(0, new SyncPoint(1), new AtomicInteger(0));
		EntityLocation startLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		byte startHealth = 100;
		CreatureEntity creature = CreatureEntity.create(-1, EntityType.COW, startLocation, startHealth);
		Map<Integer, CreatureEntity> creaturesById = Map.of(creature.id(), creature);
		_Events events = new _Events();
		TickProcessingContext context = _createContextWithEvents(events);
		Map<Integer, List<IMutationEntity<IMutableCreatureEntity>>> changesToRun = Map.of();
		CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Set.of(), Set.of())
				, changesToRun
		);
		
		CreatureEntity updated = group.updatedCreatures().get(creature.id());
		Assert.assertEquals(startHealth, updated.health());
		Assert.assertNotEquals(startLocation, updated.location());
		CowStateMachine.Test_ExtendedData extended = CowStateMachine.decodeExtendedData(updated.extendedData());
		Assert.assertNotNull(extended.movementPlan());
		long previousTickTimer = extended.nextIdleActTick();
		
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
				, new EntityCollection(Set.of(), Set.of())
				, changesToRun
		);
		
		updated = group.updatedCreatures().get(creature.id());
		Assert.assertEquals(startHealth - damage, updated.health());
		extended = CowStateMachine.decodeExtendedData(updated.extendedData());
		Assert.assertEquals(previousTickTimer + 1, extended.nextIdleActTick());
	}

	@Test
	public void followTheWheat()
	{
		// Create 3 entities, 2 holding wheat and one holding a tool, to show that we always path to the closest with wheat.
		ProcessorElement thread = new ProcessorElement(0, new SyncPoint(1), new AtomicInteger(0));
		EntityLocation startLocation = new EntityLocation(0.19f, 0.0f, 0.0f);
		CreatureEntity creature = CreatureEntity.create(-1, EntityType.COW, startLocation, (byte)100);
		Map<Integer, CreatureEntity> creaturesById = Map.of(creature.id(), creature);
		Entity farWheat = _createEntity(1, new EntityLocation(5.0f, 0.0f, 0.0f), new Items(ENV.items.getItemById("op.wheat_item"), 2), null);
		Entity closeWheat = _createEntity(1, new EntityLocation(3.0f, 0.0f, 0.0f), new Items(ENV.items.getItemById("op.wheat_item"), 2), null);
		Entity nonWheat = _createEntity(1, new EntityLocation(2.0f, 0.0f, 0.0f), null, new NonStackableItem(ENV.items.getItemById("op.iron_pickaxe"), 100));
		TickProcessingContext context = _createContext();
		Map<Integer, List<IMutationEntity<IMutableCreatureEntity>>> changesToRun = Map.of();
		CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Set.of(farWheat, closeWheat, nonWheat), creaturesById.values())
				, changesToRun
		);
		
		CreatureEntity updated = group.updatedCreatures().get(creature.id());
		Assert.assertNotEquals(startLocation, updated.location());
		
		// Make sure that the movement plan ends at the close wheat.
		List<AbsoluteLocation> movementPlan = CowStateMachine.decodeExtendedData(updated.extendedData()).movementPlan();
		AbsoluteLocation endPoint = movementPlan.get(movementPlan.size() - 1);
		Assert.assertEquals(closeWheat.location().getBlockLocation(), endPoint);
		
		// Move the target entity and observe that the plan changes.
		closeWheat = _createEntity(1, new EntityLocation(2.0f, 1.0f, 0.0f), new Items(ENV.items.getItemById("op.wheat_item"), 2), null);
		
		// We need to finish the previous step before a new one will be created so loop 6 times (5 to finish the step and one to decide on new).
		for (int i = 0; i < 6; ++i)
		{
			context = _updateContextWithPlayer(context, closeWheat);
			creaturesById = group.updatedCreatures();
			group = CreatureProcessor.processCreatureGroupParallel(thread
					, creaturesById
					, context
					, new EntityCollection(Set.of(farWheat, closeWheat, nonWheat), creaturesById.values())
					, changesToRun
			);
			updated = group.updatedCreatures().get(creature.id());
			Assert.assertNotNull(updated);
		}
		
		// Make sure that the movement plan ends at the NEW close wheat location.
		movementPlan = CowStateMachine.decodeExtendedData(updated.extendedData()).movementPlan();
		endPoint = movementPlan.get(movementPlan.size() - 1);
		Assert.assertEquals(closeWheat.location().getBlockLocation(), endPoint);
	}

	@Test
	public void cowsInLoveMode()
	{
		// Create 1 player holding wheat and 2 cows:  a distant one in love mode and a closer not in love mode.  Show that a cow priorizes the love cow.
		ProcessorElement thread = new ProcessorElement(0, new SyncPoint(1), new AtomicInteger(0));
		EntityLocation startLocation = new EntityLocation(0.19f, 0.0f, 0.0f);
		Item wheat_item = ENV.items.getItemById("op.wheat_item");
		MutableCreature mutable = MutableCreature.existing(CreatureEntity.create(-1, EntityType.COW, startLocation, (byte)100));
		CreatureLogic.applyItemToCreature(wheat_item, mutable);
		CreatureEntity fedCow = mutable.freeze();
		CreatureEntity otherCow = CreatureEntity.create(-2, EntityType.COW, new EntityLocation(2.0f, 0.0f, 0.0f),(byte)100);
		mutable = MutableCreature.existing(CreatureEntity.create(-3, EntityType.COW, new EntityLocation(5.0f, 0.0f, 0.0f), (byte)100));
		CreatureLogic.applyItemToCreature(wheat_item, mutable);
		CreatureEntity targetCow = mutable.freeze();
		Map<Integer, CreatureEntity> creaturesById = Map.of(fedCow.id(), fedCow
				, otherCow.id(), otherCow
				, targetCow.id(), targetCow
		);
		Entity closeWheat = _createEntity(1, new EntityLocation(3.0f, 0.0f, 0.0f), new Items(wheat_item, 2), null);
		TickProcessingContext context = _createContext();
		Map<Integer, List<IMutationEntity<IMutableCreatureEntity>>> changesToRun = Map.of();
		CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
				, Map.of(fedCow.id(), fedCow)
				, context
				, new EntityCollection(Set.of(closeWheat), creaturesById.values())
				, changesToRun
		);
		
		CreatureEntity updated = group.updatedCreatures().get(fedCow.id());
		Assert.assertNotEquals(startLocation, updated.location());
		
		// Make sure that the movement plan ends at the close target cow.
		List<AbsoluteLocation> movementPlan = CowStateMachine.decodeExtendedData(updated.extendedData()).movementPlan();
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
		EntityLocation location2 = new EntityLocation(0.9f, 0.0f, 0.0f);
		Item wheat_item = ENV.items.getItemById("op.wheat_item");
		MutableCreature mutable = MutableCreature.existing(CreatureEntity.create(idAssigner.next(), EntityType.COW, location1, (byte)100));
		CreatureLogic.applyItemToCreature(wheat_item, mutable);
		CreatureEntity cow1 = mutable.freeze();
		mutable = MutableCreature.existing(CreatureEntity.create(idAssigner.next(), EntityType.COW, location2, (byte)100));
		CreatureLogic.applyItemToCreature(wheat_item, mutable);
		CreatureEntity cow2 = mutable.freeze();
		
		Map<Integer, CreatureEntity> creaturesById = new HashMap<>(Map.of(cow1.id(), cow1
				, cow2.id(), cow2
		));
		TickProcessingContext context = _createContext();
		CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Set.of(), creaturesById.values())
				, Map.of()
		);
		
		// Nothing should have happened yet, as they just found their mating partners so run the next tick.
		creaturesById.putAll(group.updatedCreatures());
		Map<Integer, IMutationEntity<IMutableCreatureEntity>> changes = new HashMap<>();
		context = _updateContextWithCreatures(context, creaturesById.values(), new IChangeSink() {
			@Override
			public void next(int targetEntityId, IMutationEntity<IMutablePlayerEntity> change)
			{
				throw new AssertionError("Not in test");
			}
			@Override
			public void future(int targetEntityId, IMutationEntity<IMutablePlayerEntity> change, long millisToDelay)
			{
				throw new AssertionError("Not in test");
			}
			@Override
			public void creature(int targetCreatureId, IMutationEntity<IMutableCreatureEntity> change)
			{
				Assert.assertFalse(changes.containsKey(targetCreatureId));
				changes.put(targetCreatureId, change);
			}
		}, null);
		group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Set.of(), creaturesById.values())
				, Map.of()
		);
		
		// Verify that cow1 sent a message to cow2.
		Assert.assertEquals(1, changes.size());
		Assert.assertTrue(changes.get(cow2.id()) instanceof EntityChangeImpregnateCreature);
		
		// Run another tick to see the mother receive this request and spawn the offspring.
		creaturesById.putAll(group.updatedCreatures());
		context = _updateContextWithCreatures(context, creaturesById.values(), null, idAssigner);
		group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Set.of(), creaturesById.values())
				, Map.of(cow2.id(), List.of(changes.get(cow2.id())))
		);
		Assert.assertEquals(1, group.newlySpawnedCreatures().size());
		CreatureEntity offspring = group.newlySpawnedCreatures().get(0);
		Assert.assertEquals(-3, offspring.id());
		Assert.assertEquals(EntityType.COW, offspring.type());
		
		// Run another tick to observe that nothing special happens.
		creaturesById.putAll(group.updatedCreatures());
		CowStateMachine.Test_ExtendedData extended = CowStateMachine.decodeExtendedData(creaturesById.get(cow2.id()).extendedData());
		Assert.assertNull(extended.movementPlan());
		Assert.assertNull(extended.offspringLocation());
		creaturesById.put(offspring.id(), offspring);
		context = _updateContextWithCreatures(context, creaturesById.values(), null, null);
		group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Set.of(), creaturesById.values())
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
		CreatureEntity creature = CreatureEntity.create(-1, EntityType.ORC, startLocation, (byte)100);
		Map<Integer, CreatureEntity> creaturesById = Map.of(creature.id(), creature);
		TickProcessingContext context = _createSingleCuboidContext(cuboid);
		Map<Integer, List<IMutationEntity<IMutableCreatureEntity>>> changesToRun = Map.of();
		CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Set.of(), creaturesById.values())
				, changesToRun
		);
		
		CreatureEntity updated = group.updatedCreatures().get(creature.id());
		// The cow should first position itself against the wall before making the jump.
		// Note that it will take 3 steps, instead of 2, since this is an idle movement (half-speed but also ends in a jump).
		for (int i = 0; i < 3; ++i)
		{
			Assert.assertEquals(0.0f, updated.velocity().z(), 0.001f);
			creaturesById = group.updatedCreatures();
			group = CreatureProcessor.processCreatureGroupParallel(thread
					, creaturesById
					, context
					, new EntityCollection(Set.of(), creaturesById.values())
					, changesToRun
			);
			updated = group.updatedCreatures().get(creature.id());
		}
		// We should now be against the wall.
		Assert.assertEquals(8.01f, updated.location().x(), 0.01f);
		
		// The cow should have jumped, so verify the location, z-velocity, and no plan to the next step.
		Assert.assertNotEquals(startLocation, updated.location());
		Assert.assertNotEquals(0.0f, updated.velocity().z());
		
		// Verify that the movement plan is the one we expected (since we depend on knowing which direction we are moving for the test).
		List<AbsoluteLocation> movementPlan = OrcStateMachine.decodeExtendedData(updated.extendedData()).movementPlan();
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
					, new EntityCollection(Set.of(), creaturesById.values())
					, changesToRun
			);
			justUpdated = group.updatedCreatures().get(creature.id());
		}
		
		// By this point we should be on the ground, in the right block, with no plan.
		Assert.assertEquals(7.59f, updated.location().x(), 0.01f);
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
		_setCuboidLayer(cuboid, (byte)1, ENV.items.getItemById("op.water_source").number());
		_setCuboidLayer(cuboid, (byte)16, STONE.item().number());
		ProcessorElement thread = new ProcessorElement(0, new SyncPoint(1), new AtomicInteger(0));
		
		EntityLocation waterStart = new EntityLocation(2.0f, 2.0f, 1.0f);
		EntityLocation airStart = new EntityLocation(2.0f, 2.0f, 17.0f);
		CreatureEntity waterCreature = CreatureEntity.create(-1, EntityType.ORC, waterStart, (byte)100);
		CreatureEntity airCreature = CreatureEntity.create(-2, EntityType.ORC, airStart, (byte)100);
		float targetDistance = 4.0f;
		Entity waterTarget = _createEntity(1, new EntityLocation(waterStart.x() + targetDistance, waterStart.y(), waterStart.z()), null, null);
		Entity airTarget = _createEntity(2, new EntityLocation(airStart.x() + targetDistance, airStart.y(), airStart.z()), null, null);
		
		Map<Integer, CreatureEntity> creaturesById = Map.of(waterCreature.id(), waterCreature
				, airCreature.id(), airCreature
		);
		long millisPerTick = 100L;
		
		Map<Integer, List<IMutationEntity<IMutableCreatureEntity>>> changesToRun = Map.of();
		for (int i = 0; i < 10; ++i)
		{
			Map<Integer, MinimalEntity> minimalEntitiesById = Map.of(waterTarget.id(), MinimalEntity.fromEntity(waterTarget)
					, airTarget.id(), MinimalEntity.fromEntity(airTarget)
			);
			TickProcessingContext context = ContextBuilder.build()
					.tick(OrcStateMachine.MINIMUM_MILLIS_TO_IDLE_ACTION / millisPerTick)
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
					, new EntityCollection(Set.of(waterTarget, airTarget), creaturesById.values())
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
			// Make sure we still have our plans.
			Assert.assertNotNull(waterCreature.extendedData());
			Assert.assertNotNull(airCreature.extendedData());
		}
		Assert.assertEquals(3.5f, waterCreature.location().x(), 0.01f);
		Assert.assertEquals(5.0f, airCreature.location().x(), 0.01f);
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
		CreatureEntity creature = CreatureEntity.create(-1, EntityType.ORC, startLocation, (byte)100);
		// We need to reduce their breath to trigger this response.
		MutableCreature mutable = MutableCreature.existing(creature);
		mutable.newBreath -= 1;
		creature = mutable.freeze();
		
		Map<Integer, CreatureEntity> creaturesById = Map.of(creature.id(), creature);
		long millisPerTick = 100L;
		
		Map<Integer, List<IMutationEntity<IMutableCreatureEntity>>> changesToRun = Map.of();
		for (int i = 0; i < 11; ++i)
		{
			TickProcessingContext context = ContextBuilder.build()
					.tick(OrcStateMachine.MINIMUM_MILLIS_TO_IDLE_ACTION / millisPerTick)
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
					, new EntityCollection(Set.of(), creaturesById.values())
					, changesToRun
			);
			creaturesById = group.updatedCreatures();
			float oldZ = creature.location().z();
			creature = group.updatedCreatures().get(creature.id());
			// Make sure that we are rising.
			Assert.assertTrue(creature.location().z() > oldZ);
			
			// We expect the 11th iteration to be the final one (since we now swim up quickly).
			if (i < 10)
			{
				Assert.assertNotNull(creature.extendedData());
			}
			else
			{
				OrcStateMachine.Test_ExtendedData extended = OrcStateMachine.decodeExtendedData(creature.extendedData());
				Assert.assertNull(extended.movementPlan());
			}
		}
		// We should be in the same column but higher.
		Assert.assertEquals(startLocation.x(), creature.location().x(), 0.01f);
		Assert.assertEquals(startLocation.y(), creature.location().y(), 0.01f);
		Assert.assertEquals(3.54f, creature.location().z(), 0.01f);
	}

	@Test
	public void fallDamage()
	{
		// Show that a creature takes damage when falling.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(16, 16, 15), STONE.item().number());
		
		byte health = 100;
		EntityLocation startLocation = new EntityLocation(16.8f, 16.8f, 16.1f);
		EntityLocation startVelocity = new EntityLocation(0.0f, 0.0f, MotionHelpers.FALLING_TERMINAL_VELOCITY_PER_SECOND / 2.0f);
		CreatureEntity creature = CreatureEntity.create(-1, EntityType.ORC, startLocation, health);
		MutableCreature mutable = MutableCreature.existing(creature);
		mutable.newVelocity = startVelocity;
		creature = mutable.freeze();
		
		List<IMutationEntity<IMutableCreatureEntity>> outChanges = new ArrayList<>();
		ProcessorElement thread = new ProcessorElement(0, new SyncPoint(1), new AtomicInteger(0));
		_Events events = new _Events();
		TickProcessingContext context = ContextBuilder.build()
				.tick(1L)
				.lookups((AbsoluteLocation location) -> {
						return (cuboid.getCuboidAddress().equals(location.getCuboidAddress()))
							? new BlockProxy(location.getBlockAddress(), cuboid)
							: null
						;
					}, null)
				.sinks(null, new TickProcessingContext.IChangeSink() {
					@Override
					public void next(int targetEntityId, IMutationEntity<IMutablePlayerEntity> change)
					{
						Assert.fail("Not in test");
					}
					@Override
					public void future(int targetEntityId, IMutationEntity<IMutablePlayerEntity> change, long millisToDelay)
					{
						Assert.fail("Not in test");
					}
					@Override
					public void creature(int targetCreatureId, IMutationEntity<IMutableCreatureEntity> change)
					{
						Assert.assertEquals(-1, targetCreatureId);
						outChanges.add(change);
					}
				})
				.eventSink(events)
				.finish()
		;
		CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
				, Map.of(creature.id(), creature)
				, context
				, new EntityCollection(Set.of(), List.of(creature))
				, Map.of()
		);
		creature = group.updatedCreatures().get(creature.id());
		
		Assert.assertEquals(new EntityLocation(16.8f, 16.8f, 16.0f), creature.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), creature.velocity());
		Assert.assertEquals(health, creature.health());
		Assert.assertEquals(1, outChanges.size());
		
		events.expected(new EventRecord(EventRecord.Type.ENTITY_HURT, EventRecord.Cause.FALL, creature.location().getBlockLocation(), creature.id(), 0));
		group = CreatureProcessor.processCreatureGroupParallel(thread
				, group.updatedCreatures()
				, context
				, new EntityCollection(Set.of(), List.of(creature))
				, Map.of(creature.id(), new ArrayList<>(outChanges))
		);
		creature = group.updatedCreatures().get(creature.id());
		
		Assert.assertEquals(new EntityLocation(16.8f, 16.8f, 16.0f), creature.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), creature.velocity());
		Assert.assertEquals((byte)58, creature.health());
		Assert.assertEquals(1, outChanges.size());
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
				.tick(OrcStateMachine.MINIMUM_MILLIS_TO_IDLE_ACTION / millisPerTick)
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
				.tick(OrcStateMachine.MINIMUM_MILLIS_TO_IDLE_ACTION / millisPerTick)
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

	private static TickProcessingContext _updateContextWithCreatures(TickProcessingContext existing, Collection<CreatureEntity> creatures, IChangeSink newChangeSink, CreatureIdAssigner idAssigner)
	{
		Map<Integer, MinimalEntity> minimal = creatures.stream().collect(Collectors.toMap((CreatureEntity creature) -> creature.id(), (CreatureEntity creature) -> MinimalEntity.fromCreature(creature)));
		TickProcessingContext context = ContextBuilder.nextTick(existing, 1L)
				.lookups(existing.previousBlockLookUp, (Integer id) -> minimal.get(id))
				.sinks(null, newChangeSink)
				.assigner(idAssigner)
				.finish()
		;
		return context;
	}

	private static TickProcessingContext _updateContextWithPlayer(TickProcessingContext existing, Entity player)
	{
		TickProcessingContext context = ContextBuilder.nextTick(existing, (OrcStateMachine.MINIMUM_MILLIS_TO_DELIBERATE_ACTION / existing.millisPerTick))
				.lookups(existing.previousBlockLookUp, (Integer id) -> (id == player.id()) ? MinimalEntity.fromEntity(player) : null)
				.sinks(null, null)
				.assigner(null)
				.finish()
		;
		return context;
	}

	private static TickProcessingContext _updateContextForTick(TickProcessingContext existing)
	{
		TickProcessingContext context = ContextBuilder.nextTick(existing, 1L)
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
				, EntityConstants.MAX_BREATH
				, 0
				, MutableEntity.TESTING_LOCATION
				, 0L
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
