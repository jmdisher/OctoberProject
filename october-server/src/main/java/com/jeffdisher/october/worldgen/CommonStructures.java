package com.jeffdisher.october.worldgen;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.properties.PropertyRegistry;
import com.jeffdisher.october.properties.PropertyType;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.FacingDirection;
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

	public final Structure nexusCastle;
	public final Structure distanceTower;

	private final Environment _env;
	private final Map<String, NonStackableItem> _namedItems;

	public CommonStructures(Environment env) throws IOException
	{
		ClassLoader classLoader = getClass().getClassLoader();
		
		Block airBlock = env.blocks.fromItem(env.items.getItemById("op.air"));
		Block stoneBlock = env.blocks.fromItem(env.items.getItemById("op.stone"));
		Block stoneBrickBlock = env.blocks.fromItem(env.items.getItemById("op.stone_brick"));
		Block dirtBlock = env.blocks.fromItem(env.items.getItemById("op.dirt"));
		Block grassBlock = env.blocks.fromItem(env.items.getItemById("op.grass"));
		Block tilledSoilBlock = env.blocks.fromItem(env.items.getItemById("op.tilled_soil"));
		Block torchBlock = env.blocks.fromItem(env.items.getItemById("op.torch"));
		Block wheatBlock = env.blocks.fromItem(env.items.getItemById("op.wheat_seedling"));
		Block carrotBlock = env.blocks.fromItem(env.items.getItemById("op.carrot_seedling"));
		Block saplingBlock = env.blocks.fromItem(env.items.getItemById("op.sapling"));
		Block waterSourceBlock = env.blocks.fromItem(env.items.getItemById("op.water_source"));
		Block voidStoneBlock = env.blocks.fromItem(env.items.getItemById("op.portal_stone"));
		Block doorBlock = env.blocks.fromItem(env.items.getItemById("op.door"));
		Block portalKeystoneBlock = env.blocks.fromItem(env.items.getItemById("op.portal_keystone"));
		Block pedestalBlock = env.blocks.fromItem(env.items.getItemById("op.pedestal"));
		
		_env = env;
		_namedItems = new HashMap<>();
		try (InputStream stream  = classLoader.getResourceAsStream("common_structures.tablist"))
		{
			TabListReader.readEntireFile(this, stream);
		}
		catch (TabListReader.TabListException e)
		{
			// TODO:  Determine a better way to handle this.
			throw Assert.unexpected(e);
		}
		
		NonStackableItem northOrb = _getRequiredItem("north_orb");
		NonStackableItem southOrb = _getRequiredItem("south_orb");
		NonStackableItem eastOrb = _getRequiredItem("east_orb");
		NonStackableItem westOrb = _getRequiredItem("west_orb");
		NonStackableItem reverseOrb = _getRequiredItem("reverse_orb");
		
		NonStackableItem specialPick = _getRequiredItem("special_pickaxe");
		NonStackableItem specialShovel = _getRequiredItem("special_shovel");
		NonStackableItem specialAxe = _getRequiredItem("special_axe");
		NonStackableItem specialSword = _getRequiredItem("special_sword");
		
		Map<Character, Structure.AspectData> mapping = new HashMap<>();
		Assert.assertTrue(null == mapping.put('A', new Structure.AspectData(airBlock, null, null, null, null)));
		Assert.assertTrue(null == mapping.put('#', new Structure.AspectData(stoneBlock, null, null, null, null)));
		Assert.assertTrue(null == mapping.put('B', new Structure.AspectData(stoneBrickBlock, null, null, null, null)));
		Assert.assertTrue(null == mapping.put('D', new Structure.AspectData(dirtBlock, null, null, null, null)));
		Assert.assertTrue(null == mapping.put('G', new Structure.AspectData(grassBlock, null, null, null, null)));
		Assert.assertTrue(null == mapping.put('i', new Structure.AspectData(torchBlock, null, null, null, null)));
		Assert.assertTrue(null == mapping.put('w', new Structure.AspectData(wheatBlock, null, null, null, null)));
		Assert.assertTrue(null == mapping.put('c', new Structure.AspectData(carrotBlock, null, null, null, null)));
		Assert.assertTrue(null == mapping.put('t', new Structure.AspectData(saplingBlock, null, null, null, null)));
		Assert.assertTrue(null == mapping.put('O', new Structure.AspectData(tilledSoilBlock, null, null, null, null)));
		Assert.assertTrue(null == mapping.put('T', new Structure.AspectData(waterSourceBlock, null, null, null, null)));
		Assert.assertTrue(null == mapping.put('V', new Structure.AspectData(voidStoneBlock, null, null, null, null)));
		Assert.assertTrue(null == mapping.put('[', new Structure.AspectData(doorBlock, null, FacingDirection.NORTH, null, null)));
		Assert.assertTrue(null == mapping.put(']', new Structure.AspectData(doorBlock, null, null, null, new AbsoluteLocation(0, 0, -1))));
		Assert.assertTrue(null == mapping.put('N', new Structure.AspectData(portalKeystoneBlock, null, FacingDirection.NORTH, ItemSlot.fromNonStack(northOrb), null)));
		Assert.assertTrue(null == mapping.put('S', new Structure.AspectData(portalKeystoneBlock, null, FacingDirection.SOUTH, ItemSlot.fromNonStack(southOrb), null)));
		Assert.assertTrue(null == mapping.put('E', new Structure.AspectData(portalKeystoneBlock, null, FacingDirection.EAST, ItemSlot.fromNonStack(eastOrb), null)));
		Assert.assertTrue(null == mapping.put('W', new Structure.AspectData(portalKeystoneBlock, null, FacingDirection.WEST, ItemSlot.fromNonStack(westOrb), null)));
		Assert.assertTrue(null == mapping.put('R', new Structure.AspectData(portalKeystoneBlock, null, FacingDirection.NORTH, ItemSlot.fromNonStack(reverseOrb), null)));
		Assert.assertTrue(null == mapping.put('1', new Structure.AspectData(pedestalBlock, null, null, ItemSlot.fromNonStack(specialPick), null)));
		Assert.assertTrue(null == mapping.put('2', new Structure.AspectData(pedestalBlock, null, null, ItemSlot.fromNonStack(specialShovel), null)));
		Assert.assertTrue(null == mapping.put('3', new Structure.AspectData(pedestalBlock, null, null, ItemSlot.fromNonStack(specialAxe), null)));
		Assert.assertTrue(null == mapping.put('4', new Structure.AspectData(pedestalBlock, null, null, ItemSlot.fromNonStack(specialSword), null)));
		
		StructureLoader loader = new StructureLoader(mapping);
		try (InputStream stream  = classLoader.getResourceAsStream("nexus_castle.structure"))
		{
			String[] zLayers = StructureLoader.splitStreamIntoZLayerStrings(stream);
			this.nexusCastle = loader.loadFromStrings(zLayers);
		}
		try (InputStream stream  = classLoader.getResourceAsStream("distance_tower.structure"))
		{
			String[] zLayers = StructureLoader.splitStreamIntoZLayerStrings(stream);
			this.distanceTower = loader.loadFromStrings(zLayers);
		}
	}

	private String _currentRef;
	private Item _item;
	private AbsoluteLocation _relativeLocation;
	private String _name;
	private byte _enchantDurability;
	private byte _enchantEfficiency;
	private byte _enchantMelee;

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
		}
		else
		{
			throw new TabListReader.TabListException("Unknown stanza: \"" + name + "\"");
		}
	}
	@Override
	public void endRecord() throws TabListReader.TabListException
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
	@Override
	public void processSubRecord(String name, String[] parameters) throws TabListReader.TabListException
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

	private NonStackableItem _getRequiredItem(String name)
	{
		NonStackableItem item = _namedItems.get(name);
		Assert.assertTrue(null != item);
		return item;
	}
}
