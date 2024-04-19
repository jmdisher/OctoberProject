package com.jeffdisher.october.types;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.utils.Assert;


/**
 * A mutable wrapper over an Inventory, providing high-level helpers to manipulate it.
 */
public class MutableInventory
{
	private final Inventory _original;
	private final Map<Integer, Items> _items;
	private int _currentEncumbrance;
	private int _nextAddressId;

	/**
	 * Deconstructs the original inventory into a mutable version.
	 * 
	 * @param original The inventory to clone.
	 */
	public MutableInventory(Inventory original)
	{
		_original = original;
		_items = new HashMap<>();
		int lastId = 0;
		for (Integer key : original.sortedKeys())
		{
			_items.put(key, original.getStackForKey(key));
			lastId = key;
		}
		_currentEncumbrance = original.currentEncumbrance;
		_nextAddressId = lastId + 1;
	}

	/**
	 * Used to check how many items of a given type are in the inventory.
	 * 
	 * @param type The item type.
	 * @return The number of items of this type.
	 */
	public int getCount(Item type)
	{
		Integer id = _getKeyForType(type);
		return (null != id)
				? _items.get(id).count()
				: 0
		;
	}

	/**
	 * Adds all of the given items to the inventory, failing if they can't all be added.
	 * 
	 * @param type The type of item to add.
	 * @param count The number of items of that type.
	 * @return True if the items were all added, false if none were.
	 */
	public boolean addAllItems(Item type, int count)
	{
		Environment env = Environment.getShared();
		int requiredEncumbrance = env.inventory.getEncumbrance(type) * count;
		int updatedEncumbrance = _currentEncumbrance + requiredEncumbrance;
		
		boolean didApply = false;
		if (updatedEncumbrance <= _original.maxEncumbrance)
		{
			_addItems(type, count);
			didApply = true;
		}
		return didApply;
	}

	/**
	 * Attempts to add at least some of the given items to the inventory.
	 * 
	 * @param type The type of item to add.
	 * @param count The number of items of that type.
	 * @return The number of items which were actually added.
	 */
	public int addItemsBestEfforts(Item type, int count)
	{
		Environment env = Environment.getShared();
		int itemEncumbrance = env.inventory.getEncumbrance(type);
		int availableEncumbrance = _original.maxEncumbrance - _currentEncumbrance;
		int added = 0;
		if ((itemEncumbrance > 0) && (availableEncumbrance > 0))
		{
			int maxToAdd = availableEncumbrance / itemEncumbrance;
			int countToAdd = Math.min(maxToAdd, count);
			
			_addItems(type, countToAdd);
			added = countToAdd;
		}
		return added;
	}

	/**
	 * Adds all of the given items to the inventory, even if it causes it to become over-filled.
	 * 
	 * @param type The type of item to add.
	 * @param count The number of items of that type.
	 */
	public void addItemsAllowingOverflow(Item type, int count)
	{
		_addItems(type, count);
	}

	/**
	 * Checks how many of a given item type can be added to the inventory.
	 * 
	 * @param type The item type.
	 * @return The number of these items which can be added.
	 */
	public int maxVacancyForItem(Item type)
	{
		Environment env = Environment.getShared();
		int itemEncumbrance = env.inventory.getEncumbrance(type);
		int availableEncumbrance = _original.maxEncumbrance - _currentEncumbrance;
		int vacancy = 0;
		if ((itemEncumbrance > 0) && (availableEncumbrance > 0))
		{
			vacancy = availableEncumbrance / env.inventory.getEncumbrance(type);
		}
		return vacancy;
	}

	/**
	 * Removes the given number of items of the given type from the inventory.
	 * Note that there must be at least this many items of the type before calling this.
	 * 
	 * @param type The type of item to remove.
	 * @param count The number of items of that type to remove.
	 */
	public void removeItems(Item type, int count)
	{
		Environment env = Environment.getShared();
		// We assume that someone checked before calling this in order to make a decision.
		Integer id = _getKeyForType(type);
		Items existing = _items.get(id);
		int startCount = existing.count();
		Assert.assertTrue(startCount >= count);
		
		int newCount = startCount - count;
		if (newCount > 0)
		{
			_items.put(id, new Items(type, newCount));
		}
		else
		{
			_items.remove(id);
		}
		int removedEncumbrance = env.inventory.getEncumbrance(type) * count;
		_currentEncumbrance = _currentEncumbrance - removedEncumbrance;
	}

	/**
	 * @return The current encumbrance of the inventory (0 implies no items are stored).
	 */
	public int getCurrentEncumbrance()
	{
		return _currentEncumbrance;
	}

	/**
	 * Removes all items from the inventory, setting its encumbrance to 0.
	 */
	public void clearInventory()
	{
		_items.clear();
		_currentEncumbrance = 0;
		_nextAddressId = 1;
	}

	/**
	 * Creates an immutable copy of the receiver.
	 * Note that this will return the original instance if a new instance would have been identical.
	 * 
	 * @return The new immutable copy of the current state.
	 */
	public Inventory freeze()
	{
		// Compare this to the original (which is somewhat expensive).
		List<Integer> originalKeyList = _original.sortedKeys();
		boolean doMatch = (_currentEncumbrance == _original.currentEncumbrance) && (_items.size() == originalKeyList.size());
		if (doMatch)
		{
			for (Integer key : originalKeyList)
			{
				Items newItems = _items.get(key);
				Item newType = (null != newItems)
						? newItems.type()
						: null
				;
				int newCount = (null != newItems)
						? newItems.count()
						: 0
				;
				Items originalItems = _original.getStackForKey(key);
				int originalCount = originalItems.count();
				if ((newType != originalItems.type()) || (newCount != originalCount))
				{
					doMatch = false;
					break;
				}
			}
		}
		return doMatch
				? _original
				: Inventory.build(_original.maxEncumbrance, _items, _currentEncumbrance)
		;
	}


	private void _addItems(Item type, int count)
	{
		Environment env = Environment.getShared();
		int requiredEncumbrance = env.inventory.getEncumbrance(type) * count;
		int updatedEncumbrance = _currentEncumbrance + requiredEncumbrance;
		
		int newCount;
		Integer id = _getKeyForType(type);
		if (null != id)
		{
			Items existing = _items.get(id);
			newCount = existing.count() + count;
		}
		else
		{
			id = _nextAddressId;
			_nextAddressId += 1;
			newCount = count;
		}
		Items updated = new Items(type, newCount);
		_items.put(id, updated);
		_currentEncumbrance = updatedEncumbrance;
	}

	private Integer _getKeyForType(Item type)
	{
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
}
