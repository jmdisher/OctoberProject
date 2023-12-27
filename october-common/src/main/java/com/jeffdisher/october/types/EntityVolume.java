package com.jeffdisher.october.types;


/**
 * This record just exists as a simple way to tie together the volume associated with an entity, instead of passing
 * around the primitive floats, directly.
 */
public final record EntityVolume(float height, float width)
{
}
