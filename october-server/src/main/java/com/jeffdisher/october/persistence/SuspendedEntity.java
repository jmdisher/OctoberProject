package com.jeffdisher.october.persistence;

import java.util.List;

import com.jeffdisher.october.logic.ScheduledChange;
import com.jeffdisher.october.types.Entity;


/**
 * Just a container of entities and associated mutations used in the persistence layer.
 * This type exists just to tie these together for convenience through a few parts of the system.
 */
public record SuspendedEntity(Entity entity, List<ScheduledChange> changes)
{
}
