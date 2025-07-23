package com.jeffdisher.october.persistence;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.october.actions.Deprecated_EntityActionAttackEntity;
import com.jeffdisher.october.actions.Deprecated_EntityChangeMove;
import com.jeffdisher.october.actions.EntityChangePeriodic;
import com.jeffdisher.october.actions.MutationEntityStoreToInventory;
import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.LogicAspect;
import com.jeffdisher.october.aspects.OrientationAspect;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.CuboidHeightMap;
import com.jeffdisher.october.data.IObjectCodec;
import com.jeffdisher.october.data.OctreeInflatedByte;
import com.jeffdisher.october.data.OctreeObject;
import com.jeffdisher.october.data.OctreeShort;
import com.jeffdisher.october.logic.CreatureIdAssigner;
import com.jeffdisher.october.logic.HeightMapHelpers;
import com.jeffdisher.october.logic.ScheduledChange;
import com.jeffdisher.october.logic.ScheduledMutation;
import com.jeffdisher.october.mutations.MutationBlockIncrementalBreak;
import com.jeffdisher.october.mutations.MutationBlockOverwriteInternal;
import com.jeffdisher.october.mutations.MutationBlockPeriodic;
import com.jeffdisher.october.mutations.MutationBlockReplace;
import com.jeffdisher.october.mutations.MutationBlockStoreItems;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.persistence.legacy.LegacyCreatureEntityV1;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.CraftOperation;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Difficulty;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.FuelState;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.WorldConfig;
import com.jeffdisher.october.utils.CuboidGenerator;
import com.jeffdisher.october.utils.Encoding;
import com.jeffdisher.october.worldgen.FlatWorldGenerator;
import com.jeffdisher.october.worldgen.IWorldGenerator;


public class TestResourceLoader
{
	@ClassRule
	public static TemporaryFolder DIRECTORY = new TemporaryFolder();
	private static Environment ENV;
	private static Item STONE_ITEM;
	private static Item IRON_SWORD;
	private static Block STONE;
	private static EntityType COW;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		STONE_ITEM = ENV.items.getItemById("op.stone");
		IRON_SWORD = ENV.items.getItemById("op.iron_sword");
		STONE = ENV.blocks.fromItem(STONE_ITEM);
		COW = ENV.creatures.getTypeById("op.cow");
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void empty() throws Throwable
	{
		ResourceLoader loader = new ResourceLoader(DIRECTORY.newFolder(), null, null);
		CuboidAddress address = CuboidAddress.fromInt(1, 0, 0);
		
		// We should see nothing come back, not matter how many times we issue the request.
		Assert.assertNull(_loadSimpleCuboids(loader, List.of(address)));
		Assert.assertNull(_loadSimpleCuboids(loader, List.of(address)));
		Assert.assertNull(_loadSimpleCuboids(loader, List.of(address)));
		loader.shutdown();
	}

	@Test
	public void basic() throws Throwable
	{
		ResourceLoader loader = new ResourceLoader(DIRECTORY.newFolder(), null, null);
		CuboidAddress address = CuboidAddress.fromInt(1, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, STONE);
		loader.preload(cuboid);
		
		// We should see this satisfied, but not on the first call (we will use 10 tries, with yields).
		Collection<CuboidData> results = _loadSimpleCuboids(loader, List.of(address));
		Assert.assertNull(results);
		for (int i = 0; (null == results) && (i < 10); ++i)
		{
			Thread.sleep(10L);
			results = _loadSimpleCuboids(loader, List.of(address));
		}
		Assert.assertEquals(1, results.size());
		loader.shutdown();
	}

	@Test
	public void flatWorld() throws Throwable
	{
		ResourceLoader loader = new ResourceLoader(DIRECTORY.newFolder(), new FlatWorldGenerator(false), null);
		CuboidAddress stoneAddress = CuboidAddress.fromInt(1, 0, -1);
		CuboidAddress airAddress = CuboidAddress.fromInt(1, 0, 0);
		
		// We should see this satisfied, but not on the first call (we will use 10 tries, with yields).
		Collection<CuboidData> results = _loadSimpleCuboids(loader, List.of(stoneAddress, airAddress));
		Assert.assertNull(results);
		List<CuboidData> loaded = new ArrayList<>();
		// Note that this way of checking time is bound to be flaky but we give it a whole second to generate 2 cuboids.
		for (int i = 0; (2 != loaded.size()) && (i < 100); ++i)
		{
			Thread.sleep(10L);
			results = _loadSimpleCuboids(loader, List.of());
			if (null != results)
			{
				loaded.addAll(results);
			}
		}
		Assert.assertEquals(2, loaded.size());
		BlockAddress block = BlockAddress.fromInt(0, 0, 0);
		short test0 = loaded.get(0).getData15(AspectRegistry.BLOCK, block);
		short test1 = loaded.get(1).getData15(AspectRegistry.BLOCK, block);
		Assert.assertTrue((ENV.special.AIR.item().number() == test0) || (ENV.special.AIR.item().number() == test1));
		Assert.assertTrue((STONE_ITEM.number() == test0) || (STONE_ITEM.number() == test1));
		loader.shutdown();
	}

	@Test
	public void writeThenRead() throws Throwable
	{
		File worldDirectory = DIRECTORY.newFolder();
		ResourceLoader loader = new ResourceLoader(worldDirectory, new FlatWorldGenerator(false), null);
		CuboidAddress airAddress = CuboidAddress.fromInt(1, 0, 0);
		
		// We should see this satisfied, but not on the first call (we will use 10 tries, with yields).
		Collection<CuboidData> results = _loadSimpleCuboids(loader, List.of(airAddress));
		Assert.assertNull(results);
		CuboidData loaded = _waitForOne(loader);
		BlockAddress block = BlockAddress.fromInt(0, 0, 0);
		// Modify a block and write this back.
		loaded.setData15(AspectRegistry.BLOCK, block, STONE_ITEM.number());
		loader.writeBackToDisk(List.of(new PackagedCuboid(loaded, List.of(), List.of(), Map.of())), List.of());
		// (the shutdown will wait for the queue to drain)
		loader.shutdown();
		
		// Make sure that we see this written back.
		String fileName = "cuboid_" + airAddress.x() + "_" + airAddress.y() + "_" + airAddress.z() + ".cuboid";
		Assert.assertTrue(new File(worldDirectory, fileName).isFile());
		
		// Now, create a new loader to verify that we can read this.
		loader = new ResourceLoader(worldDirectory, null, null);
		results = _loadSimpleCuboids(loader, List.of(airAddress));
		Assert.assertNull(results);
		loaded = _waitForOne(loader);
		Assert.assertEquals(STONE_ITEM.number(), loaded.getData15(AspectRegistry.BLOCK, block));
		loader.shutdown();
	}

	@Test
	public void entities() throws Throwable
	{
		File worldDirectory = DIRECTORY.newFolder();
		WorldConfig config = new WorldConfig();
		config.worldSpawn = MutableEntity.TESTING_LOCATION.getBlockLocation();
		ResourceLoader loader = new ResourceLoader(worldDirectory, null, config);
		
		// We should see this satisfied, but not on the first call (we will use 10 tries, with yields).
		Assert.assertNull(_loadEntities(loader, List.of(1, 2)));
		List<SuspendedEntity> results = new ArrayList<>();
		for (int i = 0; (results.size() < 2) && (i < 10); ++i)
		{
			Thread.sleep(10L);
			loader.getResultsAndRequestBackgroundLoad(List.of(), results, List.of(), List.of());
		}
		
		// Modify an entity and write these  back.
		Entity original = results.get(0).entity();
		Entity other = results.get(1).entity();
		MutableEntity mutable = MutableEntity.existing(original);
		mutable.newLocation = new EntityLocation(1.0f, 2.0f, 3.0f);
		Entity modified = mutable.freeze();
		loader.writeBackToDisk(List.of(), List.of(new SuspendedEntity(modified, List.of()), new SuspendedEntity(other, List.of())));
		// (the shutdown will wait for the queue to drain)
		loader.shutdown();
		
		// Make sure that we see this written back.
		String fileName = "entity_" + original.id() + ".entity";
		Assert.assertTrue(new File(worldDirectory, fileName).isFile());
		String otherName = "entity_" + other.id() + ".entity";
		Assert.assertTrue(new File(worldDirectory, otherName).isFile());
		
		// Now, create a new loader to verify that we can read this.
		loader = new ResourceLoader(worldDirectory, null, null);
		Assert.assertNull(_loadEntities(loader, List.of(1, 2)));
		results = new ArrayList<>();
		for (int i = 0; (results.size() < 2) && (i < 10); ++i)
		{
			Thread.sleep(10L);
			loader.getResultsAndRequestBackgroundLoad(List.of(), results, List.of(), List.of());
		}
		Entity resolved = (original.id() == results.get(0).entity().id())
				? results.get(0).entity()
				: results.get(1).entity()
		;
		Assert.assertEquals(modified.location(), resolved.location());
		loader.shutdown();
	}

	@Test
	public void writeAndReadSuspendedCuboid() throws Throwable
	{
		File worldDirectory = DIRECTORY.newFolder();
		ResourceLoader loader = new ResourceLoader(worldDirectory, new FlatWorldGenerator(false), null);
		CuboidAddress airAddress = CuboidAddress.fromInt(1, 0, 0);
		
		// We should see this satisfied, but not on the first call (we will use 10 tries, with yields).
		Collection<CuboidData> results = _loadSimpleCuboids(loader, List.of(airAddress));
		Assert.assertNull(results);
		CuboidData loaded = _waitForOne(loader);
		// Create a mutation which targets this and save it back with the cuboid.
		MutationBlockOverwriteInternal mutation = new MutationBlockOverwriteInternal(new AbsoluteLocation(32, 0, 0), STONE);
		loader.writeBackToDisk(List.of(new PackagedCuboid(loaded, List.of(), List.of(new ScheduledMutation(mutation, 0L)), Map.of())), List.of());
		// (the shutdown will wait for the queue to drain)
		loader.shutdown();
		
		// Make sure that we see this written back.
		String fileName = "cuboid_" + airAddress.x() + "_" + airAddress.y() + "_" + airAddress.z() + ".cuboid";
		Assert.assertTrue(new File(worldDirectory, fileName).isFile());
		
		// Now, create a new loader to verify that we can read this.
		loader = new ResourceLoader(worldDirectory, null, null);
		SuspendedCuboid<CuboidData> suspended = _loadOneSuspended(loader, airAddress);
		Assert.assertEquals(airAddress, suspended.cuboid().getCuboidAddress());
		Assert.assertEquals(1, suspended.pendingMutations().size());
		Assert.assertTrue(suspended.pendingMutations().get(0).mutation() instanceof MutationBlockOverwriteInternal);
		Assert.assertEquals(0, suspended.periodicMutationMillis().size());
		loader.shutdown();
	}

	@Test
	public void writeAndReadSuspendedEntity() throws Throwable
	{
		File worldDirectory = DIRECTORY.newFolder();
		WorldConfig config = new WorldConfig();
		config.worldSpawn = MutableEntity.TESTING_LOCATION.getBlockLocation();
		ResourceLoader loader = new ResourceLoader(worldDirectory, null, config);
		int entityId = 1;
		
		List<SuspendedEntity> results = new ArrayList<>();
		loader.getResultsAndRequestBackgroundLoad(List.of(), results, List.of(), List.of(entityId));
		Assert.assertTrue(results.isEmpty());
		for (int i = 0; (results.size() < 1) && (i < 10); ++i)
		{
			Thread.sleep(10L);
			loader.getResultsAndRequestBackgroundLoad(List.of(), results, List.of(), List.of());
		}
		
		// Verify that this is the default.
		Assert.assertTrue(results.get(0).changes().get(0).change() instanceof EntityChangePeriodic);
		Assert.assertEquals(MutableEntity.TESTING_LOCATION, results.get(0).entity().location());
		
		// Modify the entity and create a mutation to store with it.
		MutableEntity mutable = MutableEntity.existing(results.get(0).entity());
		EntityLocation location = mutable.newLocation;
		EntityLocation newLocation = new EntityLocation(2.0f * location.x(), 3.0f * location.y(), 4.0f * location.z());
		mutable.newLocation = newLocation;
		MutationEntityStoreToInventory mutation = new MutationEntityStoreToInventory(new Items(STONE.item(), 2), null);
		
		loader.writeBackToDisk(List.of(), List.of(new SuspendedEntity(mutable.freeze(), List.of(new ScheduledChange(mutation, 0L)))));
		// (the shutdown will wait for the queue to drain)
		loader.shutdown();
		
		// Make sure that we see this written back.
		String fileName = "entity_" + entityId + ".entity";
		Assert.assertTrue(new File(worldDirectory, fileName).isFile());
		
		// Now, create a new loader to verify that we can read this.
		loader = new ResourceLoader(worldDirectory, null, null);
		results = new ArrayList<>();
		loader.getResultsAndRequestBackgroundLoad(List.of(), results, List.of(), List.of(entityId));
		Assert.assertTrue(results.isEmpty());
		for (int i = 0; (results.size() < 1) && (i < 10); ++i)
		{
			Thread.sleep(10L);
			loader.getResultsAndRequestBackgroundLoad(List.of(), results, List.of(), List.of());
		}
		SuspendedEntity suspended = results.get(0);
		Assert.assertEquals(newLocation, suspended.entity().location());
		Assert.assertEquals(1, suspended.changes().size());
		Assert.assertTrue(suspended.changes().get(0).change() instanceof MutationEntityStoreToInventory);
		loader.shutdown();
	}

	@Test
	public void overwiteFile() throws Throwable
	{
		File worldDirectory = DIRECTORY.newFolder();
		ResourceLoader loader = new ResourceLoader(worldDirectory, new FlatWorldGenerator(false), null);
		CuboidAddress airAddress = CuboidAddress.fromInt(1, 0, 0);
		
		// We should see this satisfied, but not on the first call (we will use 10 tries, with yields).
		Collection<CuboidData> results = _loadSimpleCuboids(loader, List.of(airAddress));
		Assert.assertNull(results);
		CuboidData loaded = _waitForOne(loader);
		// Create a mutation which targets this and save it back with the cuboid.
		MutationBlockOverwriteInternal mutation = new MutationBlockOverwriteInternal(new AbsoluteLocation(32, 0, 0), STONE);
		loader.writeBackToDisk(List.of(new PackagedCuboid(loaded, List.of(), List.of(new ScheduledMutation(mutation, 0L)), Map.of())), List.of());
		// (the shutdown will wait for the queue to drain)
		loader.shutdown();
		
		// Make sure that we see this written back.
		File cuboidFile = new File(worldDirectory, "cuboid_" + airAddress.x() + "_" + airAddress.y() + "_" + airAddress.z() + ".cuboid");
		Assert.assertTrue(cuboidFile.isFile());
		// Experimentally, we know that this is 63 bytes.
		Assert.assertEquals(63L, cuboidFile.length());
		
		// Now, create a new loader, load, and resave this.
		loader = new ResourceLoader(worldDirectory, null, null);
		SuspendedCuboid<CuboidData> suspended = _loadOneSuspended(loader, airAddress);
		Assert.assertEquals(airAddress, suspended.cuboid().getCuboidAddress());
		Assert.assertEquals(1, suspended.pendingMutations().size());
		Assert.assertTrue(suspended.pendingMutations().get(0).mutation() instanceof MutationBlockOverwriteInternal);
		loader.writeBackToDisk(List.of(new PackagedCuboid(suspended.cuboid(), List.of(), List.of(), Map.of())), List.of());
		Assert.assertEquals(0, suspended.periodicMutationMillis().size());
		loader.shutdown();
		
		// Verify that the file has been truncated.
		Assert.assertTrue(cuboidFile.isFile());
		// Experimentally, we know that this is 40 bytes.
		Assert.assertEquals(40L, cuboidFile.length());
		
		// Load it again and verify that the mutation is missing and we parsed without issue.
		loader = new ResourceLoader(worldDirectory, null, null);
		suspended = _loadOneSuspended(loader, airAddress);
		Assert.assertEquals(airAddress, suspended.cuboid().getCuboidAddress());
		Assert.assertEquals(0, suspended.pendingMutations().size());
		Assert.assertEquals(0, suspended.periodicMutationMillis().size());
		loader.shutdown();
	}

	@Test
	public void verifyMutationsFromGeneration() throws Throwable
	{
		File worldDirectory = DIRECTORY.newFolder();
		MutationBlockOverwriteInternal test = new MutationBlockOverwriteInternal(new AbsoluteLocation(1, 2, 3), STONE);
		ResourceLoader loader = new ResourceLoader(worldDirectory, new IWorldGenerator() {
			@Override
			public SuspendedCuboid<CuboidData> generateCuboid(CreatureIdAssigner creatureIdAssigner, CuboidAddress address)
			{
				CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
				return new SuspendedCuboid<CuboidData>(cuboid
						, HeightMapHelpers.buildHeightMap(cuboid)
						, List.of()
						, List.of(
								new ScheduledMutation(test, 100L)
						)
						, Map.of()
				);
			}
			@Override
			public EntityLocation getDefaultSpawnLocation()
			{
				throw new AssertionError("Not in test");
			}
		}, null);
		List<SuspendedCuboid<CuboidData>> out_loadedCuboids = new ArrayList<>();
		loader.getResultsAndRequestBackgroundLoad(out_loadedCuboids, List.of(), List.of(CuboidAddress.fromInt(1, 2, 3)), List.of());
		Assert.assertTrue(out_loadedCuboids.isEmpty());
		for (int i = 0; (out_loadedCuboids.isEmpty()) && (i < 10); ++i)
		{
			Thread.sleep(10L);
			loader.getResultsAndRequestBackgroundLoad(out_loadedCuboids, List.of(), List.of(), List.of());
		}
		Assert.assertEquals(1, out_loadedCuboids.size());
		SuspendedCuboid<CuboidData> result = out_loadedCuboids.get(0);
		List<ScheduledMutation> mutations = result.pendingMutations();
		Assert.assertEquals(1, mutations.size());
		Assert.assertEquals(0, result.periodicMutationMillis().size());
		Assert.assertTrue(test == mutations.get(0).mutation());
		loader.shutdown();
	}

	@Test
	public void writeAndReadSuspendedWithDroppedActions() throws Throwable
	{
		// We want to store a cuboid and entity with 2 suspended actions each and verify that the non-persistable one is dropped on reload.
		File worldDirectory = DIRECTORY.newFolder();
		WorldConfig config = new WorldConfig();
		config.worldSpawn = MutableEntity.TESTING_LOCATION.getBlockLocation();
		ResourceLoader loader = new ResourceLoader(worldDirectory, new FlatWorldGenerator(false), config);
		CuboidAddress airAddress = CuboidAddress.fromInt(1, 0, 0);
		int entityId = 1;
		int targetId = 2;
		
		List<SuspendedCuboid<CuboidData>> cuboids = new ArrayList<>();
		List<SuspendedEntity> entities = new ArrayList<>();
		loader.getResultsAndRequestBackgroundLoad(cuboids, entities, List.of(airAddress), List.of(entityId));
		for (int i = 0; ((cuboids.size() < 1) || (entities.size() < 1)) && (i < 10); ++i)
		{
			Thread.sleep(10L);
			loader.getResultsAndRequestBackgroundLoad(cuboids, entities, List.of(), List.of());
		}
		Assert.assertEquals(1, cuboids.size());
		Assert.assertEquals(1, entities.size());
		
		// Verify the height map.
		CuboidHeightMap height = cuboids.get(0).heightMap();
		for (int x = 0; x < Encoding.CUBOID_EDGE_SIZE; ++x)
		{
			for (int y = 0; y < Encoding.CUBOID_EDGE_SIZE; ++y)
			{
				Assert.assertEquals(CuboidHeightMap.UNKNOWN_HEIGHT, height.getHightestSolidBlock(x, y));
			}
		}
		
		// Create the entity changes we want.
		MutationEntityStoreToInventory persistentChange  = new MutationEntityStoreToInventory(new Items(STONE.item(), 2), null);
		// (note that we still use this deprecated action since it is convenient but should be changed in the future)
		@SuppressWarnings("deprecation")
		Deprecated_EntityActionAttackEntity ephemeralChange = new Deprecated_EntityActionAttackEntity(targetId);
		
		// Create the cuboid mutations we want.
		Block waterSource = ENV.blocks.fromItem(ENV.items.getItemById("op.water_source"));
		MutationBlockReplace persistentMutation = new MutationBlockReplace(airAddress.getBase(), ENV.special.AIR, waterSource);
		MutationBlockIncrementalBreak ephemeralMutation = new MutationBlockIncrementalBreak(airAddress.getBase(), (short)10, targetId);
		
		// Re-save these to disk.
		loader.writeBackToDisk(List.of(
				new PackagedCuboid(cuboids.get(0).cuboid(), List.of(), List.of(
						new ScheduledMutation(ephemeralMutation, 0L),
						new ScheduledMutation(persistentMutation, 0L)
				), Map.of())
		), List.of(
				new SuspendedEntity(entities.get(0).entity(), List.of(
						new ScheduledChange(persistentChange, 0L),
						new ScheduledChange(ephemeralChange, 0L)
				))
		));
		
		// Load them back.
		cuboids = new ArrayList<>();
		entities = new ArrayList<>();
		loader.getResultsAndRequestBackgroundLoad(cuboids, entities, List.of(airAddress), List.of(entityId));
		for (int i = 0; ((cuboids.size() < 1) || (entities.size() < 1)) && (i < 10); ++i)
		{
			Thread.sleep(10L);
			loader.getResultsAndRequestBackgroundLoad(cuboids, entities, List.of(), List.of());
		}
		Assert.assertEquals(1, cuboids.size());
		Assert.assertEquals(1, entities.size());
		
		// Verify that we only see the persistent change and mutation.
		Assert.assertEquals(1, cuboids.get(0).pendingMutations().size());
		Assert.assertEquals(0, cuboids.get(0).periodicMutationMillis().size());
		Assert.assertEquals(1, entities.get(0).changes().size());
		loader.shutdown();
	}

	@Test
	public void writeAndReadCuboidAndCreatures() throws Throwable
	{
		File worldDirectory = DIRECTORY.newFolder();
		ResourceLoader loader = new ResourceLoader(worldDirectory, new FlatWorldGenerator(false), null);
		CuboidAddress airAddress = CuboidAddress.fromInt(3, -5, 0);
		
		// We will request that this be generated and verify that there is an entity.
		Collection<SuspendedCuboid<CuboidData>> results = new ArrayList<>();
		loader.getResultsAndRequestBackgroundLoad(results, List.of(), List.of(airAddress), List.of());
		for (int i = 0; (i < 10) && results.isEmpty(); ++i)
		{
			Thread.sleep(10L);
			loader.getResultsAndRequestBackgroundLoad(results, List.of(), List.of(), List.of());
		}
		
		// We expect a result with a cow of ID -1 and an orc with ID -2.
		Assert.assertEquals(1, results.size());
		SuspendedCuboid<CuboidData> generated = results.iterator().next();
		Assert.assertEquals(2, generated.creatures().size());
		Assert.assertEquals(-1, generated.creatures().get(0).id());
		Assert.assertEquals(-2, generated.creatures().get(1).id());
		Assert.assertEquals(0, generated.pendingMutations().size());
		Assert.assertEquals(0, generated.periodicMutationMillis().size());
		
		// Save this back.
		Collection<PackagedCuboid> toWrite = List.of(new PackagedCuboid(generated.cuboid(), generated.creatures(), generated.pendingMutations(), generated.periodicMutationMillis()));
		loader.writeBackToDisk(toWrite, List.of());
		
		// Now, re-load this within the same loader and observe that the ID has updated.
		results = new ArrayList<>();
		loader.getResultsAndRequestBackgroundLoad(results, List.of(), List.of(airAddress), List.of());
		for (int i = 0; (i < 10) && results.isEmpty(); ++i)
		{
			Thread.sleep(10L);
			loader.getResultsAndRequestBackgroundLoad(results, List.of(), List.of(), List.of());
		}
		
		// We expect a result with a cow of ID -3 and an orc with ID -4.
		Assert.assertEquals(1, results.size());
		SuspendedCuboid<CuboidData> loaded = results.iterator().next();
		Assert.assertEquals(2, loaded.creatures().size());
		Assert.assertEquals(-3, loaded.creatures().get(0).id());
		Assert.assertEquals(-4, loaded.creatures().get(1).id());
		Assert.assertEquals(0, loaded.pendingMutations().size());
		Assert.assertEquals(0, loaded.periodicMutationMillis().size());
		loader.shutdown();
	}

	@Test
	public void config() throws Throwable
	{
		File resourceDirectory = DIRECTORY.newFolder();
		ResourceLoader loader = new ResourceLoader(resourceDirectory, null, null);
		loader.storeWorldConfig(new WorldConfig());
		File configFile = new File(resourceDirectory, "config.tablist");
		String rawData = Files.readString(configFile.toPath());
		Assert.assertTrue(rawData.contains("difficulty\tHOSTILE\n"));
		Assert.assertTrue(rawData.contains("ticks_per_day\t12000\n"));
		Assert.assertTrue(rawData.contains("should_synthesize_updates_on_load\tfalse\n"));
		Assert.assertTrue(rawData.contains("client_view_distance_maximum\t2\n"));
		Assert.assertTrue(rawData.contains("server_name\tOctoberProject Server\n"));
		Assert.assertTrue(rawData.contains("default_player_mode\tSURVIVAL\n"));
		String fileToWrite = "difficulty\tPEACEFUL\n"
				+ "basic_seed\t-465342154\n"
				+ "world_spawn\t5,6,7\n"
				+ "ticks_per_day\t2000\n"
				+ "world_generator_name\tBASIC\n"
				+ "should_synthesize_updates_on_load\ttrue\n"
				+ "client_view_distance_maximum\t5\n"
		;
		Files.writeString(configFile.toPath(), fileToWrite);
		loader = new ResourceLoader(resourceDirectory, null, null);
		WorldConfig config = new WorldConfig();
		ResourceLoader.populateWorldConfig(resourceDirectory, config);
		Assert.assertEquals(Difficulty.PEACEFUL, config.difficulty);
		Assert.assertEquals(-465342154, config.basicSeed);
		Assert.assertEquals(new AbsoluteLocation(5, 6, 7), config.worldSpawn);
		Assert.assertEquals(2000, config.ticksPerDay);
		Assert.assertEquals(WorldConfig.WorldGeneratorName.BASIC, config.worldGeneratorName);
		Assert.assertTrue(config.shouldSynthesizeUpdatesOnLoad);
		Assert.assertEquals(WorldConfig.MAX_CLIENT_VIEW_DISTANCE_MAXIMUM, config.clientViewDistanceMaximum);
		Assert.assertEquals("OctoberProject Server", config.serverName);
		Assert.assertEquals(WorldConfig.DefaultPlayerMode.SURVIVAL, config.defaultPlayerMode);
		loader.shutdown();
	}

	@Test
	public void writeAndReadEntityV1() throws Throwable
	{
		File worldDirectory = DIRECTORY.newFolder();
		
		// This is a test of our ability to read the V1 entity data.  We manually write a file and then attempt to read it, verifying the result is sensible.
		// Note that, since we no longer want to support writing old versions, we use a capture of an old version.
		byte[] version1SerializedData = new byte[] {0, 0, 0, 1, 0, 0, 0, 1, 1, 63, -128, 0, 0, 64, 0, 0, 0, 64, 64, 0, 0, 64, -128, 0, 0, 64, -96, 0, 0, 64, -64, 0, 0, 0, 0, 0, 20, 2, 0, 0, 0, 1, 0, 1, 0, 0, 0, 1, 0, 0, 0, 2, 0, 28, 0, 0, 0, 5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, -1, -1, -1, -1, -1, -1, -1, -1, 0, 0, 0, 0, 0, 0, 0, 0, 50, 12, 98, 0, 0, 1, -12, -64, -96, 0, 0, -64, -64, 0, 0, -64, -32, 0, 0, 0, 0, 0, 0, 0, 0, 1, -12, 1, 0, 0, 0, 0, 0, 0, 0, 0, 63, -128, 0, 0, 1};
		int id = 1;
		EntityLocation location = new EntityLocation(1.0f, 2.0f, 3.0f);

		// Write the file.
		String fileName = "entity_" + id + ".entity";
		try (
				RandomAccessFile aFile = new RandomAccessFile(new File(worldDirectory, fileName), "rw");
				FileChannel outChannel = aFile.getChannel();
		)
		{
			ByteBuffer buffer = ByteBuffer.wrap(version1SerializedData);
			int written = outChannel.write(buffer);
			outChannel.truncate((long)written);
		}
		Assert.assertTrue(new File(worldDirectory, fileName).isFile());
		
		// Now, read the data and verify that it is correct.
		WorldConfig config = new WorldConfig();
		config.worldSpawn = MutableEntity.TESTING_LOCATION.getBlockLocation();
		ResourceLoader loader = new ResourceLoader(worldDirectory, null, config);
		List<SuspendedEntity> results = new ArrayList<>();
		loader.getResultsAndRequestBackgroundLoad(List.of(), results, List.of(), List.of(id));
		Assert.assertTrue(results.isEmpty());
		for (int i = 0; (results.size() < 1) && (i < 10); ++i)
		{
			Thread.sleep(10L);
			loader.getResultsAndRequestBackgroundLoad(List.of(), results, List.of(), List.of());
		}
		
		// Verify that this matches.
		Assert.assertEquals(1, results.size());
		Entity entity = results.get(0).entity();
		Assert.assertEquals(location, entity.location());
		Assert.assertEquals(BodyPart.values().length, entity.armourSlots().length);
		Assert.assertNull(entity.localCraftOperation());
		Assert.assertEquals((byte)0, entity.yaw());
		List<ScheduledChange> changes = results.get(0).changes();
		Assert.assertEquals(1, changes.size());
		Assert.assertTrue(changes.get(0).change() instanceof Deprecated_EntityChangeMove);
		
		loader.shutdown();
	}

	@SuppressWarnings("unchecked")
	@Test
	public void writeAndReadCuboidV1() throws Throwable
	{
		File worldDirectory = DIRECTORY.newFolder();
		CuboidAddress address = CuboidAddress.fromInt(3, -5, 0);
		
		// This is a test of our ability to read the V1 cuboid data.  We manually write a file and then attempt to read it, verifying the result is sensible.
		OctreeShort blockData = OctreeShort.create(STONE.item().number());
		OctreeObject<Inventory> inventoryData = OctreeObject.create();
		OctreeShort damageData = OctreeShort.create((short) 0);
		OctreeObject<CraftOperation> craftingData = OctreeObject.create();
		OctreeObject<FuelState> fuelledData = OctreeObject.create();
		OctreeInflatedByte lightData = OctreeInflatedByte.empty();
		OctreeInflatedByte logicData = OctreeInflatedByte.empty();
		
		int id = -5;
		EntityType type = COW;
		EntityLocation location = new EntityLocation(1.0f, 2.0f, 3.0f);
		EntityLocation velocity = new EntityLocation(4.0f, 5.0f, 6.0f);
		byte health = 50;
		byte breath = 98;
		LegacyCreatureEntityV1 legacy = new LegacyCreatureEntityV1(id
				, type
				, location
				, velocity
				, health
				, breath
		);
		MutationBlockStoreItems store = new MutationBlockStoreItems(address.getBase(), new Items(STONE_ITEM, 2), null, Inventory.INVENTORY_ASPECT_INVENTORY);
		
		// Serialize to buffer.
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		buffer.putInt(ResourceLoader.VERSION_CUBOID_V1);
		
		// We want to manually write the cuboid in V1 shape.
		Assert.assertNull(blockData.serializeResumable(null, buffer, (IObjectCodec<Short>) AspectRegistry.ALL_ASPECTS[0].codec()));
		Assert.assertNull(inventoryData.serializeResumable(null, buffer, (IObjectCodec<Inventory>) AspectRegistry.ALL_ASPECTS[1].codec()));
		Assert.assertNull(damageData.serializeResumable(null, buffer, (IObjectCodec<Short>) AspectRegistry.ALL_ASPECTS[2].codec()));
		Assert.assertNull(craftingData.serializeResumable(null, buffer, (IObjectCodec<CraftOperation>) AspectRegistry.ALL_ASPECTS[3].codec()));
		Assert.assertNull(fuelledData.serializeResumable(null, buffer, (IObjectCodec<FuelState>) AspectRegistry.ALL_ASPECTS[4].codec()));
		Assert.assertNull(lightData.serializeResumable(null, buffer, (IObjectCodec<Byte>) AspectRegistry.ALL_ASPECTS[5].codec()));
		Assert.assertNull(logicData.serializeResumable(null, buffer, (IObjectCodec<Byte>) AspectRegistry.ALL_ASPECTS[6].codec()));
		
		// Now, proceed to write creatures and mutations.
		buffer.putInt(1);
		legacy.test_writeToBuffer(buffer);
		buffer.putInt(1);
		buffer.putLong(500L);
		MutationBlockCodec.serializeToBuffer(buffer, store);
		buffer.flip();
		
		// Write the file.
		String fileName = "cuboid_" + address.x() + "_" + address.y() + "_" + address.z() + ".cuboid";
		try (
				RandomAccessFile aFile = new RandomAccessFile(new File(worldDirectory, fileName), "rw");
				FileChannel outChannel = aFile.getChannel();
		)
		{
			int written = outChannel.write(buffer);
			outChannel.truncate((long)written);
		}
		Assert.assertTrue(new File(worldDirectory, fileName).isFile());
		
		// Now, read the data and verify that it is correct.
		WorldConfig config = new WorldConfig();
		config.worldSpawn = MutableEntity.TESTING_LOCATION.getBlockLocation();
		ResourceLoader loader = new ResourceLoader(worldDirectory, null, config);
		List<SuspendedCuboid<CuboidData>> results = new ArrayList<>();
		loader.getResultsAndRequestBackgroundLoad(results, List.of(), List.of(address), List.of());
		for (int i = 0; (i < 10) && results.isEmpty(); ++i)
		{
			Thread.sleep(10L);
			loader.getResultsAndRequestBackgroundLoad(results, List.of(), List.of(), List.of());
		}
		
		// We expect a result with a cow of ID -1 (renumbered on load).
		Assert.assertEquals(1, results.size());
		Assert.assertNotNull(results.get(0).cuboid());
		Assert.assertNotNull(results.get(0).heightMap());
		List<CreatureEntity> creatures = results.get(0).creatures();
		Assert.assertEquals(1, creatures.size());
		List<ScheduledMutation> pendingMutations = results.get(0).pendingMutations();
		Assert.assertEquals(1, pendingMutations.size());
		Map<BlockAddress, Long> periodicMutationMillis = results.get(0).periodicMutationMillis();
		Assert.assertEquals(0, periodicMutationMillis.size());
		
		CreatureEntity entity = creatures.get(0);
		Assert.assertEquals(-1, entity.id());
		Assert.assertEquals((byte)0, entity.yaw());
		
		loader.shutdown();
	}

	@SuppressWarnings("unchecked")
	@Test
	public void writeAndReadCuboidV3() throws Throwable
	{
		File worldDirectory = DIRECTORY.newFolder();
		CuboidAddress address = CuboidAddress.fromInt(3, -5, 0);
		BlockAddress periodicBlock = BlockAddress.fromInt(1, 2, 3);
		AbsoluteLocation periodicLocation = address.getBase().relativeForBlock(periodicBlock);
		
		// This is a test of our ability to read the V3 cuboid data, showing the different mutation types.  We manually write a file and then attempt to read it, verifying the result is sensible.
		OctreeShort blockData = OctreeShort.create(STONE.item().number());
		OctreeObject<Inventory> inventoryData = OctreeObject.create();
		OctreeShort damageData = OctreeShort.create((short) 0);
		OctreeObject<CraftOperation> craftingData = OctreeObject.create();
		OctreeObject<FuelState> fuelledData = OctreeObject.create();
		OctreeInflatedByte lightData = OctreeInflatedByte.empty();
		OctreeInflatedByte logicData = OctreeInflatedByte.empty();
		
		// We will save a normal store mutation (immediate) and a periodic mutation (delayed) and verify that both are read correctly.
		MutationBlockStoreItems store = new MutationBlockStoreItems(address.getBase(), new Items(STONE_ITEM, 2), null, Inventory.INVENTORY_ASPECT_INVENTORY);
		MutationBlockPeriodic periodic = new MutationBlockPeriodic(periodicLocation);
		
		// Serialize to buffer.
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		buffer.putInt(ResourceLoader.VERSION_CUBOID_V3);
		
		// We want to manually write the cuboid in V3 shape.
		Assert.assertNull(blockData.serializeResumable(null, buffer, (IObjectCodec<Short>) AspectRegistry.ALL_ASPECTS[0].codec()));
		Assert.assertNull(inventoryData.serializeResumable(null, buffer, (IObjectCodec<Inventory>) AspectRegistry.ALL_ASPECTS[1].codec()));
		Assert.assertNull(damageData.serializeResumable(null, buffer, (IObjectCodec<Short>) AspectRegistry.ALL_ASPECTS[2].codec()));
		Assert.assertNull(craftingData.serializeResumable(null, buffer, (IObjectCodec<CraftOperation>) AspectRegistry.ALL_ASPECTS[3].codec()));
		Assert.assertNull(fuelledData.serializeResumable(null, buffer, (IObjectCodec<FuelState>) AspectRegistry.ALL_ASPECTS[4].codec()));
		Assert.assertNull(lightData.serializeResumable(null, buffer, (IObjectCodec<Byte>) AspectRegistry.ALL_ASPECTS[5].codec()));
		Assert.assertNull(logicData.serializeResumable(null, buffer, (IObjectCodec<Byte>) AspectRegistry.ALL_ASPECTS[6].codec()));
		
		// 0 creatures.
		buffer.putInt(0);
		
		// Write the scheduled and periodic mutations.
		long periodicDelay = 1000L;
		buffer.putInt(2);
		buffer.putLong(0L);
		MutationBlockCodec.serializeToBuffer(buffer, store);
		buffer.putLong(periodicDelay);
		// Note that we no longer support serialization of periodic mutations so we inline that logic here.
		buffer.put((byte) periodic.getType().ordinal());
		CodecHelpers.writeAbsoluteLocation(buffer, periodic.getAbsoluteLocation());
		buffer.flip();
		
		// Write the file.
		String fileName = "cuboid_" + address.x() + "_" + address.y() + "_" + address.z() + ".cuboid";
		try (
				RandomAccessFile aFile = new RandomAccessFile(new File(worldDirectory, fileName), "rw");
				FileChannel outChannel = aFile.getChannel();
		)
		{
			int written = outChannel.write(buffer);
			outChannel.truncate((long)written);
		}
		Assert.assertTrue(new File(worldDirectory, fileName).isFile());
		
		// Now, read the data and verify that it is correct.
		WorldConfig config = new WorldConfig();
		config.worldSpawn = MutableEntity.TESTING_LOCATION.getBlockLocation();
		ResourceLoader loader = new ResourceLoader(worldDirectory, null, config);
		List<SuspendedCuboid<CuboidData>> results = new ArrayList<>();
		loader.getResultsAndRequestBackgroundLoad(results, List.of(), List.of(address), List.of());
		for (int i = 0; (i < 10) && results.isEmpty(); ++i)
		{
			Thread.sleep(10L);
			loader.getResultsAndRequestBackgroundLoad(results, List.of(), List.of(), List.of());
		}
		Assert.assertEquals(1, results.size());
		SuspendedCuboid<CuboidData> result = results.get(0);
		
		Assert.assertNotNull(result.cuboid());
		Assert.assertNotNull(result.heightMap());
		Assert.assertEquals(0, result.creatures().size());
		List<ScheduledMutation> pendingMutations = result.pendingMutations();
		Assert.assertEquals(1, pendingMutations.size());
		Assert.assertEquals(0L, pendingMutations.get(0).millisUntilReady());
		Assert.assertTrue(pendingMutations.get(0).mutation() instanceof MutationBlockStoreItems);
		Map<BlockAddress, Long> periodicMutationMillis = result.periodicMutationMillis();
		Assert.assertEquals(1, periodicMutationMillis.size());
		Assert.assertEquals(periodicDelay, periodicMutationMillis.get(periodicBlock).longValue());
		
		loader.shutdown();
	}

	@Test
	public void readCuboidV5() throws Throwable
	{
		File worldDirectory = DIRECTORY.newFolder();
		CuboidAddress address = CuboidAddress.fromInt(3, -5, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		
		// We want to show switches and logic being disabled after load so enable them, now.
		// (we can build this using current helpers and just save it out)
		short switchOnNumber = ENV.items.getItemById("DEPRECATED.op.switch_on").number();
		short switchOffNumber = ENV.items.getItemById("op.switch").number();
		short wireNumber = ENV.items.getItemById("op.logic_wire").number();
		short lampOnNumber = ENV.items.getItemById("DEPRECATED.op.lamp_on").number();
		short lampOffNumber = ENV.items.getItemById("op.lamp").number();
		AbsoluteLocation switchOnLocation = address.getBase().getRelative(2, 3, 4);
		AbsoluteLocation wireLocation = switchOnLocation.getRelative(1, 0, 0);
		AbsoluteLocation lampOnLocation = wireLocation.getRelative(1, 0, 0);
		cuboid.setData15(AspectRegistry.BLOCK, switchOnLocation.getBlockAddress(), switchOnNumber);
		cuboid.setData7(AspectRegistry.LOGIC, switchOnLocation.getBlockAddress(), LogicAspect.MAX_LEVEL);
		cuboid.setData15(AspectRegistry.BLOCK, wireLocation.getBlockAddress(), wireNumber);
		cuboid.setData7(AspectRegistry.LOGIC, wireLocation.getBlockAddress(), (byte)(LogicAspect.MAX_LEVEL - 1));
		cuboid.setData15(AspectRegistry.BLOCK, lampOnLocation.getBlockAddress(), lampOnNumber);
		
		// We also want to show how doors work in this change (since they were also changed due to the "active" flag).
		short dirtNumber = ENV.items.getItemById("op.dirt").number();
		short doorOpenNumber = ENV.items.getItemById("DEPRECATED.op.door_open").number();
		short doorClosedNumber = ENV.items.getItemById("op.door").number();
		short multiDoorOpenNumber = ENV.items.getItemById("DEPRECATED.op.double_door_open_base").number();
		short multiDoorClosedNumber = ENV.items.getItemById("op.double_door_base").number();
		AbsoluteLocation doorLocation = switchOnLocation.getRelative(0, 2, 0);
		AbsoluteLocation multiDoorLocation = doorLocation.getRelative(0, 3, 0);
		cuboid.setData15(AspectRegistry.BLOCK, doorLocation.getRelative(0, 0, -1).getBlockAddress(), dirtNumber);
		cuboid.setData15(AspectRegistry.BLOCK, multiDoorLocation.getRelative(0, 0, -1).getBlockAddress(), dirtNumber);
		cuboid.setData15(AspectRegistry.BLOCK, multiDoorLocation.getRelative(1, 0, -1).getBlockAddress(), dirtNumber);
		cuboid.setData15(AspectRegistry.BLOCK, doorLocation.getBlockAddress(), doorOpenNumber);
		cuboid.setData15(AspectRegistry.BLOCK, multiDoorLocation.getBlockAddress(), multiDoorOpenNumber);
		cuboid.setData7(AspectRegistry.ORIENTATION, multiDoorLocation.getBlockAddress(), OrientationAspect.directionToByte(OrientationAspect.Direction.NORTH));
		cuboid.setData15(AspectRegistry.BLOCK, multiDoorLocation.getRelative(0, 0, 1).getBlockAddress(), multiDoorOpenNumber);
		cuboid.setDataSpecial(AspectRegistry.MULTI_BLOCK_ROOT, multiDoorLocation.getRelative(0, 0, 1).getBlockAddress(), multiDoorLocation);
		cuboid.setData15(AspectRegistry.BLOCK, multiDoorLocation.getRelative(1, 0, 0).getBlockAddress(), multiDoorOpenNumber);
		cuboid.setDataSpecial(AspectRegistry.MULTI_BLOCK_ROOT, multiDoorLocation.getRelative(1, 0, 0).getBlockAddress(), multiDoorLocation);
		cuboid.setData15(AspectRegistry.BLOCK, multiDoorLocation.getRelative(1, 0, 1).getBlockAddress(), multiDoorOpenNumber);
		cuboid.setDataSpecial(AspectRegistry.MULTI_BLOCK_ROOT, multiDoorLocation.getRelative(1, 0, 1).getBlockAddress(), multiDoorLocation);
		
		// Another interesting case is hoppers.
		short hopperDownNumber = ENV.items.getItemById("op.hopper").number();
		short hopperNorthNumber = ENV.items.getItemById("DEPRECATED.op.hopper_north").number();
		AbsoluteLocation hopperDownLocation = address.getBase().getRelative(10, 12, 21);
		AbsoluteLocation hopperNorthLocation = hopperDownLocation.getRelative(0, -1, 0);
		cuboid.setData15(AspectRegistry.BLOCK, hopperDownLocation.getBlockAddress(), hopperDownNumber);
		cuboid.setData15(AspectRegistry.BLOCK, hopperNorthLocation.getBlockAddress(), hopperNorthNumber);
		
		// We can save this using the normal helper and then change the version number to see it updated on load.
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		buffer.putInt(ResourceLoader.VERSION_CUBOID_V5);
		Object state = cuboid.serializeResumable(null, buffer);
		Assert.assertNull(state);
		// (creature count)
		buffer.putInt(0);
		// (suspended mutations)
		buffer.putInt(0);
		// (periodic mutations)
		buffer.putInt(0);
		buffer.flip();
		
		// Write the file.
		String fileName = "cuboid_" + address.x() + "_" + address.y() + "_" + address.z() + ".cuboid";
		try (
				RandomAccessFile aFile = new RandomAccessFile(new File(worldDirectory, fileName), "rw");
				FileChannel outChannel = aFile.getChannel();
		)
		{
			int written = outChannel.write(buffer);
			outChannel.truncate((long)written);
		}
		Assert.assertTrue(new File(worldDirectory, fileName).isFile());
		
		// Now, read the data and verify that it is correct.
		WorldConfig config = new WorldConfig();
		config.worldSpawn = MutableEntity.TESTING_LOCATION.getBlockLocation();
		ResourceLoader loader = new ResourceLoader(worldDirectory, null, config);
		List<SuspendedCuboid<CuboidData>> results = new ArrayList<>();
		loader.getResultsAndRequestBackgroundLoad(results, List.of(), List.of(address), List.of());
		for (int i = 0; (i < 10) && results.isEmpty(); ++i)
		{
			Thread.sleep(10L);
			loader.getResultsAndRequestBackgroundLoad(results, List.of(), List.of(), List.of());
		}
		Assert.assertEquals(1, results.size());
		SuspendedCuboid<CuboidData> result = results.get(0);
		
		CuboidData found = result.cuboid();
		Assert.assertEquals(switchOffNumber, found.getData15(AspectRegistry.BLOCK, switchOnLocation.getBlockAddress()));
		Assert.assertEquals(0, found.getData7(AspectRegistry.LOGIC, switchOnLocation.getBlockAddress()));
		Assert.assertEquals(wireNumber, found.getData15(AspectRegistry.BLOCK, wireLocation.getBlockAddress()));
		Assert.assertEquals(0, found.getData7(AspectRegistry.LOGIC, wireLocation.getBlockAddress()));
		Assert.assertEquals(lampOffNumber, found.getData15(AspectRegistry.BLOCK, lampOnLocation.getBlockAddress()));
		
		Assert.assertEquals(doorClosedNumber, found.getData15(AspectRegistry.BLOCK, doorLocation.getBlockAddress()));
		Assert.assertEquals(multiDoorClosedNumber, found.getData15(AspectRegistry.BLOCK, multiDoorLocation.getBlockAddress()));
		Assert.assertEquals(OrientationAspect.directionToByte(OrientationAspect.Direction.NORTH), found.getData7(AspectRegistry.ORIENTATION, multiDoorLocation.getBlockAddress()));
		Assert.assertEquals(multiDoorClosedNumber, found.getData15(AspectRegistry.BLOCK, multiDoorLocation.getRelative(0, 0, 1).getBlockAddress()));
		Assert.assertEquals(multiDoorLocation, found.getDataSpecial(AspectRegistry.MULTI_BLOCK_ROOT, multiDoorLocation.getRelative(0, 0, 1).getBlockAddress()));
		Assert.assertEquals(multiDoorClosedNumber, found.getData15(AspectRegistry.BLOCK, multiDoorLocation.getRelative(1, 0, 0).getBlockAddress()));
		Assert.assertEquals(multiDoorLocation, found.getDataSpecial(AspectRegistry.MULTI_BLOCK_ROOT, multiDoorLocation.getRelative(1, 0, 0).getBlockAddress()));
		Assert.assertEquals(multiDoorClosedNumber, found.getData15(AspectRegistry.BLOCK, multiDoorLocation.getRelative(1, 0, 1).getBlockAddress()));
		Assert.assertEquals(multiDoorLocation, found.getDataSpecial(AspectRegistry.MULTI_BLOCK_ROOT, multiDoorLocation.getRelative(1, 0, 1).getBlockAddress()));
		
		Assert.assertEquals(hopperDownNumber, found.getData15(AspectRegistry.BLOCK, hopperDownLocation.getBlockAddress()));
		Assert.assertEquals(OrientationAspect.directionToByte(OrientationAspect.Direction.DOWN), found.getData7(AspectRegistry.ORIENTATION, hopperDownLocation.getBlockAddress()));
		Assert.assertEquals(hopperDownNumber, found.getData15(AspectRegistry.BLOCK, hopperNorthLocation.getBlockAddress()));
		Assert.assertEquals(OrientationAspect.directionToByte(OrientationAspect.Direction.NORTH), found.getData7(AspectRegistry.ORIENTATION, hopperNorthLocation.getBlockAddress()));
		
		loader.shutdown();
	}

	@Test
	public void creativeMode() throws Throwable
	{
		File worldDirectory = DIRECTORY.newFolder();
		WorldConfig config = new WorldConfig();
		config.worldGeneratorName = WorldConfig.WorldGeneratorName.FLAT;
		config.worldSpawn = MutableEntity.TESTING_LOCATION.getBlockLocation();
		config.defaultPlayerMode = WorldConfig.DefaultPlayerMode.CREATIVE;
		ResourceLoader loader = new ResourceLoader(worldDirectory, null, config);
		
		// Just load a single entity to verify that it has the correct mode flag.
		Assert.assertNull(_loadEntities(loader, List.of(1)));
		List<SuspendedEntity> results = new ArrayList<>();
		for (int i = 0; (results.size() < 1) && (i < 10); ++i)
		{
			Thread.sleep(10L);
			loader.getResultsAndRequestBackgroundLoad(List.of(), results, List.of(), List.of());
		}
		Entity entity = results.get(0).entity();
		
		// Verify defaults.
		Assert.assertEquals(MutableEntity.TESTING_LOCATION, entity.location());
		Assert.assertTrue(entity.isCreativeMode());
		loader.shutdown();
	}

	@Test
	public void cuboidNonStackableV7() throws Throwable
	{
		// This is a pre-serialized V7 cuboid with an iron_sword of 103 durability stored in block 2,3,4 of an air cuboid at 3,-5,0.
		byte[] preSerializedV7 = new byte[] {0, 0, 0, 7, -128, 0, 0, 0, 0, 1, 8, 100, 0, 0, 0, 10, 1, 0, 0, 0, 1, 0, 28, 0, 0, 0, 103, -128, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
			// (creature count)
			, 0, 0, 0, 0
			// (suspended mutations)
			, 0, 0, 0, 0
			// (periodic mutations)
			, 0, 0, 0, 0
		};
		CuboidAddress address = CuboidAddress.fromInt(3, -5, 0);
		BlockAddress blockLocation = BlockAddress.fromInt(2, 3, 4);
		File worldDirectory = DIRECTORY.newFolder();
		
		// Write the file.
		String fileName = "cuboid_" + address.x() + "_" + address.y() + "_" + address.z() + ".cuboid";
		try (
				RandomAccessFile aFile = new RandomAccessFile(new File(worldDirectory, fileName), "rw");
				FileChannel outChannel = aFile.getChannel();
		)
		{
			int written = outChannel.write(ByteBuffer.wrap(preSerializedV7));
			outChannel.truncate((long)written);
			Assert.assertEquals(preSerializedV7.length, written);
		}
		Assert.assertTrue(new File(worldDirectory, fileName).isFile());
		
		// Now, read this and make sure it contains what we serialized.
		ResourceLoader loader = new ResourceLoader(worldDirectory, null, new WorldConfig());
		List<SuspendedCuboid<CuboidData>> results = new ArrayList<>();
		loader.getResultsAndRequestBackgroundLoad(results, List.of(), List.of(address), List.of());
		for (int i = 0; (i < 10) && results.isEmpty(); ++i)
		{
			Thread.sleep(10L);
			loader.getResultsAndRequestBackgroundLoad(results, List.of(), List.of(), List.of());
		}
		Assert.assertEquals(1, results.size());
		SuspendedCuboid<CuboidData> result = results.get(0);
		CuboidData cuboid = result.cuboid();
		
		// Verify our assumptions.
		Inventory inv = cuboid.getDataSpecial(AspectRegistry.INVENTORY, blockLocation);
		Assert.assertEquals(10, inv.maxEncumbrance);
		Assert.assertEquals(4, inv.currentEncumbrance);
		Assert.assertEquals(IRON_SWORD, inv.getNonStackableForKey(1).type());
		Assert.assertEquals(103, inv.getNonStackableForKey(1).durability().value().intValue());
	}


	private static CuboidData _waitForOne(ResourceLoader loader) throws InterruptedException
	{
		CuboidData loaded = null;
		for (int i = 0; (null == loaded) && (i < 10); ++i)
		{
			Thread.sleep(10L);
			Collection<CuboidData> results = _loadSimpleCuboids(loader, List.of());
			if (null != results)
			{
				Assert.assertTrue(1 == results.size());
				loaded = results.iterator().next();
			}
		}
		// We expect that this should be loaded.
		Assert.assertNotNull(loaded);
		return loaded;
	}

	private static Collection<CuboidData> _loadSimpleCuboids(ResourceLoader loader, Collection<CuboidAddress> addresses)
	{
		Collection<SuspendedCuboid<CuboidData>> results = new ArrayList<>();
		loader.getResultsAndRequestBackgroundLoad(results, List.of(), addresses, List.of());
		// In this helper, we will just extract the cuboids.
		Collection<CuboidData> extracted = null;
		if (!results.isEmpty())
		{
			extracted = new ArrayList<>();
			for (SuspendedCuboid<CuboidData> suspended : results)
			{
				extracted.add(suspended.cuboid());
				Assert.assertTrue(suspended.pendingMutations().isEmpty());
				Assert.assertTrue(suspended.periodicMutationMillis().isEmpty());
			}
		}
		return extracted;
	}

	private static Collection<Entity> _loadEntities(ResourceLoader loader, Collection<Integer> ids)
	{
		Collection<SuspendedEntity> results = new ArrayList<>();
		loader.getResultsAndRequestBackgroundLoad(List.of(), results, List.of(), ids);
		return results.isEmpty()
				? null
				: results.stream().map((SuspendedEntity suspended) -> suspended.entity()).toList()
		;
	}

	private SuspendedCuboid<CuboidData> _loadOneSuspended(ResourceLoader loader, CuboidAddress address) throws InterruptedException
	{
		List<SuspendedCuboid<CuboidData>> out_loadedCuboids = new ArrayList<>();
		loader.getResultsAndRequestBackgroundLoad(out_loadedCuboids, List.of(), List.of(address), List.of());
		// The first call should give us nothing so loop until we see an answer.
		Assert.assertTrue(out_loadedCuboids.isEmpty());
		for (int i = 0; (out_loadedCuboids.isEmpty()) && (i < 10); ++i)
		{
			Thread.sleep(10L);
			loader.getResultsAndRequestBackgroundLoad(out_loadedCuboids, List.of(), List.of(), List.of());
		}
		Assert.assertEquals(1, out_loadedCuboids.size());
		return out_loadedCuboids.get(0);
	}
}
