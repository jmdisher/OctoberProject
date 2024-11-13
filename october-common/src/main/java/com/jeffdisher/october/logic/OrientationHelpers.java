package com.jeffdisher.october.logic;


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


	private static float _positiveRadians(float radians)
	{
		while (radians < 0.0f)
		{
			radians += (float)(2.0 * Math.PI);
		}
		return radians;
	}
}
