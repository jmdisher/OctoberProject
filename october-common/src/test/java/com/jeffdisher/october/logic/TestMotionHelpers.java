package com.jeffdisher.october.logic;

import org.junit.Assert;
import org.junit.Test;


public class TestMotionHelpers
{
	@Test
	public void basicAccelerate()
	{
		float startingVelocity = 0.0f;
		float finalVelocity = MotionHelpers.applyZAcceleration(startingVelocity, 1.0f);
		Assert.assertEquals(-9.8f, finalVelocity, 0.01f);
	}

	@Test
	public void basicMove()
	{
		float startingVelocity = 0.0f;
		float distance = MotionHelpers.applyZMovement(startingVelocity, 1.0f);
		Assert.assertEquals(-4.9f, distance, 0.01f);
	}

	@Test
	public void partialAccelerateVersusSum()
	{
		float startingVelocity = 0.0f;
		float velocity1 = MotionHelpers.applyZAcceleration(startingVelocity, 0.5f);
		float velocity2 = MotionHelpers.applyZAcceleration(velocity1, 0.5f);
		
		Assert.assertEquals(MotionHelpers.applyZAcceleration(startingVelocity, 1.0f), velocity2, 0.01f);
	}

	@Test
	public void partialMoveVersusSum()
	{
		float startingVelocity = 0.0f;
		float distance1 = MotionHelpers.applyZMovement(startingVelocity, 0.5f);
		float velocity1 = MotionHelpers.applyZAcceleration(startingVelocity, 0.5f);
		float distance2 = MotionHelpers.applyZMovement(velocity1, 0.5f);
		
		Assert.assertEquals(MotionHelpers.applyZMovement(startingVelocity, 1.0f), distance1 + distance2, 0.01f);
	}
}
