package com.jeffdisher.october.types;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

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
	public static Inventory build(int maxEncumbrance, Map<Item, Items> items, int currentEncumbrance)
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
	public final Map<Item, Items> items;
	public final int currentEncumbrance;

	private Inventory(int maxEncumbrance, Map<Item, Items> items, int currentEncumbrance)
	{
		this.maxEncumbrance = maxEncumbrance;
		// We will create this as empty if there is an overflow in encumbrance.
		if (currentEncumbrance >= 0)
		{
			this.items = Collections.unmodifiableMap(items);
			this.currentEncumbrance = currentEncumbrance;
		}
		else
		{
			this.items = Collections.emptyMap();
			this.currentEncumbrance = 0;
		}
	}

	/**
	 * A basic helper to check how many items of a given type are in the inventory.
	 * 
	 * @param type The item type.
	 * @return The number of items of this type.
	 */
	public int getCount(Item type)
	{
		Items existing = this.items.get(type);
		return (null != existing)
				? existing.count()
				: 0
		;
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
			Map<Item, Items> finished = _items.entrySet().stream().collect(Collectors.toMap(
					(Map.Entry<Item, Integer> entry) -> entry.getKey()
					, (Map.Entry<Item, Integer> entry) -> new Items(entry.getKey(), entry.getValue())
			));
			return new Inventory(_maxEncumbrance, finished, _currentEncumbrance);
		}
	}
}
