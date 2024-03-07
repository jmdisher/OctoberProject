package com.jeffdisher.october.persistence;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import com.jeffdisher.october.aspects.InventoryAspect;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.utils.Assert;
import com.jeffdisher.october.utils.MessageQueue;


/**
 * Handles loading or generating cuboids and entities.  This is done asynchronously but results are exposed as
 * call-return in order to avoid cross-thread interaction details becoming part of the interface.
 */
public class ResourceLoader
{
	public static final int SERIALIZATION_BUFFER_SIZE_BYTES = 1024 * 1024;

	// Defaults for entity creation.
	public static final EntityVolume ENTITY_DEFAULT_VOLUME = new EntityVolume(1.8f, 0.5f);
	public static final EntityLocation ENTITY_DEFAULT_LOCATION = new EntityLocation(0.0f, 0.0f, 0.0f);
	public static final float ENTITY_DEFAULT_BLOCKS_PER_TICK_SPEED = 0.5f;

	private final File _saveDirectory;
	private final Function<CuboidAddress, CuboidData> _cuboidGenerator;
	private final Map<CuboidAddress, CuboidData> _preLoaded;
	private final MessageQueue _queue;
	private final Thread _background;
	private final ByteBuffer _backround_serializationBuffer;

	// Shared data for passing information back from the background thread.
	private final ReentrantLock _sharedDataLock;
	private Collection<SuspendedCuboid<CuboidData>> _shared_resolvedCuboids;
	private Collection<Entity> _shared_resolvedEntities;

	public ResourceLoader(File saveDirectory
			, Function<CuboidAddress, CuboidData> cuboidGenerator
	)
	{
		// The save directory must exist as a directory before we get here.
		Assert.assertTrue(saveDirectory.isDirectory());
		
		_saveDirectory = saveDirectory;
		_cuboidGenerator = cuboidGenerator;
		_preLoaded = new HashMap<>();
		_queue = new MessageQueue();
		_background = new Thread(() -> {
			_background_main();
		}, "Cuboid Loader");
		_backround_serializationBuffer = ByteBuffer.allocate(SERIALIZATION_BUFFER_SIZE_BYTES);
		
		_sharedDataLock = new ReentrantLock();
		
		_background.start();
	}

	public void shutdown()
	{
		// We want to make sure any other IO operations requested are completed.
		_queue.waitForEmptyQueue();
		_queue.shutdown();
		try
		{
			_background.join();
		}
		catch (InterruptedException e)
		{
			// We don't use interruption.
			throw Assert.unexpected(e);
		}
	}

	/**
	 * Loads the given cuboid.  Note that this must be called before any consumer of the loader starts up as there is
	 * no synchronization on this path.
	 * Note that this is a temporary interface and will be replaced by a generator and/or pre-constructed world save in
	 * the future.
	 * 
	 * @param cuboid The cuboid to add to the pre-loaded data set.
	 */
	public void preload(CuboidData cuboid)
	{
		_preLoaded.put(cuboid.getCuboidAddress(), cuboid);
	}

	/**
	 * Queues up a background request for the given collection of cuboids and entities.  These will be returned in a
	 * future call as they will be internally loaded, asynchronously.
	 * Consequently, this call will return a collection of previously-requested cuboids and entities which have been
	 * loaded/generated in the background via their corresponding out parameters.
	 * 
	 * @param out_loadedCuboids Will be filled with previously-requested cuboids which have been satisfied in the
	 * background (null or non-empty).
	 * @param out_loadedEntities Will be filled with previously-requested entities which have been satisfied in the
	 * background.
	 * @param requestedCuboids The collection of cuboids to load/generate, by address.
	 * @param requestedEntityIds The collection of entities to load/generate, by ID.
	 */
	public void getResultsAndRequestBackgroundLoad(Collection<SuspendedCuboid<CuboidData>> out_loadedCuboids
			, Collection<Entity> out_loadedEntities
			, Collection<CuboidAddress> requestedCuboids
			, Collection<Integer> requestedEntityIds
	)
	{
		// Send this request to the background thread.
		if (!requestedCuboids.isEmpty() || !requestedEntityIds.isEmpty())
		{
			// Copy these since they could change out from under us once we hand-off.
			Collection<CuboidAddress> copiedCuboids = new ArrayList<>(requestedCuboids);
			Collection<Integer> copiedEntityIds = new ArrayList<>(requestedEntityIds);
			_queue.enqueue(() -> {
				for (CuboidAddress address : copiedCuboids)
				{
					// Priority of loads:
					// 1) Disk (since that is always considered authoritative)
					// 2) _inFlight (since these are supposed to be prioritized over generation
					// 3) The generator (if present).
					// 4) Return null (only happens in tests)
					
					// See if we can load this from disk.
					SuspendedCuboid<CuboidData> data = _background_readCuboidFromDisk(address);
					if (null == data)
					{
						if (_preLoaded.containsKey(address))
						{
							// We will "consume" this since we will load from disk on the next call.
							data = new SuspendedCuboid<>(_preLoaded.remove(address), List.of());
						}
						else if (null != _cuboidGenerator)
						{
							data = new SuspendedCuboid<>(_cuboidGenerator.apply(address), List.of());
						}
					}
					// If we found anything, return it.
					if (null != data)
					{
						_background_returnCuboid(data);
					}
				}
				for (int id : copiedEntityIds)
				{
					// Priority of loads:
					// 1) Disk (since that is always considered authoritative)
					// 2) The generator
					
					// See if we can load this from disk.
					Entity data = _background_readEntityFromDisk(id);
					if (null == data)
					{
						// Note that the entity generator is always present.
						data = _buildDefaultEntity(id);
					}
					
					// Return the result.
					Assert.assertTrue(null != data);
					_background_returnEntity(data);
				}
			});
		}
		
		// Pass back anything we have completed via out-params.
		_sharedDataLock.lock();
		try
		{
			if (null != _shared_resolvedCuboids)
			{
				out_loadedCuboids.addAll(_shared_resolvedCuboids);
				_shared_resolvedCuboids = null;
			}
			if (null != _shared_resolvedEntities)
			{
				out_loadedEntities.addAll(_shared_resolvedEntities);
				_shared_resolvedEntities = null;
			}
		}
		finally
		{
			_sharedDataLock.unlock();
		}
	}

	/**
	 * Requests that the given collection of cuboids and entities be written back to disk.  This call will return
	 * immediately while the write-back will complete asynchronously.
	 * Note that at least one of these collections must contain something.
	 * 
	 * @param cuboids The cuboids (and any suspended mutations) to write.
	 * @param entities The entities to write.
	 */
	public void writeBackToDisk(Collection<SuspendedCuboid<IReadOnlyCuboidData>> cuboids, Collection<Entity> entities)
	{
		// This one should only be called if there are some to write.
		Assert.assertTrue(!cuboids.isEmpty() || !entities.isEmpty());
		_queue.enqueue(() -> {
			for (SuspendedCuboid<IReadOnlyCuboidData> cuboid : cuboids)
			{
				_background_writeCuboidToDisk(cuboid);
			}
			for (Entity entity : entities)
			{
				_background_writeEntityToDisk(entity);
			}
		});
	}


	private void _background_main()
	{
		Runnable toRun = _queue.pollForNext(0L, null);
		while (null != toRun)
		{
			toRun.run();
			toRun = _queue.pollForNext(0L, null);
		}
	}

	private void _background_returnCuboid(SuspendedCuboid<CuboidData> loaded)
	{
		_sharedDataLock.lock();
		try
		{
			if (null == _shared_resolvedCuboids)
			{
				_shared_resolvedCuboids = new ArrayList<>();
			}
			_shared_resolvedCuboids.add(loaded);
		}
		finally
		{
			_sharedDataLock.unlock();
		}
	}

	private void _background_returnEntity(Entity loaded)
	{
		_sharedDataLock.lock();
		try
		{
			if (null == _shared_resolvedEntities)
			{
				_shared_resolvedEntities = new ArrayList<>();
			}
			_shared_resolvedEntities.add(loaded);
		}
		finally
		{
			_sharedDataLock.unlock();
		}
	}

	private SuspendedCuboid<CuboidData> _background_readCuboidFromDisk(CuboidAddress address)
	{
		// These data files are relatively small so we can just read this in, completely.
		SuspendedCuboid<CuboidData> result;
		try (
				RandomAccessFile aFile = new RandomAccessFile(_getCuboidFile(address), "r");
				FileChannel inChannel = aFile.getChannel();
		)
		{
			MappedByteBuffer buffer = inChannel.map(FileChannel.MapMode.READ_ONLY, 0, inChannel.size());
			buffer.load();
			CuboidData cuboid = CuboidData.createEmpty(address);
			Object state = cuboid.deserializeResumable(null, buffer);
			// There should be no resumable state since the file is complete.
			Assert.assertTrue(null == state);
			
			// Now, load any suspended mutations.
			List<IMutationBlock> suspended = new ArrayList<>();
			while (buffer.hasRemaining())
			{
				IMutationBlock mutation = MutationBlockCodec.parseAndSeekFlippedBuffer(buffer);
				suspended.add(mutation);
			}
			result = new SuspendedCuboid<>(cuboid, suspended);
		}
		catch (FileNotFoundException e)
		{
			// This is ok and means we should return null.
			result = null;
		}
		catch (IOException e)
		{
			throw Assert.unexpected(e);
		}
		return result;
	}

	private void _background_writeCuboidToDisk(SuspendedCuboid<IReadOnlyCuboidData> data)
	{
		// Serialize the entire cuboid into memory and write it out.
		Assert.assertTrue(0 == _backround_serializationBuffer.position());
		IReadOnlyCuboidData cuboid = data.cuboid();
		List<IMutationBlock> mutations = data.mutations();
		Object state = cuboid.serializeResumable(null, _backround_serializationBuffer);
		// We currently assume that we just do the write in a single call.
		Assert.assertTrue(null == state);
		
		// We now write any suspended mutations.
		for (IMutationBlock mutation : mutations)
		{
			MutationBlockCodec.serializeToBuffer(_backround_serializationBuffer, mutation);
		}
		_backround_serializationBuffer.flip();
		
		try (
				
				RandomAccessFile aFile = new RandomAccessFile(_getCuboidFile(cuboid.getCuboidAddress()), "rw");
				FileChannel outChannel = aFile.getChannel();
		)
		{
			int written = outChannel.write(_backround_serializationBuffer);
			// In case we are over-writting an existing file, be sure to truncate it.
			outChannel.truncate((long)written);
			
			// We expect that this wrote fully.
			Assert.assertTrue(!_backround_serializationBuffer.hasRemaining());
			_backround_serializationBuffer.clear();
		}
		catch (FileNotFoundException e)
		{
			throw Assert.unexpected(e);
		}
		catch (IOException e)
		{
			throw Assert.unexpected(e);
		}
	}

	private File _getCuboidFile(CuboidAddress address)
	{
		String fileName = "cuboid_" + address.x() + "_" + address.y() + "_" + address.z() + ".cuboid";
		return new File(_saveDirectory, fileName);
	}

	private Entity _background_readEntityFromDisk(int id)
	{
		// These data files are relatively small so we can just read this in, completely.
		Entity entity;
		try (
				RandomAccessFile aFile = new RandomAccessFile(_getEntityFile(id), "r");
				FileChannel inChannel = aFile.getChannel();
		)
		{
			MappedByteBuffer buffer = inChannel.map(FileChannel.MapMode.READ_ONLY, 0, inChannel.size());
			buffer.load();
			entity = CodecHelpers.readEntity(buffer);
		}
		catch (FileNotFoundException e)
		{
			// This is ok and means we should return null.
			entity = null;
		}
		catch (IOException e)
		{
			throw Assert.unexpected(e);
		}
		return entity;
	}

	private void _background_writeEntityToDisk(Entity entity)
	{
		// Serialize the entire entity into memory and write it out.
		Assert.assertTrue(0 == _backround_serializationBuffer.position());
		CodecHelpers.writeEntity(_backround_serializationBuffer, entity);
		_backround_serializationBuffer.flip();
		
		try (
				
				RandomAccessFile aFile = new RandomAccessFile(_getEntityFile(entity.id()), "rw");
				FileChannel outChannel = aFile.getChannel();
		)
		{
			outChannel.write(_backround_serializationBuffer);
			// We expect that this wrote fully.
			Assert.assertTrue(!_backround_serializationBuffer.hasRemaining());
			_backround_serializationBuffer.clear();
		}
		catch (FileNotFoundException e)
		{
			throw Assert.unexpected(e);
		}
		catch (IOException e)
		{
			throw Assert.unexpected(e);
		}
	}

	private File _getEntityFile(int id)
	{
		String fileName = "entity_" + id + ".entity";
		return new File(_saveDirectory, fileName);
	}

	private static Entity _buildDefaultEntity(int id)
	{
		// We start by giving the user an empty inventory.
		Inventory inventory = Inventory.start(InventoryAspect.CAPACITY_PLAYER).finish();
		return new Entity(id
				, ENTITY_DEFAULT_LOCATION
				, 0.0f
				, ENTITY_DEFAULT_VOLUME
				, ENTITY_DEFAULT_BLOCKS_PER_TICK_SPEED
				, inventory
				, null
				, null
		);
	}
}
