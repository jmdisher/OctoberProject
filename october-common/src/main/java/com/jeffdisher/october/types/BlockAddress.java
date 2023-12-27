package com.jeffdisher.october.types;


/**
 * The address of a single block within a cuboid, in units of blocks.  Note that these are relative to the base of the
 * cuboid and are therefore only UNSIGNED.
 */
public record BlockAddress(byte x, byte y, byte z)
{
}
