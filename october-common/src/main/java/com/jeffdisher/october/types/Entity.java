package com.jeffdisher.october.types;


/**
 * An Entity represents something which can move in the world.  This includes users, monsters, animals, and machines.
 */
public record Entity(int id
		// If this flag is set, the entity's inventory is ignored and it doesn't get hungry or take damage.
		, boolean isCreativeMode
		// Note that the location is the bottom, south-west corner of the space occupied by the entity and the volume extends from there.
		, EntityLocation location
		// We track the current entity velocity using an EntityLocation object since it is 3 orthogonal floats.
		// Note that horizontal movement is usually cancelled by friction within the same tick.
		, EntityLocation velocity
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
		// The breath the entity has (for drowning).
		, int breath
		// The energy deficit is used as an intermediary to decide when to consume food.  It changes in response to many actions.
		, int energyDeficit
		// The location where the entity is sent when they spawn for the first time or die and respawn.
		, EntityLocation spawnLocation
		
		// ***** Values below this point are considered ephemeral and local:  They are not persisted, nor send over the network.
		, long ephemeral_lastSpecialActionMillis
)
{
	public static final int HOTBAR_SIZE = 9;
	/**
	 * The selected item key for no selection.  All actual IDs are positive integers.
	 * Note that we assume this is 0 in various places (like int[] initialization) so this is just to add meaning to
	 * code which would otherwise just have an inline constant 0.
	 */
	public static final int NO_SELECTION = 0;
}
