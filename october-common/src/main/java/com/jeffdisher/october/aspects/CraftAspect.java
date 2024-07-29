package com.jeffdisher.october.aspects;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.types.Craft;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableInventory;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.utils.Assert;


/**
 * Describes the crafting aspect and how Craft operations are looked up and applied.
 * This will likely be changed into data once all the core crafting mechanics have been fleshed out.
 */
public class CraftAspect
{
	/**
	 * This constant is defined for the "in-inventory" built-in crafting recipes.  This constant is implicitly defined
	 * and can be referenced by any recipe.
	 */
	public static final String BUILT_IN = "BUILT_IN";

	/**
	 * Loads the craft aspect from the tablist in the given stream, sourcing Items from the given items registry and
	 * relying on the given inventory.
	 * 
	 * @param items The existing ItemRegistry.
	 * @param The existing BlockAspect.
	 * @param inventory The existing inventory aspect.
	 * @param stream The stream containing the tablist.
	 * @return The aspect (never null).
	 * @throws IOException There was a problem with the stream.
	 * @throws TabListReader.TabListException The tablist was malformed.
	 */
	public static CraftAspect load(ItemRegistry items, BlockAspect blocks, InventoryEncumbrance inventory, InputStream stream) throws IOException, TabListReader.TabListException
	{
		if (null == stream)
		{
			throw new IOException("Resource missing");
		}
		
		// We will dynamically build the set of classifications but always start with the built-in.
		Set<String> craftingClassifications = new HashSet<>();
		craftingClassifications.add(CraftAspect.BUILT_IN);
		
		List<Craft> crafts = new ArrayList<>();
		Map<String, Craft> craftByName = new HashMap<>();
		
		TabListReader.readEntireFile(new TabListReader.IParseCallbacks() {
			private String _name;
			private String _classification;
			private Map<Item, Integer> _input;
			// NOTE:  Output can be stackable or not so multiple instances of an item should just be added this list multiple times.
			private List<Item> _output;
			private long _millis = -1L;
			@Override
			public void startNewRecord(String name, String[] parameters) throws TabListReader.TabListException
			{
				// These must have 1 classification.
				if (1 != parameters.length)
				{
					throw new TabListReader.TabListException("One classification required: \"" + name + "\"");
				}
				_name = name;
				// Add this classification.
				craftingClassifications.add(parameters[0]);
				_classification = parameters[0];
				_input = new HashMap<>();
				_output = new ArrayList<>();
				_millis = 0L;
			}
			@Override
			public void endRecord() throws TabListReader.TabListException
			{
				if (_input.isEmpty() || _output.isEmpty() || (_millis <= 0L))
				{
					throw new TabListReader.TabListException("Recipe requires a valid classification, at least 1 input, at least 1 output, and a positive time cost: \"" + _name + "\"");
				}
				
				Items[] inputs = new Items[_input.size()];
				int index = 0;
				for (Map.Entry<Item, Integer> elt : _input.entrySet())
				{
					inputs[index] = new Items(elt.getKey(), elt.getValue());
					index += 1;
				}
				Item[] output = _output.toArray((int size) -> new Item[size]);
				Craft craft = new Craft((short)crafts.size(), _name, _classification, inputs, output, _millis);
				crafts.add(craft);
				craftByName.put(_name, craft);
				
				_name = null;
				_input = null;
				_output = null;
				_millis = -1L;
			}
			@Override
			public void processSubRecord(String name, String[] parameters) throws TabListReader.TabListException
			{
				if ("input".equals(name))
				{
					if (0 == parameters.length)
					{
						throw new TabListReader.TabListException("Missing input value");
					}
					for (String value : parameters)
					{
						Item item = _getItem(value);
						_modifyItemMap(_input, item);
					}
				}
				else if ("output".equals(name))
				{
					if (0 == parameters.length)
					{
						throw new TabListReader.TabListException("Missing output value");
					}
					for (String value : parameters)
					{
						Item item = _getItem(value);
						_output.add(item);
					}
				}
				else if ("millis".equals(name))
				{
					if (1 != parameters.length)
					{
						throw new TabListReader.TabListException("Single time cost value required");
					}
					try
					{
						_millis = Long.parseLong(parameters[0]);
					}
					catch (NumberFormatException e)
					{
						throw new TabListReader.TabListException("Invalid number: \"" + parameters[0] + "\"");
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
			private void _modifyItemMap(Map<Item, Integer> map, Item item)
			{
				int count = map.containsKey(item) ? map.get(item) : 0;
				map.put(item, count + 1);
			}
		}, stream);
		
		return new CraftAspect(blocks, inventory, craftingClassifications, crafts, craftByName);
	}

	private final Set<String> _craftingClassifications;
	private final Map<String, Craft> _craftByName;

	/**
	 * Since blocks are the non-negative item types, this helper exists to look them up by block type.
	 */
	public final Craft[] CRAFTING_OPERATIONS;

	private CraftAspect(BlockAspect blocks
			, InventoryEncumbrance inventory
			, Set<String> craftingClassifications
			, List<Craft> crafts
			, Map<String, Craft> craftByName
	)
	{
		this.CRAFTING_OPERATIONS = crafts.toArray((int size) -> new Craft[size]);
		
		_craftingClassifications = craftingClassifications;
		_craftByName = craftByName;
		
		// We never want to allow encumbrance to increase through crafting.
		for (Craft craft : crafts)
		{
			int inputEncumbrance = 0;
			for (Items oneInput : craft.input)
			{
				inputEncumbrance += inventory.getEncumbrance(oneInput.type()) * oneInput.count();
			}
			int outputEncumbrance = 0;
			for (Item oneOutput : craft.output)
			{
				outputEncumbrance += inventory.getEncumbrance(oneOutput);
			}
			Assert.assertTrue(inputEncumbrance >= outputEncumbrance);
		}
	}

	/**
	 * Looks up a crafting recipe by the ID assigned in the tablist file.  This is generally only useful for testing.
	 * 
	 * @param id The crafting recipe ID.
	 * @return The crafting recipe.
	 */
	public Craft getCraftById(String id)
	{
		Craft craft = _craftByName.get(id);
		// We don't expect this to be called if it can fail.
		Assert.assertTrue(null != craft);
		return craft;
	}

	/**
	 * Returns the list of crafting operations which are included in the given set of classifications.
	 * 
	 * @param classifications The set of classifications to check.
	 * @return The list of crafting operations in the union of these classifications.
	 */
	public List<Craft> craftsForClassifications(Set<String> classifications)
	{
		return List.of(CRAFTING_OPERATIONS).stream().filter((Craft craft) -> classifications.contains(craft.classification)).toList();
	}

	/**
	 * Checks if the given value is a defined crafting classification.  Note that this check is case sensitive.
	 * 
	 * @param value The value to check.
	 * @return True if the crafting classification is defined.
	 */
	public boolean isCraftingClassification(String value)
	{
		return _craftingClassifications.contains(value);
	}


	public static boolean canApply(Craft craft, Inventory inv)
	{
		return _canApply(craft, inv);
	}

	public static boolean craft(Environment env, Craft craft, MutableInventory inv)
	{
		boolean didCraft = false;
		// Verify that they have the input.
		if (_canApply(craft, inv.freeze()))
		{
			// Now, perform the craft against this inventory.
			for (Items items : craft.input)
			{
				inv.removeStackableItems(items.type(), items.count());
			}
			for (Item item : craft.output)
			{
				boolean didAdd;
				if (env.durability.isStackable(item))
				{
					didAdd = inv.addAllItems(item, 1);
				}
				else
				{
					int startingDurability = env.durability.getDurability(item);
					didAdd = inv.addNonStackableBestEfforts(new NonStackableItem(item, startingDurability));
				}
				// We can't fail to add here.
				Assert.assertTrue(didAdd);
			}
			didCraft = true;
		}
		return didCraft;
	}


	private static boolean _canApply(Craft craft, Inventory inv)
	{
		boolean canApply = true;
		for (Items items : craft.input)
		{
			canApply = inv.getCount(items.type()) >= items.count();
			if (!canApply)
			{
				break;
			}
		}
		return canApply;
	}
}
