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
public class OctreeShort implements IOctree<Short>
{
	public static final byte SUBTREE_HEADER = (byte)0xFF;

	public static OctreeShort create(short fillValue)
	{
		Assert.assertTrue(fillValue >= 0);
		// The entire tree is compact so just write the value.
		return new OctreeShort(fillValue, null);
	}

	public static OctreeShort empty()
	{
		// This is the case used when loading the octree - the data will be allocated and populated later (and cannot be used until then).
		return new OctreeShort((short)-1, null);
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
			
			// We aren't interested in what is in these trees, we just need to step over them so we can compute how many
			// to pass and walk over them.
			int subTreesToPass = (targetX * 4) + (targetY * 2) + targetZ;
			_skipSubTrees(buffer, subTreesToPass);
			
			value = _findValue(buffer, (byte)(x & ~half), (byte)(y & ~half), (byte)(z & ~half), (byte)(half >> 1));
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
			ShortWriter captured = new ShortWriter(buffer.capacity(), 1);
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

	private static int _getTopLevelIndex(byte x, byte y, byte z)
	{
		int index = 0;
		if (x >= 16)
		{
			index += 4;
		}
		if (y >= 16)
		{
			index += 2;
		}
		if (z >= 16)
		{
			index += 1;
		}
		return index;
	}


	// Either the _inlineCompact is set to >= 0 OR the _topLevelTrees is non-null.
	private short _inlineCompact;
	private byte[][] _topLevelTrees;

	private OctreeShort(short inlineCompact, byte[][] topLevelTrees)
	{
		_inlineCompact = inlineCompact;
		_topLevelTrees = topLevelTrees;
	}

	@Override
	public <O extends IOctree<Short>> Short getData(Aspect<Short, O> type, BlockAddress address)
	{
		short value;
		if (null != _topLevelTrees)
		{
			byte x = address.x();
			byte y = address.y();
			byte z = address.z();
			byte[] data = _topLevelTrees[_getTopLevelIndex(x, y, z)];
			// Half of 32 (the base size) is 16.
			byte half = 16;
			value = _findValue(ByteBuffer.wrap(data), (byte)(x & ~half), (byte)(y & ~half), (byte)(z & ~half), (byte)(half >> 1));
		}
		else
		{
			value = _inlineCompact;
		}
		Assert.assertTrue(value >= 0);
		return type.type().cast(Short.valueOf(value));
	}

	@Override
	public void setData(BlockAddress address, Short value)
	{
		short correct = value.shortValue();
		// The value cannot be negative.
		Assert.assertTrue(correct >= 0);
		
		// We need to inline the top-level update concerns, here (duplicated from the actual _updateValue).
		if (_inlineCompact == correct)
		{
			// Degenerate case - do nothing.
		}
		else if (null == _topLevelTrees)
		{
			// Just split out the trees and make the update.
			byte x = address.x();
			byte y = address.y();
			byte z = address.z();
			_topLevelTrees = new byte[8][];
			ShortWriter writer = new ShortWriter(Short.BYTES, 1);
			writer.putShort(_inlineCompact);
			byte[] raw = writer.getData();
			for (int i = 0; i < 8; ++i)
			{
				_topLevelTrees[i] = raw;
			}
			_inlineCompact = -1;
			
			int index = _getTopLevelIndex(x, y, z);
			writer = new ShortWriter(_topLevelTrees[index].length, 1);
			// Half of 32 (the base size) is 16.
			byte half = 16;
			_updateValue(writer, ByteBuffer.wrap(_topLevelTrees[index]), (byte)(x & ~half), (byte)(y & ~half), (byte)(z & ~half), (byte)(half >> 1), correct);
			_topLevelTrees[index] = writer.getData();
		}
		else
		{
			// Do the normal update in the appropriate sub-tree.
			byte x = address.x();
			byte y = address.y();
			byte z = address.z();
			int index = _getTopLevelIndex(x, y, z);
			byte[] data = _topLevelTrees[index];
			ShortWriter writer = new ShortWriter(data.length, 1);
			// Half of 32 (the base size) is 16.
			byte half = 16;
			_updateValue(writer, ByteBuffer.wrap(data), (byte)(x & ~half), (byte)(y & ~half), (byte)(z & ~half), (byte)(half >> 1), correct);
			_topLevelTrees[index] = writer.getData();
			
			// We also need to handle the case where this causes us to coalesce.
			boolean shouldCoalesce = true;;
			for (byte[] subTree : _topLevelTrees)
			{
				if ((Short.BYTES == subTree.length) && (ByteBuffer.wrap(subTree).getShort() == correct))
				{
					// This matches.
				}
				else
				{
					shouldCoalesce = false;
					break;
				}
			}
			if (shouldCoalesce)
			{
				_inlineCompact = correct;
				_topLevelTrees = null;
			}
		}
	}

	@Override
	public void readBatch(Object arrayType, BlockAddress[] addresses)
	{
		short[] outData = (short[]) arrayType;
		if (null != _topLevelTrees)
		{
			// We will assert that the inputs are in sorted order and without duplicates (the algorithm assumes this is true).
			int lastSort = -1;
			for (BlockAddress address : addresses)
			{
				int thisSort = IReadOnlyCuboidData.getBatchSortOrder(address);
				Assert.assertTrue(thisSort > lastSort);
				lastSort = thisSort;
			}
			
			_BatchState state = new _BatchState(addresses, outData);
			while (state.hasWork())
			{
				BlockAddress address = state.currentInput();
				byte x = address.x();
				byte y = address.y();
				byte z = address.z();
				int treeIndex = _getTopLevelIndex(x, y, z);
				
				byte[] data = _topLevelTrees[treeIndex];
				byte half = 16;
				_readBatchInSubtreeLevel(ByteBuffer.wrap(data)
					, state
					, treeIndex
					, (byte)(x & half)
					, (byte)(y & half)
					, (byte)(z & half)
					, half
				);
			}
		}
		else
		{
			for (int i = 0; i < addresses.length; ++i)
			{
				outData[i] = _inlineCompact;
			}
		}
	}

	@Override
	public void writeBatch(BlockAddress[] addresses, Object arrayType)
	{
		short[] valuesToWrite = (short[]) arrayType;
		
		// We will assert that the inputs are in sorted order and without duplicates (the algorithm assumes this is true).
		int lastSort = -1;
		for (BlockAddress address : addresses)
		{
			int thisSort = IReadOnlyCuboidData.getBatchSortOrder(address);
			Assert.assertTrue(thisSort > lastSort);
			lastSort = thisSort;
		}
		// We similarly want to make sure that no values are negative.
		for (short value : valuesToWrite)
		{
			Assert.assertTrue(value >= 0);
		}
		
		_BatchWriteState state = new _BatchWriteState(addresses, valuesToWrite);
		while (state.workRemaining() > 0)
		{
			// It is possible for us to change whether this is inline or has top-level trees so check the current state.
			if (null != _topLevelTrees)
			{
				// Get the location to find the next tree to seek.
				BlockAddress address = state.currentAddress();
				byte x = address.x();
				byte y = address.y();
				byte z = address.z();
				int treeIndex = _getTopLevelIndex(x, y, z);
				byte[] data = _topLevelTrees[treeIndex];
				byte half = 16;
				
				// Note that our recursive write might need to return a value if it is a fully-inline write (since it doesn't know if we need to inline it on this level, too).
				ShortWriter writer = new ShortWriter(data.length, state.workRemaining());
				ByteBuffer buffer = ByteBuffer.wrap(data);
				short oldValue = _loadHeader(buffer);
				short carriedWrite = _writeBatchInSubtreeLevel(writer
					, buffer
					, oldValue
					, state
					, (byte)(x & half)
					, (byte)(y & half)
					, (byte)(z & half)
					, half
				);
				if (-1 != carriedWrite)
				{
					// This is the case where we need to inline the result (the callee doesn't know if it should write this, based on caller).
					writer.putShort(carriedWrite);
				}
				_topLevelTrees[treeIndex] = writer.getData();
			}
			else
			{
				// This is inline so check if it can stay inline or if we need to break it out.
				short toWrite = state.currentWrite();
				if (_inlineCompact == toWrite)
				{
					// This is being preserved so do nothing but advance.
					state.advance();
				}
				else
				{
					// We need to break this out into top-level trees (we won't progress until the next loop iteration, to avoid duplication in code).
					_topLevelTrees = new byte[8][];
					ShortWriter writer = new ShortWriter(Short.BYTES, 1);
					writer.putShort(_inlineCompact);
					byte[] raw = writer.getData();
					for (int i = 0; i < 8; ++i)
					{
						_topLevelTrees[i] = raw;
					}
					_inlineCompact = -1;
				}
			}
		}
		
		// Check if we should inline this (note that we could do this while walking the trees but that would complicate the loop).
		if (null != _topLevelTrees)
		{
			boolean shouldCoalesce = true;
			short value = -1;
			for (byte[] subTree : _topLevelTrees)
			{
				if (Short.BYTES == subTree.length)
				{
					// This is a single-value tree so consider this.
					short thisShort = ByteBuffer.wrap(subTree).getShort();
					if (-1 == value)
					{
						value = thisShort;
					}
					else if (value == thisShort)
					{
						// This is still matching so continue.
					}
					else
					{
						// Mismatch so fail out.
						shouldCoalesce = false;
						break;
					}
				}
				else
				{
					// There is something more complex here so fail out.
					shouldCoalesce = false;
					break;
				}
			}
			if (shouldCoalesce)
			{
				_inlineCompact = value;
				_topLevelTrees = null;
			}
		}
	}

	@Override
	public void walkData(IWalkerCallback<Short> callback, Short valueToSkip)
	{
		short skip = valueToSkip.shortValue();
		if (null != _topLevelTrees)
		{
			// Walk the sub-trees.
			byte size = 16;
			for (int i = 0; i < _topLevelTrees.length; ++i)
			{
				byte x = (byte)((i & 0x4) * 4);
				byte y = (byte)((i & 0x2) * 8);
				byte z = (byte)((i & 0x1) * 16);
				_walkData(ByteBuffer.wrap(_topLevelTrees[i]), x, y, z, size, callback, skip);
			}
		}
		else
		{
			// Just use the inline value.
			if (skip != _inlineCompact)
			{
				byte size = 32;
				callback.visit(BlockAddress.fromInt(0, 0, 0), size, _inlineCompact);
			}
		}
	}

	@Override
	public Object serializeResumable(Object lastCallState, ByteBuffer buffer, IObjectCodec<Short> codec)
	{
		// NOTE:  For serializing, we just pass an Integer back:  Just the offset where we need to resume copying.
		
		int startOffset;
		if (null == lastCallState)
		{
			// This is the first call so we need to write out our header:  either the inline value or all subtree sizes.
			if (null == _topLevelTrees)
			{
				// Just write the inline value with a tagged high bit.
				Assert.assertTrue(buffer.remaining() >= Short.BYTES);
				buffer.putShort((short)(0x8000 | _inlineCompact));
			}
			else
			{
				// Write all the sizes, in order, as shorts (we know that these are less than 32KiB).
				Assert.assertTrue(buffer.remaining() >= (8 * Short.BYTES));
				for (byte[] subtree : _topLevelTrees)
				{
					Assert.assertTrue(subtree.length <= Short.MAX_VALUE);
					buffer.putShort((short) subtree.length);
				}
			}
			startOffset = 0;
		}
		else
		{
			startOffset = (Integer)lastCallState;
		}
		
		Integer resumeToken = null;
		if (null != _topLevelTrees)
		{
			int bytesProcessed = 0;
			for (byte[] subtree : _topLevelTrees)
			{
				// Do any copying or skipping.
				boolean workRemaing = false;
				if (startOffset < subtree.length)
				{
					int spaceInBuffer = buffer.remaining();
					int bytesRemaining = subtree.length - startOffset;
					int toCopy = Math.min(spaceInBuffer, bytesRemaining);
					Assert.assertTrue(toCopy > 0);
					buffer.put(subtree, startOffset, toCopy);
					
					if (startOffset > 0)
					{
						// We have handled the case where the start offset mattered so "consume it".
						bytesProcessed += startOffset;
						startOffset = 0;
					}
					bytesProcessed += toCopy;
					workRemaing = (bytesRemaining > toCopy);
				}
				else
				{
					// This is the next tree.
					startOffset -= subtree.length;
					bytesProcessed += subtree.length;
				}
				
				if (!buffer.hasRemaining() && workRemaing)
				{
					// We have filled this so and there is at least this subtree to do so set the token.
					Assert.assertTrue(0 == startOffset);
					resumeToken = bytesProcessed;
					break;
				}
			}
		}
		return resumeToken;
	}

	@Override
	public Object deserializeResumable(Object lastCallState, DeserializationContext context, IObjectCodec<Short> codec)
	{
		ByteBuffer buffer = context.buffer();
		
		// NOTE:  For deserializing, we just pass an Integer back:  The number of bytes we have already processed.
		
		int startOffset;
		if (null == lastCallState)
		{
			// Make sure that this is uninitialized.
			Assert.assertTrue(-1 == _inlineCompact);
			Assert.assertTrue(null == _topLevelTrees);
			
			// We need to read the header, which is either a tagged short value or 8 short sizes.
			short header = buffer.getShort();
			if (0 != (0x8000 & header))
			{
				// This is an inline value.
				_inlineCompact = (short)(header & ~0x8000);
			}
			else
			{
				// These are the top-level sizes.
				Assert.assertTrue(buffer.remaining() >= (7 * Short.BYTES));
				_topLevelTrees = new byte[8][];
				_topLevelTrees[0] = new byte[header];
				for (int i = 1; i < _topLevelTrees.length; ++i)
				{
					short size = buffer.getShort();
					_topLevelTrees[i] = new byte[size];
				}
			}
			startOffset = 0;
		}
		else
		{
			startOffset = (Integer)lastCallState;
		}
		
		Integer resumeToken = null;
		if (null != _topLevelTrees)
		{
			int bytesProcessed = 0;
			for (byte[] subtree : _topLevelTrees)
			{
				// Do any copying or skipping.
				boolean workRemaing = false;
				if (startOffset < subtree.length)
				{
					int spaceInBuffer = buffer.remaining();
					int bytesRemaining = subtree.length - startOffset;
					int toCopy = Math.min(spaceInBuffer, bytesRemaining);
					Assert.assertTrue(toCopy > 0);
					buffer.get(subtree, startOffset, toCopy);
					
					if (startOffset > 0)
					{
						// We have handled the case where the start offset mattered so "consume it".
						bytesProcessed += startOffset;
						startOffset = 0;
					}
					bytesProcessed += toCopy;
					workRemaing = (bytesRemaining > toCopy);
				}
				else
				{
					// This is the next tree.
					startOffset -= subtree.length;
					bytesProcessed += subtree.length;
				}
				
				if (!buffer.hasRemaining() && workRemaing)
				{
					// We have filled this so and there is at least this subtree to do so set the token.
					Assert.assertTrue(0 == startOffset);
					resumeToken = bytesProcessed;
					break;
				}
			}
		}
		return resumeToken;
	}

	public OctreeShort cloneData()
	{
		byte[][] clone = null;
		if (null != _topLevelTrees)
		{
			clone = new byte[8][];
			for (int i = 0; i < _topLevelTrees.length; ++i)
			{
				clone[i] = _topLevelTrees[i].clone();
			}
		}
		return new OctreeShort(_inlineCompact, clone);
	}

	public void walkTree(PrintStream out)
	{
		if (null == _topLevelTrees)
		{
			System.out.println("Compact: " + _inlineCompact);
		}
		else
		{
			out.println("Expanded:");
			for (byte[] data : _topLevelTrees)
			{
				_walkTree(out, "\t", ByteBuffer.wrap(data));
			}
		}
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

	private void _walkData(ByteBuffer buffer, byte x, byte y, byte z, byte size, IWalkerCallback<Short> callback, short valueToSkip)
	{
		short oldValue = _loadHeader(buffer);
		if (oldValue >= 0)
		{
			// Inline tree.
			if (oldValue != valueToSkip)
			{
				callback.visit(new BlockAddress(x, y, z), size, oldValue);
			}
		}
		else
		{
			// Walk subtrees.
			byte subSize = (byte)(size / 2);
			for (byte i = 0; i < 2; ++i)
			{
				for (byte j = 0; j < 2; ++j)
				{
					for (byte k = 0; k < 2; ++k)
					{
						byte subX = (byte)(x + (i * subSize));
						byte subY = (byte)(y + (j * subSize));
						byte subZ = (byte)(z + (k * subSize));
						_walkData(buffer, subX, subY, subZ, subSize, callback, valueToSkip);
					}
				}
			}
		}
	}

	private static void _readBatchInSubtreeLevel(ByteBuffer buffer
		, _BatchState state
		, int treeIndex
		, byte baseX
		, byte baseY
		, byte baseZ
		, byte size
	)
	{
		short oldValue = _loadHeader(buffer);
		byte edgeX = (byte)(baseX + size);
		byte edgeY = (byte)(baseY + size);
		byte edgeZ = (byte)(baseZ + size);
		int treesSkippedInLevel = 0;
		
		boolean continueLoop = true;
		while (continueLoop)
		{
			if (state.hasWork())
			{
				BlockAddress blockAddress = state.currentInput();
				byte thisX = blockAddress.x();
				byte thisY = blockAddress.y();
				byte thisZ = blockAddress.z();
				
				// First, see if we should continue at this level.
				if ((thisX >= baseX) && (thisX < edgeX) && (thisY >= baseY) && (thisY < edgeY) && (thisZ >= baseZ) && (thisZ < edgeZ))
				{
					if (oldValue >= 0)
					{
						// This is the entire sub-tree so just return it.
						state.completeAndAdvance(oldValue);
						treesSkippedInLevel = 8;
					}
					else if (size > 1)
					{
						// Otherwise, we need to dig into the 8 sub-trees.
						// Due to the way the octree is represented, we must walk all subtrees "before" the one where we are finding the element.
						byte half = (byte)(size >> 1);
						int treesToSkipAtThisLevel = _subTreeIndexAtThisLevel(baseX, baseY, baseZ, thisX, thisY, thisZ, half);
						
						int subTreesToPass = treesToSkipAtThisLevel - treesSkippedInLevel;
						Assert.assertTrue(subTreesToPass >= 0);
						_skipSubTrees(buffer, subTreesToPass);
						
						_readBatchInSubtreeLevel(buffer
							, state
							, treeIndex
							, (byte)(baseX + (thisX & half))
							, (byte)(baseY + (thisY & half))
							, (byte)(baseZ + (thisZ & half))
							, half
						);
						
						// Account for us skipping up to this subtree and then reading the subtree.
						treesSkippedInLevel = treesToSkipAtThisLevel + 1;
					}
					else
					{
						// This means that we fell off the tree, somehow, which would be a static error.
						throw Assert.unreachable();
					}
				}
				else
				{
					// This is in another sub-tree so we are done with this one.
					continueLoop = false;
					
					// See if we are doing to entirely different buffer or if we need to skip to a later point in the current one (returning to another level).
					int newTreeIndex = _getTopLevelIndex(thisX, thisY, thisZ);
					if (newTreeIndex == treeIndex)
					{
						// Skip past the rest of the sub-trees at this level since we will be resuming after them.
						int subTreesToPass = 8 - treesSkippedInLevel;
						_skipSubTrees(buffer, subTreesToPass);
					}
				}
			}
			else
			{
				// We can just bail out since we are completely done.
				continueLoop = false;
			}
		}
	}

	private static void _skipSubTrees(ByteBuffer buffer, int subTreesToPass)
	{
		while (subTreesToPass > 0)
		{
			short oneCheck = _loadHeader(buffer);
			subTreesToPass -= 1;
			if (oneCheck < 0)
			{
				// This is a subtree marker so 8 more reads.
				subTreesToPass += 8;
			}
		}
	}

	private static short _writeBatchInSubtreeLevel(ShortWriter writer
		, ByteBuffer buffer
		, short oldValue
		, _BatchWriteState state
		, byte baseX
		, byte baseY
		, byte baseZ
		, byte size
	)
	{
		// Determine if the next write is in this sub-tree.
		byte edgeX = (byte)(baseX + size);
		byte edgeY = (byte)(baseY + size);
		byte edgeZ = (byte)(baseZ + size);
		
		BlockAddress blockAddress = state.currentAddress();
		byte thisX = blockAddress.x();
		byte thisY = blockAddress.y();
		byte thisZ = blockAddress.z();
		
		short writeAfterReturn;
		if ((thisX >= baseX) && (thisX < edgeX) && (thisY >= baseY) && (thisY < edgeY) && (thisZ >= baseZ) && (thisZ < edgeZ))
		{
			if (1 == size)
			{
				// The leaf case always returns inline.
				writeAfterReturn = state.currentWrite();
				state.advance();
			}
			else
			{
				// This is the more complex case so we handle that elsewhere.
				// NOTE:  _writeBatchInSubtreeNonLeaf will completely process the sub-tree so we can return directly.
				writeAfterReturn = _writeBatchInSubtreeNonLeaf(writer
					, buffer
					, oldValue
					, state
					, baseX
					, baseY
					, baseZ
					, size
				);
			}
		}
		else
		{
			// We can just skip over this sub-tree so re-write it.
			writeAfterReturn = _copyFullSubtree(writer
				, buffer
				, oldValue
				, size
			);
		}
		return writeAfterReturn;
	}

	private static short _writeBatchInSubtreeNonLeaf(ShortWriter writer
		, ByteBuffer buffer
		, short oldValue
		, _BatchWriteState state
		, byte baseX
		, byte baseY
		, byte baseZ
		, byte size
	)
	{
		byte edgeX = (byte)(baseX + size);
		byte edgeY = (byte)(baseY + size);
		byte edgeZ = (byte)(baseZ + size);
		byte half = (byte)(size >> 1);
		
		// Since we handle the case of updating a size-1 inline tree elsewhere, as well as cases where we skip this sub-tree, there are 2 modes remaining:
		// 1) Seeking into an expanded sub-tree to update something within it
		//  -this is a pretty common case
		//  -the only special thing to consider here is if the change to the sub-tree should cause an inlining change at this level
		// 2) Expanding an inline sub-tree so that part of it can be updated
		//  -this is a complicated case as it is possible that we need to re-inline it if all the updates were to change every block in the sub-tree to the same value
		short possibleInlineValue = -1;
		
		// We need to fully walk the tree so just determine the next index.
		int targetIndex;
		{
			// There must be work here.
			Assert.assertTrue(state.workRemaining() > 0);
			BlockAddress blockAddress = state.currentAddress();
			byte thisX = blockAddress.x();
			byte thisY = blockAddress.y();
			byte thisZ = blockAddress.z();
			
			// There must be work in this sub-tree.
			Assert.assertTrue((thisX >= baseX) && (thisX < edgeX) && (thisY >= baseY) && (thisY < edgeY) && (thisZ >= baseZ) && (thisZ < edgeZ));
			targetIndex = _subTreeIndexAtThisLevel(baseX, baseY, baseZ, thisX, thisY, thisZ, half);
		}
		
		// Capture the buffer position in case we need to rewind it to inline the sub-tree.
		int rewindPosition = writer.getPosition();
		
		// Since we are changing something in this sub-tree, we will assume it is split out and worry about the coalesce to inline case, at the end.
		writer.putSubtreeStart();
		
		for (int i = 0; i < 8; ++i)
		{
			short valueWritten;
			if (i < targetIndex)
			{
				// Just skip this sub-tree.
				if (oldValue >= 0)
				{
					// We are expanding this case so just use the value we were given instead of reading.
					writer.putShort(oldValue);
					valueWritten = oldValue;
				}
				else
				{
					// This is similar to a blind copy but we want to see the value we wrote in case we inline, later (and changing that function would imply the caller should write, which is not the case for that helper).
					short treeValue = _loadHeader(buffer);
					short writeValue = _copyFullSubtree(writer
						, buffer
						, treeValue
						, size
					);
					Assert.assertTrue(treeValue == writeValue);
					if (writeValue >= 0)
					{
						writer.putShort(writeValue);
					}
					valueWritten = writeValue;
				}
			}
			else
			{
				// We need to process this sub-tree.
				BlockAddress blockAddress = state.currentAddress();
				byte thisX = blockAddress.x();
				byte thisY = blockAddress.y();
				byte thisZ = blockAddress.z();
				
				// The value we will start with is either the one we were given, if inline, or the one in the buffer, if expanded.
				short startingValue = (oldValue >= 0) ? oldValue : _loadHeader(buffer);
				short valueToWrite = _writeBatchInSubtreeLevel(writer
					, buffer
					, startingValue
					, state
					, (byte)(baseX + (thisX & half))
					, (byte)(baseY + (thisY & half))
					, (byte)(baseZ + (thisZ & half))
					, half
				);
				if (valueToWrite >= 0)
				{
					writer.putShort(valueToWrite);
				}
				valueWritten = valueToWrite;
				
				// We must have completed this item in the sub-tree, so determine the next sub-tree index.
				if (state.workRemaining() > 0)
				{
					BlockAddress nextAddress = state.currentAddress();
					byte nextX = nextAddress.x();
					byte nextY = nextAddress.y();
					byte nextZ = nextAddress.z();
					
					// There must be work in this sub-tree.
					if ((nextX >= baseX) && (nextX < edgeX) && (nextY >= baseY) && (nextY < edgeY) && (nextZ >= baseZ) && (nextZ < edgeZ))
					{
						targetIndex = _subTreeIndexAtThisLevel(baseX, baseY, baseZ, nextX, nextY, nextZ, half);
					}
					else
					{
						// Put the target index after the sub-trees so we will walk to the end.
						targetIndex = 8;
					}
				}
				else
				{
					// Put the target index after the sub-trees so we will walk to the end.
					targetIndex = 8;
				}
			}
			
			// Check out our possible inline case.
			if (0 == i)
			{
				// This is either the inline value or -1.
				possibleInlineValue = valueWritten;
			}
			else
			{
				// Make sure that this matches.
				if (possibleInlineValue != valueWritten)
				{
					possibleInlineValue = -1;
				}
			}
		}
		
		// See if we need to rewind and apply our inline case.
		if (possibleInlineValue >= 0)
		{
			// Rewind and return this value so that the caller can decide whether or not to write it.
			writer.setPosition(rewindPosition);
		}
		return possibleInlineValue;
	}

	private static short _copyFullSubtree(ShortWriter writer
		, ByteBuffer buffer
		, short oldValue
		, byte size
	)
	{
		// Make sure that this call is valid.
		Assert.assertTrue(size > 0);
		
		short writeAfterReturn;
		if (oldValue >= 0)
		{
			// Note that even the 1-size case will show up as a non-negative value.
			writeAfterReturn = oldValue;
		}
		else
		{
			// This is an expanded tree so walk all 8 sub-trees.
			byte half = (byte)(size >> 1);
			writer.putSubtreeStart();
			for (int i = 0; i < 8; ++i)
			{
				_blindCopyOneTree(writer, buffer, half);
			}
			
			// We aren't changing the tree so it clearly isn't becoming inline.
			writeAfterReturn = -1;
		}
		return writeAfterReturn;
	}

	private static void _blindCopyOneTree(ShortWriter writer, ByteBuffer buffer, byte size)
	{
		short treeValue = _loadHeader(buffer);
		short writeValue = _copyFullSubtree(writer
			, buffer
			, treeValue
			, size
		);
		
		// We don't expect any changes.
		Assert.assertTrue(treeValue == writeValue);
		if (writeValue >= 0)
		{
			writer.putShort(writeValue);
		}
	}

	private static int _subTreeIndexAtThisLevel(byte baseX, byte baseY, byte baseZ, byte thisX, byte thisY, byte thisZ, byte half)
	{
		// The order of the sub-trees is a 3-level nested loop:  x is outer-most, y is middle, and z is inner-most.
		int targetX = ((thisX - baseX) < half) ? 0 : 1;
		int targetY = ((thisY - baseY) < half) ? 0 : 1;
		int targetZ = ((thisZ - baseZ) < half) ? 0 : 1;
		int treesToSkipAtThisLevel = (targetX * 4) + (targetY * 2) + targetZ;
		return treesToSkipAtThisLevel;
	}


	private static class ShortWriter
	{
		private final ByteBuffer _builder;
		
		public ShortWriter(int previousLength, int possibleWrites)
		{
			// For temp space, we allocate the previous length plus the worst-case growth:  An empty tree adding 1 element.
			// An empty tree is 2 bytes and a tree with 1 element is 77 so we add 75.
			_builder = ByteBuffer.allocate(previousLength + (75 * possibleWrites));
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
		
		public int getPosition()
		{
			return _builder.position();
		}
		
		public void setPosition(int position)
		{
			_builder.position(position);
		}
	}

	private static class _BatchState
	{
		public BlockAddress[] inputArray;
		public short[] resultArray;
		public int currentIndex;
		public _BatchState(BlockAddress[] inputArray, short[] resultArray)
		{
			this.inputArray = inputArray;
			this.resultArray = resultArray;
			this.currentIndex = 0;
		}
		public boolean hasWork()
		{
			return this.currentIndex < this.inputArray.length;
		}
		public BlockAddress currentInput()
		{
			// NOTE:  This implementation assumes it is only called if hasWork() is true.
			return this.inputArray[this.currentIndex];
		}
		public void completeAndAdvance(short output)
		{
			this.resultArray[this.currentIndex] = output;
			this.currentIndex += 1;
		}
	}

	private static class _BatchWriteState
	{
		public BlockAddress[] addressArray;
		public short[] writeArray;
		public int currentIndex;
		public _BatchWriteState(BlockAddress[] addressArray, short[] writeArray)
		{
			this.addressArray = addressArray;
			this.writeArray = writeArray;
			this.currentIndex = 0;
		}
		public int workRemaining()
		{
			return this.addressArray.length - this.currentIndex;
		}
		public BlockAddress currentAddress()
		{
			// NOTE:  This implementation assumes it is only called if hasWork() is true.
			return this.addressArray[this.currentIndex];
		}
		public short currentWrite()
		{
			// NOTE:  This implementation assumes it is only called if hasWork() is true.
			return this.writeArray[this.currentIndex];
		}
		public void advance()
		{
			this.currentIndex += 1;
		}
	}
}
