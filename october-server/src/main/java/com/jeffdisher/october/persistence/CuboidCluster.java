package com.jeffdisher.october.persistence;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.utils.Assert;


/**
 * The cuboid cluster is a single on-disk file containing cuboids.  It contains 64 of them in a 4x4x4 grid.  Note that
 * all cuboids in a cluster have the same data version so any updates made to one must be made to all at the same time.
 * The file format starts with 65 4-byte, big-endian values:
 * -the version int
 * -64 ints describing the size, in bytes, of each cuboid in z-y-x magnitudes (0,0,0,1 is 1, 0,0,1,1 is 5, etc).
 * What follows is each of the cuboids in the same order as the size index.  Note that 0-size cuboids are considered
 * "null" and are not present in the file.
 */
public class CuboidCluster
{
	/**
	 * Each CuboidCluster contains 64 cuboids, in 4*4*4 regions so shift each index by 2 to address it.
	 */
	public static final int SHIFT_ADDRESS_CLUSTER = 2;
	/**
	 * Since we shift the address by 2 bits to find out the cluster, we use those 2 bits to index within it.
	 */
	public static final int MASK_ADDRESS_CLUSTER = 0x3;

	private final File _backingStore;
	/**
	 * The index of raw data is 64 elements since the cluster contains 64 cuboids.
	 */
	private final byte[][] _rawCuboidData;

	private final boolean[] _isReferenced;
	private int _refCount;

	/**
	 * Initializes internal state but doesn't touch the filesystem, yet.
	 * 
	 * @param backingStore The locaction of the file underlying this cluster.
	 */
	public CuboidCluster(File backingStore)
	{
		_backingStore = backingStore;
		_rawCuboidData = new byte[4 * 4 * 4][];
		_isReferenced = new boolean[4 * 4 * 4];
	}

	/**
	 * Loads the internal state from the backing store.
	 * 
	 * @throws IOException There was an error reading the backing store.
	 */
	public void loadFromBackingStore() throws IOException
	{
		byte[] rawData = Files.readAllBytes(_backingStore.toPath());
		ByteBuffer buffer = ByteBuffer.wrap(rawData);
		int version = buffer.getInt();
		int[] sizes = new int[64];
		
		if (StorageVersions.CURRENT != version)
		{
			throw new RuntimeException("UNSUPPORTED ENTITY STORAGE VERSION:  " + version);
		}
		for (int i = 0; i < sizes.length; ++i)
		{
			sizes[i] = buffer.getInt();
		}
		
		for (int i = 0; i < sizes.length; ++i)
		{
			int thisSize = sizes[i];
			if (thisSize > 0)
			{
				byte[] rawCuboid = new byte[thisSize];
				buffer.get(rawCuboid);
				_rawCuboidData[i] = rawCuboid;
			}
		}
	}

	/**
	 * Reads the raw data for cuboid from the internal cache.  This call has the consequence of marking this cuboid as
	 * "referenced", whether it found any data or not (meaning that there must be a write made to this cuboid address
	 * before shutdown or another read attempt).
	 * 
	 * @param address The cuboid address (assumed to be in this cluster).
	 * @return The raw cuboid data or null, if never written.
	 */
	public byte[] readCuboid(CuboidAddress address)
	{
		int index = _getIndexIntoCluster(address);
		byte[] data = _rawCuboidData[index];
		
		// This shouldn't already be referenced.
		Assert.assertTrue(!_isReferenced[index]);
		
		// We are going to set this as referenced so set an empty array as a placeholder until it is written back, later.
		if (null == data)
		{
			_rawCuboidData[index] = new byte[0];
		}
		
		_isReferenced[index] = true;
		_refCount += 1;
		return data;
	}

	/**
	 * Writes new data for the given cuboid address, potentially marking the address as no longer referenced, then
	 * returns whether or not the receiver should still be kept in memory.
	 * 
	 * @param address The cuboid address (assumed to be in this cluster).
	 * @param data The new data to store.
	 * @param keepInMemory If true, this address will continue to be referenced while false will mark it retired.
	 * @return True if the receiver must still be kept in memory (at least one of the data elements is still referenced).
	 * @throws IOException Something went wrong writing back to the backing store file.
	 */
	public boolean writeCuboid(CuboidAddress address, byte[] data, boolean keepInMemory) throws IOException
	{
		int index = _getIndexIntoCluster(address);
		
		// This must not be null since it was read or initialized.
		Assert.assertTrue(null != _rawCuboidData[index]);
		
		// Compare if these changed.
		boolean didChange = !Arrays.equals(_rawCuboidData[index], data);
		
		if (didChange)
		{
			_rawCuboidData[index] = data;
			_writeToBackingStore();
		}
		
		// This must already be referenced.
		Assert.assertTrue(_isReferenced[index]);
		
		if (!keepInMemory)
		{
			// We want to release the reference.
			_isReferenced[index] = false;
			_refCount -= 1;
		}
		
		boolean shouldKeepLoaded = (_refCount > 0);
		return shouldKeepLoaded;
	}

	/**
	 * Used in some tests in order to retire an entry which was referenced by reading, but returned null.  This is not
	 * used in normal runs but is used by tests which do not have a world generator.
	 * 
	 * @param address The cuboid address (assumed to be in this cluster).
	 * @return True if the receiver must still be kept in memory (at least one of the data elements is still referenced).
	 */
	public boolean dropForTesting(CuboidAddress address)
	{
		int index = _getIndexIntoCluster(address);
		
		// This must not be null since it was read or initialized.
		Assert.assertTrue(null != _rawCuboidData[index]);
		Assert.assertTrue(_isReferenced[index]);
		
		_rawCuboidData[index] = null;
		_isReferenced[index] = false;
		_refCount -= 1;
		
		boolean shouldKeepLoaded = (_refCount > 0);
		return shouldKeepLoaded;
	}


	private static int _getIndexIntoCluster(CuboidAddress address)
	{
		int x = address.x() & MASK_ADDRESS_CLUSTER;
		int y = address.y() & MASK_ADDRESS_CLUSTER;
		int z = address.z() & MASK_ADDRESS_CLUSTER;
		
		return (z << (2 * SHIFT_ADDRESS_CLUSTER))
			| (y << SHIFT_ADDRESS_CLUSTER)
			| x
		;
	}

	private void _writeToBackingStore() throws IOException
	{
		// TODO:  We probably want to move this flush decision to a higher-level in the stack if we are often batch writing since that will cause redundant writes.
		int combinedSize = Integer.BYTES + 64 * Integer.BYTES;
		for (int i = 0; i < _rawCuboidData.length; ++i)
		{
			byte[] one = _rawCuboidData[i];
			if (null != one)
			{
				// Note that some of these may be the zero-length placeholders but that is harmless.
				combinedSize += one.length;
			}
		}
		
		byte[] serializedBytes = new byte[combinedSize];
		ByteBuffer buffer = ByteBuffer.wrap(serializedBytes);
		buffer.putInt(StorageVersions.CURRENT);
		for (int i = 0; i < _rawCuboidData.length; ++i)
		{
			byte[] one = _rawCuboidData[i];
			int size;
			if (null != one)
			{
				size = one.length;
			}
			else
			{
				// Nulls are just 0-size since 0-size cuboids don't exist.
				size = 0;
			}
			buffer.putInt(size);
		}
		for (int i = 0; i < _rawCuboidData.length; ++i)
		{
			byte[] one = _rawCuboidData[i];
			if (null != one)
			{
				buffer.put(one);
			}
		}
		
		Files.write(_backingStore.toPath(), serializedBytes, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
	}
}
