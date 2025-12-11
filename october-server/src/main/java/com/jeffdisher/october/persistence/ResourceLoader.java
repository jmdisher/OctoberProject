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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import com.jeffdisher.october.actions.EntityActionPeriodic;
import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.OrientationAspect;
import com.jeffdisher.october.config.FlatTabListCallbacks;
import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.config.TabListReader.TabListException;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.CuboidHeightMap;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.data.IOctree;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.CreatureIdAssigner;
import com.jeffdisher.october.logic.HeightMapHelpers;
import com.jeffdisher.october.logic.PassiveIdAssigner;
import com.jeffdisher.october.logic.ScheduledChange;
import com.jeffdisher.october.logic.ScheduledMutation;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.MutationBlockPeriodic;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.net.EntityActionCodec;
import com.jeffdisher.october.persistence.legacy.LegacyCreatureEntityV1;
import com.jeffdisher.october.persistence.legacy.LegacyCreatureEntityV8;
import com.jeffdisher.october.persistence.legacy.LegacyEntityV1;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.PassiveEntity;
import com.jeffdisher.october.types.PassiveType;
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
	/**
	 * Version 0 was used in v1.0-pre4 and earlier, no longer supported (pre-releases have no migration support).
	 * Version 1 was used in v1.0.1 and earlier, and is supported.
	 * Version 2 was used in v1.1 and earlier, and is supported (3 only adds data, not changing data).
	 * Version 3 was used in v1.2.1 and earlier, and is supported (4 only adds data, not changing data).
	 * Version 4 was used in v1.3 and earlier, and is supported (5 only adds and deprecates data).
	 * Version 5 was used in v1.5 and earlier, and is supported.
	 * Version 6 was used in v1.6 and earlier, and is supported (7 only adds data).
	 * Version 7 was used in v1.7 and earlier, and is supported.
	 * Version 8 was used in v1.8 and earlier, and is supported.
	 * Version 9 was used in v1.9 and earlier, and is supported.
	 * Version 10 was used in v1.10 and earlier, and is supported.
	 */
	public static final int VERSION_CUBOID_V1 = 1;
	public static final int VERSION_CUBOID_V2 = 2;
	public static final int VERSION_CUBOID_V3 = 3;
	public static final int VERSION_CUBOID_V4 = 4;
	public static final int VERSION_CUBOID_V5 = 5;
	public static final int VERSION_CUBOID_V6 = 6;
	public static final int VERSION_CUBOID_V7 = 7;
	public static final int VERSION_CUBOID_V8 = 8;
	public static final int VERSION_CUBOID_V9 = 9;
	public static final int VERSION_CUBOID_V10 = 10;
	public static final int VERSION_CUBOID = 11;
	/**
	 * Version 0 was used in v1.0-pre6 and earlier, no longer supported (pre-releases have no migration support).
	 * Version 1 was used in v1.0.1 and earlier, and is supported.
	 * Version 2 was used in v1.1 and earlier, and is supported (3 only adds data, not changing data).
	 * Version 3 was used in v1.2.1 and earlier, and is supported (4 only adds data, not changing data).
	 * Version 4 was used in v1.3 and earlier, and is supported (5 only adds and deprecates data).
	 * Version 5 was used in v1.5 and earlier, and is supported (6 only adds and deprecates data).
	 * Version 6 was used in v1.6 and earlier, and is supported (7 only adds data).
	 * Version 7 was used in v1.7 and earlier, and is supported.
	 * Version 8 was used in v1.8 and earlier, and is supported.
	 * Version 9 was used in v1.9 and earlier, and is supported.
	 * Version 10 was used in v1.10 and earlier, and is supported.
	 */
	public static final int VERSION_ENTITY_V1 = 1;
	public static final int VERSION_ENTITY_V2 = 2;
	public static final int VERSION_ENTITY_V3 = 3;
	public static final int VERSION_ENTITY_V4 = 4;
	public static final int VERSION_ENTITY_V5 = 5;
	public static final int VERSION_ENTITY_V6 = 6;
	public static final int VERSION_ENTITY_V7 = 7;
	public static final int VERSION_ENTITY_V8 = 8;
	public static final int VERSION_ENTITY_V9 = 9;
	public static final int VERSION_ENTITY_V10 = 10;
	public static final int VERSION_ENTITY = 11;
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
	 * @param gameTimeMillis The millisecond time of the last-completed tick (used for storing "time remaining" in some
	 * counters, etc).
	 */
	public void writeBackToDisk(Collection<PackagedCuboid> cuboids, Collection<SuspendedEntity> entities, long gameTimeMillis)
	{
		// This one should only be called if there are some to write.
		Assert.assertTrue(!cuboids.isEmpty() || !entities.isEmpty());
		_queue.enqueue(() -> {
			for (PackagedCuboid cuboid : cuboids)
			{
				_background_writeCuboidToDisk(cuboid, gameTimeMillis);
			}
			for (SuspendedEntity entity : entities)
			{
				_background_writeEntityToDisk(entity);
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
					_background_writeCuboidToDisk(cuboid, gameTimeMillis);
				}
				for (SuspendedEntity entity : entities)
				{
					_background_writeEntityToDisk(entity);
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
		try (
				RandomAccessFile aFile = new RandomAccessFile(_getCuboidFile(address), "r");
				FileChannel inChannel = aFile.getChannel();
		)
		{
			MappedByteBuffer buffer = inChannel.map(FileChannel.MapMode.READ_ONLY, 0, inChannel.size());
			buffer.load();
			
			// Verify the version is one we can understand.
			int version = buffer.getInt();
			
			// We want to create the decoder context here since we have version data.
			Environment env = Environment.getShared();
			boolean usePreV8NonStackableDecoding = (version <= VERSION_CUBOID_V7);
			DeserializationContext context = new DeserializationContext(env
				, buffer
				, currentGameMillis
				, usePreV8NonStackableDecoding
			);
			
			Supplier<SuspendedCuboid<CuboidData>> dataReader;
			if ((VERSION_CUBOID == version)
			)
			{
				// Version 11 added a new aspect so we need to read the cuboid differently.
				dataReader = () -> {
					CuboidData cuboid = _background_readCuboid(address, context);
					
					// Load any creatures associated with the cuboid.
					List<CreatureEntity> creatures = _background_readCreatures(context);
					
					// Now, load any suspended mutations.
					List<ScheduledMutation> pendingMutations = _background_readMutations(context);
					// ... and any periodic mutations.
					Map<BlockAddress, Long> periodicMutations = _background_readPeriodic(buffer);
					
					// Passives are stored much like creatures.
					List<PassiveEntity> passives = _background_readPassives(context);
					
					// This should be fully read.
					Assert.assertTrue(!buffer.hasRemaining());
					
					// The height map is ephemeral so it is built here.  Note that building this might be somewhat expensive.
					CuboidHeightMap heightMap = HeightMapHelpers.buildHeightMap(cuboid);
					return new SuspendedCuboid<>(cuboid
							, heightMap
							, creatures
							, pendingMutations
							, periodicMutations
							, passives
					);
				};
			}
			else if ((VERSION_CUBOID_V9 == version)
				|| (VERSION_CUBOID_V10 == version)
			)
			{
				// Version 10 didn't change anything, just added to it, so we can read with the same logic.
				dataReader = () -> {
					CuboidData cuboid = _background_readCuboidPre11(address, context);
					
					// Load any creatures associated with the cuboid.
					List<CreatureEntity> creatures = _background_readCreatures(context);
					
					// Now, load any suspended mutations.
					List<ScheduledMutation> pendingMutations = _background_readMutations(context);
					// ... and any periodic mutations.
					Map<BlockAddress, Long> periodicMutations = _background_readPeriodic(buffer);
					
					// Passives are stored much like creatures.
					List<PassiveEntity> passives = _background_readPassives(context);
					
					// This should be fully read.
					Assert.assertTrue(!buffer.hasRemaining());
					
					// The height map is ephemeral so it is built here.  Note that building this might be somewhat expensive.
					CuboidHeightMap heightMap = HeightMapHelpers.buildHeightMap(cuboid);
					return new SuspendedCuboid<>(cuboid
							, heightMap
							, creatures
							, pendingMutations
							, periodicMutations
							, passives
					);
				};
			}
			else if (VERSION_CUBOID_V8 == version)
			{
				dataReader = () -> {
					CuboidData cuboid = _background_readCuboidPre11(address, context);
					
					// Load any creatures associated with the cuboid.
					List<CreatureEntity> creatures = _background_readCreaturesV8(context);
					
					// Now, load any suspended mutations.
					List<ScheduledMutation> pendingMutations = _background_readMutations(context);
					// ... and any periodic mutations.
					Map<BlockAddress, Long> periodicMutations = _background_readPeriodic(buffer);
					
					// Passives added in V9, extracted from empty item inventory slots.
					List<PassiveEntity> convertedPassives = _convertCuboidPre9(env, currentGameMillis, cuboid, address.getBase());
					List<PassiveEntity> passives = (null != convertedPassives)
						? convertedPassives
						: List.of()
					;
					
					// This should be fully read.
					Assert.assertTrue(!buffer.hasRemaining());
					
					// The height map is ephemeral so it is built here.  Note that building this might be somewhat expensive.
					CuboidHeightMap heightMap = HeightMapHelpers.buildHeightMap(cuboid);
					return new SuspendedCuboid<>(cuboid
							, heightMap
							, creatures
							, pendingMutations
							, periodicMutations
							, passives
					);
				};
			}
			else if (VERSION_CUBOID_V7 == version)
			{
				dataReader = () -> {
					CuboidData cuboid = _background_readCuboidPre8(address, context);
					
					// Load any creatures associated with the cuboid.
					List<CreatureEntity> creatures = _background_readCreaturesV8(context);
					
					// Now, load any suspended mutations.
					List<ScheduledMutation> pendingMutations = _background_readMutations(context);
					// ... and any periodic mutations.
					Map<BlockAddress, Long> periodicMutations = _background_readPeriodic(buffer);
					
					// Passives added in V9, extracted from empty item inventory slots.
					List<PassiveEntity> convertedPassives = _convertCuboidPre9(env, currentGameMillis, cuboid, address.getBase());
					List<PassiveEntity> passives = (null != convertedPassives)
						? convertedPassives
						: List.of()
					;
					
					// This should be fully read.
					Assert.assertTrue(!buffer.hasRemaining());
					
					// The height map is ephemeral so it is built here.  Note that building this might be somewhat expensive.
					CuboidHeightMap heightMap = HeightMapHelpers.buildHeightMap(cuboid);
					return new SuspendedCuboid<>(cuboid
							, heightMap
							, creatures
							, pendingMutations
							, periodicMutations
							, passives
					);
				};
			}
			else if (VERSION_CUBOID_V6 == version)
			{
				// V6 just adds data so this is to avoid going backward.
				dataReader = () -> {
					CuboidData cuboid = _background_readCuboidPre8(address, context);
					
					// Load any creatures associated with the cuboid.
					List<CreatureEntity> creatures = _background_readCreaturesV8(context);
					
					// Now, load any suspended mutations.
					List<ScheduledMutation> pendingMutations = _background_readMutations(context);
					// ... and any periodic mutations.
					Map<BlockAddress, Long> periodicMutations = _background_readPeriodic(buffer);
					
					// Passives added in V9, extracted from empty item inventory slots.
					List<PassiveEntity> convertedPassives = _convertCuboidPre9(env, currentGameMillis, cuboid, address.getBase());
					List<PassiveEntity> passives = (null != convertedPassives)
						? convertedPassives
						: List.of()
					;
					
					// This should be fully read.
					Assert.assertTrue(!buffer.hasRemaining());
					
					// The height map is ephemeral so it is built here.  Note that building this might be somewhat expensive.
					CuboidHeightMap heightMap = HeightMapHelpers.buildHeightMap(cuboid);
					return new SuspendedCuboid<>(cuboid
							, heightMap
							, creatures
							, pendingMutations
							, periodicMutations
							, passives
					);
				};
			}
			else if (VERSION_CUBOID_V5 == version)
			{
				// V5 requires that the logic aspect be cleared and all switches be turned off.
				dataReader = () -> {
					CuboidData cuboid = _background_readCuboidV5(address, context);
					
					// Load any creatures associated with the cuboid.
					List<CreatureEntity> creatures = _background_readCreaturesV8(context);
					
					// Now, load any suspended mutations.
					List<ScheduledMutation> pendingMutations = _background_readMutations(context);
					// ... and any periodic mutations.
					Map<BlockAddress, Long> periodicMutations = _background_readPeriodic(buffer);
					
					// Passives added in V9, extracted from empty item inventory slots.
					List<PassiveEntity> convertedPassives = _convertCuboidPre9(env, currentGameMillis, cuboid, address.getBase());
					List<PassiveEntity> passives = (null != convertedPassives)
						? convertedPassives
						: List.of()
					;
					
					// This should be fully read.
					Assert.assertTrue(!buffer.hasRemaining());
					
					// The height map is ephemeral so it is built here.  Note that building this might be somewhat expensive.
					CuboidHeightMap heightMap = HeightMapHelpers.buildHeightMap(cuboid);
					return new SuspendedCuboid<>(cuboid
							, heightMap
							, creatures
							, pendingMutations
							, periodicMutations
							, passives
					);
				};
			}
			else if (VERSION_CUBOID_V4 == version)
			{
				// V4 needs to re-write for orientation aspects.
				dataReader = () -> {
					CuboidData cuboid = _background_readCuboidPre5(address, context);
					
					// Load any creatures associated with the cuboid.
					List<CreatureEntity> creatures = _background_readCreaturesV8(context);
					
					// Now, load any suspended mutations.
					List<ScheduledMutation> pendingMutations = _background_readMutations(context);
					// ... and any periodic mutations.
					Map<BlockAddress, Long> periodicMutations = _background_readPeriodic(buffer);
					
					// Passives added in V9, extracted from empty item inventory slots.
					List<PassiveEntity> convertedPassives = _convertCuboidPre9(env, currentGameMillis, cuboid, address.getBase());
					List<PassiveEntity> passives = (null != convertedPassives)
						? convertedPassives
						: List.of()
					;
					
					// This should be fully read.
					Assert.assertTrue(!buffer.hasRemaining());
					
					// The height map is ephemeral so it is built here.  Note that building this might be somewhat expensive.
					CuboidHeightMap heightMap = HeightMapHelpers.buildHeightMap(cuboid);
					return new SuspendedCuboid<>(cuboid
							, heightMap
							, creatures
							, pendingMutations
							, periodicMutations
							, passives
					);
				};
			}
			else if ((VERSION_CUBOID_V2 == version) || (VERSION_CUBOID_V3 == version))
			{
				// V2 is a subset of V3 so do nothing special - just stops old versions from being broken.
				dataReader = () -> {
					CuboidData cuboid = _background_readCuboidPre5(address, context);
					
					// Load any creatures associated with the cuboid.
					List<CreatureEntity> creatures = _background_readCreaturesV8(context);
					
					// Now, load any suspended mutations.
					List<ScheduledMutation> pendingMutations = new ArrayList<>();
					Map<BlockAddress, Long> periodicMutations = new HashMap<>();
					_background_splitMutations(pendingMutations, periodicMutations, context);
					
					// Passives added in V9, extracted from empty item inventory slots.
					List<PassiveEntity> convertedPassives = _convertCuboidPre9(env, currentGameMillis, cuboid, address.getBase());
					List<PassiveEntity> passives = (null != convertedPassives)
						? convertedPassives
						: List.of()
					;
					
					// This should be fully read.
					Assert.assertTrue(!buffer.hasRemaining());
					
					// The height map is ephemeral so it is built here.  Note that building this might be somewhat expensive.
					CuboidHeightMap heightMap = HeightMapHelpers.buildHeightMap(cuboid);
					return new SuspendedCuboid<>(cuboid
							, heightMap
							, creatures
							, pendingMutations
							, periodicMutations
							, passives
					);
				};
			}
			else if (VERSION_CUBOID_V1 == version)
			{
				// The V1 entity is has less data.
				dataReader = () -> {
					CuboidData cuboid = _background_readCuboidPre5(address, context);
					
					// Load any creatures associated with the cuboid.
					int creatureCount = buffer.getInt();
					List<CreatureEntity> creatures = new ArrayList<>();
					for (int i = 0; i < creatureCount; ++i)
					{
						LegacyCreatureEntityV1 legacy = LegacyCreatureEntityV1.load(this.creatureIdAssigner.next(), buffer);
						CreatureEntity entity = legacy.toEntity(currentGameMillis);
						creatures.add(entity);
					}
					
					// Now, load any suspended mutations.
					List<ScheduledMutation> pendingMutations = new ArrayList<>();
					Map<BlockAddress, Long> periodicMutations = new HashMap<>();
					_background_splitMutations(pendingMutations, periodicMutations, context);
					
					// Passives added in V9, extracted from empty item inventory slots.
					List<PassiveEntity> convertedPassives = _convertCuboidPre9(env, currentGameMillis, cuboid, address.getBase());
					List<PassiveEntity> passives = (null != convertedPassives)
						? convertedPassives
						: List.of()
					;
					
					// This should be fully read.
					Assert.assertTrue(!buffer.hasRemaining());
					
					// The height map is ephemeral so it is built here.  Note that building this might be somewhat expensive.
					CuboidHeightMap heightMap = HeightMapHelpers.buildHeightMap(cuboid);
					return new SuspendedCuboid<>(cuboid
							, heightMap
							, creatures
							, pendingMutations
							, periodicMutations
							, passives
					);
				};
			}
			else
			{
				throw new RuntimeException("UNSUPPORTED ENTITY STORAGE VERSION:  " + version);
			}
			
			result = dataReader.get();
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

	private CuboidData _background_readCuboid(CuboidAddress address, DeserializationContext context)
	{
		CuboidData cuboid = CuboidData.createEmpty(address);
		cuboid.deserializeSomeAspectsFully(context, AspectRegistry.ALL_ASPECTS.length);
		return cuboid;
	}

	private CuboidData _background_readCuboidPre11(CuboidAddress address, DeserializationContext context)
	{
		// Prior to version 11, only aspects up to and including SPECIAL_ITEM_SLOT were included.
		int aspectCount = 11;
		
		CuboidData cuboid = CuboidData.createEmpty(address);
		cuboid.deserializeSomeAspectsFully(context, aspectCount);
		return cuboid;
	}

	private CuboidData _background_readCuboidPre8(CuboidAddress address, DeserializationContext context)
	{
		// Prior to version 8, only aspects up to and including MULTI_BLOCK_ROOT were included.
		int aspectCount = 10;
		
		CuboidData cuboid = CuboidData.createEmpty(address);
		cuboid.deserializeSomeAspectsFully(context, aspectCount);
		return cuboid;
	}

	private CuboidData _background_readCuboidPre5(CuboidAddress address, DeserializationContext context)
	{
		CuboidData cuboid = CuboidData.createEmpty(address);
		
		// Prior to version 5, only the aspects up to and including LOGIC were included.
		int aspectCount = 7;
		cuboid.deserializeSomeAspectsFully(context, aspectCount);
		
		// This is now a V5 cuboid so convert it to V6.
		_background_convertCuboid_V5toV6(cuboid);
		return cuboid;
	}

	private CuboidData _background_readCuboidV5(CuboidAddress address, DeserializationContext context)
	{
		// Start by just loaded the data, normally.
		CuboidData cuboid = CuboidData.createEmpty(address);
		cuboid.deserializeSomeAspectsFully(context, AspectRegistry.ALL_ASPECTS.length);
		
		_background_convertCuboid_V5toV6(cuboid);
		
		return cuboid;
	}

	public void _background_convertCuboid_V5toV6(CuboidData cuboid)
	{
		// Version 6 changed the definition of the LOGIC layer which requires that it be cleared and all switches and lamps set to "off".
		IOctree<?>[] rawOctrees = cuboid.unsafeDataAccess();
		rawOctrees[AspectRegistry.LOGIC.index()] = AspectRegistry.LOGIC.emptyTreeSupplier().get();
		
		Environment env = Environment.getShared();
		short switchOffNumber = env.items.getItemById("op.switch").number();
		short switchOnNumber = env.items.getItemById("DEPRECATED.op.switch_on").number();
		short lampOffNumber = env.items.getItemById("op.lamp").number();
		short lampOnNumber = env.items.getItemById("DEPRECATED.op.lamp_on").number();
		short gateNumber = env.items.getItemById("op.gate").number();
		short doorOpenNumber = env.items.getItemById("DEPRECATED.op.door_open").number();
		short doubleDoorNumber = env.items.getItemById("op.double_door_base").number();
		short doubleDoorOpenNumber = env.items.getItemById("DEPRECATED.op.double_door_open_base").number();
		short hopperDownNumber = env.items.getItemById("op.hopper").number();
		short hopperNorthNumber = env.items.getItemById("DEPRECATED.op.hopper_north").number();
		short hopperSouthNumber = env.items.getItemById("DEPRECATED.op.hopper_south").number();
		short hopperEastNumber = env.items.getItemById("DEPRECATED.op.hopper_east").number();
		short hopperWestNumber = env.items.getItemById("DEPRECATED.op.hopper_west").number();
		Set<BlockAddress> switches = new HashSet<>();
		Set<BlockAddress> lamps = new HashSet<>();
		Set<BlockAddress> doors = new HashSet<>();
		Set<BlockAddress> doubleDoors = new HashSet<>();
		Map<BlockAddress, OrientationAspect.Direction> hoppers = new HashMap<>();
		cuboid.walkData(AspectRegistry.BLOCK, new IOctree.IWalkerCallback<>(){
			@Override
			public void visit(BlockAddress base, byte size, Short value)
			{
				if ((switchOnNumber == value)
						|| (lampOnNumber == value)
						|| (doorOpenNumber == value)
						|| (doubleDoorOpenNumber == value)
						|| (hopperDownNumber == value)
						|| (hopperNorthNumber == value)
						|| (hopperSouthNumber == value)
						|| (hopperEastNumber == value)
						|| (hopperWestNumber == value)
				)
				{
					for (byte z = 0; z < size; ++z)
					{
						for (byte y = 0; y < size; ++y)
						{
							for (byte x = 0; x < size; ++x)
							{
								BlockAddress target = base.getRelative(x, y, z);
								if (switchOnNumber == value)
								{
									switches.add(target);
								}
								else if (lampOnNumber == value)
								{
									lamps.add(target);
								}
								else if (doorOpenNumber == value)
								{
									doors.add(target);
								}
								else if (doubleDoorOpenNumber == value)
								{
									doubleDoors.add(target);
								}
								else if (hopperDownNumber == value)
								{
									hoppers.put(target, OrientationAspect.Direction.DOWN);
								}
								else if (hopperNorthNumber == value)
								{
									hoppers.put(target, OrientationAspect.Direction.NORTH);
								}
								else if (hopperSouthNumber == value)
								{
									hoppers.put(target, OrientationAspect.Direction.SOUTH);
								}
								else if (hopperEastNumber == value)
								{
									hoppers.put(target, OrientationAspect.Direction.EAST);
								}
								else if (hopperWestNumber == value)
								{
									hoppers.put(target, OrientationAspect.Direction.WEST);
								}
								else
								{
									// Missing case.
									throw Assert.unreachable();
								}
							}
						}
					}
				}
			}
		}, env.special.AIR.item().number());
		for (BlockAddress block : switches)
		{
			cuboid.setData15(AspectRegistry.BLOCK, block, switchOffNumber);
		}
		for (BlockAddress block : lamps)
		{
			cuboid.setData15(AspectRegistry.BLOCK, block, lampOffNumber);
		}
		for (BlockAddress block : doors)
		{
			cuboid.setData15(AspectRegistry.BLOCK, block, gateNumber);
		}
		for (BlockAddress block : doubleDoors)
		{
			cuboid.setData15(AspectRegistry.BLOCK, block, doubleDoorNumber);
		}
		for (Map.Entry<BlockAddress, OrientationAspect.Direction> elt : hoppers.entrySet())
		{
			BlockAddress address = elt.getKey();
			cuboid.setData15(AspectRegistry.BLOCK, address, hopperDownNumber);
			cuboid.setData7(AspectRegistry.ORIENTATION, address, OrientationAspect.directionToByte(elt.getValue()));
		}
	}

	private List<CreatureEntity> _background_readCreatures(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		int creatureCount = buffer.getInt();
		List<CreatureEntity> creatures = new ArrayList<>();
		for (int i = 0; i < creatureCount; ++i)
		{
			CreatureEntity entity = CodecHelpers.readCreatureEntity(this.creatureIdAssigner.next(), buffer, context.currentGameMillis());
			creatures.add(entity);
		}
		return creatures;
	}

	private List<CreatureEntity> _background_readCreaturesV8(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		int creatureCount = buffer.getInt();
		List<CreatureEntity> creatures = new ArrayList<>();
		for (int i = 0; i < creatureCount; ++i)
		{
			LegacyCreatureEntityV8 legacy = LegacyCreatureEntityV8.load(this.creatureIdAssigner.next(), buffer);
			CreatureEntity entity = legacy.toEntity(context.currentGameMillis());
			creatures.add(entity);
		}
		return creatures;
	}

	private List<PassiveEntity> _background_readPassives(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		int passiveCount = buffer.getInt();
		List<PassiveEntity> passives = new ArrayList<>();
		for (int i = 0; i < passiveCount; ++i)
		{
			PassiveEntity entity = CodecHelpers.readPassiveEntity(this.passiveIdAssigner.next(), context);
			passives.add(entity);
		}
		return passives;
	}

	private void _background_splitMutations(List<ScheduledMutation> out_pendingMutations
			, Map<BlockAddress, Long> out_periodicMutations
			, DeserializationContext context
	)
	{
		for (ScheduledMutation scheduledMutation : _background_readMutations(context))
		{
			IMutationBlock mutation = scheduledMutation.mutation();
			if (mutation instanceof MutationBlockPeriodic)
			{
				BlockAddress block = mutation.getAbsoluteLocation().getBlockAddress();
				out_periodicMutations.put(block, scheduledMutation.millisUntilReady());
			}
			else
			{
				out_pendingMutations.add(scheduledMutation);
			}
		}
	}

	private List<ScheduledMutation> _background_readMutations(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		int mutationCount = buffer.getInt();
		List<ScheduledMutation> suspended = new ArrayList<>();
		for (int i = 0; i < mutationCount; ++i)
		{
			// Read the parts of the suspended data.
			long millisUntilReady = buffer.getLong();
			IMutationBlock mutation = MutationBlockCodec.parseAndSeekContext(context);
			suspended.add(new ScheduledMutation(mutation, millisUntilReady));
		}
		return suspended;
	}

	private Map<BlockAddress, Long> _background_readPeriodic(MappedByteBuffer buffer)
	{
		Map<BlockAddress, Long> periodicMutations = new HashMap<>();
		
		int mutationCount = buffer.getInt();
		for (int i = 0; i < mutationCount; ++i)
		{
			// Read the location.
			byte x = buffer.get();
			byte y = buffer.get();
			byte z = buffer.get();
			BlockAddress block = new BlockAddress(x, y, z);
			
			// Read the millis until ready.
			long millis = buffer.getLong();
			periodicMutations.put(block, millis);
		}
		return periodicMutations;
	}

	private void _background_writeCuboidToDisk(PackagedCuboid data, long gameTimeMillis)
	{
		// Serialize the entire cuboid into memory and write it out.
		// Data goes in the following order:
		// 1) version header
		// 2) cuboid data
		// 3) creatures
		// 4) suspended mutations
		// 5) periodic mutations
		// 6) passives
		Assert.assertTrue(0 == _backround_serializationBuffer.position());
		
		// 1) Write the version header.
		_backround_serializationBuffer.putInt(VERSION_CUBOID);
		
		// 2) Write the raw cuboid data.
		IReadOnlyCuboidData cuboid = data.cuboid();
		Object state = cuboid.serializeResumable(null, _backround_serializationBuffer);
		// We currently assume that we just do the write in a single call.
		Assert.assertTrue(null == state);
		
		// 3) Write the creatures.
		List<CreatureEntity> entities = data.creatures();
		_backround_serializationBuffer.putInt(entities.size());
		for (CreatureEntity entity : entities)
		{
			CodecHelpers.writeCreatureEntity(_backround_serializationBuffer, entity, gameTimeMillis);
		}
		
		// 4) Write suspended mutations.
		List<ScheduledMutation> mutationsToWrite = data.pendingMutations().stream().filter((ScheduledMutation scheduled) -> scheduled.mutation().canSaveToDisk()).toList();
		_backround_serializationBuffer.putInt(mutationsToWrite.size());
		for (ScheduledMutation scheduled : mutationsToWrite)
		{
			// Write the parts of the data.
			_backround_serializationBuffer.putLong(scheduled.millisUntilReady());
			MutationBlockCodec.serializeToBuffer(_backround_serializationBuffer, scheduled.mutation());
		}
		
		// 5) Write periodic mutations.
		Map<BlockAddress, Long> periodic = data.periodicMutationMillis();
		_backround_serializationBuffer.putInt(periodic.size());
		for (Map.Entry<BlockAddress, Long> elt : periodic.entrySet())
		{
			BlockAddress block = elt.getKey();
			long millisUntilReady = elt.getValue();
			
			_backround_serializationBuffer.put(block.x());
			_backround_serializationBuffer.put(block.y());
			_backround_serializationBuffer.put(block.z());
			_backround_serializationBuffer.putLong(millisUntilReady);
		}
		
		// 6) Write passive entities.
		List<PassiveEntity> passives = data.passives();
		_backround_serializationBuffer.putInt(passives.size());
		for (PassiveEntity passive : passives)
		{
			CodecHelpers.writePassiveEntity(_backround_serializationBuffer, passive);
		}
		
		// We are done the write so flip the buffer and write it out.
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

	private SuspendedEntity _background_readEntityFromDisk(int id, long currentGameMillis)
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
			
			// Verify the version is one we can understand.
			int version = buffer.getInt();
			
			// We want to create the decoder context here since we have version data.
			Environment env = Environment.getShared();
			boolean usePreV8NonStackableDecoding = (version <= VERSION_CUBOID_V7);
			DeserializationContext context = new DeserializationContext(env
				, buffer
				, currentGameMillis
				, usePreV8NonStackableDecoding
			);
			
			Supplier<SuspendedEntity> dataReader;
			if ((VERSION_ENTITY == version)
				|| (VERSION_ENTITY_V10 == version)
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
			else if ((VERSION_ENTITY_V2 == version)
					|| (VERSION_ENTITY_V3 == version)
					|| (VERSION_ENTITY_V4 == version)
					|| (VERSION_ENTITY_V5 == version)
					|| (VERSION_ENTITY_V6 == version)
					|| (VERSION_ENTITY_V7 == version)
					|| (VERSION_ENTITY_V8 == version)
					|| (VERSION_ENTITY_V9 == version)
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
			else if (VERSION_ENTITY_V1 == version)
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

	private void _background_writeEntityToDisk(SuspendedEntity suspended)
	{
		// Serialize the entire entity into memory and write it out.
		Assert.assertTrue(0 == _backround_serializationBuffer.position());
		
		// Write the version header.
		_backround_serializationBuffer.putInt(VERSION_ENTITY);
		
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

	// NOTE:  This will modify input and return the extracted passives or null, if there weren't any and input was unchanged.
	private List<PassiveEntity> _convertCuboidPre9(Environment env, long currentGameMillis, CuboidData input, AbsoluteLocation baseLocation)
	{
		List<BlockAddress> toClear = new ArrayList<>();
		List<PassiveEntity> passives = new ArrayList<>();
		input.walkData(AspectRegistry.INVENTORY, new IOctree.IWalkerCallback<Inventory>() {
			@Override
			public void visit(BlockAddress base, byte size, Inventory value)
			{
				short blockNumber = input.getData15(AspectRegistry.BLOCK, base);
				Block block = env.blocks.fromItem(env.items.ITEMS_BY_TYPE[blockNumber]);
				int inventorySize = env.stations.getNormalInventorySize(block);
				if (0 == inventorySize)
				{
					// This must be an empty inventory so convert its contents to passives.
					PassiveType type = PassiveType.ITEM_SLOT;
					EntityLocation passiveLocation = baseLocation.relativeForBlock(base).toEntityLocation();
					EntityLocation passiveVelocity = new EntityLocation(0.0f, 0.0f, 0.0f);
					for (Integer key : value.sortedKeys())
					{
						ItemSlot slot = value.getSlotForKey(key);
						PassiveEntity passive = new PassiveEntity(ResourceLoader.this.passiveIdAssigner.next()
							, type
							, passiveLocation
							, passiveVelocity
							, slot
							, currentGameMillis
						);
						passives.add(passive);
					}
					toClear.add(base);
				}
			}
		}, null);
		
		// Clear out these inventory slots.
		for (BlockAddress address : toClear)
		{
			input.setDataSpecial(AspectRegistry.INVENTORY, address, null);
		}
		return passives.isEmpty()
			? null
			: passives
		;
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
}
