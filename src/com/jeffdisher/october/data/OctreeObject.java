package com.jeffdisher.october.data;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;


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


	private final Map<Short, Object> _data;

	private OctreeObject(Map<Short, Object> data)
	{
		_data = data;
	}

	@Override
	public <T> T getData(Aspect<T> type, byte x, byte y, byte z)
	{
		short hash = _buildHash(x, y, z);
		return type.type().cast(_data.get(hash));
	}

	@Override
	public <T> void setData(byte x, byte y, byte z, T value)
	{
		short hash = _buildHash(x, y, z);
		_data.put(hash, value);
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

	@Override
	public IOctree cloneData()
	{
		return new OctreeObject(new HashMap<>(_data));
	}


	private short _buildHash(byte x, byte y, byte z)
	{
		// We know that none of the coordinates are more than 5 bytes so munge these into the short.
		short hash = (short) ((Byte.toUnsignedInt(x) << 10)
				| (Byte.toUnsignedInt(y)  << 5)
				| Byte.toUnsignedInt(z)
		);
		return hash;
	}
}
