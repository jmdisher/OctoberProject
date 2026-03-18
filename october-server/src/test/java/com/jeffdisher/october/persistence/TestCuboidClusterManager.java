package com.jeffdisher.october.persistence;

import java.io.File;
import java.nio.file.Files;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.october.types.CuboidAddress;


public class TestCuboidClusterManager
{
	@ClassRule
	public static TemporaryFolder DIRECTORY = new TemporaryFolder();

	@Test
	public void empty() throws Throwable
	{
		File topLevel = DIRECTORY.newFolder();
		CuboidClusterManager manager = new CuboidClusterManager(topLevel);
		manager.shutdown();
		
		// This should be empty.
		Assert.assertEquals(0, topLevel.listFiles().length);
	}

	@Test
	public void readWriteSingleLeaves() throws Throwable
	{
		File topLevel = DIRECTORY.newFolder();
		CuboidClusterManager manager = new CuboidClusterManager(topLevel);
		
		CuboidAddress address0 = CuboidAddress.fromInt(-100, 0, 50);
		CuboidAddress address1 = CuboidAddress.fromInt(1100, -200, 50);
		
		byte[] cuboid0 = manager.readCuboid(address0);
		Assert.assertNull(cuboid0);
		byte[] cuboid1 = manager.readCuboid(address1);
		Assert.assertNull(cuboid1);
		
		cuboid0 = new byte[1];
		cuboid1 = new byte[2];
		
		manager.writeCuboid(address0, cuboid0, false);
		manager.writeCuboid(address1, cuboid1, false);
		
		manager.shutdown();
		
		// Make sure that these were written-back into the expected structure.
		File dir0 = new File(topLevel, "region_-4_0_1.cd8");
		File cluster0 = new File(dir0, "cluster_7_0_4.c4");
		File dir1 = new File(topLevel, "region_34_-7_1.cd8");
		File cluster1 = new File(dir1, "cluster_3_6_4.c4");
		Assert.assertTrue(dir0.isDirectory());
		Assert.assertTrue(cluster0.isFile());
		Assert.assertTrue(dir1.isDirectory());
		Assert.assertTrue(cluster1.isFile());
		
		long headerSize = Integer.BYTES + 64 * Integer.BYTES;
		long size0 = headerSize + cuboid0.length;
		long size1 = headerSize + cuboid1.length;
		Assert.assertEquals(size0, Files.size(cluster0.toPath()));
		Assert.assertEquals(size1, Files.size(cluster1.toPath()));
	}

	@Test
	public void readWriteMultipleLeaves() throws Throwable
	{
		File topLevel = DIRECTORY.newFolder();
		CuboidClusterManager manager = new CuboidClusterManager(topLevel);
		
		CuboidAddress address0 = CuboidAddress.fromInt(0, 0, 0);
		CuboidAddress address1 = CuboidAddress.fromInt(2, 2, 2);
		
		byte[] cuboid0 = manager.readCuboid(address0);
		Assert.assertNull(cuboid0);
		byte[] cuboid1 = manager.readCuboid(address1);
		Assert.assertNull(cuboid1);
		
		cuboid0 = new byte[1];
		cuboid1 = new byte[2];
		
		manager.writeCuboid(address0, cuboid0, false);
		manager.writeCuboid(address1, cuboid1, false);
		
		manager.shutdown();
		
		// Make sure that these were written-back into the expected structure.
		File dir0 = new File(topLevel, "region_0_0_0.cd8");
		File cluster0 = new File(dir0, "cluster_0_0_0.c4");
		Assert.assertTrue(dir0.isDirectory());
		Assert.assertTrue(cluster0.isFile());
		
		long headerSize = Integer.BYTES + 64 * Integer.BYTES;
		long size0 = headerSize + cuboid0.length + cuboid1.length;
		Assert.assertEquals(size0, Files.size(cluster0.toPath()));
	}

	@Test
	public void readWriteUpdate() throws Throwable
	{
		File topLevel = DIRECTORY.newFolder();
		CuboidClusterManager manager = new CuboidClusterManager(topLevel);
		
		CuboidAddress address0 = CuboidAddress.fromInt(0, 0, 0);
		
		byte[] cuboid0 = manager.readCuboid(address0);
		Assert.assertNull(cuboid0);
		
		cuboid0 = new byte[1];
		manager.writeCuboid(address0, cuboid0, false);
		
		manager.shutdown();
		File dir0 = new File(topLevel, "region_0_0_0.cd8");
		File cluster0 = new File(dir0, "cluster_0_0_0.c4");
		Assert.assertTrue(dir0.isDirectory());
		Assert.assertTrue(cluster0.isFile());
		long headerSize = Integer.BYTES + 64 * Integer.BYTES;
		long size0 = headerSize + cuboid0.length;
		Assert.assertEquals(size0, Files.size(cluster0.toPath()));
		
		// Re-read this, make some updates, and write them back.
		manager = new CuboidClusterManager(topLevel);
		cuboid0 = manager.readCuboid(address0);
		Assert.assertEquals(1, cuboid0.length);
		
		cuboid0 = new byte[2];
		manager.writeCuboid(address0, cuboid0, true);
		Assert.assertEquals(headerSize + cuboid0.length, Files.size(cluster0.toPath()));
		
		cuboid0 = new byte[3];
		manager.writeCuboid(address0, cuboid0, false);
		manager.shutdown();
		Assert.assertEquals(headerSize + cuboid0.length, Files.size(cluster0.toPath()));
	}
}
