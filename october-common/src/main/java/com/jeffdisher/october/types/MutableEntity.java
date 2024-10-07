package com.jeffdisher.october.types;

import com.jeffdisher.october.aspects.StationRegistry;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.mutations.MutationBlockStoreItems;
import com.jeffdisher.october.utils.Assert;


/**
 * A short-lived mutable version of an entity to allow for parallel tick processing.
 */
public class MutableEntity implements IMutablePlayerEntity
{
	public static final EntityLocation TESTING_LOCATION = new EntityLocation(0.0f, 0.0f, 0.0f);

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
	 * Creates a mutable entity with the given id and location.
	 * 
	 * @param id The entity ID (must be positive).
	 * @param location The location of the entity.
	 * @param spawn The respawn location of this entity.
	 * @return A mutable entity.
	 */
	public static MutableEntity createWithLocation(int id, EntityLocation location, EntityLocation spawn)
	{
		return _createWithLocation(id, location, spawn);
	}

	/**
	 * A helper to create a mutable entity for testing purposes (uses testing defaults).
	 * 
	 * @param id The entity ID (must be positive).
	 * @return A mutable entity.
	 */
	public static MutableEntity createForTest(int id)
	{
		return _createWithLocation(id, TESTING_LOCATION, TESTING_LOCATION);
	}

	private static MutableEntity _createWithLocation(int id, EntityLocation location, EntityLocation spawn)
	{
		// We don't want to allow non-positive entity IDs (since those will be reserved for errors or future uses).
		Assert.assertTrue(id > 0);
		Assert.assertTrue(null != location);
		Inventory inventory = Inventory.start(StationRegistry.CAPACITY_PLAYER).finish();
		Entity entity = new Entity(id
				, false
				, location
				, new EntityLocation(0.0f, 0.0f, 0.0f)
				, inventory
				, new int[Entity.HOTBAR_SIZE]
				, 0
				, new NonStackableItem[BodyPart.values().length]
				, null
				, EntityConstants.PLAYER_MAX_HEALTH
				, EntityConstants.PLAYER_MAX_FOOD
				, EntityConstants.MAX_BREATH
				, 0
				, spawn
				, 0L
		);
		return new MutableEntity(entity);
	}


	// Some data elements are actually immutable (id, for example) so they are just left in the original, along with the original data.
	private final Entity _original;
	public final MutableInventory newInventory;

	// The location is immutable but can be directly replaced.
	public EntityLocation newLocation;
	public EntityLocation newVelocity;
	public int[] newHotbar;
	public int newHotbarIndex;
	public NonStackableItem[] newArmour;
	public CraftOperation newLocalCraftOperation;
	public byte newHealth;
	public byte newFood;
	public byte newBreath;
	public int newEnergyDeficit;
	public boolean isCreativeMode;
	public long ephemeral_lastSpecialActionMillis;
	public EntityLocation newSpawn;

	private MutableEntity(Entity original)
	{
		_original = original;
		this.newInventory = new MutableInventory(original.inventory());
		this.newLocation = original.location();
		this.newVelocity = original.velocity();
		this.newHotbar = original.hotbarItems().clone();
		this.newHotbarIndex = original.hotbarIndex();
		this.newArmour = original.armourSlots().clone();
		this.newLocalCraftOperation = original.localCraftOperation();
		this.newHealth = original.health();
		this.newFood = original.food();
		this.newBreath = original.breath();
		this.newEnergyDeficit = original.energyDeficit();
		this.isCreativeMode = original.isCreativeMode();
		this.ephemeral_lastSpecialActionMillis = original.ephemeral_lastSpecialActionMillis();
		this.newSpawn = original.spawnLocation();
	}

	@Override
	public int getId()
	{
		return _original.id();
	}

	@Override
	public IMutableInventory accessMutableInventory()
	{
		// If this is a creative player, which ignore their inventory and always return the fake creative one.
		IMutableInventory inv;
		if (this.isCreativeMode)
		{
			inv = new CreativeInventory();
		}
		else
		{
			inv = this.newInventory;
		}
		return inv;
	}

	@Override
	public CraftOperation getCurrentCraftingOperation()
	{
		return this.newLocalCraftOperation;
	}

	@Override
	public void setCurrentCraftingOperation(CraftOperation operation)
	{
		this.newLocalCraftOperation = operation;
	}

	@Override
	public int[] copyHotbar()
	{
		return this.newHotbar.clone();
	}

	@Override
	public int getSelectedKey()
	{
		return this.newHotbar[this.newHotbarIndex];
	}

	@Override
	public void setSelectedKey(int key)
	{
		this.newHotbar[this.newHotbarIndex] = key;
	}

	@Override
	public EntityType getType()
	{
		return EntityType.PLAYER;
	}

	@Override
	public EntityLocation getLocation()
	{
		return this.newLocation;
	}

	@Override
	public void setLocation(EntityLocation location)
	{
		this.newLocation = location;
	}

	@Override
	public EntityLocation getVelocityVector()
	{
		return this.newVelocity;
	}

	@Override
	public void setVelocityVector(EntityLocation vector)
	{
		this.newVelocity = vector;
	}

	@Override
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

	@Override
	public void resetLongRunningOperations()
	{
		// The only thing we worry about here is any crafting operation.
		this.newLocalCraftOperation = null;
	}

	@Override
	public void handleEntityDeath(TickProcessingContext context)
	{
		// Drop their inventory.
		EntityLocation entityCentre = SpatialHelpers.getEntityCentre(this.newLocation, EntityConstants.getVolume(EntityType.PLAYER));
		for (Integer key : this.newInventory.freeze().sortedKeys())
		{
			Items stackable = this.newInventory.getStackForKey(key);
			NonStackableItem nonStackable = this.newInventory.getNonStackableForKey(key);
			Assert.assertTrue((null != stackable) != (null != nonStackable));
			context.mutationSink.next(new MutationBlockStoreItems(entityCentre.getBlockLocation(), stackable, nonStackable, Inventory.INVENTORY_ASPECT_INVENTORY));
		}
		
		// Respawn them.
		this.newInventory.clearInventory(null);
		this.newLocation = this.newSpawn;
		this.newHealth = EntityConstants.PLAYER_MAX_HEALTH;
		this.newFood = EntityConstants.PLAYER_MAX_FOOD;
		// Wipe all the hotbar slots.
		for (int i = 0; i < Entity.HOTBAR_SIZE; ++i)
		{
			this.newHotbar[i] = Entity.NO_SELECTION;
		}
	}

	@Override
	public boolean changeHotbarIndex(int index)
	{
		boolean didApply = false;
		if (this.newHotbarIndex != index)
		{
			this.newHotbarIndex = index;
			didApply = true;
		}
		return didApply;
	}

	@Override
	public byte getHealth()
	{
		return this.newHealth;
	}

	@Override
	public void setHealth(byte health)
	{
		// We can't change food, health, or breath in creative mode.
		if (!this.isCreativeMode)
		{
			this.newHealth = health;
		}
	}

	@Override
	public byte getFood()
	{
		return this.newFood;
	}

	@Override
	public void setFood(byte food)
	{
		// We can't change food, health, or breath in creative mode.
		if (!this.isCreativeMode)
		{
			this.newFood = food;
		}
	}

	@Override
	public byte getBreath()
	{
		return this.newBreath;
	}

	@Override
	public void setBreath(byte breath)
	{
		// We can't change food, health, or breath in creative mode.
		if (!this.isCreativeMode)
		{
			this.newBreath = breath;
		}
	}

	@Override
	public NonStackableItem getArmour(BodyPart part)
	{
		return this.newArmour[part.ordinal()];
	}

	@Override
	public void setArmour(BodyPart part, NonStackableItem item)
	{
		this.newArmour[part.ordinal()] = item;
	}

	@Override
	public int getEnergyDeficit()
	{
		return this.newEnergyDeficit;
	}

	@Override
	public void setEnergyDeficit(int deficit)
	{
		// Energy deficit shouldn't be changed in creative mode.
		if (!this.isCreativeMode)
		{
			this.newEnergyDeficit = deficit;
		}
	}

	@Override
	public boolean isCreativeMode()
	{
		return this.isCreativeMode;
	}

	@Override
	public void setCreativeMode(boolean enableCreative)
	{
		this.isCreativeMode = enableCreative;
	}

	@Override
	public void applyEnergyCost(int cost)
	{
		// Energy deficit shouldn't be changed in creative mode.
		if (!this.isCreativeMode)
		{
			this.newEnergyDeficit += cost;
		}
	}

	@Override
	public long getLastSpecialActionMillis()
	{
		return this.ephemeral_lastSpecialActionMillis;
	}

	@Override
	public void setLastSpecialActionMillis(long millis)
	{
		this.ephemeral_lastSpecialActionMillis = millis;
	}

	@Override
	public void setSpawnLocation(EntityLocation spawnLocation)
	{
		this.newSpawn = spawnLocation;
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
				IMutableInventory inventoryToCheck = this.isCreativeMode
						? new CreativeInventory()
						: this.newInventory;
				Items stack = inventoryToCheck.getStackForKey(newKey);
				NonStackableItem nonStack = inventoryToCheck.getNonStackableForKey(newKey);
				Assert.assertTrue((null != stack) != (null != nonStack));
			}
			if (newKey != _original.hotbarItems()[i])
			{
				didHotbarChange = true;
			}
		}
		boolean didArmourChange = false;
		for (int i = 0; i < this.newArmour.length; ++i)
		{
			if (this.newArmour[i] != _original.armourSlots()[i])
			{
				didArmourChange = true;
				break;
			}
		}
		Entity newInstance = new Entity(_original.id()
				, this.isCreativeMode
				, this.newLocation
				, this.newVelocity
				, this.newInventory.freeze()
				, didHotbarChange ? this.newHotbar : _original.hotbarItems()
				, this.newHotbarIndex
				, didArmourChange ? this.newArmour : _original.armourSlots()
				, this.newLocalCraftOperation
				, this.newHealth
				, this.newFood
				, this.newBreath
				, this.newEnergyDeficit
				, this.newSpawn
				, this.ephemeral_lastSpecialActionMillis
		);
		// See if these are identical.
		return _original.equals(newInstance)
				? _original
				: newInstance
		;
	}
}
