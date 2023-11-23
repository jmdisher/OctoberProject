package com.jeffdisher.october.logic;

import java.util.function.Consumer;


/**
 * Similar to IMutation, this is the corresponding logic for changes to entity state.
 * Note that the entity changes are applied strictly BEFORE the mutation changes in a given tick.
 * Additionally, the order of how new entity changes are enqueued at the end of a tick:
 * 1) Changes enqueued by existing changes
 * 2) Changes enqueued by existing mutations
 * 3) New entity joins
 * 4) Changes enqueued by the external calls
 * TODO:  Add the entity information and new change sinks.  This is just a stop-gap to update SpeculativeProjection
 * shape.
 */
public interface IEntityChange
{
	int getTargetId();

	boolean applyChange(Consumer<IMutation> newMutationSink);

	IEntityChange applyChangeReversible(Consumer<IMutation> newMutationSink);
}
