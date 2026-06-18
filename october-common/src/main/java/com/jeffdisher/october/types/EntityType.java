package com.jeffdisher.october.types;

import java.nio.ByteBuffer;

import com.jeffdisher.october.logic.EntityCollection;


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
	, DropChance[] drops
	, Item breedingItem
	, EntityType adultType
	, IExtension extension
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
	 * An interface which exposes mechanisms for serializing the per-type extended data but also handles the per-type
	 * special logic.
	 */
	public static interface IExtension
	{
		/**
		 * Used to create a default/empty instance of the extended data.
		 * 
		 * @param gameTimeMillis The most recent game time, in case the instance needs to track relative timeouts, etc.
		 * @return A reasonable default extended data instance.
		 */
		public Object buildDefaultExtendedData(long gameTimeMillis);
		/**
		 * Reads the extended data from the given buffer.
		 * 
		 * @param buffer The buffer (disk or network).
		 * @param gameTimeMillis The most recent game time, in case the instance needs to track relative timeouts, etc.
		 * @return The deserialized extended data instance.
		 */
		public Object readExtendedData(ByteBuffer buffer, long gameTimeMillis);
		/**
		 * Writes the extended data to the given buffer.
		 * 
		 * @param buffer The buffer (disk or network).
		 * @param extendedData The extended data instance to serialize.
		 * @param gameTimeMillis The most recent game time, in case the instance needs to store relative timeouts, etc.
		 */
		public void writeExtendedData(ByteBuffer buffer, Object extendedData, long gameTimeMillis);
		
		/**
		 * Finds a target for deliberate path creation, returning null if one couldn't be found.
		 * 
		 * @param creature The MutableCreature instance.
		 * @param entityCollection The current entities in the world.
		 * @return The target entity.
		 */
		public TargetEntity findDeliberateTarget(MutableCreature creature, EntityCollection entityCollection);
		/**
		 * Checks if the current target entity is valid (the implementation can assume one is selected).
		 * 
		 * @param creature The MutableCreature instance.
		 * @param entityCollection The current entities in the world.
		 * @return True if the target is valid or false if it doesn't exist or is no longer a valid target.
		 */
		public boolean isTargetValid(MutableCreature creature, EntityCollection entityCollection);
		/**
		 * Called at the beginning of a tick to allow the creature to validate its plan and target, potentially taking
		 * a special action instead of continuing with the normal movement attempt.
		 * 
		 * @param creature The MutableCreature instance.
		 * @param context The current tick context.
		 * @return True if a special action was taken (or if normal movement should be skipped for any reason).
		 */
		public boolean didTakeSpecialAction(MutableCreature creature, TickProcessingContext context);
		/**
		 * Called when a livestock animal is being set pregnant, returning whether the animal's state changed.
		 * 
		 * @param creature The MutableCreature instance.
		 * @param sireLocation The base location of the entity which sent the request.
		 * @param gameTimeMillis The current game millisecond time.
		 * @return True if the creature became pregnant.
		 */
		public boolean setCreaturePregnant(MutableCreature creature, EntityLocation sireLocation, long gameTimeMillis);
		/**
		 * Called to check if the given creature should despawn within the current tick.  Note that despawning doesn't
		 * drop inventory/loot.
		 * 
		 * @param creature The MutableCreature instance.
		 * @param context The current tick context.
		 * @return True if creature should be despawned within the current tick.
		 */
		public boolean shouldDespawn(MutableCreature creature, TickProcessingContext context);
		/**
		 * Checks if an item can be directly applied to a creature without modifying it.
		 * 
		 * @param creature The read-only partial instance.
		 * @param itemType The item type to apply.
		 * @param gameTimeMillis The current game millisecond time.
		 * @return True if this item can be applied to the given creature.
		 */
		public boolean canApplyItemToCreature(PartialEntity creature, Item itemType, long gameTimeMillis);
		/**
		 * Applies an item directly to the creature, returning whether was successfully applied.
		 * 
		 * @param creature The MutableCreature instance.
		 * @param itemType The item type to apply.
		 * @param gameTimeMillis The current game millisecond time.
		 * @return True if this item was applied successfully.
		 */
		public boolean applyItemToCreature(MutableCreature creature, Item itemType, long gameTimeMillis);
	}

	/**
	 * A special tuple type used by IBehaviourTemplate.
	 */
	public static record TargetEntity(int id, EntityLocation location) {}
}
