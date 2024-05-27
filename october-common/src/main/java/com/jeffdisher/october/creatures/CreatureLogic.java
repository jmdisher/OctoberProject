package com.jeffdisher.october.creatures;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.IMutableCreatureEntity;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.utils.Assert;


/**
 * Helpers and data types used to implement creature behaviour.  In the future, this might move (especially if we end up
 * changing this to move of an OO approach).
 */
public class CreatureLogic
{
	public static final String ITEM_NAME_WHEAT = "op.wheat_item";

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


	private static record _ExtendedCow(boolean inLoveMode)
	{}
}
