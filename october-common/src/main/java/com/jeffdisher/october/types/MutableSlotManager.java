package com.jeffdisher.october.types;

import com.jeffdisher.october.utils.Assert;


/**
 * This represents a mutable wrapper over an Inventory, hotbar, and currently selected hotbar index.
 * The reason for this type is that these pieces of data are connected (the hotbar contains inventory keys) so
 * interactions with them need to be coordinated.
 */
public class MutableSlotManager implements ISlotManager
{
	private IMutableInventory _inventory;
	// Note that this hotbar is externally created and owned, this class merely modifies the contents.
	private int[] _hotbar;
	private int _hotbarIndex;

	public MutableSlotManager(IMutableInventory inventory
		, int[] hotbar
		, int hotbarIndex
	)
	{
		_inventory = inventory;
		_hotbar = hotbar;
		_hotbarIndex = hotbarIndex;
	}

	@Override
	public int getCount(Item stackable)
	{
		return _inventory.getCount(stackable);
	}

	/**
	 * Checks how many of a given item type can be added to the inventory.
	 * 
	 * @param type The item type.
	 * @return The number of these items which can be added.
	 */
	public int maxVacancyForItem(Item type)
	{
		return _inventory.maxVacancyForItem(type);
	}

	/**
	 * @return The current hotbar index (0-indexed) which is never invalid.
	 */
	public int getHotbarIndex()
	{
		return _hotbarIndex;
	}

	/**
	 * Changes the internal hotbar index, returning whether or not anything changed.
	 * 
	 * @param index The new index (must be valid).
	 * @return True if this changed the internal index or false if this changed nothing.
	 */
	public boolean setHotbarIndex(int index)
	{
		Assert.assertTrue(index >= 0);
		Assert.assertTrue(index < _hotbar.length);
		
		boolean didApply = false;
		if (_hotbarIndex != index)
		{
			_hotbarIndex = index;
			didApply = true;
		}
		return didApply;
	}

	/**
	 * Searches the hotbar to find an index which has the key stored in it, returning -1 if not found.
	 * 
	 * @param key The key to search.
	 * @return The index where the key is in the hotbar (-1 if not found).
	 */
	public int getHotbarIndexOfKey(int key)
	{
		int selected = -1;
		for (int i = 0; i < _hotbar.length; ++i)
		{
			if (key == _hotbar[i])
			{
				selected = i;
				break;
			}
		}
		return selected;
	}

	/**
	 * @return The key in the currently selected hotbar index of the hotbar.
	 */
	public int getSelectedKey()
	{
		return _hotbar[_hotbarIndex];
	}

	/**
	 * Sets the currently selected hotbar index of the hotbar to the given key, clearing any other hotbar references
	 * which already contain this key.  Can be given Entity.NO_SELECTION to clear the current index.
	 * 
	 * @param key The key to set.
	 */
	public void setSelectedKey(int key)
	{
		// This might be used to set to something valid or nothing.
		if (Entity.NO_SELECTION != key)
		{
			// Make sure that we clear any existing uses of this key.
			_removeHotbarKey(key);
		}
		_hotbar[_hotbarIndex] = key;
	}

	/**
	 * Checks the stackable items in this inventory to see if we have any of this type.
	 * 
	 * @param type The type to check.
	 * @return The key this inventory uses to address the stack of this type or 0 if not known.
	 */
	public int getKeyForStackable(Item type)
	{
		return _inventory.getIdOfStackableType(type);
	}

	/**
	 * Checks the given non-stackable item instance against the instances in this inventory, returning the key or 0 if
	 * not found.
	 * NOTE:  This is an instance-comparison only.
	 * 
	 * @param nonStackable The specific instance to check.
	 * @return The key this inventory uses to address the given non-stackable instance or 0 if not known.
	 */
	public int getKeyForNonStackableInstance(NonStackableItem nonStackable)
	{
		return _inventory.getIdOfNonStackableInstance(nonStackable);
	}

	/**
	 * Looks up the item slot for the given identifier key.  This will return null only if there is no such key in the
	 * inventory.
	 * 
	 * @param key The identifier key.
	 * @return The ItemSlot for this key (null if this key is not in the inventory).
	 */
	public ItemSlot getSlot(int key)
	{
		// This returns null if not there (generally if Entity.NO_SELECTION).
		return _inventory.getSlotForKey(key);
	}

	/**
	 * Replaces an existing non-stackable with a new instance.
	 * 
	 * @param key The key used to address the item.
	 * @param updated The new instance.
	 */
	public void replaceNonStackable(int key, NonStackableItem updated)
	{
		// Here, we change nothing about hotbar.
		_inventory.replaceNonStackable(key, updated);
	}

	/**
	 * Removes a non-stackable from the inventory.  Note that this asserts that the item is present as a non-stackable.
	 * NOTE:  This will also remove any references to this now-removed item from the hotbar.
	 * 
	 * @param key The key of the item to remove.
	 */
	public void removeNonStackable(int key)
	{
		// In this case, we also need to remove this from the hotbar.
		_inventory.removeNonStackableItems(key);
		_removeHotbarKey(key);
	}

	/**
	 * Clears all the hotbar entries without changing the selected index.
	 */
	public void clearHotbar()
	{
		_clearHotbar();
	}

	/**
	 * Removed count instances of the referenced stackable type from the underlying inventory.  If this means that there
	 * are no longer any instances of stackable left, any references in the hotbar will also be cleared.
	 * 
	 * @param stackable The stackable type to remove (must be in inventory).
	 * @param count The number of instances to remove (must be >0).
	 */
	public void removeStackable(Item stackable, int count)
	{
		Assert.assertTrue(count > 0);
		
		// If this causes us to use up the last, then clear anything in the hotbar.
		int key = _inventory.getIdOfStackableType(stackable);
		
		// In this case, we assume that we have some (the caller should have checked).
		Assert.assertTrue(Entity.NO_SELECTION != key);
		
		_inventory.removeStackableItems(stackable, count);
		
		// If we emptied the slot, clear the hotbar.
		if (null == _inventory.getSlotForKey(key))
		{
			_removeHotbarKey(key);
		}
	}

	/**
	 * Adds all of the given items to the inventory, failing if they can't all be added.
	 * 
	 * @param type The type of item to add.
	 * @param count The number of items of that type.
	 * @return True if the items were all added, false if none were.
	 */
	public boolean addStackableItems(Item stackable, int count)
	{
		return _inventory.addAllItems(stackable, count);
	}

	/**
	 * Adds all of the given items to the inventory, failing if they can't all be added.
	 * 
	 * @param type The type of item to add.
	 * @param count The number of items of that type.
	 * @return True if the items were all added, false if none were.
	 */
	public int addStackableBestEfforts(Item stackable, int count)
	{
		return _inventory.addItemsBestEfforts(stackable, count);
	}

	/**
	 * Attempts to add the given nonStackable to the inventory but will NOT over-fill.
	 * 
	 * @param nonStackable The item to attempt to add.
	 * @return True if it was added or false if it couldn't fit.
	 */
	public boolean addNonStackable(NonStackableItem nonStackable)
	{
		// Note that we eventually want to get rid of "best efforts" and have this always add everything.
		// This depends on some some decisions around crafting operations and what to do in the case of overflow (stay in overflow or spill on the ground).
		return _inventory.addNonStackableBestEfforts(nonStackable);
	}

	/**
	 * Adds the given nonStackable item to the inventory, potentially causing it to become over-filled.
	 * 
	 * @param nonStackable The item to add.
	 */
	public void addNonStackableAllowingOverflow(NonStackableItem nonStackable)
	{
		// TODO:  Replace this method with the above "addNonStackable()" once we avoid the best efforts.
		_inventory.addNonStackableAllowingOverflow(nonStackable);
	}

	/**
	 * Replaces the internal inventory with this new one and clears the hotbar (since the hotbar keys are associated
	 * with the inventory instance).
	 * 
	 * @param inventory The new inventory to install.
	 */
	public void setInventory(IMutableInventory inventory)
	{
		Assert.assertTrue(null != inventory);
		
		// Called when we change between creative/survival inventories.  We just want to hold this and clear the hotbar (since it is coupled to the inventory).
		_inventory = inventory;
		_clearHotbar();
	}

	/**
	 * Overwrites the existing hotbar with the contents of the given hotbar.  Note that this is an overwrite instead of
	 * a replacement since our internal hotbar is defined as being shared state.
	 * 
	 * @param hotbar The new hotbar contents.
	 */
	public void setHotbar(int[] hotbar)
	{
		// This is called when we are accepting changes from the server.
		// We are expecting that the hotbar we were given is a shared instance so over-write it.
		System.arraycopy(hotbar, 0, _hotbar, 0, hotbar.length);
	}

	/**
	 * Used by tests in order to expose write-access to the internal hotbar instance.
	 * 
	 * @return The shared hotbar instance.
	 */
	public int[] test_unsafeHotbar()
	{
		return _hotbar;
	}


	private void _removeHotbarKey(int key)
	{
		for (int i = 0; i < _hotbar.length; ++i)
		{
			if (key == _hotbar[i])
			{
				// This is missing so clear it.
				_hotbar[i] = Entity.NO_SELECTION;
			}
		}
	}

	private void _clearHotbar()
	{
		for (int i = 0; i < _hotbar.length; ++i)
		{
			_hotbar[i] = Entity.NO_SELECTION;
		}
	}
}
