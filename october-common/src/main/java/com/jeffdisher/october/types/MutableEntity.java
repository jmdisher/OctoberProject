package com.jeffdisher.october.types;

import com.jeffdisher.october.utils.Assert;


/**
 * A short-lived mutable version of an entity to allow for parallel tick processing.
 */
public class MutableEntity
{
	// Some data elements are actually immutable (id, for example) so they are just left in the original, along with the original data.
	public final Entity original;
	public final MutableInventory newInventory;

	// The location is immutable but can be directly replaced.
	public EntityLocation newLocation;
	public float newZVelocityPerSecond;
	public Item newSelectedItem;
	public CraftOperation newLocalCraftOperation;

	public MutableEntity(Entity original)
	{
		this.original = original;
		this.newInventory = new MutableInventory(original.inventory());
		this.newLocation = original.location();
		this.newZVelocityPerSecond = original.zVelocityPerSecond();
		this.newSelectedItem = original.selectedItem();
		this.newLocalCraftOperation = original.localCraftOperation();
	}

	/**
	 * Creates an immutable snapshot of the receiver.
	 * Note that this will return the original instance if a new instance would have been identical.
	 * 
	 * @return A read-only copy of the current state of the mutable entity.
	 */
	public Entity freeze()
	{
		// We want to verify that the selected item is actually in the inventory (otherwise, there was a static error).
		if (null != this.newSelectedItem)
		{
			Assert.assertTrue(this.newInventory.getCount(this.newSelectedItem) > 0);
		}
		Entity newInstance = new Entity(this.original.id()
				, this.newLocation
				, this.newZVelocityPerSecond
				, this.original.volume()
				, this.original.blocksPerTickSpeed()
				, this.newInventory.freeze()
				, this.newSelectedItem
				, this.newLocalCraftOperation
		);
		// See if these are identical.
		return this.original.equals(newInstance)
				? this.original
				: newInstance
		;
	}
}
