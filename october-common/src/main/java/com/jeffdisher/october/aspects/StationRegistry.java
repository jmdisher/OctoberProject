package com.jeffdisher.october.aspects;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.october.config.IValueTransformer;
import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.config.TabListReader.TabListException;
import com.jeffdisher.october.types.Block;


/**
 * Represents the subset of Block objects which have some kind of functionality within the game.  This includes crafting
 * and storage.
 */
public class StationRegistry
{
	/**
	 * We treat air, water, and some other cases as "empty" and they have a small capacity since it is just "on the
	 * ground" and we want containers to be used.  That said, these blocks are often over-filled to avoid destroying
	 * items.
	 */
	public static final int CAPACITY_BLOCK_EMPTY = 100;
	public static final int CAPACITY_PLAYER = 200;

	public static final String FIELD_INVENTORY = "inventory";
	public static final String FIELD_CRAFTING = "crafting";
	public static final String FIELD_FUEL_INVENTORY = "fuel_inventory";
	public static final String FIELD_MANUAL_MULTIPLIER = "manual_multiplier";

	/**
	 * Loads the station registry from the tablist in the given stream, sourcing Blocks from the given item and block
	 * registries.
	 * 
	 * @param items The existing ItemRegistry.
	 * @param blocks The existing BlockAspect.
	 * @param craftAspect The registry where crafting classifications are defined.
	 * @param stream The stream containing the tablist describing stations.
	 * @return The registry (never null).
	 * @throws IOException There was a problem with a stream.
	 * @throws TabListReader.TabListException A tablist was malformed.
	 */
	public static StationRegistry load(ItemRegistry items
			, BlockAspect blocks
			, CraftAspect craftAspect
			, InputStream stream
	) throws IOException, TabListReader.TabListException
	{
		IValueTransformer<Block> keyTransformer = new IValueTransformer.BlockTransformer(items, blocks);
		IValueTransformer<Integer> inventoryTransformer = new IValueTransformer.IntegerTransformer(FIELD_INVENTORY);
		IValueTransformer<String> craftingTransformer = new _CraftingTransformer(craftAspect, FIELD_CRAFTING);
		IValueTransformer<Integer> fuelInventoryTransformer = new IValueTransformer.IntegerTransformer(FIELD_FUEL_INVENTORY);
		IValueTransformer<Integer> manualMultiplierTransformer = new IValueTransformer.IntegerTransformer(FIELD_MANUAL_MULTIPLIER);
		
		Map<Block, Integer> inventories = new HashMap<>();
		Map<Block, Set<String>> classifications = new HashMap<>();
		Map<Block, Integer> fuelInventories = new HashMap<>();
		Map<Block, Integer> manualMultipliers = new HashMap<>();
		TabListReader.IParseCallbacks callbacks = new TabListReader.IParseCallbacks() {
			private Block _currentKey;
			@Override
			public void startNewRecord(String name, String[] parameters) throws TabListReader.TabListException
			{
				if (0 != parameters.length)
				{
					throw new TabListReader.TabListException("Station header expected to have no parameters: \"" + name + "\"");
				}
				_currentKey = keyTransformer.transform(name);
			}
			@Override
			public void endRecord() throws TabListReader.TabListException
			{
				// Make sure that the arguments are consistent.
				if (!inventories.containsKey(_currentKey))
				{
					throw new TabListReader.TabListException("Station requires inventory: \"" + _currentKey.item().id() + "\"");
				}
				boolean isCrafting = classifications.containsKey(_currentKey)
						|| fuelInventories.containsKey(_currentKey)
						|| manualMultipliers.containsKey(_currentKey)
				;
				if (isCrafting)
				{
					boolean isConsistent = classifications.containsKey(_currentKey)
							&& manualMultipliers.containsKey(_currentKey)
							&& inventories.containsKey(_currentKey)
					;
					if (!isConsistent)
					{
						throw new TabListReader.TabListException("If any crafting sub-keys are applied, \"inventory\", \"crafting\", and \"manual_multiplier\" are required: \"" + _currentKey.item().id() + "\"");
					}
				}
				_currentKey = null;
			}
			@Override
			public void processSubRecord(String name, String[] parameters) throws TabListReader.TabListException
			{
				if (name.equals(FIELD_INVENTORY))
				{
					String first = _requireOneParamter(name, parameters);
					inventories.put(_currentKey, inventoryTransformer.transform(first));
				}
				else if (name.equals(FIELD_CRAFTING))
				{
					if (0 == parameters.length)
					{
						throw new TabListReader.TabListException("At least one classification required in \"" + _currentKey.item().id() + "\"");
					}
					Set<String> set = new HashSet<>();
					for (String parameter : parameters)
					{
						set.add(craftingTransformer.transform(parameter));
					}
					classifications.put(_currentKey, Collections.unmodifiableSet(set));
				}
				else if (name.equals(FIELD_FUEL_INVENTORY))
				{
					String first = _requireOneParamter(name, parameters);
					fuelInventories.put(_currentKey, fuelInventoryTransformer.transform(first));
				}
				else if (name.equals(FIELD_MANUAL_MULTIPLIER))
				{
					String first = _requireOneParamter(name, parameters);
					manualMultipliers.put(_currentKey, manualMultiplierTransformer.transform(first));
				}
				else
				{
					throw new TabListReader.TabListException("Unexpected field in \"" + _currentKey.item().id() + "\": \"" + name + "\"");
				}
			}
			private String _requireOneParamter(String name, String[] parameters) throws TabListReader.TabListException
			{
				if (1 != parameters.length)
				{
					throw new TabListReader.TabListException("Field in \"" + _currentKey.item().id() + "\" expects a single parameter: \"" + name + "\"");
				}
				return parameters[0];
			}
		};
		
		TabListReader.readEntireFile(callbacks, stream);
		
		// We can just pass these in, directly.
		return new StationRegistry(inventories, classifications, fuelInventories, manualMultipliers);
	}


	private final Map<Block, Integer> _inventories;
	private final Map<Block, Set<String>> _classifications;
	private final Map<Block, Integer> _fuelInventories;
	private final Map<Block, Integer> _manualMultipliers;

	private StationRegistry(Map<Block, Integer> inventories
			, Map<Block, Set<String>> classifications
			, Map<Block, Integer> fuelInventories
			, Map<Block, Integer> manualMultipliers
	)
	{
		_inventories = inventories;
		_classifications = classifications;
		_fuelInventories = fuelInventories;
		_manualMultipliers = manualMultipliers;
	}

	/**
	 * Returns the size of the normal inventory for this block, 0 if it doesn't have one.
	 * 
	 * @param block The block.
	 * @return The normal inventory capacity.
	 */
	public int getNormalInventorySize(Block block)
	{
		Integer size = _inventories.get(block);
		return (null != size)
				? size.intValue()
				: 0
		;
	}

	/**
	 * Returns the set of crafting operations associated with the given block, empty if it isn't for crafting.
	 * 
	 * @param block The block.
	 * @return The crafting classifications (empty if not a crafting block).
	 */
	public Set<String> getCraftingClasses(Block block)
	{
		Set<String> set = _classifications.get(block);
		return (null != set)
				? set
				: Set.of()
		;
	}

	/**
	 * Returns the size of the fuel inventory for this block, 0 if it doesn't have one.
	 * 
	 * @param block The block.
	 * @return The fuel inventory capacity.
	 */
	public int getFuelInventorySize(Block block)
	{
		Integer size = _fuelInventories.get(block);
		return (null != size)
				? size.intValue()
				: 0
		;
	}

	/**
	 * Returns the manual crafting speed multiplier for this block, 0 if it is automatic or not for crafting.
	 * 
	 * @param block The block.
	 * @return The manual crafting speed multiplier.
	 */
	public int getManualMultiplier(Block block)
	{
		Integer multiplier = _manualMultipliers.get(block);
		return (null != multiplier)
				? multiplier.intValue()
				: 0
		;
	}


	/**
	 * Decodes the value as a crafting classification string, as previously defined by CraftAspect.
	 */
	private static class _CraftingTransformer implements IValueTransformer<String>
	{
		private final CraftAspect _craftAspect;
		private final String _name;
		public _CraftingTransformer(CraftAspect craftAspect, String fieldName)
		{
			_craftAspect = craftAspect;
			_name = fieldName;
		}
		@Override
		public String transform(String value) throws TabListException
		{
			if (_craftAspect.isCraftingClassification(value))
			{
				return value;
			}
			else
			{
				throw new TabListReader.TabListException("Not a valid " + _name + " crafting classification: \"" + value + "\"");
			}
		}
	}
}
