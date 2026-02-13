package com.jeffdisher.october.types;


/**
 * Used to track the coupling of some action for an entity/creature/passive and its target entity/passive ID.
 */
public record TargetedAction<T>(int targetId
	, T action
)
{
}
