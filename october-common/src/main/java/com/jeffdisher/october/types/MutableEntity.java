package com.jeffdisher.october.types;

import com.jeffdisher.october.aspects.StationRegistry;
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
	 * @param id The entity ID (must be positive).
	 * @return A mutable entity.
	 */
	public static MutableEntity create(int id)
	{
		// We don't want to allow non-positive entity IDs (since those will be reserved for errors or future uses).
		Assert.assertTrue(id > 0);
		Inventory inventory = Inventory.start(StationRegistry.CAPACITY_PLAYER).finish();
		Entity entity = new Entity(id
				, DEFAULT_LOCATION
				, 0.0f
				, DEFAULT_VOLUME
				, DEFAULT_BLOCKS_PER_TICK_SPEED
				, inventory
				, new int[Entity.HOTBAR_SIZE]
				, 0
				, new NonStackableItem[BodyPart.values().length]
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
	public int[] newHotbar;
	public int newHotbarIndex;
	public NonStackableItem[] newArmour;
	public CraftOperation newLocalCraftOperation;
	public byte newHealth;
	public byte newFood;

	private MutableEntity(Entity original)
	{
		this.original = original;
		this.newInventory = new MutableInventory(original.inventory());
		this.newLocation = original.location();
		this.newZVelocityPerSecond = original.zVelocityPerSecond();
		this.newHotbar = original.hotbarItems().clone();
		this.newHotbarIndex = original.hotbarIndex();
		this.newArmour = original.armourSlots().clone();
		this.newLocalCraftOperation = original.localCraftOperation();
		this.newHealth = original.health();
		this.newFood = original.food();
	}

	/**
	 * @return The key in the currently selected hotbar slot.
	 */
	public int getSelectedKey()
	{
		return this.newHotbar[this.newHotbarIndex];
	}

	/**
	 * Sets the key in the currently-selected hotbar slot.
	 * 
	 * @param key The key to store.
	 */
	public void setSelectedKey(int key)
	{
		this.newHotbar[this.newHotbarIndex] = key;
	}

	public void clearHotBarWithKey(int key)
	{
		for (int i = 0; i < Entity.HOTBAR_SIZE; ++i)
		{
			if (key == this.newHotbar[i])
			{
				this.newHotbar[i] = Entity.NO_SELECTION;
			}
		}
	}

	/**
	 * Creates an immutable snapshot of the receiver.
	 * Note that this will return the original instance if a new instance would have been identical.
	 * 
	 * @return A read-only copy of the current state of the mutable entity.
	 */
	public Entity freeze()
	{
		// We want to verify that the selection index is valid and that the hotbar only references valid inventory ids.
		Assert.assertTrue((this.newHotbarIndex >= 0) && (this.newHotbarIndex < this.newHotbar.length));
		boolean didHotbarChange = false;
		for (int i = 0; i < this.newHotbar.length; ++i)
		{
			int newKey = this.newHotbar[i];
			if (Entity.NO_SELECTION != newKey)
			{
				Items stack = this.newInventory.getStackForKey(newKey);
				NonStackableItem nonStack = this.newInventory.getNonStackableForKey(newKey);
				Assert.assertTrue((null != stack) != (null != nonStack));
			}
			if (newKey != this.original.hotbarItems()[i])
			{
				didHotbarChange = true;
			}
		}
		boolean didArmourChange = false;
		for (int i = 0; i < this.newArmour.length; ++i)
		{
			if (this.newArmour[i] != this.original.armourSlots()[i])
			{
				didArmourChange = true;
				break;
			}
		}
		Entity newInstance = new Entity(this.original.id()
				, this.newLocation
				, this.newZVelocityPerSecond
				, this.original.volume()
				, this.original.blocksPerTickSpeed()
				, this.newInventory.freeze()
				, didHotbarChange ? this.newHotbar : this.original.hotbarItems()
				, this.newHotbarIndex
				, didArmourChange ? this.newArmour : this.original.armourSlots()
				, this.newLocalCraftOperation
				, this.newHealth
				, this.newFood
		);
		// See if these are identical.
		return this.original.equals(newInstance)
				? this.original
				: newInstance
		;
	}
}
