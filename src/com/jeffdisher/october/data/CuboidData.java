package com.jeffdisher.october.data;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.utils.Assert;


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

	public static CuboidData createNew(CuboidAddress cuboidAddress, IOctree[] data)
	{
		return new CuboidData(cuboidAddress, data);
	}


	private final CuboidAddress _cuboidAddress;
	private final IOctree[] _data;

	private CuboidData(CuboidAddress cuboidAddress, IOctree[] data)
	{
		_cuboidAddress = cuboidAddress;
		_data = data;
	}

	@Override
	public CuboidAddress getCuboidAddress()
	{
		return _cuboidAddress;
	}

	@Override
	public byte getData7(Aspect<Byte> type, BlockAddress address)
	{
		return _data[type.index()].getData(type, address);
	}

	public void setData7(Aspect<Byte> type, BlockAddress address, byte value)
	{
		_data[type.index()].setData(address, value);
	}

	@Override
	public short getData15(Aspect<Short> type, BlockAddress address)
	{
		return _data[type.index()].getData(type, address);
	}

	public void setData15(Aspect<Short> type, BlockAddress address, short value)
	{
		_data[type.index()].setData(address, value);
	}

	@Override
	public <T> T getDataSpecial(Aspect<T> type, BlockAddress address)
	{
		return _data[type.index()].getData(type, address);
	}

	public <T> void setDataSpecial(Aspect<T> type, BlockAddress address, T value)
	{
		_data[type.index()].setData(address, value);
	}

	@Override
	public Block getBlock(BlockAddress address, Aspect<?>[] aspects)
	{
		// We should have one data plane for each aspect.
		Assert.assertTrue(_data.length == aspects.length);
		Object[] objects = new Object[_data.length];
		for (int i = 0; i < aspects.length; ++i)
		{
			objects[i] = _data[i].getData(aspects[i], address);
		}
		return new Block(objects);
	}
}
