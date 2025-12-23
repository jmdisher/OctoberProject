package com.jeffdisher.october.net;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.CraftOperation;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.NonStackableItem;


/**
 * Used by the network and some client-side logic to describe updates to a full Entity instance on a per-field basis.
 * This means that it doesn't capture high-level changes (like "health increased by 5") but does at least capture
 * per-field replacements instead of full-entity replacements.
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
		
		CraftOperation[] localCraftOperation = (oldEntity.ephemeralShared().localCraftOperation() != newEntity.ephemeralShared().localCraftOperation())
			? new CraftOperation[] { newEntity.ephemeralShared().localCraftOperation() }
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

	public static EntityUpdatePerField deserializeFromNetworkBuffer(ByteBuffer buffer)
	{
		// This is always coming in from the network so it has no version-specific considerations.
		DeserializationContext context = DeserializationContext.empty(Environment.getShared()
			, buffer
		);
		
		// We store a 16-bit vector for the fields present as a header.
		short bits = buffer.getShort();
		
		// Yaw and pitch are always here.
		byte yaw = buffer.get();
		byte pitch = buffer.get();
		
		// There are 13 fields so cue up the bits before reading.
		bits <<= 3;
		Boolean isCreativeMode = null;
		if (0x0 != (0x8000 & bits))
		{
			isCreativeMode = CodecHelpers.readBoolean(buffer);
		}
		
		bits <<= 1;
		EntityLocation location = null;
		if (0x0 != (0x8000 & bits))
		{
			location = CodecHelpers.readEntityLocation(buffer);
		}
		
		bits <<= 1;
		EntityLocation velocity = null;
		if (0x0 != (0x8000 & bits))
		{
			velocity = CodecHelpers.readEntityLocation(buffer);
		}
		
		bits <<= 1;
		Inventory inventory = null;
		if (0x0 != (0x8000 & bits))
		{
			inventory = CodecHelpers.readInventory(context);
		}
		
		bits <<= 1;
		int[] hotbar = null;
		if (0x0 != (0x8000 & bits))
		{
			hotbar = new int[Entity.HOTBAR_SIZE];
			for (int i = 0; i < hotbar.length; ++i)
			{
				hotbar[i] = buffer.getInt();
			}
		}
		
		bits <<= 1;
		Integer hotbarIndex = null;
		if (0x0 != (0x8000 & bits))
		{
			hotbarIndex = buffer.getInt();
		}
		
		bits <<= 1;
		NonStackableItem[] armour = null;
		if (0x0 != (0x8000 & bits))
		{
			armour = new NonStackableItem[BodyPart.values().length];
			for (int i = 0; i < armour.length; ++i)
			{
				armour[i] = CodecHelpers.readNonStackableItem(context);
			}
		}
		
		bits <<= 1;
		Byte health = null;
		if (0x0 != (0x8000 & bits))
		{
			health = buffer.get();
		}
		
		bits <<= 1;
		Byte food = null;
		if (0x0 != (0x8000 & bits))
		{
			food = buffer.get();
		}
		
		bits <<= 1;
		Byte breath = null;
		if (0x0 != (0x8000 & bits))
		{
			breath = buffer.get();
		}
		
		bits <<= 1;
		EntityLocation spawnLocation = null;
		if (0x0 != (0x8000 & bits))
		{
			spawnLocation = CodecHelpers.readEntityLocation(buffer);
		}
		
		bits <<= 1;
		CraftOperation[] localCraftOperation = null;
		if (0x0 != (0x8000 & bits))
		{
			localCraftOperation = new CraftOperation[] { CodecHelpers.readCraftOperation(buffer) };
		}
		
		bits <<= 1;
		Integer chargeMillis = null;
		if (0x0 != (0x8000 & bits))
		{
			chargeMillis = buffer.getInt();
		}
		
		return new EntityUpdatePerField(yaw
			, pitch
			
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

	private final CraftOperation[] _localCraftOperation;
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
		
		, CraftOperation[] localCraftOperation
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

	/**
	 * Applies the receiver to the given newEntity.
	 * 
	 * @param newEntity The entity which should be updated by the receiver.
	 */
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
			newEntity.newLocalCraftOperation = _localCraftOperation[0];
		}
		if (null != _chargeMillis)
		{
			newEntity.chargeMillis = _chargeMillis;
		}
	}

	/**
	 * Called to serialize the update into the given buffer for network transmission.
	 * 
	 * @param buffer The network buffer where the update should be written.
	 */
	public void serializeToNetworkBuffer(ByteBuffer buffer)
	{
		// We store a 16-bit vector for the fields present as a header.
		short bits = 0;
		if (null != _isCreativeMode)
		{
			bits |= 0x1;
		}
		bits <<= 1;
		if (null != _location)
		{
			bits |= 0x1;
		}
		bits <<= 1;
		if (null != _velocity)
		{
			bits |= 0x1;
		}
		bits <<= 1;
		if (null != _inventory)
		{
			bits |= 0x1;
		}
		bits <<= 1;
		if (null != _hotbar)
		{
			bits |= 0x1;
		}
		bits <<= 1;
		if (null != _hotbarIndex)
		{
			bits |= 0x1;
		}
		bits <<= 1;
		if (null != _armour)
		{
			bits |= 0x1;
		}
		bits <<= 1;
		if (null != _health)
		{
			bits |= 0x1;
		}
		bits <<= 1;
		if (null != _food)
		{
			bits |= 0x1;
		}
		bits <<= 1;
		if (null != _breath)
		{
			bits |= 0x1;
		}
		bits <<= 1;
		if (null != _spawnLocation)
		{
			bits |= 0x1;
		}
		bits <<= 1;
		
		if (null != _localCraftOperation)
		{
			bits |= 0x1;
		}
		bits <<= 1;
		if (null != _chargeMillis)
		{
			bits |= 0x1;
		}
		buffer.putShort(bits);
		
		// Yaw and pitch are always here.
		buffer.put(_yaw);
		buffer.put(_pitch);
		
		// There are 13 fields so cue up the bits before reading.
		bits <<= 3;
		if (0x0 != (0x8000 & bits))
		{
			CodecHelpers.writeBoolean(buffer, _isCreativeMode);
		}
		
		bits <<= 1;
		if (0x0 != (0x8000 & bits))
		{
			CodecHelpers.writeEntityLocation(buffer, _location);
		}
		
		bits <<= 1;
		if (0x0 != (0x8000 & bits))
		{
			CodecHelpers.writeEntityLocation(buffer, _velocity);
		}
		
		bits <<= 1;
		if (0x0 != (0x8000 & bits))
		{
			CodecHelpers.writeInventory(buffer, _inventory);
		}
		
		bits <<= 1;
		if (0x0 != (0x8000 & bits))
		{
			for (int i = 0; i < _hotbar.length; ++i)
			{
				buffer.putInt(_hotbar[i]);
			}
		}
		
		bits <<= 1;
		if (0x0 != (0x8000 & bits))
		{
			buffer.putInt(_hotbarIndex);
		}
		
		bits <<= 1;
		if (0x0 != (0x8000 & bits))
		{
			for (int i = 0; i < _armour.length; ++i)
			{
				CodecHelpers.writeNonStackableItem(buffer, _armour[i]);
			}
		}
		
		bits <<= 1;
		if (0x0 != (0x8000 & bits))
		{
			buffer.put(_health);
		}
		
		bits <<= 1;
		if (0x0 != (0x8000 & bits))
		{
			buffer.put(_food);
		}
		
		bits <<= 1;
		if (0x0 != (0x8000 & bits))
		{
			buffer.put(_breath);
		}
		
		bits <<= 1;
		if (0x0 != (0x8000 & bits))
		{
			CodecHelpers.writeEntityLocation(buffer, _spawnLocation);
		}
		
		bits <<= 1;
		if (0x0 != (0x8000 & bits))
		{
			CodecHelpers.writeCraftOperation(buffer, _localCraftOperation[0]);
		}
		
		bits <<= 1;
		if (0x0 != (0x8000 & bits))
		{
			buffer.putInt(_chargeMillis);
		}
	}
}
