package com.jeffdisher.october.aspects;

import com.jeffdisher.october.creatures.CowStateMachine;
import com.jeffdisher.october.creatures.OrcStateMachine;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.utils.Assert;


/**
 * Contains the description of the livestock and monsters in the game.
 */
public class CreatureRegistry
{
	// TODO:  Remove these constants once we have generalized the callers.
	public final EntityType PLAYER;
	public final EntityType COW;
	public final EntityType ORC;

	// For historical reasons, there is a limit of 254 entity types, where 0 is reserved as an error value.
	public final EntityType[] ENTITY_BY_NUMBER;

	public CreatureRegistry(ItemRegistry items)
	{
		this.PLAYER = new EntityType((byte)1
				, "op.player"
				, "PLAYER"
				, new EntityVolume(0.9f, 0.4f)
				, 4.0f
				, (byte)100
				, null
				, null
		);
		Item wheatItem = items.getItemById("op.wheat_item");
		this.COW = new EntityType((byte)2
				, "op.cow"
				, "COW"
				, new EntityVolume(0.7f, 0.8f)
				, 2.0f
				, (byte)40
				, wheatItem
				, (Object extendedData) -> {
					return new CowStateMachine(wheatItem, extendedData);
				}
		);
		this.ORC = new EntityType((byte)3
				, "op.orc"
				, "ORC"
				, new EntityVolume(0.7f, 0.4f)
				, 3.0f
				, (byte)20
				, null
				, (Object extendedData) -> {
					return new OrcStateMachine(extendedData);
				}
		);
		
		// For historical reasons, there is a limit of 254 entity types, where 0 is reserved as an error value.
		this.ENTITY_BY_NUMBER = new EntityType[] { null
				, PLAYER
				, COW
				, ORC
		};
		Assert.assertTrue(null == this.ENTITY_BY_NUMBER[0]);
		Assert.assertTrue(this.ENTITY_BY_NUMBER.length <= 254);
	}
}
