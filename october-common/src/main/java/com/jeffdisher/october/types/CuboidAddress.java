package com.jeffdisher.october.types;


/**
 * The address of a cuboid in absolute coordinates, in units of cuboids.  These coordinates are SIGNED.
 */
public record CuboidAddress(short x, short y, short z)
{
}
