package com.jeffdisher.october.logic;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.creatures.CreatureLogic;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
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
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.IMutableCreatureEntity;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableCreature;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.TickProcessingContext;
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
		Assert.assertNotNull(updated.movementPlan());
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
		CreatureEntity creature = new CreatureEntity(-1, EntityType.COW, startLocation, 0.0f, (byte)100, 0L, stepsToNextMove, movementPlan, null);
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
		Assert.assertEquals(2, updated.movementPlan().size());
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
		CreatureEntity creature = new CreatureEntity(-1, EntityType.COW, startLocation, 0.0f, (byte)100, 0L, stepsToNextMove, movementPlan, null);
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
		Assert.assertEquals(2, updated.movementPlan().size());
	}

	@Test
	public void startNextStep()
	{
		ProcessorElement thread = new ProcessorElement(0, new SyncPoint(1), new AtomicInteger(0));
		EntityLocation startLocation = new EntityLocation(0.0f, 1.0f, 0.0f);
		List<AbsoluteLocation> movementPlan = List.of(new AbsoluteLocation(0, 1, 0)
			, new AbsoluteLocation(0, 1, 1)
		);
		CreatureEntity creature = new CreatureEntity(-1, EntityType.COW, startLocation, 0.0f, (byte)100, 0L, null, movementPlan, null);
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
		Assert.assertEquals(1, updated.movementPlan().size());
	}

	@Test
	public void waitForJump()
	{
		// In this test, we should just be waiting for a jump to have an effect.
		ProcessorElement thread = new ProcessorElement(0, new SyncPoint(1), new AtomicInteger(0));
		EntityLocation startLocation = new EntityLocation(0.0f, 1.0f, 0.441f);
		List<AbsoluteLocation> movementPlan = List.of(new AbsoluteLocation(0, 1, 1)
		);
		CreatureEntity creature = new CreatureEntity(-1, EntityType.COW, startLocation, 3.92f, (byte)100, 0L, null, movementPlan, null);
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
		Assert.assertEquals(1, updated.movementPlan().size());
	}

	@Test
	public void moveAfterJump()
	{
		ProcessorElement thread = new ProcessorElement(0, new SyncPoint(1), new AtomicInteger(0));
		EntityLocation startLocation = new EntityLocation(0.0f, 0.0f, 1.2f);
		List<AbsoluteLocation> movementPlan = List.of(new AbsoluteLocation(0, 0, 1)
			, new AbsoluteLocation(0, 1, 1)
		);
		CreatureEntity creature = new CreatureEntity(-1, EntityType.COW, startLocation, 0.0f, (byte)100, 0L, null, movementPlan, null);
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
		Assert.assertEquals(1, updated.movementPlan().size());
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
		Assert.assertNotNull(updated.movementPlan());
		
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
		Assert.assertNull(updated.movementPlan());
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
		AbsoluteLocation endPoint = updated.movementPlan().get(updated.movementPlan().size() - 1);
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
		AbsoluteLocation endPoint = updated.movementPlan().get(updated.movementPlan().size() - 1);
		Assert.assertEquals(targetCow.location().getBlockLocation(), endPoint);
	}


	private static TickProcessingContext _createContext()
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
