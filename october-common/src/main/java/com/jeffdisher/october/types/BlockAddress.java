package com.jeffdisher.october.types;


/**
 * The address of a single block within a cuboid, in units of blocks.  Note that these are relative to the base of the
 * cuboid and are therefore only UNSIGNED.
 * NOTE:  It is acceptable to create these instances with fields which are out of the [0..32) range, but much of the
 * core system assumes that they are well-formed, within that range.
 */
public record BlockAddress(byte x, byte y, byte z)
{
	/**
	 * A helper factory method to create an instance without explicit casting to byte.
	 * 
	 * @param x The x coordinate.
	 * @param y The y coordinate.
	 * @param z The z coordinate.
	 * @return A new BlockAddress.
	 */
	public static final BlockAddress fromInt(int x, int y, int z)
	{
		return new BlockAddress((byte)x, (byte)y, (byte)z);
	}

	/**
	 * Creates a new instance relative to the receiver.
	 * 
	 * @param rx The relative X.
	 * @param ry The relative Y.
	 * @param rz The relative Z.
	 * @return A new instance, relative to the receiver.
	 */
	public final BlockAddress getRelative(byte rx, byte ry, byte rz)
	{
		return new BlockAddress((byte)(x + rx), (byte)(y + ry), (byte)(z + rz));
	}

	/**
	 * Creates a new instance relative to the receiver without explicit casting to byte.
	 * 
	 * @param rx The relative X.
	 * @param ry The relative Y.
	 * @param rz The relative Z.
	 * @return A new instance, relative to the receiver.
	 */
	public final BlockAddress getRelativeInt(int rx, int ry, int rz)
	{
		return new BlockAddress((byte)(x + rx), (byte)(y + ry), (byte)(z + rz));
	}
}
