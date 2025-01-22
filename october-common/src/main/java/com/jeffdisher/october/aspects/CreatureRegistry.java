package com.jeffdisher.october.aspects;

import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.EntityVolume;


/**
 * Contains the description of the livestock and monsters in the game.
 */
public class CreatureRegistry
{
	// TODO:  Remove these constants once we have generalized the callers.
	public final EntityType PLAYER = new EntityType((byte)1
			, "op.player"
			, "PLAYER"
			, new EntityVolume(0.9f, 0.4f)
			, 4.0f
			, (byte)100
	);
	public final EntityType COW = new EntityType((byte)2
			, "op.cow"
			, "COW"
			, new EntityVolume(0.7f, 0.8f)
			, 2.0f
			, (byte)40
	);
	public final EntityType ORC = new EntityType((byte)3
			, "op.orc"
			, "ORC"
			, new EntityVolume(0.7f, 0.4f)
			, 3.0f
			, (byte)20
	);

	// For historical reasons, there is a limit of 254 entity types, where 0 is reserved as an error value.
	public final EntityType[] ENTITY_BY_NUMBER = { null
			, PLAYER
			, COW
			, ORC
	};
}
