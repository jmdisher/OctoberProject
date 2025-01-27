package com.jeffdisher.october.creatures;

import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.CreatureIdAssigner;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.mutations.EntityChangeTakeDamageFromEntity;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.types.ContextBuilder;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.IMutableCreatureEntity;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.MinimalEntity;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;


public class TestOrcStateMachine
{
	private static Environment ENV;
	private static EntityType ORC;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		ORC = ENV.creatures.getTypeById("op.orc");
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void startTarget()
	{
		CreatureIdAssigner assigner = new CreatureIdAssigner();
		EntityLocation location = new EntityLocation(4.0f, 0.0f, 0.0f);
		MutableEntity mutable = MutableEntity.createForTest(1);
		mutable.newLocation = location;
		Entity player = mutable.freeze();
		EntityLocation orcLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		CreatureEntity orc = CreatureEntity.create(assigner.next(), ORC, orcLocation, (byte)100);
		
		OrcStateMachine machine = new OrcStateMachine(ORC, null);
		TickProcessingContext context = _createContext(Map.of(orc.id(), orc), Map.of(player.id(), player), null, assigner);
		ICreatureStateMachine.TargetEntity target = machine.selectTarget(context, new EntityCollection(Set.of(player), Set.of(orc)), orc.location(), orc.type(), orc.id());
		
		Assert.assertEquals(player.id(), target.id());
		Assert.assertEquals(player.location(), target.location());
		// We haven't yet attacked so there will be no extended data.
		OrcStateMachine.Test_ExtendedData testData = OrcStateMachine.decodeExtendedData(machine.freezeToData());
		Assert.assertNull(testData);
	}

	@Test
	public void sendAttack()
	{
		CreatureIdAssigner assigner = new CreatureIdAssigner();
		EntityLocation location = new EntityLocation(1.0f, 0.0f, 0.0f);
		MutableEntity mutable = MutableEntity.createForTest(1);
		mutable.newLocation = location;
		Entity player = mutable.freeze();
		EntityLocation orcLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		CreatureEntity orc = CreatureEntity.create(assigner.next(), ORC, orcLocation, (byte)100);
		
		int[] targetId = new int[1];
		IMutationEntity<?>[] message = new IMutationEntity<?>[1];
		BiConsumer<Integer, IMutationEntity<IMutablePlayerEntity>> messageAcceptor = (Integer id, IMutationEntity<IMutablePlayerEntity> change) -> {
			Assert.assertEquals(0, targetId[0]);
			Assert.assertNull(message[0]);
			targetId[0] = id;
			message[0] = change;
		};
		TickProcessingContext context = _createContext(Map.of(orc.id(), orc), Map.of(player.id(), player), messageAcceptor, assigner);
		
		// Start with the orc targeting the player.
		OrcStateMachine machine = new OrcStateMachine(ORC, OrcStateMachine.encodeExtendedData(new OrcStateMachine.Test_ExtendedData(0L)));
		ICreatureStateMachine.TargetEntity target = machine.selectTarget(context, new EntityCollection(Set.of(player), Set.of(orc)), orc.location(), orc.type(), orc.id());
		Assert.assertEquals(player.id(), target.id());
		Assert.assertEquals(player.location(), target.location());
		boolean didTakeAction = machine.doneSpecialActions(context, null, orc.location(), orc.type(), orc.id(), player.id());
		Assert.assertTrue(didTakeAction);
		
		// We should see the orc send the attack message
		Assert.assertEquals(player.id(), targetId[0]);
		targetId[0] = 0;
		Assert.assertTrue(message[0] instanceof EntityChangeTakeDamageFromEntity);
		message[0] = null;
		
		// The orc should still target them.
		OrcStateMachine.Test_ExtendedData result = OrcStateMachine.decodeExtendedData(machine.freezeToData());
		Assert.assertEquals(context.currentTick, result.lastAttackTick());
		
		// A second attack on the following tick should fail since we are on cooldown.
		Assert.assertFalse(machine.doneSpecialActions(_advanceTick(context, 1L), null, orc.location(), orc.type(), orc.id(), player.id()));
		Assert.assertEquals(context.currentTick, result.lastAttackTick());
		
		// But will work if we advance tick number further.
		long ticksToAdvance = OrcStateMachine.ATTACK_COOLDOWN_MILLIS / context.millisPerTick;
		context = _advanceTick(context, ticksToAdvance);
		didTakeAction = machine.doneSpecialActions(context, null, orc.location(), orc.type(), orc.id(), player.id());
		Assert.assertTrue(didTakeAction);
		Assert.assertEquals(player.id(), targetId[0]);
		targetId[0] = 0;
		Assert.assertTrue(message[0] instanceof EntityChangeTakeDamageFromEntity);
		message[0] = null;
		result = OrcStateMachine.decodeExtendedData(machine.freezeToData());
		Assert.assertEquals(context.currentTick, result.lastAttackTick());
	}


	private static TickProcessingContext _createContext(Map<Integer, CreatureEntity> creatures
			, Map<Integer, Entity> players
			, BiConsumer<Integer, IMutationEntity<IMutablePlayerEntity>> nextAcceptor
			, CreatureIdAssigner assigner
	)
	{
		long millisPerTick = 100L;
		long tickNumber = OrcStateMachine.ATTACK_COOLDOWN_MILLIS / millisPerTick;
		return _createContextForTick(tickNumber
				, creatures
				, players
				, nextAcceptor
				, assigner
		);
	}

	private static TickProcessingContext _createContextForTick(long tickNumber
			, Map<Integer, CreatureEntity> creatures
			, Map<Integer, Entity> players
			, BiConsumer<Integer, IMutationEntity<IMutablePlayerEntity>> nextAcceptor
			, CreatureIdAssigner assigner
	)
	{
		Function<Integer, MinimalEntity> previousEntityLookUp = (Integer id) -> {
			MinimalEntity minimal;
			if (id > 0)
			{
				Entity entity = players.get(id);
				minimal = (null != entity)
						? MinimalEntity.fromEntity(entity)
						: null
				;
			}
			else
			{
				CreatureEntity entity = creatures.get(id);
				minimal = (null != entity)
						? MinimalEntity.fromCreature(entity)
						: null
				;
			}
			return minimal;
		};
		TickProcessingContext.IChangeSink changes = new TickProcessingContext.IChangeSink() {
			@Override
			public void next(int targetEntityId, IMutationEntity<IMutablePlayerEntity> change)
			{
				nextAcceptor.accept(targetEntityId, change);
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
		TickProcessingContext context = ContextBuilder.build()
				.tick(tickNumber)
				.lookups(null, previousEntityLookUp)
				.sinks(null, changes)
				.assigner(assigner)
				.finish()
		;
		return context;
	}

	private static TickProcessingContext _advanceTick(TickProcessingContext context, long ticksToAdvance)
	{
		return ContextBuilder.nextTick(context, ticksToAdvance).finish();
	}
}
