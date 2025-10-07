package com.jeffdisher.october.types;


/**
 * Passive entities are for entity types which are not controlled by a player and are much simpler than creatures.  They
 * have only an ID, type, location, velocity, extended data (defined by type), and ephemeral data.
 * These exist as a peer to players and creatures but have a completely different ID namespace (expressed as positive
 * integers).  Note that these are also represented differently on the wire, not as normal partial entities.
 * These entity types are for things like item stacks and projectiles which usually only apply physics to their position
 * and velocity, not doing much else.
 * They can have some simple behaviour, such as grouping together or doing something to an entity when they hit it, but
 * these are generally considered very trivial and require no real planning.
 */
public record PassiveEntity(int id
	, PassiveType type
	// Note that the location is the bottom, south-west corner of the space occupied by the entity and the volume extends from there.
	, EntityLocation location
	, EntityLocation velocity
	// This data is defined by PassiveType, per-instance, and is persisted to disk and sent over the network.
	, Object extendedData
	
	// --- Data below this point is considered ephemeral and is neither persisted to disk nor sent over the network.
	// lastAliveMillis - This is the last game millisecond where the instance did something which should keep it alive
	// (since these are intended to despawn after some time in the world).
	, long lastAliveMillis
)
{
}
