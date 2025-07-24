package com.jeffdisher.october.types;

import java.util.List;

import com.jeffdisher.october.properties.Property;


/**
 * A non-stackable item.  This can be considered a peer to the Items object
 */
public record NonStackableItem (Item type
		, List<Property<?>> properties
) {}
