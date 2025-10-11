package com.jeffdisher.october.types;


/**
 * This is the client-side projection of PassiveEntity.
 * It includes everything except for the lastAliveMillis (since that isn't meaningful on the client) and mostly exists
 * just to make the type distinction more meaningful.
 */
public record PartialPassive(int id
	, PassiveType type
	, EntityLocation location
	, EntityLocation velocity
	// This data is defined by PassiveType, per-instance, and is persisted to disk and sent over the network.
	, Object extendedData
)
{
}
