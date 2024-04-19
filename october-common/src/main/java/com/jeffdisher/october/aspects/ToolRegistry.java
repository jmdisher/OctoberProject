package com.jeffdisher.october.aspects;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import com.jeffdisher.october.config.FlatTabListCallbacks;
import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.types.Item;


/**
 * Represents the subset of Item objects which are non-stackable, have durability, and have special uses.
 */
public class ToolRegistry
{
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
		FlatTabListCallbacks<Item, Integer> callbacks = new FlatTabListCallbacks<>(new FlatTabListCallbacks.ItemTransformer(items), new FlatTabListCallbacks.IntegerTransformer("speed"));
		TabListReader.readEntireFile(callbacks, stream);
		
		// We can just pass these in, directly.
		return new ToolRegistry(callbacks.data);
	}


	private final Map<Item, Integer> _speedValues;

	private ToolRegistry(Map<Item, Integer> speedValues)
	{
		_speedValues = speedValues;
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
}
