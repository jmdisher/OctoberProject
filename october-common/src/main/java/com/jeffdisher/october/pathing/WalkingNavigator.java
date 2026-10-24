package com.jeffdisher.october.pathing;


public class WalkingNavigator
{
	/**
	 * An enum describing the block kinds which are relevant for basic walking navigation.
	 */
	public static enum BlockKind
	{
		/**
		 * A block which can be traversed easily.
		 */
		WALKABLE,
		/**
		 * A block which cannot be passed through and which supports an entity.
		 */
		SOLID,
		/**
		 * A block which is "WALKABLE" but also allows swimming (and is not breathable).
		 */
		SWIMMABLE,
	}
}
