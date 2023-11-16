package com.jeffdisher.october.types;


/**
 * An Entity represents something which can move in the world.  This includes users, monsters, animals, and machines.
 * An Entity instance is immutable and is generally created through changes to EntityActionValidator.
 */
public class Entity
{
	public final int id;
	public final EntityLocation location;
	public final EntityVolume volume;
	// The maximum distance, in blocks, the entity can move in a single tick (float since this is usually less than 1).
	public final float blocksPerTickSpeed;

	public Entity(int id
			, EntityLocation location
			, EntityVolume volume
			, float blocksPerTickSpeed
	) {
		this.id = id;
		this.location = location;
		this.volume = volume;
		this.blocksPerTickSpeed = blocksPerTickSpeed;
	}
}
