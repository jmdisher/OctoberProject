package com.jeffdisher.october.actions;

import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IEntitySubAction;
import com.jeffdisher.october.types.IMutableMinimalEntity;


/**
 * An interface specifically describing the action types which can be submitted by a connected client to run on a player
 * entity.
 *
 * @param <T> The mutable entity type.
 */
public interface IEntityActionFromClient<T extends IMutableMinimalEntity> extends IEntityAction<T>
{
	/**
	 * Some tests but also things like MovementAccumulator need to know if there is a special sub-action inside this
	 * instance so this allows them to view it.
	 * 
	 * @return The sub-action.
	 */
	IEntitySubAction<T> getSubAction();
}
