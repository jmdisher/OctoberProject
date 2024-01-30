package com.jeffdisher.october.aspects;

import java.util.function.Function;
import java.util.function.Supplier;

import com.jeffdisher.october.data.IAspectCodec;
import com.jeffdisher.october.data.IOctree;


/**
 * An aspect is a single type of data associated with a block in a cuboid.  These are registered at start-up and given
 * an index.
 */
public record Aspect<T, O extends IOctree> (int index
		, Class<T> type
		, Class<O> octreeType
		, Supplier<O> emptyTreeSupplier
		, Function<O, O> deepMutableClone
		, IAspectCodec<T> codec
)
{
}
