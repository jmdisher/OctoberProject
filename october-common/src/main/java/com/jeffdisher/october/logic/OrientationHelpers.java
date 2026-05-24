package com.jeffdisher.october.logic;

import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.FacingDirection;


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
		return _yawFromRadians(radians);
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

	public static FacingDirection getYawDirection(byte yaw)
	{
		// The positive yaw is to the left (counter-clockwise, looking down from positive Z), so map these to orientations.
		int positive = Byte.toUnsignedInt(yaw) + 31;
		FacingDirection direction;
		if (positive > 192)
		{
			direction = FacingDirection.EAST;
		}
		else if (positive > 128)
		{
			direction = FacingDirection.SOUTH;
		}
		else if (positive > 64)
		{
			direction = FacingDirection.WEST;
		}
		else
		{
			direction = FacingDirection.NORTH;
		}
		return direction;
	}

	/**
	 * Determines the yaw byte to describe an entity looking from source to target.
	 * 
	 * @param source The source of the ray.
	 * @param target The end of the ray.
	 * @return The yaw byte for this facing direction.
	 */
	public static byte getYawBetweenPoints(EntityLocation source, EntityLocation target)
	{
		float deltaX = target.x() - source.x();
		float deltaY = target.y() - source.y();
		
		byte yaw;
		if (0.0f == deltaY)
		{
			yaw = (deltaX > 0.0f)
				? YAW_EAST
				: YAW_WEST
			;
		}
		else
		{
			// NOTE:  The ratio is expected to be from the north to the west, so invert the X delta.
			float ratio = -deltaX / deltaY;
			float radians = (float)Math.atan(ratio);
			if (deltaY < 0.0f)
			{
				// If the delta Y shows it is facing south, add half a rotation so we get an answer from the south half of the circle.
				radians += Math.PI;
			}
			yaw = _yawFromRadians(radians);
		}
		return yaw;
	}


	private static float _positiveRadians(float radians)
	{
		while (radians < 0.0f)
		{
			radians += (float)(2.0 * Math.PI);
		}
		return radians;
	}

	private static byte _yawFromRadians(float radians)
	{
		float portionOfCircle = (float) (radians / (2.0 * Math.PI));
		float yawFloat = 256.0f * portionOfCircle;
		return (byte)(int)Math.round(yawFloat);
	}
}
