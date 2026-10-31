package com.jeffdisher.october.pathing;


/**
 * The implementation of ASpot which handles the basics needed for walking.
 */
public class BasicSpot extends PathFinder.ASpot
{
	public final boolean isOnGround;
	public final float viscosity;

	public BasicSpot(PathLocation location
		, float distance
		, boolean isOnGround
		, float viscosity
	)
	{
		super(location, distance);
		this.isOnGround = isOnGround;
		this.viscosity = viscosity;
	}
}
