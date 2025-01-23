package com.jeffdisher.october.aspects;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.jeffdisher.october.creatures.CowStateMachine;
import com.jeffdisher.october.creatures.ICreatureStateMachine;
import com.jeffdisher.october.creatures.OrcStateMachine;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.utils.Assert;


/**
 * Contains the description of the livestock and monsters in the game.
 */
public class CreatureRegistry
{
	public final EntityType PLAYER;

	// For historical reasons, there is a limit of 254 entity types, where 0 is reserved as an error value.
	public final EntityType[] ENTITY_BY_NUMBER;
	private final Map<String, EntityType> _mapByIds;
	/**
	 * Hostile mobs are exposed as a dense array so it can be indexed for spawning.
	 */
	public final EntityType[] HOSTILE_MOBS;

	public CreatureRegistry(ItemRegistry items)
	{
		this.PLAYER = _packageEntity((byte)1
				, "op.player"
				, "PLAYER"
				, new EntityVolume(0.9f, 0.4f)
				, 4.0f
				, (byte)100
				, 0.0f
				, 0.0f
				, (byte)0
				, null
				, null
				, null
		);
		Item beef = items.getItemById("op.beef");
		EntityType cow = _packageEntity((byte)2
				, "op.cow"
				, "COW"
				, new EntityVolume(0.7f, 0.8f)
				, 2.0f
				, (byte)40
				, 7.0f
				, 1.0f
				, (byte)0
				, new Items[] { new Items(beef, 5) }
				, items.getItemById("op.wheat_item")
				, (EntityType type, Object extendedData) -> {
					return new CowStateMachine(type, extendedData);
				}
		);
		Item ironDust = items.getItemById("op.iron_dust");
		EntityType orc = _packageEntity((byte)3
				, "op.orc"
				, "ORC"
				, new EntityVolume(0.7f, 0.4f)
				, 3.0f
				, (byte)20
				, 8.0f
				, 1.0f
				, (byte)5
				, new Items[] { new Items(ironDust, 1) }
				, null
				, (EntityType type, Object extendedData) -> {
					return new OrcStateMachine(type, extendedData);
				}
		);
		
		// For historical reasons, there is a limit of 254 entity types, where 0 is reserved as an error value.
		this.ENTITY_BY_NUMBER = new EntityType[] { null
				, PLAYER
				, cow
				, orc
		};
		Assert.assertTrue(null == this.ENTITY_BY_NUMBER[0]);
		Assert.assertTrue(this.ENTITY_BY_NUMBER.length <= 254);
		
		_mapByIds = Map.of(this.PLAYER.id(), this.PLAYER
				, cow.id(), cow
				, orc.id(), orc
		);
		this.HOSTILE_MOBS = _mapByIds.values().stream()
				.filter((EntityType type) -> (type.attackDamage() > 0))
				.toArray((int size) -> new EntityType[size])
		;
	}

	/**
	 * Looks up an entity type by its named ID.
	 * 
	 * @param id The ID of an Item.
	 * @return The type or null if not known.
	 */
	public EntityType getTypeById(String id)
	{
		return _mapByIds.get(id);
	}


	private static EntityType _packageEntity(byte number
			, String id
			, String name
			, EntityVolume volume
			, float blocksPerSecond
			, byte maxHealth
			, float viewDistance
			, float actionDistance
			, byte attackDamage
			, Items[] drops
			, Item breedingItem
			, BiFunction<EntityType, Object, ICreatureStateMachine> stateMachineFactory
	)
	{
		// We need to use an indirect container to push this type into the factory.
		EntityType[] container = new EntityType[1];
		Function<Object, ICreatureStateMachine> innerFactory = (Object extendedData) -> {
			return stateMachineFactory.apply(container[0], extendedData);
		};
		container[0] = new EntityType(number
				, id
				, name
				, volume
				, blocksPerSecond
				, maxHealth
				, viewDistance
				, actionDistance
				, attackDamage
				, drops
				, breedingItem
				, innerFactory
		);
		return container[0];
	}
}
