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
	 * Checks the stackable items in this inventory to see if we have any of this type.
	 * 
	 * @param type The type to check.
	 * @return The key this inventory uses to address the stack of this type or 0 if not known.
	 */
	public int getIdOfStackableType(Item type)
	{
		return _getKeyForType(type);
	}

	/**
	 * Looks up the item stack for the given identifier key.  While this is usually called when we know that the key is
	 * here, we sometimes call it to see if a key has disappeared.
	 * 
	 * @param key The identifier key.
	 * @return The Items object for this stack (null if this key is not in the inventory).
	 */
	public Items getStackForKey(int key)
	{
		return _items.get(key);
	}

	/**
	 * Used to check how many items of a given type are in the inventory.
	 * 
	 * @param type The item type.
	 * @return The number of items of this type.
	 */
	public int getCount(Item type)
	{
		int id = _getKeyForType(type);
		return (id > 0)
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
	 * Removes all items from the inventory, setting its encumbrance to 0.  If the replacement is non-null, the state
	 * will then be updated to contain whatever it provided.
	 * 
	 * @param replacement If non-null, the receiver will be populated with this after being cleared.
	 */
	public void clearInventory(Inventory replacement)
	{
		_items.clear();
		_currentEncumbrance = 0;
		_nextAddressId = 1;
		
		if (null != replacement)
		{
			// This only makes sense if these have the same max encumbrance.
			Assert.assertTrue(_original.maxEncumbrance == replacement.maxEncumbrance);
			
			int lastId = 0;
			for (Integer key : replacement.sortedKeys())
			{
				_items.put(key, replacement.getStackForKey(key));
				lastId = key;
			}
			_currentEncumbrance = replacement.currentEncumbrance;
			_nextAddressId = lastId + 1;
		}
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
		int id = _getKeyForType(type);
		if (id > 0)
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

	private int _getKeyForType(Item type)
	{
		int id = 0;
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
