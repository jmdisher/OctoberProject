package com.jeffdisher.october.aspects;

import com.jeffdisher.october.types.AbsoluteLocation;


/**
 * Defines data and helpers related to orientation within the world.
 * NOTE:  We count "facing positive Y" as "forward" so all other orientations rotate about that.  The reasoning for this
 * is to make this consistent with how we consider "north" (positive Y) to be the 0 yaw rotation.
 */
public class OrientationAspect
{
	public static byte directionToByte(Direction direction)
	{
		return (byte)direction.ordinal();
	}
	public static Direction byteToDirection(byte value)
	{
		return Direction.values()[value];
	}
	/**
	 * The direction will rotate a given location from NORTH to the given direction.
	 * These are in the order of positive yaw (to the left).
	 */
	public static enum Direction
	{
		// North is the positive Y direction, which we consider the "default" orientation (hence 0).
		NORTH(new int[] {1, 0, 0, 1}),
		// West is the negative X direction.
		WEST(new int[] {0, -1, 1, 0}),
		// South is the negative Y direction.
		SOUTH(new int[] {-1, 0, 0, -1}),
		// East is the positive X direction.
		EAST(new int[] {0, 1, -1, 0}),
		;
		private final int[] _twoDRotationMatrix;
		private Direction(int[] twoDRotationMatrix)
		{
			_twoDRotationMatrix = twoDRotationMatrix;
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
	}
}
