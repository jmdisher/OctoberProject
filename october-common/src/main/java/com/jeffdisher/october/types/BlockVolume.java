package com.jeffdisher.october.types;


/**
 * Used to describe a rectangular prism volume in the 3D block space.  Values given are the size of the volume in X/Y/Z
 * directions.
 */
public final record BlockVolume(int x, int y, int z)
{
}
