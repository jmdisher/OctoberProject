package com.jeffdisher.october.data;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.utils.Assert;


public class OctreeObject<T> implements IOctree<T>
{
	public static <T> OctreeObject<T> load(DeserializationContext context, IObjectCodec<T> codec)
	{
		ByteBuffer raw = context.buffer();
		// We encode these as the number of elements in a single cuboid (which is a short - at most 15 bits of elements).
		short count = raw.getShort();
		Map<Short, T> data = new HashMap<>();
		// Each of these elements is a pair of Short and abstract data for some object.
		for (short i = 0; i < count; ++i)
		{
			short key = raw.getShort();
			T value = codec.loadData(context);
			data.put(key, value);
		}
		return new OctreeObject<>(data);
	}

	public static <T> OctreeObject<T> create()
	{
		// Just start with an empty map.
		return new OctreeObject<>(new HashMap<>());
	}

	/**
	 * A helper used to create the correct type binding for aspect registry.
	 * 
	 * @param <T> The type to wrap into the octree.
	 * @return The class for this decorated octree type.
	 */
	@SuppressWarnings("unchecked")
	public static <T> Class<OctreeObject<T>> getDecoratedClass()
	{
		return (Class<OctreeObject<T>>) new OctreeObject<>(null).getClass();
	}


	private final Map<Short, T> _data;

	private OctreeObject(Map<Short, T> data)
	{
		_data = data;
	}

	@Override
	public <O extends IOctree<T>> T getData(Aspect<T, O> type, BlockAddress address)
	{
		short hash = _buildHash(address);
		return type.type().cast(_data.get(hash));
	}

	@Override
	public void setData(BlockAddress address, T value)
	{
		short hash = _buildHash(address);
		if (null != value)
		{
			_data.put(hash, value);
		}
		else
		{
			_data.remove(hash);
		}
	}

	@Override
	public void walkData(IWalkerCallback<T> callback, T valueToSkip)
	{
		// In this implementation, it is only appropriate to skip null values (since we can't evaluate equality and have no other use-case).
		Assert.assertTrue(null == valueToSkip);
		
		byte size = 1;
		for (Map.Entry<Short, T> elt : _data.entrySet())
		{
			short hash = elt.getKey();
			Object value = elt.getValue();
			if (valueToSkip != value)
			{
				// Extract the location from the hash and issue the callback.
				byte x = (byte)((hash >> 10) & 0x1F);
				byte y = (byte)((hash >> 5) & 0x1F);
				byte z = (byte)((hash) & 0x1F);
				@SuppressWarnings("unchecked")
				T castValue = (T) value;
				callback.visit(new BlockAddress(x, y, z), size, castValue);
			}
		}
	}

	@Override
	public Object serializeResumable(Object lastCallState, ByteBuffer buffer, IObjectCodec<T> codec)
	{
		int keysToSkip = (null != lastCallState)
				? (Integer) lastCallState
				: -1
		;
		// We want to verify that we aren't stuck in a loop.
		boolean canFail = (null == lastCallState);
		int spaceInBuffer = buffer.remaining();
		Integer resumeToken = null;
		if (-1 == keysToSkip)
		{
			// We still need to write our size so see if that fits.
			if (spaceInBuffer >= Integer.BYTES)
			{
				// We can write this.
				int count = _data.size();
				buffer.putInt(count);
				canFail = true;
				keysToSkip = 0;
			}
			else
			{
				// We can't fit this so resume later.
				Assert.assertTrue(canFail);
				resumeToken = -1;
			}
		}
		
		// See if we can proceed to copy.
		if (null == resumeToken)
		{
			int keysCopied = keysToSkip;
			// Note that we store every tuple together so make sure that they both fit or wind the buffer back.
			for (Map.Entry<Short, T> elt : _data.entrySet())
			{
				if (keysToSkip > 0)
				{
					keysToSkip -= 1;
				}
				else
				{
					int position = buffer.position();
					try
					{
						short key = elt.getKey();
						buffer.putShort(key);
						T value = elt.getValue();
						codec.storeData(buffer, value);
						canFail = true;
						keysCopied += 1;
					}
					catch (BufferOverflowException e)
					{
						// This didn't fit so fail out - the number of keys copied will be skipped on the next call.
						buffer.position(position);
						Assert.assertTrue(canFail);
						resumeToken = keysCopied;
						break;
					}
				}
			}
		}
		return resumeToken;
	}

	@Override
	public Object deserializeResumable(Object lastCallState, DeserializationContext context, IObjectCodec<T> codec)
	{
		ByteBuffer buffer = context.buffer();
		
		// NOTE:  For deserializing, we just pass an Integer back:  Size is always in the first packet so just the number of pairs we still need.
		
		int pairsRemaining;
		if (null == lastCallState)
		{
			// This means that we should be able to read at least the size.
			Assert.assertTrue(buffer.remaining() >= Integer.BYTES);
			pairsRemaining = buffer.getInt();
		}
		else
		{
			pairsRemaining = (Integer) lastCallState;
		}
		// We want to verify that we aren't stuck in a loop.
		boolean canFail = (null == lastCallState);
		
		// We read each pair at a time so we will fail out if we fail to read with underflow.
		Integer resumeToken = null;
		for (int i = 0; i < pairsRemaining; ++i)
		{
			int position = buffer.position();
			try
			{
				short key = buffer.getShort();
				T value = codec.loadData(context);
				_data.put(key, value);
				canFail = true;
			}
			catch (BufferUnderflowException e)
			{
				// We ran out of data so update our state to include how many we did read.
				buffer.position(position);
				Assert.assertTrue(canFail);
				resumeToken = pairsRemaining - i;
				break;
			}
		}
		return resumeToken;
	}

	public OctreeObject<T> cloneData(Class<T> type, Function<T, T> valueCopier)
	{
		Map<Short, T> newMap = new HashMap<>();
		for (Map.Entry<Short, T> elt : _data.entrySet())
		{
			T original = type.cast(elt.getValue());
			T copy = valueCopier.apply(original);
			newMap.put(elt.getKey(), copy);
		}
		return new OctreeObject<>(newMap);
	}

	/**
	 * While it is possible for aspects to have special uses which want to avoid referencing things which might be
	 * immutable in this data structure, the common-case is that the values in the map are already immutable so this
	 * helper merely clones the map with the same instances inside (shallow).
	 * 
	 * @return A new instance with a new data map, yet containing the same key and value instances as the original.
	 */
	public OctreeObject<T> cloneMapShallow()
	{
		return new OctreeObject<>(new HashMap<>(_data));
	}


	private short _buildHash(BlockAddress address)
	{
		// We know that none of the coordinates are more than 5 bytes so munge these into the short.
		short hash = (short) ((Byte.toUnsignedInt(address.x()) << 10)
				| (Byte.toUnsignedInt(address.y())  << 5)
				| Byte.toUnsignedInt(address.z())
		);
		return hash;
	}
}
