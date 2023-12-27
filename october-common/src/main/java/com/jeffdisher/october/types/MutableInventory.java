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

	public MutableInventory(Inventory original)
	{
		_maxEncumbrance = original.maxEncumbrance;
		_items = new HashMap<>(original.items);
		_currentEncumbrance = original.currentEncumbrance;
	}

	public int getCount(Item type)
	{
		Items existing = _items.get(type);
		return (null != existing)
				? existing.count()
				: 0
		;
	}

	public boolean addItems(Item type, int count)
	{
		int requiredEncumbrance = type.encumbrance() * count;
		int updatedEncumbrance = _currentEncumbrance + requiredEncumbrance;
		
		boolean didApply = false;
		if (updatedEncumbrance <= _maxEncumbrance)
		{
			Items existing = _items.get(type);
			int newCount = (null != existing)
					? (existing.count() + count)
					: count
			;
			Items updated = new Items(type, newCount);
			_items.put(type, updated);
			_currentEncumbrance = updatedEncumbrance;
			didApply = true;
		}
		return didApply;
	}

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

	public Inventory freeze()
	{
		return new Inventory(_maxEncumbrance, _items, _currentEncumbrance);
	}
}
