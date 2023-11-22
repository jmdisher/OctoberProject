package com.jeffdisher.october.aspects;

import java.util.function.Function;

import com.jeffdisher.october.data.IOctree;


/**
 * An aspect is a single type of data associated with a block in a cuboid.  These are registered at start-up and given
 * an index.
 */
public record Aspect<T> (int index, Class<T> type, Function<IOctree, IOctree> deepMutableClone)
{
}
