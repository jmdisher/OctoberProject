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
	 * @param slots The map of all item slots in this inventory.
	 * @param currentEncumbrance The current encumbrance represented by the items.
	 * @return A new immutable Inventory object.
	 */
	public static Inventory build(int maxEncumbrance
			, Map<Integer, ItemSlot> slots
			, int currentEncumbrance
	)
	{
		return new Inventory(maxEncumbrance, slots, currentEncumbrance);
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
	private final Map<Integer, ItemSlot> _slots;
	public final int currentEncumbrance;

	private Inventory(int maxEncumbrance
			, Map<Integer, ItemSlot> slots
			, int currentEncumbrance
	)
	{
		this.maxEncumbrance = maxEncumbrance;
		// We will create this as empty if there is an overflow in encumbrance.
		if (currentEncumbrance >= 0)
		{
			_slots = Collections.unmodifiableMap(slots);
			this.currentEncumbrance = currentEncumbrance;
		}
		else
		{
			_slots = Collections.emptyMap();
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
		ItemSlot slot = _slots.get(key);
		return (null != slot)
			? slot.stack
			: null
		;
	}

	/**
	 * Looks up the item stack for the given identifier key.
	 * 
	 * @param key The identifier key.
	 * @return The NonStackable object for this stack (null if stackable).
	 */
	public NonStackableItem getNonStackableForKey(int key)
	{
		ItemSlot slot = _slots.get(key);
		return (null != slot)
			? slot.nonStackable
			: null
		;
	}

	/**
	 * Looks up the slot for the given identifier key.
	 * 
	 * @param key The identifier key.
	 * @return The slot for whatever is stored in this key (null if the key isn't in the receiver).
	 */
	public ItemSlot getSlotForKey(int key)
	{
		return _slots.get(key);
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
		ItemSlot slot = _slots.get(id);
		Items existing = (null != slot) ? slot.stack : null;
		int count;
		if (null != existing)
		{
			count = existing.count();
		}
		else
		{
			// This should not be called if the item is non-stackable.
			Assert.assertTrue(null == slot);
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
		builder.append("Inventory: " + this.currentEncumbrance + " / " + this.maxEncumbrance + " with slot count: " + _slots.size() + "\n");
		for (Integer key : _allSortedKeys())
		{
			ItemSlot slot = _slots.get(key);
			builder.append("\t" + key + " -> " + slot + "\n");
		}
		return builder.toString();
	}


	private int _getKeyForStackableType(Item type)
	{
		// NOTE:  We don't currently keep a parallel look-up structure for the types since this structure is always very small but that may change in the future.
		int id = 0;
		for (Map.Entry<Integer, ItemSlot> elt : _slots.entrySet())
		{
			Items stack = elt.getValue().stack;
			if ((null != stack) && (stack.type() == type))
			{
				id = elt.getKey();
				break;
			}
		}
		return id;
	}

	private List<Integer> _allSortedKeys()
	{
		return _slots.keySet().stream().sorted((Integer one, Integer two) -> (one.intValue() > two.intValue()) ? 1 : -1).toList();
	}


	/**
	 * The builder exists to provide some convenient idioms for building Inventory objects.
	 */
	public static class Builder
	{
		private final int _maxEncumbrance;
		private final Map<Integer, ItemSlot> _slots;
		private final Map<Item, Integer> _stackableKeys;
		private int _currentEncumbrance;
		
		private Builder(int maxEncumbrance)
		{
			_maxEncumbrance = maxEncumbrance;
			_slots = new HashMap<>();
			_stackableKeys = new HashMap<>();
		}
		public Builder addStackable(Item type, int count)
		{
			Environment env = Environment.getShared();
			Assert.assertTrue(count > 0);
			
			Integer key = _stackableKeys.get(type);
			int oldSize = 0;
			if (null != key)
			{
				oldSize = _slots.get(key).stack.count();
			}
			int newSize = count + oldSize;
			if (null == key)
			{
				key = _slots.size() + 1;
				_stackableKeys.put(type, key);
			}
			_slots.put(key, ItemSlot.fromStack(new Items(type, newSize)));
			_currentEncumbrance += env.encumbrance.getEncumbrance(type) * count;
			return this;
		}
		public Builder addNonStackable(NonStackableItem item)
		{
			Environment env = Environment.getShared();
			
			Integer key = _slots.size() + 1;
			_slots.put(key, ItemSlot.fromNonStack(item));
			_currentEncumbrance += env.encumbrance.getEncumbrance(item.type());
			return this;
		}
		public Inventory finish()
		{
			return new Inventory(_maxEncumbrance
				, _slots
				, _currentEncumbrance
			);
		}
	}
}
