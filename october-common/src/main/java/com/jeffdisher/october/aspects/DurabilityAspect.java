package com.jeffdisher.october.aspects;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import com.jeffdisher.october.config.FlatTabListCallbacks;
import com.jeffdisher.october.config.IValueTransformer;
import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.types.Item;


/**
 * Represents the subset of Item objects which are non-stackable and may have a specific durability.
 */
public class DurabilityAspect
{
	/**
	 * Loads the durability values from the tablist in the given stream, sourcing Items from the given items registry.
	 * 
	 * @param items The existing ItemRegistry.
	 * @param stream The stream containing the tablist describing durabilities.
	 * @return The aspect (never null).
	 * @throws IOException There was a problem with a stream.
	 * @throws TabListReader.TabListException A tablist was malformed.
	 */
	public static DurabilityAspect load(ItemRegistry items
			, InputStream stream
	) throws IOException, TabListReader.TabListException
	{
		IValueTransformer<Item> keyTransformer = new IValueTransformer.ItemTransformer(items);
		IValueTransformer<Integer> valueTransformer = new IValueTransformer.IntegerTransformer("durability");
		FlatTabListCallbacks<Item, Integer> callbacks = new FlatTabListCallbacks<>(keyTransformer, valueTransformer);
		
		TabListReader.readEntireFile(callbacks, stream);
		
		// We can just pass these in, directly.
		return new DurabilityAspect(callbacks.data);
	}


	private final Map<Item, Integer> _durabilityValues;

	private DurabilityAspect(Map<Item, Integer> durabilityValues
	)
	{
		_durabilityValues = durabilityValues;
	}

	/**
	 * Checks if the given item is stackable, based on whether it has an explicit durability.
	 * 
	 * @param item The item.
	 * @return True if this item is a stackable type.
	 */
	public boolean isStackable(Item item)
	{
		// Everything listed in the durability aspect is non-stackable.
		return !_durabilityValues.containsKey(item);
	}

	/**
	 * Looks up the durability for the given item.  Returns 0 if not known or unbreakable.
	 * 
	 * @param item The item to look up.
	 * @return The durability value or 0 if this isn't known or is unbreakable.
	 */
	public int getDurability(Item item)
	{
		Integer value = _durabilityValues.get(item);
		return (null != value)
				? value.intValue()
				: 0
		;
	}
}
