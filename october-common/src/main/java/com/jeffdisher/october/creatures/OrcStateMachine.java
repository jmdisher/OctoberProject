package com.jeffdisher.october.creatures;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.mutations.EntityChangeTakeDamage;
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
	public static final byte ORC_DEFAULT_HEALTH = 50;
	public static final float ORC_ATTACK_DISTANCE = 1.0f;
	public static final byte ORC_DAMAGE = 5;
	// Use 2x the view distance to account for obstacles.
	public static final int ORC_PATH_DISTANCE = 2 * (int) ORC_VIEW_DISTANCE;
	public static final int NO_TARGET_ENTITY_ID = 0;
	// We will only allow one attack per second.
	public static final long ATTACK_COOLDOWN_MILLIS = 1000L;
	/**
	 * The minimum number of millis we can wait from our last action until we decide to make a deliberate plan.
	 */
	public static final long MINIMUM_MILLIS_TO_DELIBERATE_ACTION = 1_000L;
	/**
	 * The minimum number of millis we can wait from our last action until we decide to make an idling plan, assuming
	 * there was no good deliberate option.
	 */
	public static final long MINIMUM_MILLIS_TO_IDLE_ACTION = 30_000L;

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
				? new _ExtendedData(testing.movementPlan
						, testing.targetEntityId
						, testing.targetPreviousLocation
						, testing.lastAttackTick
						, testing.nextDeliberateActTick
						, testing.nextIdleActTick
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
				? new Test_ExtendedData(extended.movementPlan
						, extended.targetEntityId
						, extended.targetPreviousLocation
						, extended.lastAttackTick
						, extended.nextDeliberateActTick
						, extended.nextIdleActTick
				)
				: null
		;
	}


	private final _ExtendedData _originalData;
	private List<AbsoluteLocation> _movementPlan;
	private int _targetEntityId;
	private AbsoluteLocation _targetPreviousLocation;
	private long _lastAttackTick;
	private long _nextDeliberateActTick;
	private long _nextIdleActTick;
	
	private OrcStateMachine(_ExtendedData data)
	{
		_originalData = data;
		if (null != data)
		{
			_movementPlan = data.movementPlan;
			_targetEntityId = data.targetEntityId;
			_targetPreviousLocation = data.targetPreviousLocation;
			_lastAttackTick = data.lastAttackTick;
			_nextDeliberateActTick = data.nextDeliberateActTick;
			_nextIdleActTick = data.nextIdleActTick;
		}
		else
		{
			_movementPlan = null;
			_targetEntityId = NO_TARGET_ENTITY_ID;
			_targetPreviousLocation = null;
			_lastAttackTick = 0L;
			_nextDeliberateActTick = 0L;
			_nextIdleActTick = 0L;
		}
	}

	@Override
	public EntityLocation selectDeliberateTarget(TickProcessingContext context, EntityCollection entityCollection, EntityLocation creatureLocation, int creatureId)
	{
		// We can only call this if we don't already have a movement plan.
		Assert.assertTrue(null == _movementPlan);
		
		EntityLocation targetLocation = null;
		if (context.currentTick >= _nextDeliberateActTick)
		{
			// Orcs only have a single target:  Any player in range.
			// We will just use arrays to pass this "by reference".
			int[] targetId = new int[] { NO_TARGET_ENTITY_ID };
			EntityLocation[] target = new EntityLocation[1];
			_findPlayerInRange(entityCollection, creatureLocation, targetId, target);
			// We store the entity we are targeting (will default to 0 if nothing) so we know who to contact when we get close enough.
			_targetEntityId = targetId[0];
			_targetPreviousLocation = (null != target[0]) ? target[0].getBlockLocation() : null;
			targetLocation = target[0];
			
			// Update our next action ticks.
			_nextDeliberateActTick = context.currentTick + (MINIMUM_MILLIS_TO_DELIBERATE_ACTION / context.millisPerTick);
			if (null != _targetPreviousLocation)
			{
				// If we found someone, we also want to delay idle actions (we should fall into idle movement if we keep failing here).
				_nextIdleActTick = context.currentTick + (MINIMUM_MILLIS_TO_IDLE_ACTION / context.millisPerTick);
			}
		}
		return targetLocation;
	}

	@Override
	public List<AbsoluteLocation> getMovementPlan()
	{
		// The caller shouldn't change this.
		return (null != _movementPlan)
				? Collections.unmodifiableList(_movementPlan)
				: null
		;
	}

	@Override
	public void setMovementPlan(List<AbsoluteLocation> movementPlan)
	{
		// This can be null but never empty.
		Assert.assertTrue((null == movementPlan) || !movementPlan.isEmpty());
		_movementPlan = (null != movementPlan)
				? new ArrayList<>(movementPlan)
				: null
		;
	}

	@Override
	public boolean doneSpecialActions(TickProcessingContext context, Consumer<CreatureEntity> creatureSpawner, EntityLocation creatureLocation, int creatureId)
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
			if (null != targetEntity)
			{
				EntityLocation targetLocation = targetEntity.location();
				
				// Should we attack them.
				float distance = SpatialHelpers.distanceBetween(creatureLocation, targetLocation);
				if (distance <= ORC_ATTACK_DISTANCE)
				{
					// We can attack them so choose the target.
					int index = context.randomInt.applyAsInt(BodyPart.values().length);
					BodyPart target = BodyPart.values()[index];
					EntityChangeTakeDamage<IMutablePlayerEntity> takeDamage = new EntityChangeTakeDamage<>(target, ORC_DAMAGE);
					context.newChangeSink.next(_targetEntityId, takeDamage);
					// Set us on to cooldown.
					_lastAttackTick = context.currentTick;
					// We only count a successful attack as an "action".
					didTakeAction = true;
				}
				else
				{
					// We can't so just see if they moved from our last plan.
					AbsoluteLocation newLocation = targetLocation.getBlockLocation();
					if (!newLocation.equals(_targetPreviousLocation))
					{
						_clearPlans();
						_nextDeliberateActTick = 0L;
					}
				}
			}
			else
			{
				_clearPlans();
			}
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
	public boolean canMakeIdleMovement(TickProcessingContext context)
	{
		boolean canMove = (context.currentTick >= _nextIdleActTick);
		if (canMove)
		{
			// We want to "consume" this decision.
			_nextIdleActTick = context.currentTick + (MINIMUM_MILLIS_TO_IDLE_ACTION / context.millisPerTick);
		}
		return canMove;
	}

	@Override
	public Object freezeToData()
	{
		_ExtendedData newData = ((null != _movementPlan) || (NO_TARGET_ENTITY_ID != _targetEntityId) || (_nextDeliberateActTick > 0L) || (_nextIdleActTick > 0L))
				? new _ExtendedData(_movementPlan, _targetEntityId, _targetPreviousLocation, _lastAttackTick, _nextDeliberateActTick, _nextIdleActTick)
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
		_movementPlan = null;
	}

	private void _findPlayerInRange(EntityCollection entityCollection, EntityLocation creatureLocation, int[] out_targetId, EntityLocation[] out_target)
	{
		float[] distanceToTarget = new float[] { Float.MAX_VALUE };
		entityCollection.walkPlayersInRange(creatureLocation, ORC_VIEW_DISTANCE, (Entity player) -> {
			EntityLocation end = player.location();
			float distance = SpatialHelpers.distanceBetween(creatureLocation, end);
			if (distance < distanceToTarget[0])
			{
				out_targetId[0] = player.id();
				out_target[0] = end;
				distanceToTarget[0] = distance;
			}
		});
	}


	/**
	 * This is a testing variant of _ExtendedData which only exists to make unit tests simpler.
	 */
	public static record Test_ExtendedData(List<AbsoluteLocation> movementPlan
			, int targetEntityId
			, AbsoluteLocation targetPreviousLocation
			, long lastAttackTick
			, long nextDeliberateActTick
			, long nextIdleActTick
	)
	{}

	private static record _ExtendedData(List<AbsoluteLocation> movementPlan
			, int targetEntityId
			, AbsoluteLocation targetPreviousLocation
			, long lastAttackTick
			, long nextDeliberateActTick
			, long nextIdleActTick
	)
	{}
}
