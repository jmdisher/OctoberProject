package com.jeffdisher.october.creatures;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.logic.CreatureMovementHelpers;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.logic.PathFinder;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.IMutableCreatureEntity;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableCreature;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Helpers and data types used to implement creature behaviour.  In the future, this might move (especially if we end up
 * changing this to move of an OO approach).
 */
public class CreatureLogic
{
	public static final String ITEM_NAME_WHEAT = "op.wheat_item";
	public static final float COW_VIEW_DISTANCE = 6.0f;
	public static final long MINIMUM_TICKS_TO_NEW_ACTION = 10L;
	public static final float RANDOM_MOVEMENT_DISTANCE = 2.5f;

	/**
	 * A helper to determine if the given item can be used on a specific entity type with this entity mutation.
	 * 
	 * @param item The item.
	 * @param entityType The target entity type.
	 * @return True if this mutation can be used to apply the item to the entity.
	 */
	public static boolean canUseOnEntity(Item item, EntityType entityType)
	{
		Environment env = Environment.getShared();
		Item wheat = env.items.getItemById(ITEM_NAME_WHEAT);
		return (wheat == item) && (EntityType.COW == entityType);
	}

	/**
	 * Called within a mutation to apply an item to a creature.  This may change their state or not.
	 * 
	 * @param itemType The item type to apply (it will be consumed, either way).
	 * @param creature The creature to change.
	 * @return True if the creature state changed or false if it had no effect.
	 */
	public static boolean applyItemToCreature(Item itemType, IMutableCreatureEntity creature)
	{
		boolean didApply;
		switch (creature.getType())
		{
		case COW:
			_ExtendedCow updated = _didApplyToCow(itemType, (_ExtendedCow) creature.getExtendedData());
			if (null != updated)
			{
				creature.setExtendedData(updated);
				didApply = true;
			}
			else
			{
				didApply = false;
			}
			break;
		case ERROR:
		case PLAYER:
		default:
			throw Assert.unreachable();
		}
		return didApply;
	}

	/**
	 * Called when newStepsToNextMove is null in order to determine the next action for the entity.  This field will be
	 * populated by this method, providing either the next actions the entity should take, an empty list to reset the
	 * last action timer, or null to allow the timer to expire.
	 * 
	 * @param context The context of the current tick.
	 * @param entityCollection The read-only collection of entities in the world.
	 * @param random A random generator.
	 * @param millisSinceLastTick Milliseconds since the last tick was run.
	 * @param mutable The mutable creature object currently being evaluated.
	 */
	public static void populateNextSteps(TickProcessingContext context, EntityCollection entityCollection, Random random, long millisSinceLastTick, MutableCreature mutable)
	{
		// Only called if we don't currently have a published set of next steps.
		Assert.assertTrue(null == mutable.newStepsToNextMove);
		// The logic is per-creature type.
		switch (mutable.creature.type())
		{
		case COW:
			_populateNextStepsForCow(context, entityCollection, random, millisSinceLastTick, mutable);
			break;
		case ERROR:
		case PLAYER:
		default:
			throw Assert.unreachable();
		}
	}

	/**
	 * TESTING ONLY!
	 * Packages the given movementPlan into the extended data object for a cow.
	 * 
	 * @param movementPlan The plan.
	 * @return The packaged extended object.
	 */
	public static Object test_packageMovementPlanCow(List<AbsoluteLocation> movementPlan)
	{
		return new _ExtendedCow(false, movementPlan);
	}

	/**
	 * TESTING ONLY!
	 * Unpackages the movement plan from the extended data object for a cow.
	 * 
	 * @param creature The creature to read.
	 * @return The movement plan, potentially null.
	 */
	public static List<AbsoluteLocation> test_unwrapMovementPlanCow(CreatureEntity creature)
	{
		_ExtendedCow extended = (_ExtendedCow) creature.extendedData();
		return (null != extended)
				? extended.movementPlan
				: null
		;
	}


	private static _ExtendedCow _didApplyToCow(Item itemType, _ExtendedCow extendedData)
	{
		Environment env = Environment.getShared();
		Item wheat = env.items.getItemById(ITEM_NAME_WHEAT);
		_ExtendedCow updated = null;
		if ((itemType == wheat) && ((null == extendedData) || !extendedData.inLoveMode))
		{
			// Even if we had a plan, we will drop it here since we are changing modes.
			updated = new _ExtendedCow(true, null);
		}
		return updated;
	}

	private static List<AbsoluteLocation> _buildPathForCow(Predicate<AbsoluteLocation> blockPermitsPassage, EntityCollection entityCollection, CreatureEntity creature)
	{
		Environment environment = Environment.getShared();
		EntityLocation start = creature.location();
		_ExtendedCow extendedData = (_ExtendedCow) creature.extendedData();
		
		// As a cow, we have 2 explicit reasons for movement:  another cow when in love mode or a player holding wheat when not.
		boolean isInLoveMove = (null != extendedData) && extendedData.inLoveMode;
		EntityLocation targetLocation;
		if (isInLoveMove)
		{
			// Find another cow in breeding mode.
			// We will just use arrays to pass this "by reference".
			EntityLocation[] target = new EntityLocation[1];
			float[] distanceToTarget = new float[] { Float.MAX_VALUE };
			entityCollection.walkCreaturesInRange(start, COW_VIEW_DISTANCE, (CreatureEntity check) -> {
				// Ignore ourselves and make sure that they are the right type.
				if ((creature != check) && (EntityType.COW == check.type()))
				{
					// See if they are also in love mode.
					_ExtendedCow other = (_ExtendedCow) check.extendedData();
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
		
		List<AbsoluteLocation> path = null;
		if (null != targetLocation)
		{
			// We have a target so try to build a path (we will use double the distance for pathing overhead).
			// If this fails, it will return null which is already our failure case.
			EntityVolume volume = CreatureVolumes.getVolume(creature);
			path = PathFinder.findPathWithLimit(blockPermitsPassage, volume, start, targetLocation, 2 * COW_VIEW_DISTANCE);
		}
		return path;
	}

	private static void _populateNextStepsForCow(TickProcessingContext context, EntityCollection entityCollection, Random random, long millisSinceLastTick, MutableCreature mutable)
	{
		// We know that this is for a cow so unwrap our extended data.
		_ExtendedCow extended = (_ExtendedCow) mutable.newExtendedData;
		List<AbsoluteLocation> movementPlan = null;
		
		if ((null == extended) || (null == extended.movementPlan))
		{
			// We have no plan so determine one.
			// We will only do this if we have been idle for long enough.
			if (context.currentTick > (mutable.newLastActionGameTick + MINIMUM_TICKS_TO_NEW_ACTION))
			{
				// First, we want to see if we should walk toward a player.
				movementPlan = _buildDeliberatePathCow(context, entityCollection, mutable.creature);
				if (null == movementPlan)
				{
					// We couldn't find a player so just make a random move.
					movementPlan = _findPathToRandomSpot(context, random, mutable.creature);
				}
				if (null == movementPlan)
				{
					// There are no possible moves so do nothing, but make an empty plan so we will reset the last move timer (as to not just do the same thing, again).
					mutable.newStepsToNextMove = List.of();
				}
				else
				{
					// We have a path so make sure we are centred in a block, first.
					mutable.newStepsToNextMove = CreatureMovementHelpers.centreOnCurrentBlock(mutable.creature);
					if (mutable.newStepsToNextMove.isEmpty())
					{
						// We are already in the centre so just get started.
						mutable.newStepsToNextMove = null;
					}
				}
			}
		}
		else
		{
			// Just use whatever we had.
			movementPlan = extended.movementPlan;
		}
		
		// Determine if we need to set the next steps in case we devised a plan.
		// (the movementPlan should only be null here if we are waiting to make our next decision)
		if ((null == mutable.newStepsToNextMove) && (null != movementPlan))
		{
			// The only reason why this is still null at this point is if we have a plan and we are ready to use it.
			List<AbsoluteLocation> mutablePlan = new ArrayList<>(movementPlan);
			mutable.newStepsToNextMove = _determineNextSteps(mutable.creature, mutablePlan);
			
			// We can now update our extended data.
			boolean inLoveMode = (null != extended) ? extended.inLoveMode : false;
			// Since this data structure should be immutable, use an immutable list.
			List<AbsoluteLocation> remainingPlan = mutablePlan.isEmpty() ? null : Collections.unmodifiableList(mutablePlan);
			if (inLoveMode || (null != remainingPlan))
			{
				mutable.newExtendedData = new _ExtendedCow(inLoveMode, remainingPlan);
			}
			else
			{
				mutable.newExtendedData = null;
			}
		}
	}

	private static List<AbsoluteLocation> _buildDeliberatePathCow(TickProcessingContext context, EntityCollection entityCollection, CreatureEntity creature)
	{
		Environment environment = Environment.getShared();
		Predicate<AbsoluteLocation> blockPermitsPassage = (AbsoluteLocation location) -> {
			BlockProxy proxy = context.previousBlockLookUp.apply(location);
			return (null != proxy)
					? environment.blocks.permitsEntityMovement(proxy.getBlock())
					: false
			;
		};
		return _buildPathForCow(blockPermitsPassage, entityCollection, creature);
	}

	private static List<AbsoluteLocation> _findPathToRandomSpot(TickProcessingContext context, Random random, CreatureEntity creature)
	{
		// Our current only action is to find a block nearby and walk to it.
		Environment environment = Environment.getShared();
		Predicate<AbsoluteLocation> blockPermitsUser = (AbsoluteLocation location) -> {
			BlockProxy proxy = context.previousBlockLookUp.apply(location);
			return (null != proxy)
					? environment.blocks.permitsEntityMovement(proxy.getBlock())
					: false
			;
		};
		EntityVolume volume = CreatureVolumes.getVolume(creature);
		EntityLocation source = creature.location();
		float limitSteps = RANDOM_MOVEMENT_DISTANCE;
		Map<AbsoluteLocation, AbsoluteLocation> possiblePaths = PathFinder.findPlacesWithinLimit(blockPermitsUser, volume, source, limitSteps);
		// Just pick one of these destinations at random, of default to standing still.
		int size = possiblePaths.size();
		List<AbsoluteLocation> plannedPath;
		if (size > 0)
		{
			int selection = Math.abs(random.nextInt()) % size;
			// Skip over this many options (we can't really index into this and choosing the "first" would give a hash-order preference).
			AbsoluteLocation target = possiblePaths.keySet().toArray((int arraySize) -> new AbsoluteLocation[arraySize])[selection];
			
			// We can now build the plan - note that we build this in reverse since the map is back-pointers.
			plannedPath = new ArrayList<>();
			while (null != target)
			{
				plannedPath.add(0, target);
				target = possiblePaths.get(target);
			}
		}
		else
		{
			plannedPath = null;
		}
		return plannedPath;
	}

	private static List<IMutationEntity<IMutableCreatureEntity>> _determineNextSteps(CreatureEntity creature, List<AbsoluteLocation> mutablePlan)
	{
		// First, check to see if we are already in our next location.
		AbsoluteLocation thisStep = mutablePlan.get(0);
		EntityLocation entityLocation = creature.location();
		AbsoluteLocation currentLocation = entityLocation.getBlockLocation();
		
		List<IMutationEntity<IMutableCreatureEntity>> changes;
		if (currentLocation.equals(thisStep))
		{
			// If we are, that means that we can remove this from the path and plan to move to the next step.
			mutablePlan.remove(0);
			if (!mutablePlan.isEmpty())
			{
				AbsoluteLocation nextStep = mutablePlan.get(0);
				changes = CreatureMovementHelpers.moveToNextLocation(creature, nextStep);
				// If this is empty, just allow us to do nothing and reset the timer instead of returning null (should only be empty if falling, for example).
			}
			else
			{
				// There is nothing left in the plan so just return null so that the timer will expire soon for the next plan.
				changes = null;
			}
		}
		else
		{
			// Otherwise, check to see if we are currently in the air, since we might just be jumping/falling into the next location.
			if (SpatialHelpers.isBlockAligned(entityLocation.z()))
			{
				// We appear to be on the surface, meaning that we were somehow blocked.  Therefore, clear the plan and we will try again after our idle timer.
				mutablePlan.clear();
				changes = null;
			}
			else
			{
				// Do nothing since we are moving through the air.
				changes = null;
			}
		}
		return changes;
	}


	private static record _ExtendedCow(boolean inLoveMode
			, List<AbsoluteLocation> movementPlan
	)
	{}
}
