package com.jeffdisher.october.client;

import com.jeffdisher.october.types.CraftOperation;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.NonStackableItem;


/**
 * Similar to MutationEntitySetEntity but specifically made for the client-side logic in order to layer changes to one
 * part of an entity on top of another.
 * This means that it doesn't capture high-level changes (like "health increased by 5") but does at least capture
 * per-field replacements instead of full-entity replacements.
 * In the future, this may be adapted as an IEntityUpdate implementation in order to optimize network updates.
 */
public class EntityUpdatePerField
{
	public static EntityUpdatePerField update(Entity oldEntity, Entity newEntity)
	{
		// We rely on the system applying copy-on-write semantics and only use instance-comparisons.
		Boolean isCreativeMode = (oldEntity.isCreativeMode() != newEntity.isCreativeMode())
			? newEntity.isCreativeMode()
			: null
		;
		EntityLocation location = (oldEntity.location() != newEntity.location())
			? newEntity.location()
			: null
		;
		EntityLocation velocity = (oldEntity.velocity() != newEntity.velocity())
			? newEntity.velocity()
			: null
		;
		Inventory inventory = (oldEntity.inventory() != newEntity.inventory())
			? newEntity.inventory()
			: null
		;
		int[] hotbar = (oldEntity.hotbarItems() != newEntity.hotbarItems())
			? newEntity.hotbarItems()
			: null
		;
		Integer hotbarIndex = (oldEntity.hotbarIndex() != newEntity.hotbarIndex())
			? newEntity.hotbarIndex()
			: null
		;
		NonStackableItem[] armour = (oldEntity.armourSlots() != newEntity.armourSlots())
			? newEntity.armourSlots()
			: null
		;
		Byte health = (oldEntity.health() != newEntity.health())
			? newEntity.health()
			: null
		;
		Byte food = (oldEntity.food() != newEntity.food())
			? newEntity.food()
			: null
		;
		Byte breath = (oldEntity.breath() != newEntity.breath())
			? newEntity.breath()
			: null
		;
		EntityLocation spawnLocation = (oldEntity.spawnLocation() != newEntity.spawnLocation())
			? newEntity.spawnLocation()
			: null
		;
		
		CraftOperation localCraftOperation = (oldEntity.ephemeralShared().localCraftOperation() != newEntity.ephemeralShared().localCraftOperation())
			? newEntity.ephemeralShared().localCraftOperation()
			: null
		;
		Integer chargeMillis = (oldEntity.ephemeralShared().chargeMillis() != newEntity.ephemeralShared().chargeMillis())
			? newEntity.ephemeralShared().chargeMillis()
			: null
		;
		
		return new EntityUpdatePerField(newEntity.yaw()
			, newEntity.pitch()
			
			, isCreativeMode
			, location
			, velocity
			, inventory
			, hotbar
			, hotbarIndex
			, armour
			, health
			, food
			, breath
			, spawnLocation
			
			, localCraftOperation
			, chargeMillis
		);
	}

	public static EntityUpdatePerField merge(EntityUpdatePerField bottom, EntityUpdatePerField top)
	{
		// Just prefer the top over the bottom.
		return new EntityUpdatePerField(top._yaw
			, top._pitch
			
			, (null != top._isCreativeMode) ? top._isCreativeMode : bottom._isCreativeMode
			, (null != top._location) ? top._location : bottom._location
			, (null != top._velocity) ? top._velocity : bottom._velocity
			, (null != top._inventory) ? top._inventory : bottom._inventory
			, (null != top._hotbar) ? top._hotbar : bottom._hotbar
			, (null != top._hotbarIndex) ? top._hotbarIndex : bottom._hotbarIndex
			, (null != top._armour) ? top._armour : bottom._armour
			, (null != top._health) ? top._health : bottom._health
			, (null != top._food) ? top._food : bottom._food
			, (null != top._breath) ? top._breath : bottom._breath
			, (null != top._spawnLocation) ? top._spawnLocation : bottom._spawnLocation
			
			, (null != top._localCraftOperation) ? top._localCraftOperation : bottom._localCraftOperation
			, (null != top._chargeMillis) ? top._chargeMillis : bottom._chargeMillis
		);
	}


	// These are the fields which are always considered changed since it isn't worth micro-managing them.
	private final byte _yaw;
	private final byte _pitch;

	// Any of these entries which are non-null will be treated as replacements while the nulls will be considered unchanged.
	private final Boolean _isCreativeMode;
	private final EntityLocation _location;
	private final EntityLocation _velocity;
	private final Inventory _inventory;
	private final int[] _hotbar;
	private final Integer _hotbarIndex;
	private final NonStackableItem[] _armour;
	private final Byte _health;
	private final Byte _food;
	private final Byte _breath;
	private final EntityLocation _spawnLocation;

	private final CraftOperation _localCraftOperation;
	private final Integer _chargeMillis;

	private EntityUpdatePerField(byte yaw
		, byte pitch
		
		, Boolean isCreativeMode
		, EntityLocation location
		, EntityLocation velocity
		, Inventory inventory
		, int[] hotbar
		, Integer hotbarIndex
		, NonStackableItem[] armour
		, Byte health
		, Byte food
		, Byte breath
		, EntityLocation spawnLocation
		
		, CraftOperation localCraftOperation
		, Integer chargeMillis
	)
	{
		_yaw = yaw;
		_pitch = pitch;
		
		_isCreativeMode = isCreativeMode;
		_location = location;
		_velocity = velocity;
		_inventory = inventory;
		_hotbar = hotbar;
		_hotbarIndex = hotbarIndex;
		_armour = armour;
		_health = health;
		_food = food;
		_breath = breath;
		_spawnLocation = spawnLocation;
		
		_localCraftOperation = localCraftOperation;
		_chargeMillis = chargeMillis;
	}

	public void applyToEntity(MutableEntity newEntity)
	{
		newEntity.newYaw = _yaw;
		newEntity.newPitch = _pitch;
		
		if (null != _isCreativeMode)
		{
			newEntity.isCreativeMode = _isCreativeMode;
		}
		if (null != _location)
		{
			newEntity.newLocation = _location;
		}
		if (null != _velocity)
		{
			newEntity.newVelocity = _velocity;
		}
		if (null != _inventory)
		{
			newEntity.newInventory.clearInventory(_inventory);
		}
		if (null != _hotbar)
		{
			newEntity.newHotbar = _hotbar;
		}
		if (null != _hotbarIndex)
		{
			newEntity.newHotbarIndex = _hotbarIndex;
		}
		if (null != _armour)
		{
			newEntity.newArmour = _armour;
		}
		if (null != _health)
		{
			newEntity.newHealth = _health;
		}
		if (null != _food)
		{
			newEntity.newFood = _food;
		}
		if (null != _breath)
		{
			newEntity.newBreath = _breath;
		}
		if (null != _spawnLocation)
		{
			newEntity.newSpawn = _spawnLocation;
		}
		
		if (null != _localCraftOperation)
		{
			newEntity.newLocalCraftOperation = _localCraftOperation;
		}
		if (null != _chargeMillis)
		{
			newEntity.chargeMillis = _chargeMillis;
		}
	}
}
