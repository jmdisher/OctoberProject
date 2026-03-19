package com.jeffdisher.october.persistence;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.utils.Assert;

/**
 * The management of cuboids on disk is rather complicated (it was originally a flat directory of small files but that
 * introduced scalability and performance concerns so it is now this mechanism).
 * Cuboids are now stored as a directory containing directories, containing files of clusters of cuboids.  This means
 * that the path management requires some logic but the management of memory for the backing-store of the clusters is
 * also a big point of concern (note that this could use lots of memory).
 * Design points:
 * -CuboidAddress is split into CuboidDirectory, CuboidCluster, and CuboidIndex (in each coordinate):
 * -- CuboidDirectory = CuboidAddress >> (3 + 2)
 * -- CuboidFile = (CuboidAddress >> 3) % 8
 * -- CuboidIndex = (CuboidAddress % 4)
 * This means that the top-level directory has unbounded size but is generally small since each lower-level directory
 * contains 512 clusters (8 * 8 * 8) and each cluster contains 64 cuboids (4 * 4 * 4), hence each in-memory cluster is
 * 64 times the size of a single cuboid.
 * The nice element of symmetry here is that all the cuboids in a single CuboidDirectory are 32 * 32 * 32, just like the
 * number of blocks inside a cuboid (hence, making it a sort of aligned "super-cuboid").
 */
public class CuboidClusterManager
{
	/**
	 * Each cuboid directory contains 512 cuboid clusters, in 8*8*8 regions so shift each index by 3 to address it.
	 */
	public static final int SHIFT_ADDRESS_DIRECTORY = 3;
	public static final int MASK_ADDRESS_DIRECTORY = 0x7;

	private final File _topLevelDirectory;
	private final Map<_CuboidFile, CuboidCluster> _clusters;

	/**
	 * Creates the new manager, backing all cuboid cluster and directory storage in the given topLevelDirectory.
	 * 
	 * @param topLevelDirectory The directory which will be used to contain all cuboid directories.
	 */
	public CuboidClusterManager(File topLevelDirectory)
	{
		_topLevelDirectory = topLevelDirectory;
		_clusters = new HashMap<>();
	}

	/**
	 * Reads the raw data for cuboid from the internal cache, populating the cache if needed.  This call has the
	 * consequence of marking this cuboid as "referenced", whether it found any data or not (meaning that there must be
	 * a write made to this cuboid address before shutdown or another read attempt).
	 * 
	 * @param address The cuboid address.
	 * @return The raw cuboid data or null, if never written.
	 * @throws IOException There was an error populating the internal cache from external data store.
	 */
	public byte[] readCuboid(CuboidAddress address) throws IOException
	{
		_CuboidFile file = _CuboidFile.fromAddress(address);
		CuboidCluster cluster = _clusters.get(file);
		
		// Note that the reading path is how we load something in so just do that if null.
		if (null == cluster)
		{
			File cuboidDirectory = _getCuboidDirectory(address);
			if (!cuboidDirectory.exists())
			{
				boolean didCreate = cuboidDirectory.mkdir();
				// TODO:  Determine a reasonable failure mode here.
				Assert.assertTrue(didCreate);
			}
			
			File clusterFile = _getClusterFile(cuboidDirectory, address);
			cluster = new CuboidCluster(clusterFile);
			if (clusterFile.exists())
			{
				Assert.assertTrue(clusterFile.isFile());
				cluster.loadFromBackingStore();
			}
			_clusters.put(file, cluster);
		}
		
		// Read from storage, - might still be null if this wasn't generated.
		return cluster.readCuboid(address);
	}

	/**
	 * Writes new data for the given cuboid address, potentially marking the address as no longer referenced, and
	 * writes-back this change to storage.
	 * 
	 * @param address The cuboid address.
	 * @param data The new data to store.
	 * @param keepInMemory If true, this address will continue to be referenced while false will mark it retired.
	 * @throws IOException Something went wrong writing back to the backing store file.
	 */
	public void writeCuboid(CuboidAddress address, byte[] data, boolean keepInMemory) throws IOException
	{
		// In this case, we know that the cluster must be loaded since we either read or initialized the value.
		_CuboidFile file = _CuboidFile.fromAddress(address);
		CuboidCluster cluster = _clusters.get(file);
		Assert.assertTrue(null != cluster);
		
		boolean shouldKeepLoaded = cluster.writeCuboid(address, data, keepInMemory);
		if (!shouldKeepLoaded)
		{
			// We can only unload something if we were told not to keep it in memory.
			Assert.assertTrue(!keepInMemory);
			
			_clusters.remove(file);
		}
	}

	/**
	 * Used in some tests in order to retire an entry which was referenced by reading, but returned null.  This is not
	 * used in normal runs but is used by tests which do not have a world generator.
	 * 
	 * @param address The cuboid address.
	 */
	public void dropForTesting(CuboidAddress address)
	{
		_CuboidFile file = _CuboidFile.fromAddress(address);
		CuboidCluster cluster = _clusters.get(file);
		Assert.assertTrue(null != cluster);
		
		boolean shouldKeepLoaded = cluster.dropForTesting(address);
		if (!shouldKeepLoaded)
		{
			_clusters.remove(file);
		}
	}

	/**
	 * Verifies that there are no leaked references to any clusters known to the system.
	 */
	public void shutdown()
	{
		// Here, we just make sure that we have nothing left in memory (these should have been retired).
		Assert.assertTrue(_clusters.isEmpty());
	}


	private File _getCuboidDirectory(CuboidAddress address)
	{
		int x = address.x() >> (SHIFT_ADDRESS_DIRECTORY + CuboidCluster.SHIFT_ADDRESS_CLUSTER);
		int y = address.y() >> (SHIFT_ADDRESS_DIRECTORY + CuboidCluster.SHIFT_ADDRESS_CLUSTER);
		int z = address.z() >> (SHIFT_ADDRESS_DIRECTORY + CuboidCluster.SHIFT_ADDRESS_CLUSTER);
		
		String directoryName = String.format("region_%d_%d_%d.cd8", x, y, z);
		return new File(_topLevelDirectory, directoryName);
	}

	private File _getClusterFile(File cuboidDirectory, CuboidAddress address)
	{
		int x = (address.x() >> CuboidCluster.SHIFT_ADDRESS_CLUSTER) & MASK_ADDRESS_DIRECTORY;
		int y = (address.y() >> CuboidCluster.SHIFT_ADDRESS_CLUSTER) & MASK_ADDRESS_DIRECTORY;
		int z = (address.z() >> CuboidCluster.SHIFT_ADDRESS_CLUSTER) & MASK_ADDRESS_DIRECTORY;
		
		String fileName = String.format("cluster_%d_%d_%d.c4", x, y, z);
		return new File(cuboidDirectory, fileName);
	}


	private static record _CuboidFile(short x, short y, short z)
	{
		public static _CuboidFile fromAddress(CuboidAddress address)
		{
			return new _CuboidFile((short)(address.x() >> CuboidCluster.SHIFT_ADDRESS_CLUSTER)
				, (short)(address.y() >> CuboidCluster.SHIFT_ADDRESS_CLUSTER)
				, (short)(address.z() >> CuboidCluster.SHIFT_ADDRESS_CLUSTER)
			);
		}
	}
}
