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
		// The selected item is either null of something which MUST have some items in the inventory.
		, Item selectedItem
		// This is typically null but is used in the case where the entity is currently crafting something.
		, CraftOperation localCraftOperation
		// The health value of the entity.  Currently, we just use a byte since it is in the range of [1..100].
		, byte health
		// The food level stored within the entity.  Currently, we just use a byte since it is in the range of [0..100].
		, byte food
) {
}
