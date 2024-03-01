package com.jeffdisher.october.persistence;

import java.util.List;

import com.jeffdisher.october.mutations.IMutationBlock;


/**
 * Just a container of cuboids and associated mutations used in the persistence layer.
 * This type exists just to tie these together for convenience through a few parts of the system.
 */
public record SuspendedCuboid<T>(T cuboid, List<IMutationBlock> mutations)
{
}
