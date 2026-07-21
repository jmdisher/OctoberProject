package com.jeffdisher.october.types;


/**
 * This is the minimal wrapper over ISlotManager to provide read-only access to a read-only inventory.
 */
public class SlotReader implements ISlotManager
{
	private Inventory _inventory;

	public SlotReader(Inventory inventory)
	{
		_inventory = inventory;
	}

	@Override
	public int getCount(Item stackable)
	{
		return _inventory.getCount(stackable);
	}
}
