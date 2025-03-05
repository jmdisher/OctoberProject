package com.jeffdisher.october.aspects;

import com.jeffdisher.october.types.AbsoluteLocation;


/**
 * Defines data and helpers related to orientation within the world.
 * NOTE:  We count "facing positive Y" as "forward" so all other orientations rotate about that.  The reasoning for this
 * is to make this consistent with how we consider "north" (positive Y) to be the 0 yaw rotation.
 */
public class OrientationAspect
{
	public static enum Direction
	{
		// We want IDENTITY to be the "0" value since it will be the default in the aspect and the only orientation most blocks have.
		IDENTITY(new int[] {1, 0, 0, 1}),
		POS_X(new int[] {0, -1, 1, 0}),
		NEG_X(new int[] {0, 1, -1, 0}),
		POS_Y(new int[] {1, 0, 0, 1}),
		NEG_Y(new int[] {-1, 0, 0, -1}),
		;
		private final int[] _twoDRotationMatrix;
		private Direction(int[] twoDRotationMatrix)
		{
			_twoDRotationMatrix = twoDRotationMatrix;
		}
		public AbsoluteLocation rotateAboutZ(AbsoluteLocation in)
		{
			int x = _twoDRotationMatrix[0] * in.x() + _twoDRotationMatrix[1] * in.y();
			int y = _twoDRotationMatrix[2] * in.x() + _twoDRotationMatrix[3] * in.y();
			return new AbsoluteLocation(x, y, in.z());
		}
	}
}
