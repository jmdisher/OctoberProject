package com.jeffdisher.october.types;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.utils.Assert;


/**
 * We use this fake mutable inventory implementation for cases where we need an inventory to modify but we don't want
 * to use a real inventory.
 * This implementation will include 1 of every item which is non-stackable or stackable with non-zero encumbrance but
 * is internally immutable and claims to accept MAX_INT instances of each item.
 */
public class CreativeInventory implements IMutableInventory
{
	/**
	 * A helper to return a faked-up inventory with 1 of everything.
	 * 
	 * @return A fake read-only inventory instance.
	 */
	public static Inventory fakeInventory()
	{
		Inventory.Builder builder = Inventory.start(Integer.MAX_VALUE);
		Environment env = Environment.getShared();
		for (Item item : env.items.ITEMS_BY_TYPE)
		{
			if (env.special.AIR.item() == item)
			{
				// We will skip air since it is 0 (and we want the keys to sync up) but also it can't be placed/used.
			}
			else if (env.durability.isStackable(item))
			{
				// We only want to include stackable items if they have a non-zero encumbrance since those items are only in-world block modes.
				if (env.encumbrance.getEncumbrance(item) > 0)
				{
					builder.addStackable(item, 1);
				}
			}
			else
			{
				NonStackableItem nonStackable = new NonStackableItem(item, env.durability.getDurability(item));
				builder.addNonStackable(nonStackable);
			}
		}
		return builder.finish();
	}


	private final Environment _env;
	private final Inventory _inventory;

	public CreativeInventory()
	{
		_env = Environment.getShared();
		_inventory = fakeInventory();
	}

	@Override
	public int getIdOfStackableType(Item type)
	{
		return _inventory.getIdOfStackableType(type);
	}

	@Override
	public Items getStackForKey(int key)
	{
		return _inventory.getStackForKey(key);
	}

	@Override
	public NonStackableItem getNonStackableForKey(int key)
	{
		return _inventory.getNonStackableForKey(key);
	}

	@Override
	public int getCount(Item type)
	{
		return _inventory.getCount(type);
	}

	@Override
	public boolean addAllItems(Item type, int count)
	{
		Assert.assertTrue(_env.durability.isStackable(type));
		return true;
	}

	@Override
	public int addItemsBestEfforts(Item type, int count)
	{
		Assert.assertTrue(_env.durability.isStackable(type));
		return count;
	}

	@Override
	public void addItemsAllowingOverflow(Item type, int count)
	{
		Assert.assertTrue(_env.durability.isStackable(type));
	}

	@Override
	public boolean addNonStackableBestEfforts(NonStackableItem nonStackable)
	{
		// We will say that we accept this but will just drop it (we only have one of every non-stackable).
		Assert.assertTrue(!_env.durability.isStackable(nonStackable.type()));
		return true;
	}

	@Override
	public void addNonStackableAllowingOverflow(NonStackableItem nonStackable)
	{
		// We will just drop this (we only have one of every non-stackable).
		Assert.assertTrue(!_env.durability.isStackable(nonStackable.type()));
	}

	@Override
	public void replaceNonStackable(int key, NonStackableItem updated)
	{
		// We will just drop this (we only have one of every non-stackable).
		if (key > 0)
		{
			Assert.assertTrue(!_env.durability.isStackable(updated.type()));
		}
	}

	@Override
	public int maxVacancyForItem(Item type)
	{
		return Integer.MAX_VALUE;
	}

	@Override
	public void removeStackableItems(Item type, int count)
	{
		Assert.assertTrue(_env.durability.isStackable(type));
	}

	@Override
	public void removeNonStackableItems(int key)
	{
		Assert.assertTrue(null != _inventory.getNonStackableForKey(key));
	}

	@Override
	public int getCurrentEncumbrance()
	{
		// Just say that we are empty.
		return 0;
	}
}
