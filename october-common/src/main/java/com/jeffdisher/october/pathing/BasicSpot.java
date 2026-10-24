package com.jeffdisher.october.pathing;

import com.jeffdisher.october.types.AbsoluteLocation;


/**
 * The implementation of ASpot which handles the basics needed for walking.
 */
public class BasicSpot extends PathFinder.ASpot
{
	public BasicSpot(AbsoluteLocation location, float distance)
	{
		super(location, distance);
	}
}
