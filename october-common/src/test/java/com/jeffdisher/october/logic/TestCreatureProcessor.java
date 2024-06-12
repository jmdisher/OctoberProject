package com.jeffdisher.october.logic;

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

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.creatures.CowStateMachine;
import com.jeffdisher.october.creatures.CreatureLogic;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.mutations.EntityChangeImpregnateCreature;
import com.jeffdisher.october.mutations.EntityChangeMove;
import com.jeffdisher.october.mutations.EntityChangeTakeDamage;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.mutations.MutationBlockStoreItems;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Difficulty;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.IMutableCreatureEntity;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MinimalEntity;
import com.jeffdisher.october.types.MutableCreature;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.TickProcessingContext;
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
		TickProcessingContext context = _createContext();
		long millisSinceLastTick = 100L;
		EntityChangeTakeDamage<IMutableCreatureEntity> change = new EntityChangeTakeDamage<>(BodyPart.FEET, (byte)10);
		Map<Integer, List<IMutationEntity<IMutableCreatureEntity>>> changesToRun = Map.of(creature.id(), List.of(change));
		CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Set.of(), Set.of())
				, millisSinceLastTick
				, changesToRun
		);
		
		Assert.assertEquals(1, group.committedMutationCount());
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
		CuboidData fakeCuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), STONE);
		TickProcessingContext context = new TickProcessingContext(1
				, (AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), fakeCuboid)
				, null
				, new IMutationSink() {
					@Override
					public void next(IMutationBlock mutation)
					{
						Assert.assertNull(mutationHolder[0]);
						mutationHolder[0] = mutation;
					}
					@Override
					public void future(IMutationBlock mutation, long millisToDelay)
					{
						Assert.fail();
					}}
				, null
				, null
				, null
				, Difficulty.HOSTILE
		);
		long millisSinceLastTick = 100L;
		EntityChangeTakeDamage<IMutableCreatureEntity> change = new EntityChangeTakeDamage<>(BodyPart.FEET, (byte)120);
		Map<Integer, List<IMutationEntity<IMutableCreatureEntity>>> changesToRun = Map.of(creature.id(), List.of(change));
		CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Set.of(), Set.of())
				, millisSinceLastTick
				, changesToRun
		);
		
		Assert.assertEquals(1, group.committedMutationCount());
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
		long millisSinceLastTick = 100L;
		Map<Integer, List<IMutationEntity<IMutableCreatureEntity>>> changesToRun = Map.of();
		CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Set.of(), Set.of())
				, millisSinceLastTick
				, changesToRun
		);
		
		CreatureEntity updated = group.updatedCreatures().get(creature.id());
		Assert.assertNotEquals(startLocation, updated.location());
		Assert.assertNotNull(updated.stepsToNextMove());
		Assert.assertNotNull(CowStateMachine.decodeExtendedData(updated.extendedData()).movementPlan());
	}

	@Test
	public void despawnOrcOnMovementDecision()
	{
		ProcessorElement thread = new ProcessorElement(0, new SyncPoint(1), new AtomicInteger(0));
		EntityLocation startLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		CreatureEntity creature = CreatureEntity.create(-1, EntityType.ORC, startLocation, (byte)50);
		Map<Integer, CreatureEntity> creaturesById = Map.of(creature.id(), creature);
		TickProcessingContext context = _createContextWithDifficulty(Difficulty.PEACEFUL);
		long millisSinceLastTick = 100L;
		Map<Integer, List<IMutationEntity<IMutableCreatureEntity>>> changesToRun = Map.of();
		CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Set.of(), Set.of())
				, millisSinceLastTick
				, changesToRun
		);
		
		Assert.assertTrue(group.updatedCreatures().isEmpty());
	}

	@Test
	public void takeNextStep()
	{
		ProcessorElement thread = new ProcessorElement(0, new SyncPoint(1), new AtomicInteger(0));
		EntityLocation startLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		List<IMutationEntity<IMutableCreatureEntity>> stepsToNextMove = List.of(new EntityChangeMove<>(startLocation, 0.0f, 0.4f)
			, new EntityChangeMove<>(new EntityLocation(0.0f, 0.4f, 0.0f), 0.0f, 0.4f)
			, new EntityChangeMove<>(new EntityLocation(0.0f, 0.8f, 0.0f), 0.0f, 0.2f)
		);
		List<AbsoluteLocation> movementPlan = List.of(new AbsoluteLocation(0, 1, 0)
			, new AbsoluteLocation(0, 1, 1)
		);
		CreatureEntity creature = new CreatureEntity(-1, EntityType.COW, startLocation, 0.0f, (byte)100, 0L, stepsToNextMove, CowStateMachine.encodeExtendedData(new CowStateMachine.Test_ExtendedData(false, movementPlan, 0, null, null)));
		Map<Integer, CreatureEntity> creaturesById = Map.of(creature.id(), creature);
		TickProcessingContext context = _createContext();
		long millisSinceLastTick = 100L;
		Map<Integer, List<IMutationEntity<IMutableCreatureEntity>>> changesToRun = Map.of();
		CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Set.of(), Set.of())
				, millisSinceLastTick
				, changesToRun
		);
		
		CreatureEntity updated = group.updatedCreatures().get(creature.id());
		Assert.assertNotEquals(startLocation, updated.location());
		Assert.assertEquals(2, updated.stepsToNextMove().size());
		Assert.assertEquals(2, CowStateMachine.decodeExtendedData(updated.extendedData()).movementPlan().size());
	}

	@Test
	public void endStep()
	{
		ProcessorElement thread = new ProcessorElement(0, new SyncPoint(1), new AtomicInteger(0));
		EntityLocation startLocation = new EntityLocation(0.0f, 0.8f, 0.0f);
		List<IMutationEntity<IMutableCreatureEntity>> stepsToNextMove = List.of(new EntityChangeMove<>(startLocation, 0.0f, 0.2f)
		);
		List<AbsoluteLocation> movementPlan = List.of(new AbsoluteLocation(0, 1, 0)
			, new AbsoluteLocation(0, 1, 1)
		);
		CreatureEntity creature = new CreatureEntity(-1, EntityType.COW, startLocation, 0.0f, (byte)100, 0L, stepsToNextMove, CowStateMachine.encodeExtendedData(new CowStateMachine.Test_ExtendedData(false, movementPlan, 0, null, null)));
		Map<Integer, CreatureEntity> creaturesById = Map.of(creature.id(), creature);
		TickProcessingContext context = _createContext();
		long millisSinceLastTick = 100L;
		Map<Integer, List<IMutationEntity<IMutableCreatureEntity>>> changesToRun = Map.of();
		CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Set.of(), Set.of())
				, millisSinceLastTick
				, changesToRun
		);
		
		CreatureEntity updated = group.updatedCreatures().get(creature.id());
		Assert.assertNotEquals(startLocation, updated.location());
		Assert.assertNull(updated.stepsToNextMove());
		Assert.assertEquals(2, CowStateMachine.decodeExtendedData(updated.extendedData()).movementPlan().size());
	}

	@Test
	public void startNextStep()
	{
		ProcessorElement thread = new ProcessorElement(0, new SyncPoint(1), new AtomicInteger(0));
		EntityLocation startLocation = new EntityLocation(0.0f, 1.0f, 0.0f);
		List<AbsoluteLocation> movementPlan = List.of(new AbsoluteLocation(0, 1, 0)
			, new AbsoluteLocation(0, 1, 1)
		);
		CreatureEntity creature = new CreatureEntity(-1, EntityType.COW, startLocation, 0.0f, (byte)100, 0L, null, CowStateMachine.encodeExtendedData(new CowStateMachine.Test_ExtendedData(false, movementPlan, 0, null, null)));
		Map<Integer, CreatureEntity> creaturesById = Map.of(creature.id(), creature);
		TickProcessingContext context = _createContext();
		long millisSinceLastTick = 100L;
		Map<Integer, List<IMutationEntity<IMutableCreatureEntity>>> changesToRun = Map.of();
		CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Set.of(), Set.of())
				, millisSinceLastTick
				, changesToRun
		);
		
		CreatureEntity updated = group.updatedCreatures().get(creature.id());
		Assert.assertNotEquals(startLocation, updated.location());
		Assert.assertEquals(3.92f, updated.zVelocityPerSecond(), 0.001f);
		// We should only jump so there will be no next movement.
		Assert.assertNull(updated.stepsToNextMove());
		Assert.assertEquals(1, CowStateMachine.decodeExtendedData(updated.extendedData()).movementPlan().size());
	}

	@Test
	public void waitForJump()
	{
		// In this test, we should just be waiting for a jump to have an effect.
		ProcessorElement thread = new ProcessorElement(0, new SyncPoint(1), new AtomicInteger(0));
		EntityLocation startLocation = new EntityLocation(0.0f, 1.0f, 0.441f);
		List<AbsoluteLocation> movementPlan = List.of(new AbsoluteLocation(0, 1, 1)
		);
		CreatureEntity creature = new CreatureEntity(-1, EntityType.COW, startLocation, 3.92f, (byte)100, 0L, null, CowStateMachine.encodeExtendedData(new CowStateMachine.Test_ExtendedData(false, movementPlan, 0, null, null)));
		Map<Integer, CreatureEntity> creaturesById = Map.of(creature.id(), creature);
		TickProcessingContext context = _createContext();
		long millisSinceLastTick = 100L;
		Map<Integer, List<IMutationEntity<IMutableCreatureEntity>>> changesToRun = Map.of();
		CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Set.of(), Set.of())
				, millisSinceLastTick
				, changesToRun
		);
		
		CreatureEntity updated = group.updatedCreatures().get(creature.id());
		Assert.assertNotEquals(startLocation, updated.location());
		Assert.assertEquals(2.94f, updated.zVelocityPerSecond(), 0.001f);
		// We should only jump so there will be no next movement.
		Assert.assertNull(updated.stepsToNextMove());
		Assert.assertEquals(1, CowStateMachine.decodeExtendedData(updated.extendedData()).movementPlan().size());
	}

	@Test
	public void moveAfterJump()
	{
		ProcessorElement thread = new ProcessorElement(0, new SyncPoint(1), new AtomicInteger(0));
		EntityLocation startLocation = new EntityLocation(0.0f, 0.0f, 1.2f);
		List<AbsoluteLocation> movementPlan = List.of(new AbsoluteLocation(0, 0, 1)
			, new AbsoluteLocation(0, 1, 1)
		);
		CreatureEntity creature = new CreatureEntity(-1, EntityType.COW, startLocation, 0.0f, (byte)100, 0L, null, CowStateMachine.encodeExtendedData(new CowStateMachine.Test_ExtendedData(false, movementPlan, 0, null, null)));
		Map<Integer, CreatureEntity> creaturesById = Map.of(creature.id(), creature);
		TickProcessingContext context = _createContext();
		long millisSinceLastTick = 100L;
		Map<Integer, List<IMutationEntity<IMutableCreatureEntity>>> changesToRun = Map.of();
		CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Set.of(), Set.of())
				, millisSinceLastTick
				, changesToRun
		);
		
		CreatureEntity updated = group.updatedCreatures().get(creature.id());
		Assert.assertNotEquals(startLocation, updated.location());
		Assert.assertEquals(2, updated.stepsToNextMove().size());
		Assert.assertEquals(1, CowStateMachine.decodeExtendedData(updated.extendedData()).movementPlan().size());
	}

	@Test
	public void resetPlanOnDamage()
	{
		ProcessorElement thread = new ProcessorElement(0, new SyncPoint(1), new AtomicInteger(0));
		EntityLocation startLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		byte startHealth = 100;
		CreatureEntity creature = CreatureEntity.create(-1, EntityType.COW, startLocation, startHealth);
		Map<Integer, CreatureEntity> creaturesById = Map.of(creature.id(), creature);
		TickProcessingContext context = _createContext();
		long millisSinceLastTick = 100L;
		Map<Integer, List<IMutationEntity<IMutableCreatureEntity>>> changesToRun = Map.of();
		CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Set.of(), Set.of())
				, millisSinceLastTick
				, changesToRun
		);
		
		CreatureEntity updated = group.updatedCreatures().get(creature.id());
		Assert.assertEquals(startHealth, updated.health());
		Assert.assertNotEquals(startLocation, updated.location());
		Assert.assertNotNull(updated.stepsToNextMove());
		Assert.assertNotNull(CowStateMachine.decodeExtendedData(updated.extendedData()).movementPlan());
		
		// Now, hit them and see this clears their movement plan.
		creaturesById = group.updatedCreatures();
		byte damage = 10;
		changesToRun = Map.of(creature.id(), List.of(new EntityChangeTakeDamage<>(BodyPart.FEET, damage)));
		group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Set.of(), Set.of())
				, millisSinceLastTick
				, changesToRun
		);
		
		updated = group.updatedCreatures().get(creature.id());
		Assert.assertEquals(startHealth - damage, updated.health());
		Assert.assertNull(updated.stepsToNextMove());
		Assert.assertNull(CowStateMachine.decodeExtendedData(updated.extendedData()));
	}

	@Test
	public void followTheWheat()
	{
		// Create 3 entities, 2 holding wheat and one holding a tool, to show that we always path to the closest with wheat.
		ProcessorElement thread = new ProcessorElement(0, new SyncPoint(1), new AtomicInteger(0));
		EntityLocation startLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		CreatureEntity creature = CreatureEntity.create(-1, EntityType.COW, startLocation, (byte)100);
		Map<Integer, CreatureEntity> creaturesById = Map.of(creature.id(), creature);
		Entity farWheat = _createEntity(1, new EntityLocation(5.0f, 0.0f, 0.0f), new Items(ENV.items.getItemById("op.wheat_item"), 2), null);
		Entity closeWheat = _createEntity(1, new EntityLocation(3.0f, 0.0f, 0.0f), new Items(ENV.items.getItemById("op.wheat_item"), 2), null);
		Entity nonWheat = _createEntity(1, new EntityLocation(2.0f, 0.0f, 0.0f), null, new NonStackableItem(ENV.items.getItemById("op.iron_pickaxe"), 100));
		TickProcessingContext context = _createContext();
		long millisSinceLastTick = 100L;
		Map<Integer, List<IMutationEntity<IMutableCreatureEntity>>> changesToRun = Map.of();
		CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Set.of(farWheat, closeWheat, nonWheat), creaturesById.values())
				, millisSinceLastTick
				, changesToRun
		);
		
		CreatureEntity updated = group.updatedCreatures().get(creature.id());
		Assert.assertNotEquals(startLocation, updated.location());
		Assert.assertNotNull(updated.stepsToNextMove());
		
		// Make sure that the movement plan ends at the close wheat.
		List<AbsoluteLocation> movementPlan = CowStateMachine.decodeExtendedData(updated.extendedData()).movementPlan();
		AbsoluteLocation endPoint = movementPlan.get(movementPlan.size() - 1);
		Assert.assertEquals(closeWheat.location().getBlockLocation(), endPoint);
		
		// Move the target entity and observe that the plan changes.
		closeWheat = _createEntity(1, new EntityLocation(2.0f, 1.0f, 0.0f), new Items(ENV.items.getItemById("op.wheat_item"), 2), null);
		
		// We need to finish the previous step before a new one will be created so loop 3 times (2 to finish the step and one to decide on new).
		for (int i = 0; i < 3; ++i)
		{
			context = _updateContextWithPlayer(context, closeWheat);
			creaturesById = group.updatedCreatures();
			group = CreatureProcessor.processCreatureGroupParallel(thread
					, creaturesById
					, context
					, new EntityCollection(Set.of(farWheat, closeWheat, nonWheat), creaturesById.values())
					, millisSinceLastTick
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
		EntityLocation startLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
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
		long millisSinceLastTick = 100L;
		Map<Integer, List<IMutationEntity<IMutableCreatureEntity>>> changesToRun = Map.of();
		CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
				, Map.of(fedCow.id(), fedCow)
				, context
				, new EntityCollection(Set.of(closeWheat), creaturesById.values())
				, millisSinceLastTick
				, changesToRun
		);
		
		CreatureEntity updated = group.updatedCreatures().get(fedCow.id());
		Assert.assertNotEquals(startLocation, updated.location());
		Assert.assertNotNull(updated.stepsToNextMove());
		
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
		long millisSinceLastTick = 100L;
		CreatureProcessor.CreatureGroup group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Set.of(), creaturesById.values())
				, millisSinceLastTick
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
				, millisSinceLastTick
				, Map.of()
		);
		
		// Verify that cow1 sent a message to cow2.
		Assert.assertEquals(1, changes.size());
		Assert.assertTrue(changes.get(cow2.id()) instanceof EntityChangeImpregnateCreature);
		
		// Run another tick to see the mother receive this request.
		creaturesById.putAll(group.updatedCreatures());
		context = _updateContextWithCreatures(context, creaturesById.values(), null, null);
		group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Set.of(), creaturesById.values())
				, millisSinceLastTick
				, Map.of(cow2.id(), List.of(changes.get(cow2.id())))
		);
		
		// Run a final tick to see the mother spawn the offspring.
		creaturesById.putAll(group.updatedCreatures());
		context = _updateContextWithCreatures(context, creaturesById.values(), null, idAssigner);
		group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Set.of(), creaturesById.values())
				, millisSinceLastTick
				, Map.of()
		);
		Assert.assertEquals(1, group.newlySpawnedCreatures().size());
		CreatureEntity offspring = group.newlySpawnedCreatures().get(0);
		Assert.assertEquals(-3, offspring.id());
		Assert.assertEquals(EntityType.COW, offspring.type());
		
		// Run another tick to observe that nothing special happens.
		creaturesById.putAll(group.updatedCreatures());
		creaturesById.put(offspring.id(), offspring);
		context = _updateContextWithCreatures(context, creaturesById.values(), null, null);
		group = CreatureProcessor.processCreatureGroupParallel(thread
				, creaturesById
				, context
				, new EntityCollection(Set.of(), creaturesById.values())
				, millisSinceLastTick
				, Map.of()
		);
	}


	private static TickProcessingContext _createContext()
	{
		return _createContextWithDifficulty(Difficulty.HOSTILE);
	}

	private static TickProcessingContext _createContextWithDifficulty(Difficulty difficulty)
	{
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), AIR);
		CuboidData stoneCuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)-1), STONE);
		TickProcessingContext context = new TickProcessingContext(CreatureProcessor.MINIMUM_TICKS_TO_NEW_ACTION + 1L
				, (AbsoluteLocation location) -> {
					return ((short)-1 == location.z())
						? new BlockProxy(location.getBlockAddress(), stoneCuboid)
						: new BlockProxy(location.getBlockAddress(), airCuboid)
					;
				}
				, null
				, null
				, null
				, null
				// We return a fixed "1" for the random generator to make sure that we select a reasonable plan for all tests.
				, (int bound) -> 1
				, difficulty
		);
		return context;
	}

	private static TickProcessingContext _updateContextWithCreatures(TickProcessingContext existing, Collection<CreatureEntity> creatures, IChangeSink newChangeSink, CreatureIdAssigner idAssigner)
	{
		Map<Integer, MinimalEntity> minimal = creatures.stream().collect(Collectors.toMap((CreatureEntity creature) -> creature.id(), (CreatureEntity creature) -> MinimalEntity.fromCreature(creature)));
		TickProcessingContext context = new TickProcessingContext(existing.currentTick + 1
				, existing.previousBlockLookUp
				, (Integer id) -> minimal.get(id)
				, null
				, newChangeSink
				, idAssigner
				, existing.randomInt
				, existing.difficulty
		);
		return context;
	}

	private static TickProcessingContext _updateContextWithPlayer(TickProcessingContext existing, Entity player)
	{
		TickProcessingContext context = new TickProcessingContext(existing.currentTick + CreatureProcessor.MINIMUM_TICKS_TO_NEW_ACTION + 1
				, existing.previousBlockLookUp
				, (Integer id) -> (id == player.id()) ? MinimalEntity.fromEntity(player) : null
				, null
				, null
				, null
				, existing.randomInt
				, existing.difficulty
		);
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
				, location
				, 0.0f
				, null
				, 0.0f
				, inventory
				, new int[] { key }
				, 0
				, null
				, null
				, (byte)0
				, (byte)0
				, 0
		);
	}
}
