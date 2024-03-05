package com.jeffdisher.october.data;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.aspects.FuelAspect;
import com.jeffdisher.october.aspects.InventoryAspect;
import com.jeffdisher.october.registries.AspectRegistry;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CraftOperation;
import com.jeffdisher.october.types.FuelState;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.utils.Assert;


/**
 * A proxy to access mutable block data within a specific cuboid which is currently being updated.
 */
public class MutableBlockProxy implements IMutableBlockProxy
{
	public final AbsoluteLocation absoluteLocation;
	private final BlockAddress _address;
	private final IReadOnlyCuboidData _data;

	// We cache any writes so that they are just flushed at the end.
	private final byte[] _write7;
	private final short[] _write15;
	private final Object[] _writeObject;
	private final Object[] _writes;

	private Item _cachedItem;

	public MutableBlockProxy(AbsoluteLocation absoluteLocation, BlockAddress address, IReadOnlyCuboidData data)
	{
		this.absoluteLocation = absoluteLocation;
		_address = address;
		_data = data;
		
		_write7 = new byte[AspectRegistry.ALL_ASPECTS.length];
		_write15 = new short[AspectRegistry.ALL_ASPECTS.length];
		_writeObject = new Object[AspectRegistry.ALL_ASPECTS.length];
		_writes = new Object[AspectRegistry.ALL_ASPECTS.length];
		
		// We cache the item since we use it to make some other internal decisions.
		_cachedItem = ItemRegistry.BLOCKS_BY_TYPE[_getData15(AspectRegistry.BLOCK)];
	}

	@Override
	public Item getItem()
	{
		return _cachedItem;
	}

	@Override
	public void setItemAndClear(Item item)
	{
		// Cache the item for internal checks.
		_cachedItem = item;
		
		// Set the updated item type.
		_setData15(AspectRegistry.BLOCK, item.number());
		
		// Clear other aspects since they are all based on the item type.
		_setDataSpecial(AspectRegistry.INVENTORY, null);
		_setData15(AspectRegistry.DAMAGE, (short)0);
		_setDataSpecial(AspectRegistry.CRAFTING, null);
	}

	@Override
	public Inventory getInventory()
	{
		Inventory inv = _getDataSpecial(AspectRegistry.INVENTORY);
		// We can't return null if this block can support one.
		if (null == inv)
		{
			int size = InventoryAspect.getSizeForType(_cachedItem);
			if (size > 0)
			{
				inv = Inventory.start(size).finish();
			}
		}
		return inv;
	}

	@Override
	public void setInventory(Inventory inv)
	{
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
	public short getDamage()
	{
		return _getData15(AspectRegistry.DAMAGE);
	}

	@Override
	public void setDamage(short damage)
	{
		_setData15(AspectRegistry.DAMAGE, damage);
	}

	@Override
	public CraftOperation getCrafting()
	{
		return _getDataSpecial(AspectRegistry.CRAFTING);
	}

	@Override
	public void setCrafting(CraftOperation crafting)
	{
		_setDataSpecial(AspectRegistry.CRAFTING, crafting);
	}

	@Override
	public FuelState getFuel()
	{
		FuelState fuel = _getDataSpecial(AspectRegistry.FUELED);
		// We can't return null if this block can support fuel.
		if ((null == fuel) && FuelAspect.doesHaveFuelInventory(_cachedItem))
		{
			fuel = new FuelState(0, Inventory.start(FuelAspect.CAPACITY).finish());
		}
		return fuel;
	}

	@Override
	public void setFuel(FuelState fuel)
	{
		// If this is empty, we want to store null, instead.
		if (fuel.isEmpty())
		{
			_setDataSpecial(AspectRegistry.FUELED, null);
		}
		else
		{
			_setDataSpecial(AspectRegistry.FUELED, fuel);
		}
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		for (int i = 0; i < AspectRegistry.ALL_ASPECTS.length; ++i)
		{
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
				Object value = _getDataSpecial(type);
				type.codec().storeData(buffer, value);
			}
		}
	}

	// We check these types internally.
	@SuppressWarnings("unchecked")
	@Override
	public void deserializeFromBuffer(ByteBuffer buffer)
	{
		for (int i = 0; i < AspectRegistry.ALL_ASPECTS.length; ++i)
		{
			Aspect<?, ?> type = AspectRegistry.ALL_ASPECTS[i];
			if (Short.class == type.type())
			{
				short value = buffer.getShort();
				_setData15((Aspect<Short, ?>) type, value);
			}
			else if (Byte.class == type.type())
			{
				byte value = buffer.get();
				_setData7((Aspect<Byte, ?>) type, value);
			}
			else
			{
				_readAndStore(type, buffer);
			}
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
		for (int i = 0; !didChange && (i < _writes.length); ++i)
		{
			// Check what was modified.
			if (_write15 == _writes[i])
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
			if (_write15 == _writes[i])
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

	private <T> void _readAndStore(Aspect<T, ?> type, ByteBuffer buffer)
	{
		T value = type.codec().loadData(buffer);
		_setDataSpecial(type, value);
	}
}
