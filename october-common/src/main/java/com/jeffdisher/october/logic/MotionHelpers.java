package com.jeffdisher.october.logic;

import com.jeffdisher.october.utils.Assert;


/**
 * Contains some helpers related to motion.
 * This is mostly just to give a concise place for this logic, and our associated constants, to live.
 * 
 * NOTE:  For simplicity (since horizontal and vertical are treated differently), the viscosity is only applied when
 * calculating displacement based on velocity and NOT during acceleration.
 */
public class MotionHelpers
{
	// Constants related to how we apply gravity and terminal velocity.
	public static final float GRAVITY_CHANGE_PER_SECOND = -9.8f;
	// (40 m/s seems to be a common free-fall velocity for sky-divers)
	public static final float FALLING_TERMINAL_VELOCITY_PER_SECOND = -40.0f;
	public static final float FLOAT_MILLIS_PER_SECOND = 1000.0f;

	/**
	 * Applies acceleration to the velocity, determining a final updated velocity after the given time interval.
	 * 
	 * @param initialZVelocityPerSecond The initial velocity.
	 * @param secondsInMotion The seconds of acceleration.
	 * @return The updated velocity, in blocks per second, capped at the terminal velocity.
	 */
	public static float applyZAcceleration(float initialZVelocityPerSecond, float secondsInMotion)
	{
		// NOTE:  For simplicity (since horizontal and vertical are treated differently), the viscosity is only applied when calculating displacement based on velocity and NOT during acceleration.
		// TODO:  This should probably be generalized into the correct physics model instead of treating the axes differently.
		float velocityIncrease = GRAVITY_CHANGE_PER_SECOND * secondsInMotion;
		float newZVelocityPerSecond = initialZVelocityPerSecond + velocityIncrease;
		// Verify terminal velocity (we only apply this to falling).
		// Note that we don't gradually slow acceleration, we immediately stop it at terminal velocity.
		if (newZVelocityPerSecond < FALLING_TERMINAL_VELOCITY_PER_SECOND)
		{
			newZVelocityPerSecond = FALLING_TERMINAL_VELOCITY_PER_SECOND;
		}
		return newZVelocityPerSecond;
	}

	/**
	 * Applies existing velocity and acceleration based on the seconds in motion.  Note that this will also account for
	 * the terminal velocity, meaning that acceleration will no longer be considered once that is reached.
	 * 
	 * @param initialZVelocityPerSecond The initial velocity.
	 * @param secondsInMotion The seconds of motion.
	 * @return The distance moved, in blocks.
	 */
	public static float applyZMovement(float initialZVelocityPerSecond, float secondsInMotion)
	{
		// NOTE:  This comparison appears backward since terminal velocity is negative, only applied to falling.
		Assert.assertTrue(FALLING_TERMINAL_VELOCITY_PER_SECOND <= initialZVelocityPerSecond);
		// We may need to account for this in 2 components: (1) before reaching terminal velocity, (2) after reaching terminal velocity.
		// Find when we would reach initial velocity.
		float deltaToTerminalVelocity = FALLING_TERMINAL_VELOCITY_PER_SECOND - initialZVelocityPerSecond;
		float secondsToTerminal = (deltaToTerminalVelocity / GRAVITY_CHANGE_PER_SECOND);
		
		float secondsAccelerating = Math.min(secondsInMotion, secondsToTerminal);
		float secondsConstant = Math.max(0.0f, secondsInMotion - secondsToTerminal);
		
		// The acceleration distance is:  d = vt + (1/2)at^2
		float distanceAccelerating = (initialZVelocityPerSecond * secondsAccelerating) + (0.5f * GRAVITY_CHANGE_PER_SECOND * secondsAccelerating * secondsAccelerating);
		
		// Constant distance is:  d = vt
		float distanceConstant = (initialZVelocityPerSecond * secondsConstant);
		
		return distanceAccelerating + distanceConstant;
	}
}
