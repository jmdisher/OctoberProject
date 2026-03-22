package com.jeffdisher.october.persistence;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.types.CuboidAddress;


public class TestStorageModelMigration
{
	@ClassRule
	public static TemporaryFolder DIRECTORY = new TemporaryFolder();
	@BeforeClass
	public static void setup() throws Throwable
	{
		Environment.createSharedInstance();
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void dataV8AndV10InOne() throws Throwable
	{
		// Verify that we can read V8 cuboid (with creatures and on-ground inventories) and an entity.
		File worldDirectory = DIRECTORY.newFolder();
		
		byte[] v8CuboidData = new byte[] { 0, 0, 0, 8, -128, 0, 0, 0, 0, 1, 12, -64, 0, 0, 0, 50, 2, 0, 0, 0, 1, 0, 1, 0, 0, 0, 2, 0, 0, 0, 2, 0, 28, 1, 0, 0, 0, 1, -12, -128, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 2, 64, -96, 0, 0, 64, -96, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 50, 100, 3, 65, 112, 0, 0, 65, 112, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 60, 100, 0, 0, 0, 0, 0, 0, 0, 0 };
		byte[] v10CuboidData = new byte[] { 0, 0, 0, 10, -128, 0, 0, 0, 0, 0, -128, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
		byte[] v8EntityData = new byte[] { 0, 0, 0, 8, 0, 0, 0, 1, 0, 65, -56, 0, 0, 65, -56, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -56, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1, -1, -1, -1, -1, -1, -1, -1, 0, 0, 0, 0, 0, 0, 0, 0, 100, 100, 100, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
		
		CuboidAddress v8Address = CuboidAddress.fromInt(1, 2, -1);
		CuboidAddress v10Address = CuboidAddress.fromInt(1, 2, -2);
		int entityId = 1;
		String cuboidV8File = "cuboid_" + v8Address.x() + "_" + v8Address.y() + "_" + v8Address.z() + ".cuboid";
		String cuboidV10File = "cuboid_" + v10Address.x() + "_" + v10Address.y() + "_" + v10Address.z() + ".cuboid";
		String entityFile = "entity_" + entityId + ".entity";
		
		// Write the pre-serialized data.
		_storePerSerialized(worldDirectory, cuboidV8File, v8CuboidData);
		_storePerSerialized(worldDirectory, cuboidV10File, v10CuboidData);
		_storePerSerialized(worldDirectory, entityFile, v8EntityData);
		
		// This is written in the old format so it should require transformation.
		Assert.assertTrue(StorageModelMigration.requiresMigration(worldDirectory));
		
		// Perform the transformation and verify it no longer needs it.
		File entityDirectory = new File(worldDirectory, "entities");
		entityDirectory.mkdir();
		File cuboidDirectory = new File(worldDirectory, "cuboids");
		cuboidDirectory.mkdir();
		CuboidClusterManager manager = new CuboidClusterManager(cuboidDirectory);
		ByteBuffer scratchBuffer = ByteBuffer.allocate(1024 * 1024);
		Map<String, Integer> phaseCounters = new HashMap<>();
		Map<String, Integer> phaseTotals = new HashMap<>();
		StorageModelMigration.migrateStorage(worldDirectory
			, entityDirectory
			, manager
			, scratchBuffer
			, (String activity, int completedCount, int totalCount) -> {
				int old = phaseCounters.getOrDefault(activity, 0);
				Assert.assertEquals(old + 1, completedCount);
				phaseCounters.put(activity, completedCount);
				if (completedCount == totalCount)
				{
					phaseTotals.put(activity, completedCount);
				}
			}
		);
		Assert.assertFalse(StorageModelMigration.requiresMigration(worldDirectory));
		
		// Verify counters.
		Assert.assertEquals(2, phaseCounters.size());
		Assert.assertEquals(6, phaseCounters.get("Backup").intValue());
		Assert.assertEquals(3, phaseCounters.get("Extract").intValue());
		Assert.assertEquals(2, phaseTotals.size());
		Assert.assertEquals(6, phaseTotals.get("Backup").intValue());
		Assert.assertEquals(3, phaseTotals.get("Extract").intValue());
		
		// Verify the final shape after the conversion.
		Assert.assertEquals(3, worldDirectory.listFiles().length);
		Assert.assertEquals(1, entityDirectory.listFiles().length);
		Assert.assertEquals(1, cuboidDirectory.listFiles().length);
		Assert.assertTrue(new File(worldDirectory, "BACKUP_pre12.zip").isFile());
		Assert.assertTrue(new File(entityDirectory, "entity_1.entity").isFile());
		
		File regionDirectory = new File(cuboidDirectory, "region_0_0_-1.cd8");
		Assert.assertTrue(regionDirectory.isDirectory());
		Assert.assertEquals(1, regionDirectory.listFiles().length);
		Assert.assertTrue(new File(regionDirectory, "cluster_0_0_7.c4").isFile());
	}


	private void _storePerSerialized(File worldDirectory, String fileName, byte[] preSerialized) throws IOException, FileNotFoundException
	{
		try (
			RandomAccessFile aFile = new RandomAccessFile(new File(worldDirectory, fileName), "rw");
			FileChannel outChannel = aFile.getChannel();
		)
		{
			int written = outChannel.write(ByteBuffer.wrap(preSerialized));
			outChannel.truncate((long)written);
			Assert.assertEquals(preSerialized.length, written);
		}
		Assert.assertTrue(new File(worldDirectory, fileName).isFile());
	}
}
