package com.jeffdisher.october.persistence;

import java.io.File;
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
import com.jeffdisher.october.logic.ScheduledMutation;
import com.jeffdisher.october.mutations.MutationBlockOverwrite;
import com.jeffdisher.october.mutations.MutationEntityStoreToInventory;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.worldgen.CuboidGenerator;


public class TestResourceLoader
{
	@ClassRule
	public static TemporaryFolder DIRECTORY = new TemporaryFolder();
	private static Environment ENV;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void empty() throws Throwable
	{
		ResourceLoader loader = new ResourceLoader(DIRECTORY.newFolder(), null);
		CuboidAddress address = new CuboidAddress((short)1, (short)0, (short)0);
		
		// We should see nothing come back, not matter how many times we issue the request.
		Assert.assertNull(_loadSimpleCuboids(loader, List.of(address)));
		Assert.assertNull(_loadSimpleCuboids(loader, List.of(address)));
		Assert.assertNull(_loadSimpleCuboids(loader, List.of(address)));
		loader.shutdown();
	}

	@Test
	public void basic() throws Throwable
	{
		ResourceLoader loader = new ResourceLoader(DIRECTORY.newFolder(), null);
		CuboidAddress address = new CuboidAddress((short)1, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.blocks.STONE);
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
		ResourceLoader loader = new ResourceLoader(DIRECTORY.newFolder(), new FlatWorldGenerator());
		CuboidAddress stoneAddress = new CuboidAddress((short)1, (short)0, (short)-1);
		CuboidAddress airAddress = new CuboidAddress((short)1, (short)0, (short)0);
		
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
		BlockAddress block = new BlockAddress((byte)0, (byte)0, (byte)0);
		short test0 = loaded.get(0).getData15(AspectRegistry.BLOCK, block);
		short test1 = loaded.get(1).getData15(AspectRegistry.BLOCK, block);
		Assert.assertTrue((ENV.items.AIR.number() == test0) || (ENV.items.AIR.number() == test1));
		Assert.assertTrue((ENV.items.STONE.number() == test0) || (ENV.items.STONE.number() == test1));
		loader.shutdown();
	}

	@Test
	public void writeThenRead() throws Throwable
	{
		File worldDirectory = DIRECTORY.newFolder();
		ResourceLoader loader = new ResourceLoader(worldDirectory, new FlatWorldGenerator());
		CuboidAddress airAddress = new CuboidAddress((short)1, (short)0, (short)0);
		
		// We should see this satisfied, but not on the first call (we will use 10 tries, with yields).
		Collection<CuboidData> results = _loadSimpleCuboids(loader, List.of(airAddress));
		Assert.assertNull(results);
		CuboidData loaded = _waitForOne(loader);
		BlockAddress block = new BlockAddress((byte)0, (byte)0, (byte)0);
		// Modify a block and write this back.
		loaded.setData15(AspectRegistry.BLOCK, block, ENV.items.STONE.number());
		loader.writeBackToDisk(List.of(new SuspendedCuboid<>(loaded, List.of())), List.of());
		// (the shutdown will wait for the queue to drain)
		loader.shutdown();
		
		// Make sure that we see this written back.
		String fileName = "cuboid_" + airAddress.x() + "_" + airAddress.y() + "_" + airAddress.z() + ".cuboid";
		Assert.assertTrue(new File(worldDirectory, fileName).isFile());
		
		// Now, create a new loader to verify that we can read this.
		loader = new ResourceLoader(worldDirectory, null);
		results = _loadSimpleCuboids(loader, List.of(airAddress));
		Assert.assertNull(results);
		loaded = _waitForOne(loader);
		Assert.assertEquals(ENV.items.STONE.number(), loaded.getData15(AspectRegistry.BLOCK, block));
		loader.shutdown();
	}

	@Test
	public void entities() throws Throwable
	{
		File worldDirectory = DIRECTORY.newFolder();
		ResourceLoader loader = new ResourceLoader(worldDirectory, null);
		
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
		loader = new ResourceLoader(worldDirectory, null);
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
		ResourceLoader loader = new ResourceLoader(worldDirectory, new FlatWorldGenerator());
		CuboidAddress airAddress = new CuboidAddress((short)1, (short)0, (short)0);
		
		// We should see this satisfied, but not on the first call (we will use 10 tries, with yields).
		Collection<CuboidData> results = _loadSimpleCuboids(loader, List.of(airAddress));
		Assert.assertNull(results);
		CuboidData loaded = _waitForOne(loader);
		// Create a mutation which targets this and save it back with the cuboid.
		MutationBlockOverwrite mutation = new MutationBlockOverwrite(new AbsoluteLocation(32, 0, 0), ENV.blocks.STONE);
		loader.writeBackToDisk(List.of(new SuspendedCuboid<>(loaded, List.of(new ScheduledMutation(mutation, 0L)))), List.of());
		// (the shutdown will wait for the queue to drain)
		loader.shutdown();
		
		// Make sure that we see this written back.
		String fileName = "cuboid_" + airAddress.x() + "_" + airAddress.y() + "_" + airAddress.z() + ".cuboid";
		Assert.assertTrue(new File(worldDirectory, fileName).isFile());
		
		// Now, create a new loader to verify that we can read this.
		loader = new ResourceLoader(worldDirectory, null);
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
		ResourceLoader loader = new ResourceLoader(worldDirectory, null);
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
		Assert.assertTrue(results.get(0).mutations().isEmpty());
		Assert.assertEquals(MutableEntity.DEFAULT_LOCATION, results.get(0).entity().location());
		
		// Modify the entity and create a mutation to store with it.
		MutableEntity mutable = MutableEntity.existing(results.get(0).entity());
		EntityLocation location = mutable.newLocation;
		EntityLocation newLocation = new EntityLocation(2.0f * location.x(), 3.0f * location.y(), 4.0f * location.z());
		mutable.newLocation = newLocation;
		MutationEntityStoreToInventory mutation = new MutationEntityStoreToInventory(new Items(ENV.blocks.STONE.item(), 2));
		
		loader.writeBackToDisk(List.of(), List.of(new SuspendedEntity(mutable.freeze(), List.of(mutation))));
		// (the shutdown will wait for the queue to drain)
		loader.shutdown();
		
		// Make sure that we see this written back.
		String fileName = "entity_" + entityId + ".entity";
		Assert.assertTrue(new File(worldDirectory, fileName).isFile());
		
		// Now, create a new loader to verify that we can read this.
		loader = new ResourceLoader(worldDirectory, null);
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
		Assert.assertEquals(1, suspended.mutations().size());
		Assert.assertTrue(suspended.mutations().get(0) instanceof MutationEntityStoreToInventory);
		loader.shutdown();
	}

	@Test
	public void overwiteFile() throws Throwable
	{
		File worldDirectory = DIRECTORY.newFolder();
		ResourceLoader loader = new ResourceLoader(worldDirectory, new FlatWorldGenerator());
		CuboidAddress airAddress = new CuboidAddress((short)1, (short)0, (short)0);
		
		// We should see this satisfied, but not on the first call (we will use 10 tries, with yields).
		Collection<CuboidData> results = _loadSimpleCuboids(loader, List.of(airAddress));
		Assert.assertNull(results);
		CuboidData loaded = _waitForOne(loader);
		// Create a mutation which targets this and save it back with the cuboid.
		MutationBlockOverwrite mutation = new MutationBlockOverwrite(new AbsoluteLocation(32, 0, 0), ENV.blocks.STONE);
		loader.writeBackToDisk(List.of(new SuspendedCuboid<>(loaded, List.of(new ScheduledMutation(mutation, 0L)))), List.of());
		// (the shutdown will wait for the queue to drain)
		loader.shutdown();
		
		// Make sure that we see this written back.
		File cuboidFile = new File(worldDirectory, "cuboid_" + airAddress.x() + "_" + airAddress.y() + "_" + airAddress.z() + ".cuboid");
		Assert.assertTrue(cuboidFile.isFile());
		// Experimentally, we know that this is 41 bytes.
		Assert.assertEquals(41L, cuboidFile.length());
		
		// Now, create a new loader, load, and resave this.
		loader = new ResourceLoader(worldDirectory, null);
		SuspendedCuboid<CuboidData> suspended = _loadOneSuspended(loader, airAddress);
		Assert.assertEquals(airAddress, suspended.cuboid().getCuboidAddress());
		Assert.assertEquals(1, suspended.mutations().size());
		Assert.assertTrue(suspended.mutations().get(0).mutation() instanceof MutationBlockOverwrite);
		loader.writeBackToDisk(List.of(new SuspendedCuboid<>(suspended.cuboid(), List.of())), List.of());
		loader.shutdown();
		
		// Verify that the file has been truncated.
		Assert.assertTrue(cuboidFile.isFile());
		// Experimentally, we know that this is 18 bytes.
		Assert.assertEquals(18L, cuboidFile.length());
		
		// Load it again and verify that the mutation is missing and we parsed without issue.
		loader = new ResourceLoader(worldDirectory, null);
		suspended = _loadOneSuspended(loader, airAddress);
		Assert.assertEquals(airAddress, suspended.cuboid().getCuboidAddress());
		Assert.assertEquals(0, suspended.mutations().size());
		loader.shutdown();
	}

	@Test
	public void verifyMutationsFromGeneration() throws Throwable
	{
		File worldDirectory = DIRECTORY.newFolder();
		MutationBlockOverwrite test = new MutationBlockOverwrite(new AbsoluteLocation(1, 2, 3), ENV.blocks.STONE);
		ResourceLoader loader = new ResourceLoader(worldDirectory, (CuboidAddress address) -> {
			return new SuspendedCuboid<CuboidData>(CuboidGenerator.createFilledCuboid(address, ENV.blocks.AIR), List.of(
					new ScheduledMutation(test, 100L)
			));
		});
		List<SuspendedCuboid<CuboidData>> out_loadedCuboids = new ArrayList<>();
		loader.getResultsAndRequestBackgroundLoad(out_loadedCuboids, List.of(), List.of(new CuboidAddress((short)1, (short)2, (short)3)), List.of());
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
