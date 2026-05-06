package com.jeffdisher.october.client;


/**
 * The direction of a horizontal move, relative to the orientation.
 * We assume that the client is digital, meaning it can only move in 8 directions (4 single-key moves and 8
 * double-key moves).
 */
public enum RelativeDirection
{
	FORWARD(_Multipliers.FORWARD, 0.0f),
	FORWARD_LEFT(_Multipliers.STRAFE, (float)(1.0 / 4.0 * Math.PI)),
	LEFT(_Multipliers.STRAFE, (float)(2.0 / 4.0 * Math.PI)),
	BACKWARD_LEFT(_Multipliers.BACKWARD, (float)(3.0 / 4.0 * Math.PI)),
	BACKWARD(_Multipliers.BACKWARD, (float)(4.0 / 4.0 * Math.PI)),
	BACKWARD_RIGHT(_Multipliers.BACKWARD, (float)(5.0 / 4.0 * Math.PI)),
	RIGHT(_Multipliers.STRAFE, (float)(6.0 / 4.0 * Math.PI)),
	FORWARD_RIGHT(_Multipliers.STRAFE, (float)(7.0 / 4.0 * Math.PI)),
	;
	public final float speedMultiplier;
	public final float yawRadians;
	private RelativeDirection(float speedMultiplier, float yawRadians)
	{
		this.speedMultiplier = speedMultiplier;
		this.yawRadians = yawRadians;
	}

	private static class _Multipliers
	{
		public static final float FORWARD = 1.0f;
		public static final float STRAFE = 0.8f;
		public static final float BACKWARD = 0.6f;
	}
}
