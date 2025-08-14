package com.jeffdisher.october.types;


/**
 * The high-level interface for modifying an Inventory object using a mutable wrapper.
 * Note that this could be a real wrapper over an Inventory or a special wrapper representing a creative inventory.
 */
public interface IMutableInventory
{
	/**
	 * Checks the stackable items in this inventory to see if we have any of this type.
	 * 
	 * @param type The type to check.
	 * @return The key this inventory uses to address the stack of this type or 0 if not known.
	 */
	public int getIdOfStackableType(Item type);

	/**
	 * Looks up the item stack for the given identifier key.  While this is usually called when we know that the key is
	 * here, we sometimes call it to see if a key has disappeared.
	 * 
	 * @param key The identifier key.
	 * @return The Items object for this stack (null if this key is not in the inventory).
	 */
	public Items getStackForKey(int key);

	/**
	 * Looks up the item stack for the given identifier key.
	 * 
	 * @param key The identifier key.
	 * @return The NonStackable object for this stack (null if stackable).
	 */
	public NonStackableItem getNonStackableForKey(int key);

	/**
	 * Looks up the item slot for the given identifier key.  This will return null only if there is no such key in the
	 * inventory.
	 * 
	 * @param key The identifier key.
	 * @return The ItemSlot for this key (null if this key is not in the inventory).
	 */
	public ItemSlot getSlotForKey(int key);

	/**
	 * A basic helper to check how many items of a given type are in the inventory.  This cannot be called if the item
	 * is non-stackable.
	 * 
	 * @param type The item type.
	 * @return The number of items of this type.
	 */
	public int getCount(Item type);

	/**
	 * Adds all of the given items to the inventory, failing if they can't all be added.
	 * 
	 * @param type The type of item to add.
	 * @param count The number of items of that type.
	 * @return True if the items were all added, false if none were.
	 */
	public boolean addAllItems(Item type, int count);

	/**
	 * Attempts to add at least some of the given items to the inventory.
	 * 
	 * @param type The type of item to add.
	 * @param count The number of items of that type.
	 * @return The number of items which were actually added.
	 */
	public int addItemsBestEfforts(Item type, int count);

	/**
	 * Adds all of the given items to the inventory, even if it causes it to become over-filled.
	 * 
	 * @param type The type of item to add.
	 * @param count The number of items of that type.
	 */
	public void addItemsAllowingOverflow(Item type, int count);

	/**
	 * Attempts to add the given nonStackable to the inventory but will NOT over-fill.
	 * 
	 * @param nonStackable The item to attempt to add.
	 * @return True if it was added or false if it couldn't fit.
	 */
	public boolean addNonStackableBestEfforts(NonStackableItem nonStackable);

	/**
	 * Adds the given nonStackable item to the inventory, potentially causing it to become over-filled.
	 * 
	 * @param nonStackable The item to add.
	 */
	public void addNonStackableAllowingOverflow(NonStackableItem nonStackable);

	/**
	 * Replaces an existing non-stackable with a new instance.
	 * 
	 * @param key The key used to address the item.
	 * @param updated The new instance.
	 */
	public void replaceNonStackable(int key, NonStackableItem updated);

	/**
	 * Checks how many of a given item type can be added to the inventory.
	 * 
	 * @param type The item type.
	 * @return The number of these items which can be added.
	 */
	public int maxVacancyForItem(Item type);

	/**
	 * Removes the given number of items of the given type from the inventory.
	 * Note that there must be at least this many items of the type before calling this.
	 * 
	 * @param type The type of item to remove.
	 * @param count The number of items of that type to remove.
	 */
	public void removeStackableItems(Item type, int count);

	/**
	 * Removes a non-stackable from the inventory.  Note that this asserts that the item is present as a non-stackable.
	 * 
	 * @param key The key of the item to remove.
	 */
	public void removeNonStackableItems(int key);

	/**
	 * @return The current encumbrance of the inventory (0 implies no items are stored).
	 */
	public int getCurrentEncumbrance();
}
