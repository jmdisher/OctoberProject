package com.jeffdisher.october.properties;

import com.jeffdisher.october.data.IObjectCodec;


/**
 * A Property is similar to an Aspect, but it is intended to be associated with things like NonStackableItem instances.
 * Additionally, a user of a Property doesn't need to use all of them and can use duplicates.
 */
public record PropertyType<T> (int index
		, Class<T> type
		, IObjectCodec<T> codec
)
{
}
