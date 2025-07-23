package com.jeffdisher.october.data;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.utils.Assert;
import com.jeffdisher.october.utils.Encoding;


/**
 * This is a "pseudo-octree" (kind of like OctreeObject) in that it implements the IOctree interface but has an
 * in-memory and on-disk representation which is unrelated to an octree.
 * This should be used in cases where bytes are stored, but have little shared by proximity, and are almost always 0.
 * 
 * The internal storage follows a basic rule:  An array only exists if there is content within it, meaning that 0-filled
 * leaf arrays are always null, null-filled branch arrays are null, and the top-level will be null if empty.
 * 
 * Our serialized representation is always a byte for the number of arrays at a level, followed by the byte index of
 * that array and either the same recursive structure or the raw byte array data, for a leaf.  This means that the
 * top-level is somewhat special since it is the only place where a null can be found - also the only place where a "0"
 * length can be written.
 */
public class OctreeInflatedByte implements IOctree<Byte>
{
	public static OctreeInflatedByte empty()
	{
		// A null array is just "0" for every value.
		return new OctreeInflatedByte(null);
	}


	private byte[][][] _zyxData;

	private OctreeInflatedByte(byte[][][] zyxData)
	{
		_zyxData = zyxData;
	}

	@Override
	public <O extends IOctree<Byte>> Byte getData(Aspect<Byte, O> type, BlockAddress address)
	{
		byte value;
		if (null == _zyxData)
		{
			value = 0;
		}
		else
		{
			byte z = address.z();
			byte[][] yxData = _zyxData[z];
			if (null == yxData)
			{
				value = 0;
			}
			else
			{
				byte y = address.y();
				byte[] xData = yxData[y];
				if (null == xData)
				{
					value = 0;
				}
				else
				{
					byte x = address.x();
					value = xData[x];
				}
			}
		}
		return type.type().cast(Byte.valueOf(value));
	}

	@Override
	public void setData(BlockAddress address, Byte value)
	{
		byte correct = ((Byte)value).byteValue();
		// The value cannot be negative.
		Assert.assertTrue(correct >= 0);
		
		// See if this is setting a value or clearing a value.
		byte z = address.z();
		byte y = address.y();
		byte x = address.x();
		if (correct > 0)
		{
			// We are setting a value so we might need to build arrays.
			if (null == _zyxData)
			{
				_zyxData = new byte[Encoding.CUBOID_EDGE_SIZE][][];
			}
			byte[][] yxData = _zyxData[z];
			if (null == yxData)
			{
				yxData = new byte[Encoding.CUBOID_EDGE_SIZE][];
				_zyxData[z] = yxData;
			}
			byte[] xData = yxData[y];
			if (null == xData)
			{
				xData = new byte[Encoding.CUBOID_EDGE_SIZE];
				yxData[y] = xData;
			}
			xData[x] = correct;
		}
		else
		{
			// We are clearing a value so we might need to clear arrays.
			if (null != _zyxData)
			{
				byte[][] yxData = _zyxData[z];
				if (null != yxData)
				{
					byte[] xData = yxData[y];
					if (null != xData)
					{
						xData[x] = correct;
						// Now, see if we need to clear this.
						boolean shouldClear = true;
						for (int i = 0; shouldClear && (i < xData.length); ++i)
						{
							shouldClear = (0 == xData[i]);
						}
						if (shouldClear)
						{
							yxData[y] = null;
						}
					}
					if (null == yxData[y])
					{
						// See if we need to clear this level.
						boolean shouldClear = true;
						for (int i = 0; shouldClear && (i < yxData.length); ++i)
						{
							shouldClear = (null == yxData[i]);
						}
						if (shouldClear)
						{
							_zyxData[z] = null;
						}
					}
				}
				if (null == _zyxData[z])
				{
					// See if we need to clear this level.
					boolean shouldClear = true;
					for (int i = 0; shouldClear && (i < _zyxData.length); ++i)
					{
						shouldClear = (null == _zyxData[i]);
					}
					if (shouldClear)
					{
						_zyxData = null;
					}
				}
			}
		}
	}

	@Override
	public void walkData(IWalkerCallback<Byte> callback, Byte valueToSkip)
	{
		byte skip = valueToSkip.byteValue();
		
		// This variant only allows skipping 0 since that is how it is structured.
		Assert.assertTrue(0 == skip);
		if (null != _zyxData)
		{
			for (byte z = 0; z < Encoding.CUBOID_EDGE_SIZE; ++z)
			{
				byte[][] yxData = _zyxData[z];
				if (null != yxData)
				{
					for (byte y = 0; y < Encoding.CUBOID_EDGE_SIZE; ++y)
					{
						byte[] xData = yxData[y];
						if (null != xData)
						{
							for (byte x = 0; x < Encoding.CUBOID_EDGE_SIZE; ++x)
							{
								byte value = xData[x];
								if (skip != value)
								{
									BlockAddress address = new BlockAddress(x, y, z);
									callback.visit(address, (byte)1, value);
								}
							}
						}
					}
				}
			}
		}
	}

	@Override
	public Object serializeResumable(Object lastCallState, ByteBuffer buffer, IObjectCodec<Byte> codec)
	{
		// NOTE:  For serializing, we just pass a 3-element byte[] back:  zLength, zIndex, yIndex.
		byte[] ourState = (byte[]) lastCallState;
		byte zIndex = (null != ourState)
				? ourState[1]
				: 0
		;
		byte yIndex = (null != ourState)
				? ourState[2]
				: 0
		;
		// This means that we can minimally write either:
		// -(1 byte) the byte for z-level entries
		// -(3 + 32 bytes) the byte for the z index, the byte for the number of y entries, the byte for the y index, the 32 bytes for all x values.
		// To this end, we will require at least 36 bytes available so we can combine these on the first call.
		Assert.assertTrue(buffer.remaining() >= 1);
		
		byte[] stateToResume = null;
		if (null != _zyxData)
		{
			byte zLength = _numberOfRefs(_zyxData);
			if (null != ourState)
			{
				Assert.assertTrue(ourState[0] == zLength);
			}
			else
			{
				buffer.put(zLength);
			}
			
			for (byte i = zIndex; (null == stateToResume) && (i < _zyxData.length); ++i)
			{
				byte[][] yxData = _zyxData[i];
				if (null != yxData)
				{
					if (buffer.remaining() >= 35)
					{
						buffer.put(i);
						byte yLength = _numberOfRefs(yxData);
						buffer.put(yLength);
						
						// Now, walk the y-indices and write the byte arrays.
						for (byte j = yIndex; (null == stateToResume) && (j < yxData.length); ++j)
						{
							byte[] xData = yxData[j];
							if (null != xData)
							{
								if (buffer.remaining() >= 33)
								{
									buffer.put(j);
									buffer.put(xData);
								}
								else
								{
									stateToResume = new byte[] {zLength, i, j};
								}
							}
						}
					}
					else
					{
						stateToResume = new byte[] {zLength, i, 0};
					}
				}
			}
		}
		else
		{
			// Just write 0.
			buffer.put((byte)0);
		}
		return stateToResume;
	}

	@Override
	public Object deserializeResumable(Object lastCallState, ByteBuffer buffer, IObjectCodec<Byte> codec)
	{
		// NOTE:  For serializing, we just pass a Short back:  It is the byte-combination of the z-y array to next copy.
		byte[] ourState = (byte[]) lastCallState;
		byte zIndex = (null != ourState)
				? ourState[1]
				: 0
		;
		byte yIndex = (null != ourState)
				? ourState[2]
				: 0
		;
		Assert.assertTrue(buffer.remaining() >= 1);
		
		byte top;
		if (null != ourState)
		{
			top = ourState[0];
		}
		else
		{
			top = buffer.get();
		}
		byte[] stateToResume = null;
		if (top > 0)
		{
			if (null == ourState)
			{
				_zyxData = new byte[Encoding.CUBOID_EDGE_SIZE][][];
			}
			for (byte i = zIndex; i < top; ++i)
			{
				if (buffer.remaining() >= 35)
				{
					byte index = buffer.get();
					byte[][] yxData = new byte[Encoding.CUBOID_EDGE_SIZE][];
					_zyxData[index] = yxData;
					byte yMax = buffer.get();
					
					for (byte j = yIndex; j < yMax; ++j)
					{
						if (buffer.remaining() >= 33)
						{
							byte inner = buffer.get();
							byte[] xData = new byte[Encoding.CUBOID_EDGE_SIZE];
							yxData[inner] = xData;
							
							buffer.get(xData);
						}
						else
						{
							stateToResume = new byte[] {top, i, j};
						}
					}
				}
				else
				{
					stateToResume = new byte[] {top, i, 0};
				}
			}
		}
		else
		{
			_zyxData = null;
		}
		return stateToResume;
	}

	public OctreeInflatedByte cloneData()
	{
		byte[][][] clone;
		if (null != _zyxData)
		{
			clone = new byte[Encoding.CUBOID_EDGE_SIZE][][];
			for (int z = 0; z < clone.length; ++z)
			{
				byte[][] originalY = _zyxData[z];
				if (null != originalY)
				{
					byte[][] inner = new byte[Encoding.CUBOID_EDGE_SIZE][];
					
					for (int y = 0; y < originalY.length; ++y)
					{
						if (null != originalY[y])
						{
							inner[y] = originalY[y].clone();
						}
					}
					clone[z] = inner;
				}
			}
		}
		else
		{
			clone = null;
		}
		return new OctreeInflatedByte(clone);
	}


	private static byte _numberOfRefs(Object[] array)
	{
		byte count = 0;
		for (int i = 0; i < array.length; ++i)
		{
			if (null != array[i])
			{
				count += 1;
			}
		}
		return count;
	}
}
