package com.jeffdisher.october.aspects;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.utils.Assert;


/**
 * Parses the trading.tablist and maintains the trades and crafting recipes for each profession type.
 */
public class TradingRegistry
{
	/**
	 * Loads the trading registry from the tablist in the given stream, sourcing Items from the given items registry.
	 * 
	 * @param items The existing ItemRegistry.
	 * @param stream The stream containing the tablist.
	 * @return The registry (never null).
	 * @throws IOException There was a problem with the stream.
	 * @throws TabListReader.TabListException The tablist was malformed.
	 */
	public static TradingRegistry load(ItemRegistry items, InputStream stream) throws IOException, TabListReader.TabListException
	{
		if (null == stream)
		{
			throw new IOException("Resource missing");
		}
		
		Map<String, Profession> professionsById = new HashMap<>();
		
		TabListReader.readEntireFile(new TabListReader.IParseCallbacks() {
			private String _id;
			private String _name;
			private Map<Item, Integer> _buyOffers;
			private Map<Item, Integer> _sellOffers;
			private List<TradeCraft> _crafts;
			@Override
			public void startNewRecord(String name, String[] parameters) throws TabListReader.TabListException
			{
				// These must have 1 name.
				if (1 != parameters.length)
				{
					throw new TabListReader.TabListException("One name required: \"" + name + "\"");
				}
				_id = name;
				_name = parameters[0];
				_buyOffers = new HashMap<>();
				_sellOffers = new HashMap<>();
				_crafts = new ArrayList<>();
			}
			@Override
			public void endRecord() throws TabListReader.TabListException
			{
				if (_buyOffers.isEmpty() && _sellOffers.isEmpty())
				{
					throw new TabListReader.TabListException("There must be at least one trade offered: \"" + _name + "\"");
				}
				
				// We determine our target inventory by being the max or 1 for each item sold and the input to each crafting operation.
				Map<Item, Integer> targetInventory = new HashMap<>();
				for (Item sell : _sellOffers.keySet())
				{
					targetInventory.put(sell, 1);
				}
				for (TradeCraft craft : _crafts)
				{
					for (Map.Entry<Item, Integer> input : craft.inputs.entrySet())
					{
						Item item = input.getKey();
						int existing = targetInventory.getOrDefault(item, 0);
						int updated = Math.max(existing, input.getValue());
						targetInventory.put(item, updated);
					}
				}
				
				Profession profession = new Profession(_id
					, _name
					, Collections.unmodifiableMap(_buyOffers)
					, Collections.unmodifiableMap(_sellOffers)
					, Collections.unmodifiableList(_crafts)
					, Collections.unmodifiableMap(targetInventory)
				);
				professionsById.put(_id, profession);
				
				_id = null;
				_name = null;
				_buyOffers = null;
				_sellOffers = null;
				_crafts = null;
			}
			@Override
			public void processSubRecord(String name, String[] parameters) throws TabListReader.TabListException
			{
				if ("buy".equals(name))
				{
					if (2 != parameters.length)
					{
						throw new TabListReader.TabListException("Malformed buy stanza");
					}
					int coinValue = _readPositiveValue(parameters[0]);
					Item item = _getItem(parameters[1]);
					Object old = _buyOffers.put(item, coinValue);
					if (null != old)
					{
						throw new TabListReader.TabListException("Duplicate buy for \"" + item.name() + "\"");
					}
				}
				else if ("sell".equals(name))
				{
					if (2 != parameters.length)
					{
						throw new TabListReader.TabListException("Malformed sell stanza");
					}
					int coinValue = _readPositiveValue(parameters[0]);
					Item item = _getItem(parameters[1]);
					Object old = _sellOffers.put(item, coinValue);
					if (null != old)
					{
						throw new TabListReader.TabListException("Duplicate sell for \"" + item.name() + "\"");
					}
				}
				else if ("craft".equals(name))
				{
					if ((0 == parameters.length) || (0 != (parameters.length % 3)))
					{
						throw new TabListReader.TabListException("Crafting stanza malformed (must be triplets) for \"" + _id + "\"");
					}
					
					Map<Item, Integer> inputs = new HashMap<>();
					Map<Item, Integer> outputs = new HashMap<>();
					for (int i = 0; i < (parameters.length / 3); ++i)
					{
						int index = 3 * i;
						String part = parameters[index];
						int count = _readPositiveValue(parameters[index + 1]);
						Item item = _getItem(parameters[index + 2]);
						if ("in".equals(part))
						{
							Object old = inputs.put(item, count);
							if (null != old)
							{
								throw new TabListReader.TabListException("Crafting stanza includes duplicate input: \"" + item.name() + "\"");
							}
						}
						else if ("out".equals(part))
						{
							Object old = outputs.put(item, count);
							if (null != old)
							{
								throw new TabListReader.TabListException("Crafting stanza includes duplicate output: \"" + item.name() + "\"");
							}
						}
						else
						{
							throw new TabListReader.TabListException("Crafting stanza includes unknown triplet header: \"" + part + "\"");
						}
					}
					if (inputs.isEmpty())
					{
						throw new TabListReader.TabListException("Crafting stanza requires at least one input for \"" + _id + "\"");
					}
					if (outputs.isEmpty())
					{
						throw new TabListReader.TabListException("Crafting stanza requires at least one output for \"" + _id + "\"");
					}
					TradeCraft craft = new TradeCraft(Collections.unmodifiableMap(inputs)
						, Collections.unmodifiableMap(outputs)
					);
					_crafts.add(craft);
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
			private int _readPositiveValue(String value) throws TabListReader.TabListException
			{
				int val;
				try
				{
					val = Integer.parseInt(value);
					if (val <= 0)
					{
						throw new TabListReader.TabListException("Value must be positive integer: \"" + value + "\"");
					}
				}
				catch (NumberFormatException e)
				{
					throw new TabListReader.TabListException("Value must be positive integer: \"" + value + "\"");
				}
				return val;
			}
		}, stream);
		
		return new TradingRegistry(professionsById);
	}

	private final Map<String, Profession> _professionsById;

	private TradingRegistry(Map<String, Profession> professionsById
	)
	{
		_professionsById = professionsById;
	}

	/**
	 * Looks up a profession by the ID assigned in the tablist file.
	 * 
	 * @param id The profession ID.
	 * @return The profession (cannot fail).
	 */
	public Profession getProfessionById(String id)
	{
		Profession profession = _professionsById.get(id);
		// We don't expect this to be called if it can fail.
		Assert.assertTrue(null != profession);
		return profession;
	}

	/**
	 * @return A view of all of the Professions defined in the system.
	 */
	public Collection<Profession> getAllProfessions()
	{
		return _professionsById.values();
	}


	public static record TradeCraft(Map<Item, Integer> inputs, Map<Item, Integer> outputs) {}

	public static record Profession(String id
		, String name
		, Map<Item, Integer> buyOffers
		, Map<Item, Integer> sellOffers
		, List<TradeCraft> crafts
		, Map<Item, Integer> targetInventory
	) {}
}
