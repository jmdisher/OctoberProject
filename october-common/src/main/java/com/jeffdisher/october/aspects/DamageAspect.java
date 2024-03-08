package com.jeffdisher.october.aspects;


/**
 * Contains constants and helpers associated with the damage aspect.
 */
public class DamageAspect
{
	/**
	 * We are limited to 15 bits to store the damage so we just fix the maximum at a round 32000.
	 */
	public static final short MAX_DAMAGE = 32000;

	/**
	 * The durability of items which CANNOT exist as blocks in the world.
	 */
	public static final short NOT_BLOCK = -1;

	/**
	 * Blocks which either can't be broken or don't make sense to break.
	 */
	public static final short UNBREAKABLE = 0;

	/**
	 * Very weak blocks which are trivial to break.
	 */
	public static final short TRIVIAL = 20;

	/**
	 * Common weak blocks.
	 */
	public static final short WEAK = 200;

	/**
	 * Common medium toughness blocks.
	 */
	public static final short MEDIUM = 2000;

	/**
	 * Common hard toughness blocks.
	 */
	public static final short HARD = 8000;

	/**
	 * Exceptionally strong blocks.
	 */
	public static final short STRONG = 20000;
}
