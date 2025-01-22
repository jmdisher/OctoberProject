package com.jeffdisher.october.aspects;

import com.jeffdisher.october.types.EntityType;


/**
 * Contains the description of the livestock and monsters in the game.
 */
public class CreatureRegistry
{
	// TODO:  Remove these constants once we have generalized the callers.
	public static final EntityType PLAYER = new EntityType((byte)1, "op.player", "PLAYER");
	public static final EntityType COW = new EntityType((byte)2, "op.cow", "COW");
	public static final EntityType ORC = new EntityType((byte)3, "op.orc", "ORC");

	// For historical reasons, there is a limit of 254 entity types, where 0 is reserved as an error value.
	public static final EntityType[] ENTITY_BY_NUMBER = { null
			, PLAYER
			, COW
			, ORC
	};
}
