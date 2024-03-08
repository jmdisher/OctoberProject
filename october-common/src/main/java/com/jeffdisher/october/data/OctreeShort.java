package com.jeffdisher.october.data;

import java.io.PrintStream;
import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.utils.Assert;


/**
 * Stores 15-bit values in a 32x32x32 octree.  All values in the sub-tree must be non-negative (15 bits shorts) and it
 * is worth noting that the implementation stores all multi-byte quantities in big-endian format.  Walking the octree
 * from its stored data format can be viewed as walking a number of "sub-trees", recursively:
 * -At each level of the sub-tree, a byte is read.
 * -If this byte has the value 0xFF, then the sub-tree is to be considered "expanded" and each of its sub-trees must be
 * recursively walked.
 * -Otherwise, the sub-tree is considered "compact" and this byte is interpreted as the high-byte of the short value
 * representing the entire value of the sub-tree.
 * 
 * The order of the sub-trees is a 3-level nested loop:  x is outer-most, y is middle, and z is inner-most.  This means
 * that, in data order, the sub-trees found are:
 * -0, 0, 0
 * -0, 0, 1
 * -0, 1, 0
 * -0, 1, 1
 * -1, 0, 0
 * -1, 0, 1
 * -1, 1, 0
 * -1, 1, 1
 */
public class OctreeShort implements IOctree
{
	public static final byte SUBTREE_HEADER = (byte)0xFF;

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
		// The entire tree is compact so just write the value.
		byte[] data = ByteBuffer.allocate(Short.BYTES).putShort(fillValue).array();
		return new OctreeShort(data);
	}

	public static OctreeShort empty()
	{
		// This is the case used when loading the octree - the data will be allocated and populated later (and cannot be used until then).
		return new OctreeShort(null);
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
			for (int i = 0; (-1 == found) && (i < 2); ++i)
			{
				for (int j = 0; (-1 == found) && (j < 2); ++j)
				{
					for (int k = 0; (-1 == found) && (k < 2); ++k)
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
				// Just write this into the output.
				writer.putShort(newValue);
				value = newValue;
			}
			else if (0 == half)
			{
				// (technically, this is the same as above but split out for clarity)
				writer.putShort(newValue);
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
								byte[] fake = ByteBuffer.allocate(Short.BYTES).putShort(oldValue).array();
								_updateValue(writer, ByteBuffer.wrap(fake), (byte)(x & ~half), (byte)(y & ~half), (byte)(z & ~half), (byte)(half >> 1), newValue);
							}
							else
							{
								// Write each other sub-tree as just the value.
								writer.putShort(oldValue);
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
			ShortWriter captured = new ShortWriter(buffer.capacity());
			for (int i = 0; i < 2; ++i)
			{
				for (int j = 0; j < 2; ++j)
				{
					for (int k = 0; k < 2; ++k)
					{
						if ((i == targetX) && (j == targetY) && (k == targetZ))
						{
							values[index] = _updateValue(captured, buffer, (byte)(x & ~half), (byte)(y & ~half), (byte)(z & ~half), (byte)(half >> 1), newValue);
						}
						else
						{
							values[index] = _advanceAndReportSingleLevel(captured, buffer);
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
				writer.putShort(newValue);
				value = newValue;
			}
			else
			{
				// We need to write-back the sub-trees.
				writer.putSubtreeStart();
				writer.consume(captured);
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
			writer.putShort(oldValue);
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
		byte header = buffer.get();
		if (header >= 0)
		{
			// This is an inline value so grab the next byte and build the short as big-endian.
			byte low = buffer.get();
			value = (short)((Byte.toUnsignedInt(header) << 8)
					| Byte.toUnsignedInt(low)
			);
		}
		else
		{
			// This MUST be the 0xFF special token.
			Assert.assertTrue(SUBTREE_HEADER == header);
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
	public <T, O extends IOctree> T getData(Aspect<T, O> type, BlockAddress address)
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
		ShortWriter writer = new ShortWriter(_data.length);
		_updateValue(writer, ByteBuffer.wrap(_data), address.x(), address.y(), address.z(), (byte)16, correct);
		_data = writer.getData();
	}

	@Override
	public Object serializeResumable(Object lastCallState, ByteBuffer buffer, IAspectCodec<?> codec)
	{
		// NOTE:  For serializing, we just pass an Integer back:  Either the offset to continue the copy or -1 if we still need to write the size.
		
		// The only statefulness we have here is that we first send the 4-byte quantity describing the number of bytes we will be sending.
		int startOffset = (null != lastCallState)
				? (Integer) lastCallState
				: -1
		;
		// We want to verify that we aren't stuck in a loop.
		boolean canFail = (null == lastCallState);
		Integer resumeToken = null;
		if (-1 == startOffset)
		{
			// We still need to write our size so see if that fits.
			if (buffer.remaining() >= Integer.BYTES)
			{
				// We can write this.
				buffer.putInt(_data.length);
				canFail = true;
				startOffset = 0;
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
			// See what can fit in the buffer.
			int spaceInBuffer = buffer.remaining();
			int bytesRemaining = _data.length - startOffset;
			int toCopy = Math.min(spaceInBuffer, bytesRemaining);
			Assert.assertTrue((toCopy > 0) || canFail);
			buffer.put(_data, startOffset, toCopy);
			
			resumeToken = (toCopy < bytesRemaining)
					? (startOffset + toCopy)
					: null
			;
		}
		return resumeToken;
	}

	@Override
	public Object deserializeResumable(Object lastCallState, ByteBuffer buffer, IAspectCodec<?> codec)
	{
		// NOTE:  For deserializing, we just pass an Integer back:  Size is always in the first packet so just the number of byte we still need.
		
		int bytesToCopy;
		if (null == lastCallState)
		{
			// This means that we should be able to read at least the size.
			Assert.assertTrue(buffer.remaining() >= Integer.BYTES);
			Assert.assertTrue(null == _data);
			bytesToCopy = buffer.getInt();
			_data = new byte[bytesToCopy];
		}
		else
		{
			bytesToCopy = (Integer) lastCallState;
		}
		// We want to verify that we aren't stuck in a loop.
		boolean canFail = (null == lastCallState);
		
		// See how many of the remaining bytes are here.
		int bytesInBuffer = buffer.remaining();
		int toCopy = Math.min(bytesInBuffer, bytesToCopy);
		Assert.assertTrue((toCopy > 0) || canFail);
		
		// See our seek point.
		int startOffset = _data.length - bytesToCopy;
		
		buffer.get(_data, startOffset, toCopy);
		
		// Return a description of how many more we expect.
		return (toCopy < bytesToCopy)
				? (bytesToCopy - toCopy)
				: null
		;
	}

	public OctreeShort cloneData()
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
		private final ByteBuffer _builder;
		
		public ShortWriter(int previousLength)
		{
			// For temp space, we allocate the previous length plus the worst-case growth:  An empty tree adding 1 element.
			// An empty tree is 2 bytes and a tree with 1 element is 77 so we add 75.
			_builder = ByteBuffer.allocate(previousLength + 75);
		}
		
		public void putSubtreeStart()
		{
			_builder.put(SUBTREE_HEADER);
		}
		
		public void putShort(short value)
		{
			_builder.putShort(value);
		}
		
		public void consume(ShortWriter oneSub)
		{
			ByteBuffer other = oneSub._builder;
			_builder.put(other.array(), 0, other.position());
		}
		
		public byte[] getData()
		{
			_builder.flip();
			byte[] data = new byte[_builder.remaining()];
			_builder.get(data);
			return data;
		}
	}
}
