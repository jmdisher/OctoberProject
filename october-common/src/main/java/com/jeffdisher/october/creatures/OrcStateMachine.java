package com.jeffdisher.october.creatures;

import java.util.function.Consumer;

import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.mutations.EntityChangeTakeDamageFromEntity;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.MinimalEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * The extended state data in CreatureEntity is immutable but the interactions with it form a pretty straight-forward
 * state machine so this object exists as a mutable projection of that state with a high-level interface.
 * It will re-serialize itself to the original instance if unchanged.
 * Ideally, we will eventually find a way to encode this in some declarative data structure, but that is a ways off, if
 * even possible (may not be sufficiently expressive).
 */
public class OrcStateMachine implements ICreatureStateMachine
{
	// We will only allow one attack per second.
	public static final long ATTACK_COOLDOWN_MILLIS = 1000L;


	private final float _viewDistance;
	private final float _attackDistance;
	private final byte _attackDamage;

	/**
	 * Creates a mutable state machine for a orc..
	 * 
	 * @param type The type being acted upon.
	 */
	public OrcStateMachine(EntityType type)
	{
		_viewDistance = type.viewDistance();
		_attackDistance = type.actionDistance();
		_attackDamage = type.attackDamage();
	}

	@Override
	public boolean applyItem(Item itemType)
	{
		// This shouldn't be called.
		throw Assert.unreachable();
	}

	@Override
	public ICreatureStateMachine.TargetEntity selectTarget(TickProcessingContext context, EntityCollection entityCollection, EntityLocation creatureLocation, EntityType thisType, int thisCreatureId)
	{
		return _findPlayerInRange(entityCollection, creatureLocation);
	}

	@Override
	public boolean setPregnant(EntityLocation offspringLocation)
	{
		// This shouldn't be called.
		throw Assert.unreachable();
	}

	@Override
	public boolean doneSpecialActions(TickProcessingContext context, Consumer<CreatureEntity> creatureSpawner, EntityLocation creatureLocation, EntityType thisType, int thisCreatureId, int targetEntityId, long lastAttackTick)
	{
		// The only special action we will take is attacking but this path will also reset our tracking if the target moves.
		boolean didTakeAction = false;
		// We don't have an objective measurement of time but the tick rate is considered constant within a server instance so we will estimate time passed.
		long millisSinceLastAttack = (context.currentTick - lastAttackTick) * context.millisPerTick;
		if ((CreatureEntity.NO_TARGET_ENTITY_ID != targetEntityId) && (millisSinceLastAttack >= ATTACK_COOLDOWN_MILLIS))
		{
			// We are tracking a target so see if they have moved (since we would need to clear our existing targets and
			// movement plans unless they are close enough for other actions).
			MinimalEntity targetEntity = context.previousEntityLookUp.apply(targetEntityId);
			// If we got here, they must not have unloaded (we would have observed that in didUpdateTargetLocation.
			Assert.assertTrue(null != targetEntity);
			
			// See if they are in attack range.
			EntityLocation targetLocation = targetEntity.location();
			float distance = SpatialHelpers.distanceBetween(creatureLocation, targetLocation);
			if (distance <= _attackDistance)
			{
				// We can attack them so choose the target.
				int index = context.randomInt.applyAsInt(BodyPart.values().length);
				BodyPart target = BodyPart.values()[index];
				EntityChangeTakeDamageFromEntity<IMutablePlayerEntity> takeDamage = new EntityChangeTakeDamageFromEntity<>(target, _attackDamage, thisCreatureId);
				context.newChangeSink.next(targetEntityId, takeDamage);
				// We only count a successful attack as an "action".
				didTakeAction = true;
			}
		}
		return didTakeAction;
	}

	@Override
	public Object freezeToData()
	{
		return null;
	}


	private ICreatureStateMachine.TargetEntity _findPlayerInRange(EntityCollection entityCollection, EntityLocation creatureLocation)
	{
		ICreatureStateMachine.TargetEntity[] target = new ICreatureStateMachine.TargetEntity[1];
		float[] distanceToTarget = new float[] { Float.MAX_VALUE };
		entityCollection.walkPlayersInRange(creatureLocation, _viewDistance, (Entity player) -> {
			EntityLocation end = player.location();
			float distance = SpatialHelpers.distanceBetween(creatureLocation, end);
			if (distance < distanceToTarget[0])
			{
				target[0] = new ICreatureStateMachine.TargetEntity(player.id(), end);
				distanceToTarget[0] = distance;
			}
		});
		return target[0];
	}
}
