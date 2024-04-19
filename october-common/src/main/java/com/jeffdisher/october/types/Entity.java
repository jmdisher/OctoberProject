package com.jeffdisher.october.types;


/**
 * An Entity represents something which can move in the world.  This includes users, monsters, animals, and machines.
 * An Entity instance is immutable and is generally created through changes to EntityActionValidator.
 */
public record Entity(int id
		// Note that the location is the bottom, south-west corner of the space occupied by the entity and the volume extends from there.
		, EntityLocation location
		// We track the current z-velocity in blocks per second, up.
		, float zVelocityPerSecond
		, EntityVolume volume
		// The maximum distance, in blocks, the entity can move in a single tick (float since this is usually less than 1).
		, float blocksPerTickSpeed
		, Inventory inventory
		// If the selected item key is 0, there is no selection.
		, int selectedItemKey
		// This is typically null but is used in the case where the entity is currently crafting something.
		, CraftOperation localCraftOperation
		// The health value of the entity.  Currently, we just use a byte since it is in the range of [1..100].
		, byte health
		// The food level stored within the entity.  Currently, we just use a byte since it is in the range of [0..100].
		, byte food
)
{
	/**
	 * The selected item key for no selection.  All actual IDs are positive integers.
	 */
	public static final int NO_SELECTION = 0;

	public static Entity fromPartial(PartialEntity entity)
	{
		return new Entity(entity.id()
				, entity.location()
				, entity.zVelocityPerSecond()
				, entity.volume()
				, 0.0f
				, Inventory.start(0).finish()
				, NO_SELECTION
				, null
				, (byte)0
				, (byte)0
		);
	}
}
