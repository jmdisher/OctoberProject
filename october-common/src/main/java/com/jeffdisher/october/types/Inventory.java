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
	 * @param stackable The map of stackable items in this inventory.
	 * @param currentEncumbrance The current encumbrance represented by the items.
	 * @return A new immutable Inventory object.
	 */
	public static Inventory build(int maxEncumbrance
			, Map<Integer, Items> stackable
			, int currentEncumbrance
	)
	{
		return new Inventory(maxEncumbrance, stackable, currentEncumbrance);
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
	public final int currentEncumbrance;

	private Inventory(int maxEncumbrance
			, Map<Integer, Items> stackable
			, int currentEncumbrance
	)
	{
		this.maxEncumbrance = maxEncumbrance;
		// We will create this as empty if there is an overflow in encumbrance.
		if (currentEncumbrance >= 0)
		{
			_stackable = Collections.unmodifiableMap(stackable);
			this.currentEncumbrance = currentEncumbrance;
		}
		else
		{
			_stackable = Collections.emptyMap();
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
	 * A basic helper to check how many items of a given type are in the inventory.
	 * 
	 * @param type The item type.
	 * @return The number of items of this type.
	 */
	public int getCount(Item type)
	{
		int id = _getKeyForStackableType(type);
		Items existing = _stackable.get(id);
		return (null != existing)
				? existing.count()
				: 0
		;
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
		builder.append("Inventory: " + this.currentEncumbrance + " / " + this.maxEncumbrance + " with items: " + _stackable.size() + "\n");
		for (Integer key : _allSortedKeys())
		{
			builder.append("\t" + key + " -> " + _stackable.get(key) + "\n");
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
		return _stackable.keySet().stream().sorted((Integer one, Integer two) -> (one.intValue() > two.intValue()) ? 1 : -1).toList();
	}


	/**
	 * The builder exists to provide some convenient idioms for building Inventory objects.
	 */
	public static class Builder
	{
		private final int _maxEncumbrance;
		private final Map<Item, Integer> _stackable;
		private int _currentEncumbrance;
		
		private Builder(int maxEncumbrance)
		{
			_maxEncumbrance = maxEncumbrance;
			_stackable = new HashMap<>();
		}
		public Builder addStackable(Item type, int count)
		{
			Environment env = Environment.getShared();
			Assert.assertTrue(count > 0);
			
			int current = _stackable.containsKey(type) ? _stackable.get(type) : 0;
			current += count;
			_stackable.put(type, current);
			_currentEncumbrance += env.inventory.getEncumbrance(type) * count;
			return this;
		}
		public Inventory finish()
		{
			Map<Integer, Items> stackable = new HashMap<>();
			int nextAddressId = 1;
			for (Map.Entry<Item, Integer> entry : _stackable.entrySet())
			{
				stackable.put(nextAddressId, new Items(entry.getKey(), entry.getValue()));
				nextAddressId += 1;
			}
			return new Inventory(_maxEncumbrance
					, stackable
					, _currentEncumbrance
			);
		}
	}
}
