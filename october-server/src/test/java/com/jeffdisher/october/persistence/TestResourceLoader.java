package com.jeffdisher.october.persistence;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.mutations.MutationBlockOverwrite;
import com.jeffdisher.october.registries.AspectRegistry;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.worldgen.CuboidGenerator;


public class TestResourceLoader
{
	@ClassRule
	public static TemporaryFolder DIRECTORY = new TemporaryFolder();

	@Test
	public void empty() throws Throwable
	{
		ResourceLoader loader = new ResourceLoader(DIRECTORY.newFolder(), null);
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		
		// We should see nothing come back, not matter how many times we issue the request.
		Assert.assertNull(_loadCuboids(loader, List.of(address)));
		Assert.assertNull(_loadCuboids(loader, List.of(address)));
		Assert.assertNull(_loadCuboids(loader, List.of(address)));
		loader.shutdown();
	}

	@Test
	public void basic() throws Throwable
	{
		ResourceLoader loader = new ResourceLoader(DIRECTORY.newFolder(), null);
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ItemRegistry.STONE);
		loader.preload(cuboid);
		
		// We should see this satisfied, but not on the first call (we will use 10 tries, with yields).
		Collection<CuboidData> results = _loadCuboids(loader, List.of(address));
		Assert.assertNull(results);
		for (int i = 0; (null == results) && (i < 10); ++i)
		{
			Thread.sleep(10L);
			results = _loadCuboids(loader, List.of(address));
		}
		Assert.assertEquals(1, results.size());
		loader.shutdown();
	}

	@Test
	public void flatWorld() throws Throwable
	{
		ResourceLoader loader = new ResourceLoader(DIRECTORY.newFolder(), new FlatWorldGenerator());
		CuboidAddress stoneAddress = new CuboidAddress((short)0, (short)0, (short)-1);
		CuboidAddress airAddress = new CuboidAddress((short)0, (short)0, (short)0);
		
		// We should see this satisfied, but not on the first call (we will use 10 tries, with yields).
		Collection<CuboidData> results = _loadCuboids(loader, List.of(stoneAddress, airAddress));
		Assert.assertNull(results);
		List<CuboidData> loaded = new ArrayList<>();
		for (int i = 0; (2 != loaded.size()) && (i < 10); ++i)
		{
			Thread.sleep(10L);
			results = _loadCuboids(loader, List.of());
			if (null != results)
			{
				loaded.addAll(results);
			}
		}
		Assert.assertEquals(2, loaded.size());
		BlockAddress block = new BlockAddress((byte)0, (byte)0, (byte)0);
		short test0 = loaded.get(0).getData15(AspectRegistry.BLOCK, block);
		short test1 = loaded.get(1).getData15(AspectRegistry.BLOCK, block);
		Assert.assertTrue((ItemRegistry.AIR.number() == test0) || (ItemRegistry.AIR.number() == test1));
		Assert.assertTrue((ItemRegistry.STONE.number() == test0) || (ItemRegistry.STONE.number() == test1));
		loader.shutdown();
	}

	@Test
	public void writeThenRead() throws Throwable
	{
		File worldDirectory = DIRECTORY.newFolder();
		ResourceLoader loader = new ResourceLoader(worldDirectory, new FlatWorldGenerator());
		CuboidAddress airAddress = new CuboidAddress((short)0, (short)0, (short)0);
		
		// We should see this satisfied, but not on the first call (we will use 10 tries, with yields).
		Collection<CuboidData> results = _loadCuboids(loader, List.of(airAddress));
		Assert.assertNull(results);
		CuboidData loaded = _waitForOne(loader);
		BlockAddress block = new BlockAddress((byte)0, (byte)0, (byte)0);
		// Modify a block and write this back.
		loaded.setData15(AspectRegistry.BLOCK, block, ItemRegistry.STONE.number());
		loader.writeBackToDisk(List.of(new SuspendedCuboid<>(loaded, List.of())), List.of());
		// (the shutdown will wait for the queue to drain)
		loader.shutdown();
		
		// Make sure that we see this written back.
		String fileName = "cuboid_" + airAddress.x() + "_" + airAddress.y() + "_" + airAddress.z() + ".cuboid";
		Assert.assertTrue(new File(worldDirectory, fileName).isFile());
		
		// Now, create a new loader to verify that we can read this.
		loader = new ResourceLoader(worldDirectory, null);
		results = _loadCuboids(loader, List.of(airAddress));
		Assert.assertNull(results);
		loaded = _waitForOne(loader);
		Assert.assertEquals(ItemRegistry.STONE.number(), loaded.getData15(AspectRegistry.BLOCK, block));
		loader.shutdown();
	}

	@Test
	public void entities() throws Throwable
	{
		File worldDirectory = DIRECTORY.newFolder();
		ResourceLoader loader = new ResourceLoader(worldDirectory, null);
		
		// We should see this satisfied, but not on the first call (we will use 10 tries, with yields).
		Assert.assertNull(_loadEntities(loader, List.of(1, 2)));
		List<Entity> results = new ArrayList<>();
		for (int i = 0; (results.size() < 2) && (i < 10); ++i)
		{
			Thread.sleep(10L);
			loader.getResultsAndRequestBackgroundLoad(List.of(), results, List.of(), List.of());
		}
		
		// Modify an entity and write these  back.
		Entity original = results.get(0);
		Entity other = results.get(1);
		Entity modified = new Entity(original.id()
				, new EntityLocation(1.0f, 2.0f, 3.0f)
				, original.zVelocityPerSecond()
				, original.volume()
				, original.blocksPerTickSpeed()
				, original.inventory()
				, original.selectedItem()
				, null
		);
		loader.writeBackToDisk(List.of(), List.of(modified, other));
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
		Entity resolved = (original.id() == results.get(0).id())
				? results.get(0)
				: results.get(1)
		;
		Assert.assertEquals(modified.location(), resolved.location());
		loader.shutdown();
	}

	@Test
	public void writeAndReadSuspended() throws Throwable
	{
		File worldDirectory = DIRECTORY.newFolder();
		ResourceLoader loader = new ResourceLoader(worldDirectory, new FlatWorldGenerator());
		CuboidAddress airAddress = new CuboidAddress((short)0, (short)0, (short)0);
		
		// We should see this satisfied, but not on the first call (we will use 10 tries, with yields).
		Collection<CuboidData> results = _loadCuboids(loader, List.of(airAddress));
		Assert.assertNull(results);
		CuboidData loaded = _waitForOne(loader);
		// Create a mutation which targets this and save it back with the cuboid.
		MutationBlockOverwrite mutation = new MutationBlockOverwrite(new AbsoluteLocation(0, 0, 0), ItemRegistry.STONE);
		loader.writeBackToDisk(List.of(new SuspendedCuboid<>(loaded, List.of(mutation))), List.of());
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
		Assert.assertTrue(suspended.mutations().get(0) instanceof MutationBlockOverwrite);
		loader.shutdown();
	}

	@Test
	public void overwiteFile() throws Throwable
	{
		File worldDirectory = DIRECTORY.newFolder();
		ResourceLoader loader = new ResourceLoader(worldDirectory, new FlatWorldGenerator());
		CuboidAddress airAddress = new CuboidAddress((short)0, (short)0, (short)0);
		
		// We should see this satisfied, but not on the first call (we will use 10 tries, with yields).
		Collection<CuboidData> results = _loadCuboids(loader, List.of(airAddress));
		Assert.assertNull(results);
		CuboidData loaded = _waitForOne(loader);
		// Create a mutation which targets this and save it back with the cuboid.
		MutationBlockOverwrite mutation = new MutationBlockOverwrite(new AbsoluteLocation(0, 0, 0), ItemRegistry.STONE);
		loader.writeBackToDisk(List.of(new SuspendedCuboid<>(loaded, List.of(mutation))), List.of());
		// (the shutdown will wait for the queue to drain)
		loader.shutdown();
		
		// Make sure that we see this written back.
		File cuboidFile = new File(worldDirectory, "cuboid_" + airAddress.x() + "_" + airAddress.y() + "_" + airAddress.z() + ".cuboid");
		Assert.assertTrue(cuboidFile.isFile());
		// Experimentally, we know that this is 39 bytes.
		Assert.assertEquals(39L, cuboidFile.length());
		
		// Now, create a new loader, load, and resave this.
		loader = new ResourceLoader(worldDirectory, null);
		SuspendedCuboid<CuboidData> suspended = _loadOneSuspended(loader, airAddress);
		Assert.assertEquals(airAddress, suspended.cuboid().getCuboidAddress());
		Assert.assertEquals(1, suspended.mutations().size());
		Assert.assertTrue(suspended.mutations().get(0) instanceof MutationBlockOverwrite);
		loader.writeBackToDisk(List.of(new SuspendedCuboid<>(suspended.cuboid(), List.of())), List.of());
		loader.shutdown();
		
		// Verify that the file has been truncated.
		Assert.assertTrue(cuboidFile.isFile());
		// Experimentally, we know that this is 24 bytes.
		Assert.assertEquals(24L, cuboidFile.length());
		
		// Load it again and verify that the mutation is missing and we parsed without issue.
		loader = new ResourceLoader(worldDirectory, null);
		suspended = _loadOneSuspended(loader, airAddress);
		Assert.assertEquals(airAddress, suspended.cuboid().getCuboidAddress());
		Assert.assertEquals(0, suspended.mutations().size());
		loader.shutdown();
	}


	private static CuboidData _waitForOne(ResourceLoader loader) throws InterruptedException
	{
		CuboidData loaded = null;
		for (int i = 0; (null == loaded) && (i < 10); ++i)
		{
			Thread.sleep(10L);
			Collection<CuboidData> results = _loadCuboids(loader, List.of());
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

	private static Collection<CuboidData> _loadCuboids(ResourceLoader loader, Collection<CuboidAddress> addresses)
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
		Collection<Entity> results = new ArrayList<>();
		loader.getResultsAndRequestBackgroundLoad(List.of(), results, List.of(), ids);
		return results.isEmpty()
				? null
				: results
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
