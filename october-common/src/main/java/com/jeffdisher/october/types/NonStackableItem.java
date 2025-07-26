package com.jeffdisher.october.types;

import java.util.Map;

import com.jeffdisher.october.properties.PropertyType;


/**
 * A non-stackable item.  This can be considered a peer to the Items object
 */
public record NonStackableItem (Item type
		, Map<PropertyType<?>, Object> properties
) {}
