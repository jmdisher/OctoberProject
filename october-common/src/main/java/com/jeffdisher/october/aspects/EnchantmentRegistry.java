package com.jeffdisher.october.aspects;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.logic.PropertyHelpers;
import com.jeffdisher.october.properties.PropertyRegistry;
import com.jeffdisher.october.properties.PropertyType;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Enchantment;
import com.jeffdisher.october.types.Infusion;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.utils.Assert;


/**
 * Describes the enchantment registered in the system:  What block is required to apply the enchantment, the target
 * item, the consumed items, how long it takes, and the enchantment to apply to the target.
 * Note that there are 2 variants of this case:
 * 1) A NonStackableItem to which an enchantment property is applied (called "enchantment")
 * 2) An item (stackable or not) which is deleted and replaced with another (called "infusion")
 * Note that how the enchanting block interprets this data is up to it.
 * Serialization note:  The "number" field of Enchantment and Infusion objects is considered stable and can be used for
 * serializing references to these (be aware that they are in their own namespaces and 0 is considered "null").
 */
public class EnchantmentRegistry
{
	public static EnchantmentRegistry load(ItemRegistry items
		, BlockAspect blocks
		, DurabilityAspect durability
		, ToolRegistry tools
		, InputStream enchantingStream
		, InputStream infusionsStream
	) throws IOException, TabListReader.TabListException
	{
		// For each enchantment type, we will infer which tools can receive it based on DurabilityAspect and ToolRegistry.
		_EnchantingDefinitions definitions = new _EnchantingDefinitions(items, blocks);
		TabListReader.readEntireFile(definitions, enchantingStream);
		// We currently expect only the one table, just for ease of testing.
		Assert.assertTrue(1 == definitions.durability.tables.size());
		Block enchantingTable = definitions.durability.tables.get(0);
		Assert.assertTrue(1 == definitions.weapons.tables.size());
		Assert.assertTrue(enchantingTable == definitions.weapons.tables.get(0));
		Assert.assertTrue(1 == definitions.tools.tables.size());
		Assert.assertTrue(enchantingTable == definitions.tools.tables.get(0));
		
		List<Enchantment> enchantments = new ArrayList<>();
		for (Item target : items.ITEMS_BY_TYPE)
		{
			if (durability.getDurability(target) > 0)
			{
				// This has a kind of durability so we can use it.
				// These can't be stackable.
				Assert.assertTrue(!durability.isStackable(target));
				Enchantment enchantDurability = new Enchantment(enchantingTable
					, definitions.durability.chargeMillis
					, target
					, definitions.durability.consumedItems
					, PropertyRegistry.ENCHANT_DURABILITY
				);
				enchantments.add(enchantDurability);
			}
			if (tools.toolWeaponDamage(target) > 1)
			{
				// This has damage so we assume it is a melee weapon.
				// These can't be stackable.
				Assert.assertTrue(!durability.isStackable(target));
				Enchantment enchantDamage = new Enchantment(enchantingTable
					, definitions.weapons.chargeMillis
					, target
					, definitions.weapons.consumedItems
					, PropertyRegistry.ENCHANT_WEAPON_MELEE
				);
				enchantments.add(enchantDamage);
			}
			if (tools.toolSpeedModifier(target) > 1)
			{
				// This has a speed modifier so we assume it is a tool.
				// These can't be stackable.
				Assert.assertTrue(!durability.isStackable(target));
				Enchantment enchantEfficiency = new Enchantment(enchantingTable
					, definitions.tools.chargeMillis
					, target
					, definitions.tools.consumedItems
					, PropertyRegistry.ENCHANT_TOOL_EFFICIENCY
				);
				enchantments.add(enchantEfficiency);
			}
		}
		
		_InfusionDefinitions infusionDefinitions = new _InfusionDefinitions(items, blocks);
		TabListReader.readEntireFile(infusionDefinitions, infusionsStream);
		
		return new EnchantmentRegistry(enchantments
			, Collections.unmodifiableList(infusionDefinitions.infusions)
		);
	}

	/**
	 * Checks if the given enchantment can successfully be applied to the given target.
	 * 
	 * @param target The item to enchant.
	 * @param enchantment The enchantment property to apply.
	 * @return True if the enchantment can be applied to the target.
	 */
	public static boolean canApplyToTarget(NonStackableItem target, PropertyType<Byte> enchantment)
	{
		// Check that this enchantment can apply to this type and that it can be incremented.
		Environment env = Environment.getShared();
		Item type = target.type();
		
		boolean isValidForType = ((PropertyRegistry.ENCHANT_DURABILITY == enchantment) && (env.durability.getDurability(type) > 0))
			|| ((PropertyRegistry.ENCHANT_WEAPON_MELEE == enchantment) && (env.tools.toolWeaponDamage(type) > 1))
			|| ((PropertyRegistry.ENCHANT_TOOL_EFFICIENCY == enchantment) && (env.tools.toolSpeedModifier(type) > 1))
		;
		return isValidForType && _canIncrementEnchantment(target, enchantment);
	}

	/**
	 * Since we want to assume that lists of item requirements can be easily compared with the List .equals(), this
	 * helper will sort a list of Item types into the canonical order for that comparison.
	 * 
	 * @param toConsume The list to sort.
	 * @return The canonically sorted list.
	 */
	public static List<Item> getCanonicallySortedList(List<Item> toConsume)
	{
		return _sortedItemList(toConsume);
	}


	private final Infusion[] _infusions;
	private final Map<Block, List<Enchantment>> _enchantmentsByBlock;
	private final Map<Block, List<Infusion>> _infusionsByBlock;

	private EnchantmentRegistry(List<Enchantment> enchantments, List<Infusion> infusions)
	{
		// We need to leave the 0 index empty since we reserve that value as "null".
		_infusions = new Infusion[infusions.size() + 1];
		for (int i = 0; i < infusions.size(); ++i)
		{
			_infusions[i + 1] = infusions.get(i);
		}
		
		_enchantmentsByBlock = _packMap(enchantments, (Enchantment input) -> input.table());
		_infusionsByBlock = _packMap(infusions, (Infusion input) -> input.table());
	}

	public List<Enchantment> allEnchantments(Block table)
	{
		return _enchantmentsByBlock.get(table);
	}

	public List<Infusion> allInfusions(Block table)
	{
		return _infusionsByBlock.get(table);
	}

	public Infusion infusionForNumber(int number)
	{
		return _infusions[number];
	}

	public Enchantment getEnchantment(Block table, NonStackableItem target, List<Item> toConsume)
	{
		Enchantment match = null;
		List<Enchantment> possible = _enchantmentsByBlock.get(table);
		if (null != possible)
		{
			Item targetType = target.type();
			// Filter these by anything which can apply to these arguments.
			List<Item> sorted = _sortedItemList(toConsume);
			List<Enchantment> matched = possible.stream().filter((Enchantment e) -> {
				return (targetType == e.targetItem()) && sorted.equals(e.consumedItems());
			}).toList();
			
			if (!matched.isEmpty())
			{
				// There should be only one (or there are duplicates in the enchantment config.
				Assert.assertTrue(1 == matched.size());
				Enchantment candidate = matched.get(0);
				
				if (_canIncrementEnchantment(target, candidate.enchantmentToApply()))
				{
					match = candidate;
				}
			}
		}
		return match;
	}

	public Infusion getInfusion(Block table, Item centralItem, List<Item> toConsume)
	{
		Infusion match = null;
		List<Infusion> possible = _infusionsByBlock.get(table);
		if (null != possible)
		{
			// Filter these by anything which can apply to these arguments.
			List<Item> sorted = _sortedItemList(toConsume);
			List<Infusion> matched = possible.stream().filter((Infusion e) -> {
				return (centralItem == e.centralItem()) && sorted.equals(e.consumedItems());
			}).toList();
			
			if (!matched.isEmpty())
			{
				// There should be only one (or there are duplicates in the infusion config.
				Assert.assertTrue(1 == matched.size());
				match = matched.get(0);
			}
		}
		return match;
	}

	public boolean canEnchant(Block block)
	{
		return _enchantmentsByBlock.containsKey(block) || _infusionsByBlock.containsKey(block);
	}

	public Enchantment getBlindEnchantment(Block table, Item targetItem, PropertyType<Byte> property)
	{
		Enchantment result = null;
		List<Enchantment> possible = _enchantmentsByBlock.get(table);
		if (null != possible)
		{
			List<Enchantment> matched = possible.stream().filter((Enchantment e) -> {
				return (targetItem == e.targetItem()) && (property == e.enchantmentToApply());
			}).toList();
			
			if (!matched.isEmpty())
			{
				// There should be only one (or there are duplicates in the infusion config).
				Assert.assertTrue(1 == matched.size());
				result = matched.get(0);
			}
		}
		return result;
	}


	private static <T> Map<Block, List<T>> _packMap(List<T> input
		, Function<T, Block> keyMap
	)
	{
		return input.stream()
			.collect(Collectors.toMap(keyMap
			, (T elt) -> {
				Block k = keyMap.apply(elt);
				return input.stream().filter((T inner) -> (k == keyMap.apply(inner))).toList();
			}
			, (List<T> one, List<T> two) -> {
				// Each sublist will be the same, we just see them for each key instance.
				Assert.assertTrue(one.equals(two));
				return one;
			}))
		;
	}

	private static List<Item> _sortedItemList(List<Item> toConsume)
	{
		List<Item> sorted = new ArrayList<>(toConsume);
		Collections.sort(sorted, (Item one, Item two) -> one.number() - two.number());
		return Collections.unmodifiableList(sorted);
	}

	private static boolean _canIncrementEnchantment(NonStackableItem target, PropertyType<Byte> enchantment)
	{
		// Make sure that we aren't at the limit of this enchantment.
		// For now, this is just max byte but we will probably constrain this in the future.
		byte value = PropertyHelpers.getBytePropertyValue(target.properties(), enchantment);
		return (value < Byte.MAX_VALUE);
	}


	/**
	 * A basic reader for the enchanting.tablist.
	 */
	private static class _EnchantingDefinitions implements TabListReader.IParseCallbacks
	{
		public static final String TYPE_DURABILITY = "DURABILITY";
		public static final String TYPE_WEAPON_MELEE = "WEAPON_MELEE";
		public static final String TYPE_TOOL_EFFICIENCY = "TOOL_EFFICIENCY";
		public static final String KEY_CHARGE_MILLIS = "charge_millis";
		public static final String KEY_CONSUMED_ITEMS = "consumed_items";
		
		private final ItemRegistry _items;
		private final  BlockAspect _blocks;
		
		public _EnchantingEntry durability;
		public _EnchantingEntry weapons;
		public _EnchantingEntry tools;
		
		private String _currentName;
		private _EnchantingEntry _currentEntry;
		
		public _EnchantingDefinitions(ItemRegistry items
			, BlockAspect blocks)
		{
			_items = items;
			_blocks = blocks;
		}
		@Override
		public void startNewRecord(String name, String[] parameters) throws TabListReader.TabListException
		{
			List<Block> tables = Arrays.stream(parameters).map((String id) -> {
				return _blocks.fromItem(_items.getItemById(id));
			}).toList();
			_currentName = name;
			_currentEntry = new _EnchantingEntry(tables);
			if (_currentName.equals(TYPE_DURABILITY))
			{
				Assert.assertTrue(null == this.durability);
				this.durability = _currentEntry;
			}
			else if (_currentName.equals(TYPE_WEAPON_MELEE))
			{
				Assert.assertTrue(null == this.weapons);
				this.weapons = _currentEntry;
			}
			else if (_currentName.equals(TYPE_TOOL_EFFICIENCY))
			{
				Assert.assertTrue(null == this.tools);
				this.tools = _currentEntry;
			}
			else
			{
				throw new TabListReader.TabListException("Unknown enchanting type: \"" + name + "\"");
			}
		}
		@Override
		public void endRecord() throws TabListReader.TabListException
		{
			_currentName = null;
			_currentEntry = null;
		}
		@Override
		public void processSubRecord(String name, String[] parameters) throws TabListReader.TabListException
		{
			if (name.equals(KEY_CHARGE_MILLIS))
			{
				if (1 != parameters.length)
				{
					throw new TabListReader.TabListException("Charge expecting one parameter under \"" + _currentName + "\"");
				}
				_currentEntry.chargeMillis = Integer.parseInt(parameters[0]);
			}
			else if (name.equals(KEY_CONSUMED_ITEMS))
			{
				_currentEntry.consumedItems = _sortedItemList(Arrays.stream(parameters).map((String id) -> {
					return _items.getItemById(id);
				}).toList());
			}
			else
			{
				throw new TabListReader.TabListException("Unknown key type: \"" + name + "\" under \"" + _currentName + "\"");
			}
		}
	}

	/**
	 * A basic reader for the enchanting.tablist.
	 */
	private static class _InfusionDefinitions implements TabListReader.IParseCallbacks
	{
		public static final String KEY_CHARGE_MILLIS = "charge_millis";
		public static final String KEY_CENTRAL_ITEM = "central_item";
		public static final String KEY_CONSUMED_ITEMS = "consumed_items";
		public static final String KEY_OUTPUT_ITEM = "output_item";
		
		private final ItemRegistry _items;
		private final  BlockAspect _blocks;
		
		public final List<Infusion> infusions;
		
		private int _currentNumber;
		private Block _currentTable;
		private long _currentMillisToApply;
		private Item _currentCentralItem;
		private List<Item> _currentConsumedItems;
		private Item _currentOutputItem;
		
		public _InfusionDefinitions(ItemRegistry items
			, BlockAspect blocks)
		{
			_items = items;
			_blocks = blocks;
			this.infusions = new ArrayList<>();
		}
		@Override
		public void startNewRecord(String name, String[] parameters) throws TabListReader.TabListException
		{
			_currentNumber = Integer.parseInt(name);
			if (_currentNumber <= 0)
			{
				throw new TabListReader.TabListException("Infusion number must be positive: " + _currentNumber);
			}
			List<Block> tables = Arrays.stream(parameters).map((String id) -> {
				return _blocks.fromItem(_items.getItemById(id));
			}).toList();
			// We currently only allow a single table.
			Assert.assertTrue(1 == tables.size());
			_currentTable = tables.get(0);
		}
		@Override
		public void endRecord() throws TabListReader.TabListException
		{
			if (this.infusions.stream().anyMatch((Infusion i) -> i.number() == _currentNumber))
			{
				throw new TabListReader.TabListException("Duplicate infusion number: " + _currentNumber);
			}
			if (null == _currentCentralItem)
			{
				throw new TabListReader.TabListException("Infusion central item missing: " + _currentNumber);
			}
			if (null == _currentConsumedItems)
			{
				throw new TabListReader.TabListException("Infusion consumed items missing: " + _currentNumber);
			}
			if (null == _currentOutputItem)
			{
				throw new TabListReader.TabListException("Infusion output item missing: " + _currentNumber);
			}
			Infusion infusion = new Infusion(_currentNumber
				, _currentTable
				, _currentMillisToApply
				, _currentCentralItem
				, Collections.unmodifiableList(_currentConsumedItems)
				, _currentOutputItem
			);
			this.infusions.add(infusion);
			
			_currentNumber = 0;
			_currentTable = null;
			_currentMillisToApply = 0L;
			_currentCentralItem = null;
			_currentConsumedItems = null;
			_currentOutputItem = null;
		}
		@Override
		public void processSubRecord(String name, String[] parameters) throws TabListReader.TabListException
		{
			if (name.equals(KEY_CHARGE_MILLIS))
			{
				if (1 != parameters.length)
				{
					throw new TabListReader.TabListException("Charge expecting one parameter under \"" + _currentNumber + "\"");
				}
				_currentMillisToApply = Integer.parseInt(parameters[0]);
			}
			else if (name.equals(KEY_CENTRAL_ITEM))
			{
				if (1 != parameters.length)
				{
					throw new TabListReader.TabListException("Central item expecting one parameter under \"" + _currentNumber + "\"");
				}
				_currentCentralItem = _items.getItemById(parameters[0]);
			}
			else if (name.equals(KEY_CONSUMED_ITEMS))
			{
				if (0 == parameters.length)
				{
					throw new TabListReader.TabListException("Consumed items cannot be empty \"" + _currentNumber + "\"");
				}
				_currentConsumedItems = _sortedItemList(Arrays.stream(parameters).map((String id) -> {
					return _items.getItemById(id);
				}).toList());
			}
			else if (name.equals(KEY_OUTPUT_ITEM))
			{
				if (1 != parameters.length)
				{
					throw new TabListReader.TabListException("Output item expecting one parameter under \"" + _currentNumber + "\"");
				}
				_currentOutputItem = _items.getItemById(parameters[0]);
			}
			else
			{
				throw new TabListReader.TabListException("Unknown key type: \"" + name + "\" under \"" + _currentNumber + "\"");
			}
		}
	}

	private static class _EnchantingEntry
	{
		public final List<Block> tables;
		public long chargeMillis;
		public List<Item> consumedItems;
		public _EnchantingEntry(List<Block> tables)
		{
			this.tables = tables;
		}
	}
}
