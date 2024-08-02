package com.jeffdisher.october.types;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.utils.Assert;


/**
 * We use this fake mutable inventory implementation for cases where we need an inventory to modify but we don't want
 * to use a real inventory.
 * This implementation will show all the known items with an unlimited (MAX_INT) count or durability and will never
 * change these numbers whether dropping them or accepting them.
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
				builder.addStackable(item, 1);
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

	public CreativeInventory()
	{
		_env = Environment.getShared();
	}

	@Override
	public int getIdOfStackableType(Item type)
	{
		return type.number();
	}

	@Override
	public Items getStackForKey(int key)
	{
		Items items = null;
		if (key > 0)
		{
			Item item = _env.items.ITEMS_BY_TYPE[key];
			if (_env.durability.isStackable(item))
			{
				items = new Items(item, Integer.MAX_VALUE);
			}
		}
		return items;
	}

	@Override
	public NonStackableItem getNonStackableForKey(int key)
	{
		NonStackableItem nonStack = null;
		if (key > 0)
		{
			Item item = _env.items.ITEMS_BY_TYPE[key];
			if (!_env.durability.isStackable(item))
			{
				nonStack = new NonStackableItem(item, _env.durability.getDurability(item));
			}
		}
		return nonStack;
	}

	@Override
	public int getCount(Item type)
	{
		Assert.assertTrue(_env.durability.isStackable(type));
		return Integer.MAX_VALUE;
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
			Assert.assertTrue(key == updated.type().number());
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
		if (key > 0)
		{
			Item item = _env.items.ITEMS_BY_TYPE[key];
			Assert.assertTrue(!_env.durability.isStackable(item));
		}
	}

	@Override
	public int getCurrentEncumbrance()
	{
		// Just say that we are empty.
		return 0;
	}
}
