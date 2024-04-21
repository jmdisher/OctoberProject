package com.jeffdisher.october.aspects;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import com.jeffdisher.october.config.FlatTabListCallbacks;
import com.jeffdisher.october.config.IValueTransformer;
import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.types.Item;


/**
 * Represents the subset of Item objects which can be directly eaten.
 */
public class FoodRegistry
{
	/**
	 * Loads the food registry from the tablist in the given stream, sourcing Items from the given items registry.
	 * 
	 * @param items The existing ItemRegistry.
	 * @param stream The stream containing the tablist describing growth divisors.
	 * @return The registry (never null).
	 * @throws IOException There was a problem with a stream.
	 * @throws TabListReader.TabListException A tablist was malformed.
	 */
	public static FoodRegistry load(ItemRegistry items
			, InputStream stream
	) throws IOException, TabListReader.TabListException
	{
		FlatTabListCallbacks<Item, Integer> callbacks = new FlatTabListCallbacks<>(new IValueTransformer.ItemTransformer(items), new IValueTransformer.IntegerTransformer("food_value"));
		TabListReader.readEntireFile(callbacks, stream);
		
		// We can just pass these in, directly.
		return new FoodRegistry(callbacks.data);
	}
	private final Map<Item, Integer> _foodValues;

	private FoodRegistry(Map<Item, Integer> foodValues)
	{
		_foodValues = foodValues;
	}

	/**
	 * Returns the food value of the given item.  Returns 0 if this is not edible.
	 * 
	 * @param item The item to check.
	 * @return The food value (0 if not edible).
	 */
	public int foodValue(Item item)
	{
		return _foodValues.containsKey(item)
				? _foodValues.get(item)
				: 0
		;
	}
}
