package com.jeffdisher.october.creatures;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.actions.EntityActionSimpleMove;
import com.jeffdisher.october.actions.EntityActionImpregnateCreature;
import com.jeffdisher.october.actions.EntityActionNudge;
import com.jeffdisher.october.actions.EntityActionTakeDamageFromEntity;
import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.CreatureExtendedData;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.logic.CreatureIdAssigner;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.ContextBuilder;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutableCreatureEntity;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.IPassiveAction;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.MinimalEntity;
import com.jeffdisher.october.types.MutableCreature;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.PassiveEntity;
import com.jeffdisher.october.types.PassiveType;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.types.CreatureEntity.Ephemeral;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestCreatureLogic
{
	private static Environment ENV;
	private static Item WHEAT;
	private static EntityType COW;
	private static EntityType ORC;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		WHEAT = ENV.items.getItemById("op.wheat_item");
		COW = ENV.creatures.getTypeById("op.cow");
		ORC = ENV.creatures.getTypeById("op.orc");
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void idleWithoutJumping()
	{
		// Verify that all possible idle paths end on the ground.
		EntityLocation entityLocation = new EntityLocation(16.0f, 16.0f, 1.0f);
		CreatureEntity entity = CreatureEntity.create(-1, ORC, entityLocation, 0L);
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		_setLayer(input, (byte)0, "op.stone");
		
		// We should see 24 possible locations, all at z-level 1 as their final destination, minus where we started.
		for (int i = 0; i < 24; ++i)
		{
			TickProcessingContext context = _createContext((AbsoluteLocation location) -> {
				return location.getCuboidAddress().equals(cuboidAddress)
						? new BlockProxy(location.getBlockAddress(), input)
						: null
				;
			}, i);
			List<AbsoluteLocation> path = CreatureLogic.test_findPathToRandomSpot(context, entity.location(), entity.type());
			AbsoluteLocation target = path.get(path.size() - 1);
			Assert.assertEquals(1, target.z());
		}
		// Verify that there are only 24.
		try
		{
			TickProcessingContext context = _createContext((AbsoluteLocation location) -> {
				return location.getCuboidAddress().equals(cuboidAddress)
						? new BlockProxy(location.getBlockAddress(), input)
						: null
				;
			}, 24);
			CreatureLogic.test_findPathToRandomSpot(context, entity.location(), entity.type());
		}
		catch (AssertionError e)
		{
			// Expected from test.
		}
	}

	@Test
	public void avoidDrowning()
	{
		// Verify that idle movement will avoid stopping in the water (these should all jump).
		EntityLocation entityLocation = new EntityLocation(16.0f, 16.0f, 1.0f);
		CreatureEntity entity = CreatureEntity.create(-1, ORC, entityLocation, 0L);
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		_setLayer(input, (byte)0, "op.stone");
		_setLayer(input, (byte)1, "op.water_source");
		
		// We should see 13 possible locations, all at z-level 2 as their final destination.
		for (int i = 0; i < 13; ++i)
		{
			TickProcessingContext context = _createContext((AbsoluteLocation location) -> {
				return location.getCuboidAddress().equals(cuboidAddress)
						? new BlockProxy(location.getBlockAddress(), input)
						: null
				;
			}, i);
			List<AbsoluteLocation> path = CreatureLogic.test_findPathToRandomSpot(context, entity.location(), entity.type());
			AbsoluteLocation target = path.get(path.size() - 1);
			Assert.assertEquals(2, target.z());
		}
		// Verify that there are only 13.
		try
		{
			TickProcessingContext context = _createContext((AbsoluteLocation location) -> {
				return location.getCuboidAddress().equals(cuboidAddress)
						? new BlockProxy(location.getBlockAddress(), input)
						: null
				;
			}, 13);
			CreatureLogic.test_findPathToRandomSpot(context, entity.location(), entity.type());
		}
		catch (AssertionError e)
		{
			// Expected from test.
		}
	}

	@Test
	public void leaveWaterWhenDrowning()
	{
		// Verify that idle movement will avoid stopping in the water (these should all jump).
		EntityLocation entityLocation = new EntityLocation(16.0f, 16.0f, 1.0f);
		CreatureEntity entity = CreatureEntity.create(-1, ORC, entityLocation, 0L);
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		_setLayer(input, (byte)0, "op.stone");
		_setLayer(input, (byte)1, "op.water_source");
		
		// Even though this is only the idle timeout, we will see a plan made and that it ends above water.
		TickProcessingContext context = ContextBuilder.build()
				.tick((CreatureLogic.MINIMUM_MILLIS_TO_ACTION / 100L) + 1L)
				.lookups((AbsoluteLocation location) -> {
					return location.getCuboidAddress().equals(cuboidAddress)
							? new BlockProxy(location.getBlockAddress(), input)
							: null
					;
				}, null, null)
				.fixedRandom(0)
				.finish()
		;
		MutableCreature mutable = MutableCreature.existing(entity);
		mutable.newBreath -= 1;
		boolean didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, EntityCollection.fromMaps(Map.of(), Map.of(entity.id(), entity))
				, mutable
		);
		Assert.assertFalse(didTakeAction);
		IEntityAction<IMutableCreatureEntity> action = CreatureLogic.planNextAction(context
				, mutable
				, 100L
		);
		Assert.assertNotNull(action);
		List<AbsoluteLocation> plan = mutable.newMovementPlan;
		Assert.assertEquals(2, plan.get(plan.size() - 1).z());
	}

	@Test
	public void idleNoPath()
	{
		// Verify that we will choose to do nothing if there are no idle movement targets.
		EntityLocation entityLocation = new EntityLocation(16.0f, 16.0f, 1.0f);
		CreatureEntity entity = CreatureEntity.create(-1, ORC, entityLocation, 0L);
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		short stoneNumber = ENV.items.getItemById("op.stone").number();
		_setLayer(input, (byte)0, "op.stone");
		_setLayer(input, (byte)1, "op.stone");
		input.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(16, 16, 1), stoneNumber);
		_setLayer(input, (byte)2, "op.stone");
		input.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(16, 16, 2), stoneNumber);
		
		// We should see 0 possible locations.
		TickProcessingContext context = _createContext((AbsoluteLocation location) -> {
			return location.getCuboidAddress().equals(cuboidAddress)
					? new BlockProxy(location.getBlockAddress(), input)
					: null
			;
		}, 0);
		List<AbsoluteLocation> path = CreatureLogic.test_findPathToRandomSpot(context, entity.location(), entity.type());
		Assert.assertNull(path);
	}

	@Test
	public void orcObserveTargetMove()
	{
		// Show an orc acquiring a target and then updating its path when the target moves.
		EntityLocation orcLocation = new EntityLocation(2.0f, 2.0f, 1.0f);
		CreatureEntity orc = CreatureEntity.create(-1, ORC, orcLocation, 0L);
		EntityLocation playerLocation = new EntityLocation(5.0f, 1.0f, 1.0f);
		Entity[] player = new Entity[] { new Entity(1
				, false
				, playerLocation
				, new EntityLocation(0.0f, 0.0f, 0.0f)
				, (byte)0
				, (byte)0
				, Inventory.start(10).finish()
				, new int[1]
				, 0
				, null
				, (byte)0
				, (byte)0
				, MiscConstants.MAX_BREATH
				, MutableEntity.TESTING_LOCATION
				, Entity.EMPTY_SHARED
				, Entity.EMPTY_LOCAL
		) };
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		_setLayer(input, (byte)0, "op.stone");
		
		// We should see them acquire this target.
		Function<AbsoluteLocation, BlockProxy> previousBlockLookUp = (AbsoluteLocation location) -> {
			return location.getCuboidAddress().equals(cuboidAddress)
					? new BlockProxy(location.getBlockAddress(), input)
					: null
			;
		};
		Function<Integer, MinimalEntity> previousEntityLookUp = (Integer id) -> {
			MinimalEntity min;
			switch (id)
			{
			case -1:
				min = MinimalEntity.fromCreature(orc);
				break;
			case 1:
				min = MinimalEntity.fromEntity(player[0]);
				break;
			default:
				throw new AssertionError();
			}
			return min;
		};
		TickProcessingContext context = ContextBuilder.build()
				.tick((CreatureLogic.MINIMUM_MILLIS_TO_ACTION / 100L) + 1L)
				.lookups(previousBlockLookUp, previousEntityLookUp, null)
				.finish()
		;
		MutableCreature mutableOrc = MutableCreature.existing(orc);
		
		// First, choose the target.
		boolean didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, EntityCollection.fromMaps(Map.of(player[0].id(), player[0]), Map.of(orc.id(), orc))
				, mutableOrc
		);
		Assert.assertFalse(didTakeAction);
		IEntityAction<IMutableCreatureEntity> action = CreatureLogic.planNextAction(context
				, mutableOrc
				, 100L
		);
		Assert.assertNotNull(action);
		Assert.assertEquals(playerLocation.getBlockLocation(), mutableOrc.newMovementPlan.get(mutableOrc.newMovementPlan.size() - 1));
		
		// Now, move the entity and see that the special action updates it.
		EntityLocation newPlayerLocation = new EntityLocation(2.0f, 5.0f, 1.0f);
		player[0] = new Entity(1
				, false
				, newPlayerLocation
				, new EntityLocation(0.0f, 0.0f, 0.0f)
				, (byte)0
				, (byte)0
				, Inventory.start(10).finish()
				, new int[1]
				, 0
				, null
				, (byte)0
				, (byte)0
				, MiscConstants.MAX_BREATH
				, MutableEntity.TESTING_LOCATION
				, Entity.EMPTY_SHARED
				, Entity.EMPTY_LOCAL
		);
		// Special action is where we account for this targeting update but it doesn't count as a special action.
		didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, EntityCollection.fromMaps(Map.of(player[0].id(), player[0]), Map.of(orc.id(), orc))
				, mutableOrc
		);
		Assert.assertFalse(didTakeAction);
		// We should now see the updated movement plan.
		Assert.assertEquals(newPlayerLocation.getBlockLocation(), mutableOrc.newMovementPlan.get(mutableOrc.newMovementPlan.size() - 1));
	}

	@Test
	public void despawnAfterIdleTimeout()
	{
		// We will show that orcs despawn when idle while cows don't.
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		_setLayer(input, (byte)0, "op.stone");
		CreatureIdAssigner assigner = new CreatureIdAssigner();
		long startTick = 1000L;
		long startMillis = startTick * ContextBuilder.DEFAULT_MILLIS_PER_TICK;
		CreatureEntity orc = _updateKeepAlive(CreatureEntity.create(assigner.next(), ORC, new EntityLocation(0.0f, 0.0f, 0.0f), 0L), startMillis);
		CreatureEntity cow = _updateKeepAlive(CreatureEntity.create(assigner.next(), COW, new EntityLocation(0.0f, 0.0f, 0.0f), 0L), startMillis);
		
		// We will take a special action where nothing should happen.
		Function<AbsoluteLocation, BlockProxy> previousBlockLookUp = (AbsoluteLocation location) -> {
			return location.getCuboidAddress().equals(cuboidAddress)
					? new BlockProxy(location.getBlockAddress(), input)
					: null
			;
		};
		// (we won't expose any of the entities since we don't expect targeting).
		Function<Integer, MinimalEntity> previousEntityLookUp = (Integer id) -> null;
		TickProcessingContext context = ContextBuilder.build()
				.tick(startTick)
				.lookups(previousBlockLookUp, previousEntityLookUp, null)
				.finish()
		;
		MutableCreature mutableCow = MutableCreature.existing(cow);
		MutableCreature mutableOrc = MutableCreature.existing(orc);
		boolean didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, EntityCollection.fromMaps(Map.of(), Map.of(cow.id(), cow, orc.id(), orc))
				, mutableCow
		);
		Assert.assertFalse(didTakeAction);
		Assert.assertEquals((byte)40, mutableCow.newHealth);
		didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, EntityCollection.fromMaps(Map.of(), Map.of(cow.id(), cow, orc.id(), orc))
				, mutableOrc
		);
		Assert.assertFalse(didTakeAction);
		Assert.assertEquals((byte)20, mutableOrc.newHealth);
		
		// Now, advance time and do the same, seeing the despawn of the orc but not the cow.
		context = ContextBuilder.build()
				.tick(startTick + (CreatureLogic.MILLIS_UNTIL_NO_ACTION_DESPAWN / context.millisPerTick))
				.lookups(previousBlockLookUp, previousEntityLookUp, null)
				.finish()
		;
		didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, EntityCollection.fromMaps(Map.of(), Map.of(cow.id(), cow, orc.id(), orc))
				, mutableCow
		);
		Assert.assertFalse(didTakeAction);
		Assert.assertEquals((byte)40, mutableCow.newHealth);
		didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, EntityCollection.fromMaps(Map.of(), Map.of(cow.id(), cow, orc.id(), orc))
				, mutableOrc
		);
		Assert.assertTrue(didTakeAction);
		Assert.assertEquals((byte)0,mutableOrc.newHealth);
	}

	@Test
	public void enterLoveMode()
	{
		CreatureIdAssigner assigner = new CreatureIdAssigner();
		CreatureEntity cow = CreatureEntity.create(assigner.next(), COW, new EntityLocation(0.0f, 0.0f, 0.0f), 0L);
		MutableCreature mutable = MutableCreature.existing(cow);
		CreatureLogic.applyItemToCreature(WHEAT, mutable, 1000L);
		Assert.assertTrue(((CreatureExtendedData.LivestockData)mutable.getExtendedData()).inLoveMode());
	}

	@Test
	public void sendImpregnate()
	{
		CreatureIdAssigner assigner = new CreatureIdAssigner();
		EntityLocation fatherLocation = new EntityLocation(0.8f, 0.0f, 0.0f);
		CreatureEntity father = CreatureEntity.create(assigner.next(), COW, fatherLocation, 0L);
		EntityLocation motherLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		CreatureEntity mother = CreatureEntity.create(assigner.next(), COW, motherLocation, 0L);
		// Start with them both in a love mode.
		MutableCreature mutable = MutableCreature.existing(father);
		mutable.newTargetEntityId = mother.id();
		mutable.setExtendedData(new CreatureExtendedData.LivestockData(true, null, 0L));
		father = mutable.freeze();
		mutable = MutableCreature.existing(mother);
		mutable.newTargetEntityId = father.id();
		mutable.setExtendedData(new CreatureExtendedData.LivestockData(true, null, 0L));
		mother = mutable.freeze();
		Map<Integer, CreatureEntity> creatures = new HashMap<>();
		creatures.put(father.id(), father);
		creatures.put(mother.id(), mother);
		
		int[] targetId = new int[1];
		IEntityAction<?>[] message = new IEntityAction<?>[1];
		TickProcessingContext context = ContextBuilder.build()
				.sinks(null, new TickProcessingContext.IChangeSink() {
					@Override
					public boolean next(int targetEntityId, IEntityAction<IMutablePlayerEntity> change)
					{
						throw new AssertionError();
					}
					@Override
					public boolean future(int targetEntityId, IEntityAction<IMutablePlayerEntity> change, long millisToDelay)
					{
						throw new AssertionError();
					}
					@Override
					public boolean creature(int targetCreatureId, IEntityAction<IMutableCreatureEntity> change)
					{
						Assert.assertEquals(0, targetId[0]);
						Assert.assertNull(message[0]);
						targetId[0] = targetCreatureId;
						message[0] = change;
						return true;
					}
					@Override
					public boolean passive(int targetPassiveId, IPassiveAction action)
					{
						throw new AssertionError();
					}
				})
				.lookups(null, (Integer entityId) -> {
					return creatures.containsKey(entityId)
							? MinimalEntity.fromCreature(creatures.get(entityId))
							: null
					;
				}, null)
				.finish()
		;
		
		// We should see the father sending a message
		mutable = MutableCreature.existing(father);
		boolean didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, EntityCollection.fromMaps(Map.of(), creatures)
				, mutable
		);
		Assert.assertTrue(didTakeAction);
		Assert.assertEquals(mother.id(), targetId[0]);
		targetId[0] = 0;
		Assert.assertTrue(message[0] instanceof EntityActionImpregnateCreature);
		message[0] = null;
		father = mutable.freeze();
		creatures.put(father.id(), father);
		
		// The mother should not take any action since they are waiting for the father.
		mutable = MutableCreature.existing(mother);
		didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, EntityCollection.fromMaps(Map.of(), creatures)
				, mutable
		);
		Assert.assertFalse(didTakeAction);
		Assert.assertNull(message[0]);
		mother = mutable.freeze();
		creatures.put(mother.id(), mother);
		
		// The father should no longer be in love mode but the mother should be.
		Assert.assertFalse(((CreatureExtendedData.LivestockData)father.extendedData()).inLoveMode());
		Assert.assertTrue(((CreatureExtendedData.LivestockData)mother.extendedData()).inLoveMode());
	}

	@Test
	public void becomePregnant()
	{
		CreatureIdAssigner assigner = new CreatureIdAssigner();
		EntityLocation fatherLocation = new EntityLocation(0.8f, 0.0f, 0.0f);
		EntityLocation motherLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		CreatureEntity mother = CreatureEntity.create(assigner.next(), COW, motherLocation, 0L);
		MutableCreature mutable = MutableCreature.existing(mother);
		mutable.setExtendedData(new CreatureExtendedData.LivestockData(true, null, 0L));
		
		long gameTimeMillis = 1000L;
		boolean didBecomePregnant = CreatureLogic.setCreaturePregnant(mutable, fatherLocation, gameTimeMillis);
		Assert.assertTrue(didBecomePregnant);
		Assert.assertFalse(((CreatureExtendedData.LivestockData)mutable.getExtendedData()).inLoveMode());
		Assert.assertEquals(new EntityLocation(0.4f, 0.0f, 0.0f), ((CreatureExtendedData.LivestockData)mutable.getExtendedData()).offspringLocation());
		Assert.assertEquals(gameTimeMillis + CreatureLogic.MILLIS_BREEDING_COOLDOWN, ((CreatureExtendedData.LivestockData)mutable.getExtendedData()).breedingReadyMillis());
	}

	@Test
	public void spawnOffspring()
	{
		CreatureIdAssigner assigner = new CreatureIdAssigner();
		EntityLocation offspringLocation = new EntityLocation(0.4f, 0.0f, 0.0f);
		EntityLocation motherLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		CreatureEntity mother = CreatureEntity.create(assigner.next(), COW, motherLocation, 0L);
		MutableCreature mutable = MutableCreature.existing(mother);
		mutable.setExtendedData(new CreatureExtendedData.LivestockData(false, offspringLocation, 0L));
		
		CreatureEntity[] offspring = new CreatureEntity[1];
		TickProcessingContext.ICreatureSpawner creatureSpawner = (EntityType type, EntityLocation location) -> {
			Assert.assertNull(offspring[0]);
			offspring[0] = CreatureEntity.create(assigner.next(), type, location, 0L);
		};
		TickProcessingContext context = ContextBuilder.build()
				.spawner(creatureSpawner)
				.finish()
		;
		boolean didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, EntityCollection.fromMaps(Map.of(), Map.of(mother.id(), mother))
				, mutable
		);
		Assert.assertTrue(didTakeAction);
		Assert.assertNull(((CreatureExtendedData.LivestockData)mutable.getExtendedData()).offspringLocation());
		Assert.assertEquals(offspringLocation, offspring[0].location());
	}

	@Test
	public void startTarget()
	{
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		_setLayer(input, (byte)0, "op.stone");
		CreatureIdAssigner assigner = new CreatureIdAssigner();
		EntityLocation location = new EntityLocation(4.0f, 0.0f, 1.0f);
		MutableEntity mutable = MutableEntity.createForTest(1);
		mutable.newLocation = location;
		Entity player = mutable.freeze();
		EntityLocation orcLocation = new EntityLocation(0.0f, 0.0f, 1.0f);
		CreatureEntity orc = CreatureEntity.create(assigner.next(), ORC, orcLocation, 0L);
		
		Function<AbsoluteLocation, BlockProxy> previousBlockLookUp = (AbsoluteLocation blockLocation) -> {
			return blockLocation.getCuboidAddress().equals(cuboidAddress)
					? new BlockProxy(blockLocation.getBlockAddress(), input)
					: null
			;
		};
		Function<Integer, MinimalEntity> previousEntityLookUp = (Integer id) -> {
			MinimalEntity min;
			switch (id)
			{
			case -1:
				min = MinimalEntity.fromCreature(orc);
				break;
			case 1:
				min = MinimalEntity.fromEntity(player);
				break;
			default:
				throw new AssertionError();
			}
			return min;
		};
		long millisPerTick = 100L;
		TickProcessingContext context = ContextBuilder.build()
				.millisPerTick(millisPerTick)
				.tick(CreatureLogic.MINIMUM_MILLIS_TO_ACTION / millisPerTick)
				.lookups(previousBlockLookUp, previousEntityLookUp, null)
				.finish()
		;
		MutableCreature mutableOrc = MutableCreature.existing(orc);
		boolean didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, EntityCollection.fromMaps(Map.of(player.id(), player), Map.of(orc.id(), orc))
				, mutableOrc
		);
		Assert.assertFalse(didTakeAction);
		EntityActionSimpleMove<IMutableCreatureEntity> action = CreatureLogic.planNextAction(context
				, mutableOrc
				, 100L
		);
		Assert.assertNotNull(action);
		Assert.assertEquals(player.id(), mutableOrc.newTargetEntityId);
		Assert.assertNotNull(mutableOrc.newMovementPlan);
	}

	@Test
	public void sendAttack()
	{
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		_setLayer(input, (byte)0, "op.stone");
		CreatureIdAssigner assigner = new CreatureIdAssigner();
		EntityLocation location = new EntityLocation(1.3f, 0.0f, 1.0f);
		MutableEntity mutable = MutableEntity.createForTest(1);
		mutable.newLocation = location;
		Entity player = mutable.freeze();
		EntityLocation orcLocation = new EntityLocation(0.0f, 0.0f, 1.0f);
		CreatureEntity orc = CreatureEntity.create(assigner.next(), ORC, orcLocation, 0L);
		
		Function<AbsoluteLocation, BlockProxy> previousBlockLookUp = (AbsoluteLocation blockLocation) -> {
			return blockLocation.getCuboidAddress().equals(cuboidAddress)
					? new BlockProxy(blockLocation.getBlockAddress(), input)
					: null
			;
		};
		Function<Integer, MinimalEntity> previousEntityLookUp = (Integer id) -> {
			MinimalEntity min;
			switch (id)
			{
			case -1:
				min = MinimalEntity.fromCreature(orc);
				break;
			case 1:
				min = MinimalEntity.fromEntity(player);
				break;
			default:
				throw new AssertionError();
			}
			return min;
		};
		List<IEntityAction<IMutablePlayerEntity>> outChanges = new ArrayList<>();
		TickProcessingContext.IChangeSink changeSink = new TickProcessingContext.IChangeSink() {
			@Override
			public boolean next(int targetEntityId, IEntityAction<IMutablePlayerEntity> change)
			{
				Assert.assertEquals(player.id(), targetEntityId);
				outChanges.add(change);
				return true;
			}
			@Override
			public boolean future(int targetEntityId, IEntityAction<IMutablePlayerEntity> change, long millisToDelay)
			{
				throw new AssertionError();
			}
			@Override
			public boolean creature(int targetCreatureId, IEntityAction<IMutableCreatureEntity> change)
			{
				throw new AssertionError();
			}
			@Override
			public boolean passive(int targetPassiveId, IPassiveAction action)
			{
				throw new AssertionError();
			}
		};
		long millisPerTick = 100L;
		TickProcessingContext context = ContextBuilder.build()
				.millisPerTick(millisPerTick)
				.tick(CreatureLogic.MINIMUM_MILLIS_TO_ACTION / millisPerTick)
				.sinks(null, changeSink)
				.lookups(previousBlockLookUp, previousEntityLookUp, null)
				.finish()
		;
		
		// Start with the orc targeting the player.
		MutableCreature mutableOrc = MutableCreature.existing(orc);
		mutableOrc.newTargetEntityId = player.id();
		boolean didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, EntityCollection.fromMaps(Map.of(player.id(), player), Map.of(orc.id(), orc))
				, mutableOrc
		);
		Assert.assertFalse(didTakeAction);
		EntityActionSimpleMove<IMutableCreatureEntity> action = CreatureLogic.planNextAction(context
				, mutableOrc
				, 100L
		);
		// We will try to walk toward them still.
		Assert.assertNotNull(action);
		Assert.assertEquals(player.id(), mutableOrc.newTargetEntityId);
		Assert.assertEquals(player.location().getBlockLocation(), mutableOrc.newTargetPreviousLocation);
		
		// Update the player to be close enough.
		Assert.assertTrue(action.applyChange(context, mutableOrc));
		
		// Now, allow it to perform the attack.
		didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, EntityCollection.fromMaps(Map.of(player.id(), player), Map.of(orc.id(), orc))
				, mutableOrc
		);
		Assert.assertTrue(didTakeAction);
		Assert.assertEquals(player.id(), mutableOrc.newTargetEntityId);
		Assert.assertEquals(2, outChanges.size());
		Assert.assertTrue(outChanges.get(0) instanceof EntityActionTakeDamageFromEntity);
		Assert.assertTrue(outChanges.get(1) instanceof EntityActionNudge);
		outChanges.clear();
		
		// A second attack on the following tick should fail since we are on cooldown.
		didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, EntityCollection.fromMaps(Map.of(player.id(), player), Map.of(orc.id(), orc))
				, mutableOrc
		);
		Assert.assertFalse(didTakeAction);
		Assert.assertEquals(player.id(), mutableOrc.newTargetEntityId);
		Assert.assertEquals(0, outChanges.size());
		
		// But will work if we advance tick number further.
		context = ContextBuilder.build()
				.tick(context.currentTick + CreatureLogic.MILLIS_ATTACK_COOLDOWN / context.millisPerTick)
				.sinks(null, changeSink)
				.lookups(previousBlockLookUp, previousEntityLookUp, null)
				.finish()
		;
		didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, EntityCollection.fromMaps(Map.of(player.id(), player), Map.of(orc.id(), orc))
				, mutableOrc
		);
		Assert.assertTrue(didTakeAction);
		Assert.assertEquals(player.id(), mutableOrc.newTargetEntityId);
		Assert.assertEquals(2, outChanges.size());
		Assert.assertTrue(outChanges.get(0) instanceof EntityActionTakeDamageFromEntity);
		Assert.assertTrue(outChanges.get(1) instanceof EntityActionNudge);
	}

	@Test
	public void targetChangeWithDistance()
	{
		// Show how a cow responds to targeting a player as they move around.
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		_setLayer(input, (byte)0, "op.stone");
		CreatureIdAssigner assigner = new CreatureIdAssigner();
		EntityLocation location = new EntityLocation(4.0f, 0.0f, 1.0f);
		MutableEntity mutablePlayer = MutableEntity.createForTest(1);
		mutablePlayer.newLocation = location;
		mutablePlayer.newInventory.addAllItems(WHEAT, 1);
		mutablePlayer.setSelectedKey(1);
		Entity player = mutablePlayer.freeze();
		EntityLocation cowLocation = new EntityLocation(0.0f, 0.0f, 1.0f);
		CreatureEntity cow = CreatureEntity.create(assigner.next(), COW, cowLocation, 0L);
		MutableCreature mutableCow = MutableCreature.existing(cow);
		
		Function<AbsoluteLocation, BlockProxy> previousBlockLookUp = (AbsoluteLocation blockLocation) -> {
			return blockLocation.getCuboidAddress().equals(cuboidAddress)
					? new BlockProxy(blockLocation.getBlockAddress(), input)
					: null
			;
		};
		Map<Integer, MinimalEntity> entities = new HashMap<>();
		Function<Integer, MinimalEntity> previousEntityLookUp = (Integer id) -> entities.get(id);
		TickProcessingContext context = ContextBuilder.build()
				.tick(1000L)
				.lookups(previousBlockLookUp, previousEntityLookUp, null)
				.finish()
		;
		
		// Acquire target.
		entities.put(player.id(), MinimalEntity.fromEntity(player));
		entities.put(cow.id(), MinimalEntity.fromCreature(cow));
		boolean didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, EntityCollection.fromMaps(Map.of(player.id(), player), Map.of(cow.id(), cow))
				, mutableCow
		);
		Assert.assertFalse(didTakeAction);
		Assert.assertEquals(player.id(), mutableCow.newTargetEntityId);
		Assert.assertEquals(player.location().getBlockLocation(), mutableCow.newTargetPreviousLocation);
		Assert.assertNotNull(mutableCow.newMovementPlan);
		
		// Move close.
		mutablePlayer.newLocation = new EntityLocation(0.8f, 0.0f, 1.0f);
		player = mutablePlayer.freeze();
		entities.put(player.id(), MinimalEntity.fromEntity(player));
		cow = mutableCow.freeze();
		entities.put(cow.id(), MinimalEntity.fromCreature(cow));
		didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, EntityCollection.fromMaps(Map.of(player.id(), player), Map.of(cow.id(), cow))
				, mutableCow
		);
		Assert.assertFalse(didTakeAction);
		Assert.assertEquals(player.id(), mutableCow.newTargetEntityId);
		Assert.assertNull(mutableCow.newTargetPreviousLocation);
		Assert.assertNull(mutableCow.newMovementPlan);
		
		// Move far.
		mutablePlayer.newLocation = new EntityLocation(5.0f, 0.0f, 1.0f);
		player = mutablePlayer.freeze();
		entities.put(player.id(), MinimalEntity.fromEntity(player));
		cow = mutableCow.freeze();
		entities.put(cow.id(), MinimalEntity.fromCreature(cow));
		didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, EntityCollection.fromMaps(Map.of(player.id(), player), Map.of(cow.id(), cow))
				, mutableCow
		);
		Assert.assertFalse(didTakeAction);
		Assert.assertEquals(player.id(), mutableCow.newTargetEntityId);
		Assert.assertEquals(player.location().getBlockLocation(), mutableCow.newTargetPreviousLocation);
		Assert.assertNotNull(mutableCow.newMovementPlan);
		
		// Switch out wheat.
		mutablePlayer.setSelectedKey(Entity.NO_SELECTION);
		player = mutablePlayer.freeze();
		entities.put(player.id(), MinimalEntity.fromEntity(player));
		cow = mutableCow.freeze();
		entities.put(cow.id(), MinimalEntity.fromCreature(cow));
		didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, EntityCollection.fromMaps(Map.of(player.id(), player), Map.of(cow.id(), cow))
				, mutableCow
		);
		Assert.assertFalse(didTakeAction);
		Assert.assertEquals(CreatureEntity.NO_TARGET_ENTITY_ID, mutableCow.newTargetEntityId);
		Assert.assertNull(mutableCow.newTargetPreviousLocation);
		Assert.assertNull(mutableCow.newMovementPlan);
	}

	@Test
	public void closeBug()
	{
		// A test related to a bug where a hostile mob would get "stuck" just out of range of the player.
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(-1, 0, 0);
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		_setLayer(input, (byte)0, "op.stone");
		CreatureIdAssigner assigner = new CreatureIdAssigner();
		EntityLocation location = new EntityLocation(-16.2f, 7.63f, 0.0f);
		int entityId = 1;
		MutableEntity mutablePlayer = MutableEntity.createForTest(entityId);
		mutablePlayer.newLocation = location;
		Entity player = mutablePlayer.freeze();
		EntityLocation orcLocation = new EntityLocation(-16.99f, 7.93f, 0.0f);
		CreatureEntity orc = CreatureEntity.create(assigner.next(), ORC, orcLocation, 0L);
		MutableCreature mutableOrc = MutableCreature.existing(orc);
		
		Function<AbsoluteLocation, BlockProxy> previousBlockLookUp = (AbsoluteLocation blockLocation) -> {
			return blockLocation.getCuboidAddress().equals(cuboidAddress)
					? new BlockProxy(blockLocation.getBlockAddress(), input)
					: null
			;
		};
		Map<Integer, MinimalEntity> entities = new HashMap<>();
		Function<Integer, MinimalEntity> previousEntityLookUp = (Integer id) -> entities.get(id);
		boolean[] out = new boolean[1];
		TickProcessingContext context = ContextBuilder.build()
				.tick(1000L)
				.lookups(previousBlockLookUp, previousEntityLookUp, null)
				.sinks(null, new TickProcessingContext.IChangeSink() {
					@Override
					public boolean next(int targetEntityId, IEntityAction<IMutablePlayerEntity> change)
					{
						Assert.assertEquals(entityId, targetEntityId);
						out[0] = true;
						return true;
					}
					@Override
					public boolean future(int targetEntityId, IEntityAction<IMutablePlayerEntity> change, long millisToDelay)
					{
						throw new AssertionError();
					}
					@Override
					public boolean creature(int targetCreatureId, IEntityAction<IMutableCreatureEntity> change)
					{
						throw new AssertionError();
					}
					@Override
					public boolean passive(int targetPassiveId, IPassiveAction action)
					{
						throw new AssertionError();
					}
				})
				.finish()
		;
		
		// In the bug, the orc had the entity as a target but no movement plan.
		mutableOrc.newTargetEntityId = entityId;
		mutableOrc.newTargetPreviousLocation = location.getBlockLocation();
		entities.put(player.id(), MinimalEntity.fromEntity(player));
		entities.put(orc.id(), MinimalEntity.fromCreature(orc));
		boolean didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, EntityCollection.fromMaps(Map.of(player.id(), player), Map.of(orc.id(), orc))
				, mutableOrc
		);
		// We should have taken no action but created a plan.
		Assert.assertTrue(didTakeAction);
		Assert.assertNull(mutableOrc.newMovementPlan);
		Assert.assertTrue(out[0]);
	}

	@Test
	public void swimUpWhenSinking()
	{
		// Show that we swim correctly when sinking.
		CreatureIdAssigner assigner = new CreatureIdAssigner();
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		_setLayer(input, (byte)0, "op.stone");
		_setLayer(input, (byte)1, "op.stone");
		AbsoluteLocation waterLocation = new AbsoluteLocation(5, 5, 1);
		short waterSourceNumber = ENV.items.getItemById("op.water_source").number();
		input.setData15(AspectRegistry.BLOCK, waterLocation.getBlockAddress(), waterSourceNumber);
		input.setData15(AspectRegistry.BLOCK, waterLocation.getRelative(0, 1, 0).getBlockAddress(), waterSourceNumber);
		input.setData15(AspectRegistry.BLOCK, waterLocation.getRelative(1, 0, 0).getBlockAddress(), waterSourceNumber);
		input.setData15(AspectRegistry.BLOCK, waterLocation.getRelative(1, 1, 0).getBlockAddress(), waterSourceNumber);
		
		EntityLocation startLocation = new EntityLocation(5.0f, 5.0f, 1.4f);
		EntityLocation startVelocity = new EntityLocation(0.0f, 0.0f, -2.17f);
		CreatureEntity cow = CreatureEntity.create(assigner.next(), COW, startLocation, 0L);
		MutableCreature mutable = MutableCreature.existing(cow);
		mutable.newVelocity = startVelocity;
		mutable.newMovementPlan = List.of(waterLocation.getRelative(0, 0, 1), waterLocation.getRelative(-1, 0, 1));
		
		TickProcessingContext context = ContextBuilder.build()
			.lookups((AbsoluteLocation location) -> {
				return new BlockProxy(location.getBlockAddress(), input);
			}, null, null)
			.finish()
		;
		
		// Make sure that the movement is correct.
		EntityActionSimpleMove<IMutableCreatureEntity> change = CreatureLogic.planNextAction(context, mutable, context.millisPerTick);
		Assert.assertTrue(change.applyChange(context, mutable));
		Assert.assertEquals(new EntityLocation(5.0f, 5.0f, 1.54f), mutable.newLocation);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 1.37f), mutable.newVelocity);
	}

	@Test
	public void findBreedableTarget()
	{
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		CreatureIdAssigner assigner = new CreatureIdAssigner();
		EntityLocation fatherLocation = new EntityLocation(4.0f, 0.0f, 0.0f);
		CreatureEntity father = CreatureEntity.create(assigner.next(), COW, fatherLocation, 0L);
		EntityLocation motherLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		CreatureEntity mother = CreatureEntity.create(assigner.next(), COW, motherLocation, 0L);
		EntityLocation orcLocation = new EntityLocation(2.0f, 0.0f, 0.0f);
		CreatureEntity orc = CreatureEntity.create(assigner.next(), ORC, orcLocation, 0L);
		
		// Start with them both in a love mode but not yet targeting each other.
		MutableCreature mutable = MutableCreature.existing(father);
		mutable.setExtendedData(new CreatureExtendedData.LivestockData(true, null, 0L));
		father = mutable.freeze();
		mutable = MutableCreature.existing(mother);
		mutable.setExtendedData(new CreatureExtendedData.LivestockData(true, null, 0L));
		mother = mutable.freeze();
		Map<Integer, CreatureEntity> creatures = new HashMap<>();
		creatures.put(father.id(), father);
		creatures.put(mother.id(), mother);
		creatures.put(orc.id(), orc);
		
		TickProcessingContext context = ContextBuilder.build()
			.tick(1000L)
			.sinks(null, new TickProcessingContext.IChangeSink() {
				@Override
				public boolean next(int targetEntityId, IEntityAction<IMutablePlayerEntity> change)
				{
					throw new AssertionError();
				}
				@Override
				public boolean future(int targetEntityId, IEntityAction<IMutablePlayerEntity> change, long millisToDelay)
				{
					throw new AssertionError();
				}
				@Override
				public boolean creature(int targetCreatureId, IEntityAction<IMutableCreatureEntity> change)
				{
					throw new AssertionError();
				}
				@Override
				public boolean passive(int targetPassiveId, IPassiveAction action)
				{
					throw new AssertionError();
				}
			})
			.lookups((AbsoluteLocation location) -> {
				return location.getCuboidAddress().equals(cuboidAddress)
					? new BlockProxy(location.getBlockAddress(), input)
					: null
				;
			}, (Integer entityId) -> {
				return creatures.containsKey(entityId)
					? MinimalEntity.fromCreature(creatures.get(entityId))
					: null
				;
			}, null)
			.finish()
		;
		
		// We should see them target each other.
		mutable = MutableCreature.existing(father);
		boolean didTakeAction = CreatureLogic.didTakeSpecialActions(context
			, EntityCollection.fromMaps(Map.of(), creatures)
			, mutable
		);
		Assert.assertFalse(didTakeAction);
		Assert.assertEquals(mother.id(), mutable.newTargetEntityId);
		
		mutable = MutableCreature.existing(mother);
		CreatureLogic.didTakeSpecialActions(context
			, EntityCollection.fromMaps(Map.of(), creatures)
			, mutable
		);
		Assert.assertFalse(didTakeAction);
		Assert.assertEquals(father.id(), mutable.newTargetEntityId);
	}

	@Test
	public void breedingTimeout()
	{
		CreatureIdAssigner assigner = new CreatureIdAssigner();
		CreatureEntity cow = CreatureEntity.create(assigner.next(), COW, new EntityLocation(0.0f, 0.0f, 0.0f), 0L);
		MutableCreature mutable = MutableCreature.existing(cow);
		long nextReadyMillis = 2000L;
		mutable.setExtendedData(new CreatureExtendedData.LivestockData(false, null, nextReadyMillis));
		Assert.assertFalse(CreatureLogic.applyItemToCreature(WHEAT, mutable, nextReadyMillis - 1L));
		Assert.assertFalse(((CreatureExtendedData.LivestockData)mutable.getExtendedData()).inLoveMode());
		Assert.assertTrue(CreatureLogic.applyItemToCreature(WHEAT, mutable, nextReadyMillis));
		Assert.assertTrue(((CreatureExtendedData.LivestockData)mutable.getExtendedData()).inLoveMode());
	}

	@Test
	public void calfGrowsUp()
	{
		// Show how a calf grows into an adult.
		CreatureIdAssigner assigner = new CreatureIdAssigner();
		EntityType cowBaby = ENV.creatures.getTypeById("op.cow_baby");
		EntityLocation cowLocation = new EntityLocation(0.0f, 0.0f, 1.0f);
		CreatureEntity cow = CreatureEntity.create(assigner.next(), cowBaby, cowLocation, 0L);
		MutableCreature mutableCow = MutableCreature.existing(cow);
		
		TickProcessingContext context = ContextBuilder.build()
				.tick(CreatureExtendedData.BabyCodec.MILLIS_TO_MATURITY / ContextBuilder.DEFAULT_MILLIS_PER_TICK)
				.finish()
		;
		boolean didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, EntityCollection.emptyCollection()
				, mutableCow
		);
		Assert.assertTrue(didTakeAction);
		CreatureEntity output = mutableCow.freeze();
		Assert.assertEquals(COW, output.type());
		Assert.assertTrue(output.extendedData() instanceof CreatureExtendedData.LivestockData);
	}

	@Test
	public void rangedAttack()
	{
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		_setLayer(input, (byte)0, "op.stone");
		CreatureIdAssigner assigner = new CreatureIdAssigner();
		EntityLocation playerLocation = new EntityLocation(4.5f, 0.0f, 1.0f);
		MutableEntity mutable = MutableEntity.createForTest(1);
		mutable.newLocation = playerLocation;
		Entity player = mutable.freeze();
		EntityType skeletonType = ENV.creatures.getTypeById("op.skeleton");
		EntityLocation skeletonLocation = new EntityLocation(0.0f, 0.0f, 1.0f);
		CreatureEntity skeleton = CreatureEntity.create(assigner.next(), skeletonType, skeletonLocation, 0L);
		
		Function<AbsoluteLocation, BlockProxy> previousBlockLookUp = (AbsoluteLocation blockLocation) -> {
			return blockLocation.getCuboidAddress().equals(cuboidAddress)
				? new BlockProxy(blockLocation.getBlockAddress(), input)
				: null
			;
		};
		Function<Integer, MinimalEntity> previousEntityLookUp = (Integer id) -> {
			MinimalEntity min;
			switch (id)
			{
			case -1:
				min = MinimalEntity.fromCreature(skeleton);
				break;
			case 1:
				min = MinimalEntity.fromEntity(player);
				break;
			default:
				throw new AssertionError();
			}
			return min;
		};
		long millisPerTick = 100L;
		PassiveEntity[] out = new PassiveEntity[1];
		TickProcessingContext.IPassiveSpawner passiveSpawner = (PassiveType type, EntityLocation location, EntityLocation velocity, Object extendedData) -> {
			Assert.assertNull(out[0]);
			out[0] = new PassiveEntity(1
				, type
				, location
				, velocity
				, extendedData
				, 1000L
			);
		};
		TickProcessingContext context = ContextBuilder.build()
			.millisPerTick(millisPerTick)
			.tick(CreatureLogic.MINIMUM_MILLIS_TO_ACTION / millisPerTick)
			.lookups(previousBlockLookUp, previousEntityLookUp, null)
			.passive(passiveSpawner)
			.finish()
		;
		
		// Start with the skeleton targeting the player.
		MutableCreature mutableSkeleton = MutableCreature.existing(skeleton);
		mutableSkeleton.newTargetEntityId = player.id();
		boolean didTakeAction = CreatureLogic.didTakeSpecialActions(context
			, EntityCollection.fromMaps(Map.of(player.id(), player), Map.of(skeleton.id(), skeleton))
			, mutableSkeleton
		);
		// They should attack since they are ranged.
		Assert.assertTrue(didTakeAction);
		Assert.assertEquals(player.id(), mutableSkeleton.newTargetEntityId);
		Assert.assertNotNull(out[0]);
		Assert.assertEquals(PassiveType.PROJECTILE_ARROW, out[0].type());
		Assert.assertEquals(new EntityLocation(0.3f, 0.3f, 2.62f), out[0].location());
		Assert.assertEquals(new EntityLocation(9.98f, 0.0f, 0.61f), out[0].velocity());
		out[0] = null;
		
		// A second attack on the following tick should fail since we are on cooldown.
		didTakeAction = CreatureLogic.didTakeSpecialActions(context
			, EntityCollection.fromMaps(Map.of(player.id(), player), Map.of(skeleton.id(), skeleton))
			, mutableSkeleton
		);
		Assert.assertFalse(didTakeAction);
		Assert.assertEquals(player.id(), mutableSkeleton.newTargetEntityId);
		Assert.assertNull(out[0]);
		
		// But will work if we advance tick number further.
		context = ContextBuilder.build()
			.tick(context.currentTick + CreatureLogic.MILLIS_ATTACK_COOLDOWN / context.millisPerTick)
			.lookups(previousBlockLookUp, previousEntityLookUp, null)
			.passive(passiveSpawner)
			.finish()
		;
		didTakeAction = CreatureLogic.didTakeSpecialActions(context
			, EntityCollection.fromMaps(Map.of(player.id(), player), Map.of(skeleton.id(), skeleton))
			, mutableSkeleton
		);
		Assert.assertTrue(didTakeAction);
		Assert.assertEquals(player.id(), mutableSkeleton.newTargetEntityId);
		Assert.assertNotNull(out[0]);
		Assert.assertEquals(PassiveType.PROJECTILE_ARROW, out[0].type());
		Assert.assertEquals(new EntityLocation(0.3f, 0.3f, 2.62f), out[0].location());
		Assert.assertEquals(new EntityLocation(9.98f, 0.0f, 0.61f), out[0].velocity());
	}


	private static TickProcessingContext _createContext(Function<AbsoluteLocation, BlockProxy> previousBlockLookUp, int random)
	{
		TickProcessingContext context = ContextBuilder.build()
				.lookups(previousBlockLookUp, null, null)
				.fixedRandom(random)
				.finish()
		;
		return context;
	}

	private void _setLayer(CuboidData input, byte z, String name)
	{
		short stoneNumber = ENV.items.getItemById(name).number();
		for (byte x = 0; x < 32; ++x)
		{
			for (byte y = 0; y < 32; ++y)
			{
				input.setData15(AspectRegistry.BLOCK, new BlockAddress(x, y, z), stoneNumber);
			}
		}
	}

	private static CreatureEntity _updateKeepAlive(CreatureEntity original, long gameMillis)
	{
		return new CreatureEntity(original.id()
			, original.type()
			, original.location()
			, original.velocity()
			, original.yaw()
			, original.pitch()
			, original.health()
			, original.breath()
			, original.extendedData()
			
			, new Ephemeral(
				original.ephemeral().movementPlan()
				, original.ephemeral().lastActionMillis()
				, original.ephemeral().shouldTakeImmediateAction()
				, gameMillis
				, original.ephemeral().targetEntityId()
				, original.ephemeral().targetPreviousLocation()
				, original.ephemeral().lastAttackMillis()
				, original.ephemeral().lastDamageTakenMillis()
			)
		);
	}
}
