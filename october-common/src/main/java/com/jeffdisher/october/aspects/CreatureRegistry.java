package com.jeffdisher.october.aspects;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.october.config.TabListReader;
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
	private static final String SUB_VOLUME = "volume";
	private static final String SUB_BLOCKS_PER_SECOND = "blocks_per_second";
	private static final String SUB_MAX_HEALTH = "max_health";
	private static final String SUB_VIEW_DISTANCE = "view_distance";
	private static final String SUB_ACTION_DISTANCE = "action_distance";
	private static final String SUB_DROPS = "drops";
	private static final String SUB_OPT_MELEE = "melee";
	private static final String SUB_OPT_BREEDING = "breeding";
	private static final String SUB_OPT_HOSTILE = "hostile";

	private static final byte FAKE_PLAYER_NUMBER = 1;
	private static final String FAKE_PLAYER_ID = "op.player";

	/**
	 * Loads the creature registry from the tablist in the given stream, sourcing Items from the given items registry.
	 * 
	 * @param items The existing ItemRegistry.
	 * @param stream The stream containing the tablist.
	 * @return The registry (never null).
	 * @throws IOException There was a problem with the stream.
	 * @throws TabListReader.TabListException The tablist was malformed.
	 */
	public static CreatureRegistry loadRegistry(ItemRegistry items, InputStream stream) throws IOException, TabListReader.TabListException
	{
		if (null == stream)
		{
			throw new IOException("Resource missing");
		}
		Map<String, EntityType> creatureById = new HashMap<>();
		EntityType player = _packageEntity(FAKE_PLAYER_NUMBER
			, FAKE_PLAYER_ID
			, "PLAYER"
			, new EntityVolume(0.9f, 0.4f)
			, 4.0f
			, (byte)100
			, 0.0f
			, 0.0f
			, Set.of()
			, (byte)0
			, null
			, null
			, new CreatureExtendedData.NullCodec()
		);
		creatureById.put(FAKE_PLAYER_ID, player);
		
		TabListReader.readEntireFile(new TabListReader.IParseCallbacks() {
			private String _id = null;
			private String _name = null;
			private EntityVolume _volume = null;
			private float _blocksPerSecond = -1.0f;
			private byte _maxHealth = -1;
			private float _viewDistance = -1.0f;
			private float _actionDistance = -1.0f;
			private Set<EntityType> _hostileTargets = new HashSet<>();
			private byte _attackDamage = 0;
			private Items[] _drops = null;
			private Item _breedingItem = null;
			
			@Override
			public void startNewRecord(String name, String[] parameters) throws TabListReader.TabListException
			{
				Assert.assertTrue(null == _id);
				if (creatureById.containsKey(name))
				{
					throw new TabListReader.TabListException("Duplicate ID: " + name);
				}
				_id = name;
				if (1 != parameters.length)
				{
					throw new TabListReader.TabListException("Name missing: " + name);
				}
				_name = parameters[0];
			}
			@Override
			public void endRecord() throws TabListReader.TabListException
			{
				Assert.assertTrue(null != _id);
				Assert.assertTrue(null != _name);
				_assert(SUB_VOLUME, null != _volume);
				_assert(SUB_BLOCKS_PER_SECOND, _blocksPerSecond > 0.0f);
				_assert(SUB_MAX_HEALTH, _maxHealth > 0);
				_assert(SUB_VIEW_DISTANCE, _viewDistance > 0.0f);
				_assert(SUB_ACTION_DISTANCE, _actionDistance > 0.0f);
				// optional _attackDamage
				_assert(SUB_DROPS, null != _drops);
				// optional _breedingItem
				
				// Add 1 to the number since 0 is reserved as an error.
				Assert.assertTrue(creatureById.size() < 254);
				byte number = (byte)(creatureById.size() + 1);
				EntityType type = _packageEntity(number
						, _id
						, _name
						, _volume
						, _blocksPerSecond
						, _maxHealth
						, _viewDistance
						, _actionDistance
						, _hostileTargets
						, _attackDamage
						, _drops
						, _breedingItem
						, new CreatureExtendedData.NullCodec()
				);
				creatureById.put(_id, type);
				
				_id = null;
				_name = null;
				_volume = null;
				_blocksPerSecond = -1.0f;
				_maxHealth = -1;
				_viewDistance = -1.0f;
				_actionDistance = -1.0f;
				_attackDamage = 0;
				_drops = null;
				_breedingItem = null;
			}
			@Override
			public void processSubRecord(String name, String[] parameters) throws TabListReader.TabListException
			{
				Assert.assertTrue(null != _id);
				// See which of the sublists this is an enter the correct state.
				if (SUB_VOLUME.equals(name))
				{
					if (2 != parameters.length)
					{
						throw new TabListReader.TabListException(_id + ": Expected 2 parameters for " + SUB_VOLUME);
					}
					float height = _getFloat(parameters[0]);
					float width = _getFloat(parameters[1]);
					_volume = new EntityVolume(height, width);
				}
				else if (SUB_BLOCKS_PER_SECOND.equals(name))
				{
					if (1 != parameters.length)
					{
						throw new TabListReader.TabListException(_id + ": Expected 1 parameter for " + SUB_BLOCKS_PER_SECOND);
					}
					_blocksPerSecond = _getFloat(parameters[0]);
				}
				else if (SUB_MAX_HEALTH.equals(name))
				{
					if (1 != parameters.length)
					{
						throw new TabListReader.TabListException(_id + ": Expected 1 parameter for " + SUB_MAX_HEALTH);
					}
					_maxHealth = _getByte(parameters[0]);
				}
				else if (SUB_VIEW_DISTANCE.equals(name))
				{
					if (1 != parameters.length)
					{
						throw new TabListReader.TabListException(_id + ": Expected 1 parameter for " + SUB_VIEW_DISTANCE);
					}
					_viewDistance = _getFloat(parameters[0]);
				}
				else if (SUB_ACTION_DISTANCE.equals(name))
				{
					if (1 != parameters.length)
					{
						throw new TabListReader.TabListException(_id + ": Expected 1 parameter for " + SUB_ACTION_DISTANCE);
					}
					_actionDistance = _getFloat(parameters[0]);
				}
				else if (SUB_DROPS.equals(name))
				{
					if (2 != parameters.length)
					{
						throw new TabListReader.TabListException(_id + ": Expected 2 parameters for " + SUB_DROPS);
					}
					Item item = _getItem(parameters[0]);
					int count = _getInt(parameters[1]);
					_drops = new Items[] { new Items(item, count) };
				}
				else if (SUB_OPT_MELEE.equals(name))
				{
					if (1 != parameters.length)
					{
						throw new TabListReader.TabListException(_id + ": Expected 1 parameter for " + SUB_OPT_MELEE);
					}
					_attackDamage = _getByte(parameters[0]);
				}
				else if (SUB_OPT_BREEDING.equals(name))
				{
					if (1 != parameters.length)
					{
						throw new TabListReader.TabListException(_id + ": Expected 1 parameter for " + SUB_OPT_BREEDING);
					}
					_breedingItem = _getItem(parameters[0]);
				}
				else if (SUB_OPT_HOSTILE.equals(name))
				{
					if (0 == parameters.length)
					{
						throw new TabListReader.TabListException(_id + ": Expected at least 1 parameter for " + SUB_OPT_HOSTILE);
					}
					for (String param : parameters)
					{
						EntityType target = creatureById.get(param);
						if (null == target)
						{
							throw new TabListReader.TabListException(_id + ": Specified invalid hostile target " + param);
						}
						_hostileTargets.add(target);
					}
				}
				else
				{
					throw new TabListReader.TabListException("Unknown sub-record identifier: \"" + name + "\"");
				}
			}
			private Item _getItem(String id) throws TabListReader.TabListException
			{
				Item item = items.getItemById(id);
				if (null == item)
				{
					throw new TabListReader.TabListException("Unknown item: \"" + id + "\"");
				}
				return item;
			}
			private float _getFloat(String num) throws TabListReader.TabListException
			{
				float value;
				try
				{
					value = Float.parseFloat(num);
				}
				catch (NumberFormatException e)
				{
					throw new TabListReader.TabListException(_id + ": Not a float: \"" + num + "\"");
				}
				return value;
			}
			private int _getInt(String num) throws TabListReader.TabListException
			{
				int value;
				try
				{
					value = Integer.parseInt(num);
				}
				catch (NumberFormatException e)
				{
					throw new TabListReader.TabListException(_id + ": Not an int: \"" + num + "\"");
				}
				return value;
			}
			private byte _getByte(String num) throws TabListReader.TabListException
			{
				byte value;
				try
				{
					value = Byte.parseByte(num);
				}
				catch (NumberFormatException e)
				{
					throw new TabListReader.TabListException(_id + ": Not a byte: \"" + num + "\"");
				}
				return value;
			}
			private void _assert(String field, boolean check) throws TabListReader.TabListException
			{
				if (!check)
				{
					throw new TabListReader.TabListException(_id + ": Required field missing: " + field);
				}
			}
		}, stream);
		
		return new CreatureRegistry(items, player, creatureById);
	}


	public final EntityType PLAYER;

	// For historical reasons, there is a limit of 254 entity types, where 0 is reserved as an error value.
	public final EntityType[] ENTITY_BY_NUMBER;
	private final Map<String, EntityType> _mapByIds;
	/**
	 * Hostile mobs are exposed as a dense array so it can be indexed for spawning.
	 */
	public final EntityType[] HOSTILE_MOBS;

	private CreatureRegistry(ItemRegistry items, EntityType player, Map<String, EntityType> creatureById)
	{
		this.PLAYER = player;
		ENTITY_BY_NUMBER = new EntityType[creatureById.size() + 1];
		for (EntityType type : creatureById.values())
		{
			ENTITY_BY_NUMBER[type.number()] = type;
		}
		
		// For historical reasons, there is a limit of 254 entity types, where 0 is reserved as an error value.
		Assert.assertTrue(null == this.ENTITY_BY_NUMBER[0]);
		Assert.assertTrue(this.ENTITY_BY_NUMBER.length <= 254);
		
		_mapByIds = Collections.unmodifiableMap(creatureById);
		this.HOSTILE_MOBS = _mapByIds.values().stream()
				.filter((EntityType type) -> type.hostileTargets().contains(this.PLAYER))
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
			, Set<EntityType> hostileTargets
			, byte attackDamage
			, Items[] drops
			, Item breedingItem
			, EntityType.IExtendedCodec extendedCodec
	)
	{
		return new EntityType(number
				, id
				, name
				, volume
				, blocksPerSecond
				, maxHealth
				, viewDistance
				, actionDistance
				, hostileTargets
				, attackDamage
				, drops
				, breedingItem
				, extendedCodec
		);
	}
}
