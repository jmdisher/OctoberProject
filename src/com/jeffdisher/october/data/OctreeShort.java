package com.jeffdisher.october.data;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

import com.jeffdisher.october.utils.Assert;
import com.jeffdisher.october.utils.Encoding;


public class OctreeShort implements IOctree
{
	public static OctreeShort load(ByteBuffer raw)
	{
		// We want to parse forward until we find the end of the cuboid, then reverse and copy the bytes out.
		ShortBuffer buffer = raw.asShortBuffer();
		int start = buffer.position();
		// We just want to read to the end of the octree, to see how big it is, so just say we are looking for the last element
		short verify = _findValue(buffer, (byte)31, (byte)31, (byte)31, (byte)16);
		Assert.assertTrue(verify >= 0);
		int end = buffer.position();
		short[] data = new short[end - start];
		buffer.position(start);
		buffer.get(data);
		return new OctreeShort(data);
	}

	public static OctreeShort create(short fillValue)
	{
		short[] data = new short[] { Encoding.setShortTag(fillValue) };
		return new OctreeShort(data);
	}


	private static short _findValue(ShortBuffer buffer, byte x, byte y, byte z, byte half)
	{
		final short value;
		short header = buffer.get();
		if (Encoding.checkShortTag(header))
		{
			// If the value is negative, the value represents the entire level.
			value = Encoding.clearShortTag(header);
		}
		else if (half > 0)
		{
			// Otherwise, the value MUST be 0 and means that we need to dig into the 8 sub-trees.
			Assert.assertTrue(0 == header);
			// Due to the way the octree is represented, we must walk all subtrees "before" the one where we are finding the element.
			// The order of the sub-trees is a 3-level nested loop:  x is outer-most, y is middle, and z is inner-most.
			int targetX = (x < half) ? 0 : 1;
			int targetY = (y < half) ? 0 : 1;
			int targetZ = (z < half) ? 0 : 1;
			short found = -1;
			for (int i = 0; i < 2; ++i)
			{
				for (int j = 0; j < 2; ++j)
				{
					for (int k = 0; k < 2; ++k)
					{
						// Unless this is the one we want to look at, we just want to look for the last sub-element of each sub-tree.
						if ((i == targetX) && (j == targetY) && (k == targetZ))
						{
							found = _findValue(buffer, (byte)(x & ~half), (byte)(y & ~half), (byte)(z & ~half), (byte)(half >> 1));
						}
						else
						{
							_findValue(buffer, (byte)(half - 1), (byte)(half - 1), (byte)(half - 1), (byte)(half >> 1));
						}
					}
				}
			}
			value = found;
		}
		else
		{
			// This means that we fell off the tree, somehow, which would be a static error.
			throw Assert.unreachable();
		}
		return value;
	}

	private static short _updateValue(ShortWriter writer, ShortBuffer buffer, byte x, byte y, byte z, byte half, short newValue)
	{
		// We will return -1 if the this sub-tree has multiple values or the actual value, if it is just one.
		final short value;
		short header = buffer.get();
		if (Encoding.checkShortTag(header))
		{
			// If the value is negative, the value represents the entire level.
			// In this case, there are 3 possibilities:
			// 1 - the values match, so we don't change anything
			// 2 - this is a leaf, so we just return it
			// 3 - we need to split out new sub-trees
			short oldValue = Encoding.clearShortTag(header);
			if (newValue == oldValue)
			{
				// Just encode this into the output.
				writer.putShort(header);
				value = oldValue;
			}
			else if (0 == half)
			{
				// (technically, this is the same as above but split out for clarity)
				writer.putShort(Encoding.setShortTag(newValue));
				value = newValue;
			}
			else
			{
				// Create the sub-trees and return -1.
				writer.putShort((short)0);
				int targetX = (x < half) ? 0 : 1;
				int targetY = (y < half) ? 0 : 1;
				int targetZ = (z < half) ? 0 : 1;
				for (int i = 0; i < 2; ++i)
				{
					for (int j = 0; j < 2; ++j)
					{
						for (int k = 0; k < 2; ++k)
						{
							// Unless this is the relevant sub-tree, just treat it as the header.
							if ((i == targetX) && (j == targetY) && (k == targetZ))
							{
								short[] fake = new short[] { header };
								_updateValue(writer, ShortBuffer.wrap(fake), (byte)(x & ~half), (byte)(y & ~half), (byte)(z & ~half), (byte)(half >> 1), newValue);
							}
							else
							{
								writer.putShort(header);
							}
						}
					}
				}
				value = -1;
			}
		}
		else if (half > 0)
		{
			// Otherwise, the value MUST be 0 and means that we need to dig into the 8 sub-trees.
			Assert.assertTrue(0 == header);
			// In this case, we need to run this for all sub-trees, but then see if the trees should coalesce, meaning we need temporary output buffers.
			int index = 0;
			short[] values = new short[8];
			ShortWriter[] captured = new ShortWriter[8];
			for (int i = 0; i < 2; ++i)
			{
				for (int j = 0; j < 2; ++j)
				{
					for (int k = 0; k < 2; ++k)
					{
						captured[index] = new ShortWriter();
						values[index] = _updateValue(captured[index], buffer, (byte)(x & ~half), (byte)(y & ~half), (byte)(z & ~half), (byte)(half >> 1), newValue);
						index += 1;
					}
				}
			}
			
			// Check to see if we want to write these back as normal or coalesce them.
			boolean isMatched = true;
			for (short check : values)
			{
				isMatched &= (check == newValue);
			}
			if (isMatched)
			{
				// The sub-trees can be coalesced.
				writer.putShort(Encoding.setShortTag(newValue));
				value = newValue;
			}
			else
			{
				// We need to write-back the sub-trees.
				writer.putShort((short)0);
				for (ShortWriter oneSub : captured)
				{
					writer.consume(oneSub);
				}
				value = -1;
			}
		}
		else
		{
			// This means that we fell off the tree, somehow, which would be a static error.
			throw Assert.unreachable();
		}
		return value;
	}


	private short[] _data;

	private OctreeShort(short[] data)
	{
		_data = data;
	}

	@Override
	public <T> T getData(Aspect<T> type, byte x, byte y, byte z)
	{
		// Hash of 32 (the base size) is 16 so we use that as the initial half size.
		short value = _findValue(ShortBuffer.wrap(_data), x, y, z, (byte)16);
		Assert.assertTrue(value >= 0);
		return type.type().cast(Short.valueOf(value));
	}

	@Override
	public <T> void setData(byte x, byte y, byte z, T value)
	{
		short correct = ((Short)value).shortValue();
		ShortWriter writer = new ShortWriter();
		_updateValue(writer, ShortBuffer.wrap(_data), x, y, z, (byte)16, correct);
		_data = writer.getData();
	}

	@Override
	public void serialize(ByteBuffer buffer, IAspectCodec<?> codec)
	{
		buffer.asShortBuffer().put(_data);
	}

	@Override
	public IOctree cloneData()
	{
		return new OctreeShort(_data.clone());
	}


	private static class ShortWriter
	{
		private final ByteArrayOutputStream _builder = new ByteArrayOutputStream();
		
		public void putShort(short value)
		{
			byte[] one = new byte[Short.BYTES];
			ShortBuffer buffer = ByteBuffer.wrap(one).asShortBuffer();
			buffer.put(value);
			try
			{
				_builder.write(one);
			}
			catch (IOException e)
			{
				throw Assert.unexpected(e);
			}
		}
		
		public void consume(ShortWriter oneSub)
		{
			byte[] data = oneSub._builder.toByteArray();
			try
			{
				_builder.write(data);
			}
			catch (IOException e)
			{
				throw Assert.unexpected(e);
			}
		}
		
		public short[] getData()
		{
			byte[] raw = _builder.toByteArray();
			ShortBuffer buffer = ByteBuffer.wrap(raw).asShortBuffer();
			short[] output = new short[raw.length / Short.BYTES];
			buffer.get(output);
			return output;
		}
	}
}
