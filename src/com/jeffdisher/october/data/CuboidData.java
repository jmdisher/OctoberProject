package com.jeffdisher.october.data;

import com.jeffdisher.october.aspects.Aspect;

/**
 * A single 32x32x32 cuboid region.
 * A cuboid stores all the data associated with the blocks in the region, representing them as an octree.
 * All x/y/z coordinates are relative to the cuboid so they are all in the range of [0..31].
 */
public class CuboidData implements IReadOnlyCuboidData
{
	public static CuboidData mutableClone(IReadOnlyCuboidData raw)
	{
		CuboidData original = (CuboidData)raw;
		IOctree[] newer = new IOctree[original._data.length];
		for (int i = 0; i < newer.length; ++i)
		{
			newer[i] = original._data[i].cloneData();
		}
		return new CuboidData(original._cuboidAddress, newer);
	}

	public static CuboidData createNew(short[] cuboidAddress, IOctree[] data)
	{
		return new CuboidData(cuboidAddress, data);
	}


	private final short[] _cuboidAddress;
	private final IOctree[] _data;

	private CuboidData(short[] cuboidAddress, IOctree[] data)
	{
		_cuboidAddress = cuboidAddress;
		_data = data;
	}

	@Override
	public short[] getCuboidAddress()
	{
		return _cuboidAddress;
	}

	@Override
	public byte getData7(Aspect<Byte> type, byte x, byte y, byte z)
	{
		return _data[type.index()].getData(type, x, y, z);
	}

	public void setData7(Aspect<Byte> type, byte x, byte y, byte z, byte value)
	{
		_data[type.index()].setData(x, y, z, value);
	}

	@Override
	public short getData15(Aspect<Short> type, byte x, byte y, byte z)
	{
		return _data[type.index()].getData(type, x, y, z);
	}

	public void setData15(Aspect<Short> type, byte x, byte y, byte z, short value)
	{
		_data[type.index()].setData(x, y, z, value);
	}

	@Override
	public <T> T getDataSpecial(Aspect<T> type, byte x, byte y, byte z)
	{
		return _data[type.index()].getData(type, x, y, z);
	}

	public <T> void setDataSpecial(Aspect<T> type, byte x, byte y, byte z, T value)
	{
		_data[type.index()].setData(x, y, z, value);
	}

	@Override
	public Block getBlock(byte[] blockAddress)
	{
		Object[] aspects = new Object[_data.length];
		for (int i = 0; i < aspects.length; ++i)
		{
			// TODO:  Plumb down a common aspect registry.
			aspects[i] = _data[i].getData(new Aspect<>("unknown", i, Object.class), blockAddress[0], blockAddress[1], blockAddress[2]);
		}
		return new Block(aspects);
	}
}
