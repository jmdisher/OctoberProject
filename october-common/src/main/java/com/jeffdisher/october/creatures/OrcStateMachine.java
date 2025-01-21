package com.jeffdisher.october.creatures;

import java.util.function.Consumer;

import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.mutations.EntityChangeTakeDamageFromEntity;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.IMutablePlayerEntity;
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
	public static final float ORC_VIEW_DISTANCE = 8.0f;
	public static final float ORC_ATTACK_DISTANCE = 1.0f;
	public static final byte ORC_DAMAGE = 5;
	// Use 2x the view distance to account for obstacles.
	public static final int ORC_PATH_DISTANCE = 2 * (int) ORC_VIEW_DISTANCE;
	public static final int NO_TARGET_ENTITY_ID = 0;
	// We will only allow one attack per second.
	public static final long ATTACK_COOLDOWN_MILLIS = 1000L;
	/**
	 * The amount of time will orc will continue to live if not taking any deliberate action before despawn (5 minutes).
	 */
	public static final long MILLIS_UNTIL_NO_ACTION_DESPAWN = 5L * 60L * 1_000L;

	/**
	 * Creates a mutable state machine for a orc based on the given extendedData opaque type (could be null).
	 * 
	 * @param extendedData The orc extended data (previously created by this class).
	 * @return The mutable state machine.
	 */
	public static OrcStateMachine extractFromData(Object extendedData)
	{
		// This MUST be an orc.
		_ExtendedData data = (_ExtendedData) extendedData;
		return new OrcStateMachine(data);
	}

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
				? new _ExtendedData(testing.targetEntityId
						, testing.targetPreviousLocation
						, testing.lastAttackTick
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
				? new Test_ExtendedData(extended.targetEntityId
						, extended.targetPreviousLocation
						, extended.lastAttackTick
						, extended.idleDespawnTick
				)
				: null
		;
	}


	private final _ExtendedData _originalData;
	private int _targetEntityId;
	private AbsoluteLocation _targetPreviousLocation;
	private long _lastAttackTick;
	private long _idleDespawnTick;
	
	private OrcStateMachine(_ExtendedData data)
	{
		_originalData = data;
		if (null != data)
		{
			_targetEntityId = data.targetEntityId;
			_targetPreviousLocation = data.targetPreviousLocation;
			_lastAttackTick = data.lastAttackTick;
			_idleDespawnTick = data.idleDespawnTick;
		}
		else
		{
			_targetEntityId = NO_TARGET_ENTITY_ID;
			_targetPreviousLocation = null;
			_lastAttackTick = 0L;
			// We will initialize this when making the first deliberate action.
			_idleDespawnTick = Long.MAX_VALUE;
		}
	}

	@Override
	public EntityLocation selectDeliberateTarget(TickProcessingContext context, EntityCollection entityCollection, EntityLocation creatureLocation, int creatureId)
	{
		EntityLocation targetLocation = null;
		// Orcs only have a single target:  Any player in range.
		_Target target = _findPlayerInRange(entityCollection, creatureLocation);
		// We store the entity we are targeting (will default to 0 if nothing) so we know who to contact when we get close enough.
		if (null != target)
		{
			_targetEntityId = target.id;
			_targetPreviousLocation = target.location.getBlockLocation();
			targetLocation = target.location;
		}
		else
		{
			_targetEntityId = NO_TARGET_ENTITY_ID;
			_targetPreviousLocation = null;
			targetLocation = null;
		}
		if ((null != target) || (Long.MAX_VALUE == _idleDespawnTick))
		{
			// We made a deliberate action or just started so delay our idle despawn.
			_idleDespawnTick = context.currentTick + (MILLIS_UNTIL_NO_ACTION_DESPAWN / context.millisPerTick);
		}
		return targetLocation;
	}

	@Override
	public EntityLocation didUpdateTargetLocation(TickProcessingContext context, EntityLocation creatureLocation)
	{
		// If we are tracking another entity, see if we can update our target location.
		EntityLocation updatedLocation = null;
		if (NO_TARGET_ENTITY_ID != _targetEntityId)
		{
			// See if they are still loaded.
			MinimalEntity targetEntity = context.previousEntityLookUp.apply(_targetEntityId);
			if (null != targetEntity)
			{
				// Make sure that they are still in our site range.
				EntityLocation targetLocation = targetEntity.location();
				float distance = SpatialHelpers.distanceBetween(creatureLocation, targetLocation);
				if (distance <= ORC_PATH_DISTANCE)
				{
					// We can keep this but see if we need to update their location.
					AbsoluteLocation newLocation = targetLocation.getBlockLocation();
					if (!newLocation.equals(_targetPreviousLocation))
					{
						_targetPreviousLocation = newLocation;
						updatedLocation = targetLocation;
					}
				}
				else
				{
					// They are out of range so forget them.
					_clearPlans();
				}
			}
			else
			{
				// The unloaded, so clear.
				_clearPlans();
			}
		}
		return updatedLocation;
	}

	@Override
	public boolean doneSpecialActions(TickProcessingContext context, Consumer<CreatureEntity> creatureSpawner, Runnable requestDespawnWithoutDrops, EntityLocation creatureLocation, int creatureId)
	{
		// The only special action we will take is attacking but this path will also reset our tracking if the target moves.
		boolean didTakeAction = false;
		// We don't have an objective measurement of time but the tick rate is considered constant within a server instance so we will estimate time passed.
		long millisSinceLastAttack = (context.currentTick - _lastAttackTick) * context.millisPerTick;
		if ((NO_TARGET_ENTITY_ID != _targetEntityId) && (millisSinceLastAttack >= ATTACK_COOLDOWN_MILLIS))
		{
			// We are tracking a target so see if they have moved (since we would need to clear our existing targets and
			// movement plans unless they are close enough for other actions).
			MinimalEntity targetEntity = context.previousEntityLookUp.apply(_targetEntityId);
			// If we got here, they must not have unloaded (we would have observed that in didUpdateTargetLocation.
			Assert.assertTrue(null != targetEntity);
			
			// See if they are in attack range.
			EntityLocation targetLocation = targetEntity.location();
			float distance = SpatialHelpers.distanceBetween(creatureLocation, targetLocation);
			if (distance <= ORC_ATTACK_DISTANCE)
			{
				// We can attack them so choose the target.
				int index = context.randomInt.applyAsInt(BodyPart.values().length);
				BodyPart target = BodyPart.values()[index];
				EntityChangeTakeDamageFromEntity<IMutablePlayerEntity> takeDamage = new EntityChangeTakeDamageFromEntity<>(target, ORC_DAMAGE, creatureId);
				context.newChangeSink.next(_targetEntityId, takeDamage);
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
		return ORC_PATH_DISTANCE;
	}

	@Override
	public boolean isPlanDeliberate()
	{
		return (NO_TARGET_ENTITY_ID != _targetEntityId);
	}

	@Override
	public Object freezeToData()
	{
		_ExtendedData newData = ((NO_TARGET_ENTITY_ID != _targetEntityId) || (Long.MAX_VALUE != _idleDespawnTick))
				? new _ExtendedData(_targetEntityId, _targetPreviousLocation, _lastAttackTick, _idleDespawnTick)
				: null
		;
		_ExtendedData matchingData = (null != _originalData)
				? (_originalData.equals(newData) ? _originalData : newData)
				: newData
		;
		return matchingData;
	}


	private void _clearPlans()
	{
		_targetEntityId = NO_TARGET_ENTITY_ID;
		_targetPreviousLocation = null;
	}

	private _Target _findPlayerInRange(EntityCollection entityCollection, EntityLocation creatureLocation)
	{
		_Target[] target = new _Target[1];
		float[] distanceToTarget = new float[] { Float.MAX_VALUE };
		entityCollection.walkPlayersInRange(creatureLocation, ORC_VIEW_DISTANCE, (Entity player) -> {
			EntityLocation end = player.location();
			float distance = SpatialHelpers.distanceBetween(creatureLocation, end);
			if (distance < distanceToTarget[0])
			{
				target[0] = new _Target(player.id(), end);
				distanceToTarget[0] = distance;
			}
		});
		return target[0];
	}


	/**
	 * This is a testing variant of _ExtendedData which only exists to make unit tests simpler.
	 */
	public static record Test_ExtendedData(int targetEntityId
			, AbsoluteLocation targetPreviousLocation
			, long lastAttackTick
			, long idleDespawnTick
	)
	{}

	private static record _ExtendedData(int targetEntityId
			, AbsoluteLocation targetPreviousLocation
			, long lastAttackTick
			, long idleDespawnTick
	)
	{}

	private static record _Target(int id
			, EntityLocation location
	)
	{}
}
