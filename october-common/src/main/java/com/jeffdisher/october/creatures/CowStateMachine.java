package com.jeffdisher.october.creatures;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.mutations.EntityChangeImpregnateCreature;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityConstants;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
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
public class CowStateMachine implements ICreatureStateMachine
{
	public static final String ITEM_NAME_WHEAT = "op.wheat_item";
	public static final float COW_VIEW_DISTANCE = 7.0f;
	public static final float COW_MATING_DISTANCE = 1.0f;
	// Use 2x the view distance to account for obstacles.
	public static final int COW_PATH_DISTANCE = 2 * (int) COW_VIEW_DISTANCE;
	public static final int NO_TARGET_ENTITY_ID = 0;
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
	 * Creates a mutable state machine for a cow based on the given extendedData opaque type (could be null).
	 * 
	 * @param extendedData The cow's extended data (previously created by this class).
	 * @return The mutable state machine.
	 */
	public static CowStateMachine extractFromData(Object extendedData)
	{
		// This MUST be a cow.
		_ExtendedData data = (_ExtendedData) extendedData;
		return new CowStateMachine(data);
	}

	/**
	 * A helper to determine if the given item can be used an instance of this type.
	 * 
	 * @param item The item.
	 * @return True if this item could potentially be applied to this type (may do nothing but _can_ do something).
	 */
	public static boolean canUseItem(Item item)
	{
		Environment env = Environment.getShared();
		Item wheat = env.items.getItemById(ITEM_NAME_WHEAT);
		return (wheat == item);
	}

	/**
	 * TESTING ONLY!
	 * Packages the given testing data into the extended data object for a cow.
	 * 
	 * @param testing The testing data.
	 * @return The packaged extended object.
	 */
	public static Object encodeExtendedData(Test_ExtendedData testing)
	{
		return (null != testing)
				? new _ExtendedData(testing.inLoveMode
						, testing.movementPlan
						, testing.targetEntityId
						, testing.targetPreviousLocation
						, testing.offspringLocation
						, testing.nextDeliberateActTick
						, testing.nextIdleActTick
				)
				: null
		;
	}

	/**
	 * TESTING ONLY!
	 * Unpackages the testing data from the extended data object for a cow.
	 * 
	 * @param creature The creature to read.
	 * @return The testing data, potentially null.
	 */
	public static Test_ExtendedData decodeExtendedData(Object data)
	{
		_ExtendedData extended = (_ExtendedData) data;
		return (null != extended)
				? new Test_ExtendedData(extended.inLoveMode
						, extended.movementPlan
						, extended.targetEntityId
						, extended.targetPreviousLocation
						, extended.offspringLocation
						, extended.nextDeliberateActTick
						, extended.nextIdleActTick
				)
				: null
		;
	}


	private final _ExtendedData _originalData;
	private boolean _inLoveMode;
	private List<AbsoluteLocation> _movementPlan;
	private int _targetEntityId;
	private AbsoluteLocation _targetPreviousLocation;
	private EntityLocation _offspringLocation;
	private long _nextDeliberateActTick;
	private long _nextIdleActTick;
	
	private CowStateMachine(_ExtendedData data)
	{
		_originalData = data;
		if (null != data)
		{
			_inLoveMode = data.inLoveMode;
			_movementPlan = data.movementPlan;
			_targetEntityId = data.targetEntityId;
			_targetPreviousLocation = data.targetPreviousLocation;
			_offspringLocation = data.offspringLocation;
			_nextDeliberateActTick = data.nextDeliberateActTick;
			_nextIdleActTick = data.nextIdleActTick;
		}
		else
		{
			_inLoveMode = false;
			_movementPlan = null;
			_targetEntityId = NO_TARGET_ENTITY_ID;
			_targetPreviousLocation = null;
			_offspringLocation = null;
			_nextDeliberateActTick = 0L;
			_nextIdleActTick = 0L;
		}
	}

	/**
	 * Applies the given item to the cow.  Note that this may do nothing if the item can't be applied to this creature
	 * or the creature isn't in a state where it will have any effect.
	 * 
	 * @param itemType The type of item to apply.
	 */
	public void applyItem(Item itemType)
	{
		Environment env = Environment.getShared();
		Item wheat = env.items.getItemById(ITEM_NAME_WHEAT);
		if (itemType == wheat)
		{
			// We can't enter love mode if already pregnant (although that would only remain the case for a single tick).
			if (null == _offspringLocation)
			{
				// If this is already true, the item is lost as this is a saturating operation.
				_inLoveMode = true;
				// Wipe any movement plan.
				_clearPlans();
			}
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
			// As a cow, we have 2 explicit reasons for movement:  another cow when in love mode or a player holding wheat when not.
			// We will just use arrays to pass this "by reference".
			int[] targetId = new int[] { NO_TARGET_ENTITY_ID };
			EntityLocation[] target = new EntityLocation[1];
			if (_inLoveMode)
			{
				// Find another cow in breeding mode.
				_findBreedableCow(entityCollection, creatureLocation, creatureId, targetId, target);
			}
			else
			{
				// We will keep this simple:  Find the closest player holding wheat, up to our limit.
				_findWheatTarget(entityCollection, creatureLocation, targetId, target);
			}
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

	/**
	 * Sets the state of the cow to be ready to produce offspring at a specific location if it is in love mode but not
	 * already pregnant.
	 * 
	 * @param offspringLocation The location where the offspring should be created.
	 * @return True if the cow became pregnant.
	 */
	public boolean setPregnant(EntityLocation offspringLocation)
	{
		boolean didBecomePregnant = false;
		if (_inLoveMode)
		{
			// Must not already be pregnant if in love mode.
			Assert.assertTrue(null == _offspringLocation);
			
			_inLoveMode = false;
			_clearPlans();
			_offspringLocation = offspringLocation;
			_nextDeliberateActTick = 0L;
			didBecomePregnant = true;
		}
		return didBecomePregnant;
	}

	@Override
	public boolean doneSpecialActions(TickProcessingContext context, Consumer<CreatureEntity> creatureSpawner, Runnable requestDespawnWithoutDrops, EntityLocation creatureLocation, int creatureId)
	{
		// See if we are pregnant or searching for our mate.
		boolean didTakeAction = false;
		if (null != _offspringLocation)
		{
			// We need to spawn an entity here (we use a placeholder since ID is re-assigned in the consumer).
			creatureSpawner.accept(CreatureEntity.create(context.idAssigner.next(), EntityType.COW, _offspringLocation, EntityConstants.COW_MAX_HEALTH));
			_offspringLocation = null;
			didTakeAction = true;
		}
		else if (0 != _targetEntityId)
		{
			// We are tracking a target so see if they have moved (since we would need to clear our existing targets and
			// movement plans unless they are close enough for other actions).
			MinimalEntity targetEntity = context.previousEntityLookUp.apply(_targetEntityId);
			if (null != targetEntity)
			{
				EntityLocation targetLocation = targetEntity.location();
				
				// First, see if we could impregnate them.
				float distance = SpatialHelpers.distanceBetween(creatureLocation, targetLocation);
				if (_inLoveMode && (distance <= COW_MATING_DISTANCE) && (targetEntity.id() < creatureId))
				{
					// Send the message to impregnate them.
					EntityChangeImpregnateCreature sperm = new EntityChangeImpregnateCreature(creatureLocation);
					context.newChangeSink.creature(_targetEntityId, sperm);
					// We can now exit love mode.
					_inLoveMode = false;
					_clearPlans();
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
						didTakeAction = true;
					}
				}
			}
			else
			{
				_clearPlans();
				didTakeAction = true;
			}
		}
		return didTakeAction;
	}

	@Override
	public int getPathDistance()
	{
		return COW_PATH_DISTANCE;
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
		_ExtendedData newData = (_inLoveMode || (null != _movementPlan) || (null != _offspringLocation) || (_nextDeliberateActTick > 0L) || (_nextIdleActTick > 0L))
				? new _ExtendedData(_inLoveMode, _movementPlan, _targetEntityId, _targetPreviousLocation, _offspringLocation, _nextDeliberateActTick, _nextIdleActTick)
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

	private void _findBreedableCow(EntityCollection entityCollection, EntityLocation creatureLocation, int thisCreatureId, int[] out_targetId, EntityLocation[] out_target)
	{
		float[] distanceToTarget = new float[] { Float.MAX_VALUE };
		entityCollection.walkCreaturesInRange(creatureLocation, COW_VIEW_DISTANCE, (CreatureEntity check) -> {
			// Ignore ourselves and make sure that they are the right type.
			if ((thisCreatureId != check.id()) && (EntityType.COW == check.type()))
			{
				// See if they are also in love mode.
				_ExtendedData other = (_ExtendedData) check.extendedData();
				if ((null != other) && other.inLoveMode)
				{
					EntityLocation end = check.location();
					float distance = SpatialHelpers.distanceBetween(creatureLocation, end);
					if (distance < distanceToTarget[0])
					{
						out_targetId[0] = check.id();
						out_target[0] = end;
						distanceToTarget[0] = distance;
					}
				}
			}
		});
	}

	private void _findWheatTarget(EntityCollection entityCollection, EntityLocation creatureLocation, int[] out_targetId, EntityLocation[] out_target)
	{
		float[] distanceToTarget = new float[] { Float.MAX_VALUE };
		Environment environment = Environment.getShared();
		Item wheat = environment.items.getItemById(ITEM_NAME_WHEAT);
		entityCollection.walkPlayersInRange(creatureLocation, COW_VIEW_DISTANCE, (Entity player) -> {
			// See if this player has wheat in their hand.
			int itemKey = player.hotbarItems()[player.hotbarIndex()];
			Items itemsInHand = player.inventory().getStackForKey(itemKey);
			if ((null != itemsInHand) && (wheat == itemsInHand.type()))
			{
				EntityLocation end = player.location();
				float distance = SpatialHelpers.distanceBetween(creatureLocation, end);
				if (distance < distanceToTarget[0])
				{
					out_targetId[0] = player.id();
					out_target[0] = end;
					distanceToTarget[0] = distance;
				}
			}
		});
	}


	/**
	 * This is a testing variant of _ExtendedData which only exists to make unit tests simpler.
	 */
	public static record Test_ExtendedData(boolean inLoveMode
			, List<AbsoluteLocation> movementPlan
			, int targetEntityId
			, AbsoluteLocation targetPreviousLocation
			, EntityLocation offspringLocation
			, long nextDeliberateActTick
			, long nextIdleActTick
	)
	{}

	private static record _ExtendedData(boolean inLoveMode
			, List<AbsoluteLocation> movementPlan
			, int targetEntityId
			, AbsoluteLocation targetPreviousLocation
			, EntityLocation offspringLocation
			, long nextDeliberateActTick
			, long nextIdleActTick
	)
	{}
}
