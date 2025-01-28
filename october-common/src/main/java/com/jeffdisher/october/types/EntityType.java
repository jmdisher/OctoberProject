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
	public float getPathDistance()
	{
		// Use 2x the view distance to account for obstacles.
		return 2.0f * this.viewDistance;
	}

	public boolean canDespawn()
	{
		// Anything which can't breed can despawn.
		return (null == this.breedingItem);
	}

	public boolean isLivestock()
	{
		// Anything which has a breedable item is considered livestock.
		return (null != this.breedingItem);
	}
}
