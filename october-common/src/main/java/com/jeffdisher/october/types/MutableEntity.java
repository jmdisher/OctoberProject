package com.jeffdisher.october.types;

import java.util.function.Consumer;

import com.jeffdisher.october.aspects.StationRegistry;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.mutations.EntityChangePeriodic;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.MutationBlockStoreItems;
import com.jeffdisher.october.utils.Assert;


/**
 * A short-lived mutable version of an entity to allow for parallel tick processing.
 */
public class MutableEntity implements IMutablePlayerEntity
{
	public static final EntityLocation TESTING_LOCATION = new EntityLocation(0.0f, 0.0f, 0.0f);
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
	 * Creates a mutable entity with the given id and location.
	 * 
	 * @param id The entity ID (must be positive).
	 * @param location The location of the entity.
	 * @return A mutable entity.
	 */
	public static MutableEntity createWithLocation(int id, EntityLocation location)
	{
		return _createWithLocation(id, location);
	}

	/**
	 * A helper to create a mutable entity for testing purposes (uses testing defaults).
	 * 
	 * @param id The entity ID (must be positive).
	 * @return A mutable entity.
	 */
	public static MutableEntity createForTest(int id)
	{
		return _createWithLocation(id, TESTING_LOCATION);
	}

	private static MutableEntity _createWithLocation(int id, EntityLocation location)
	{
		// We don't want to allow non-positive entity IDs (since those will be reserved for errors or future uses).
		Assert.assertTrue(id > 0);
		Assert.assertTrue(null != location);
		Inventory inventory = Inventory.start(StationRegistry.CAPACITY_PLAYER).finish();
		Entity entity = new Entity(id
				, false
				, location
				, new EntityLocation(0.0f, 0.0f, 0.0f)
				, DEFAULT_BLOCKS_PER_TICK_SPEED
				, inventory
				, new int[Entity.HOTBAR_SIZE]
				, 0
				, new NonStackableItem[BodyPart.values().length]
				, null
				, DEFAULT_HEALTH
				, DEFAULT_FOOD
				, EntityConstants.MAX_BREATH
				, 0
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
	public int newBreath;
	public int newEnergyDeficit;

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
		if (_original.isCreativeMode())
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
	public void handleEntityDeath(Consumer<IMutationBlock> mutationConsumer)
	{
		// Drop their inventory.
		EntityLocation entityCentre = SpatialHelpers.getEntityCentre(this.newLocation, EntityConstants.getVolume(EntityType.PLAYER));
		for (Integer key : this.newInventory.freeze().sortedKeys())
		{
			Items stackable = this.newInventory.getStackForKey(key);
			NonStackableItem nonStackable = this.newInventory.getNonStackableForKey(key);
			Assert.assertTrue((null != stackable) != (null != nonStackable));
			mutationConsumer.accept(new MutationBlockStoreItems(entityCentre.getBlockLocation(), stackable, nonStackable, Inventory.INVENTORY_ASPECT_INVENTORY));
		}
		
		// Respawn them.
		this.newInventory.clearInventory(null);
		this.newLocation = MutableEntity.TESTING_LOCATION;
		this.newHealth = MutableEntity.DEFAULT_HEALTH;
		this.newFood = MutableEntity.DEFAULT_FOOD;
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
		this.newHealth = health;
	}

	@Override
	public byte getFood()
	{
		return this.newFood;
	}

	@Override
	public void setFood(byte food)
	{
		this.newFood = food;
	}

	@Override
	public int getBreath()
	{
		return this.newBreath;
	}

	@Override
	public void setBreath(int breath)
	{
		this.newBreath = breath;
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
		this.newEnergyDeficit = deficit;
	}

	@Override
	public void applyEnergyCost(TickProcessingContext context, int cost)
	{
		// Apply the energy cost using the logic in the periodic entity.
		EntityChangePeriodic.useEnergyAllowingDamage(context, this, cost);
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
				IMutableInventory inventoryToCheck = _original.isCreativeMode()
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
				, _original.isCreativeMode()
				, this.newLocation
				, this.newVelocity
				, _original.blocksPerTickSpeed()
				, this.newInventory.freeze()
				, didHotbarChange ? this.newHotbar : _original.hotbarItems()
				, this.newHotbarIndex
				, didArmourChange ? this.newArmour : _original.armourSlots()
				, this.newLocalCraftOperation
				, this.newHealth
				, this.newFood
				, this.newBreath
				, this.newEnergyDeficit
		);
		// See if these are identical.
		return _original.equals(newInstance)
				? _original
				: newInstance
		;
	}
}
