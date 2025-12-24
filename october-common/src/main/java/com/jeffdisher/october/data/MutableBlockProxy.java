package com.jeffdisher.october.data;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.aspects.LightAspect;
import com.jeffdisher.october.aspects.LogicAspect;
import com.jeffdisher.october.aspects.OrientationAspect;
import com.jeffdisher.october.logic.LogicLayerHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CraftOperation;
import com.jeffdisher.october.types.EnchantingOperation;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.FuelState;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.utils.Assert;


/**
 * A proxy to access mutable block data within a specific cuboid which is currently being updated.
 */
public class MutableBlockProxy implements IMutableBlockProxy
{
	private final Environment _env;
	public final AbsoluteLocation absoluteLocation;
	private final BlockAddress _address;
	private final IReadOnlyCuboidData _data;

	// We cache any writes so that they are just flushed at the end.
	private final byte[] _write7;
	private final short[] _write15;
	private final Object[] _writeObject;
	private final Object[] _writes;

	private Block _cachedBlock;
	private Object _ephemeralState;

	public long periodicDelayMillis;

	public MutableBlockProxy(AbsoluteLocation absoluteLocation, IReadOnlyCuboidData data)
	{
		_env = Environment.getShared();
		this.absoluteLocation = absoluteLocation;
		_address = absoluteLocation.getBlockAddress();
		_data = data;
		
		_write7 = new byte[AspectRegistry.ALL_ASPECTS.length];
		_write15 = new short[AspectRegistry.ALL_ASPECTS.length];
		_writeObject = new Object[AspectRegistry.ALL_ASPECTS.length];
		_writes = new Object[AspectRegistry.ALL_ASPECTS.length];
		
		// We cache the item since we use it to make some other internal decisions.
		Item rawItem = _env.items.ITEMS_BY_TYPE[_getData15(AspectRegistry.BLOCK)];
		_cachedBlock = _env.blocks.fromItem(rawItem);
	}

	@Override
	public Block getBlock()
	{
		return _cachedBlock;
	}

	@Override
	public void setBlockAndClear(Block block)
	{
		// Cache the item for internal checks.
		_cachedBlock = block;
		
		// Set the updated item type.
		_setData15(AspectRegistry.BLOCK, block.item().number());
		
		// Clear other aspects since they are all based on the item type.
		_setDataSpecial(AspectRegistry.INVENTORY, null);
		_setDataSpecial(AspectRegistry.DAMAGE, null);
		_setDataSpecial(AspectRegistry.CRAFTING, null);
		_setDataSpecial(AspectRegistry.FUELLED, null);
		// Note that we EXPLICTLY avoid clearing the light value since that is updated via a delayed mechanism.
		
		_setData7(AspectRegistry.FLAGS, (byte)0);
		_setData7(AspectRegistry.ORIENTATION, FacingDirection.directionToByte(FacingDirection.NORTH));
		_setDataSpecial(AspectRegistry.MULTI_BLOCK_ROOT, null);
		_setDataSpecial(AspectRegistry.SPECIAL_ITEM_SLOT, null);
		_setDataSpecial(AspectRegistry.ENCHANTING, null);
	}

	@Override
	public Inventory getInventory()
	{
		Inventory inv = _getDataSpecial(AspectRegistry.INVENTORY);
		// We can't return null if this block can support one.
		if (null == inv)
		{
			inv = BlockProxy.getDefaultNormalInventory(_env, _cachedBlock);
		}
		return inv;
	}

	@Override
	public void setInventory(Inventory inv)
	{
		// This shouldn't be called if the block destroys inventories.
		Assert.assertTrue(0 == _env.blocks.getBlockDamage(_cachedBlock));
		
		// If this is empty, we want to store null, instead.
		if (0 == inv.currentEncumbrance)
		{
			_setDataSpecial(AspectRegistry.INVENTORY, null);
		}
		else
		{
			_setDataSpecial(AspectRegistry.INVENTORY, inv);
		}
	}

	@Override
	public int getDamage()
	{
		Integer object = _getDataSpecial(AspectRegistry.DAMAGE);
		return (null != object)
			? object.intValue()
			: 0
		;
	}

	@Override
	public void setDamage(int damage)
	{
		Integer val;
		if (damage > 0)
		{
			val = damage;
		}
		else
		{
			// This cannot be negative.
			Assert.assertTrue(0 == damage);
			val = null;
		}
		_setDataSpecial(AspectRegistry.DAMAGE, val);
	}

	@Override
	public CraftOperation getCrafting()
	{
		return _getDataSpecial(AspectRegistry.CRAFTING);
	}

	@Override
	public void setCrafting(CraftOperation crafting)
	{
		// If this is non-null, we MUST have done at least some work.
		if (null != crafting)
		{
			Assert.assertTrue(crafting.completedMillis() > 0L);
		}
		_setDataSpecial(AspectRegistry.CRAFTING, crafting);
	}

	@Override
	public FuelState getFuel()
	{
		FuelState fuel = _getDataSpecial(AspectRegistry.FUELLED);
		// We can't return null if this block can support fuel.
		if (null == fuel)
		{
			int fuelInventorySize = _env.stations.getFuelInventorySize(_cachedBlock);
			if (fuelInventorySize > 0)
			{
				fuel = new FuelState(0, null, Inventory.start(fuelInventorySize).finish());
			}
		}
		return fuel;
	}

	@Override
	public void setFuel(FuelState fuel)
	{
		// If this is empty, we want to store null, instead.
		if (fuel.isEmpty())
		{
			_setDataSpecial(AspectRegistry.FUELLED, null);
		}
		else
		{
			_setDataSpecial(AspectRegistry.FUELLED, fuel);
		}
	}

	@Override
	public byte getLight()
	{
		return _getData7(AspectRegistry.LIGHT);
	}

	@Override
	public void setLight(byte light)
	{
		Assert.assertTrue((light >= 0) && (light <= LightAspect.MAX_LIGHT));
		_setData7(AspectRegistry.LIGHT, light);
	}

	@Override
	public byte getLogic()
	{
		return _getData7(AspectRegistry.LOGIC);
	}

	@Override
	public void setLogic(byte logic)
	{
		Assert.assertTrue((logic >= 0) && (logic <= LogicAspect.MAX_LEVEL));
		_setData7(AspectRegistry.LOGIC, logic);
	}

	@Override
	public byte getFlags()
	{
		return _getData7(AspectRegistry.FLAGS);
	}

	@Override
	public void setFlags(byte flags)
	{
		_setData7(AspectRegistry.FLAGS, flags);
	}

	@Override
	public FacingDirection getOrientation()
	{
		byte ordinal = _getData7(AspectRegistry.ORIENTATION);
		return FacingDirection.byteToDirection(ordinal);
	}

	@Override
	public void setOrientation(FacingDirection direction)
	{
		byte ordinal = FacingDirection.directionToByte(direction);
		_setData7(AspectRegistry.ORIENTATION, ordinal);
	}

	@Override
	public AbsoluteLocation getMultiBlockRoot()
	{
		return _getDataSpecial(AspectRegistry.MULTI_BLOCK_ROOT);
	}

	@Override
	public void setMultiBlockRoot(AbsoluteLocation rootLocation)
	{
		_setDataSpecial(AspectRegistry.MULTI_BLOCK_ROOT, rootLocation);
	}

	@Override
	public ItemSlot getSpecialSlot()
	{
		return _getDataSpecial(AspectRegistry.SPECIAL_ITEM_SLOT);
	}

	@Override
	public void setSpecialSlot(ItemSlot slot)
	{
		_setDataSpecial(AspectRegistry.SPECIAL_ITEM_SLOT, slot);
	}

	@Override
	public EnchantingOperation getEnchantingOperation()
	{
		return _getDataSpecial(AspectRegistry.ENCHANTING);
	}

	@Override
	public void setEnchantingOperation(EnchantingOperation operation)
	{
		_setDataSpecial(AspectRegistry.ENCHANTING, operation);
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		// Note that we don't want to send all the data if it didn't all change (since things like lighting updates are common).
		// This means we will send just the index (as a byte) and the data for that aspect if it changed.
		for (int i = 0; i < AspectRegistry.ALL_ASPECTS.length; ++i)
		{
			// We only want to send this if it changed.
			if (null != _writes[i])
			{
				// There is some change so write this.
				// First, the index as a byte.
				Assert.assertTrue(i <= Byte.MAX_VALUE);
				buffer.put((byte)i);
				
				// Now, serialize the data.
				Aspect<?, ?> type = AspectRegistry.ALL_ASPECTS[i];
				// We want to honour the mutable proxy's internal type-specific data so check this type.
				if (Short.class == type.type())
				{
					// We just checked this.
					@SuppressWarnings("unchecked")
					short value = _getData15((Aspect<Short, ?>) type);
					buffer.putShort(value);
				}
				else if (Byte.class == type.type())
				{
					// We just checked this.
					@SuppressWarnings("unchecked")
					byte value = _getData7((Aspect<Byte, ?>) type);
					buffer.put(value);
				}
				else
				{
					_storeSafe(buffer, type);
				}
			}
		}
	}

	@Override
	public Object getEphemeralState()
	{
		return _ephemeralState;
	}

	@Override
	public void setEphemeralState(Object state)
	{
		_ephemeralState = state;
	}

	@Override
	public void requestFutureMutation(long millisToDelay)
	{
		Assert.assertTrue(millisToDelay > 0L);
		
		if (0L == this.periodicDelayMillis)
		{
			this.periodicDelayMillis = millisToDelay;
		}
		else
		{
			this.periodicDelayMillis = Math.min(this.periodicDelayMillis, millisToDelay);
		}
	}

	/**
	 * Checks any updates against the original values to see if anything needs to be updated.  Note that this will have
	 * the consequence of dropping any changes which were deemed redundant.
	 * 
	 * @return True if there are any changes to write-back to a mutable data copy.
	 */
	public boolean didChange()
	{
		boolean didChange = false;
		for (int i = 0; i < _writes.length; ++i)
		{
			// Check what was modified.
			if (_write7 == _writes[i])
			{
				byte original = _data.getData7(_aspectAsType(Byte.class, AspectRegistry.ALL_ASPECTS[i]), _address);
				if (original == _write7[i])
				{
					// A change was reverted.
					_writes[i] = null;
				}
				else
				{
					didChange = true;
				}
			}
			else if (_write15 == _writes[i])
			{
				short original = _data.getData15(_aspectAsType(Short.class, AspectRegistry.ALL_ASPECTS[i]), _address);
				if (original == _write15[i])
				{
					// A change was reverted.
					_writes[i] = null;
				}
				else
				{
					didChange = true;
				}
			}
			else if (_writeObject == _writes[i])
			{
				Object original = _data.getDataSpecial(AspectRegistry.ALL_ASPECTS[i], _address);
				if (original == _writeObject[i])
				{
					// A change was reverted.
					_writes[i] = null;
				}
				else
				{
					didChange = true;
				}
			}
			else
			{
				// Nothing should be changed.
				Assert.assertTrue(null == _writes[i]);
			}
		}
		return didChange;
	}

	/**
	 * Checks the internal writes to see if those are state changes which should trigger the creation of a block update
	 * event for the adjacent blocks in the following tick.
	 * NOTE:  This assumes that it was called AFTER didChange() in order to avoid vacuous changes.
	 * 
	 * @return True if the writes made to this proxy will require generating an update event for neighbouring blocks.
	 */
	public boolean shouldTriggerUpdateEvent()
	{
		// We want to trigger updates on BLOCK changes, since that often changes adjacent blocks.
		// We also want to include INVENTORY changes, since that can impact things like hoppers.
		// We need to include FLAGS changes since this can change how adjacent blocks interpret this block.
		return (null != _writes[AspectRegistry.BLOCK.index()])
				|| (null != _writes[AspectRegistry.INVENTORY.index()])
				|| (null != _writes[AspectRegistry.FLAGS.index()])
		;
	}

	/**
	 * Checks the internal writes to see if those are state changes which may require a lighting update in the
	 * surrounding blocks.  Note that other details may mean that there is no change (breaking a block in a dark room,
	 * for example) so the actual check must be made in the following tick (since the data is only consistent then).
	 * NOTE:  This assumes that it was called AFTER didChange() in order to avoid vacuous changes.
	 * 
	 * @return True if this changed in a way which may require a lighting recalculation.
	 */
	public boolean mayTriggerLightingChange()
	{
		// Currently, either a block change (break/place light) or a flag change (enable lamp) can result in light change.
		boolean lightMayChange = false;
		boolean blockChange = (null != _writes[AspectRegistry.BLOCK.index()]);
		boolean flagChange = (null != _writes[AspectRegistry.FLAGS.index()]);
		if (blockChange || flagChange)
		{
			short original = _data.getData15(AspectRegistry.BLOCK, _address);
			Item rawItem = _env.items.ITEMS_BY_TYPE[original];
			Block originalBlock = _env.blocks.fromItem(rawItem);
			byte oldFlags = _data.getData7(AspectRegistry.FLAGS, _address);
			boolean wasActive = FlagsAspect.isSet(_data.getData7(AspectRegistry.FLAGS, _address), FlagsAspect.FLAG_ACTIVE);
			byte originalEmission = _env.lighting.getLightEmission(originalBlock, wasActive);
			
			byte newFlags = (null != _writes[AspectRegistry.FLAGS.index()])
					? _write7[AspectRegistry.FLAGS.index()]
					: oldFlags
			;
			boolean isActive = FlagsAspect.isSet(newFlags, FlagsAspect.FLAG_ACTIVE);
			byte updatedEmission = _env.lighting.getLightEmission(_cachedBlock, isActive);
			byte originalOpacity = _env.lighting.getOpacity(originalBlock);
			byte updatedOpacity = _env.lighting.getOpacity(_cachedBlock);
			lightMayChange = (originalEmission != updatedEmission) || (originalOpacity != updatedOpacity);
		}
		return lightMayChange;
	}

	/**
	 * Checks the internal writes to see if those are state changes which may impact the logic aspect for the logically
	 * connected blocks.  Note that other details may mean that there is no change (placing a sink block next to a "low"
	 * source for example) so the actual check must be made in the following tick (since the data is only consistent
	 * then).
	 * NOTE:  This assumes that it was called AFTER didChange() in order to avoid vacuous changes.
	 * 
	 * @return A bit-vector from LogicLayerHelpers describing which logic values have potential changed around this
	 * block.
	 */
	public byte potentialLogicChangeBits()
	{
		// We check what this changed from/to to determine what blocks could have had a logic change.
		byte changeBits = 0x0;
		
		// Currently, either a block change (conduit) or a flag change (switch) can result in logic change.
		boolean blockChange = (null != _writes[AspectRegistry.BLOCK.index()]);
		boolean flagChange = (null != _writes[AspectRegistry.FLAGS.index()]);
		if (blockChange || flagChange)
		{
			short original = _data.getData15(AspectRegistry.BLOCK, _address);
			Item rawItem = _env.items.ITEMS_BY_TYPE[original];
			Block originalBlock = _env.blocks.fromItem(rawItem);
			
			// We expect that there should be no redundant writes here (called AFTER didChange()).
			if (blockChange)
			{
				Assert.assertTrue(originalBlock != _cachedBlock);
			}
			
			// Here, we have cases to check:  If this changed to/from a conduit block, it could actually have changed, itself.
			if (blockChange && (_env.logic.isConduit(originalBlock) != _env.logic.isConduit(_cachedBlock)))
			{
				changeBits |= LogicLayerHelpers.LOGIC_BIT_THIS;
			}
			
			// We to check if this either is, or was, a logic signal source.  We will build the change bit vector if the
			// block type changed or the flag changed.
			if (_env.logic.isSource(originalBlock))
			{
				FacingDirection direction = FacingDirection.byteToDirection(_data.getData7(AspectRegistry.ORIENTATION, _address));
				changeBits = _populateBitsForSource(originalBlock, direction, changeBits);
			}
			if (_env.logic.isSource(_cachedBlock))
			{
				FacingDirection direction = FacingDirection.byteToDirection(_write7[AspectRegistry.ORIENTATION.index()]);
				changeBits = _populateBitsForSource(_cachedBlock, direction, changeBits);
			}
		}
		
		return changeBits;
	}

	/**
	 * Writes back any changes cached to the given CuboidData object.  Note that this should be called after didChange()
	 * since it will revert redundant parts of the change.
	 * 
	 * @param newData The new cuboid where the changes should be written.
	 */
	public void writeBack(CuboidData newData)
	{
		for (int i = 0; i < _writes.length; ++i)
		{
			// Write this back if modified.
			if (_write7 == _writes[i])
			{
				newData.setData7(_aspectAsType(Byte.class, AspectRegistry.ALL_ASPECTS[i]), _address, _write7[i]);
			}
			else if (_write15 == _writes[i])
			{
				newData.setData15(_aspectAsType(Short.class, AspectRegistry.ALL_ASPECTS[i]), _address, _write15[i]);
			}
			else if (_writeObject == _writes[i])
			{
				_writeAsType(newData, AspectRegistry.ALL_ASPECTS[i], _address, _writeObject[i]);
			}
			else
			{
				// Nothing should be changed.
				Assert.assertTrue(null == _writes[i]);
			}
		}
	}


	private byte _getData7(Aspect<Byte, ?> type)
	{
		int index = type.index();
		return (null != _writes[index])
				? _write7[index]
				: _data.getData7(type, _address)
		;
	}

	private void _setData7(Aspect<Byte, ?> type, byte value)
	{
		_write7[type.index()] = value;
		_writes[type.index()] = _write7;
	}

	private short _getData15(Aspect<Short, ?> type)
	{
		int index = type.index();
		return (null != _writes[index])
				? _write15[index]
				: _data.getData15(type, _address)
		;
	}

	private void _setData15(Aspect<Short, ?> type, short value)
	{
		_write15[type.index()] = value;
		_writes[type.index()] = _write15;
	}

	private <T> T _getDataSpecial(Aspect<T, ?> type)
	{
		int index = type.index();
		return (null != _writes[index])
				? type.type().cast(_writeObject[index])
				: _data.getDataSpecial(type, _address)
		;
	}

	private <T> void _setDataSpecial(Aspect<T, ?> type, T value)
	{
		_writeObject[type.index()] = value;
		_writes[type.index()] = _writeObject;
	}

	@SuppressWarnings("unchecked")
	private static <T> Aspect<T, ?> _aspectAsType(Class<T> type, Aspect<?, ?> aspect)
	{
		// We can't cast this without adding another kind of helper to the Aspect so we will manually check and cast, for now.
		Assert.assertTrue(type == aspect.type());
		return (Aspect<T, ?>) aspect;
	}

	private static <T> void _writeAsType(CuboidData newData, Aspect<T, ?> aspect, BlockAddress address, Object value)
	{
		newData.setDataSpecial(aspect, address, aspect.type().cast(value));
	}

	private byte _populateBitsForSource(Block type, FacingDirection direction, byte changeBits)
	{
		if (OrientationAspect.doesSingleBlockRequireOrientation(type))
		{
			switch (direction)
			{
			case NORTH:
				changeBits |= LogicLayerHelpers.LOGIC_BIT_NORTH;
				break;
			case WEST:
				changeBits |= LogicLayerHelpers.LOGIC_BIT_WEST;
				break;
			case SOUTH:
				changeBits |= LogicLayerHelpers.LOGIC_BIT_SOUTH;
				break;
			case EAST:
				changeBits |= LogicLayerHelpers.LOGIC_BIT_EAST;
				break;
			case DOWN:
				changeBits |= LogicLayerHelpers.LOGIC_BIT_DOWN;
				break;
			default:
				// Missing case statement.
				throw Assert.unreachable();
			}
		}
		else
		{
			changeBits |= LogicLayerHelpers.LOGIC_BIT_EAST;
			changeBits |= LogicLayerHelpers.LOGIC_BIT_WEST;
			changeBits |= LogicLayerHelpers.LOGIC_BIT_NORTH;
			changeBits |= LogicLayerHelpers.LOGIC_BIT_SOUTH;
			changeBits |= LogicLayerHelpers.LOGIC_BIT_UP;
			changeBits |= LogicLayerHelpers.LOGIC_BIT_DOWN;
		}
		return changeBits;
	}

	private <T> void _storeSafe(ByteBuffer buffer, Aspect<T, ?> type)
	{
		T value = _getDataSpecial(type);
		type.codec().storeData(buffer, value);
	}
}
