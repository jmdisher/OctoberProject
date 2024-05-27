package com.jeffdisher.october.creatures;

import java.util.List;
import java.util.function.Predicate;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.logic.PathFinder;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.IMutableCreatureEntity;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.utils.Assert;


/**
 * Helpers and data types used to implement creature behaviour.  In the future, this might move (especially if we end up
 * changing this to move of an OO approach).
 */
public class CreatureLogic
{
	public static final String ITEM_NAME_WHEAT = "op.wheat_item";
	public static final float COW_VIEW_DISTANCE = 6.0f;

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
	 * Called by the CreatureProcessor AI logic to build a new planned action for a creature.
	 * 
	 * @param blockPermitsPassage A predicate used by the PathFinder to see if an entity can pass through a block.
	 * @param entityCollection The entities in the loaded world.
	 * @param creature The creature to plan.
	 * @return The path to take or null if no deliberate plan could be made.
	 */
	public static List<AbsoluteLocation> buildNewPath(Predicate<AbsoluteLocation> blockPermitsPassage, EntityCollection entityCollection, CreatureEntity creature)
	{
		List<AbsoluteLocation> path;
		switch (creature.type())
		{
		case COW:
			path = _buildPathForCow(blockPermitsPassage, entityCollection, creature);
			break;
		case ERROR:
		case PLAYER:
		default:
			throw Assert.unreachable();
		}
		return path;
	}


	private static _ExtendedCow _didApplyToCow(Item itemType, _ExtendedCow extendedData)
	{
		Environment env = Environment.getShared();
		Item wheat = env.items.getItemById(ITEM_NAME_WHEAT);
		_ExtendedCow updated = null;
		if ((itemType == wheat) && ((null == extendedData) || !extendedData.inLoveMode))
		{
			updated = new _ExtendedCow(true);
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


	private static record _ExtendedCow(boolean inLoveMode)
	{}
}
