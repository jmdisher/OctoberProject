package com.jeffdisher.october.data;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.types.BlockAddress;


public class OctreeObject implements IOctree
{
	public static <T> OctreeObject load(ByteBuffer raw, IAspectCodec<T> codec)
	{
		// We encode these as the number of elements in a single cuboid (which is a short - at most 15 bits of elements).
		short count = raw.getShort();
		Map<Short, Object> data = new HashMap<>();
		// Each of these elements is a pair of Short and abstract data for some object.
		for (short i = 0; i < count; ++i)
		{
			short key = raw.getShort();
			Object value = codec.loadData(raw);
			data.put(key, value);
		}
		return new OctreeObject(data);
	}

	public static OctreeObject create()
	{
		// Just start with an empty map.
		return new OctreeObject(new HashMap<>());
	}


	private final Map<Short, Object> _data;

	private OctreeObject(Map<Short, Object> data)
	{
		_data = data;
	}

	@Override
	public <T> T getData(Aspect<T> type, BlockAddress address)
	{
		short hash = _buildHash(address);
		return type.type().cast(_data.get(hash));
	}

	@Override
	public <T> void setData(BlockAddress address, T value)
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
	public void serialize(ByteBuffer buffer, IAspectCodec<?> codec)
	{
		short count = (short) _data.size();
		buffer.putShort(count);
		for (Map.Entry<Short, Object> elt : _data.entrySet())
		{
			short key = elt.getKey();
			buffer.putShort(key);
			Object value = elt.getValue();
			codec.storeData(buffer, value);
		}
	}

	public <T> OctreeObject cloneData(Class<T> type, Function<T, T> valueCopier)
	{
		Map<Short, Object> newMap = new HashMap<>();
		for (Map.Entry<Short, Object> elt : _data.entrySet())
		{
			T original = type.cast(elt.getValue());
			T copy = valueCopier.apply(original);
			newMap.put(elt.getKey(), copy);
		}
		return new OctreeObject(newMap);
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
