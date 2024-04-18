package com.jeffdisher.october.types;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.utils.Assert;


/**
 * An immutable container where items are or could be.  This may be associated with a block, a player, or something
 * else.
 */
public class Inventory
{
	// For convenience, we store the constants we use to identify which type of inventory, here.
	// These are used by mutations to describe which inventory-related aspect they should use.
	public static final byte INVENTORY_ASPECT_INVENTORY = (byte)1;
	public static final byte INVENTORY_ASPECT_FUEL = (byte)2;

	/**
	 * Builds a new immutable inventory with the given state.
	 * 
	 * @param maxEncumbrance The encumbrance limit for this inventory.
	 * @param items The map of items in this inventory.
	 * @param currentEncumbrance The current encumbrance represented by the items.
	 * @return A new immutable Inventory object.
	 */
	public static Inventory build(int maxEncumbrance
			, Map<Integer, Items> items
			, int currentEncumbrance
	)
	{
		return new Inventory(maxEncumbrance, items, currentEncumbrance);
	}

	/**
	 * Creates a builder for creating an inventory.
	 * 
	 * @param maxEncumbrance The encumbrance limit for this inventory.
	 * @return A Builder object which can be used to finish building the Inventory.
	 */
	public static Builder start(int maxEncumbrance)
	{
		return new Builder(maxEncumbrance);
	}

	public final int maxEncumbrance;
	private final Map<Integer, Items> _items;
	public final int currentEncumbrance;

	private Inventory(int maxEncumbrance
			, Map<Integer, Items> items
			, int currentEncumbrance
	)
	{
		this.maxEncumbrance = maxEncumbrance;
		// We will create this as empty if there is an overflow in encumbrance.
		if (currentEncumbrance >= 0)
		{
			_items = Collections.unmodifiableMap(items);
			this.currentEncumbrance = currentEncumbrance;
		}
		else
		{
			_items = Collections.emptyMap();
			this.currentEncumbrance = 0;
		}
	}

	/**
	 * Looks up the item stack for the given identifier key.
	 * 
	 * @param key The identifier key.
	 * @return The Items object for this stack (asserts on failed look-up).
	 */
	public Items getStackForKey(Integer key)
	{
		Items val = _items.get(key);
		// Someone calling this with an invalid key likely means that the value is stale, which is an error.
		Assert.assertTrue(null != val);
		return val;
	}

	/**
	 * A basic helper to check how many items of a given type are in the inventory.
	 * 
	 * @param type The item type.
	 * @return The number of items of this type.
	 */
	public int getCount(Item type)
	{
		Integer id = _getKeyForType(type);
		Items existing = _items.get(id);
		return (null != existing)
				? existing.count()
				: 0
		;
	}

	/**
	 * @return A list of the identifier keys used in the inventory, sorted from earliest to latest.
	 */
	public List<Integer> sortedKeys()
	{
		return _items.keySet().stream().sorted((Integer one, Integer two) -> (one.intValue() > two.intValue()) ? 1 : -1).toList();
	}

	/**
	 * @return A list of the items within the inventory, sorted by item type.
	 */
	public List<Items> sortedItems()
	{
		return _items.values().stream().sorted((Items one, Items two) -> (one.type().number() > two.type().number()) ? 1 : -1).toList();
	}


	private Integer _getKeyForType(Item type)
	{
		// NOTE:  We don't currently keep a parallel look-up structure for the types since this structure is always very small but that may change in the future.
		Integer id = null;
		for (Map.Entry<Integer, Items> elt : _items.entrySet())
		{
			if (elt.getValue().type() == type)
			{
				id = elt.getKey();
				break;
			}
		}
		return id;
	}


	/**
	 * The builder exists to provide some convenient idioms for building Inventory objects.
	 */
	public static class Builder
	{
		private final int _maxEncumbrance;
		private final Map<Item, Integer> _items;
		private int _currentEncumbrance;
		
		private Builder(int maxEncumbrance)
		{
			_maxEncumbrance = maxEncumbrance;
			_items = new HashMap<>();
		}
		public Builder add(Item type, int count)
		{
			Environment env = Environment.getShared();
			Assert.assertTrue(count > 0);
			
			int current = _items.containsKey(type) ? _items.get(type) : 0;
			current += count;
			_items.put(type, current);
			_currentEncumbrance += env.inventory.getEncumbrance(type) * count;
			return this;
		}
		public Inventory finish()
		{
			Map<Integer, Items> finished = new HashMap<>();
			int nextAddressId = 1;
			for (Map.Entry<Item, Integer> entry : _items.entrySet())
			{
				finished.put(nextAddressId, new Items(entry.getKey(), entry.getValue()));
				nextAddressId += 1;
			}
			return new Inventory(_maxEncumbrance
					, finished
					, _currentEncumbrance
			);
		}
	}
}
