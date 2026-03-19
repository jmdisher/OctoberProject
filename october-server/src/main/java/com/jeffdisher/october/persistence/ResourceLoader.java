package com.jeffdisher.october.persistence;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import com.jeffdisher.october.actions.EntityActionPeriodic;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.config.FlatTabListCallbacks;
import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.config.TabListReader.TabListException;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.logic.CreatureIdAssigner;
import com.jeffdisher.october.logic.HeightMapHelpers;
import com.jeffdisher.october.logic.PassiveIdAssigner;
import com.jeffdisher.october.logic.ScheduledChange;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.net.EntityActionCodec;
import com.jeffdisher.october.persistence.legacy.LegacyEntityV1;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.WorldConfig;
import com.jeffdisher.october.utils.Assert;
import com.jeffdisher.october.utils.MessageQueue;
import com.jeffdisher.october.worldgen.IWorldGenerator;


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
	private final IWorldGenerator _cuboidGenerator;
	private final WorldConfig _config;
	private final Map<CuboidAddress, CuboidData> _preLoaded;
	private final MessageQueue _queue;
	private final Thread _background;
	private final ByteBuffer _backround_serializationBuffer;

	// These collections contain the serialized versions of the cuboids and entities which are considered live within the
	// system (not yet retired by a call to writeBackToDiskAndRetire) and used to avoid redundant write-back to disk.
	private final Map<CuboidAddress, byte[]> _background_serializedCuboidBuffer;
	private final Map<Integer, byte[]> _background_serializedEntityBuffer;

	// We directly expose the ID assigner since it is designed to be shared and is atomic.
	public final CreatureIdAssigner creatureIdAssigner;
	public final PassiveIdAssigner passiveIdAssigner;

	// Shared data for passing information back from the background thread.
	private final ReentrantLock _sharedDataLock;
	private Collection<SuspendedCuboid<CuboidData>> _shared_resolvedCuboids;
	private Collection<SuspendedEntity> _shared_resolvedEntities;

	// Technically shared between the foreground and background thread but only updated to true by the foreground and
	// false by the background so it can be safely handled as a normal variable with volatile.
	private volatile boolean _isAttemptedWritePending;

	public ResourceLoader(File saveDirectory
			, IWorldGenerator cuboidGenerator
			, WorldConfig config
	)
	{
		// The save directory must exist as a directory before we get here.
		Assert.assertTrue(saveDirectory.isDirectory());
		
		_saveDirectory = saveDirectory;
		_cuboidGenerator = cuboidGenerator;
		_config = config;
		_preLoaded = new HashMap<>();
		_queue = new MessageQueue();
		_background = new Thread(() -> {
			_background_main();
		}, "Cuboid Loader");
		_backround_serializationBuffer = ByteBuffer.allocate(SERIALIZATION_BUFFER_SIZE_BYTES);
		_background_serializedCuboidBuffer = new HashMap<>();
		_background_serializedEntityBuffer = new HashMap<>();
		this.creatureIdAssigner = new CreatureIdAssigner();
		this.passiveIdAssigner = new PassiveIdAssigner();
		
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
		
		// Once everything is done, we expect those internal caches to be empty (otherwise, something is leaking).
		Assert.assertTrue(_background_serializedCuboidBuffer.isEmpty());
		Assert.assertTrue(_background_serializedEntityBuffer.isEmpty());
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
	 * @param currentGameMillis The millisecond time of the loading tick.
	 * @param requestedEntityIds The collection of entities to load/generate, by ID.
	 */
	public void getResultsAndRequestBackgroundLoad(Collection<SuspendedCuboid<CuboidData>> out_loadedCuboids
			, Collection<SuspendedEntity> out_loadedEntities
			, Collection<CuboidAddress> requestedCuboids
			, Collection<Integer> requestedEntityIds
			, long currentGameMillis
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
					SuspendedCuboid<CuboidData> data = _background_readCuboidFromDisk(address, currentGameMillis);
					if (null == data)
					{
						if (_preLoaded.containsKey(address))
						{
							// We will "consume" this since we will load from disk on the next call.
							CuboidData preloaded = _preLoaded.remove(address);
							data = new SuspendedCuboid<>(preloaded
									, HeightMapHelpers.buildHeightMap(preloaded)
									, List.of()
									, List.of()
									, Map.of()
									, List.of()
							);
						}
						else if (null != _cuboidGenerator)
						{
							data = _cuboidGenerator.generateCuboid(this.creatureIdAssigner, address, currentGameMillis);
						}
						if (null != data)
						{
							// If we just generated this for the first time, make an empty entry in the buffer to force write-back on next write attempt.
							_background_serializedCuboidBuffer.put(address, new byte[0]);
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
					SuspendedEntity data = _background_readEntityFromDisk(id, currentGameMillis);
					if (null == data)
					{
						// Note that the entity generator is always present.
						data = _buildDefaultEntity(id, _config.worldSpawn.toEntityLocation(), (WorldConfig.DefaultPlayerMode.CREATIVE == _config.defaultPlayerMode));
						
						if (null != data)
						{
							// If we just generated this for the first time, make an empty entry in the buffer to force write-back on next write attempt.
							_background_serializedEntityBuffer.put(id, new byte[0]);
						}
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
	 * immediately while the write-back will complete asynchronously.  After this call, the cuboids and entities are
	 * considered "retired" and are purged from internal caches.
	 * Note that at least one of these collections must contain something.
	 * 
	 * @param cuboids The cuboids (and any suspended mutations) to write.
	 * @param entities The entities (and any suspended mutations) to write.
	 * @param gameTimeMillis The millisecond time of the last-completed tick (used for storing "time remaining" in some
	 * counters, etc).
	 */
	public void writeBackToDiskAndRetire(Collection<PackagedCuboid> cuboids, Collection<SuspendedEntity> entities, long gameTimeMillis)
	{
		// This one should only be called if there are some to write.
		Assert.assertTrue(!cuboids.isEmpty() || !entities.isEmpty());
		_queue.enqueue(() -> {
			for (PackagedCuboid cuboid : cuboids)
			{
				_background_writeCuboidToDisk(cuboid, gameTimeMillis, false);
			}
			for (SuspendedEntity entity : entities)
			{
				_background_writeEntityToDisk(entity, false);
			}
		});
	}

	public void tryWriteBackToDisk(Collection<PackagedCuboid> cuboids, Collection<SuspendedEntity> entities, long gameTimeMillis)
	{
		// This one should only be called if there are some to write.
		Assert.assertTrue(!cuboids.isEmpty() || !entities.isEmpty());
		if (!_isAttemptedWritePending)
		{
			_isAttemptedWritePending = true;
			_queue.enqueue(() -> {
				for (PackagedCuboid cuboid : cuboids)
				{
					_background_writeCuboidToDisk(cuboid, gameTimeMillis, true);
				}
				for (SuspendedEntity entity : entities)
				{
					_background_writeEntityToDisk(entity, true);
				}
				_isAttemptedWritePending = false;
			});
		}
	}

	/**
	 * Reads the world config from disk, updating corresponding options in the given config object.
	 * NOTE:  This call is synchronous so should only be called during start-up.
	 * 
	 * @param saveDirectory The directory where the world data is stored.
	 * @param config A WorldConfig object to modify.
	 * @return True if the config was loaded from disk or false if the default was left untouched.
	 * @throws IOException There was a problem loading the file.
	 */
	public static boolean populateWorldConfig(File saveDirectory, WorldConfig config) throws IOException
	{
		boolean didLoad = false;
		File configFile = _getConfigFile(saveDirectory);
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
			didLoad = true;
		}
		return didLoad;
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
		File configFile = _getConfigFile(_saveDirectory);
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

	private SuspendedCuboid<CuboidData> _background_readCuboidFromDisk(CuboidAddress address, long currentGameMillis)
	{
		// These data files are relatively small so we can just read this in, completely.
		SuspendedCuboid<CuboidData> result;
		try
		{
			byte[] rawData = Files.readAllBytes(_getCuboidFile(address));
			ByteBuffer buffer = ByteBuffer.wrap(rawData);
			
			// Verify the version is one we can understand.
			int version = buffer.getInt();
			
			if (StorageVersions.CURRENT != version)
			{
				// We need to re-write this and re-write it before returning it.
				Assert.assertTrue(0 == _backround_serializationBuffer.position());
				
				// We will use the serialization buffer.
				_backround_serializationBuffer.putInt(StorageVersions.CURRENT);
				CuboidTranslator.changeToLatestVersion(_backround_serializationBuffer, buffer, version);
				
				// Note that we could make CuboidTranslator return the parsed object, but this allows us to verify we didn't break anything in serialization.
				_backround_serializationBuffer.flip();
				byte[] serializedBytes = new byte[_backround_serializationBuffer.remaining()];
				_backround_serializationBuffer.get(serializedBytes);
				Assert.assertTrue(!_backround_serializationBuffer.hasRemaining());
				_backround_serializationBuffer.clear();
				
				// Save this out.
				_background_writeCuboidBytesToFile(address, serializedBytes);
				
				// Update our local variables to this new content and proceed with the common path.
				rawData = serializedBytes;
				buffer = ByteBuffer.wrap(rawData);
				version = buffer.getInt();
			}
			
			result = CuboidCodec.deserializeCuboidWithoutVersionHeader(buffer
				, address
				, currentGameMillis
				, this.creatureIdAssigner
				, this.passiveIdAssigner
			);
			
			// We got this far so store the raw data for later comparisons on write-back.
			_background_serializedCuboidBuffer.put(address, rawData);
		}
		catch (NoSuchFileException e)
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

	private void _background_writeCuboidToDisk(PackagedCuboid data, long gameTimeMillis, boolean maintainCache)
	{
		// Serialize the entire cuboid into memory and write it out.
		// We write the version header here but the CuboidCodec does the rest of the serialization.
		Assert.assertTrue(0 == _backround_serializationBuffer.position());
		_backround_serializationBuffer.putInt(StorageVersions.CURRENT);
		
		CuboidCodec.serializeCuboidWithoutVersionHeader(_backround_serializationBuffer, data, gameTimeMillis);
		
		// We are done the write so flip the buffer and write it out.
		_backround_serializationBuffer.flip();
		byte[] serializedBytes = new byte[_backround_serializationBuffer.remaining()];
		_backround_serializationBuffer.get(serializedBytes);
		Assert.assertTrue(!_backround_serializationBuffer.hasRemaining());
		_backround_serializationBuffer.clear();
		
		CuboidAddress address = data.cuboid().getCuboidAddress();
		byte[] originalBytes = _background_serializedCuboidBuffer.get(address);
		Assert.assertTrue(null != originalBytes);
		
		if (!Arrays.equals(originalBytes, serializedBytes))
		{
			_background_writeCuboidBytesToFile(address, serializedBytes);
			
			// Now that we are done, update the cuboid buffer.
			if (maintainCache)
			{
				_background_serializedCuboidBuffer.put(address, serializedBytes);
			}
		}
		
		// If we should clear the cache, do that whether we updated or not.
		if (!maintainCache)
		{
			_background_serializedCuboidBuffer.remove(address);
		}
	}

	private Path _getCuboidFile(CuboidAddress address)
	{
		String fileName = "cuboid_" + address.x() + "_" + address.y() + "_" + address.z() + ".cuboid";
		return new File(_saveDirectory, fileName).toPath();
	}

	private SuspendedEntity _background_readEntityFromDisk(int id, long currentGameMillis)
	{
		// These data files are relatively small so we can just read this in, completely.
		SuspendedEntity result;
		try
		{
			byte[] rawData = Files.readAllBytes(_getEntityFile(id));
			ByteBuffer buffer = ByteBuffer.wrap(rawData);
			
			// Verify the version is one we can understand.
			int version = buffer.getInt();
			
			// We want to create the decoder context here since we have version data.
			Environment env = Environment.getShared();
			boolean usePreV8NonStackableDecoding = (version <= StorageVersions.V7);
			boolean usePreV11DamageDecoding = false;
			DeserializationContext context = new DeserializationContext(env
				, buffer
				, currentGameMillis
				, usePreV8NonStackableDecoding
				, usePreV11DamageDecoding
			);
			
			Supplier<SuspendedEntity> dataReader;
			if ((StorageVersions.CURRENT == version)
				|| (StorageVersions.V10 == version)
			)
			{
				// Do nothing special - just stops old versions from being broken.
				dataReader = () -> {
					Entity entity = CodecHelpers.readEntityDisk(context);
					
					// Now, load any suspended changes.
					List<ScheduledChange> suspended = _background_readSuspendedMutations(context);
					return new SuspendedEntity(entity, suspended);
				};
			}
			else if ((StorageVersions.V2 == version)
					|| (StorageVersions.V3 == version)
					|| (StorageVersions.V4 == version)
					|| (StorageVersions.V5 == version)
					|| (StorageVersions.V6 == version)
					|| (StorageVersions.V7 == version)
					|| (StorageVersions.V8 == version)
					|| (StorageVersions.V9 == version)
			)
			{
				// These versions used a different on-disk entity shape.
				dataReader = () -> {
					Entity entity = _readEntityPre10(context);
					
					// Now, load any suspended changes.
					List<ScheduledChange> suspended = _background_readSuspendedMutations(context);
					return new SuspendedEntity(entity, suspended);
				};
			}
			else if (StorageVersions.V1 == version)
			{
				// The V1 entity is has less data.
				dataReader = () -> {
					// Read the legacy data.
					LegacyEntityV1 legacy = LegacyEntityV1.load(context);
					Entity entity = legacy.toEntity();
					
					// Now, load any suspended changes.
					List<ScheduledChange> suspended = _background_readSuspendedMutations(context);
					return new SuspendedEntity(entity, suspended);
				};
			}
			else
			{
				throw new RuntimeException("UNSUPPORTED ENTITY STORAGE VERSION:  " + version);
			}
			
			result = dataReader.get();
			
			// We got this far so store the raw data for later comparisons on write-back.
			_background_serializedEntityBuffer.put(id, rawData);
		}
		catch (NoSuchFileException e)
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

	private List<ScheduledChange> _background_readSuspendedMutations(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		List<ScheduledChange> suspended = new ArrayList<>();
		while (buffer.hasRemaining())
		{
			// Read the parts of the suspended data.
			long millisUntilReady = buffer.getLong();
			IEntityAction<IMutablePlayerEntity> change = EntityActionCodec.parseAndSeekContext(context);
			suspended.add(new ScheduledChange(change, millisUntilReady));
		}
		return suspended;
	}

	private void _background_writeEntityToDisk(SuspendedEntity suspended, boolean maintainCache)
	{
		// Serialize the entire entity into memory and write it out.
		Assert.assertTrue(0 == _backround_serializationBuffer.position());
		
		// Write the version header.
		_backround_serializationBuffer.putInt(StorageVersions.CURRENT);
		
		Entity entity = suspended.entity();
		List<ScheduledChange> changes = suspended.changes();
		CodecHelpers.writeEntityDisk(_backround_serializationBuffer, entity);
		
		// We now write any suspended changes.
		for (ScheduledChange scheduled : changes)
		{
			// Check that this kind of change can be stored to disk (some have ephemeral references and should be dropped).
			if (scheduled.change().canSaveToDisk())
			{
				// Write the parts of the data.
				_backround_serializationBuffer.putLong(scheduled.millisUntilReady());
				EntityActionCodec.serializeToBuffer(_backround_serializationBuffer, scheduled.change());
			}
		}
		_backround_serializationBuffer.flip();
		
		byte[] serializedBytes = new byte[_backround_serializationBuffer.remaining()];
		_backround_serializationBuffer.get(serializedBytes);
		Assert.assertTrue(!_backround_serializationBuffer.hasRemaining());
		_backround_serializationBuffer.clear();
		
		int entityId = entity.id();
		byte[] originalBytes = _background_serializedEntityBuffer.get(entityId);
		Assert.assertTrue(null != originalBytes);
		
		if (!Arrays.equals(originalBytes, serializedBytes))
		{
			try
			{
				Files.write(_getEntityFile(entityId), serializedBytes, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
			}
			catch (NoSuchFileException e)
			{
				throw Assert.unexpected(e);
			}
			catch (IOException e)
			{
				throw Assert.unexpected(e);
			}
			
			// Now that we are done, update the entity buffer.
			if (maintainCache)
			{
				_background_serializedEntityBuffer.put(entityId, serializedBytes);
			}
		}
		
		// If we should clear the cache, do that whether we updated or not.
		if (!maintainCache)
		{
			_background_serializedEntityBuffer.remove(entityId);
		}
	}

	private Path _getEntityFile(int id)
	{
		String fileName = "entity_" + id + ".entity";
		return new File(_saveDirectory, fileName).toPath();
	}

	private static SuspendedEntity _buildDefaultEntity(int id, EntityLocation spawn, boolean isCreative)
	{
		List<ScheduledChange> initialChanges = List.of(
				new ScheduledChange(new EntityActionPeriodic(), EntityActionPeriodic.MILLIS_BETWEEN_PERIODIC_UPDATES)
		);
		MutableEntity entity = MutableEntity.createWithLocation(id, spawn, spawn);
		entity.isCreativeMode = isCreative;
		return new SuspendedEntity(entity.freeze(), initialChanges);
	}

	private static File _getConfigFile(File saveDirectory)
	{
		String fileName = "config.tablist";
		return new File(saveDirectory, fileName);
	}

	private static Entity _readEntityPre10(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		int id = buffer.getInt();
		boolean isCreativeMode = CodecHelpers.readBoolean(buffer);
		EntityLocation location = CodecHelpers.readEntityLocation(buffer);
		EntityLocation velocity = CodecHelpers.readEntityLocation(buffer);
		byte yaw = buffer.get();
		byte pitch = buffer.get();
		Inventory inventory = CodecHelpers.readInventory(context);
		int[] hotbar = new int[Entity.HOTBAR_SIZE];
		for (int i = 0; i < hotbar.length; ++i)
		{
			hotbar[i] = buffer.getInt();
		}
		int hotbarIndex = buffer.getInt();
		NonStackableItem[] armour = new NonStackableItem[BodyPart.values().length];
		for (int i = 0; i < armour.length; ++i)
		{
			armour[i] = CodecHelpers.readNonStackableItem(context);
		}
		// We ignore localCraftOperation as it is now ephemeral.
		CodecHelpers.readCraftOperation(buffer);
		byte health = buffer.get();
		byte food = buffer.get();
		byte breath = buffer.get();
		// We ignore int energyDeficit as it is now ephemeral.
		buffer.getInt();
		EntityLocation spawn = CodecHelpers.readEntityLocation(buffer);
		
		return new Entity(id
			, isCreativeMode
			, location
			, velocity
			, yaw
			, pitch
			, inventory
			, hotbar
			, hotbarIndex
			, armour
			, health
			, food
			, breath
			, spawn
			
			, Entity.EMPTY_SHARED
			, Entity.EMPTY_LOCAL
		);
	}

	private void _background_writeCuboidBytesToFile(CuboidAddress address, byte[] serializedBytes)
	{
		try
		{
			Files.write(_getCuboidFile(address), serializedBytes, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
		}
		catch (NoSuchFileException e)
		{
			throw Assert.unexpected(e);
		}
		catch (IOException e)
		{
			throw Assert.unexpected(e);
		}
	}
}
