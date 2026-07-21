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
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.logic.CreatureIdAssigner;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.ContextBuilder;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.IEntityAction;
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
import com.jeffdisher.october.utils.LazyEntityIndex;


public class TestCreatureLogic
{
	private static Environment ENV;
	private static Item WHEAT;
	private static EntityType COW;
	private static EntityType ORC;
	private static EntityType COW_BABY;
	private static EntityType VILLAGER;
	private static EntityType VILLAGER_BABY;
	@BeforeClass
	public static void setup() throws Throwable
	{
		ENV = Environment.createSharedInstance();
		WHEAT = ENV.items.getItemById("op.wheat_item");
		COW = ENV.creatures.getTypeById("op.cow");
		ORC = ENV.creatures.getTypeById("op.orc");
		COW_BABY = ENV.creatures.getTypeById("op.cow_baby");
		VILLAGER = ENV.creatures.getTypeById("op.villager");
		VILLAGER_BABY = ENV.creatures.getTypeById("op.villager_baby");
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
						? BlockProxy.load(location.getBlockAddress(), input)
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
						? BlockProxy.load(location.getBlockAddress(), input)
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
						? BlockProxy.load(location.getBlockAddress(), input)
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
						? BlockProxy.load(location.getBlockAddress(), input)
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
				.lookups(ContextBuilder.buildFetcher((AbsoluteLocation location) -> {
					return location.getCuboidAddress().equals(cuboidAddress)
							? BlockProxy.load(location.getBlockAddress(), input)
							: null
					;
				}), null, null)
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
		IEntityAction<MutableCreature> action = CreatureLogic.planNextAction(context
				, mutable
				, 100L
		);
		Assert.assertNotNull(action);
		Assert.assertNull(mutable.movementPlan.fullPlan());
		Assert.assertEquals(CreatureEntity.NO_TARGET_ENTITY_ID, mutable.movementPlan.targetEntityId());
		Assert.assertEquals(null, mutable.movementPlan.targetPreviousLocation());
		Assert.assertEquals(new EntityLocation(14.0f, 16.0f, 1.0f), mutable.movementPlan.directLocation());
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
					? BlockProxy.load(location.getBlockAddress(), input)
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
		Entity player = new Entity(1
			, ENV.creatures.PLAYER
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
		);
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		_setLayer(input, (byte)0, "op.stone");
		
		// We should see them acquire this target.
		TickProcessingContext.IBlockFetcher previousBlockLookUp = ContextBuilder.buildFetcher((AbsoluteLocation location) -> {
			return location.getCuboidAddress().equals(cuboidAddress)
					? BlockProxy.load(location.getBlockAddress(), input)
					: null
			;
		});
		TickProcessingContext context = ContextBuilder.build()
			.tick((CreatureLogic.MINIMUM_MILLIS_TO_ACTION / 100L) + 1L)
			.lookups(previousBlockLookUp, new _PairEntityIndex(player, orc), null)
			.fixedRandom(2)
			.finish()
		;
		MutableCreature mutableOrc = MutableCreature.existing(orc);
		
		// First, choose the target.
		boolean didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, EntityCollection.fromMaps(Map.of(player.id(), player), Map.of(orc.id(), orc))
				, mutableOrc
		);
		Assert.assertFalse(didTakeAction);
		IEntityAction<MutableCreature> action = CreatureLogic.planNextAction(context
				, mutableOrc
				, 100L
		);
		Assert.assertNotNull(action);
		Assert.assertNull(mutableOrc.movementPlan.fullPlan());
		Assert.assertEquals(player.id(), mutableOrc.movementPlan.targetEntityId());
		Assert.assertEquals(player.location(), mutableOrc.movementPlan.targetPreviousLocation());
		Assert.assertEquals(player.location(), mutableOrc.movementPlan.directLocation());
		
		// Now, move the entity and see that the special action updates it.
		EntityLocation newPlayerLocation = new EntityLocation(2.0f, 5.0f, 1.0f);
		player = new Entity(1
			, ENV.creatures.PLAYER
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
		context = ContextBuilder.build()
			.tick((CreatureLogic.MINIMUM_MILLIS_TO_ACTION / 100L) + 1L)
			.lookups(previousBlockLookUp, new _PairEntityIndex(player, orc), null)
			.fixedRandom(2)
			.finish()
		;
		didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, EntityCollection.fromMaps(Map.of(player.id(), player), Map.of(orc.id(), orc))
				, mutableOrc
		);
		Assert.assertFalse(didTakeAction);
		// We should now see the updated movement plan.
		Assert.assertNull(mutableOrc.movementPlan.fullPlan());
		Assert.assertEquals(player.id(), mutableOrc.movementPlan.targetEntityId());
		Assert.assertEquals(player.location(), mutableOrc.movementPlan.targetPreviousLocation());
		Assert.assertEquals(player.location(), mutableOrc.movementPlan.directLocation());
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
		TickProcessingContext.IBlockFetcher previousBlockLookUp = ContextBuilder.buildFetcher((AbsoluteLocation location) -> {
			return location.getCuboidAddress().equals(cuboidAddress)
					? BlockProxy.load(location.getBlockAddress(), input)
					: null
			;
		});
		// (we won't expose any of the entities since we don't expect targeting).
		_MinimalEntityIndex previousEntityLookUp = new _MinimalEntityIndex(Map.of());
		TickProcessingContext context = ContextBuilder.build()
			.tick(startTick)
			.lookups(previousBlockLookUp, previousEntityLookUp, null)
			.fixedRandom(2)
			.finish()
		;
		MutableCreature mutableCow = MutableCreature.existing(cow);
		MutableCreature mutableOrc = MutableCreature.existing(orc);
		mutableOrc.despawnMillis = startTick * context.millisPerTick + CreatureEntity.MILLIS_UNTIL_NO_ACTION_DESPAWN;
		boolean didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, EntityCollection.fromMaps(Map.of(), Map.of(cow.id(), cow, orc.id(), orc))
				, mutableCow
		);
		Assert.assertFalse(didTakeAction);
		Assert.assertFalse(mutableCow.shouldTakeActionInTick);
		Assert.assertEquals((byte)40, mutableCow.newHealth);
		didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, EntityCollection.fromMaps(Map.of(), Map.of(cow.id(), cow, orc.id(), orc))
				, mutableOrc
		);
		Assert.assertFalse(didTakeAction);
		Assert.assertFalse(mutableCow.shouldTakeActionInTick);
		Assert.assertEquals((byte)20, mutableOrc.newHealth);
		
		// Now, advance time and do the same, seeing the despawn of the orc but not the cow.
		context = ContextBuilder.build()
			.tick(startTick + (CreatureEntity.MILLIS_UNTIL_NO_ACTION_DESPAWN / context.millisPerTick))
			.lookups(previousBlockLookUp, previousEntityLookUp, null)
			.fixedRandom(2)
			.finish()
		;
		didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, EntityCollection.fromMaps(Map.of(), Map.of(cow.id(), cow, orc.id(), orc))
				, mutableCow
		);
		Assert.assertFalse(didTakeAction);
		Assert.assertFalse(mutableCow.shouldTakeActionInTick);
		Assert.assertEquals((byte)40, mutableCow.newHealth);
		didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, EntityCollection.fromMaps(Map.of(), Map.of(cow.id(), cow, orc.id(), orc))
				, mutableOrc
		);
		Assert.assertTrue(didTakeAction);
		Assert.assertFalse(mutableCow.shouldTakeActionInTick);
		Assert.assertEquals((byte)0,mutableOrc.newHealth);
	}

	@Test
	public void enterLoveMode()
	{
		CreatureIdAssigner assigner = new CreatureIdAssigner();
		CreatureEntity cow = CreatureEntity.create(assigner.next(), COW, new EntityLocation(0.0f, 0.0f, 0.0f), 0L);
		MutableCreature mutable = MutableCreature.existing(cow);
		CreatureLogic.applyItemToCreature(WHEAT, mutable, 1000L);
		Assert.assertTrue(((ExtensionLivestock.LivestockData)mutable.newExtendedData).breeding().inLoveMode());
		Assert.assertTrue(mutable.shouldTakeActionInTick);
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
		mutable.movementPlan = new CreatureEntity.MovementPlan(null
			, mother.id()
			, null
			, null
		);
		mutable.newExtendedData = new ExtensionLivestock.LivestockData(new CommonBreedingLogic.Data(true, null, 0L));
		father = mutable.freeze();
		mutable = MutableCreature.existing(mother);
		mutable.movementPlan = new CreatureEntity.MovementPlan(null
			, father.id()
			, null
			, null
		);
		mutable.newExtendedData = new ExtensionLivestock.LivestockData(new CommonBreedingLogic.Data(true, null, 0L));
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
					public boolean creature(int targetCreatureId, IEntityAction<MutableCreature> change)
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
				.lookups(null, new LazyEntityIndex(Map.of(), creatures), null)
				.fixedRandom(2)
				.finish()
		;
		
		// We should see the father sending a message
		mutable = MutableCreature.existing(father);
		boolean didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, EntityCollection.fromMaps(Map.of(), creatures)
				, mutable
		);
		Assert.assertTrue(didTakeAction);
		Assert.assertFalse(mutable.shouldTakeActionInTick);
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
		Assert.assertFalse(mutable.shouldTakeActionInTick);
		Assert.assertNull(message[0]);
		mother = mutable.freeze();
		creatures.put(mother.id(), mother);
		
		// The father should no longer be in love mode but the mother should be.
		Assert.assertFalse(((ExtensionLivestock.LivestockData)father.extendedData()).breeding().inLoveMode());
		Assert.assertTrue(((ExtensionLivestock.LivestockData)mother.extendedData()).breeding().inLoveMode());
	}

	@Test
	public void becomePregnant()
	{
		CreatureIdAssigner assigner = new CreatureIdAssigner();
		EntityLocation fatherLocation = new EntityLocation(0.8f, 0.0f, 0.0f);
		EntityLocation motherLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		CreatureEntity mother = CreatureEntity.create(assigner.next(), COW, motherLocation, 0L);
		MutableCreature mutable = MutableCreature.existing(mother);
		mutable.newExtendedData = new ExtensionLivestock.LivestockData(new CommonBreedingLogic.Data(true, null, 0L));
		
		long gameTimeMillis = 1000L;
		boolean didBecomePregnant = CreatureLogic.setCreaturePregnant(mutable, fatherLocation, gameTimeMillis);
		Assert.assertTrue(didBecomePregnant);
		Assert.assertTrue(mutable.shouldTakeActionInTick);
		Assert.assertFalse(((ExtensionLivestock.LivestockData)mutable.newExtendedData).breeding().inLoveMode());
		Assert.assertEquals(new EntityLocation(0.4f, 0.0f, 0.0f), ((ExtensionLivestock.LivestockData)mutable.newExtendedData).breeding().offspringLocation());
		Assert.assertEquals(gameTimeMillis + CommonBreedingLogic.MILLIS_BREEDING_COOLDOWN, ((ExtensionLivestock.LivestockData)mutable.newExtendedData).breeding().breedingReadyMillis());
	}

	@Test
	public void spawnOffspring()
	{
		CreatureIdAssigner assigner = new CreatureIdAssigner();
		EntityLocation offspringLocation = new EntityLocation(0.4f, 0.0f, 0.0f);
		EntityLocation motherLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		CreatureEntity mother = CreatureEntity.create(assigner.next(), COW, motherLocation, 0L);
		MutableCreature mutable = MutableCreature.existing(mother);
		mutable.newExtendedData = new ExtensionLivestock.LivestockData(new CommonBreedingLogic.Data(false, offspringLocation, 0L));
		
		CreatureEntity[] offspring = new CreatureEntity[1];
		TickProcessingContext.ICreatureSpawner creatureSpawner = (EntityType type, EntityLocation location) -> {
			Assert.assertNull(offspring[0]);
			offspring[0] = CreatureEntity.create(assigner.next(), type, location, 0L);
		};
		TickProcessingContext context = ContextBuilder.build()
			.spawner(creatureSpawner)
			.fixedRandom(2)
			.finish()
		;
		boolean didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, EntityCollection.fromMaps(Map.of(), Map.of(mother.id(), mother))
				, mutable
		);
		Assert.assertTrue(didTakeAction);
		Assert.assertFalse(mutable.shouldTakeActionInTick);
		Assert.assertNull(((ExtensionLivestock.LivestockData)mutable.newExtendedData).breeding().offspringLocation());
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
		
		TickProcessingContext.IBlockFetcher previousBlockLookUp = ContextBuilder.buildFetcher((AbsoluteLocation blockLocation) -> {
			return blockLocation.getCuboidAddress().equals(cuboidAddress)
					? BlockProxy.load(blockLocation.getBlockAddress(), input)
					: null
			;
		});
		_PairEntityIndex previousEntityLookUp = new _PairEntityIndex(player, orc);
		long millisPerTick = 100L;
		TickProcessingContext context = ContextBuilder.build()
			.millisPerTick(millisPerTick)
			.tick(CreatureLogic.MINIMUM_MILLIS_TO_ACTION / millisPerTick)
			.lookups(previousBlockLookUp, previousEntityLookUp, null)
			.fixedRandom(2)
			.finish()
		;
		MutableCreature mutableOrc = MutableCreature.existing(orc);
		boolean didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, EntityCollection.fromMaps(Map.of(player.id(), player), Map.of(orc.id(), orc))
				, mutableOrc
		);
		Assert.assertFalse(didTakeAction);
		EntityActionSimpleMove<MutableCreature> action = CreatureLogic.planNextAction(context
				, mutableOrc
				, 100L
		);
		Assert.assertNotNull(action);
		Assert.assertNull(mutableOrc.movementPlan.fullPlan());
		Assert.assertEquals(player.id(), mutableOrc.movementPlan.targetEntityId());
		Assert.assertEquals(player.location(), mutableOrc.movementPlan.targetPreviousLocation());
		Assert.assertEquals(player.location(), mutableOrc.movementPlan.directLocation());
	}

	@Test
	public void sendAttack()
	{
		long attackCooldownMillis = ORC.actionCooldownMillis();
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
		
		TickProcessingContext.IBlockFetcher previousBlockLookUp = ContextBuilder.buildFetcher((AbsoluteLocation blockLocation) -> {
			return blockLocation.getCuboidAddress().equals(cuboidAddress)
					? BlockProxy.load(blockLocation.getBlockAddress(), input)
					: null
			;
		});
		_PairEntityIndex previousEntityLookUp = new _PairEntityIndex(player, orc);
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
			public boolean creature(int targetCreatureId, IEntityAction<MutableCreature> change)
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
			.fixedRandom(2)
			.finish()
		;
		
		// Start with the orc targeting the player.
		MutableCreature mutableOrc = MutableCreature.existing(orc);
		mutableOrc.movementPlan = new CreatureEntity.MovementPlan(null
			, player.id()
			, null
			, null
		);
		
		// The first attempt to attack should fail since we are still out of range.
		boolean didTakeAction = CreatureLogic.didTakeSpecialActions(context
			, EntityCollection.fromMaps(Map.of(player.id(), player), Map.of(orc.id(), orc))
			, mutableOrc
		);
		Assert.assertFalse(didTakeAction);
		EntityActionSimpleMove<MutableCreature> action = CreatureLogic.planNextAction(context
			, mutableOrc
			, 100L
		);
		// We will try to walk toward them still.
		Assert.assertNotNull(action);
		Assert.assertEquals(player.id(), mutableOrc.movementPlan.targetEntityId());
		Assert.assertEquals(player.location().getBlockLocation(), mutableOrc.movementPlan.targetPreviousLocation().getBlockLocation());
		Assert.assertEquals(context.currentTickTimeMillis + ORC.actionCooldownMillis(), mutableOrc.nextActionMillis);
		
		// Update the player to be close enough.
		Assert.assertTrue(action.applyChange(context, mutableOrc));
		// Also, update our next action millis so we can try.
		mutableOrc.nextActionMillis = context.currentTickTimeMillis;
		
		// Now, allow it to perform the attack.
		didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, EntityCollection.fromMaps(Map.of(player.id(), player), Map.of(orc.id(), orc))
				, mutableOrc
		);
		Assert.assertTrue(didTakeAction);
		Assert.assertEquals(player.id(), mutableOrc.movementPlan.targetEntityId());
		Assert.assertEquals(context.currentTickTimeMillis + attackCooldownMillis, mutableOrc.nextActionMillis);
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
		Assert.assertEquals(player.id(), mutableOrc.movementPlan.targetEntityId());
		Assert.assertEquals(0, outChanges.size());
		
		// But will work if we advance tick number further.
		context = ContextBuilder.build()
			.tick(context.currentTick + attackCooldownMillis / context.millisPerTick)
			.sinks(null, changeSink)
			.lookups(previousBlockLookUp, previousEntityLookUp, null)
			.fixedRandom(2)
			.finish()
		;
		didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, EntityCollection.fromMaps(Map.of(player.id(), player), Map.of(orc.id(), orc))
				, mutableOrc
		);
		Assert.assertTrue(didTakeAction);
		Assert.assertEquals(player.id(), mutableOrc.movementPlan.targetEntityId());
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
		mutablePlayer.slotManager.setSelectedKey(1);
		Entity player = mutablePlayer.freeze();
		EntityLocation cowLocation = new EntityLocation(0.0f, 0.0f, 1.0f);
		CreatureEntity cow = CreatureEntity.create(assigner.next(), COW, cowLocation, 0L);
		MutableCreature mutableCow = MutableCreature.existing(cow);
		
		TickProcessingContext.IBlockFetcher previousBlockLookUp = ContextBuilder.buildFetcher((AbsoluteLocation blockLocation) -> {
			return blockLocation.getCuboidAddress().equals(cuboidAddress)
					? BlockProxy.load(blockLocation.getBlockAddress(), input)
					: null
			;
		});
		Map<Integer, MinimalEntity> entities = new HashMap<>();
		_MinimalEntityIndex previousEntityLookUp = new _MinimalEntityIndex(entities);
		TickProcessingContext context = ContextBuilder.build()
			.tick(1000L)
			.lookups(previousBlockLookUp, previousEntityLookUp, null)
			.fixedRandom(2)
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
		Assert.assertNull(mutableCow.movementPlan.fullPlan());
		Assert.assertEquals(player.id(), mutableCow.movementPlan.targetEntityId());
		Assert.assertEquals(player.location(), mutableCow.movementPlan.targetPreviousLocation());
		Assert.assertEquals(player.location(), mutableCow.movementPlan.directLocation());
		
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
		Assert.assertEquals(player.id(), mutableCow.movementPlan.targetEntityId());
		Assert.assertNull(mutableCow.movementPlan.targetPreviousLocation());
		Assert.assertNull(mutableCow.movementPlan.fullPlan());
		
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
		Assert.assertNull(mutableCow.movementPlan.fullPlan());
		Assert.assertEquals(player.id(), mutableCow.movementPlan.targetEntityId());
		Assert.assertEquals(player.location(), mutableCow.movementPlan.targetPreviousLocation());
		Assert.assertEquals(player.location(), mutableCow.movementPlan.directLocation());
		
		// Switch out wheat.
		mutablePlayer.slotManager.setSelectedKey(Entity.NO_SELECTION);
		player = mutablePlayer.freeze();
		entities.put(player.id(), MinimalEntity.fromEntity(player));
		cow = mutableCow.freeze();
		entities.put(cow.id(), MinimalEntity.fromCreature(cow));
		didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, EntityCollection.fromMaps(Map.of(player.id(), player), Map.of(cow.id(), cow))
				, mutableCow
		);
		Assert.assertFalse(didTakeAction);
		Assert.assertNull(mutableCow.movementPlan);
	}

	@Test
	public void closeBug()
	{
		// A test related to a bug where a hostile mob would get "stuck" just out of range of the player.
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(-1, 0, 0);
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		_setLayer(input, (byte)0, "op.stone");
		CreatureIdAssigner assigner = new CreatureIdAssigner();
		EntityLocation location = new EntityLocation(-16.0f, 7.0f, 1.0f);
		int entityId = 1;
		MutableEntity mutablePlayer = MutableEntity.createForTest(entityId);
		mutablePlayer.newLocation = location;
		Entity player = mutablePlayer.freeze();
		EntityLocation orcLocation = new EntityLocation(-16.99f, 7.99f, 1.0f);
		CreatureEntity orc = CreatureEntity.create(assigner.next(), ORC, orcLocation, 0L);
		MutableCreature mutableOrc = MutableCreature.existing(orc);
		
		TickProcessingContext.IBlockFetcher previousBlockLookUp = ContextBuilder.buildFetcher((AbsoluteLocation blockLocation) -> {
			return blockLocation.getCuboidAddress().equals(cuboidAddress)
				? BlockProxy.load(blockLocation.getBlockAddress(), input)
				: null
			;
		});
		Map<Integer, MinimalEntity> entities = new HashMap<>();
		_MinimalEntityIndex previousEntityLookUp = new _MinimalEntityIndex(entities);
		TickProcessingContext context = ContextBuilder.build()
			.tick(1000L)
			.lookups(previousBlockLookUp, previousEntityLookUp, null)
			.fixedRandom(2)
			.finish()
		;
		
		// In the bug, the orc had the entity as a target but no movement plan.
		mutableOrc.movementPlan = new CreatureEntity.MovementPlan(null
			, entityId
			, location
			, location
		);
		entities.put(player.id(), MinimalEntity.fromEntity(player));
		entities.put(orc.id(), MinimalEntity.fromCreature(orc));
		boolean didTakeAction = CreatureLogic.didTakeSpecialActions(context
			, EntityCollection.fromMaps(Map.of(player.id(), player), Map.of(orc.id(), orc))
			, mutableOrc
		);
		
		// Since they are out of range, they should have taken no action and, since they are in the same block, produced no full path to move.
		Assert.assertFalse(didTakeAction);
		Assert.assertNull(mutableOrc.movementPlan.fullPlan());
		Assert.assertEquals(player.id(), mutableOrc.movementPlan.targetEntityId());
		Assert.assertNotNull(mutableOrc.movementPlan.targetPreviousLocation());
		
		// Now, if we try to move, we should see them move toward the target, even though there is no planned path.
		EntityActionSimpleMove<MutableCreature> change = CreatureLogic.planNextAction(context, mutableOrc, context.millisPerTick);
		Assert.assertEquals("SimpleMove(WALKING), by 0.14, -0.14, Sub: null", change.toString());
		Assert.assertNull(mutableOrc.movementPlan.fullPlan());
		Assert.assertEquals(player.id(), mutableOrc.movementPlan.targetEntityId());
		Assert.assertNotNull(mutableOrc.movementPlan.targetPreviousLocation());
		
		// This should apply cleanly.
		boolean didApply = change.applyChange(context, mutableOrc);
		Assert.assertTrue(didApply);
		Assert.assertEquals(new EntityLocation(-16.85f, 7.85f, 1.0f), mutableOrc.newLocation);
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
		mutable.movementPlan = new CreatureEntity.MovementPlan(List.of(waterLocation.getRelative(0, 0, 1), waterLocation.getRelative(-1, 0, 1))
			, CreatureEntity.NO_TARGET_ENTITY_ID
			, null
			, null
		);
		
		TickProcessingContext context = ContextBuilder.build()
			.lookups(ContextBuilder.buildFetcher((AbsoluteLocation location) -> {
				return BlockProxy.load(location.getBlockAddress(), input);
			}), null, null)
			.fixedRandom(2)
			.finish()
		;
		
		// Make sure that the movement is correct.
		EntityActionSimpleMove<MutableCreature> change = CreatureLogic.planNextAction(context, mutable, context.millisPerTick);
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
		mutable.newExtendedData = new ExtensionLivestock.LivestockData(new CommonBreedingLogic.Data(true, null, 0L));
		father = mutable.freeze();
		mutable = MutableCreature.existing(mother);
		mutable.newExtendedData = new ExtensionLivestock.LivestockData(new CommonBreedingLogic.Data(true, null, 0L));
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
				public boolean creature(int targetCreatureId, IEntityAction<MutableCreature> change)
				{
					throw new AssertionError();
				}
				@Override
				public boolean passive(int targetPassiveId, IPassiveAction action)
				{
					throw new AssertionError();
				}
			})
			.lookups(ContextBuilder.buildFetcher((AbsoluteLocation location) -> {
				return location.getCuboidAddress().equals(cuboidAddress)
					? BlockProxy.load(location.getBlockAddress(), input)
					: null
				;
				})
				, new LazyEntityIndex(Map.of(), creatures)
				, null)
			.fixedRandom(2)
			.finish()
		;
		
		// We should see them target each other.
		mutable = MutableCreature.existing(father);
		boolean didTakeAction = CreatureLogic.didTakeSpecialActions(context
			, EntityCollection.fromMaps(Map.of(), creatures)
			, mutable
		);
		Assert.assertFalse(didTakeAction);
		Assert.assertEquals(mother.id(), mutable.movementPlan.targetEntityId());
		
		mutable = MutableCreature.existing(mother);
		CreatureLogic.didTakeSpecialActions(context
			, EntityCollection.fromMaps(Map.of(), creatures)
			, mutable
		);
		Assert.assertFalse(didTakeAction);
		Assert.assertEquals(father.id(), mutable.movementPlan.targetEntityId());
	}

	@Test
	public void breedingTimeout()
	{
		CreatureIdAssigner assigner = new CreatureIdAssigner();
		CreatureEntity cow = CreatureEntity.create(assigner.next(), COW, new EntityLocation(0.0f, 0.0f, 0.0f), 0L);
		MutableCreature mutable = MutableCreature.existing(cow);
		long nextReadyMillis = 2000L;
		mutable.newExtendedData = new ExtensionLivestock.LivestockData(new CommonBreedingLogic.Data(false, null, nextReadyMillis));
		Assert.assertFalse(CreatureLogic.applyItemToCreature(WHEAT, mutable, nextReadyMillis - 1L));
		Assert.assertFalse(((ExtensionLivestock.LivestockData)mutable.newExtendedData).breeding().inLoveMode());
		Assert.assertTrue(CreatureLogic.applyItemToCreature(WHEAT, mutable, nextReadyMillis));
		Assert.assertTrue(((ExtensionLivestock.LivestockData)mutable.newExtendedData).breeding().inLoveMode());
	}

	@Test
	public void calfGrowsUp()
	{
		// Show how a calf grows into an adult.
		CreatureIdAssigner assigner = new CreatureIdAssigner();
		EntityLocation cowLocation = new EntityLocation(0.0f, 0.0f, 1.0f);
		CreatureEntity cow = CreatureEntity.create(assigner.next(), COW_BABY, cowLocation, 0L);
		MutableCreature mutableCow = MutableCreature.existing(cow);
		
		TickProcessingContext context = ContextBuilder.build()
				.tick(ExtensionLivestockBaby.MILLIS_TO_MATURITY / ContextBuilder.DEFAULT_MILLIS_PER_TICK)
				.fixedRandom(0)
				.finish()
		;
		boolean didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, EntityCollection.emptyCollection()
				, mutableCow
		);
		Assert.assertTrue(didTakeAction);
		CreatureEntity output = mutableCow.freeze();
		Assert.assertEquals(COW, output.type());
		Assert.assertTrue(output.extendedData() instanceof ExtensionLivestock.LivestockData);
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
		long attackCooldownMillis = skeletonType.actionCooldownMillis();
		EntityLocation skeletonLocation = new EntityLocation(0.0f, 0.0f, 1.0f);
		CreatureEntity skeleton = CreatureEntity.create(assigner.next(), skeletonType, skeletonLocation, 0L);
		
		TickProcessingContext.IBlockFetcher previousBlockLookUp = ContextBuilder.buildFetcher((AbsoluteLocation blockLocation) -> {
			return blockLocation.getCuboidAddress().equals(cuboidAddress)
				? BlockProxy.load(blockLocation.getBlockAddress(), input)
				: null
			;
		});
		_PairEntityIndex previousEntityLookUp = new _PairEntityIndex(player, skeleton);
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
			.tick(attackCooldownMillis / millisPerTick)
			.lookups(previousBlockLookUp, previousEntityLookUp, null)
			.passive(passiveSpawner)
			.fixedRandom(2)
			.finish()
		;
		
		// Start with the skeleton targeting the player.
		MutableCreature mutableSkeleton = MutableCreature.existing(skeleton);
		mutableSkeleton.movementPlan = new CreatureEntity.MovementPlan(null
			, player.id()
			, null
			, null
		);
		boolean didTakeAction = CreatureLogic.didTakeSpecialActions(context
			, EntityCollection.fromMaps(Map.of(player.id(), player), Map.of(skeleton.id(), skeleton))
			, mutableSkeleton
		);
		// They should attack since they are ranged.
		Assert.assertTrue(didTakeAction);
		Assert.assertEquals(player.id(), mutableSkeleton.movementPlan.targetEntityId());
		Assert.assertEquals(context.currentTickTimeMillis + attackCooldownMillis, mutableSkeleton.nextActionMillis);
		Assert.assertNotNull(out[0]);
		Assert.assertEquals(PassiveType.PROJECTILE_ARROW, out[0].type());
		Assert.assertEquals(new EntityLocation(0.3f, 0.3f, 2.62f), out[0].location());
		Assert.assertEquals(new EntityLocation(24.75f, -0.56f, -3.46f), out[0].velocity());
		out[0] = null;
		
		// A second attack on the following tick should fail since we are on cooldown.
		long previousTickTime = context.currentTickTimeMillis;
		context = ContextBuilder.build()
			.tick(context.currentTick + 1L)
			.lookups(previousBlockLookUp, previousEntityLookUp, null)
			.passive(passiveSpawner)
			.fixedRandom(2)
			.finish()
		;
		didTakeAction = CreatureLogic.didTakeSpecialActions(context
			, EntityCollection.fromMaps(Map.of(player.id(), player), Map.of(skeleton.id(), skeleton))
			, mutableSkeleton
		);
		Assert.assertFalse(didTakeAction);
		Assert.assertEquals(player.id(), mutableSkeleton.movementPlan.targetEntityId());
		Assert.assertEquals(previousTickTime + attackCooldownMillis, mutableSkeleton.nextActionMillis);
		Assert.assertNull(out[0]);
		
		// But will work if we advance tick number further.
		context = ContextBuilder.build()
			.tick(context.currentTick + attackCooldownMillis / context.millisPerTick)
			.lookups(previousBlockLookUp, previousEntityLookUp, null)
			.passive(passiveSpawner)
			.fixedRandom(2)
			.finish()
		;
		didTakeAction = CreatureLogic.didTakeSpecialActions(context
			, EntityCollection.fromMaps(Map.of(player.id(), player), Map.of(skeleton.id(), skeleton))
			, mutableSkeleton
		);
		Assert.assertTrue(didTakeAction);
		Assert.assertEquals(player.id(), mutableSkeleton.movementPlan.targetEntityId());
		Assert.assertEquals(context.currentTickTimeMillis + attackCooldownMillis, mutableSkeleton.nextActionMillis);
		Assert.assertNotNull(out[0]);
		Assert.assertEquals(PassiveType.PROJECTILE_ARROW, out[0].type());
		Assert.assertEquals(new EntityLocation(0.3f, 0.3f, 2.62f), out[0].location());
		Assert.assertEquals(new EntityLocation(24.75f, -0.56f, -3.46f), out[0].velocity());
	}

	@Test
	public void calfTarget()
	{
		// The calf should never target anything so make sure that is correct and is correctly treated.
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		_setLayer(input, (byte)0, "op.stone");
		TickProcessingContext.IBlockFetcher previousBlockLookUp = ContextBuilder.buildFetcher((AbsoluteLocation blockLocation) -> {
			return blockLocation.getCuboidAddress().equals(cuboidAddress)
				? BlockProxy.load(blockLocation.getBlockAddress(), input)
				: null
			;
		});
		
		CreatureIdAssigner assigner = new CreatureIdAssigner();
		EntityLocation calfLocation = new EntityLocation(0.0f, 0.0f, 1.0f);
		EntityLocation cowLocation = new EntityLocation(1.0f, 0.0f, 1.0f);
		EntityLocation orcLocation = new EntityLocation(0.0f, 1.0f, 1.0f);
		EntityLocation playerLocation = new EntityLocation(1.0f, 1.0f, 1.0f);
		CreatureEntity calf = CreatureEntity.create(assigner.next(), COW_BABY, calfLocation, 0L);
		CreatureEntity cow = CreatureEntity.create(assigner.next(), COW, cowLocation, 0L);
		CreatureEntity orc= CreatureEntity.create(assigner.next(), ORC, orcLocation, 0L);
		MutableEntity mutable = MutableEntity.createForTest(1);
		mutable.newLocation = playerLocation;
		Entity player = mutable.freeze();
		EntityCollection collection = EntityCollection.fromMaps(Map.of(
			1, player
		), Map.of(
			calf.id(), calf
			, cow.id(), cow
			, orc.id(), orc
		));
		
		TickProcessingContext context = ContextBuilder.build()
			.tick(1000L)
			.lookups(previousBlockLookUp, null, null)
			.fixedRandom(2)
			.finish()
		;
		MutableCreature mutableCalf = MutableCreature.existing(calf);
		// We should take no action and not set any kind of target.
		boolean didTakeAction = CreatureLogic.didTakeSpecialActions(context
			, collection
			, mutableCalf
		);
		Assert.assertFalse(didTakeAction);
		Assert.assertNull(mutableCalf.movementPlan);
		// Run this again to make sure we didn't change state which caused confusion.
		didTakeAction = CreatureLogic.didTakeSpecialActions(context
			, collection
			, mutableCalf
		);
		Assert.assertFalse(didTakeAction);
		CreatureEntity output = mutableCalf.freeze();
		Assert.assertEquals(COW_BABY, output.type());
		Assert.assertTrue(output.extendedData() instanceof ExtensionLivestockBaby.BabyData);
	}

	@Test
	public void standingInViscosityChange()
	{
		// Show that we correctly plan the next action when partially standing in a block which changes viscosity.
		CreatureIdAssigner assigner = new CreatureIdAssigner();
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		_setLayer(input, (byte)0, "op.stone");
		AbsoluteLocation waterLocation = new AbsoluteLocation(5, 4, 1);
		short waterSourceNumber = ENV.items.getItemById("op.water_source").number();
		input.setData15(AspectRegistry.BLOCK, waterLocation.getBlockAddress(), waterSourceNumber);
		
		EntityLocation startLocation = new EntityLocation(4.7f, 4.99f, 1.0f);
		CreatureEntity cow = CreatureEntity.create(assigner.next(), COW, startLocation, 0L);
		MutableCreature mutable = MutableCreature.existing(cow);
		mutable.movementPlan = new CreatureEntity.MovementPlan(List.of(new AbsoluteLocation(4, 5, 1))
			, CreatureEntity.NO_TARGET_ENTITY_ID
			, null
			, null
		);
		
		TickProcessingContext context = ContextBuilder.build()
			.lookups(ContextBuilder.buildFetcher((AbsoluteLocation location) -> {
				return BlockProxy.load(location.getBlockAddress(), input);
			}), null, null)
			.fixedRandom(2)
			.finish()
		;
		
		// Make sure that the movement is correct.
		EntityActionSimpleMove<MutableCreature> change = CreatureLogic.planNextAction(context, mutable, context.millisPerTick);
		Assert.assertEquals("SimpleMove(WALKING), by 0.00, 0.04, Sub: null", change.toString());
		Assert.assertTrue(change.applyChange(context, mutable));
		Assert.assertEquals(new EntityLocation(4.7f, 5.01f, 1.0f), mutable.newLocation);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), mutable.newVelocity);
	}

	@Test
	public void suddenlyInSolidBlock()
	{
		// Show that we correctly handle the case where we have a plan but are suddenly in a solid block (like sand falling or a player placing a block at a racy time).
		CreatureIdAssigner assigner = new CreatureIdAssigner();
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		_setLayer(input, (byte)0, "op.stone");
		AbsoluteLocation blockLocation = new AbsoluteLocation(2, 3, 1);
		short blockNumber = ENV.items.getItemById("op.stone").number();
		input.setData15(AspectRegistry.BLOCK, blockLocation.getBlockAddress(), blockNumber);
		
		EntityLocation startLocation = new EntityLocation(2.79f, 3.01f, 1.0f);
		CreatureEntity cow = CreatureEntity.create(assigner.next(), COW, startLocation, 0L);
		MutableCreature mutable = MutableCreature.existing(cow);
		mutable.movementPlan = new CreatureEntity.MovementPlan(List.of(new AbsoluteLocation(2, 3, 1), new AbsoluteLocation(3, 3, 1))
			, CreatureEntity.NO_TARGET_ENTITY_ID
			, null
			, null
		);
		
		TickProcessingContext context = ContextBuilder.build()
			.lookups(ContextBuilder.buildFetcher((AbsoluteLocation location) -> {
				return BlockProxy.load(location.getBlockAddress(), input);
			}), null, null)
			.fixedRandom(2)
			.finish()
		;
		
		boolean didTakeAction = CreatureLogic.didTakeSpecialActions(context
			, EntityCollection.fromMaps(Map.of(), Map.of(cow.id(), cow))
			, mutable
		);
		Assert.assertFalse(didTakeAction);
		Assert.assertNull(mutable.movementPlan);
		
		// We should detect that we are intersecting with a solid block, clear our plan, and take no action.
		EntityActionSimpleMove<MutableCreature> change = CreatureLogic.planNextAction(context, mutable, context.millisPerTick);
		Assert.assertNull(change);
		Assert.assertNull(mutable.movementPlan);
	}

	@Test
	public void partialUnload()
	{
		// Show that we correctly handle the case where we have a plan to follow but a cuboid the entity stradles has unloaded.
		CreatureIdAssigner assigner = new CreatureIdAssigner();
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		_setLayer(input, (byte)0, "op.stone");
		
		EntityLocation startLocation = new EntityLocation(30.9f, 0.0f, 1.0f);
		CreatureEntity cow = CreatureEntity.create(assigner.next(), COW, startLocation, 0L);
		MutableCreature mutable = MutableCreature.existing(cow);
		mutable.movementPlan = new CreatureEntity.MovementPlan(List.of(new AbsoluteLocation(30, 0, 1), new AbsoluteLocation(30, 1, 1))
			, CreatureEntity.NO_TARGET_ENTITY_ID
			, null
			, null
		);
		
		TickProcessingContext context = ContextBuilder.build()
			.lookups(ContextBuilder.buildFetcher((AbsoluteLocation location) -> {
				return cuboidAddress.equals(location.getCuboidAddress())
					? BlockProxy.load(location.getBlockAddress(), input)
					: null
				;
			}), null, null)
			.fixedRandom(2)
			.finish()
		;
		
		boolean didTakeAction = CreatureLogic.didTakeSpecialActions(context
			, EntityCollection.fromMaps(Map.of(), Map.of(cow.id(), cow))
			, mutable
		);
		Assert.assertFalse(didTakeAction);
		Assert.assertNull(mutable.movementPlan);
		
		// We should detect that we are intersecting with a solid block, clear our plan, and take no action.
		EntityActionSimpleMove<MutableCreature> change = CreatureLogic.planNextAction(context, mutable, context.millisPerTick);
		Assert.assertNull(change);
		Assert.assertNull(mutable.movementPlan);
	}

	@Test
	public void trackUnreachableTarget()
	{
		// Show that we handle the case where a target we are tracking becomes unreachable.
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		EntityLocation playerLocation = new EntityLocation(4.0f, 4.0f, 4.0f);
		EntityLocation orcLocation = new EntityLocation(0.0f, 0.0f, 1.0f);
		
		short blockNumber = ENV.items.getItemById("op.stone").number();
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		input.setData15(AspectRegistry.BLOCK, playerLocation.getBlockLocation().getRelative(0, 0, -1).getBlockAddress(), blockNumber);
		input.setData15(AspectRegistry.BLOCK, orcLocation.getBlockLocation().getRelative(0, 0, -1).getBlockAddress(), blockNumber);
		
		CreatureIdAssigner assigner = new CreatureIdAssigner();
		MutableEntity mutable = MutableEntity.createForTest(1);
		mutable.newLocation = playerLocation;
		Entity player = mutable.freeze();
		CreatureEntity orc = CreatureEntity.create(assigner.next(), ORC, orcLocation, 0L);
		
		TickProcessingContext.IBlockFetcher previousBlockLookUp = ContextBuilder.buildFetcher((AbsoluteLocation blockLocation) -> {
			return blockLocation.getCuboidAddress().equals(cuboidAddress)
				? BlockProxy.load(blockLocation.getBlockAddress(), input)
				: null
			;
		});
		_PairEntityIndex previousEntityLookUp = new _PairEntityIndex(player, orc);
		long millisPerTick = 100L;
		TickProcessingContext context = ContextBuilder.build()
			.millisPerTick(millisPerTick)
			.tick(CreatureLogic.MINIMUM_MILLIS_TO_ACTION / millisPerTick)
			.lookups(previousBlockLookUp, previousEntityLookUp, null)
			.fixedRandom(2)
			.finish()
		;
		MutableCreature mutableOrc = MutableCreature.existing(orc);
		mutableOrc.movementPlan = new CreatureEntity.MovementPlan(List.of(orcLocation.getBlockLocation().getRelative(1, 0, 0))
			, player.id()
			, playerLocation.getRelative(1.0f, 0.0f, 0.0f)
			, null
		);
		boolean didTakeAction = CreatureLogic.didTakeSpecialActions(context
			, EntityCollection.fromMaps(Map.of(player.id(), player), Map.of(orc.id(), orc))
			, mutableOrc
		);
		
		// They should have taken no action but dropped the tracking since the target is unreachable.
		Assert.assertFalse(didTakeAction);
		Assert.assertNull(mutableOrc.movementPlan);
	}

	@Test
	public void updateMovementPlanWithTarget()
	{
		// Show how we handle updates to a movement plan when there is a target:  Unchanged, moved, despawned.
		EntityLocation playerLocation = new EntityLocation(2.0f, 1.0f, 1.0f);
		EntityLocation movedPlayerLocation = playerLocation.getRelative(0.1f, 1.1f, 0.0f);
		EntityLocation orcLocation = new EntityLocation(0.0f, 0.0f, 1.0f);
		AbsoluteLocation orcStartLocation = orcLocation.getBlockLocation();
		
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		_setLayer(input, (byte)0, "op.stone");
		
		// Put an obstacle in the way to avoid the path being changed to direct.
		short stoneNumber = ENV.items.getItemById("op.stone").number();
		input.setData15(AspectRegistry.BLOCK, orcStartLocation.getRelative(1, 1, 0).getBlockAddress(), stoneNumber);
		
		CreatureIdAssigner assigner = new CreatureIdAssigner();
		MutableEntity mutable = MutableEntity.createForTest(1);
		mutable.newLocation = playerLocation;
		Entity player = mutable.freeze();
		mutable.newLocation = movedPlayerLocation;
		Entity movedPlayer = mutable.freeze();
		CreatureEntity orc = CreatureEntity.create(assigner.next(), ORC, orcLocation, 0L);
		
		TickProcessingContext.IBlockFetcher previousBlockLookUp = ContextBuilder.buildFetcher((AbsoluteLocation blockLocation) -> {
			return blockLocation.getCuboidAddress().equals(cuboidAddress)
				? BlockProxy.load(blockLocation.getBlockAddress(), input)
				: null
			;
		});
		
		// If the player did NOT move - change nothing.
		MutableCreature mutableOrc = MutableCreature.existing(orc);
		mutableOrc.movementPlan = new CreatureEntity.MovementPlan(List.of(orcStartLocation.getRelative(1, 0, 0)
				, orcStartLocation.getRelative(2, 0, 0)
				, orcStartLocation.getRelative(2, 1, 0)
			)
			, player.id()
			, playerLocation
			, null
		);
		TickProcessingContext context = ContextBuilder.build()
			.lookups(previousBlockLookUp, new _PairEntityIndex(player, orc), null)
			.fixedRandom(2)
			.finish()
		;
		CreatureLogic.test_updateExistingMovementPlanState(context
			, EntityCollection.fromMaps(Map.of(player.id(), player), Map.of(orc.id(), orc))
			, mutableOrc
		);
		Assert.assertEquals(3, mutableOrc.movementPlan.fullPlan().size());
		Assert.assertEquals(player.id(), mutableOrc.movementPlan.targetEntityId());
		Assert.assertEquals(playerLocation, mutableOrc.movementPlan.targetPreviousLocation());
		
		// If the player DID move - change path.
		mutableOrc = MutableCreature.existing(orc);
		mutableOrc.movementPlan = new CreatureEntity.MovementPlan(List.of(orcStartLocation.getRelative(1, 0, 0)
				, orcStartLocation.getRelative(2, 0, 0)
				, orcStartLocation.getRelative(2, 1, 0)
			)
			, player.id()
			, playerLocation
			, null
		);
		context = ContextBuilder.build()
			.lookups(previousBlockLookUp, new _PairEntityIndex(movedPlayer, orc), null)
			.fixedRandom(2)
			.finish()
		;
		CreatureLogic.test_updateExistingMovementPlanState(context
			, EntityCollection.fromMaps(Map.of(player.id(), movedPlayer), Map.of(orc.id(), orc))
			, mutableOrc
		);
		Assert.assertEquals(4, mutableOrc.movementPlan.fullPlan().size());
		Assert.assertEquals(player.id(), mutableOrc.movementPlan.targetEntityId());
		Assert.assertEquals(movedPlayerLocation, mutableOrc.movementPlan.targetPreviousLocation());
		
		// If the player DID disappear - clear path.
		mutableOrc = MutableCreature.existing(orc);
		mutableOrc.movementPlan = new CreatureEntity.MovementPlan(List.of(orcStartLocation.getRelative(1, 0, 0)
				, orcStartLocation.getRelative(2, 0, 0)
				, orcStartLocation.getRelative(2, 1, 0)
			)
			, player.id()
			, playerLocation
			, null
		);
		context = ContextBuilder.build()
			.lookups(previousBlockLookUp, new _PairEntityIndex(null, orc), null)
			.fixedRandom(2)
			.finish()
		;
		CreatureLogic.test_updateExistingMovementPlanState(context
			, EntityCollection.fromMaps(Map.of(), Map.of(orc.id(), orc))
			, mutableOrc
		);
		Assert.assertNull(mutableOrc.movementPlan);
	}

	@Test
	public void specialActionDirectTargets()
	{
		// Show how requesting a special action changes the movement plans:
		// -no change
		// -update existing plan:
		//  -target moved
		//  -enter next step (no direct)
		//  -enter next step (direct)
		// -create new plan
		EntityLocation playerLocation = new EntityLocation(3.0f, 1.0f, 1.0f);
		EntityLocation movedPlayerLocation = new EntityLocation(3.0f, 2.0f, 1.0f);
		EntityLocation elevatedPlayerLocation = playerLocation.getRelative(0.0f, 0.0f, 1.0f);
		EntityLocation orcLocation = new EntityLocation(0.0f, 0.0f, 1.0f);
		AbsoluteLocation orcStartLocation = orcLocation.getBlockLocation();
		
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		_setLayer(input, (byte)0, "op.stone");
		
		MutableEntity mutable = MutableEntity.createForTest(1);
		mutable.newLocation = playerLocation;
		Entity player = mutable.freeze();
		mutable.newLocation = movedPlayerLocation;
		Entity movedPlayer = mutable.freeze();
		mutable.newLocation = elevatedPlayerLocation;
		Entity elevatedPlayer = mutable.freeze();
		CreatureEntity orc = CreatureEntity.create(-1, ORC, orcLocation, 0L);
		
		TickProcessingContext.IBlockFetcher previousBlockLookUp = ContextBuilder.buildFetcher((AbsoluteLocation blockLocation) -> {
			return blockLocation.getCuboidAddress().equals(cuboidAddress)
				? BlockProxy.load(blockLocation.getBlockAddress(), input)
				: null
			;
		});
		
		// Create the movement plan for the orc to move toward the player but show that they take no special action when at distance.
		MutableCreature mutableOrc = MutableCreature.existing(orc);
		mutableOrc.movementPlan = new CreatureEntity.MovementPlan(List.of(orcStartLocation.getRelative(1, 0, 0)
				, orcStartLocation.getRelative(2, 0, 0)
				, orcStartLocation.getRelative(2, 1, 0)
				, orcStartLocation.getRelative(3, 1, 0)
			)
			, player.id()
			, playerLocation
			, null
		);
		TickProcessingContext context = ContextBuilder.build()
			.lookups(previousBlockLookUp, new _PairEntityIndex(player, orc), null)
			.finish()
		;
		boolean didAct = CreatureLogic.didTakeSpecialActions(context, EntityCollection.fromMaps(Map.of(player.id(), player), Map.of(orc.id(), orc)), mutableOrc);
		Assert.assertFalse(didAct);
		Assert.assertEquals(4, mutableOrc.movementPlan.fullPlan().size());
		Assert.assertNull(mutableOrc.movementPlan.directLocation());
		
		// Show that the target moved.
		mutableOrc = MutableCreature.existing(orc);
		mutableOrc.movementPlan = new CreatureEntity.MovementPlan(List.of(orcStartLocation.getRelative(1, 0, 0)
				, orcStartLocation.getRelative(2, 0, 0)
				, orcStartLocation.getRelative(2, 1, 0)
				, orcStartLocation.getRelative(3, 1, 0)
			)
			, player.id()
			, playerLocation
			, null
		);
		context = ContextBuilder.build()
			.lookups(previousBlockLookUp, new _PairEntityIndex(movedPlayer, orc), null)
			.finish()
		;
		didAct = CreatureLogic.didTakeSpecialActions(context, EntityCollection.fromMaps(Map.of(movedPlayer.id(), player), Map.of(orc.id(), orc)), mutableOrc);
		Assert.assertFalse(didAct);
		Assert.assertNull(mutableOrc.movementPlan.fullPlan());
		Assert.assertEquals(movedPlayerLocation, mutableOrc.movementPlan.directLocation());
		
		// Show that we entered the next step (no direct).
		mutableOrc = MutableCreature.existing(orc);
		mutableOrc.newLocation = new EntityLocation(1.0f, 0.0f, 1.0f);
		mutableOrc.movementPlan = new CreatureEntity.MovementPlan(List.of(orcStartLocation.getRelative(1, 0, 0)
				, orcStartLocation.getRelative(1, 0, 1)
				, orcStartLocation.getRelative(2, 0, 1)
				, orcStartLocation.getRelative(2, 1, 1)
				, orcStartLocation.getRelative(3, 1, 1)
			)
			, elevatedPlayer.id()
			, elevatedPlayerLocation
			, null
		);
		context = ContextBuilder.build()
			.lookups(previousBlockLookUp, new _PairEntityIndex(elevatedPlayer, orc), null)
			.finish()
		;
		didAct = CreatureLogic.didTakeSpecialActions(context, EntityCollection.fromMaps(Map.of(elevatedPlayer.id(), elevatedPlayer), Map.of(orc.id(), orc)), mutableOrc);
		Assert.assertFalse(didAct);
		Assert.assertEquals(4, mutableOrc.movementPlan.fullPlan().size());
		Assert.assertNull(mutableOrc.movementPlan.directLocation());
		
		// Show that we entered the next step (direct).
		mutableOrc = MutableCreature.existing(orc);
		mutableOrc.newLocation = new EntityLocation(1.0f, 0.0f, 1.0f);
		mutableOrc.movementPlan = new CreatureEntity.MovementPlan(List.of(orcStartLocation.getRelative(1, 0, 0)
				, orcStartLocation.getRelative(2, 0, 0)
				, orcStartLocation.getRelative(2, 1, 0)
				, orcStartLocation.getRelative(3, 1, 0)
			)
			, player.id()
			, playerLocation
			, null
		);
		context = ContextBuilder.build()
			.lookups(previousBlockLookUp, new _PairEntityIndex(player, orc), null)
			.finish()
		;
		didAct = CreatureLogic.didTakeSpecialActions(context, EntityCollection.fromMaps(Map.of(player.id(), player), Map.of(orc.id(), orc)), mutableOrc);
		Assert.assertFalse(didAct);
		Assert.assertNull(mutableOrc.movementPlan.fullPlan());
		Assert.assertEquals(playerLocation, mutableOrc.movementPlan.directLocation());
		
		// Create a new plan from scratch.
		mutableOrc = MutableCreature.existing(orc);
		mutableOrc.movementPlan = null;
		mutableOrc.shouldTakeActionInTick = true;
		context = ContextBuilder.build()
			.lookups(previousBlockLookUp, new _PairEntityIndex(player, orc), null)
			.finish()
		;
		didAct = CreatureLogic.didTakeSpecialActions(context, EntityCollection.fromMaps(Map.of(player.id(), player), Map.of(orc.id(), orc)), mutableOrc);
		Assert.assertFalse(didAct);
		Assert.assertNull(mutableOrc.movementPlan.fullPlan());
		Assert.assertEquals(playerLocation, mutableOrc.movementPlan.directLocation());
	}

	@Test
	public void handleDirectChangesZLevel()
	{
		// Handle the case where we are in direct mode but have fallen from the same z-level since last planning.
		EntityLocation playerLocation = new EntityLocation(3.0f, 1.0f, 2.6f);
		EntityLocation idleLocation = new EntityLocation(3.0f, 1.0f, 2.0f);
		EntityLocation orcLocation = new EntityLocation(0.0f, 0.0f, 1.9f);
		
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		_setLayer(input, (byte)0, "op.stone");
		
		MutableEntity mutable = MutableEntity.createForTest(1);
		mutable.newLocation = playerLocation;
		Entity player = mutable.freeze();
		
		TickProcessingContext.IBlockFetcher previousBlockLookUp = ContextBuilder.buildFetcher((AbsoluteLocation blockLocation) -> {
			return blockLocation.getCuboidAddress().equals(cuboidAddress)
				? BlockProxy.load(blockLocation.getBlockAddress(), input)
				: null
			;
		});
		
		// Try this for the case where there is a target.
		MutableCreature mutableOrc = MutableCreature.existing(CreatureEntity.create(-1, ORC, orcLocation, 0L));
		mutableOrc.movementPlan = new CreatureEntity.MovementPlan(null
			, player.id()
			, playerLocation
			, playerLocation
		);
		CreatureEntity orc = mutableOrc.freeze();
		TickProcessingContext context = ContextBuilder.build()
			.lookups(previousBlockLookUp, new _PairEntityIndex(player, orc), null)
			.finish()
		;
		boolean didAct = CreatureLogic.didTakeSpecialActions(context, EntityCollection.fromMaps(Map.of(player.id(), player), Map.of(orc.id(), orc)), mutableOrc);
		Assert.assertFalse(didAct);
		Assert.assertNull(mutableOrc.movementPlan.fullPlan());
		Assert.assertEquals(player.id(), mutableOrc.movementPlan.targetEntityId());
		Assert.assertEquals(playerLocation, mutableOrc.movementPlan.targetPreviousLocation());
		Assert.assertEquals(new EntityLocation(3.0f, 1.0f, 1.9f), mutableOrc.movementPlan.directLocation());
		
		EntityActionSimpleMove<MutableCreature> action = CreatureLogic.planNextAction(context
			, mutableOrc
			, 50L
		);
		Assert.assertNotNull(action);
		
		// Try the case where we are idly moving (no target).
		mutableOrc = MutableCreature.existing(CreatureEntity.create(-1, ORC, orcLocation, 0L));
		mutableOrc.movementPlan = new CreatureEntity.MovementPlan(null
			, CreatureEntity.NO_TARGET_ENTITY_ID
			, null
			, idleLocation
		);
		mutableOrc.nextMovementPlanMillis = CreatureLogic.MINIMUM_MILLIS_TO_ACTION;
		orc = mutableOrc.freeze();
		context = ContextBuilder.build()
			.lookups(previousBlockLookUp, new _PairEntityIndex(player, orc), null)
			.finish()
		;
		didAct = CreatureLogic.didTakeSpecialActions(context, EntityCollection.fromMaps(Map.of(player.id(), player), Map.of(orc.id(), orc)), mutableOrc);
		Assert.assertFalse(didAct);
		Assert.assertNull(mutableOrc.movementPlan);
		
		action = CreatureLogic.planNextAction(context
			, mutableOrc
			, 50L
		);
		Assert.assertNull(action);
	}

	@Test
	public void villagersIdle()
	{
		// Show that the villager types will ignore nearby players and just make idle movements.
		EntityLocation playerLocation = new EntityLocation(5.0f, 5.0f, 1.0f);
		EntityLocation villagerLocation = playerLocation.getRelative(2.0f, 0.0f, 0.0f);
		EntityLocation villagerBabyLocation = playerLocation.getRelative(0.0f, 2.0f, 0.0f);
		
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		_setLayer(input, (byte)0, "op.stone");
		
		MutableEntity mutable = MutableEntity.createForTest(1);
		mutable.newLocation = playerLocation;
		Entity player = mutable.freeze();
		
		CreatureEntity villager = CreatureEntity.create(-1, VILLAGER, villagerLocation, 0L);
		CreatureEntity villagerBaby = CreatureEntity.create(-2, VILLAGER_BABY, villagerBabyLocation, 0L);
		
		TickProcessingContext.IBlockFetcher previousBlockLookUp = ContextBuilder.buildFetcher((AbsoluteLocation blockLocation) -> {
			return blockLocation.getCuboidAddress().equals(cuboidAddress)
				? BlockProxy.load(blockLocation.getBlockAddress(), input)
				: null
			;
		});
		_MinimalEntityIndex previousEntityLookUp = new _MinimalEntityIndex(Map.of(player.id(), MinimalEntity.fromEntity(player)
			, villager.id(), MinimalEntity.fromCreature(villager)
			, villagerBaby.id(), MinimalEntity.fromCreature(villagerBaby)
		));
		TickProcessingContext context = ContextBuilder.build()
			.tick(100L)
			.lookups(previousBlockLookUp, previousEntityLookUp, null)
			.fixedRandom(1)
			.finish()
		;
		EntityCollection entityCollection = EntityCollection.fromMaps(Map.of(player.id(), player), Map.of(villager.id(), villager, villagerBaby.id(), villagerBaby));
		
		// Note that the villager will choose its profession on the first didTakeSpecialActions call.
		MutableCreature mutableVillager = MutableCreature.existing(villager);
		boolean didAct = CreatureLogic.didTakeSpecialActions(context, entityCollection, mutableVillager);
		Assert.assertTrue(didAct);
		ExtensionVillager.Data internal = (ExtensionVillager.Data) mutableVillager.newExtendedData;
		Assert.assertEquals(ENV.trading.getProfessionById("op.baker"), internal.profession());
		Assert.assertEquals(0, internal.inventory().size());
		
		// Show basic idle movement of the villager.
		didAct = CreatureLogic.didTakeSpecialActions(context, entityCollection, mutableVillager);
		Assert.assertFalse(didAct);
		Assert.assertNull(mutableVillager.movementPlan.fullPlan());
		Assert.assertEquals(CreatureEntity.NO_TARGET_ENTITY_ID, mutableVillager.movementPlan.targetEntityId());
		Assert.assertNull(mutableVillager.movementPlan.targetPreviousLocation());
		Assert.assertEquals(new EntityLocation(7.0f, 8.0f, 1.0f), mutableVillager.movementPlan.directLocation());
		
		EntityActionSimpleMove<MutableCreature> action = CreatureLogic.planNextAction(context
			, mutableVillager
			, 50L
		);
		Assert.assertNotNull(action);
		
		// Show basic idle movement of the villager baby.
		MutableCreature mutableVillagerBaby = MutableCreature.existing(villagerBaby);
		didAct = CreatureLogic.didTakeSpecialActions(context, entityCollection, mutableVillagerBaby);
		Assert.assertFalse(didAct);
		Assert.assertNull(mutableVillagerBaby.movementPlan.fullPlan());
		Assert.assertEquals(CreatureEntity.NO_TARGET_ENTITY_ID, mutableVillagerBaby.movementPlan.targetEntityId());
		Assert.assertNull(mutableVillagerBaby.movementPlan.targetPreviousLocation());
		Assert.assertEquals(new EntityLocation(7.0f, 8.0f, 1.0f), mutableVillagerBaby.movementPlan.directLocation());
		
		action = CreatureLogic.planNextAction(context
			, mutableVillagerBaby
			, 50L
		);
		Assert.assertNotNull(action);
	}

	@Test
	public void changeTargetAfterFeeding()
	{
		// Show that a cow changes target after being fed.
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		CreatureIdAssigner assigner = new CreatureIdAssigner();
		MutableEntity mutableEntity = MutableEntity.createWithLocation(1, new EntityLocation(3.0f, 0.0f, 0.0f), new EntityLocation(0.0f, 0.0f, 0.0f));
		mutableEntity.newInventory.addAllItems(WHEAT, 2);
		mutableEntity.slotManager.setSelectedKey(1);
		Entity player = mutableEntity.freeze();
		MutableCreature mutable = MutableCreature.existing(CreatureEntity.create(assigner.next(), COW, new EntityLocation(4.0f, 0.0f, 0.0f), 0L));
		mutable.newExtendedData = new ExtensionLivestock.LivestockData(new CommonBreedingLogic.Data(false, null, 0L));
		mutable.movementPlan = new CreatureEntity.MovementPlan(List.of(new AbsoluteLocation(3, 0, 0))
			, player.id()
			, player.location()
			, player.location()
		);
		CreatureEntity cow0 = mutable.freeze();
		mutable = MutableCreature.existing(CreatureEntity.create(assigner.next(), COW, new EntityLocation(8.0f, 0.0f, 0.0f), 0L));
		mutable.newExtendedData = new ExtensionLivestock.LivestockData(new CommonBreedingLogic.Data(true, null, 0L));
		CreatureEntity cow1 = mutable.freeze();
		
		// Feed cow0 and we should see it clear its plan.
		mutable = MutableCreature.existing(cow0);
		Assert.assertTrue(CreatureLogic.applyItemToCreature(WHEAT, mutable, 0L));
		Assert.assertEquals(null, mutable.movementPlan);
		cow0 = mutable.freeze();
		
		// Create a context at the next time where we can take an action and see that they pick no target if they can't see the other cow..
		TickProcessingContext.IBlockFetcher previousBlockLookUp = ContextBuilder.buildFetcher((AbsoluteLocation blockLocation) -> {
			return blockLocation.getCuboidAddress().equals(cuboidAddress)
				? BlockProxy.load(blockLocation.getBlockAddress(), input)
				: null
			;
		});
		TickProcessingContext context = ContextBuilder.build()
			.tick(10L)
			.lookups(previousBlockLookUp, null, null)
			.fixedRandom(2)
			.finish()
		;
		mutable = MutableCreature.existing(cow0);
		Assert.assertFalse(CreatureLogic.didTakeSpecialActions(context
			, EntityCollection.fromMaps(Map.of(player.id(), player), Map.of(cow0.id(), cow0))
			, mutable
		));
		Assert.assertEquals(null, mutable.movementPlan);
		
		// Try again, but this time with the other cow visible.
		context = ContextBuilder.build()
			.tick(10L)
			.lookups(previousBlockLookUp, null, null)
			.fixedRandom(2)
			.finish()
		;
		mutable = MutableCreature.existing(cow0);
		Assert.assertFalse(CreatureLogic.didTakeSpecialActions(context
			, EntityCollection.fromMaps(Map.of(player.id(), player), Map.of(cow0.id(), cow0, cow1.id(), cow1))
			, mutable
		));
		Assert.assertEquals(cow1.id(), mutable.movementPlan.targetEntityId());
	}


	private static TickProcessingContext _createContext(Function<AbsoluteLocation, BlockProxy> function, int random)
	{
		TickProcessingContext.IBlockFetcher previousBlockLookUp = ContextBuilder.buildFetcher(function);
		TickProcessingContext context = ContextBuilder.build()
				.lookups(previousBlockLookUp, null, null)
				.fixedRandom(random)
				.finish()
		;
		return context;
	}

	private void _setLayer(CuboidData input, byte z, String name)
	{
		Block block = ENV.blocks.fromItem(ENV.items.getItemById(name));
		CuboidGenerator.fillPlane(input, z, block);
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
				, original.ephemeral().nextMovementPlanMillis()
				, gameMillis
				, original.ephemeral().nextActionMillis()
				, original.ephemeral().nextTakeDamageMillis()
			)
		);
	}

	private static class _MinimalEntityIndex implements TickProcessingContext.IEntitySearch
	{
		private final Map<Integer, MinimalEntity> _minimal;
		public _MinimalEntityIndex(Map<Integer, MinimalEntity> minimal)
		{
			_minimal = minimal;
		}
		@Override
		public MinimalEntity getById(int id)
		{
			return _minimal.get(id);
		}
		@Override
		public int[] findEntityIdsInRegion(EntityLocation base, EntityLocation edge)
		{
			throw new AssertionError("Not used in test");
		}
	}

	private static class _PairEntityIndex implements TickProcessingContext.IEntitySearch
	{
		private final Entity _player;
		private final CreatureEntity _creature;
		public _PairEntityIndex(Entity player, CreatureEntity creature)
		{
			_player = player;
			_creature = creature;
		}
		@Override
		public MinimalEntity getById(int id)
		{
			MinimalEntity min;
			if ((null != _player) && (id == _player.id()))
			{
				min = MinimalEntity.fromEntity(_player);
			}
			else if (id == _creature.id())
			{
				min = MinimalEntity.fromCreature(_creature);
			}
			else
			{
				throw new AssertionError();
			}
			return min;
		}
		@Override
		public int[] findEntityIdsInRegion(EntityLocation base, EntityLocation edge)
		{
			throw new AssertionError("Not used in test");
		}
	}
}
