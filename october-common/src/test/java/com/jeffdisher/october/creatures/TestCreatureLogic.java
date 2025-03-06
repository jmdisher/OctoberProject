package com.jeffdisher.october.creatures;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.logic.CreatureIdAssigner;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.mutations.EntityChangeImpregnateCreature;
import com.jeffdisher.october.mutations.EntityChangeMove;
import com.jeffdisher.october.mutations.EntityChangeTakeDamageFromEntity;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.mutations.TickUtils;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.ContextBuilder;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.IMutableCreatureEntity;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.MinimalEntity;
import com.jeffdisher.october.types.MutableCreature;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;
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
		CreatureEntity entity = CreatureEntity.create(-1, ORC, entityLocation, (byte)100);
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
		CreatureEntity entity = CreatureEntity.create(-1, ORC, entityLocation, (byte)100);
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
		CreatureEntity entity = CreatureEntity.create(-1, ORC, entityLocation, (byte)100);
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
				}, null)
				.fixedRandom(0)
				.finish()
		;
		MutableCreature mutable = MutableCreature.existing(entity);
		mutable.newBreath -= 1;
		boolean didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, new EntityCollection(Map.of(), Map.of(entity.id(), entity))
				, null
				, mutable
		);
		Assert.assertFalse(didTakeAction);
		IMutationEntity<IMutableCreatureEntity> action = CreatureLogic.planNextAction(context
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
		CreatureEntity entity = CreatureEntity.create(-1, ORC, entityLocation, (byte)100);
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
		CreatureEntity orc = CreatureEntity.create(-1, ORC, orcLocation, (byte)100);
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
				, null
				, (byte)0
				, (byte)0
				, MiscConstants.MAX_BREATH
				, 0
				, MutableEntity.TESTING_LOCATION
				, Entity.EMPTY_DATA
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
				.lookups(previousBlockLookUp, previousEntityLookUp)
				.finish()
		;
		MutableCreature mutableOrc = MutableCreature.existing(orc);
		
		// First, choose the target.
		boolean didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, new EntityCollection(Map.of(player[0].id(), player[0]), Map.of(orc.id(), orc))
				, null
				, mutableOrc
		);
		Assert.assertFalse(didTakeAction);
		IMutationEntity<IMutableCreatureEntity> action = CreatureLogic.planNextAction(context
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
				, null
				, (byte)0
				, (byte)0
				, MiscConstants.MAX_BREATH
				, 0
				, MutableEntity.TESTING_LOCATION
				, Entity.EMPTY_DATA
		);
		// Special action is where we account for this targeting update but it doesn't count as a special action.
		didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, new EntityCollection(Map.of(player[0].id(), player[0]), Map.of(orc.id(), orc))
				, null
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
		CreatureEntity orc = CreatureEntity.create(assigner.next(), ORC, new EntityLocation(0.0f, 0.0f, 0.0f), (byte)100).updateKeepAliveTick(startTick);
		CreatureEntity cow = CreatureEntity.create(assigner.next(), COW, new EntityLocation(0.0f, 0.0f, 0.0f), (byte)100).updateKeepAliveTick(startTick);
		
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
				.lookups(previousBlockLookUp, previousEntityLookUp)
				.finish()
		;
		MutableCreature mutableCow = MutableCreature.existing(cow);
		MutableCreature mutableOrc = MutableCreature.existing(orc);
		boolean didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, new EntityCollection(Map.of(), Map.of(cow.id(), cow, orc.id(), orc))
				, null
				, mutableCow
		);
		Assert.assertFalse(didTakeAction);
		Assert.assertEquals((byte)100, mutableCow.newHealth);
		didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, new EntityCollection(Map.of(), Map.of(cow.id(), cow, orc.id(), orc))
				, null
				, mutableOrc
		);
		Assert.assertFalse(didTakeAction);
		Assert.assertEquals((byte)100, mutableOrc.newHealth);
		
		// Now, advance time and do the same, seeing the despawn of the orc but not the cow.
		context = ContextBuilder.build()
				.tick(startTick + (CreatureLogic.MILLIS_UNTIL_NO_ACTION_DESPAWN / context.millisPerTick))
				.lookups(previousBlockLookUp, previousEntityLookUp)
				.finish()
		;
		didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, new EntityCollection(Map.of(), Map.of(cow.id(), cow, orc.id(), orc))
				, null
				, mutableCow
		);
		Assert.assertFalse(didTakeAction);
		Assert.assertEquals((byte)100, mutableCow.newHealth);
		didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, new EntityCollection(Map.of(), Map.of(cow.id(), cow, orc.id(), orc))
				, null
				, mutableOrc
		);
		Assert.assertTrue(didTakeAction);
		Assert.assertEquals((byte)0,mutableOrc.newHealth);
	}

	@Test
	public void enterLoveMode()
	{
		CreatureIdAssigner assigner = new CreatureIdAssigner();
		CreatureEntity cow = CreatureEntity.create(assigner.next(), COW, new EntityLocation(0.0f, 0.0f, 0.0f), (byte)100);
		MutableCreature mutable = MutableCreature.existing(cow);
		CreatureLogic.applyItemToCreature(WHEAT, mutable);
		Assert.assertTrue(mutable.newInLoveMode);
	}

	@Test
	public void sendImpregnate()
	{
		CreatureIdAssigner assigner = new CreatureIdAssigner();
		EntityLocation fatherLocation = new EntityLocation(0.8f, 0.0f, 0.0f);
		CreatureEntity father = CreatureEntity.create(assigner.next(), COW, fatherLocation, (byte)100);
		EntityLocation motherLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		CreatureEntity mother = CreatureEntity.create(assigner.next(), COW, motherLocation, (byte)100);
		// Start with them both in a love mode.
		MutableCreature mutable = MutableCreature.existing(father);
		mutable.newTargetEntityId = mother.id();
		mutable.newInLoveMode = true;
		father = mutable.freeze();
		mutable = MutableCreature.existing(mother);
		mutable.newTargetEntityId = father.id();
		mutable.newInLoveMode = true;
		mother = mutable.freeze();
		Map<Integer, CreatureEntity> creatures = new HashMap<>();
		creatures.put(father.id(), father);
		creatures.put(mother.id(), mother);
		
		int[] targetId = new int[1];
		IMutationEntity<?>[] message = new IMutationEntity<?>[1];
		TickProcessingContext context = ContextBuilder.build()
				.sinks(null, new TickProcessingContext.IChangeSink() {
					@Override
					public void next(int targetEntityId, IMutationEntity<IMutablePlayerEntity> change)
					{
						Assert.fail();
					}
					@Override
					public void future(int targetEntityId, IMutationEntity<IMutablePlayerEntity> change, long millisToDelay)
					{
						Assert.fail();
					}
					@Override
					public void creature(int targetCreatureId, IMutationEntity<IMutableCreatureEntity> change)
					{
						Assert.assertEquals(0, targetId[0]);
						Assert.assertNull(message[0]);
						targetId[0] = targetCreatureId;
						message[0] = change;
					}})
				.lookups(null, (Integer entityId) -> {
					return creatures.containsKey(entityId)
							? MinimalEntity.fromCreature(creatures.get(entityId))
							: null
					;
				})
				.finish()
		;
		
		// We should see the father sending a message
		mutable = MutableCreature.existing(father);
		boolean didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, new EntityCollection(Map.of(), creatures)
				, null
				, mutable
		);
		Assert.assertTrue(didTakeAction);
		Assert.assertEquals(mother.id(), targetId[0]);
		targetId[0] = 0;
		Assert.assertTrue(message[0] instanceof EntityChangeImpregnateCreature);
		message[0] = null;
		father = mutable.freeze();
		creatures.put(father.id(), father);
		
		// The mother should not take any action since they are waiting for the father.
		mutable = MutableCreature.existing(mother);
		didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, new EntityCollection(Map.of(), creatures)
				, null
				, mutable
		);
		Assert.assertFalse(didTakeAction);
		Assert.assertNull(message[0]);
		mother = mutable.freeze();
		creatures.put(mother.id(), mother);
		
		// The father should no longer be in love mode but the mother should be.
		Assert.assertFalse(father.ephemeral().inLoveMode());
		Assert.assertTrue(mother.ephemeral().inLoveMode());
	}

	@Test
	public void becomePregnant()
	{
		CreatureIdAssigner assigner = new CreatureIdAssigner();
		EntityLocation fatherLocation = new EntityLocation(0.8f, 0.0f, 0.0f);
		EntityLocation motherLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		CreatureEntity mother = CreatureEntity.create(assigner.next(), COW, motherLocation, (byte)100);
		MutableCreature mutable = MutableCreature.existing(mother);
		mutable.newInLoveMode = true;
		
		boolean didBecomePregnant = CreatureLogic.setCreaturePregnant(mutable, fatherLocation);
		Assert.assertTrue(didBecomePregnant);
		Assert.assertFalse(mutable.newInLoveMode);
		Assert.assertEquals(new EntityLocation(0.4f, 0.0f, 0.0f), mutable.newOffspringLocation);
	}

	@Test
	public void spawnOffspring()
	{
		CreatureIdAssigner assigner = new CreatureIdAssigner();
		EntityLocation offspringLocation = new EntityLocation(0.4f, 0.0f, 0.0f);
		EntityLocation motherLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		CreatureEntity mother = CreatureEntity.create(assigner.next(), COW, motherLocation, (byte)100);
		MutableCreature mutable = MutableCreature.existing(mother);
		mutable.newOffspringLocation = offspringLocation;
		
		TickProcessingContext context = ContextBuilder.build()
				.assigner(assigner)
				.finish()
		;
		CreatureEntity[] offspring = new CreatureEntity[1];
		Consumer<CreatureEntity> creatureSpawner = (CreatureEntity spawn) -> {
			Assert.assertNull(offspring[0]);
			offspring[0] = spawn;
		};
		boolean didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, new EntityCollection(Map.of(), Map.of(mother.id(), mother))
				, creatureSpawner
				, mutable
		);
		Assert.assertTrue(didTakeAction);
		Assert.assertNull(mutable.newOffspringLocation);
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
		CreatureEntity orc = CreatureEntity.create(assigner.next(), ORC, orcLocation, (byte)100);
		
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
				.lookups(previousBlockLookUp, previousEntityLookUp)
				.finish()
		;
		MutableCreature mutableOrc = MutableCreature.existing(orc);
		boolean didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, new EntityCollection(Map.of(player.id(), player), Map.of(orc.id(), orc))
				, null
				, mutableOrc
		);
		Assert.assertFalse(didTakeAction);
		IMutationEntity<IMutableCreatureEntity> action = CreatureLogic.planNextAction(context
				, mutableOrc
				, 100L
		);
		Assert.assertTrue(action instanceof EntityChangeMove<IMutableCreatureEntity>);
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
		EntityLocation location = new EntityLocation(1.1f, 0.0f, 1.0f);
		MutableEntity mutable = MutableEntity.createForTest(1);
		mutable.newLocation = location;
		Entity player = mutable.freeze();
		EntityLocation orcLocation = new EntityLocation(0.0f, 0.0f, 1.0f);
		CreatureEntity orc = CreatureEntity.create(assigner.next(), ORC, orcLocation, (byte)100);
		
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
		int[] ref_targetEntityId = new int[1];
		@SuppressWarnings("unchecked")
		IMutationEntity<IMutablePlayerEntity>[] ref_change = new IMutationEntity[1];
		TickProcessingContext.IChangeSink changeSink = new TickProcessingContext.IChangeSink() {
			@Override
			public void next(int targetEntityId, IMutationEntity<IMutablePlayerEntity> change)
			{
				Assert.assertEquals(CreatureEntity.NO_TARGET_ENTITY_ID, ref_targetEntityId[0]);
				ref_targetEntityId[0] = targetEntityId;
				Assert.assertNull(ref_change[0]);
				ref_change[0] = change;
			}
			@Override
			public void future(int targetEntityId, IMutationEntity<IMutablePlayerEntity> change, long millisToDelay)
			{
				Assert.fail();
			}
			@Override
			public void creature(int targetCreatureId, IMutationEntity<IMutableCreatureEntity> change)
			{
				Assert.fail();
			}
		};
		long millisPerTick = 100L;
		TickProcessingContext context = ContextBuilder.build()
				.millisPerTick(millisPerTick)
				.tick(CreatureLogic.MINIMUM_MILLIS_TO_ACTION / millisPerTick)
				.sinks(null, changeSink)
				.lookups(previousBlockLookUp, previousEntityLookUp)
				.finish()
		;
		
		// Start with the orc targeting the player.
		MutableCreature mutableOrc = MutableCreature.existing(orc);
		mutableOrc.newTargetEntityId = player.id();
		boolean didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, new EntityCollection(Map.of(player.id(), player), Map.of(orc.id(), orc))
				, null
				, mutableOrc
		);
		Assert.assertFalse(didTakeAction);
		IMutationEntity<IMutableCreatureEntity> action = CreatureLogic.planNextAction(context
				, mutableOrc
				, 100L
		);
		// We will try to walk toward them still.
		Assert.assertTrue(action instanceof EntityChangeMove<IMutableCreatureEntity>);
		Assert.assertEquals(player.id(), mutableOrc.newTargetEntityId);
		Assert.assertEquals(player.location().getBlockLocation(), mutableOrc.newTargetPreviousLocation);
		
		// Update the player to be close enough.
		Assert.assertTrue(action.applyChange(context, mutableOrc));
		TickUtils.allowMovement(context.previousBlockLookUp, null, mutableOrc, action.getTimeCostMillis());
		
		// Now, allow it to perform the attack.
		didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, new EntityCollection(Map.of(player.id(), player), Map.of(orc.id(), orc))
				, null
				, mutableOrc
		);
		Assert.assertTrue(didTakeAction);
		Assert.assertEquals(player.id(), mutableOrc.newTargetEntityId);
		Assert.assertEquals(player.id(), ref_targetEntityId[0]);
		// We should see the orc send the attack message
		Assert.assertTrue(ref_change[0] instanceof EntityChangeTakeDamageFromEntity);
		ref_targetEntityId[0] = CreatureEntity.NO_TARGET_ENTITY_ID;
		ref_change[0] = null;
		
		// A second attack on the following tick should fail since we are on cooldown.
		didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, new EntityCollection(Map.of(player.id(), player), Map.of(orc.id(), orc))
				, null
				, mutableOrc
		);
		Assert.assertFalse(didTakeAction);
		Assert.assertEquals(player.id(), mutableOrc.newTargetEntityId);
		Assert.assertEquals(CreatureEntity.NO_TARGET_ENTITY_ID, ref_targetEntityId[0]);
		Assert.assertNull(ref_change[0]);
		
		// But will work if we advance tick number further.
		context = ContextBuilder.build()
				.tick(context.currentTick + CreatureLogic.MILLIS_ATTACK_COOLDOWN / context.millisPerTick)
				.sinks(null, changeSink)
				.lookups(previousBlockLookUp, previousEntityLookUp)
				.finish()
		;
		didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, new EntityCollection(Map.of(player.id(), player), Map.of(orc.id(), orc))
				, null
				, mutableOrc
		);
		Assert.assertTrue(didTakeAction);
		Assert.assertEquals(player.id(), mutableOrc.newTargetEntityId);
		Assert.assertEquals(player.id(), ref_targetEntityId[0]);
		Assert.assertTrue(ref_change[0] instanceof EntityChangeTakeDamageFromEntity);
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
		CreatureEntity cow = CreatureEntity.create(assigner.next(), COW, cowLocation, (byte)100);
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
				.lookups(previousBlockLookUp, previousEntityLookUp)
				.finish()
		;
		
		// Acquire target.
		entities.put(player.id(), MinimalEntity.fromEntity(player));
		entities.put(cow.id(), MinimalEntity.fromCreature(cow));
		boolean didTakeAction = CreatureLogic.didTakeSpecialActions(context
				, new EntityCollection(Map.of(player.id(), player), Map.of(cow.id(), cow))
				, null
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
				, new EntityCollection(Map.of(player.id(), player), Map.of(cow.id(), cow))
				, null
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
				, new EntityCollection(Map.of(player.id(), player), Map.of(cow.id(), cow))
				, null
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
				, new EntityCollection(Map.of(player.id(), player), Map.of(cow.id(), cow))
				, null
				, mutableCow
		);
		Assert.assertFalse(didTakeAction);
		Assert.assertEquals(CreatureEntity.NO_TARGET_ENTITY_ID, mutableCow.newTargetEntityId);
		Assert.assertNull(mutableCow.newTargetPreviousLocation);
		Assert.assertNull(mutableCow.newMovementPlan);
	}


	private static TickProcessingContext _createContext(Function<AbsoluteLocation, BlockProxy> previousBlockLookUp, int random)
	{
		TickProcessingContext context = ContextBuilder.build()
				.lookups(previousBlockLookUp, null)
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
}
