package com.jeffdisher.october.data;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.aspects.AspectRegistry;
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
	/**
	 * When serializing the cuboid, we will only try to start serializing a new aspect if we have at least this many
	 * bytes remaining in the buffer (this is to allow them a non-resumable "header" + 1 byte to avoid an extra state).
	 */
	public static final int MINIMUM_ASPECT_BUFFER_BYTES = 17;

	public static CuboidData mutableClone(IReadOnlyCuboidData raw)
	{
		CuboidData original = (CuboidData)raw;
		IOctree[] newer = new IOctree[original._data.length];
		for (int i = 0; i < newer.length; ++i)
		{
			newer[i] = _cloneOneOctree(AspectRegistry.ALL_ASPECTS[i], original._data[i]);
		}
		return new CuboidData(original._cuboidAddress, newer);
	}

	public static CuboidData createNew(CuboidAddress cuboidAddress, IOctree[] data)
	{
		// We expect that there is a data plane for every aspect.
		Assert.assertTrue(AspectRegistry.ALL_ASPECTS.length == data.length);
		return new CuboidData(cuboidAddress, data);
	}

	public static CuboidData createEmpty(CuboidAddress cuboidAddress)
	{
		IOctree[] data = new IOctree[AspectRegistry.ALL_ASPECTS.length];
		for (int i = 0; i < data.length; ++i)
		{
			data[i] = AspectRegistry.ALL_ASPECTS[i].emptyTreeSupplier().get();
		}
		return new CuboidData(cuboidAddress, data);
	}

	private static <O extends IOctree> IOctree _cloneOneOctree(Aspect<?,O> aspect, IOctree rawOriginal)
	{
		O original = aspect.octreeType().cast(rawOriginal);
		return aspect.deepMutableClone().apply(original);
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
	public byte getData7(Aspect<Byte, ?> type, BlockAddress address)
	{
		return _data[type.index()].getData(type, address);
	}

	public void setData7(Aspect<Byte, ?> type, BlockAddress address, byte value)
	{
		_data[type.index()].setData(address, value);
	}

	@Override
	public short getData15(Aspect<Short, ?> type, BlockAddress address)
	{
		return _data[type.index()].getData(type, address);
	}

	public void setData15(Aspect<Short, ?> type, BlockAddress address, short value)
	{
		_data[type.index()].setData(address, value);
	}

	@Override
	public <T> T getDataSpecial(Aspect<T, ?> type, BlockAddress address)
	{
		return _data[type.index()].getData(type, address);
	}

	public <T> void setDataSpecial(Aspect<T, ?> type, BlockAddress address, T value)
	{
		_data[type.index()].setData(address, value);
	}

	@Override
	public <T> void walkData(Aspect<T, ?> type, IOctree.IWalkerCallback<T> callback, T valueToSkip)
	{
		_data[type.index()].walkData(callback, valueToSkip);
	}

	@Override
	public Object serializeResumable(Object lastCallState, ByteBuffer buffer)
	{
		// Our implementation requires some minimum buffer size.
		Assert.assertTrue(buffer.remaining() >= MINIMUM_ASPECT_BUFFER_BYTES);
		
		_ResumableState previousCall = (_ResumableState) lastCallState;
		int startIndex = (null != previousCall)
				? previousCall.currentAspectIndex
				: 0
		;
		Object octreeState = (null != previousCall)
				? previousCall.octreeState
				: null
		;
		
		_ResumableState resume = null;
		for (int i = startIndex; (null == resume) && (i < AspectRegistry.ALL_ASPECTS.length); ++i)
		{
			if (buffer.remaining() >= MINIMUM_ASPECT_BUFFER_BYTES)
			{
				Aspect<?, ?> type = AspectRegistry.ALL_ASPECTS[i];
				Object octreeResume = _data[i].serializeResumable(octreeState, buffer, type.codec());
				if (null != octreeResume)
				{
					resume = new _ResumableState(i, octreeResume);
				}
				// Clear this resumed state if we are moving to the next aspect.
				octreeState = null;
			}
			else
			{
				// If we are out of data, we want to resume here.
				resume = new _ResumableState(i, null);
			}
		}
		return resume;
	}

	public Object deserializeResumable(Object lastCallState, ByteBuffer buffer)
	{
		_ResumableState previousCall = (_ResumableState) lastCallState;
		return _deserializeResumablePartial(previousCall, buffer, AspectRegistry.ALL_ASPECTS.length);
	}

	public void deserializeSomeAspectsFully(ByteBuffer buffer, int aspectCount)
	{
		_ResumableState resume = _deserializeResumablePartial(null, buffer, aspectCount);
		// This is only used when reading from disk, when the buffer is fully mapped.
		Assert.assertTrue(null == resume);
	}

	/**
	 * NOTE:  This helper allows mutable access to the internal data trees of the instance so it should only be used in
	 * cases which are explicitly unsafe as the data structure should generally be considered copy-on-write with this
	 * data array being very private.
	 * 
	 * @return A reference the internal data octrees of the receiver.
	 */
	public IOctree[] unsafeDataAccess()
	{
		return _data;
	}


	private _ResumableState _deserializeResumablePartial(_ResumableState previousCall, ByteBuffer buffer, int aspectCount)
	{
		int startIndex = (null != previousCall)
				? previousCall.currentAspectIndex
				: 0
		;
		Object octreeState = (null != previousCall)
				? previousCall.octreeState
				: null
		;
		
		_ResumableState resume = null;
		for (int i = startIndex; (null == resume) && (i < aspectCount); ++i)
		{
			if (buffer.hasRemaining())
			{
				Aspect<?, ?> type = AspectRegistry.ALL_ASPECTS[i];
				Object octreeResume = _data[i].deserializeResumable(octreeState, buffer, type.codec());
				if (null != octreeResume)
				{
					resume = new _ResumableState(i, octreeResume);
				}
				// Clear this resumed state if we are moving to the next aspect.
				octreeState = null;
			}
			else
			{
				// If we are out of data, we want to resume here.
				resume = new _ResumableState(i, null);
			}
		}
		return resume;
	}


	private static record _ResumableState(int currentAspectIndex, Object octreeState)
	{
	}
}
