package com.jeffdisher.october.aspects;


/**
 * An aspect is a single type of data associated with a block in a cuboid.  These are registered at start-up and given
 * an index.
 */
public record Aspect<T> (String name, int index, Class<T> type)
{
}
