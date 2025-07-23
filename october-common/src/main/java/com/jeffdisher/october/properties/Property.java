package com.jeffdisher.october.properties;


/**
 * A single instance of a property - having a specific type and value.  These instances are intended to be immutable,
 * meaning that the underlying value should also be an immutable type.
 * 
 * @param <T> The type of the value within the property.
 */
public record Property<T>(PropertyType<T> type
	, T value
)
{
}
