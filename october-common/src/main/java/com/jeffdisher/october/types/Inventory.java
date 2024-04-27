package com.jeffdisher.october.types;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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
	 * @param stackable The map of stackable items in this inventory.
	 * @param nonStackable The map of non-stackable items in this inventory.
	 * @param currentEncumbrance The current encumbrance represented by the items.
	 * @return A new immutable Inventory object.
	 */
	public static Inventory build(int maxEncumbrance
			, Map<Integer, Items> stackable
			, Map<Integer, NonStackableItem> nonStackable
			, int currentEncumbrance
	)
	{
		return new Inventory(maxEncumbrance, stackable, nonStackable, currentEncumbrance);
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
	private final Map<Integer, Items> _stackable;
	private final Map<Integer, NonStackableItem> _nonStackable;
	public final int currentEncumbrance;

	private Inventory(int maxEncumbrance
			, Map<Integer, Items> stackable
			, Map<Integer, NonStackableItem> nonStackable
			, int currentEncumbrance
	)
	{
		this.maxEncumbrance = maxEncumbrance;
		// We will create this as empty if there is an overflow in encumbrance.
		if (currentEncumbrance >= 0)
		{
			_stackable = Collections.unmodifiableMap(stackable);
			_nonStackable = Collections.unmodifiableMap(nonStackable);
			this.currentEncumbrance = currentEncumbrance;
		}
		else
		{
			_stackable = Collections.emptyMap();
			_nonStackable = Collections.emptyMap();
			this.currentEncumbrance = 0;
		}
	}

	/**
	 * Looks up the item stack for the given identifier key.
	 * 
	 * @param key The identifier key.
	 * @return The Items object for this stack (null if not stackable).
	 */
	public Items getStackForKey(int key)
	{
		return _stackable.get(key);
	}

	/**
	 * Looks up the item stack for the given identifier key.
	 * 
	 * @param key The identifier key.
	 * @return The NonStackable object for this stack (null if stackable).
	 */
	public NonStackableItem getNonStackableForKey(int key)
	{
		return _nonStackable.get(key);
	}

	/**
	 * A basic helper to check how many items of a given type are in the inventory.  This cannot be called if the item
	 * is non-stackable.
	 * 
	 * @param type The item type.
	 * @return The number of items of this type.
	 */
	public int getCount(Item type)
	{
		int id = _getKeyForStackableType(type);
		Items existing = _stackable.get(id);
		int count;
		if (null != existing)
		{
			count = existing.count();
		}
		else
		{
			// This should not be called if the item is non-stackable.
			Assert.assertTrue(!_nonStackable.containsKey(id));
			count = 0;
		}
		return count;
	}

	/**
	 * Checks the stackable items in this inventory to see if we have any of this type.
	 * 
	 * @param type The type to check.
	 * @return The key this inventory uses to address the stack of this type or 0 if not known.
	 */
	public int getIdOfStackableType(Item type)
	{
		return _getKeyForStackableType(type);
	}

	/**
	 * @return A list of the identifier keys used in the inventory, sorted from earliest to latest.
	 */
	public List<Integer> sortedKeys()
	{
		return _allSortedKeys();
	}

	@Override
	public String toString()
	{
		StringBuilder builder = new StringBuilder();
		builder.append("Inventory: " + this.currentEncumbrance + " / " + this.maxEncumbrance + " with stackable items: " + _stackable.size() + " and non-stackable: " + _nonStackable.size() + "\n");
		for (Integer key : _allSortedKeys())
		{
			if (_stackable.containsKey(key))
			{
				builder.append("\t" + key + " -> " + _stackable.get(key) + "\n");
			}
			if (_nonStackable.containsKey(key))
			{
				builder.append("\t" + key + " -> " + _nonStackable.get(key) + "\n");
			}
		}
		return builder.toString();
	}


	private int _getKeyForStackableType(Item type)
	{
		// NOTE:  We don't currently keep a parallel look-up structure for the types since this structure is always very small but that may change in the future.
		int id = 0;
		for (Map.Entry<Integer, Items> elt : _stackable.entrySet())
		{
			if (elt.getValue().type() == type)
			{
				id = elt.getKey();
				break;
			}
		}
		return id;
	}

	private List<Integer> _allSortedKeys()
	{
		return Stream.concat(_stackable.keySet().stream(), _nonStackable.keySet().stream()).sorted((Integer one, Integer two) -> (one.intValue() > two.intValue()) ? 1 : -1).toList();
	}


	/**
	 * The builder exists to provide some convenient idioms for building Inventory objects.
	 */
	public static class Builder
	{
		private final int _maxEncumbrance;
		private final Map<Item, Integer> _stackable;
		private final List<NonStackableItem> _nonStackable;
		private int _currentEncumbrance;
		
		private Builder(int maxEncumbrance)
		{
			_maxEncumbrance = maxEncumbrance;
			_stackable = new HashMap<>();
			_nonStackable = new ArrayList<>();
		}
		public Builder addStackable(Item type, int count)
		{
			Environment env = Environment.getShared();
			Assert.assertTrue(count > 0);
			
			int current = _stackable.containsKey(type) ? _stackable.get(type) : 0;
			current += count;
			_stackable.put(type, current);
			_currentEncumbrance += env.encumbrance.getEncumbrance(type) * count;
			return this;
		}
		public Builder addNonStackable(NonStackableItem item)
		{
			Environment env = Environment.getShared();
			
			_nonStackable.add(item);
			_currentEncumbrance += env.encumbrance.getEncumbrance(item.type());
			return this;
		}
		public Inventory finish()
		{
			Map<Integer, Items> stackable = new HashMap<>();
			Map<Integer, NonStackableItem> nonStackable = new HashMap<>();
			int nextAddressId = 1;
			for (Map.Entry<Item, Integer> entry : _stackable.entrySet())
			{
				stackable.put(nextAddressId, new Items(entry.getKey(), entry.getValue()));
				nextAddressId += 1;
			}
			for (NonStackableItem item : _nonStackable)
			{
				nonStackable.put(nextAddressId, item);
				nextAddressId += 1;
			}
			return new Inventory(_maxEncumbrance
					, stackable
					, nonStackable
					, _currentEncumbrance
			);
		}
	}
}
