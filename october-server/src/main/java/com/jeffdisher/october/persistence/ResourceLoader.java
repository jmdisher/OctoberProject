package com.jeffdisher.october.persistence;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;

import com.jeffdisher.october.config.FlatTabListCallbacks;
import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.config.TabListReader.TabListException;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.CreatureIdAssigner;
import com.jeffdisher.october.logic.ScheduledChange;
import com.jeffdisher.october.logic.ScheduledMutation;
import com.jeffdisher.october.mutations.EntityChangePeriodic;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.net.MutationEntityCodec;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.WorldConfig;
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
	private final BiFunction<CreatureIdAssigner, CuboidAddress, SuspendedCuboid<CuboidData>> _cuboidGenerator;
	private final Map<CuboidAddress, CuboidData> _preLoaded;
	private final MessageQueue _queue;
	private final Thread _background;
	private final ByteBuffer _backround_serializationBuffer;

	// We directly expose the ID assigner since it is designed to be shared and is atomic.
	public final CreatureIdAssigner creatureIdAssigner;

	// Shared data for passing information back from the background thread.
	private final ReentrantLock _sharedDataLock;
	private Collection<SuspendedCuboid<CuboidData>> _shared_resolvedCuboids;
	private Collection<SuspendedEntity> _shared_resolvedEntities;

	public ResourceLoader(File saveDirectory
			, BiFunction<CreatureIdAssigner, CuboidAddress, SuspendedCuboid<CuboidData>> cuboidGenerator
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
		this.creatureIdAssigner = new CreatureIdAssigner();
		
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
	 * background.
	 * @param out_loadedEntities Will be filled with previously-requested entities which have been satisfied in the
	 * background.
	 * @param requestedCuboids The collection of cuboids to load/generate, by address.
	 * @param requestedEntityIds The collection of entities to load/generate, by ID.
	 */
	public void getResultsAndRequestBackgroundLoad(Collection<SuspendedCuboid<CuboidData>> out_loadedCuboids
			, Collection<SuspendedEntity> out_loadedEntities
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
							data = new SuspendedCuboid<>(_preLoaded.remove(address)
									, List.of()
									, List.of()
							);
						}
						else if (null != _cuboidGenerator)
						{
							data = _cuboidGenerator.apply(this.creatureIdAssigner, address);
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
					// We don't want to allow non-positive entity IDs (since those will be reserved for errors or future uses).
					Assert.assertTrue(id > 0);
					
					// Priority of loads:
					// 1) Disk (since that is always considered authoritative)
					// 2) The generator
					
					// See if we can load this from disk.
					SuspendedEntity data = _background_readEntityFromDisk(id);
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
	 * @param entities The entities (and any suspended mutations) to write.
	 */
	public void writeBackToDisk(Collection<SuspendedCuboid<IReadOnlyCuboidData>> cuboids, Collection<SuspendedEntity> entities)
	{
		// This one should only be called if there are some to write.
		Assert.assertTrue(!cuboids.isEmpty() || !entities.isEmpty());
		_queue.enqueue(() -> {
			for (SuspendedCuboid<IReadOnlyCuboidData> cuboid : cuboids)
			{
				_background_writeCuboidToDisk(cuboid);
			}
			for (SuspendedEntity entity : entities)
			{
				_background_writeEntityToDisk(entity);
			}
		});
	}

	/**
	 * Reads the world config from disk, updating corresponding options in the given config object.
	 * NOTE:  This call is synchronous so should only be called during start-up.
	 * 
	 * @param config A WorldConfig object to modify.
	 * @throws IOException There was a problem loading the file.
	 */
	public void populateWorldConfig(WorldConfig config) throws IOException
	{
		File configFile = _getConfigFile();
		if (configFile.exists())
		{
			Map<String, String> overrides;
			try (FileInputStream stream = new FileInputStream(configFile))
			{
				FlatTabListCallbacks<String, String> callbacks = new FlatTabListCallbacks<>((String value) -> value, (String value) -> value);
				TabListReader.readEntireFile(callbacks, stream);
				overrides = callbacks.data;
			}
			catch (TabListException e)
			{
				// We will treat this as a static start-up failure.
				throw Assert.unexpected(e);
			}
			config.loadOverrides(overrides);
		}
	}

	/**
	 * Stores the given world config to disk.
	 * NOTE:  This call is synchronous so should only be called during shut-down.
	 * 
	 * @param config A WorldConfig object to save to disk.
	 * @throws IOException There was a problem saving the file.
	 */
	public void storeWorldConfig(WorldConfig config) throws IOException
	{
		File configFile = _getConfigFile();
		Map<String, String> options = config.getRawOptions();
		try (FileOutputStream stream = new FileOutputStream(configFile))
		{
			stream.write("# World config for an OctoberProject world.  This uses the tablist format and errors will cause start-up failures.\n\n".getBytes(StandardCharsets.UTF_8));
			for (Map.Entry<String, String> elt : options.entrySet())
			{
				String line = elt.getKey() + "\t" + elt.getValue() + "\n";
				stream.write(line.getBytes(StandardCharsets.UTF_8));
			}
		}
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

	private void _background_returnEntity(SuspendedEntity loaded)
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
			
			// Load any creatures associated with the cuboid.
			int creatureCount = buffer.getInt();
			List<CreatureEntity> creatures = new ArrayList<>();
			for (int i = 0; i < creatureCount; ++i)
			{
				CreatureEntity entity = CodecHelpers.readCreatureEntity(this.creatureIdAssigner.next(), buffer);
				creatures.add(entity);
			}
			
			// Now, load any suspended mutations.
			int mutationCount = buffer.getInt();
			List<ScheduledMutation> suspended = new ArrayList<>();
			for (int i = 0; i < mutationCount; ++i)
			{
				// Read the parts of the suspended data.
				long millisUntilReady = buffer.getLong();
				IMutationBlock mutation = MutationBlockCodec.parseAndSeekFlippedBuffer(buffer);
				suspended.add(new ScheduledMutation(mutation, millisUntilReady));
			}
			
			// This should be fully read.
			Assert.assertTrue(!buffer.hasRemaining());
			result = new SuspendedCuboid<>(cuboid
					, creatures
					, suspended
			);
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
		List<ScheduledMutation> mutations = data.mutations();
		List<CreatureEntity> entities = data.creatures();
		Object state = cuboid.serializeResumable(null, _backround_serializationBuffer);
		// We currently assume that we just do the write in a single call.
		Assert.assertTrue(null == state);
		
		// Store any creatures in this cuboid.
		_backround_serializationBuffer.putInt(entities.size());
		for (CreatureEntity entity : entities)
		{
			CodecHelpers.writeCreatureEntity(_backround_serializationBuffer, entity);
		}
		
		// We now write any suspended mutations.
		// Find out how many are going to be saved (some have ephemeral references and should be dropped).
		List<ScheduledMutation> mutationsToWrite = mutations.stream().filter((ScheduledMutation scheduled) -> scheduled.mutation().canSaveToDisk()).toList();
		_backround_serializationBuffer.putInt(mutationsToWrite.size());
		for (ScheduledMutation scheduled : mutationsToWrite)
		{
			// Write the parts of the data.
			_backround_serializationBuffer.putLong(scheduled.millisUntilReady());
			MutationBlockCodec.serializeToBuffer(_backround_serializationBuffer, scheduled.mutation());
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

	private SuspendedEntity _background_readEntityFromDisk(int id)
	{
		// These data files are relatively small so we can just read this in, completely.
		SuspendedEntity result;
		try (
				RandomAccessFile aFile = new RandomAccessFile(_getEntityFile(id), "r");
				FileChannel inChannel = aFile.getChannel();
		)
		{
			MappedByteBuffer buffer = inChannel.map(FileChannel.MapMode.READ_ONLY, 0, inChannel.size());
			buffer.load();
			Entity entity = CodecHelpers.readEntity(buffer);
			
			// Now, load any suspended changes.
			List<ScheduledChange> suspended = new ArrayList<>();
			while (buffer.hasRemaining())
			{
				// Read the parts of the suspended data.
				long millisUntilReady = buffer.getLong();
				IMutationEntity<IMutablePlayerEntity> change = MutationEntityCodec.parseAndSeekFlippedBuffer(buffer);
				suspended.add(new ScheduledChange(change, millisUntilReady));
			}
			result = new SuspendedEntity(entity, suspended);
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

	private void _background_writeEntityToDisk(SuspendedEntity suspended)
	{
		// Serialize the entire entity into memory and write it out.
		Assert.assertTrue(0 == _backround_serializationBuffer.position());
		Entity entity = suspended.entity();
		List<ScheduledChange> changes = suspended.changes();
		CodecHelpers.writeEntity(_backround_serializationBuffer, entity);
		
		// We now write any suspended changes.
		for (ScheduledChange scheduled : changes)
		{
			// Check that this kind of change can be stored to disk (some have ephemeral references and should be dropped).
			if (scheduled.change().canSaveToDisk())
			{
				// Write the parts of the data.
				_backround_serializationBuffer.putLong(scheduled.millisUntilReady());
				MutationEntityCodec.serializeToBuffer(_backround_serializationBuffer, scheduled.change());
			}
		}
		_backround_serializationBuffer.flip();
		
		try (
				
				RandomAccessFile aFile = new RandomAccessFile(_getEntityFile(entity.id()), "rw");
				FileChannel outChannel = aFile.getChannel();
		)
		{
			int written = outChannel.write(_backround_serializationBuffer);
			// In case we are over-writing an existing file, be sure to truncate it.
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

	private File _getEntityFile(int id)
	{
		String fileName = "entity_" + id + ".entity";
		return new File(_saveDirectory, fileName);
	}

	private static SuspendedEntity _buildDefaultEntity(int id)
	{
		List<ScheduledChange> initialChanges = List.of(
				new ScheduledChange(new EntityChangePeriodic(), EntityChangePeriodic.MILLIS_BETWEEN_PERIODIC_UPDATES)
		);
		return new SuspendedEntity(MutableEntity.create(id).freeze(), initialChanges);
	}

	private File _getConfigFile()
	{
		String fileName = "config.tablist";
		return new File(_saveDirectory, fileName);
	}
}
