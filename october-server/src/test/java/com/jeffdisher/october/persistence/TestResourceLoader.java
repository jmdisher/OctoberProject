package com.jeffdisher.october.persistence;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.CuboidHeightMap;
import com.jeffdisher.october.logic.CreatureIdAssigner;
import com.jeffdisher.october.logic.HeightMapHelpers;
import com.jeffdisher.october.logic.ScheduledChange;
import com.jeffdisher.october.logic.ScheduledMutation;
import com.jeffdisher.october.mutations.EntityChangeAttackEntity;
import com.jeffdisher.october.mutations.EntityChangeMove;
import com.jeffdisher.october.mutations.EntityChangePeriodic;
import com.jeffdisher.october.mutations.MutationBlockIncrementalBreak;
import com.jeffdisher.october.mutations.MutationBlockOverwrite;
import com.jeffdisher.october.mutations.MutationBlockReplace;
import com.jeffdisher.october.mutations.MutationEntityStoreToInventory;
import com.jeffdisher.october.net.MutationEntityCodec;
import com.jeffdisher.october.persistence.legacy.LegacyEntityV1;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.CraftOperation;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Difficulty;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.WorldConfig;
import com.jeffdisher.october.utils.Encoding;
import com.jeffdisher.october.worldgen.CuboidGenerator;


public class TestResourceLoader
{
	@ClassRule
	public static TemporaryFolder DIRECTORY = new TemporaryFolder();
	private static Environment ENV;
	private static Item STONE_ITEM;
	private static Block STONE;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		STONE_ITEM = ENV.items.getItemById("op.stone");
		STONE = ENV.blocks.fromItem(STONE_ITEM);
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
		loader.writeBackToDisk(List.of(new PackagedCuboid(loaded, List.of(), List.of())), List.of());
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
		ResourceLoader loader = new ResourceLoader(worldDirectory, null, MutableEntity.TESTING_LOCATION);
		
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
		MutationBlockOverwrite mutation = new MutationBlockOverwrite(new AbsoluteLocation(32, 0, 0), STONE);
		loader.writeBackToDisk(List.of(new PackagedCuboid(loaded, List.of(), List.of(new ScheduledMutation(mutation, 0L)))), List.of());
		// (the shutdown will wait for the queue to drain)
		loader.shutdown();
		
		// Make sure that we see this written back.
		String fileName = "cuboid_" + airAddress.x() + "_" + airAddress.y() + "_" + airAddress.z() + ".cuboid";
		Assert.assertTrue(new File(worldDirectory, fileName).isFile());
		
		// Now, create a new loader to verify that we can read this.
		loader = new ResourceLoader(worldDirectory, null, null);
		SuspendedCuboid<CuboidData> suspended = _loadOneSuspended(loader, airAddress);
		Assert.assertEquals(airAddress, suspended.cuboid().getCuboidAddress());
		Assert.assertEquals(1, suspended.mutations().size());
		Assert.assertTrue(suspended.mutations().get(0).mutation() instanceof MutationBlockOverwrite);
		loader.shutdown();
	}

	@Test
	public void writeAndReadSuspendedEntity() throws Throwable
	{
		File worldDirectory = DIRECTORY.newFolder();
		ResourceLoader loader = new ResourceLoader(worldDirectory, null, MutableEntity.TESTING_LOCATION);
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
		MutationBlockOverwrite mutation = new MutationBlockOverwrite(new AbsoluteLocation(32, 0, 0), STONE);
		loader.writeBackToDisk(List.of(new PackagedCuboid(loaded, List.of(), List.of(new ScheduledMutation(mutation, 0L)))), List.of());
		// (the shutdown will wait for the queue to drain)
		loader.shutdown();
		
		// Make sure that we see this written back.
		File cuboidFile = new File(worldDirectory, "cuboid_" + airAddress.x() + "_" + airAddress.y() + "_" + airAddress.z() + ".cuboid");
		Assert.assertTrue(cuboidFile.isFile());
		// Experimentally, we know that this is 53 bytes.
		Assert.assertEquals(53L, cuboidFile.length());
		
		// Now, create a new loader, load, and resave this.
		loader = new ResourceLoader(worldDirectory, null, null);
		SuspendedCuboid<CuboidData> suspended = _loadOneSuspended(loader, airAddress);
		Assert.assertEquals(airAddress, suspended.cuboid().getCuboidAddress());
		Assert.assertEquals(1, suspended.mutations().size());
		Assert.assertTrue(suspended.mutations().get(0).mutation() instanceof MutationBlockOverwrite);
		loader.writeBackToDisk(List.of(new PackagedCuboid(suspended.cuboid(), List.of(), List.of())), List.of());
		loader.shutdown();
		
		// Verify that the file has been truncated.
		Assert.assertTrue(cuboidFile.isFile());
		// Experimentally, we know that this is 30 bytes.
		Assert.assertEquals(30L, cuboidFile.length());
		
		// Load it again and verify that the mutation is missing and we parsed without issue.
		loader = new ResourceLoader(worldDirectory, null, null);
		suspended = _loadOneSuspended(loader, airAddress);
		Assert.assertEquals(airAddress, suspended.cuboid().getCuboidAddress());
		Assert.assertEquals(0, suspended.mutations().size());
		loader.shutdown();
	}

	@Test
	public void verifyMutationsFromGeneration() throws Throwable
	{
		File worldDirectory = DIRECTORY.newFolder();
		MutationBlockOverwrite test = new MutationBlockOverwrite(new AbsoluteLocation(1, 2, 3), STONE);
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
		List<ScheduledMutation> mutations = result.mutations();
		Assert.assertEquals(1, mutations.size());
		Assert.assertTrue(test == mutations.get(0).mutation());
		loader.shutdown();
	}

	@Test
	public void writeAndReadSuspendedWithDroppedActions() throws Throwable
	{
		// We want to store a cuboid and entity with 2 suspended actions each and verify that the non-persistable one is dropped on reload.
		File worldDirectory = DIRECTORY.newFolder();
		ResourceLoader loader = new ResourceLoader(worldDirectory, new FlatWorldGenerator(false), MutableEntity.TESTING_LOCATION);
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
		EntityChangeAttackEntity ephemeralChange = new EntityChangeAttackEntity(targetId);
		
		// Create the cuboid mutations we want.
		MutationBlockReplace persistentMutation = new MutationBlockReplace(airAddress.getBase(), ENV.special.AIR, ENV.special.WATER_SOURCE);
		MutationBlockIncrementalBreak ephemeralMutation = new MutationBlockIncrementalBreak(airAddress.getBase(), (short)10, targetId);
		
		// Re-save these to disk.
		loader.writeBackToDisk(List.of(
				new PackagedCuboid(cuboids.get(0).cuboid(), List.of(), List.of(
						new ScheduledMutation(ephemeralMutation, 0L),
						new ScheduledMutation(persistentMutation, 0L)
				))
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
		Assert.assertEquals(1, cuboids.get(0).mutations().size());
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
		Assert.assertEquals(0, generated.mutations().size());
		
		// Save this back.
		Collection<PackagedCuboid> toWrite = List.of(new PackagedCuboid(generated.cuboid(), generated.creatures(), generated.mutations()));
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
		Assert.assertEquals(0, loaded.mutations().size());
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
		String fileToWrite = "difficulty\tPEACEFUL\n"
				+ "basic_seed\t-465342154\n"
				+ "world_spawn\t5,6,7\n"
				+ "ticks_per_day\t2000\n"
				+ "world_generator_name\tBASIC\n"
				+ "should_synthesize_updates_on_load\ttrue\n"
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
		loader.shutdown();
	}

	@Test
	public void writeAndReadEntityV1() throws Throwable
	{
		Item swordItem = ENV.items.getItemById("op.iron_sword");
		File worldDirectory = DIRECTORY.newFolder();
		
		// This is a test of our ability to read the V1 entity data.  We manually write a file and then attempt to read it, verifying the result is sensible.
		int id = 1;
		boolean isCreativeMode = true;
		EntityLocation location = new EntityLocation(1.0f, 2.0f, 3.0f);
		EntityLocation velocity = new EntityLocation(4.0f, 5.0f, 6.0f);
		Inventory inventory = Inventory.start(20).addStackable(STONE_ITEM, 1).addNonStackable(new NonStackableItem(swordItem, 5)).finish();
		int[] hotbarItems = new int[LegacyEntityV1.HOTBAR_SIZE];
		int hotbarIndex = 3;
		NonStackableItem[] armourSlots = new NonStackableItem[BodyPart.values().length];
		CraftOperation localCraftOperation = null;
		byte health = 50;
		byte food = 12;
		byte breath = 98;
		int energyDeficit = 500;
		EntityLocation spawnLocation = new EntityLocation(-5.0f, -6.0f, -7.0f);
		LegacyEntityV1 legacy = new LegacyEntityV1(id
			, isCreativeMode
			, location
			, velocity
			, inventory
			, hotbarItems
			, hotbarIndex
			, armourSlots
			, localCraftOperation
			, health
			, food
			, breath
			, energyDeficit
			, spawnLocation
		);
		EntityChangeMove<IMutablePlayerEntity> move = new EntityChangeMove<>(10L, 1.0f, EntityChangeMove.Direction.EAST);
		
		// Serialize to buffer.
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		buffer.putInt(ResourceLoader.VERSION_ENTITY_V1);
		legacy.test_writeToBuffer(buffer);
		buffer.putLong(500L);
		MutationEntityCodec.serializeToBuffer(buffer, move);
		buffer.flip();
		
		// Write the file.
		String fileName = "entity_" + id + ".entity";
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
		ResourceLoader loader = new ResourceLoader(worldDirectory, null, MutableEntity.TESTING_LOCATION);
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
		Assert.assertTrue(changes.get(0).change() instanceof EntityChangeMove);
		
		loader.shutdown();
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
				Assert.assertTrue(suspended.mutations().isEmpty());
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
