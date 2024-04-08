package com.jeffdisher.october.types;

import com.jeffdisher.october.aspects.InventoryAspect;
import com.jeffdisher.october.utils.Assert;


/**
 * A short-lived mutable version of an entity to allow for parallel tick processing.
 */
public class MutableEntity
{
	// Note that we used 1.8 x 0.5 volume for initial testing, and this will be good in the future, but makes spatial understanding in OctoberPlains confusing.
	public static final EntityVolume DEFAULT_VOLUME = new EntityVolume(0.9f, 0.4f);
	public static final EntityLocation DEFAULT_LOCATION = new EntityLocation(0.0f, 0.0f, 0.0f);
	public static final float DEFAULT_BLOCKS_PER_TICK_SPEED = 0.5f;
	public static final byte DEFAULT_HEALTH = 100;
	public static final byte DEFAULT_FOOD = 100;

	/**
	 * Create a mutable entity from the elements of an existing entity.
	 * 
	 * @param entity An existing entity.
	 * @return A mutable entity.
	 */
	public static MutableEntity existing(Entity entity)
	{
		return new MutableEntity(entity);
	}

	/**
	 * Creates a mutable entity based on the default values for a new entity.
	 * 
	 * @param id The entity ID.
	 * @return A mutable entity.
	 */
	public static MutableEntity create(int id)
	{
		Inventory inventory = Inventory.start(InventoryAspect.CAPACITY_PLAYER).finish();
		Entity entity = new Entity(id
				, DEFAULT_LOCATION
				, 0.0f
				, DEFAULT_VOLUME
				, DEFAULT_BLOCKS_PER_TICK_SPEED
				, inventory
				, null
				, null
				, DEFAULT_HEALTH
				, DEFAULT_FOOD
		);
		return new MutableEntity(entity);
	}


	// Some data elements are actually immutable (id, for example) so they are just left in the original, along with the original data.
	public final Entity original;
	public final MutableInventory newInventory;

	// The location is immutable but can be directly replaced.
	public EntityLocation newLocation;
	public float newZVelocityPerSecond;
	public Item newSelectedItem;
	public CraftOperation newLocalCraftOperation;

	private MutableEntity(Entity original)
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
				, this.original.health()
				, this.original.food()
		);
		// See if these are identical.
		return this.original.equals(newInstance)
				? this.original
				: newInstance
		;
	}
}
