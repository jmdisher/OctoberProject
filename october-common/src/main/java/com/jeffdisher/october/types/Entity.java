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
		// The maximum distance, in blocks, the entity can move in a single tick (float since this is usually less than 1).
		, float blocksPerTickSpeed
		, Inventory inventory
		// The keys in the hotbar are references to inventory.  If any are 0, they have no selection.
		, int[] hotbarItems
		// hotbarIndex is always [0..HOTBAR_SIZE).
		, int hotbarIndex
		// The armour slots don't count as part of the inventory so they keep the non-stackables inline.
		, NonStackableItem[] armourSlots
		// This is typically null but is used in the case where the entity is currently crafting something.
		, CraftOperation localCraftOperation
		// The health value of the entity.  Currently, we just use a byte since it is in the range of [1..100].
		, byte health
		// The food level stored within the entity.  Currently, we just use a byte since it is in the range of [0..100].
		, byte food
		// The energy deficit is used as an intermediary to decide when to consume food.  It changes in response to many actions.
		, int energyDeficit
)
{
	public static final int HOTBAR_SIZE = 9;
	/**
	 * The selected item key for no selection.  All actual IDs are positive integers.
	 * Note that we assume this is 0 in various places (like int[] initialization) so this is just to add meaning to
	 * code which would otherwise just have an inline constant 0.
	 */
	public static final int NO_SELECTION = 0;

	public static Entity fromPartial(PartialEntity entity)
	{
		return new Entity(entity.id()
				, entity.location()
				, entity.zVelocityPerSecond()
				, 0.0f
				, Inventory.start(0).finish()
				, new int[HOTBAR_SIZE]
				, 0
				, new NonStackableItem[BodyPart.values().length]
				, null
				, (byte)0
				, (byte)0
				, 0
		);
	}
}
