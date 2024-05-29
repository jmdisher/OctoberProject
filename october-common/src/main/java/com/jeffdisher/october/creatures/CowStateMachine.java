package com.jeffdisher.october.creatures;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.utils.Assert;


/**
 * The extended state data in CreatureEntity is immutable but the interactions with it form a pretty straight-forward
 * state machine so this object exists as a mutable projection of that state with a high-level interface.
 * It will re-serialize itself to the original instance if unchanged.
 * Ideally, we will eventually find a way to encode this in some declarative data structure, but that is a ways off, if
 * even possible (may not be sufficiently expressive).
 */
public class CowStateMachine
{
	public static final String ITEM_NAME_WHEAT = "op.wheat_item";
	public static final float COW_VIEW_DISTANCE = 6.0f;

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
	 * Packages the given movementPlan into the extended data object for a cow.
	 * 
	 * @param movementPlan The plan.
	 * @return The packaged extended object.
	 */
	public static Object test_packageMovementPlan(List<AbsoluteLocation> movementPlan)
	{
		return new _ExtendedData(false, movementPlan);
	}

	/**
	 * TESTING ONLY!
	 * Unpackages the movement plan from the extended data object for a cow.
	 * 
	 * @param creature The creature to read.
	 * @return The movement plan, potentially null.
	 */
	public static List<AbsoluteLocation> test_unwrapMovementPlan(Object data)
	{
		_ExtendedData extended = (_ExtendedData) data;
		return (null != extended)
				? extended.movementPlan
				: null
		;
	}


	private final _ExtendedData _originalData;
	private boolean _inLoveMode;
	private List<AbsoluteLocation> _movementPlan;
	
	private CowStateMachine(_ExtendedData data)
	{
		_originalData = data;
		_inLoveMode = (null != data) ? data.inLoveMode : false;
		_movementPlan = (null != data) ? data.movementPlan : null;
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
			// If this is already true, the item is lost as this is a saturating operation.
			_inLoveMode = true;
		}
	}

	/**
	 * Asks the creature to pick a new target entity location based on its currently location and the other players or
	 * creatures in the loaded world.
	 * 
	 * @param entityCollection The collection of entities in the world.
	 * @param thisCreature The instance being asked.
	 * @return The location of the target entity or null if there is no target.
	 */
	public EntityLocation selectDeliberateTarget(EntityCollection entityCollection, CreatureEntity thisCreature)
	{
		// We can only call this if we don't already have a movement plan.
		Assert.assertTrue(null == _movementPlan);
		EntityLocation start = thisCreature.location();
		
		// As a cow, we have 2 explicit reasons for movement:  another cow when in love mode or a player holding wheat when not.
		EntityLocation targetLocation;
		if (_inLoveMode)
		{
			// Find another cow in breeding mode.
			// We will just use arrays to pass this "by reference".
			EntityLocation[] target = new EntityLocation[1];
			float[] distanceToTarget = new float[] { Float.MAX_VALUE };
			entityCollection.walkCreaturesInRange(start, COW_VIEW_DISTANCE, (CreatureEntity check) -> {
				// Ignore ourselves and make sure that they are the right type.
				if ((thisCreature != check) && (EntityType.COW == check.type()))
				{
					// See if they are also in love mode.
					_ExtendedData other = (_ExtendedData) check.extendedData();
					if ((null != other) && other.inLoveMode)
					{
						EntityLocation end = check.location();
						float distance = SpatialHelpers.distanceBetween(start, end);
						if (distance < distanceToTarget[0])
						{
							target[0] = end;
							distanceToTarget[0] = distance;
						}
					}
				}
			});
			targetLocation = target[0];
		}
		else
		{
			// We will keep this simple:  Find the closest player holding wheat, up to our limit.
			Environment environment = Environment.getShared();
			Item wheat = environment.items.getItemById(ITEM_NAME_WHEAT);
			// We will just use arrays to pass this "by reference".
			EntityLocation[] target = new EntityLocation[1];
			float[] distanceToTarget = new float[] { Float.MAX_VALUE };
			entityCollection.walkPlayersInRange(start, COW_VIEW_DISTANCE, (Entity player) -> {
				// See if this player has wheat in their hand.
				int itemKey = player.hotbarItems()[player.hotbarIndex()];
				Items itemsInHand = player.inventory().getStackForKey(itemKey);
				if ((null != itemsInHand) && (wheat == itemsInHand.type()))
				{
					EntityLocation end = player.location();
					float distance = SpatialHelpers.distanceBetween(start, end);
					if (distance < distanceToTarget[0])
					{
						target[0] = end;
						distanceToTarget[0] = distance;
					}
				}
			});
			targetLocation = target[0];
		}
		return targetLocation;
	}

	/**
	 * An accessor for the read-only movement plan in the instance.
	 * 
	 * @return A read-only view of the current movement plan (could be null).
	 */
	public List<AbsoluteLocation> getMovementPlan()
	{
		// The caller shouldn't change this.
		return (null != _movementPlan)
				? Collections.unmodifiableList(_movementPlan)
				: null
		;
	}

	/**
	 * Updates the movement plan to a copy of the one given.
	 * 
	 * @param movementPlan The movement plan (could be null).
	 */
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
	 * Freezes the current state of the creature's extended data into an opaque read-only instance.  May return null or
	 * the original instance.
	 * NOTE:  The instance should be considered invalid after this call.
	 * 
	 * @return An opaque extended data object (could be null).
	 */
	public Object freezeToData()
	{
		_ExtendedData newData = (_inLoveMode || (null != _movementPlan))
				? new _ExtendedData(_inLoveMode, _movementPlan)
				: null
		;
		_ExtendedData matchingData = (null != _originalData)
				? (_originalData.equals(newData) ? _originalData : newData)
				: newData
		;
		return matchingData;
	}


	private static record _ExtendedData(boolean inLoveMode
			, List<AbsoluteLocation> movementPlan
	)
	{}
}
