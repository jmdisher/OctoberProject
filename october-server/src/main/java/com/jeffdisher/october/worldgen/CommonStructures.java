package com.jeffdisher.october.worldgen;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.config.TabListReader.TabListException;
import com.jeffdisher.october.properties.PropertyRegistry;
import com.jeffdisher.october.properties.PropertyType;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.utils.Assert;


/**
 * This class parses the data files which define the common structures used in the world generators and stores the final
 * results for look-up.
 */
public class CommonStructures implements TabListReader.IParseCallbacks
{
	public static final int CASTLE_X =  -8;
	public static final int CASTLE_Y =  -8;
	public static final int CASTLE_Z = -10;
	public static final int TOWER_NORTH_X = -5;
	public static final int TOWER_NORTH_Y = 1002;
	public static final int TOWER_SOUTH_X =  5;
	public static final int TOWER_SOUTH_Y = -1002;
	public static final int TOWER_EAST_X = 1002;
	public static final int TOWER_EAST_Y = 5;
	public static final int TOWER_WEST_X =  -1002;
	public static final int TOWER_WEST_Y = -5;
	public static final int TOWER_Z = -6;

	public static final String STANZA_ITEM_DEF = "item_def";
	public static final String KEY_ITEM = "item";
	public static final String KEY_LOCATION_REL = "location_rel";
	public static final String KEY_NAME = "name";
	public static final String KEY_ENCHANT_DURABILITY = "enchant_durability";
	public static final String KEY_ENCHANT_EFFICIENCY = "enchant_efficiency";
	public static final String KEY_ENCHANT_MELEE = "enchant_melee";

	public static final String STANZA_BLOCK_DEF = "block_def";
	public static final String KEY_BLOCK = "block";
	public static final String KEY_FACING_DIRECTION = "facing_direction";
	public static final String KEY_ITEM_SLOT = "item_slot";
	public static final String KEY_ROOT_REL = "root_rel";

	public final Structure nexusCastle;
	public final Structure distanceTower;
	public final Structure basicTree;
	public final Structure coalNode;
	public final Structure copperNode;
	public final Structure ironNode;
	public final Structure diamondNode;

	private final Environment _env;
	private final Map<String, NonStackableItem> _namedItems;
	private final Map<Character, Structure.AspectData> _blockMapping;

	public CommonStructures(Environment env) throws IOException
	{
		ClassLoader classLoader = getClass().getClassLoader();
		
		_env = env;
		_namedItems = new HashMap<>();
		_blockMapping = new HashMap<>();
		try (InputStream stream  = classLoader.getResourceAsStream("common_structures.tablist"))
		{
			TabListReader.readEntireFile(this, stream);
		}
		catch (TabListReader.TabListException e)
		{
			// TODO:  Determine a better way to handle this.
			throw Assert.unexpected(e);
		}
		
		StructureLoader loader = new StructureLoader(_blockMapping);
		this.nexusCastle = _loadStructureResource(classLoader, loader, "nexus_castle.structure");
		this.distanceTower = _loadStructureResource(classLoader, loader, "distance_tower.structure");
		this.basicTree = _loadStructureResource(classLoader, loader, "basic_tree.structure");
		this.coalNode = _loadStructureResource(classLoader, loader, "coal_node.structure");
		this.copperNode = _loadStructureResource(classLoader, loader, "copper_node.structure");
		this.ironNode = _loadStructureResource(classLoader, loader, "iron_node.structure");
		this.diamondNode = _loadStructureResource(classLoader, loader, "diamond_node.structure");
	}

	private String _mode;

	// Item state.
	private String _currentRef;
	private Item _item;
	private AbsoluteLocation _relativeLocation;
	private String _name;
	private byte _enchantDurability;
	private byte _enchantEfficiency;
	private byte _enchantMelee;

	// Block state.
	private char _currentBlockRef;
	private Block _block;
	private FacingDirection _facingDirection;
	private NonStackableItem _itemSlot;
	private AbsoluteLocation _rootRelative;

	@Override
	public void startNewRecord(String name, String[] parameters) throws TabListReader.TabListException
	{
		if (STANZA_ITEM_DEF.equals(name))
		{
			if (1 != parameters.length)
			{
				throw new TabListReader.TabListException(STANZA_ITEM_DEF + " requires 1 parameter");
			}
			String refName = parameters[0];
			if (_namedItems.containsKey(refName))
			{
				throw new TabListReader.TabListException("Duplicate item ref: \"" + refName + "\"");
			}
			_currentRef = refName;
			_mode = STANZA_ITEM_DEF;
		}
		else if (STANZA_BLOCK_DEF.equals(name))
		{
			if ((1 != parameters.length) || (1 != parameters[0].length()))
			{
				throw new TabListReader.TabListException(STANZA_BLOCK_DEF + " requires 1 parameter with only 1 character");
			}
			char refName = parameters[0].charAt(0);
			if (_blockMapping.containsKey(refName))
			{
				throw new TabListReader.TabListException("Duplicate block ref: \"" + refName + "\"");
			}
			_currentBlockRef = refName;
			_mode = STANZA_BLOCK_DEF;
		}
		else
		{
			throw new TabListReader.TabListException("Unknown stanza: \"" + name + "\"");
		}
	}
	@Override
	public void endRecord() throws TabListReader.TabListException
	{
		if (STANZA_ITEM_DEF == _mode)
		{
			_endItem();
		}
		else if (STANZA_BLOCK_DEF == _mode)
		{
			_endBlock();
		}
		else
		{
			Assert.unreachable();
		}
		_mode = null;
	}
	@Override
	public void processSubRecord(String name, String[] parameters) throws TabListReader.TabListException
	{
		if (STANZA_ITEM_DEF == _mode)
		{
			_itemSubRecord(name, parameters);
		}
		else if (STANZA_BLOCK_DEF == _mode)
		{
			_blockSubRecord(name, parameters);
		}
		else
		{
			Assert.unreachable();
		}
	}


	private void _endItem()
	{
		Map<PropertyType<?>, Object> properties = new HashMap<>();
		properties.put(PropertyRegistry.DURABILITY, _env.durability.getDurability(_item));
		if (null != _relativeLocation)
		{
			properties.put(PropertyRegistry.LOCATION, _relativeLocation);
		}
		if (null != _name)
		{
			properties.put(PropertyRegistry.NAME, _name);
		}
		if (0 != _enchantDurability)
		{
			properties.put(PropertyRegistry.ENCHANT_DURABILITY, _enchantDurability);
		}
		if (0 != _enchantEfficiency)
		{
			properties.put(PropertyRegistry.ENCHANT_TOOL_EFFICIENCY, _enchantEfficiency);
		}
		if (0 != _enchantMelee)
		{
			properties.put(PropertyRegistry.ENCHANT_WEAPON_MELEE, _enchantMelee);
		}
		
		NonStackableItem special = new NonStackableItem(_item, Collections.unmodifiableMap(properties));
		_namedItems.put(_currentRef, special);
		
		_currentRef = null;
		_item = null;
		_relativeLocation = null;
		_name = null;
		_enchantDurability = 0;
		_enchantEfficiency = 0;
		_enchantMelee = 0;
	}

	private void _endBlock() throws TabListReader.TabListException
	{
		if (null == _block)
		{
			throw new TabListReader.TabListException("Missing block type: \"" + _currentBlockRef + "\"");
		}
		
		// Check the various rules.
		boolean requiresFacing = _env.orientations.doesSingleBlockRequireOrientation(_block) || (_env.blocks.isMultiBlock(_block) && (null == _rootRelative));
		if ((null != _facingDirection) != requiresFacing)
		{
			throw new TabListReader.TabListException("Inconsistent facing: \"" + _currentBlockRef + "\"");
		}
		if ((null != _itemSlot) && !_env.specialSlot.hasSpecialSlot(_block))
		{
			throw new TabListReader.TabListException("Block should not have an item slot: \"" + _currentBlockRef + "\"");
		}
		if ((null != _rootRelative) && !_env.blocks.isMultiBlock(_block))
		{
			throw new TabListReader.TabListException("Only multi-blocks can have root relative locations: \"" + _currentBlockRef + "\"");
		}
		
		// The reader can currently only handle null inventories.
		Inventory normalInventory = null;
		ItemSlot specialItemSlot = (null != _itemSlot)
			? ItemSlot.fromNonStack(_itemSlot)
			: null
		;
		Structure.AspectData aspectData = new Structure.AspectData(_block
			, normalInventory
			, _facingDirection
			, specialItemSlot
			, _rootRelative
		);
		_blockMapping.put(_currentBlockRef, aspectData);
		
		_currentBlockRef = '\0';
		_block = null;
		_facingDirection = null;
		_itemSlot = null;
		_rootRelative = null;
	}

	private void _itemSubRecord(String name, String[] parameters) throws TabListException
	{
		if (KEY_ITEM.equals(name))
		{
			_checkValidSubRecord(KEY_ITEM, _item, 1, parameters);
			_item = _env.items.getItemById(parameters[0]);
			if (null == _item)
			{
				throw new TabListReader.TabListException("Invalid " + KEY_ITEM + " under \"" + _currentRef + "\": \"" + parameters[0] + "\"");
			}
			if (_env.durability.isStackable(_item))
			{
				throw new TabListReader.TabListException(_item + " is a stackable item under \"" + _currentRef + "\"");
			}
		}
		else if (KEY_LOCATION_REL.equals(name))
		{
			_checkValidSubRecord(KEY_LOCATION_REL, _relativeLocation, 3, parameters);
			try
			{
				_relativeLocation = new AbsoluteLocation(Integer.parseInt(parameters[0]), Integer.parseInt(parameters[1]), Integer.parseInt(parameters[2]));
			}
			catch (NumberFormatException e)
			{
				throw new TabListReader.TabListException("Invalid " + KEY_LOCATION_REL + " under \"" + _currentRef + "\": " + Arrays.toString(parameters));
			}
		}
		else if (KEY_NAME.equals(name))
		{
			_checkValidSubRecord(KEY_NAME, _name, 1, parameters);
			_name = parameters[0];
		}
		else if (KEY_ENCHANT_DURABILITY.equals(name))
		{
			_enchantDurability = _readByteSubRecord(KEY_ENCHANT_DURABILITY, _enchantDurability, parameters);
		}
		else if (KEY_ENCHANT_EFFICIENCY.equals(name))
		{
			_enchantEfficiency = _readByteSubRecord(KEY_ENCHANT_EFFICIENCY, _enchantEfficiency, parameters);
		}
		else if (KEY_ENCHANT_MELEE.equals(name))
		{
			_enchantMelee = _readByteSubRecord(KEY_ENCHANT_MELEE, _enchantMelee, parameters);
		}
		else
		{
			throw new TabListReader.TabListException("Unknown sub-record key: \"" + name + "\"");
		}
	}

	private void _blockSubRecord(String name, String[] parameters) throws TabListException
	{
		if (KEY_BLOCK.equals(name))
		{
			_checkValidSubRecord(KEY_BLOCK, _block, 1, parameters);
			_block = _env.blocks.fromItem(_env.items.getItemById(parameters[0]));
			if (null == _block)
			{
				throw new TabListReader.TabListException("Invalid " + KEY_BLOCK + " under \"" + _currentBlockRef + "\": \"" + parameters[0] + "\"");
			}
		}
		else if (KEY_FACING_DIRECTION.equals(name))
		{
			_checkValidSubRecord(KEY_FACING_DIRECTION, _facingDirection, 1, parameters);
			try
			{
				_facingDirection = FacingDirection.valueOf(parameters[0]);
			}
			catch (IllegalArgumentException e)
			{
				throw new TabListReader.TabListException("Invalid " + KEY_FACING_DIRECTION + " under \"" + _currentBlockRef + "\": \"" + parameters[0] + "\"");
			}
		}
		else if (KEY_ITEM_SLOT.equals(name))
		{
			_checkValidSubRecord(KEY_ITEM_SLOT, _itemSlot, 1, parameters);
			_itemSlot = _namedItems.get(parameters[0]);
			if (null == _itemSlot)
			{
				throw new TabListReader.TabListException("Unknown item for " + KEY_ITEM_SLOT + " under \"" + _currentBlockRef + "\": \"" + parameters[0] + "\"");
			}
		}
		else if (KEY_ROOT_REL.equals(name))
		{
			_checkValidSubRecord(KEY_ROOT_REL, _rootRelative, 3, parameters);
			try
			{
				_rootRelative = new AbsoluteLocation(Integer.parseInt(parameters[0]), Integer.parseInt(parameters[1]), Integer.parseInt(parameters[2]));
			}
			catch (NumberFormatException e)
			{
				throw new TabListReader.TabListException("Invalid " + KEY_ROOT_REL + " under \"" + _currentBlockRef + "\": " + Arrays.toString(parameters));
			}
		}
		else
		{
			throw new TabListReader.TabListException("Unknown sub-record key: \"" + name + "\"");
		}
	}

	private void _checkValidSubRecord(String key, Object existing, int requiredCount, String[] parameters) throws TabListReader.TabListException
	{
		if (requiredCount != parameters.length)
		{
			throw new TabListReader.TabListException(key + " requires " + requiredCount + " parameter");
		}
		if (null != existing)
		{
			throw new TabListReader.TabListException("Duplicate " + key + " under \"" + _currentRef + "\"");
		}
	}

	private byte _readByteSubRecord(String key, byte existing, String[] parameters) throws TabListReader.TabListException
	{
		if (1 != parameters.length)
		{
			throw new TabListReader.TabListException(key + " requires 1 parameter");
		}
		if (0 != existing)
		{
			throw new TabListReader.TabListException("Duplicate " + key + " under \"" + _currentRef + "\"");
		}
		byte value;
		try
		{
			value = Byte.parseByte(parameters[0]);
			if (value < 0)
			{
				throw new TabListReader.TabListException(key + " must be positive byte under \"" + _currentRef + "\"");
			}
		}
		catch (NumberFormatException e)
		{
			throw new TabListReader.TabListException("Invalid " + key + " under \"" + _currentRef + "\": \"" + parameters[0] + "\"");
		}
		return value;
	}
	private static Structure _loadStructureResource(ClassLoader classLoader, StructureLoader loader, String fileName) throws IOException
	{
		Structure structure;
		try (InputStream stream  = classLoader.getResourceAsStream(fileName))
		{
			String[] zLayers = StructureLoader.splitStreamIntoZLayerStrings(stream);
			structure = loader.loadFromStrings(zLayers);
		}
		return structure;
	}
}
