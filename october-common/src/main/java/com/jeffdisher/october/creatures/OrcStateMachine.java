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
	/**
	 * The amount of time will orc will continue to live if not taking any deliberate action before despawn (5 minutes).
	 */
	public static final long MILLIS_UNTIL_NO_ACTION_DESPAWN = 5L * 60L * 1_000L;

	/**
	 * TESTING ONLY!
	 * Packages the given testing data into the extended data object for an orc.
	 * 
	 * @param testing The testing data.
	 * @return The packaged extended object.
	 */
	public static Object encodeExtendedData(Test_ExtendedData testing)
	{
		return (null != testing)
				? new _ExtendedData(testing.lastAttackTick
						, testing.idleDespawnTick
				)
				: null
		;
	}

	/**
	 * TESTING ONLY!
	 * Unpackages the testing data from the extended data object for an orc.
	 * 
	 * @param creature The creature to read.
	 * @return The testing data, potentially null.
	 */
	public static Test_ExtendedData decodeExtendedData(Object data)
	{
		_ExtendedData extended = (_ExtendedData) data;
		return (null != extended)
				? new Test_ExtendedData(extended.lastAttackTick
						, extended.idleDespawnTick
				)
				: null
		;
	}


	private final float _viewDistance;
	private final float _attackDistance;
	private final byte _attackDamage;
	private final int _pathDistance;
	private final _ExtendedData _originalData;
	private long _lastAttackTick;
	private long _idleDespawnTick;

	/**
	 * Creates a mutable state machine for a orc based on the given extendedData opaque type (could be null).
	 * 
	 * @param type The type being acted upon.
	 * @param extendedData The orc extended data (previously created by this class).
	 */
	public OrcStateMachine(EntityType type, Object extendedData)
	{
		_ExtendedData data = (_ExtendedData) extendedData;
		_viewDistance = type.viewDistance();
		_attackDistance = type.actionDistance();
		_attackDamage = type.attackDamage();
		// Use 2x the view distance to account for obstacles.
		_pathDistance = 2 * (int)_viewDistance;
		_originalData = data;
		if (null != data)
		{
			_lastAttackTick = data.lastAttackTick;
			_idleDespawnTick = data.idleDespawnTick;
		}
		else
		{
			_lastAttackTick = 0L;
			// We will initialize this when making the first deliberate action.
			_idleDespawnTick = Long.MAX_VALUE;
		}
	}

	@Override
	public boolean applyItem(Item itemType)
	{
		// This shouldn't be called.
		throw Assert.unreachable();
	}

	@Override
	public ICreatureStateMachine.TargetEntity selectTarget(TickProcessingContext context, EntityCollection entityCollection, EntityLocation creatureLocation, int creatureId)
	{
		ICreatureStateMachine.TargetEntity target = _findPlayerInRange(entityCollection, creatureLocation);
		if ((null != target) || (Long.MAX_VALUE == _idleDespawnTick))
		{
			// We made a deliberate action or just started so delay our idle despawn.
			_idleDespawnTick = context.currentTick + (MILLIS_UNTIL_NO_ACTION_DESPAWN / context.millisPerTick);
		}
		return target;
	}

	@Override
	public boolean setPregnant(EntityLocation offspringLocation)
	{
		// This shouldn't be called.
		throw Assert.unreachable();
	}

	@Override
	public boolean doneSpecialActions(TickProcessingContext context, Consumer<CreatureEntity> creatureSpawner, Runnable requestDespawnWithoutDrops, EntityLocation creatureLocation, int creatureId, int targetEntityId)
	{
		// The only special action we will take is attacking but this path will also reset our tracking if the target moves.
		boolean didTakeAction = false;
		// We don't have an objective measurement of time but the tick rate is considered constant within a server instance so we will estimate time passed.
		long millisSinceLastAttack = (context.currentTick - _lastAttackTick) * context.millisPerTick;
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
				EntityChangeTakeDamageFromEntity<IMutablePlayerEntity> takeDamage = new EntityChangeTakeDamageFromEntity<>(target, _attackDamage, creatureId);
				context.newChangeSink.next(targetEntityId, takeDamage);
				// Set us on to cooldown.
				_lastAttackTick = context.currentTick;
				// We only count a successful attack as an "action".
				didTakeAction = true;
			}
		}
		if (!didTakeAction && (context.currentTick >= _idleDespawnTick))
		{
			// We aren't doing anything so despawn without drops.
			requestDespawnWithoutDrops.run();
		}
		return didTakeAction;
	}

	@Override
	public int getPathDistance()
	{
		return _pathDistance;
	}

	@Override
	public Object freezeToData()
	{
		_ExtendedData newData = (Long.MAX_VALUE != _idleDespawnTick)
				? new _ExtendedData(_lastAttackTick, _idleDespawnTick)
				: null
		;
		_ExtendedData matchingData = (null != _originalData)
				? (_originalData.equals(newData) ? _originalData : newData)
				: newData
		;
		return matchingData;
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


	/**
	 * This is a testing variant of _ExtendedData which only exists to make unit tests simpler.
	 */
	public static record Test_ExtendedData(long lastAttackTick
			, long idleDespawnTick
	)
	{}

	private static record _ExtendedData(long lastAttackTick
			, long idleDespawnTick
	)
	{}
}
