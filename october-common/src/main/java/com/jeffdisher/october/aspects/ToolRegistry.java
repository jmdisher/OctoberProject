package com.jeffdisher.october.aspects;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.october.config.IValueTransformer;
import com.jeffdisher.october.config.SimpleTabListCallbacks;
import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.config.TabListReader.TabListException;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.utils.Assert;


/**
 * Represents the subset of Item objects which are non-stackable, have durability, and have special uses.
 */
public class ToolRegistry
{
	public static final String FIELD_MATERIAL_MULTIPLIER = "material_multiplier";
	public static final String FIELD_WEAPON_DAMAGE = "weapon_damage";
	public static final String FIELD_CHARGE_MILLIS_MAX = "charge_millis_max";
	public static final String FIELD_AMMUNITION_TYPE = "ammunition_type";

	/**
	 * Loads the tool registry from the tablist in the given stream, sourcing Items from the given items registry.
	 * 
	 * @param items The existing ItemRegistry.
	 * @param stream The stream containing the tablist describing tool durabilities.
	 * @return The registry (never null).
	 * @throws IOException There was a problem with a stream.
	 * @throws TabListReader.TabListException A tablist was malformed.
	 */
	public static ToolRegistry load(ItemRegistry items
			, InputStream stream
	) throws IOException, TabListReader.TabListException
	{
		IValueTransformer<Item> keyTransformer = new IValueTransformer.ItemTransformer(items);
		IValueTransformer<BlockMaterial> valueTransformer = new IValueTransformer<>() {
			@Override
			public BlockMaterial transform(String value) throws TabListException
			{
				BlockMaterial material = BlockMaterial.valueOf(value);
				if (null == material)
				{
					throw new TabListReader.TabListException("Unknown constant for block_material: \"" + value + "\"");
				}
				return material;
			}
		};
		
		SimpleTabListCallbacks<Item, BlockMaterial> callbacks = new SimpleTabListCallbacks<>(keyTransformer, valueTransformer);
		SimpleTabListCallbacks.SubRecordCapture<Item, Integer> materialMutliplers = callbacks.captureSubRecord(FIELD_MATERIAL_MULTIPLIER, new IValueTransformer.IntegerTransformer(FIELD_MATERIAL_MULTIPLIER), true);
		SimpleTabListCallbacks.SubRecordCapture<Item, Integer> weaponDamage = callbacks.captureSubRecord(FIELD_WEAPON_DAMAGE, new IValueTransformer.IntegerTransformer(FIELD_WEAPON_DAMAGE), true);
		SimpleTabListCallbacks.SubRecordCapture<Item, Integer> chargeMillis = callbacks.captureSubRecord(FIELD_CHARGE_MILLIS_MAX, new IValueTransformer.IntegerTransformer(FIELD_CHARGE_MILLIS_MAX), false);
		SimpleTabListCallbacks.SubRecordCapture<Item, Item> ammoType = callbacks.captureSubRecord(FIELD_AMMUNITION_TYPE, new IValueTransformer.ItemTransformer(items), false);
		
		TabListReader.readEntireFile(callbacks, stream);
		
		// Note that we will, for now at least, require that charge and ammo always appear together.
		Set<Item> overlap = new HashSet<>(chargeMillis.recordData.keySet());
		overlap.retainAll(ammoType.recordData.keySet());
		Assert.assertTrue((chargeMillis.recordData.size() == ammoType.recordData.size())
			&& (overlap.size() == chargeMillis.recordData.size())
		);
		
		// We can just pass these in, directly.
		return new ToolRegistry(callbacks.topLevel, materialMutliplers.recordData, weaponDamage.recordData, chargeMillis.recordData, ammoType.recordData);
	}


	private final Map<Item, BlockMaterial> _blockMaterials;
	private final Map<Item, Integer> _materialMutliplers;
	private final Map<Item, Integer> _weaponDamage;
	private final Map<Item, Integer> _chargeMillis;
	private final Map<Item, Item> _ammoType;

	private ToolRegistry(Map<Item, BlockMaterial> blockMaterials
		, Map<Item, Integer> materialMutliplers
		, Map<Item, Integer> weaponDamage
		, Map<Item, Integer> chargeMillis
		, Map<Item, Item> ammoType
	)
	{
		_blockMaterials = blockMaterials;
		_materialMutliplers = materialMutliplers;
		_weaponDamage = weaponDamage;
		_chargeMillis = chargeMillis;
		_ammoType = ammoType;
	}

	/**
	 * Checks to see if the given item is a tool, returning the speed modifier if it is, or 1 if it isn't.
	 * 
	 * @param item The item to check.
	 * @return The speed modifier or 1 if this is not a tool.
	 */
	public int toolSpeedModifier(Item item)
	{
		Integer toolValue = _materialMutliplers.get(item);
		return (null != toolValue)
				? toolValue.intValue()
				: 1
		;
	}

	/**
	 * Checks the type of blocks which receive a speed multiplier when using the given item as a tool.  NO_MATERIAL is
	 * returned if this is no tool.
	 * 
	 * @param item The item which may be a tool.
	 * @return The material type this tool can break.
	 */
	public BlockMaterial toolTargetMaterial(Item item)
	{
		BlockMaterial value = _blockMaterials.get(item);
		return (null != value)
				? value
				: BlockMaterial.NO_MATERIAL
		;
	}

	/**
	 * Checks the damage applied by item when it is used as a weapon against another entity, or 1 if it isn't a weapon.
	 * 
	 * @param item The item to check.
	 * @return The weapon or 1 if this is not a weapon.
	 */
	public int toolWeaponDamage(Item item)
	{
		Integer toolValue = _weaponDamage.get(item);
		return (null != toolValue)
				? toolValue.intValue()
				: 1
		;
	}

	/**
	 * Checks if this item can be "charged up", returning the number of milliseconds to "maximum charge", or 0 if not.
	 * 
	 * @param item The item to check.
	 * @return The number of milliseconds to full charge, 0 if not charged.
	 */
	public int getChargeMillis(Item item)
	{
		Integer toolValue = _chargeMillis.get(item);
		return (null != toolValue)
			? toolValue.intValue()
			: 0
		;
	}

	/**
	 * Checks for the ammunition item type for the given weapon, returning null if there isn't one.
	 * Note that the caller can assume that a non-null return value is a stackable type.
	 * 
	 * @param item The item to check.
	 * @return The ammunition type for this weapon or null, if it doesn't have ammunition.
	 */
	public Item getAmmunitionType(Item item)
	{
		Item ammoType = _ammoType.get(item);
		return ammoType;
	}
}
