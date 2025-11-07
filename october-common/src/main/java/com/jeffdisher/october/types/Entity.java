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
		// Yaw is measured from [-128..127] where 0 is "North" and positive values move to the "left" (counter-clockwise, from above).
		, byte yaw
		// Pitch is measured from [-64..64] where 0 is "level", -64 is "straight down", and 64 is "straight up".
		, byte pitch
		// The normal inventory of the entity (hotbar slots reference this but armour slots do NOT overlap with this).
		, Inventory inventory
		// The keys in the hotbar are references to inventory.  If any are 0, they have no selection.
		, int[] hotbarItems
		// hotbarIndex is always [0..HOTBAR_SIZE).
		, int hotbarIndex
		// The armour slots don't count as part of the inventory so they keep the non-stackables inline.
		, NonStackableItem[] armourSlots
		// The health value of the entity.  Currently, we just use a byte since it is in the range of [1..100].
		, byte health
		// The food level stored within the entity.  Currently, we just use a byte since it is in the range of [0..100].
		, byte food
		// The breath the entity has (for drowning).
		, byte breath
		// The location where the entity is sent when they spawn for the first time or die and respawn.
		, EntityLocation spawnLocation
		
		// We have some ephemeral data which is still passed to the client, just never persisted to disk.
		, Ephemeral_Shared ephemeralShared
		
		// We have some private ephemeral data which is server-local and never passed to the client or stored to disk.
		, Ephemeral_Local ephemeralLocal
)
{
	public static final int HOTBAR_SIZE = 9;
	/**
	 * The selected item key for no selection.  All actual IDs are positive integers.
	 * Note that we assume this is 0 in various places (like int[] initialization) so this is just to add meaning to
	 * code which would otherwise just have an inline constant 0.
	 */
	public static final int NO_SELECTION = 0;

	/**
	 * The empty ephemeral shared data used when loading a new instance.
	 */
	public static final Ephemeral_Shared EMPTY_SHARED = new Ephemeral_Shared(null
		, 0
	);

	/**
	 * The empty ephemeral local data used when loading a new instance.
	 */
	public static final Ephemeral_Local EMPTY_LOCAL = new Ephemeral_Local(0L
		, 0L
		, 0
	);

	/**
	 * All data stored in this class is shared with the client but never persisted to disk.
	 */
	public static record Ephemeral_Shared(
		// This is typically null but is used in the case where the entity is currently crafting something.
		CraftOperation localCraftOperation
		// The number of milliseconds accumulated in "charging" a weapon.  Usually 0 as this is reset by many things.
		, int chargeMillis
	) {}

	/**
	 * All data stored in this class is considered ephemeral and local:  It is not persisted, nor sent over the network.
	 */
	public static record Ephemeral_Local(
		// The last millisecond when the entity took a special action.
		long lastSpecialActionMillis
		// The millisecond time when this entity last took damage.
		, long lastDamageTakenMillis
		// The energy deficit is used as an intermediary to decide when to consume food.  It changes in response to many actions.
		, int energyDeficit
	) {}
}
