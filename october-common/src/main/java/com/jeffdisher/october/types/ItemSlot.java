package com.jeffdisher.october.types;

import com.jeffdisher.october.utils.Assert;


/**
 * Contains either a stack of Items or a single NonStackableItem, providing some useful helpers to access common
 * elements like the Item type.  This allows a single "slot" for items to be described as a self-contained object.
 */
public class ItemSlot
{
	public static ItemSlot fromStack(Items stack)
	{
		return new ItemSlot(stack, null);
	}

	public static ItemSlot fromNonStack(NonStackableItem nonStackable)
	{
		return new ItemSlot(null, nonStackable);
	}


	public final Items stack;
	public final NonStackableItem nonStackable;

	private ItemSlot(Items stack, NonStackableItem nonStackable)
	{
		// Explicitly 1 of these must be non-null.
		Assert.assertTrue((null != stack) != (null != nonStackable));
		
		this.stack = stack;
		this.nonStackable = nonStackable;
	}

	@Override
	public int hashCode()
	{
		return (null != this.stack)
			? this.stack.hashCode()
			: this.nonStackable.hashCode()
		;
	}

	@Override
	public boolean equals(Object obj)
	{
		boolean equals = false;
		if ((null != obj) && (this.getClass() == obj.getClass()))
		{
			ItemSlot other = (ItemSlot)obj;
			if ((null != this.stack) && (null != other.stack))
			{
				equals = this.stack.equals(other.stack);
			}
			else if ((null != this.nonStackable) && (null != other.nonStackable))
			{
				equals = this.nonStackable.equals(other.nonStackable);
			}
		}
		return equals;
	}

	@Override
	public String toString()
	{
		boolean isStackable = (null != this.stack);
		Item type = isStackable ? this.stack.type() : this.nonStackable.type();
		return type + (isStackable ? ("(" + this.stack.count() + ")") : "");
	}

	public Item getType()
	{
		return (null != this.stack)
			? this.stack.type()
			: this.nonStackable.type()
		;
	}

	public int getCount()
	{
		return (null != this.stack)
			? this.stack.count()
			: 1
		;
	}
}
