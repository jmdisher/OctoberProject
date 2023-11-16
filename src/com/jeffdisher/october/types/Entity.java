package com.jeffdisher.october.types;


/**
 * An Entity represents something which can move in the world.  This includes users, monsters, animals, and machines.
 * An Entity has a location, volume, and face orientation.
 * An Entity is completely mutable, and has no internal rules governing state updates, so care must be taken when
 * assuming anything about it.
 */
public class Entity
{
	public EntityLocation location;
	public EntityVolume volume;
	// Yaw is horizontal face direction where the full circle is all 256 values.
	public byte faceYaw;
	// Pitch is vertical face direction where the half-circle from bottom to top is all 256 values.
	public byte facePitch;
}
