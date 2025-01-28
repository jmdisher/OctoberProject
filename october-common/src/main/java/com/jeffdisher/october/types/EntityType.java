package com.jeffdisher.october.types;


/**
 * This is used closely by CreatureRegistry to determine server-side behaviour but also client-side audio, etc.
 */
public record EntityType(byte number
		, String id
		, String name
		, EntityVolume volume
		, float blocksPerSecond
		, byte maxHealth
		, float viewDistance
		, float actionDistance
		, byte attackDamage
		, Items[] drops
		, Item breedingItem
)
{
	/**
	 * @return The distance an entity is willing to path in order to reach a target.
	 */
	public float getPathDistance()
	{
		// Use 2x the view distance to account for obstacles.
		return 2.0f * this.viewDistance;
	}

	/**
	 * @return True if this kind of creature can passively despawn when inactive (ie:  not a livestock type).
	 */
	public boolean canDespawn()
	{
		// Anything which can't breed can despawn.
		return (null == this.breedingItem);
	}

	/**
	 * @return True if this is a livestock type (ie:  can be bread).
	 */
	public boolean isLivestock()
	{
		// Anything which has a breedable item is considered livestock.
		return (null != this.breedingItem);
	}

	/**
	 * @return True if this is a hostile creater (ie:  does damage to the player).
	 */
	public boolean isHostile()
	{
		return (this.attackDamage > 0);
	}
}
