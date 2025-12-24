package com.jeffdisher.october.types;

import java.util.function.Function;


/**
 * The direction will rotate a given location from NORTH to the given direction.
 * These are in the order of positive yaw (to the left - aka counter-clockwise).
 */
public enum FacingDirection
{
	// North is the positive Y direction, which we consider the "default" orientation (hence 0).
	NORTH(new int[] {1, 0, 0, 1}, (AbsoluteLocation thisLocation) -> thisLocation.getRelative(0, 1, 0)),
	// West is the negative X direction.
	WEST(new int[] {0, -1, 1, 0}, (AbsoluteLocation thisLocation) -> thisLocation.getRelative(-1, 0, 0)),
	// South is the negative Y direction.
	SOUTH(new int[] {-1, 0, 0, -1}, (AbsoluteLocation thisLocation) -> thisLocation.getRelative(0, -1, 0)),
	// East is the positive X direction.
	EAST(new int[] {0, 1, -1, 0}, (AbsoluteLocation thisLocation) -> thisLocation.getRelative(1, 0, 0)),
	// Down is the negative Z direction but rotation doesn't make sense for this direction.
	DOWN(null, (AbsoluteLocation thisLocation) -> thisLocation.getRelative(0, 0, -1)),
	;

	/**
	 * Converts a FacingDirection object into a byte for storage.
	 * 
	 * @param direction The direction object (cannot be null).
	 * @return The byte to store in the aspect storage.
	 */
	public static byte directionToByte(FacingDirection direction)
	{
		return (byte)direction.ordinal();
	}

	/**
	 * Converts a byte value from storage into a high-level FacingDirection enum object.
	 * 
	 * @param value The byte extracted from storage (must be in valid range).
	 * @return The associated FacingDirection enum object.
	 */
	public static FacingDirection byteToDirection(byte value)
	{
		return FacingDirection.values()[value];
	}

	/**
	 * Determines the orientation direction a block in blockLocation would be if its output is facing outputLocation.
	 * 
	 * @param blockLocation The location of a block.
	 * @param outputLocation The location the output is facing.
	 * @return The relative direction.
	 */
	public static FacingDirection getRelativeDirection(AbsoluteLocation blockLocation, AbsoluteLocation outputLocation)
	{
		// Check the direction of the output, relative to target block.
		int blockX = blockLocation.x();
		int blockY = blockLocation.y();
		int outputX = outputLocation.x();
		int outputY = outputLocation.y();
		
		FacingDirection outputDirection;
		if (outputY > blockY)
		{
			outputDirection = FacingDirection.NORTH;
		}
		else if (outputY < blockY)
		{
			outputDirection = FacingDirection.SOUTH;
		}
		else if (outputX > blockX)
		{
			outputDirection = FacingDirection.EAST;
		}
		else if (outputX < blockX)
		{
			outputDirection = FacingDirection.WEST;
		}
		else
		{
			outputDirection = FacingDirection.DOWN;
		}
		return outputDirection;
	}


	private final int[] _twoDRotationMatrix;
	private final Function<AbsoluteLocation, AbsoluteLocation> _sinkLocation;

	private FacingDirection(int[] twoDRotationMatrix, Function<AbsoluteLocation, AbsoluteLocation> sinkLocation)
	{
		_twoDRotationMatrix = twoDRotationMatrix;
		_sinkLocation = sinkLocation;
	}

	/**
	 * Given an "in" location in the NORTH orientation, this will rotate that position, about the Z-axis and origin,
	 * into the orientation of the receiver.
	 * 
	 * @param in The input orientation in NORTH orientation (default).
	 * @return The rotated orientation.
	 */
	public AbsoluteLocation rotateAboutZ(AbsoluteLocation in)
	{
		int x = _twoDRotationMatrix[0] * in.x() + _twoDRotationMatrix[1] * in.y();
		int y = _twoDRotationMatrix[2] * in.x() + _twoDRotationMatrix[3] * in.y();
		return new AbsoluteLocation(x, y, in.z());
	}

	/**
	 * Given an "in" X/Y tuple in the NORTH orientation, this will rotate that position, about the Z-axis and origin,
	 * into the orientation of the receiver.
	 * 
	 * @param in The input orientation in NORTH orientation (default).
	 * @return The rotated orientation.
	 */
	public float[] rotateXYTupleAboutZ(float[] in)
	{
		float x = (float)_twoDRotationMatrix[0] * in[0] + (float)_twoDRotationMatrix[1] * in[1];
		float y = (float)_twoDRotationMatrix[2] * in[0] + (float)_twoDRotationMatrix[3] * in[1];
		return new float[] { x, y };
	}

	public AbsoluteLocation getOutputBlockLocation(AbsoluteLocation thisLocation)
	{
		return _sinkLocation.apply(thisLocation);
	}

	public FacingDirection rotateOrientation(FacingDirection orientation)
	{
		FacingDirection rotated;
		if (DOWN == orientation)
		{
			rotated = orientation;
		}
		else
		{
			int index = (orientation.ordinal() + this.ordinal()) % 4;
			rotated = FacingDirection.values()[index];
		}
		return rotated;
	}
}
