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
	private final Map<Integer, Items> _stackable;
	private final Map<Integer, NonStackableItem> _nonStackable;
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
		_stackable = new HashMap<>();
		_nonStackable = new HashMap<>();
		int lastId = 0;
		for (Integer key : original.sortedKeys())
		{
			Items stackable = original.getStackForKey(key);
			if (null != stackable)
			{
				_stackable.put(key, stackable);
			}
			else
			{
				_nonStackable.put(key, original.getNonStackableForKey(key));
			}
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
		Assert.assertTrue(null != type);
		return _getKeyForStackableType(type);
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
		Assert.assertTrue(null != type);
		
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
	 * Adds all of the given items to the inventory, failing if they can't all be added.
	 * 
	 * @param type The type of item to add.
	 * @param count The number of items of that type.
	 * @return True if the items were all added, false if none were.
	 */
	public boolean addAllItems(Item type, int count)
	{
		Assert.assertTrue(null != type);
		Assert.assertTrue(count > 0);
		
		Environment env = Environment.getShared();
		int requiredEncumbrance = env.inventory.getEncumbrance(type) * count;
		int updatedEncumbrance = _currentEncumbrance + requiredEncumbrance;
		
		boolean didApply = false;
		if (updatedEncumbrance <= _original.maxEncumbrance)
		{
			_addStackableItems(type, count);
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
		Assert.assertTrue(null != type);
		Assert.assertTrue(count > 0);
		
		Environment env = Environment.getShared();
		int itemEncumbrance = env.inventory.getEncumbrance(type);
		int availableEncumbrance = _original.maxEncumbrance - _currentEncumbrance;
		int added = 0;
		if ((itemEncumbrance > 0) && (availableEncumbrance > 0))
		{
			int maxToAdd = availableEncumbrance / itemEncumbrance;
			int countToAdd = Math.min(maxToAdd, count);
			
			if (countToAdd > 0)
			{
				_addStackableItems(type, countToAdd);
				added = countToAdd;
			}
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
		Assert.assertTrue(null != type);
		Assert.assertTrue(count > 0);
		
		_addStackableItems(type, count);
	}

	/**
	 * Attempts to add the given nonStackable to the inventory but will NOT over-fill.
	 * 
	 * @param nonStackable The item to attempt to add.
	 * @return True if it was added or false if it couldn't fit.
	 */
	public boolean addNonStackableBestEfforts(NonStackableItem nonStackable)
	{
		Assert.assertTrue(null != nonStackable);
		
		Environment env = Environment.getShared();
		int itemEncumbrance = env.inventory.getEncumbrance(nonStackable.type());
		int availableEncumbrance = _original.maxEncumbrance - _currentEncumbrance;
		boolean didAdd = false;
		if (itemEncumbrance <= availableEncumbrance)
		{
			_nonStackable.put(_nextAddressId, nonStackable);
			_nextAddressId += 1;
			_currentEncumbrance += itemEncumbrance;
			didAdd = true;
		}
		return didAdd;
	}

	/**
	 * Adds the given nonStackable item to the inventory, potentially causing it to become over-filled.
	 * 
	 * @param nonStackable The item to add.
	 */
	public void addNonStackableAllowingOverflow(NonStackableItem nonStackable)
	{
		Assert.assertTrue(null != nonStackable);
		
		Environment env = Environment.getShared();
		int itemEncumbrance = env.inventory.getEncumbrance(nonStackable.type());
		_nonStackable.put(_nextAddressId, nonStackable);
		_nextAddressId += 1;
		_currentEncumbrance += itemEncumbrance;
	}

	/**
	 * Replaces an existing non-stackable with a new instance.
	 * 
	 * @param key The key used to address the item.
	 * @param updated The new instance.
	 */
	public void replaceNonStackable(int key, NonStackableItem updated)
	{
		Assert.assertTrue(key > 0);
		Assert.assertTrue(null != updated);
		
		NonStackableItem old = _nonStackable.put(key, updated);
		// We expect it was already here.
		Assert.assertTrue(null != old);
	}

	/**
	 * Checks how many of a given item type can be added to the inventory.
	 * 
	 * @param type The item type.
	 * @return The number of these items which can be added.
	 */
	public int maxVacancyForItem(Item type)
	{
		Assert.assertTrue(null != type);
		
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
	public void removeStackableItems(Item type, int count)
	{
		Assert.assertTrue(null != type);
		Assert.assertTrue(count > 0);
		
		Environment env = Environment.getShared();
		// We assume that someone checked before calling this in order to make a decision.
		Integer id = _getKeyForStackableType(type);
		Items existing = _stackable.get(id);
		int startCount = existing.count();
		Assert.assertTrue(startCount >= count);
		
		int newCount = startCount - count;
		if (newCount > 0)
		{
			_stackable.put(id, new Items(type, newCount));
		}
		else
		{
			_stackable.remove(id);
		}
		int removedEncumbrance = env.inventory.getEncumbrance(type) * count;
		_currentEncumbrance = _currentEncumbrance - removedEncumbrance;
	}

	/**
	 * Removes a non-stackable from the inventory.  Note that this asserts that the item is present as a non-stackable.
	 * 
	 * @param key The key of the item to remove.
	 */
	public void removeNonStackableItems(int key)
	{
		Assert.assertTrue(key > 0);
		
		Environment env = Environment.getShared();
		NonStackableItem removed = _nonStackable.remove(key);
		Assert.assertTrue(null != removed);
		_currentEncumbrance -= env.inventory.getEncumbrance(removed.type());
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
		_stackable.clear();
		_nonStackable.clear();
		_currentEncumbrance = 0;
		_nextAddressId = 1;
		
		if (null != replacement)
		{
			// This only makes sense if these have the same max encumbrance.
			Assert.assertTrue(_original.maxEncumbrance == replacement.maxEncumbrance);
			
			int lastId = 0;
			for (Integer key : replacement.sortedKeys())
			{
				Items stackable = replacement.getStackForKey(key);
				if (null != stackable)
				{
					_stackable.put(key, stackable);
				}
				else
				{
					_nonStackable.put(key, replacement.getNonStackableForKey(key));
				}
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
		boolean doMatch = (_currentEncumbrance == _original.currentEncumbrance) && ((_stackable.size() + _nonStackable.size()) == originalKeyList.size());
		if (doMatch)
		{
			for (Integer key : originalKeyList)
			{
				Items newItems = _stackable.get(key);
				if (null != newItems)
				{
					Items originalItems = _original.getStackForKey(key);
					if ((newItems.type() != originalItems.type()) || (newItems.count() != originalItems.count()))
					{
						doMatch = false;
						break;
					}
				}
				else
				{
					NonStackableItem nonStackable = _nonStackable.get(key);
					NonStackableItem originalNonStackable = _original.getNonStackableForKey(key);
					if ((null != nonStackable) && (null != originalNonStackable))
					{
						if (!nonStackable.equals(originalNonStackable))
						{
							doMatch = false;
							break;
						}
					}
					else
					{
						doMatch = false;
						break;
					}
				}
			}
		}
		return doMatch
				? _original
				: Inventory.build(_original.maxEncumbrance, _stackable, _nonStackable, _currentEncumbrance)
		;
	}


	private void _addStackableItems(Item type, int count)
	{
		Environment env = Environment.getShared();
		int requiredEncumbrance = env.inventory.getEncumbrance(type) * count;
		int updatedEncumbrance = _currentEncumbrance + requiredEncumbrance;
		
		int newCount;
		int id = _getKeyForStackableType(type);
		if (id > 0)
		{
			Items existing = _stackable.get(id);
			newCount = existing.count() + count;
		}
		else
		{
			id = _nextAddressId;
			_nextAddressId += 1;
			newCount = count;
		}
		Items updated = new Items(type, newCount);
		_stackable.put(id, updated);
		_currentEncumbrance = updatedEncumbrance;
	}

	private int _getKeyForStackableType(Item type)
	{
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
}
