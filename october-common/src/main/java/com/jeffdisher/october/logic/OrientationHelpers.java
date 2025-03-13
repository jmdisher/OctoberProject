package com.jeffdisher.october.logic;

import com.jeffdisher.october.aspects.OrientationAspect;


/**
 * Helpers to convert to/from yaw/pitch values.
 */
public class OrientationHelpers
{
	public static final byte YAW_NORTH = 0;
	public static final byte YAW_WEST = 64;
	public static final byte YAW_SOUTH = -128;
	public static final byte YAW_EAST = -64;
	public static final byte PITCH_FLAT = 0;
	public static final byte PITCH_UP = 64;
	public static final byte PITCH_DOWN = -64;

	public static float getYawRadians(byte yaw)
	{
		float yawFloat = (float)(int)yaw;
		float portionOfCircle = yawFloat / 256.0f;
		return _positiveRadians((float) (portionOfCircle * 2.0 * Math.PI));
	}

	public static float getPitchRadians(byte pitch)
	{
		float pitchFloat = (float)(int)pitch;
		float portionOfCircle = pitchFloat / 256.0f;
		return _positiveRadians((float) (portionOfCircle * 2.0 * Math.PI));
	}

	public static byte yawFromRadians(float radians)
	{
		float portionOfCircle = (float) (radians / (2.0 * Math.PI));
		float yawFloat = 256.0f * portionOfCircle;
		return (byte)(int)Math.round(yawFloat);
	}

	public static byte pitchFromRadians(float radians)
	{
		float portionOfCircle = (float) (radians / (2.0 * Math.PI));
		float pitchFloat = 256.0f * portionOfCircle;
		return (byte)(int)Math.round(pitchFloat);
	}

	public static float getEastYawComponent(float yawRadians)
	{
		// Note that the positive X is to the east but the positive yaw is to the left so we need to invert this.
		float component = (float) Math.sin(yawRadians);
		return -component;
	}

	public static float getNorthYawComponent(float yawRadians)
	{
		return (float) Math.cos(yawRadians);
	}

	public static OrientationAspect.Direction getYawDirection(byte yaw)
	{
		// The positive yaw is to the left (counter-clockwise, looking down from positive Z), so map these to orientations.
		int positive = Byte.toUnsignedInt(yaw) + 31;
		OrientationAspect.Direction direction;
		if (positive > 192)
		{
			direction = OrientationAspect.Direction.EAST;
		}
		else if (positive > 128)
		{
			direction = OrientationAspect.Direction.SOUTH;
		}
		else if (positive > 64)
		{
			direction = OrientationAspect.Direction.WEST;
		}
		else
		{
			direction = OrientationAspect.Direction.NORTH;
		}
		return direction;
	}


	private static float _positiveRadians(float radians)
	{
		while (radians < 0.0f)
		{
			radians += (float)(2.0 * Math.PI);
		}
		return radians;
	}
}
