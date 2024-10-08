package com.jeffdisher.october.creatures;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.CreatureIdAssigner;
import com.jeffdisher.october.mutations.EntityChangeImpregnateCreature;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.ContextBuilder;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.IMutableCreatureEntity;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.MinimalEntity;
import com.jeffdisher.october.types.TickProcessingContext;


public class TestCowStateMachine
{
	private static Environment ENV;
	private static Item WHEAT;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		WHEAT = ENV.items.getItemById("op.wheat_item");
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void enterLoveMode()
	{
		CowStateMachine machine = CowStateMachine.extractFromData(null);
		machine.applyItem(WHEAT);
		Object extendedData = machine.freezeToData();
		CowStateMachine.Test_ExtendedData result = CowStateMachine.decodeExtendedData(extendedData);
		Assert.assertTrue(result.inLoveMode());
	}

	@Test
	public void sendImpregnate()
	{
		CreatureIdAssigner assigner = new CreatureIdAssigner();
		EntityLocation fatherLocation = new EntityLocation(0.8f, 0.0f, 0.0f);
		CreatureEntity father = CreatureEntity.create(assigner.next(), EntityType.COW, fatherLocation, (byte)100);
		EntityLocation motherLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		CreatureEntity mother = CreatureEntity.create(assigner.next(), EntityType.COW, motherLocation, (byte)100);
		
		int[] targetId = new int[1];
		IMutationEntity<?>[] message = new IMutationEntity<?>[1];
		BiConsumer<Integer, IMutationEntity<IMutableCreatureEntity>> messageAcceptor = (Integer id, IMutationEntity<IMutableCreatureEntity> change) -> {
			Assert.assertEquals(0, targetId[0]);
			Assert.assertNull(message[0]);
			targetId[0] = id;
			message[0] = change;
		};
		TickProcessingContext context = _createContext(Map.of(father.id(), father, mother.id(), mother), messageAcceptor, assigner);
		
		// Start with them both in a love mode.
		CowStateMachine fatherMachine = CowStateMachine.extractFromData(CowStateMachine.encodeExtendedData(new CowStateMachine.Test_ExtendedData(true, null, mother.id(), mother.location().getBlockLocation(), null, 0L, 0L)));
		boolean didTakeAction = fatherMachine.doneSpecialActions(context, null, null, father.location(), father.id());
		Assert.assertTrue(didTakeAction);
		// We should see the father sending a message
		Assert.assertEquals(mother.id(), targetId[0]);
		targetId[0] = 0;
		Assert.assertTrue(message[0] instanceof EntityChangeImpregnateCreature);
		message[0] = null;
		
		CowStateMachine motherMachine = CowStateMachine.extractFromData(CowStateMachine.encodeExtendedData(new CowStateMachine.Test_ExtendedData(true, null, father.id(), father.location().getBlockLocation(), null, 0L, 0L)));
		// The mother should not take any action since they are waiting for the father.
		didTakeAction = motherMachine.doneSpecialActions(context, null, null, mother.location(), mother.id());
		Assert.assertFalse(didTakeAction);
		// The mother should be unchanged.
		Assert.assertNull(message[0]);
		
		// The father should no longer be in love mode but the mother should be.
		CowStateMachine.Test_ExtendedData fatherResult = CowStateMachine.decodeExtendedData(fatherMachine.freezeToData());
		Assert.assertNull(fatherResult);
		CowStateMachine.Test_ExtendedData motherResult = CowStateMachine.decodeExtendedData(motherMachine.freezeToData());
		Assert.assertTrue(motherResult.inLoveMode());
		Assert.assertEquals(father.id(), motherResult.targetEntityId());
	}

	@Test
	public void becomePregnant()
	{
		Object extendedData = CowStateMachine.encodeExtendedData(new CowStateMachine.Test_ExtendedData(true, null, 0, null, null, 0L, 0L));
		CowStateMachine machine = CowStateMachine.extractFromData(extendedData);
		EntityLocation location = new EntityLocation(0.0f, 0.0f, 0.0f);
		machine.setPregnant(location);
		extendedData = machine.freezeToData();
		CowStateMachine.Test_ExtendedData result = CowStateMachine.decodeExtendedData(extendedData);
		Assert.assertFalse(result.inLoveMode());
		Assert.assertNotNull(result.offspringLocation());
	}

	@Test
	public void spawnOffspring()
	{
		EntityLocation location = new EntityLocation(0.0f, 0.0f, 0.0f);
		Object extendedData = CowStateMachine.encodeExtendedData(new CowStateMachine.Test_ExtendedData(false, null, 0, null, location, 0L, 0L));
		CowStateMachine machine = CowStateMachine.extractFromData(extendedData);
		TickProcessingContext context = _createContext(null, null, new CreatureIdAssigner());
		CreatureEntity[] offspring = new CreatureEntity[1];
		boolean didTakeAction = machine.doneSpecialActions(context, (CreatureEntity spawn) -> {
			Assert.assertNull(offspring[0]);
			offspring[0] = spawn;
		}, null, null, 0);
		Assert.assertTrue(didTakeAction);
		extendedData = machine.freezeToData();
		CowStateMachine.Test_ExtendedData result = CowStateMachine.decodeExtendedData(extendedData);
		Assert.assertNull(result);
		Assert.assertEquals(location, offspring[0].location());
	}

	@Test
	public void followMovement()
	{
		CreatureIdAssigner assigner = new CreatureIdAssigner();
		EntityLocation fatherLocation = new EntityLocation(4.0f, 0.0f, 0.0f);
		CreatureEntity father = CreatureEntity.create(assigner.next(), EntityType.COW, fatherLocation, (byte)100);
		EntityLocation motherLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		CreatureEntity mother = CreatureEntity.create(assigner.next(), EntityType.COW, motherLocation, (byte)100);
		
		TickProcessingContext context = _createContext(Map.of(father.id(), father, mother.id(), mother), null, assigner);
		
		// Start with them both in a love mode and give the father a non-null path and with the target of the mother (a "previous" location).
		CowStateMachine fatherMachine = CowStateMachine.extractFromData(CowStateMachine.encodeExtendedData(new CowStateMachine.Test_ExtendedData(true, List.of(), mother.id(), new AbsoluteLocation(3, 0, 0), null, 0L, 0L)));
		boolean didTakeAction = fatherMachine.doneSpecialActions(context, null, null, father.location(), father.id());
		Assert.assertTrue(didTakeAction);
		
		// We should see that the father has lost the target and path (they will need to re-find it on a future selection).
		Assert.assertNull(fatherMachine.getMovementPlan());
		CowStateMachine.Test_ExtendedData testData = CowStateMachine.decodeExtendedData(fatherMachine.freezeToData());
		Assert.assertTrue(testData.inLoveMode());
		Assert.assertEquals(0, testData.targetEntityId());
		Assert.assertNull(testData.targetPreviousLocation());
		Assert.assertNull(testData.movementPlan());
	}


	private static TickProcessingContext _createContext(Map<Integer, CreatureEntity> entities
			, BiConsumer<Integer, IMutationEntity<IMutableCreatureEntity>> messageAcceptor
			, CreatureIdAssigner assigner
	)
	{
		Function<Integer, MinimalEntity> previousEntityLookUp = (Integer id) -> {
			CreatureEntity entity = entities.get(id);
			return (null != entity)
					? MinimalEntity.fromCreature(entity)
					: null
			;
		};
		TickProcessingContext.IChangeSink changes = new TickProcessingContext.IChangeSink() {
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
				messageAcceptor.accept(targetCreatureId, change);
			}
		};
		TickProcessingContext context = ContextBuilder.build()
				.lookups(null, previousEntityLookUp)
				.sinks(null, changes)
				.assigner(assigner)
				.finish()
		;
		return context;
	}
}
