package com.jeffdisher.october.aspects;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import com.jeffdisher.october.config.FlatTabListCallbacks;
import com.jeffdisher.october.config.IValueTransformer;
import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.types.Item;


/**
 * Contains constants associated with the inventory encumbrance of items.
 * Note that, since this is tightly coupled to the ItemRegistry (as it is associated with all items) and StationRegistry
 * (as it is associated with inventory encumbrance limits within stations or entities), this may be rolled into one of
 * those, in the future.
 */
public class InventoryEncumbrance
{
	/**
	 * Loads item encumbrance values for every item.  Note that this will fail if there are missing items since all of
	 * them must be present.
	 * 
	 * @param items The existing ItemRegistry.
	 * @param encumbranceStream The stream containing the tablist describing per-item encumbrance.
	 * @return The inventory encumbrance registry (never null).
	 * @throws IOException There was a problem with a stream.
	 * @throws TabListReader.TabListException A tablist was malformed.
	 */
	public static InventoryEncumbrance load(ItemRegistry items
			, InputStream encumbranceStream
	) throws IOException, TabListReader.TabListException
	{
		FlatTabListCallbacks<Item, Integer> encumbranceCallbacks = new FlatTabListCallbacks<>(new IValueTransformer.ItemTransformer(items), new IValueTransformer.IntegerTransformer("encumbrance"));
		int[] encumbranceByItemType = new int[items.ITEMS_BY_TYPE.length];
		for (int i = 0; i < encumbranceByItemType.length; ++i)
		{
			encumbranceByItemType[i] = -1;
		}
		TabListReader.readEntireFile(encumbranceCallbacks, encumbranceStream);
		for (Map.Entry<Item, Integer> elt : encumbranceCallbacks.data.entrySet())
		{
			Item item = elt.getKey();
			encumbranceByItemType[item.number()] = elt.getValue();
		}
		
		// We expect an entry for every item.
		for (int i = 0; i < encumbranceByItemType.length; ++i)
		{
			if (-1 == encumbranceByItemType[i])
			{
				throw new TabListReader.TabListException("Missing encumbrance: \"" + items.ITEMS_BY_TYPE[i].name() + "\"");
			}
		}
		
		return new InventoryEncumbrance(encumbranceByItemType);
	}

	private final int[] _encumbranceByItemType;

	private InventoryEncumbrance(int[] encumbranceByItemType)
	{
		_encumbranceByItemType = encumbranceByItemType;
	}

	public int getEncumbrance(Item item)
	{
		return _encumbranceByItemType[item.number()];
	}
}
