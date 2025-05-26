package com.jeffdisher.october.types;


/**
 * The difficulty setting controls some of the behaviours of the system.  This is passed through the
 * TickProcessingContext so various parts of the system logic can use it.
 */
public enum Difficulty
{
	/**
	 * The default mode of the system where damage is normal and hostile entities spawn.
	 */
	HOSTILE,
	/**
	 * In peaceful mode, hostile mobs don't spawn and any which are loaded are immediately despawned.
	 */
	PEACEFUL,
}
