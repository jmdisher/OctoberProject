package com.jeffdisher.october.types;


public record EventRecord(Type type
		, Cause cause
		, AbsoluteLocation location
		, int entityTarget
		, int entitySource
) {
	public enum Type
	{
		BLOCK_BROKEN,
		BLOCK_PLACED,
		/**
		 * entityTarget took damage from entitySource (may be 0 if environmental).
		 */
		ENTITY_HURT,
		/**
		 * entityTarget killed by entitySource (may be 0 if environmental).
		 */
		ENTITY_KILLED,
		LIQUID_REMOVED,
		LIQUID_PLACED,
		/**
		 * Both entityTarget and entitySource set to the entity ID.
		 */
		ENTITY_ATE_FOOD,
		/**
		 * entityTarget picked it up and entitySource is the now-gone passive.
		 */
		ENTITY_PICKED_UP_PASSIVE,
		/**
		 * The entityTarget and entitySource will be set to the entity ID.
		 */
		CRAFT_IN_INVENTORY_COMPLETE,
		/**
		 * This type refers to crafting in a block, be in manually crafted or automatic/fueled crafting.
		 * The entityTarget and entitySource will be set to 0.
		 */
		CRAFT_IN_BLOCK_COMPLETE,
		ENCHANT_COMPLETE,
	}

	/**
	 * The cause is always NONE except when the type is ENTITY_HURT or ENTITY_KILLED.
	 */
	public enum Cause
	{
		NONE,
		ATTACKED,
		STARVATION,
		SUFFOCATION,
		FALL,
		/**
		 * A block touching the entity applies damage.
		 */
		BLOCK_DAMAGE,
	}
}
