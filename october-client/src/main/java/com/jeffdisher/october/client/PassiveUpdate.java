package com.jeffdisher.october.client;

import com.jeffdisher.october.types.EntityLocation;


/**
 * This is just a tuple to contain the updates to make against client-side PartialPassive instances.
 */
public record PassiveUpdate(int passiveEntityId
	, EntityLocation location
	, EntityLocation velocity
)
{
}
