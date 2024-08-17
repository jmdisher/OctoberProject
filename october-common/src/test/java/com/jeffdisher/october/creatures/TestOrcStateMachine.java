package com.jeffdisher.october.creatures;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.logic.CreatureIdAssigner;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.mutations.EntityChangeTakeDamage;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.IMutableCreatureEntity;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.MinimalEntity;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.types.WorldConfig;


public class TestOrcStateMachine
{
	@Test
	public void startTarget()
	{
		CreatureIdAssigner assigner = new CreatureIdAssigner();
		EntityLocation location = new EntityLocation(4.0f, 0.0f, 0.0f);
		MutableEntity mutable = MutableEntity.createForTest(1);
		mutable.newLocation = location;
		Entity player = mutable.freeze();
		EntityLocation orcLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		CreatureEntity orc = CreatureEntity.create(assigner.next(), EntityType.ORC, orcLocation, (byte)100);
		
		OrcStateMachine machine = OrcStateMachine.extractFromData(null);
		TickProcessingContext context = _createContext(Map.of(orc.id(), orc), Map.of(player.id(), player), null, assigner);
		machine.selectDeliberateTarget(context, new EntityCollection(Set.of(player), Set.of(orc)), orc.location(), orc.id());
		// Set a movement plan so we store the extended data.
		machine.setMovementPlan(List.of(orcLocation.getBlockLocation()));
		
		OrcStateMachine.Test_ExtendedData testData = OrcStateMachine.decodeExtendedData(machine.freezeToData());
		Assert.assertEquals(player.id(), testData.targetEntityId());
		Assert.assertEquals(player.location().getBlockLocation(), testData.targetPreviousLocation());
	}

	@Test
	public void dropTargetOnMovement()
	{
		CreatureIdAssigner assigner = new CreatureIdAssigner();
		EntityLocation location = new EntityLocation(4.0f, 0.0f, 0.0f);
		MutableEntity mutable = MutableEntity.createForTest(1);
		mutable.newLocation = location;
		Entity player = mutable.freeze();
		EntityLocation orcLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		CreatureEntity orc = CreatureEntity.create(assigner.next(), EntityType.ORC, orcLocation, (byte)100);
		
		TickProcessingContext context = _createContext(Map.of(orc.id(), orc), Map.of(player.id(), player), null, assigner);
		
		// See that the orc targets the entity.
		AbsoluteLocation previousLocation = new AbsoluteLocation(5, 1, 0);
		OrcStateMachine machine = OrcStateMachine.extractFromData(OrcStateMachine.encodeExtendedData(new OrcStateMachine.Test_ExtendedData(List.of(), player.id(), previousLocation, 0L, 0L, 0L)));
		boolean didTakeAction = machine.doneSpecialActions(context, null, orc.location(), orc.id());
		// (they are still out of range so we didn't hit them)
		Assert.assertFalse(didTakeAction);
		
		// We should see that the father has lost the target and path (they will need to re-find it on a future selection).
		Assert.assertNull(machine.getMovementPlan());
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
		CreatureEntity orc = CreatureEntity.create(assigner.next(), EntityType.ORC, orcLocation, (byte)100);
		
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
		OrcStateMachine machine = OrcStateMachine.extractFromData(OrcStateMachine.encodeExtendedData(new OrcStateMachine.Test_ExtendedData(List.of(), player.id(), player.location().getBlockLocation(), 0L, 0L, 0L)));
		boolean didTakeAction = machine.doneSpecialActions(context, null, orc.location(), orc.id());
		Assert.assertTrue(didTakeAction);
		
		// We should see the orc send the attack message
		Assert.assertEquals(player.id(), targetId[0]);
		targetId[0] = 0;
		Assert.assertTrue(message[0] instanceof EntityChangeTakeDamage);
		message[0] = null;
		
		// The orc should still target them.
		OrcStateMachine.Test_ExtendedData result = OrcStateMachine.decodeExtendedData(machine.freezeToData());
		Assert.assertEquals(player.id(), result.targetEntityId());
		Assert.assertEquals(player.location().getBlockLocation(), result.targetPreviousLocation());
		Assert.assertEquals(context.currentTick, result.lastAttackTick());
		
		// A second attack on the following tick should fail since we are on cooldown.
		Assert.assertFalse(machine.doneSpecialActions(_advanceTick(context, 1L), null, orc.location(), orc.id()));
		Assert.assertEquals(context.currentTick, result.lastAttackTick());
		
		// But will work if we advance tick number further.
		long ticksToAdvance = OrcStateMachine.ATTACK_COOLDOWN_MILLIS / context.millisPerTick;
		context = _advanceTick(context, ticksToAdvance);
		didTakeAction = machine.doneSpecialActions(context, null, orc.location(), orc.id());
		Assert.assertTrue(didTakeAction);
		Assert.assertEquals(player.id(), targetId[0]);
		targetId[0] = 0;
		Assert.assertTrue(message[0] instanceof EntityChangeTakeDamage);
		message[0] = null;
		result = OrcStateMachine.decodeExtendedData(machine.freezeToData());
		Assert.assertEquals(player.id(), result.targetEntityId());
		Assert.assertEquals(player.location().getBlockLocation(), result.targetPreviousLocation());
		Assert.assertEquals(context.currentTick, result.lastAttackTick());
	}


	private static TickProcessingContext _createContext(Map<Integer, CreatureEntity> creatures
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
		long millisPerTick = 100L;
		long tickNumber = OrcStateMachine.ATTACK_COOLDOWN_MILLIS / millisPerTick;
		Random random = new Random();
		TickProcessingContext context = new TickProcessingContext(tickNumber
				, null
				, previousEntityLookUp
				, null
				, changes
				, assigner
				, (int bound) -> random.nextInt(bound)
				, new WorldConfig()
				, millisPerTick
		);
		return context;
	}

	private static TickProcessingContext _advanceTick(TickProcessingContext context, long ticksToAdvance)
	{
		return new TickProcessingContext(context.currentTick + ticksToAdvance
				, context.previousBlockLookUp
				, context.previousEntityLookUp
				, context.mutationSink
				, context.newChangeSink
				, context.idAssigner
				, context.randomInt
				, context.config
				, context.millisPerTick
		);
	}
}
