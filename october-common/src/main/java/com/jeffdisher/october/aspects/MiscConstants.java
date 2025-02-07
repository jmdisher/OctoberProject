package com.jeffdisher.october.aspects;


/**
 * Constants for miscellaneous concerns.
 * In the future, some/all of these may be moved into some kind of data file.
 */
public class MiscConstants
{
	/**
	 * The distance limit for an entity interacting with a block.
	 * This can include things like break/place/craft/inventory/etc.
	 */
	public static final float REACH_BLOCK = 2.5f;
	/**
	 * The distance limit for an entity interacting with another entity.
	 * This can include things like attach/feed/interact/etc.
	 */
	public static final float REACH_ENTITY = 1.5f;

	/**
	 * The breath value an entity is given when it is in a breathable block.
	 */
	public static final byte MAX_BREATH = 100;
	/**
	 * Breath is lost at this rate when the entity is not in a breathable block.
	 */
	public static final byte SUFFOCATION_BREATH_PER_SECOND = 5;
	/**
	 * When breath has run out, this is the damage per second to the entity while not in a breathable block.
	 */
	public static final byte SUFFOCATION_DAMAGE_PER_SECOND = 10;

	/**
	 * The maximum food value a player can have.
	 */
	public static final byte PLAYER_MAX_FOOD = 100;
	/**
	 * If the player's food reaches zero, this is the starvation damage they will receieve, per second.
	 */
	public static final byte STARVATION_DAMAGE_PER_SECOND = 5;

	/**
	 * A given creature or entity can only take damage at most once every half a second.
	 */
	public static final long DAMAGE_TAKEN_TIMEOUT_MILLIS = 500L;
}
