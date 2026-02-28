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
			ShortWriter writer = new ShortWriter(Short.BYTES);
			writer.putShort(_inlineCompact);
			byte[] raw = writer.getData();
			for (int i = 0; i < 8; ++i)
			{
				_topLevelTrees[i] = raw;
			}
			_inlineCompact = -1;
			
			int index = _getTopLevelIndex(x, y, z);
			writer = new ShortWriter(_topLevelTrees[index].length);
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
			ShortWriter writer = new ShortWriter(data.length);
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
				_level(ByteBuffer.wrap(data)
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

	private static void _level(ByteBuffer buffer
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
				int newTreeIndex = _getTopLevelIndex(blockAddress.x(), blockAddress.y(), blockAddress.z());
				if (newTreeIndex == treeIndex)
				{
					// We are continuing in this tree so see if it is the current sub-tree.
					byte thisX = blockAddress.x();
					byte thisY = blockAddress.y();
					byte thisZ = blockAddress.z();
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
							// The order of the sub-trees is a 3-level nested loop:  x is outer-most, y is middle, and z is inner-most.
							byte half = (byte)(size >> 1);
							int targetX = ((thisX - baseX) < half) ? 0 : 1;
							int targetY = ((thisY - baseY) < half) ? 0 : 1;
							int targetZ = ((thisZ - baseZ) < half) ? 0 : 1;
							int treesToSkipAtThisLevel = (targetX * 4) + (targetY * 2) + targetZ;
							
							int subTreesToPass = treesToSkipAtThisLevel - treesSkippedInLevel;
							Assert.assertTrue(subTreesToPass >= 0);
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
							treesSkippedInLevel = treesToSkipAtThisLevel;
							
							_level(buffer
								, state
								, treeIndex
								, (byte)(baseX + (thisX & half))
								, (byte)(baseY + (thisY & half))
								, (byte)(baseZ + (thisZ & half))
								, half
							);
							treesSkippedInLevel += 1;
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
						
						// Skip past the rest of the sub-trees at this level since we will be resuming after them.
						int subTreesToPass = 8 - treesSkippedInLevel;
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
				}
				else
				{
					// We can just bail out since we will be resuming on another buffer.
					continueLoop = false;
				}
			}
			else
			{
				// We can just bail out since we are completely done.
				continueLoop = false;
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
			return (this.currentIndex < this.inputArray.length)
				? this.inputArray[this.currentIndex]
				: null
			;
		}
		public void completeAndAdvance(short output)
		{
			this.resultArray[this.currentIndex] = output;
			this.currentIndex += 1;
		}
	}
}
