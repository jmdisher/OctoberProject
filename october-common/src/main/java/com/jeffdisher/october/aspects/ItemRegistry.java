package com.jeffdisher.october.aspects;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jeffdisher.october.config.FlatTabListCallbacks;
import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.utils.Assert;


/**
 * Items are defined as constants, and this is where they are all created and looked up.
 * At the moment, items here are defined as a composition of other aspects but it isn't clear that this is the correct
 * direction of relationship (it is convenient and simple, yet seems conceptually backward).
 * Note that the ItemRegistry will eventually be loaded from data, once we have a sense of what kind of information is
 * required.
 */
public class ItemRegistry
{
	/**
	 * Loads the registry from the tablist in the given stream.
	 * The format for the item registry tablist is simple, where each line has the form:
	 * ID<TAB>NAME
	 * 
	 * @param stream The stream containing the tablist.
	 * @return The registry (never null).
	 * @throws IOException There was a problem with the stream.
	 * @throws TabListException The tablist was malformed.
	 */
	public static ItemRegistry loadRegistry(InputStream stream) throws IOException, TabListReader.TabListException
	{
		if (null == stream)
		{
			throw new IOException("Resource missing");
		}
		FlatTabListCallbacks<String, String> callbacks = new FlatTabListCallbacks<>((String value) -> value, (String value) -> value);
		TabListReader.readEntireFile(callbacks, stream);
		return new ItemRegistry(callbacks.keyOrder, callbacks.data);
	}


	/**
	 * Since blocks are the non-negative item types, this helper exists to look them up by block type.
	 */
	public final Item[] ITEMS_BY_TYPE;
	private final Map<String, Item> _idsMap;

	private ItemRegistry(List<String> keyList, Map<String, String> map)
	{
		int size = keyList.size();
		Assert.assertTrue(size <= Short.MAX_VALUE);
		this.ITEMS_BY_TYPE = new Item[size];
		_idsMap = new HashMap<>();
		short index = 0;
		for (String key : keyList)
		{
			Item item = new Item(key, map.get(key), index);
			this.ITEMS_BY_TYPE[index] = item;
			index += 1;
			_idsMap.put(key, item);
		}
	}

	/**
	 * Looks up an item object by its named ID.
	 * 
	 * @param id The ID of an Item.
	 * @return The item or null if not known.
	 */
	public Item getItemById(String id)
	{
		return _idsMap.get(id);
	}
}
