package com.jeffdisher.october.aspects;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import com.jeffdisher.october.config.IValueTransformer;
import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.utils.Assert;


/**
 * Represents the subset of Item objects which are non-stackable, have durability, and have special uses.
 */
public class ToolRegistry
{
	public static final String FIELD_DURABILITY = "durability";

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
		IValueTransformer<Integer> valueTransformer = new IValueTransformer.IntegerTransformer("speed");
		IValueTransformer<Integer> durabilityTransformer = new IValueTransformer.IntegerTransformer(FIELD_DURABILITY);
		Map<Item, Integer> speeds = new HashMap<>();
		Map<Item, Integer> durabilities = new HashMap<>();
		TabListReader.IParseCallbacks callbacks = new TabListReader.IParseCallbacks() {
			private Item _currentKey;
			@Override
			public void startNewRecord(String name, String[] parameters) throws TabListReader.TabListException
			{
				if (1 != parameters.length)
				{
					throw new TabListReader.TabListException("Expected a single speed value for the tool \"" + name + "\"");
				}
				Item key = keyTransformer.transform(name);
				Integer speed = valueTransformer.transform(parameters[0]);
				speeds.put(key, speed);
				_currentKey = key;
			}
			@Override
			public void endRecord() throws TabListReader.TabListException
			{
				_currentKey = null;
			}
			@Override
			public void processSubRecord(String name, String[] parameters) throws TabListReader.TabListException
			{
				if (!name.equals(FIELD_DURABILITY))
				{
					throw new TabListReader.TabListException("Unexpected field in \"" + _currentKey.id() + "\": \"" + name + "\"");
				}
				if (1 != parameters.length)
				{
					throw new TabListReader.TabListException("Expected a single durability value for the tool \"" + _currentKey.id() + "\"");
				}
				Integer durablity = durabilityTransformer.transform(parameters[0]);
				durabilities.put(_currentKey, durablity);
			}
		};
		
		TabListReader.readEntireFile(callbacks, stream);
		
		// We expect a durability entry for each item.
		Assert.assertTrue(speeds.size() == durabilities.size());
		// We can just pass these in, directly.
		return new ToolRegistry(speeds, durabilities);
	}


	private final Map<Item, Integer> _speedValues;
	private final Map<Item, Integer> _durabilityValues;

	private ToolRegistry(Map<Item, Integer> speedValues, Map<Item, Integer> durabilityValues)
	{
		_speedValues = speedValues;
		_durabilityValues = durabilityValues;
	}

	/**
	 * Checks to see if the given item is a tool, returning the speed modifier if it is, or 1 if it isn't.
	 * 
	 * @param item The item to check.
	 * @return The speed modifier or 1 if this is not a tool.
	 */
	public int toolSpeedModifier(Item item)
	{
		// TODO:  Change this to a default of 1 (the documented answer) once we re-work the block toughness.
		Integer toolValue = _speedValues.get(item);
		return (null != toolValue)
				? toolValue.intValue()
				: 5
		;
	}

	/**
	 * Checks if the given item is stackable, based on whether it is a tool (tools cannot be stacked).
	 * 
	 * @param item The item.
	 * @return True if this item is a stackable type.
	 */
	public boolean isStackable(Item item)
	{
		// We will assume that no tools are stackable.
		return !_speedValues.containsKey(item);
	}

	/**
	 * Looks up the durability for the given tool.  Returns 0 if not a tool or an unbreakable tool.
	 * 
	 * @param item The item to look up.
	 * @return The durability value or 0 if this isn't a tool or is an unbreakable tool.
	 */
	public int toolDurability(Item item)
	{
		Integer value = _durabilityValues.get(item);
		return (null != value)
				? value.intValue()
				: 0
		;
	}
}
