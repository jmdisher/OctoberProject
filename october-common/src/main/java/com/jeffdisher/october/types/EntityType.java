package com.jeffdisher.october.types;

import java.nio.ByteBuffer;

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
		, EntityType adultType
		, IExtendedCodec extendedCodec
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
		return (null == this.breedingItem) && (null == this.adultType);
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
	 * @return True if this is a hostile creature which does melee damage.
	 */
	public boolean isHostileMelee()
	{
		return (this.attackDamage > 0);
	}

	/**
	 * @return True if this is a hostile creature which does ranged damage using a bow and arrow.
	 */
	public boolean isHostileRanged()
	{
		return (-1 == this.attackDamage);
	}

	/**
	 * @return True if this is the baby version of an adult creature.
	 */
	public boolean isBaby()
	{
		return (null != this.adultType);
	}


	/**
	 * The codec used by the type-specific extendedData since it is persisted and passed over the network.
	 */
	public static interface IExtendedCodec
	{
		/**
		 * Used to create a default/empty instance of the extended data.
		 * 
		 * @param gameTimeMillis The most recent game time, in case the instance needs to track relative timeouts, etc.
		 * @return A reasonable default extended data instance.
		 */
		public Object buildDefault(long gameTimeMillis);
		/**
		 * Reads the extended data from the given buffer.
		 * 
		 * @param buffer The buffer (disk or network).
		 * @param gameTimeMillis The most recent game time, in case the instance needs to track relative timeouts, etc.
		 * @return The deserialized extended data instance.
		 */
		public Object read(ByteBuffer buffer, long gameTimeMillis);
		/**
		 * Writes the extended data to the given buffer.
		 * 
		 * @param buffer The buffer (disk or network).
		 * @param extendedData The extended data instance to serialize.
		 * @param gameTimeMillis The most recent game time, in case the instance needs to store relative timeouts, etc.
		 */
		public void write(ByteBuffer buffer, Object extendedData, long gameTimeMillis);
	}
}
