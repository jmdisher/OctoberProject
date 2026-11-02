package com.jeffdisher.october.pathing;


/**
 * The implementation of ASpot which handles the basics needed for walking.
 */
public class BasicSpot extends PathFinder.ASpot
{
	public final boolean isOnGround;
	public final float viscosity;
	public final boolean canContinueRise;

	public BasicSpot(PathLocation location
		, float distance
		, boolean isOnGround
		, float viscosity
		, boolean canContinueRise
	)
	{
		super(location, distance);
		this.isOnGround = isOnGround;
		this.viscosity = viscosity;
		this.canContinueRise = canContinueRise;
	}
}
