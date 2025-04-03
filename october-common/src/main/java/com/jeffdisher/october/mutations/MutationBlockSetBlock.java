package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.utils.Assert;


/**
 * An operation which over-writes part of the state of a single block.  This may be the entire block state, a single
 * aspect, or part of an aspect.
 * Note that these operations are considered idempotent and should blindly write data, not basing that on any existing
 * state.
 */
public class MutationBlockSetBlock
{
	/**
	 * Decodes an instance from the given buffer.
	 * 
	 * @param buffer The buffer to read.
	 * @return The decoded instance.
	 */
	public static MutationBlockSetBlock deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		byte[] rawData = new byte[buffer.remaining()];
		buffer.get(rawData);
		return new MutationBlockSetBlock(location, rawData);
	}

	/**
	 * Creates the set block mutation from the data held within a mutable proxy.
	 * Note that scratchBuffer is provided to avoid allocating a large buffer for every one of these, since they are
	 * common in the inner loop.
	 * 
	 * @param scratchBuffer A reusable scratch buffer which should be large enough to contain the entire serialized data
	 * from the proxy.
	 * @param proxy The proxy to extract.
	 * @return The set block mutation describing the final state of the proxy.
	 */
	public static MutationBlockSetBlock extractFromProxy(ByteBuffer scratchBuffer, MutableBlockProxy proxy)
	{
		scratchBuffer.clear();
		proxy.serializeToBuffer(scratchBuffer);
		scratchBuffer.flip();
		byte[] rawData = new byte[scratchBuffer.remaining()];
		scratchBuffer.get(rawData);
		return new MutationBlockSetBlock(proxy.absoluteLocation, rawData);
	}

	/**
	 * Creates a new instance by merging the data from top onto bottom (meaning the union of data between them, choosing
	 * top in the case where there is a conflict).
	 * 
	 * @param bottom The instance to merge (aspect data will be shadowed by corresponding data in top).
	 * @param top The instance to merge on top of bottom (will override data from bottom when present in both).
	 * @return The instance with the merged data.
	 */
	public static MutationBlockSetBlock merge(MutationBlockSetBlock bottom, MutationBlockSetBlock top)
	{
		// We assume that these already match.
		Assert.assertTrue(bottom._location.equals(top._location));
		
		ByteBuffer scratch = ByteBuffer.allocate(bottom._rawData.length + top._rawData.length);
		ByteBuffer readBottom = ByteBuffer.wrap(bottom._rawData);
		ByteBuffer readTop = ByteBuffer.wrap(top._rawData);
		
		// We will read both, copying each index to scratch, favouring top over bottom when conflicting (note that the indices are in sorted order).
		byte bottomIndex = readBottom.get();
		byte topIndex = readTop.get();
		while ((-1  != bottomIndex) || (-1 != topIndex))
		{
			if (bottomIndex == topIndex)
			{
				// This is a conflict so take the top.
				_copyAspect(scratch, readTop, topIndex);
				_skipAspect(readBottom, bottomIndex);
				bottomIndex = _readIndexOrNegOne(readBottom);
				topIndex = _readIndexOrNegOne(readTop);
			}
			else if (-1 == bottomIndex)
			{
				// Copy the top.
				_copyAspect(scratch, readTop, topIndex);
				topIndex = _readIndexOrNegOne(readTop);
			}
			else if (-1 == topIndex)
			{
				// Copy the bottom.
				_copyAspect(scratch, readBottom, bottomIndex);
				bottomIndex = _readIndexOrNegOne(readBottom);
			}
			else if (bottomIndex < topIndex)
			{
				// Copy the bottom.
				_copyAspect(scratch, readBottom, bottomIndex);
				bottomIndex = _readIndexOrNegOne(readBottom);
			}
			else
			{
				// Copy the top.
				_copyAspect(scratch, readTop, topIndex);
				topIndex = _readIndexOrNegOne(readTop);
			}
		}
		scratch.flip();
		byte[] data = new byte[scratch.remaining()];
		scratch.get(data);
		return new MutationBlockSetBlock(bottom._location, data);
	}


	private final AbsoluteLocation _location;
	private final byte[] _rawData;

	/**
	 * Creates a new instance from raw aspect data.
	 * 
	 * @param location The location where the block update should be applied.
	 * @param rawData The raw serialized aspect data which should be written over a target cuboid.
	 */
	public MutationBlockSetBlock(AbsoluteLocation location, byte[] rawData)
	{
		_location = location;
		_rawData = rawData;
	}

	/**
	 * @return The absolute coordinates of the block to which the mutation applies.
	 */
	public AbsoluteLocation getAbsoluteLocation()
	{
		return _location;
	}

	/**
	 * Applies the change to the given target.
	 * 
	 * @param target The cuboid to modify by writing the receiver's changes.
	 */
	@SuppressWarnings("unchecked")
	public void applyState(CuboidData target)
	{
		ByteBuffer buffer = ByteBuffer.wrap(_rawData);
		BlockAddress location = _location.getBlockAddress();
		// We only store the data which actually changed so loop until the buffer segment is empty, checking for indices (byte).
		while (buffer.hasRemaining())
		{
			byte i = buffer.get();
			Assert.assertTrue(i >= 0);
			Assert.assertTrue(i < AspectRegistry.ALL_ASPECTS.length);
			
			Aspect<?, ?> type = AspectRegistry.ALL_ASPECTS[i];
			if (Short.class == type.type())
			{
				short value = buffer.getShort();
				target.setData15((Aspect<Short, ?>) type, location, value);
			}
			else if (Byte.class == type.type())
			{
				byte value = buffer.get();
				target.setData7((Aspect<Byte, ?>) type, location, value);
			}
			else
			{
				_readAndStore(target, location, type, buffer);
			}
		}
	}

	/**
	 * Called during serialization to serialize any internal instance variables of the state update to the given buffer.
	 * 
	 * @param buffer The buffer where the state update should be written.
	 */
	public void serializeToBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writeAbsoluteLocation(buffer, _location);
		buffer.put(_rawData);
	}

	/**
	 * Compares the raw data directly.
	 * 
	 * @param other Another instance for comparison.
	 * @return True if the data in other and the receiver matches directly.
	 */
	public boolean doesDataMatch(MutationBlockSetBlock other)
	{
		return Arrays.equals(_rawData, other._rawData);
	}

	/**
	 * Determines the set of aspects changed by applying the receiver on top of baseline.  This means that data not
	 * specified in the receiver is not counted, nor is aspect data which is equivalent in both instances.
	 * 
	 * @param baseline The instance to compare the receiver to.
	 * @return The set of aspects which change in the receiver when compared to baseline.
	 */
	public Set<Aspect<?, ?>> getChangedAspectsAfter(MutationBlockSetBlock baseline)
	{
		Set<Aspect<?, ?>> aspectsChanged = new HashSet<>();
		
		// We assume that these already match.
		Assert.assertTrue(_location.equals(baseline._location));
		
		ByteBuffer readThis = ByteBuffer.wrap(_rawData);
		ByteBuffer readBaseline = ByteBuffer.wrap(baseline._rawData);
		
		// Read each, reporting any in this which are missing in baseline (note that the indices are in sorted order).
		byte thisIndex = readThis.get();
		byte baselineIndex = _readIndexOrNegOne(readBaseline);
		while (-1 != thisIndex)
		{
			if (thisIndex == baselineIndex)
			{
				// Present in both, advance both.
				if (!_doAspectsMatch(readThis, readBaseline, thisIndex))
				{
					Aspect<?, ?> type = AspectRegistry.ALL_ASPECTS[thisIndex];
					aspectsChanged.add(type);
				}
				thisIndex = _readIndexOrNegOne(readThis);
				baselineIndex = _readIndexOrNegOne(readBaseline);
			}
			else if ((-1 != baselineIndex) && (baselineIndex < thisIndex))
			{
				// The baseline has something we don't - just skip over it.
				_skipAspect(readBaseline, baselineIndex);
				baselineIndex = _readIndexOrNegOne(readBaseline);
			}
			else
			{
				// This is something we have that isn't in the baseline.  Add it to the set and skip over it.
				Aspect<?, ?> type = AspectRegistry.ALL_ASPECTS[thisIndex];
				aspectsChanged.add(type);
				_skipAspect(readThis, thisIndex);
				thisIndex = _readIndexOrNegOne(readThis);
			}
		}
		return aspectsChanged;
	}

	/**
	 * Determines the set of aspects which would be changed by applying the receiver to the given cuboid.  This means it
	 * will read the cuboid data for any aspects it wishes to change, but does not write to it.
	 * 
	 * @param cuboid The read-only cuboid to compare against.
	 * @return The set of aspects which the receiver would change if it were written-back to cuboid.
	 */
	@SuppressWarnings("unchecked")
	public Set<Aspect<?, ?>> changedAspectsVersusCuboid(IReadOnlyCuboidData cuboid)
	{
		Set<Aspect<?, ?>> aspectsChanged = new HashSet<>();
		ByteBuffer buffer = ByteBuffer.wrap(_rawData);
		BlockAddress location = _location.getBlockAddress();
		// We only store the data which actually changed so loop until the buffer segment is empty, checking for indices (byte).
		while (buffer.hasRemaining())
		{
			byte i = buffer.get();
			Assert.assertTrue(i >= 0);
			Assert.assertTrue(i < AspectRegistry.ALL_ASPECTS.length);
			
			Aspect<?, ?> type = AspectRegistry.ALL_ASPECTS[i];
			if (Short.class == type.type())
			{
				short value = buffer.getShort();
				short existing = cuboid.getData15((Aspect<Short, ?>) type, location);
				if (value != existing)
				{
					aspectsChanged.add(type);
				}
			}
			else if (Byte.class == type.type())
			{
				byte value = buffer.get();
				byte existing = cuboid.getData7((Aspect<Byte, ?>) type, location);
				if (value != existing)
				{
					aspectsChanged.add(type);
				}
			}
			else
			{
				Object value = _readSpecial(type, buffer);
				Object existing = cuboid.getDataSpecial(type, location);
				boolean isEqual = (value == existing) || ((null != value) && value.equals(existing));
				if (!isEqual)
				{
					aspectsChanged.add(type);
				}
			}
		}
		return aspectsChanged;
	}


	private static <T> void _readAndStore(CuboidData target, BlockAddress location, Aspect<T, ?> type, ByteBuffer buffer)
	{
		T value = _readSpecial(type, buffer);
		target.setDataSpecial((Aspect<T, ?>) type, location, value);
	}

	private static <T> T _readSpecial(Aspect<T, ?> type, ByteBuffer buffer)
	{
		return type.codec().loadData(buffer);
	}

	private static byte _readIndexOrNegOne(ByteBuffer readThis)
	{
		return readThis.hasRemaining() ? readThis.get() : -1;
	}

	private static void _copyAspect(ByteBuffer writer, ByteBuffer reader, byte index)
	{
		writer.put(index);
		
		Aspect<?, ?> type = AspectRegistry.ALL_ASPECTS[index];
		if (Short.class == type.type())
		{
			short value = reader.getShort();
			writer.putShort(value);
		}
		else if (Byte.class == type.type())
		{
			byte value = reader.get();
			writer.put(value);
		}
		else
		{
			Object value = _readSpecial((Aspect<?, ?>) type, reader);
			type.codec().storeData(writer, value);
		}
	}

	private static void _skipAspect(ByteBuffer reader, byte index)
	{
		Aspect<?, ?> type = AspectRegistry.ALL_ASPECTS[index];
		if (Short.class == type.type())
		{
			reader.getShort();
		}
		else if (Byte.class == type.type())
		{
			reader.get();
		}
		else
		{
			_readSpecial((Aspect<?, ?>) type, reader);
		}
	}

	private static boolean _doAspectsMatch(ByteBuffer one, ByteBuffer two, byte index)
	{
		boolean doMatch;
		Aspect<?, ?> type = AspectRegistry.ALL_ASPECTS[index];
		if (Short.class == type.type())
		{
			short first = one.getShort();
			short second = two.getShort();
			doMatch = (first == second);
		}
		else if (Byte.class == type.type())
		{
			byte first = one.get();
			byte second = two.get();
			doMatch = (first == second);
		}
		else
		{
			Object first = _readSpecial((Aspect<?, ?>) type, one);
			Object second = _readSpecial((Aspect<?, ?>) type, two);
			doMatch = (first == second) || ((null != first) && first.equals(second));
		}
		return doMatch;
	}
}
