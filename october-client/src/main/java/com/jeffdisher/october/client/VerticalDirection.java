package com.jeffdisher.october.client;


/**
 * Enum to describe creative flight directions in the Z-axis.
 * This is only used by the CreativeFlightAccumulator but is its own type because it is exposed by the public interface.
 */
public enum VerticalDirection
{
	LEVEL(0.0f),
	UP(1.0f),
	DOWN(-1.0f),
	;
	public final float z;
	private VerticalDirection(float z)
	{
		this.z = z;
	}
}
