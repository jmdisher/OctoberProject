package com.jeffdisher.october.data;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.registries.AspectRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.utils.Assert;


/**
 * A proxy to access mutable block data within a specific cuboid which is currently being updated.
 */
public class MutableBlockProxy
{
	public final AbsoluteLocation absoluteLocation;
	private final BlockAddress _address;
	private final IReadOnlyCuboidData _data;

	// We cache any writes so that they are just flushed at the end.
	private final byte[] _write7;
	private final short[] _write15;
	private final Object[] _writeObject;
	private final Object[] _writes;

	public MutableBlockProxy(AbsoluteLocation absoluteLocation, BlockAddress address, IReadOnlyCuboidData data)
	{
		this.absoluteLocation = absoluteLocation;
		_address = address;
		_data = data;
		
		_write7 = new byte[AspectRegistry.ALL_ASPECTS.length];
		_write15 = new short[AspectRegistry.ALL_ASPECTS.length];
		_writeObject = new Object[AspectRegistry.ALL_ASPECTS.length];
		_writes = new Object[AspectRegistry.ALL_ASPECTS.length];
	}

	public byte getData7(Aspect<Byte, ?> type)
	{
		int index = type.index();
		return (null != _writes[index])
				? _write7[index]
				: _data.getData7(type, _address)
		;
	}

	public void setData7(Aspect<Byte, ?> type, byte value)
	{
		_write7[type.index()] = value;
		_writes[type.index()] = _write7;
	}

	public short getData15(Aspect<Short, ?> type)
	{
		int index = type.index();
		return (null != _writes[index])
				? _write15[index]
				: _data.getData15(type, _address)
		;
	}

	public void setData15(Aspect<Short, ?> type, short value)
	{
		_write15[type.index()] = value;
		_writes[type.index()] = _write15;
	}

	public <T> T getDataSpecial(Aspect<T, ?> type)
	{
		int index = type.index();
		return (null != _writes[index])
				? type.type().cast(_writeObject[index])
				: _data.getDataSpecial(type, _address)
		;
	}

	public <T> void setDataSpecial(Aspect<T, ?> type, T value)
	{
		_writeObject[type.index()] = value;
		_writes[type.index()] = _writeObject;
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
}
