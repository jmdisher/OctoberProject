package com.jeffdisher.october.data;

import java.nio.ByteBuffer;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.registries.AspectRegistry;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.BlockAddress;


public class TestOctreeBlock
{
	@Test
	public void filled()
	{
		OctreeShort test = OctreeShort.create(ItemRegistry.AIR.number());
		Assert.assertEquals(ItemRegistry.AIR.number(), (short)test.getData(AspectRegistry.BLOCK, new BlockAddress((byte)0, (byte)0, (byte)0)));
		Assert.assertEquals(ItemRegistry.AIR.number(), (short)test.getData(AspectRegistry.BLOCK, new BlockAddress((byte)31, (byte)31, (byte)31)));
	}

	@Test
	public void update()
	{
		OctreeShort test = OctreeShort.create(ItemRegistry.AIR.number());
		
		// Write a value into each subtree.
		_setAllSubtrees(test, (byte)0, ItemRegistry.STONE.number());
		// Check that it changed, but not adjacent blocks.
		_checkAllSubtrees(test, (byte)0, ItemRegistry.STONE.number());
		_checkAllSubtrees(test, (byte)1, ItemRegistry.AIR.number());
		
		// Change it back, causing it to coalesce.
		_setAllSubtrees(test, (byte)0, ItemRegistry.AIR.number());
		_checkAllSubtrees(test, (byte)0, ItemRegistry.AIR.number());
		_checkAllSubtrees(test, (byte)1, ItemRegistry.AIR.number());
	}

	@Test
	public void worstCaseShort()
	{
		// Fill an octree with each possible value (32x32x32 is 32768).
		short value = 0;
		// We init to 0 and will set the first block to 0.
		OctreeShort test = OctreeShort.create(value);
		long startStore = System.currentTimeMillis();
		for (int x = 0; x < 32; ++x)
		{
			long start = System.currentTimeMillis();
			for (int y = 0; y < 32; ++y)
			{
				for (int z = 0; z < 32; ++z)
				{
					test.setData(new BlockAddress((byte)x, (byte)y, (byte)z), value);
					value += 1;
				}
			}
			long end = System.currentTimeMillis();
			System.out.println("Store X: " + x + " took " + (end -start) + " millis");
		}
		long endStore = System.currentTimeMillis();
		System.out.println("Store total took " + (endStore -startStore) + " millis");
		
		OctreeShort verify = _codec(test, 70216);
		
		// Verify that we can read all of these.
		value = 0;
		startStore = System.currentTimeMillis();
		for (int x = 0; x < 32; ++x)
		{
			long start = System.currentTimeMillis();
			for (int y = 0; y < 32; ++y)
			{
				for (int z = 0; z < 32; ++z)
				{
					Assert.assertEquals(value, (short)verify.getData(AspectRegistry.BLOCK, new BlockAddress((byte)x, (byte)y, (byte)z)));
					value += 1;
				}
			}
			long end = System.currentTimeMillis();
			System.out.println("Load X: " + x + " took " + (end -start) + " millis");
		}
		endStore = System.currentTimeMillis();
		System.out.println("Load total took " + (endStore -startStore) + " millis");
		
		// Clear all the bytes.
		startStore = System.currentTimeMillis();
		for (int x = 0; x < 32; ++x)
		{
			long start = System.currentTimeMillis();
			for (int y = 0; y < 32; ++y)
			{
				for (int z = 0; z < 32; ++z)
				{
					verify.setData(new BlockAddress((byte)x, (byte)y, (byte)z), (short)0);
				}
			}
			long end = System.currentTimeMillis();
			System.out.println("Clean X: " + x + " took " + (end -start) + " millis");
		}
		endStore = System.currentTimeMillis();
		System.out.println("Clean total took " + (endStore -startStore) + " millis");
		
		verify = _codec(verify, 2);
		
		// Perform final verification.
		value = 0;
		startStore = System.currentTimeMillis();
		for (int x = 0; x < 32; ++x)
		{
			long start = System.currentTimeMillis();
			for (int y = 0; y < 32; ++y)
			{
				for (int z = 0; z < 32; ++z)
				{
					Assert.assertEquals(0, (short)verify.getData(AspectRegistry.BLOCK, new BlockAddress((byte)x, (byte)y, (byte)z)));
				}
			}
			long end = System.currentTimeMillis();
			System.out.println("Verify X: " + x + " took " + (end -start) + " millis");
		}
		endStore = System.currentTimeMillis();
		System.out.println("Verify total took " + (endStore -startStore) + " millis");
	}

	@Test
	public void worstCaseByte()
	{
		// Fill an octree with each possible value (this will cycle since byte range is only [0..127]).
		byte value = 0;
		// We init to 0 and will set the first block to 0.
		OctreeByte test = OctreeByte.create(value);
		long startStore = System.currentTimeMillis();
		for (int x = 0; x < 32; ++x)
		{
			long start = System.currentTimeMillis();
			for (int y = 0; y < 32; ++y)
			{
				for (int z = 0; z < 32; ++z)
				{
					test.setData(new BlockAddress((byte)x, (byte)y, (byte)z), value);
					value += 1;
					if (value < 0)
					{
						value = 0;
					}
				}
			}
			long end = System.currentTimeMillis();
			System.out.println("Store X: " + x + " took " + (end -start) + " millis");
		}
		long endStore = System.currentTimeMillis();
		System.out.println("Store total took " + (endStore -startStore) + " millis");
		
		// This is precisely 32 KiB less than the short test.
		OctreeByte verify = _codecByte(test, 37448);
		
		// Verify that we can read all of these.
		value = 0;
		startStore = System.currentTimeMillis();
		for (int x = 0; x < 32; ++x)
		{
			long start = System.currentTimeMillis();
			for (int y = 0; y < 32; ++y)
			{
				for (int z = 0; z < 32; ++z)
				{
					Assert.assertEquals(value, (byte)verify.getData(new Aspect<Byte, IOctree>(0, Byte.class, null, null, null, null), new BlockAddress((byte)x, (byte)y, (byte)z)));
					value += 1;
					if (value < 0)
					{
						value = 0;
					}
				}
			}
			long end = System.currentTimeMillis();
			System.out.println("Load X: " + x + " took " + (end -start) + " millis");
		}
		endStore = System.currentTimeMillis();
		System.out.println("Load total took " + (endStore -startStore) + " millis");
		
		// Clear all the bytes.
		startStore = System.currentTimeMillis();
		for (int x = 0; x < 32; ++x)
		{
			long start = System.currentTimeMillis();
			for (int y = 0; y < 32; ++y)
			{
				for (int z = 0; z < 32; ++z)
				{
					verify.setData(new BlockAddress((byte)x, (byte)y, (byte)z), (byte)0);
				}
			}
			long end = System.currentTimeMillis();
			System.out.println("Clean X: " + x + " took " + (end -start) + " millis");
		}
		endStore = System.currentTimeMillis();
		System.out.println("Clean total took " + (endStore -startStore) + " millis");
		
		verify = _codecByte(verify, 2);
		
		// Perform final verification.
		value = 0;
		startStore = System.currentTimeMillis();
		for (int x = 0; x < 32; ++x)
		{
			long start = System.currentTimeMillis();
			for (int y = 0; y < 32; ++y)
			{
				for (int z = 0; z < 32; ++z)
				{
					Assert.assertEquals(0, (byte)verify.getData(new Aspect<Byte, IOctree>(0, Byte.class, null, null, null, null), new BlockAddress((byte)x, (byte)y, (byte)z)));
				}
			}
			long end = System.currentTimeMillis();
			System.out.println("Verify X: " + x + " took " + (end -start) + " millis");
		}
		endStore = System.currentTimeMillis();
		System.out.println("Verify total took " + (endStore -startStore) + " millis");
	}


	private static OctreeShort _codec(OctreeShort input, int expectedSizeBytes)
	{
		// Note that the test is asking about the size of the actual data but the serializer has a size header.
		int headerSize = (expectedSizeBytes > Short.BYTES) ? (8 * Short.BYTES) : 0;
		ByteBuffer buffer = ByteBuffer.allocate(expectedSizeBytes + headerSize);
		Object state = input.serializeResumable(null, buffer, null);
		Assert.assertNull(state);
		Assert.assertFalse(buffer.hasRemaining());
		
		buffer.flip();
		OctreeShort output = OctreeShort.empty();
		state = output.deserializeResumable(null, buffer, null);
		Assert.assertNull(state);
		Assert.assertFalse(buffer.hasRemaining());
		return output;
	}

	private static void _setAllSubtrees(OctreeShort input, byte offset, short value)
	{
		input.setData(new BlockAddress((byte)0, (byte)0, (byte)(0 + offset)), value);
		input.setData(new BlockAddress((byte)0, (byte)0, (byte)(16 + offset)), value);
		input.setData(new BlockAddress((byte)0, (byte)16, (byte)(0 + offset)), value);
		input.setData(new BlockAddress((byte)0, (byte)16, (byte)(16 + offset)), value);
		input.setData(new BlockAddress((byte)16, (byte)0, (byte)(0 + offset)), value);
		input.setData(new BlockAddress((byte)16, (byte)0, (byte)(16 + offset)), value);
		input.setData(new BlockAddress((byte)16, (byte)16, (byte)(0 + offset)), value);
		input.setData(new BlockAddress((byte)16, (byte)16, (byte)(16 + offset)), value);
	}

	private static void _checkAllSubtrees(OctreeShort input, byte offset, short value)
	{
		Assert.assertEquals(value, (short)input.getData(AspectRegistry.BLOCK, new BlockAddress((byte)0, (byte)0, (byte)(0 + offset))));
		Assert.assertEquals(value, (short)input.getData(AspectRegistry.BLOCK, new BlockAddress((byte)0, (byte)0, (byte)(16 + offset))));
		Assert.assertEquals(value, (short)input.getData(AspectRegistry.BLOCK, new BlockAddress((byte)0, (byte)16, (byte)(0 + offset))));
		Assert.assertEquals(value, (short)input.getData(AspectRegistry.BLOCK, new BlockAddress((byte)0, (byte)16, (byte)(16 + offset))));
		Assert.assertEquals(value, (short)input.getData(AspectRegistry.BLOCK, new BlockAddress((byte)16, (byte)0, (byte)(0 + offset))));
		Assert.assertEquals(value, (short)input.getData(AspectRegistry.BLOCK, new BlockAddress((byte)16, (byte)0, (byte)(16 + offset))));
		Assert.assertEquals(value, (short)input.getData(AspectRegistry.BLOCK, new BlockAddress((byte)16, (byte)16, (byte)(0 + offset))));
		Assert.assertEquals(value, (short)input.getData(AspectRegistry.BLOCK, new BlockAddress((byte)16, (byte)16, (byte)(16 + offset))));
	}

	private static OctreeByte _codecByte(OctreeByte input, int expectedSizeBytes)
	{
		// Note that the test is asking about the size of the actual data but the serializer has a size header.
		int headerSize = (expectedSizeBytes > Short.BYTES) ? (8 * Short.BYTES) : 0;
		ByteBuffer buffer = ByteBuffer.allocate(expectedSizeBytes + headerSize);
		Object state = input.serializeResumable(null, buffer, null);
		Assert.assertNull(state);
		Assert.assertFalse(buffer.hasRemaining());
		
		buffer.flip();
		OctreeByte output = OctreeByte.empty();
		state = output.deserializeResumable(null, buffer, null);
		Assert.assertNull(state);
		Assert.assertFalse(buffer.hasRemaining());
		return output;
	}
}
