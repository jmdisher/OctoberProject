package com.jeffdisher.october.types;

import java.util.HashMap;
import java.util.Map;

import com.jeffdisher.october.utils.Assert;


/**
 * A mutable wrapper over an Inventory, providing high-level helpers to manipulate it.
 */
public class MutableInventory
{
	private final int _maxEncumbrance;
	private final Map<Item, Items> _items;
	private int _currentEncumbrance;

	/**
	 * Deconstructs the original inventory into a mutable version.
	 * 
	 * @param original The inventory to clone.
	 */
	public MutableInventory(Inventory original)
	{
		_maxEncumbrance = original.maxEncumbrance;
		_items = new HashMap<>(original.items);
		_currentEncumbrance = original.currentEncumbrance;
	}

	/**
	 * Used to check how many items of a given type are in the inventory.
	 * 
	 * @param type The item type.
	 * @return The number of items of this type.
	 */
	public int getCount(Item type)
	{
		Items existing = _items.get(type);
		return (null != existing)
				? existing.count()
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
		int requiredEncumbrance = type.encumbrance() * count;
		int updatedEncumbrance = _currentEncumbrance + requiredEncumbrance;
		
		boolean didApply = false;
		if (updatedEncumbrance <= _maxEncumbrance)
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
		int availableEncumbrance = _maxEncumbrance - _currentEncumbrance;
		int maxToAdd = availableEncumbrance / type.encumbrance();
		int countToAdd = Math.min(maxToAdd, count);
		
		_addItems(type, countToAdd);
		return countToAdd;
	}

	/**
	 * Checks how many of a given item type can be added to the inventory.
	 * 
	 * @param type The item type.
	 * @return The number of these items which can be added.
	 */
	public int maxVacancyForItem(Item type)
	{
		int availableEncumbrance = _maxEncumbrance - _currentEncumbrance;
		int maxToAdd = availableEncumbrance / type.encumbrance();
		return maxToAdd;
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
		// We assume that someone checked before calling this in order to make a decision.
		Items existing = _items.get(type);
		int startCount = existing.count();
		Assert.assertTrue(startCount >= count);
		
		int newCount = startCount - count;
		if (newCount > 0)
		{
			_items.put(type, new Items(type, newCount));
		}
		else
		{
			_items.remove(type);
		}
		int removedEncumbrance = type.encumbrance() * count;
		_currentEncumbrance = _currentEncumbrance - removedEncumbrance;
	}

	/**
	 * Creates an immutable copy of the receiver.
	 * 
	 * @return The new immutable copy of the current state.
	 */
	public Inventory freeze()
	{
		return new Inventory(_maxEncumbrance, _items, _currentEncumbrance);
	}


	private void _addItems(Item type, int count)
	{
		int requiredEncumbrance = type.encumbrance() * count;
		int updatedEncumbrance = _currentEncumbrance + requiredEncumbrance;
		
		Items existing = _items.get(type);
		int newCount = (null != existing)
				? (existing.count() + count)
				: count
		;
		Items updated = new Items(type, newCount);
		_items.put(type, updated);
		_currentEncumbrance = updatedEncumbrance;
		Assert.assertTrue(_currentEncumbrance <= _maxEncumbrance);
	}
}
