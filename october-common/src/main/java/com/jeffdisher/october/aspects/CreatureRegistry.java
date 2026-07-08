package com.jeffdisher.october.aspects;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.creatures.ExtensionHostileMelee;
import com.jeffdisher.october.creatures.ExtensionHostileRanged;
import com.jeffdisher.october.creatures.ExtensionLivestock;
import com.jeffdisher.october.creatures.ExtensionLivestockBaby;
import com.jeffdisher.october.creatures.ExtensionPlayer;
import com.jeffdisher.october.creatures.ExtensionVillager;
import com.jeffdisher.october.types.DropChance;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.Item;
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
	private static final String SUB_ACTION_COOLDOWN_MILLIS = "action_cooldown_millis";
	private static final String SUB_DROPS = "drops";
	private static final String SUB_EXTENSION = "extension";

	// These are the valid values for SUB_EXTENSION.
	private static final String EXTENSION_NAME_LIVESTOCK = "livestock";
	private static final String EXTENSION_NAME_LIVESTOCK_BABY = "livestock_baby";
	private static final String EXTENSION_NAME_HOSTILE_MELEE = "hostile_melee";
	private static final String EXTENSION_NAME_HOSTILE_RANGED = "hostile_ranged";
	private static final String EXTENSION_NAME_VILLAGER = "villager";

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
		Map<String, EntityType> typesById = new HashMap<>();
		List<EntityType> creatures = new ArrayList<>();
		Map<EntityType, EntityType> babyTypeByAdultType = new HashMap<>();
		Set<EntityType> breedableTypesToVerify = new HashSet<>();
		List<EntityType> dynamicSpawns = new ArrayList<>();
		
		TabListReader.readEntireFile(new TabListReader.IParseCallbacks() {
			private String _id = null;
			private String _name = null;
			private EntityVolume _volume = null;
			private float _blocksPerSecond = -1.0f;
			private byte _maxHealth = -1;
			private float _viewDistance = -1.0f;
			private float _actionDistance = -1.0f;
			private int _actionCooldownMillis = 0;
			private DropChance[] _drops = null;
			private EntityType.IExtension _extension = null;
			private boolean _canSpawnDynamically = false;
			private boolean _isBreedable = false;
			private EntityType _adultToValidate = null;
			
			@Override
			public void startNewRecord(String name, String[] parameters) throws TabListReader.TabListException
			{
				Assert.assertTrue(null == _id);
				if (typesById.containsKey(name))
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
				_assert(SUB_ACTION_COOLDOWN_MILLIS, _actionCooldownMillis > 0);
				_assert(SUB_DROPS, null != _drops);
				_assert(SUB_EXTENSION, null != _extension);
				
				// Add 2 to the number since 0 is reserved as an error and 1 is for the player.
				Assert.assertTrue(creatures.size() < 253);
				byte number = (byte)(creatures.size() + 2);
				
				EntityType type = new EntityType(number
						, _id
						, _name
						, _volume
						, _blocksPerSecond
						, _maxHealth
						, _viewDistance
						, _actionDistance
						, _actionCooldownMillis
						, _drops
						, _extension
				);
				typesById.put(_id, type);
				if (_isBreedable)
				{
					// If this has a breeding item, it better have offspring (described later in the file).
					breedableTypesToVerify.add(type);
				}
				if (null != _adultToValidate)
				{
					// Note that a type cannot be both adult and baby at the same time.
					babyTypeByAdultType.put(_adultToValidate, type);
				}
				creatures.add(type);
				if (_canSpawnDynamically)
				{
					dynamicSpawns.add(type);
				}
				
				_id = null;
				_name = null;
				_volume = null;
				_blocksPerSecond = -1.0f;
				_maxHealth = -1;
				_viewDistance = -1.0f;
				_actionDistance = -1.0f;
				_actionCooldownMillis = 0;
				_drops = null;
				_extension = null;
				_canSpawnDynamically = false;
				_isBreedable = false;
				_adultToValidate = null;
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
				else if (SUB_ACTION_COOLDOWN_MILLIS.equals(name))
				{
					if (1 != parameters.length)
					{
						throw new TabListReader.TabListException(_id + ": Expected 1 parameter for " + SUB_ACTION_COOLDOWN_MILLIS);
					}
					_actionCooldownMillis = _getInt(parameters[0]);
				}
				else if (SUB_DROPS.equals(name))
				{
					// Note that duplicates are expected in this parameter list (empty also makes sense).
					// This list is always in pairs (probability<TAB>item).
					if (0 != (parameters.length % 2))
					{
						throw new TabListReader.TabListException(_id + ": Drop parameters must be in pairs for " + SUB_DROPS);
					}
					int pairCount = parameters.length / 2;
					DropChance[] drops = new DropChance[pairCount];
					for (int i = 0; i < pairCount; ++i)
					{
						Item item = _getItem(parameters[2 * i + 1]);
						int probability = _getInt(parameters[2 * i]);
						drops[i] = new DropChance(item, probability);
					}
					_drops = drops;
				}
				else if (SUB_EXTENSION.equals(name))
				{
					// The extension requires that we parse each special-type as a logical white-list with special parameters.
					if (0 == parameters.length)
					{
						throw new TabListReader.TabListException(_id + ": Extension name missing");
					}
					String extensionName = parameters[0];
					if (extensionName.equals(EXTENSION_NAME_LIVESTOCK))
					{
						if (2 != parameters.length)
						{
							throw new TabListReader.TabListException(_id + ": livestock extension requires breeding item, only");
						}
						Item breedingItem = _getItem(parameters[1]);
						_extension = new ExtensionLivestock(breedingItem);
						_isBreedable = true;
					}
					else if (extensionName.equals(EXTENSION_NAME_LIVESTOCK_BABY))
					{
						if (2 != parameters.length)
						{
							throw new TabListReader.TabListException(_id + ": livestock_baby extension requires adult type, only");
						}
						EntityType adultType = typesById.get(parameters[1]);
						_extension = new ExtensionLivestockBaby(adultType);
						_adultToValidate = adultType;
					}
					else if (extensionName.equals(EXTENSION_NAME_HOSTILE_MELEE))
					{
						if (2 != parameters.length)
						{
							throw new TabListReader.TabListException(_id + ": hostile_melee extension requires attack damage, only");
						}
						byte attackDamage = _getByte(parameters[1]);
						_extension = new ExtensionHostileMelee(attackDamage);
						_canSpawnDynamically = true;
					}
					else if (extensionName.equals(EXTENSION_NAME_HOSTILE_RANGED))
					{
						if (1 != parameters.length)
						{
							throw new TabListReader.TabListException(_id + ": hostile_ranged extension has no parameter");
						}
						_extension = new ExtensionHostileRanged();
						_canSpawnDynamically = true;
					}
					else if (extensionName.equals(EXTENSION_NAME_VILLAGER))
					{
						if (1 != parameters.length)
						{
							throw new TabListReader.TabListException(_id + ": villager extension has no parameter");
						}
						_extension = new ExtensionVillager();
						_isBreedable = true;
					}
					else
					{
						throw new TabListReader.TabListException(_id + ": unknown extension type: \"" + extensionName + "\"");
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
		
		// Verify our data is consistent.
		for (EntityType parent : breedableTypesToVerify)
		{
			if (!babyTypeByAdultType.containsKey(parent))
			{
				throw new TabListReader.TabListException("No type declares this breedable type as its parent: \"" + parent.id() + "\"");
			}
		}
		
		return new CreatureRegistry(items
			, typesById
			, creatures
			, babyTypeByAdultType
			, dynamicSpawns
		);
	}


	public final EntityType PLAYER;

	// For historical reasons, there is a limit of 254 entity types, where 0 is reserved as an error value.
	public final EntityType[] ENTITY_BY_NUMBER;
	private final Map<String, EntityType> _mapByIds;
	private final Map<EntityType, EntityType> _babyTypeByAdultType;
	/**
	 * Hostile mobs are exposed as a dense array so it can be indexed for spawning.
	 */
	public final EntityType[] HOSTILE_MOBS;

	private CreatureRegistry(ItemRegistry items
		, Map<String, EntityType> mapByIds
		, List<EntityType> creatures
		, Map<EntityType, EntityType> babyTypeByAdultType
		, List<EntityType> dynamicSpawns
	)
	{
		this.PLAYER = new EntityType((byte)1
				, FAKE_PLAYER_ID
				, "PLAYER"
				, new EntityVolume(1.7f, 0.4f)
				, 4.0f
				, (byte)100
				, 0.0f
				, 0.0f
				, 0
				, null
				, new ExtensionPlayer()
		);
		ENTITY_BY_NUMBER = new EntityType[creatures.size() + 2];
		ENTITY_BY_NUMBER[1] = this.PLAYER;
		int index = 2;
		for (EntityType type : creatures)
		{
			Assert.assertTrue(type.number() == index);
			ENTITY_BY_NUMBER[index] = type;
			index += 1;
		}
		
		// For historical reasons, there is a limit of 254 entity types, where 0 is reserved as an error value.
		Assert.assertTrue(null == this.ENTITY_BY_NUMBER[0]);
		Assert.assertTrue(this.ENTITY_BY_NUMBER.length <= 254);
		
		_mapByIds = Collections.unmodifiableMap(mapByIds);
		_babyTypeByAdultType = Collections.unmodifiableMap(babyTypeByAdultType);
		this.HOSTILE_MOBS = dynamicSpawns.stream()
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

	/**
	 * Returns the offspring EntityType for a given parentType.  Note that the parentType MUST have a valid offspring
	 * type in order to call this function.
	 * 
	 * @param parentType The type of entity which is to spawn offspring.
	 * @return The offspring type (never null).
	 */
	public EntityType getOffspringType(EntityType parentType)
	{
		// We should only be calling this if it can be a parent.
		Assert.assertTrue(_babyTypeByAdultType.containsKey(parentType));
		return _babyTypeByAdultType.get(parentType);
	}
}
