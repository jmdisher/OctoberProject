package com.jeffdisher.october.data;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.utils.Assert;
import com.jeffdisher.october.utils.Encoding;


public class OctreeShort implements IOctree
{
	public static OctreeShort load(ByteBuffer raw)
	{
		// We want to parse forward until we find the end of the cuboid, then reverse and copy the bytes out.
		int start = raw.position();
		// We just want to read to the end of the octree, to see how big it is, so just say we are looking for the last element
		short verify = _findValue(raw, (byte)31, (byte)31, (byte)31, (byte)16);
		Assert.assertTrue(verify >= 0);
		int end = raw.position();
		byte[] data = new byte[end - start];
		raw.position(start);
		raw.get(data);
		return new OctreeShort(data);
	}

	public static OctreeShort create(short fillValue)
	{
		Assert.assertTrue(fillValue >= 0);
		byte[] data = ByteBuffer.allocate(Short.BYTES).putShort(Encoding.setShortTag(fillValue)).array();
		return new OctreeShort(data);
	}


	private static short _findValue(ByteBuffer buffer, byte x, byte y, byte z, byte half)
	{
		final short value;
		short oldValue = _loadHeader(buffer);
		if (oldValue >= 0)
		{
			// This is the entire sub-tree so just return it.
			value = oldValue;
		}
		else if (half > 0)
		{
			// Otherwise, we need to dig into the 8 sub-trees.
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

	private static short _updateValue(ShortWriter writer, ByteBuffer buffer, byte x, byte y, byte z, byte half, short newValue)
	{
		// We will return -1 if the this sub-tree has multiple values or the actual value, if it is just one.
		final short value;
		short oldValue = _loadHeader(buffer);
		if (oldValue >= 0)
		{
			// This is a single value for the subtree.
			// In this case, there are 3 possibilities:
			// 1 - the values match, so we don't change anything
			// 2 - this is a leaf, so we just return it
			// 3 - we need to split out new sub-trees
			if (newValue == oldValue)
			{
				// Just encode this into the output.
				writer.putShort(Encoding.setShortTag(newValue));
				value = newValue;
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
				writer.putSubtreeStart();
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
								byte[] fake = ByteBuffer.allocate(Short.BYTES).putShort(Encoding.setShortTag(oldValue)).array();
								_updateValue(writer, ByteBuffer.wrap(fake), (byte)(x & ~half), (byte)(y & ~half), (byte)(z & ~half), (byte)(half >> 1), newValue);
							}
							else
							{
								writer.putShort(Encoding.setShortTag(oldValue));
							}
						}
					}
				}
				value = -1;
			}
		}
		else if (half > 0)
		{
			// Otherwise, we need to dig into the 8 sub-trees.
			// In this case, we need to walk all the sub-trees, but then coalesce them if they now have all the same values.
			int targetX = (x < half) ? 0 : 1;
			int targetY = (y < half) ? 0 : 1;
			int targetZ = (z < half) ? 0 : 1;
			
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
						if ((i == targetX) && (j == targetY) && (k == targetZ))
						{
							values[index] = _updateValue(captured[index], buffer, (byte)(x & ~half), (byte)(y & ~half), (byte)(z & ~half), (byte)(half >> 1), newValue);
						}
						else
						{
							values[index] = _advanceAndReportSingleLevel(captured[index], buffer);
						}
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
				writer.putSubtreeStart();
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

	// Walks the current sub-tree, advancing through the given buffer, and returns the value if the tree is only a
	// single level.  Otherwise, returns -1.
	private static short _advanceAndReportSingleLevel(ShortWriter writer, ByteBuffer buffer)
	{
		final short value;
		short oldValue = _loadHeader(buffer);
		if (oldValue >= 0)
		{
			// This is the entire subtree so copy it over and return it.
			writer.putShort(Encoding.setShortTag(oldValue));
			value = oldValue;
		}
		else
		{
			// Otherwise, we need to walk the 8 sub-trees.
			writer.putSubtreeStart();
			for (int i = 0; i < 2; ++i)
			{
				for (int j = 0; j < 2; ++j)
				{
					for (int k = 0; k < 2; ++k)
					{
						_advanceAndReportSingleLevel(writer, buffer);
					}
				}
			}
			// This wasn't a single level, so return -1
			value = -1;
		}
		return value;
	}

	// Returns -1 if this is a sub-tree marker or the value if it is an inline value.
	private static short _loadHeader(ByteBuffer buffer)
	{
		final short value;
		short header = buffer.getShort();
		if (Encoding.checkShortTag(header))
		{
			value = Encoding.clearShortTag(header);
		}
		else
		{
			value = (short)-1;
		}
		return value;
	}


	private byte[] _data;

	private OctreeShort(byte[] data)
	{
		_data = data;
	}

	@Override
	public <T> T getData(Aspect<T> type, BlockAddress address)
	{
		// Hash of 32 (the base size) is 16 so we use that as the initial half size.
		short value = _findValue(ByteBuffer.wrap(_data), address.x(), address.y(), address.z(), (byte)16);
		Assert.assertTrue(value >= 0);
		return type.type().cast(Short.valueOf(value));
	}

	@Override
	public <T> void setData(BlockAddress address, T value)
	{
		short correct = ((Short)value).shortValue();
		// The value cannot be negative.
		Assert.assertTrue(correct >= 0);
		ShortWriter writer = new ShortWriter();
		_updateValue(writer, ByteBuffer.wrap(_data), address.x(), address.y(), address.z(), (byte)16, correct);
		_data = writer.getData();
	}

	@Override
	public void serialize(ByteBuffer buffer, IAspectCodec<?> codec)
	{
		buffer.put(_data);
	}

	@Override
	public IOctree cloneData()
	{
		return new OctreeShort(_data.clone());
	}

	public void walkTree(PrintStream out)
	{
		_walkTree(out, "", ByteBuffer.wrap(_data));
		
	}

	public byte[] copyRawData()
	{
		return _data.clone();
	}


	private void _walkTree(PrintStream out, String indent, ByteBuffer buffer)
	{
		short oldValue = _loadHeader(buffer);
		if (oldValue >= 0)
		{
			// This is the tree value.
			short value = oldValue;
			out.println(indent + value);
		}
		else
		{
			// Walk subtrees.
			for (int i = 0; i < 2; ++i)
			{
				for (int j = 0; j < 2; ++j)
				{
					for (int k = 0; k < 2; ++k)
					{
						out.println(indent + "( subtree " + i + ", " + j + ", " + k);
						_walkTree(out, indent + "\t", buffer);
						out.println(indent + ") subtree " + i + ", " + j + ", " + k);
					}
				}
			}
		}
	}


	private static class ShortWriter
	{
		private final ByteArrayOutputStream _builder = new ByteArrayOutputStream();
		
		public void putSubtreeStart()
		{
			// The subtree start token is just a 0.
			byte[] one = ByteBuffer.allocate(Short.BYTES).putShort((short)0).array();
			try
			{
				_builder.write(one);
			}
			catch (IOException e)
			{
				throw Assert.unexpected(e);
			}
		}
		
		public void putShort(short value)
		{
			byte[] one = ByteBuffer.allocate(Short.BYTES).putShort(value).array();
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
		
		public byte[] getData()
		{
			return _builder.toByteArray();
		}
	}
}
