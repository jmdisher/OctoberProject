package com.jeffdisher.october.data;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.Item;


public class TestOctreeBlock
{
	private static Environment ENV;
	@BeforeClass
	public static void setup() throws Throwable
	{
		ENV = Environment.createSharedInstance();
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void filled()
	{
		OctreeShort test = OctreeShort.create(ENV.special.AIR.item().number());
		Assert.assertEquals(ENV.special.AIR.item().number(), (short)test.getData(AspectRegistry.BLOCK, BlockAddress.fromInt(0, 0, 0)));
		Assert.assertEquals(ENV.special.AIR.item().number(), (short)test.getData(AspectRegistry.BLOCK, BlockAddress.fromInt(31, 31, 31)));
	}

	@Test
	public void update()
	{
		Item stoneItem = ENV.items.getItemById("op.stone");
		OctreeShort test = OctreeShort.create(ENV.special.AIR.item().number());
		
		// Write a value into each subtree.
		_setAllSubtrees(test, (byte)0, stoneItem.number());
		// Check that it changed, but not adjacent blocks.
		_checkAllSubtrees(test, (byte)0, stoneItem.number());
		_checkAllSubtrees(test, (byte)1, ENV.special.AIR.item().number());
		
		// Change it back, causing it to coalesce.
		_setAllSubtrees(test, (byte)0, ENV.special.AIR.item().number());
		_checkAllSubtrees(test, (byte)0, ENV.special.AIR.item().number());
		_checkAllSubtrees(test, (byte)1, ENV.special.AIR.item().number());
	}

	@Test
	public void worstCaseShort()
	{
		// Fill an octree with each possible value (32x32x32 is 32768).
		short value = 0;
		// We init to 0 and will set the first block to 0.
		OctreeShort test = OctreeShort.create(value);
		long startStore = System.currentTimeMillis();
		for (byte x = 0; x < 32; ++x)
		{
			long start = System.currentTimeMillis();
			for (byte y = 0; y < 32; ++y)
			{
				for (byte z = 0; z < 32; ++z)
				{
					test.setData(new BlockAddress(x, y, z), value);
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
		for (byte x = 0; x < 32; ++x)
		{
			long start = System.currentTimeMillis();
			for (byte y = 0; y < 32; ++y)
			{
				for (byte z = 0; z < 32; ++z)
				{
					Assert.assertEquals(value, (short)verify.getData(AspectRegistry.BLOCK, new BlockAddress(x, y, z)));
					value += 1;
				}
			}
			long end = System.currentTimeMillis();
			System.out.println("Load X: " + x + " took " + (end -start) + " millis");
		}
		endStore = System.currentTimeMillis();
		System.out.println("Load total took " + (endStore -startStore) + " millis");
		
		// Check the cost of the batch read.
		BlockAddress[] array = new BlockAddress[32 * 32 * 32];
		int index = 0;
		for (byte x = 0; x < 32; ++x)
		{
			for (byte y = 0; y < 32; ++y)
			{
				for (byte z = 0; z < 32; ++z)
				{
					array[index] = BlockAddress.fromInt(x, y, z);
					index += 1;
				}
			}
		}
		Arrays.sort(array, new IReadOnlyCuboidData.BlockAddressBatchComparator());
		short[] shorts = new short[32 * 32 * 32];
		startStore = System.currentTimeMillis();
		verify.readBatch(shorts, array);
		endStore = System.currentTimeMillis();
		System.out.println("Full batch load total took " + (endStore -startStore) + " millis");
		
		// Clear all the bytes.
		startStore = System.currentTimeMillis();
		for (byte x = 0; x < 32; ++x)
		{
			long start = System.currentTimeMillis();
			for (byte y = 0; y < 32; ++y)
			{
				for (byte z = 0; z < 32; ++z)
				{
					verify.setData(new BlockAddress(x, y, z), (short)0);
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
		for (byte x = 0; x < 32; ++x)
		{
			long start = System.currentTimeMillis();
			for (byte y = 0; y < 32; ++y)
			{
				for (byte z = 0; z < 32; ++z)
				{
					Assert.assertEquals(0, (short)verify.getData(AspectRegistry.BLOCK, new BlockAddress(x, y, z)));
				}
			}
			long end = System.currentTimeMillis();
			System.out.println("Verify X: " + x + " took " + (end -start) + " millis");
		}
		endStore = System.currentTimeMillis();
		System.out.println("Verify total took " + (endStore -startStore) + " millis");
		
		// Also check the walking callback.
		int[] count = new int[] {0};
		test.walkData((BlockAddress base, byte size, Short ignored) -> {
			count[0] += 1;
		}, value);
		long endWalk = System.currentTimeMillis();
		System.out.println("Callback walk took " + (endWalk - endStore) + " millis");
		Assert.assertEquals(32 * 32 * 32 -1, count[0]);
	}

	@Test
	public void worstCaseByte()
	{
		// Fill an octree with each possible value (this will cycle since byte range is only [0..127]).
		byte value = 0;
		// We init to 0 and will set the first block to 0.
		OctreeByte test = OctreeByte.create(value);
		long startStore = System.currentTimeMillis();
		for (byte x = 0; x < 32; ++x)
		{
			long start = System.currentTimeMillis();
			for (byte y = 0; y < 32; ++y)
			{
				for (byte z = 0; z < 32; ++z)
				{
					test.setData(new BlockAddress(x, y, z), value);
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
		for (byte x = 0; x < 32; ++x)
		{
			long start = System.currentTimeMillis();
			for (byte y = 0; y < 32; ++y)
			{
				for (byte z = 0; z < 32; ++z)
				{
					Assert.assertEquals(value, (byte)verify.getData(AspectRegistry.LIGHT, new BlockAddress(x, y, z)));
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
		for (byte x = 0; x < 32; ++x)
		{
			long start = System.currentTimeMillis();
			for (byte y = 0; y < 32; ++y)
			{
				for (byte z = 0; z < 32; ++z)
				{
					verify.setData(new BlockAddress(x, y, z), (byte)0);
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
		for (byte x = 0; x < 32; ++x)
		{
			long start = System.currentTimeMillis();
			for (byte y = 0; y < 32; ++y)
			{
				for (byte z = 0; z < 32; ++z)
				{
					Assert.assertEquals(0, (byte)verify.getData(AspectRegistry.LIGHT, new BlockAddress(x, y, z)));
				}
			}
			long end = System.currentTimeMillis();
			System.out.println("Verify X: " + x + " took " + (end -start) + " millis");
		}
		endStore = System.currentTimeMillis();
		System.out.println("Verify total took " + (endStore -startStore) + " millis");
	}

	@Test
	public void walkByte()
	{
		// Put a few values into a byte octree and observe the walker callbacks.
		byte start = 0;
		OctreeByte test = OctreeByte.create(start);
		for (int x = 0; x < 2; ++x)
		{
			for (int y = 0; y < 2; ++y)
			{
				for (int z = 0; z < 2; ++z)
				{
					test.setData(BlockAddress.fromInt((2 + x), (2 + y), (2 + z)), (byte)1);
				}
			}
		}
		test.setData(BlockAddress.fromInt(18, 29, 7), (byte)2);
		
		BlockAddress[] bases = new BlockAddress[] {
				BlockAddress.fromInt(2, 2, 2),
				BlockAddress.fromInt(18, 29, 7),
		};
		byte[] sizes = new byte[] {
				(byte)2,
				(byte)1,
		};
		byte[] values = new byte[] {
				1,
				2,
		};
		int[] cursor = new int[] {0};
		test.walkData((BlockAddress base, byte size, Byte value) -> {
			int index = cursor[0];
			cursor[0] += 1;
			Assert.assertEquals(bases[index], base);
			Assert.assertEquals(sizes[index], size);
			Assert.assertEquals(values[index], value.byteValue());
		}, start);
	}

	@Test
	public void walkShort()
	{
		// Put a few values into a short octree and observe the walker callbacks.
		short start = 0;
		OctreeShort test = OctreeShort.create(start);
		for (int x = 0; x < 4; ++x)
		{
			for (int y = 0; y < 4; ++y)
			{
				for (int z = 0; z < 4; ++z)
				{
					test.setData(BlockAddress.fromInt((28 + x), (28 + y), (28 + z)), (short)1);
				}
			}
		}
		test.setData(BlockAddress.fromInt(0, 0, 0), (short)2);
		
		BlockAddress[] bases = new BlockAddress[] {
				BlockAddress.fromInt(0, 0, 0),
				BlockAddress.fromInt(28, 28, 28),
		};
		byte[] sizes = new byte[] {
				(byte)1,
				(byte)4,
		};
		short[] values = new short[] {
				2,
				1,
		};
		int[] cursor = new int[] {0};
		test.walkData((BlockAddress base, byte size, Short value) -> {
			int index = cursor[0];
			cursor[0] += 1;
			Assert.assertEquals(bases[index], base);
			Assert.assertEquals(sizes[index], size);
			Assert.assertEquals(values[index], value.shortValue());
		}, start);
	}

	@Test
	public void walkObject()
	{
		// Put a few instances into the object tree and walk them.
		OctreeObject<Integer> test = OctreeObject.create();
		test.setData(BlockAddress.fromInt(28, 29, 7), Integer.valueOf(1));
		test.setData(BlockAddress.fromInt(0, 0, 0), Integer.valueOf(2));
		
		BlockAddress[] bases = new BlockAddress[] {
				BlockAddress.fromInt(0, 0, 0),
				BlockAddress.fromInt(28, 29, 7),
		};
		byte sizes = (byte)1;
		int[] values = new int[] {
				2,
				1,
		};
		int[] cursor = new int[] {0};
		test.walkData((BlockAddress base, byte size, Integer value) -> {
			int index = cursor[0];
			cursor[0] += 1;
			Assert.assertEquals(bases[index], base);
			Assert.assertEquals(sizes, size);
			Assert.assertEquals(values[index], value.intValue());
		}, null);
	}

	@Test
	public void inflatedByte()
	{
		OctreeInflatedByte empty = OctreeInflatedByte.empty();
		Aspect<Byte, OctreeInflatedByte> aspect = new Aspect<>(0
				, Byte.class
				, OctreeInflatedByte.class
				, () -> OctreeInflatedByte.empty()
				, (OctreeInflatedByte original) -> {
					return original.cloneData();
				}
				, null
		);
		BlockAddress blockAddress = BlockAddress.fromInt(14, 15, 16);
		Assert.assertEquals((byte)0, empty.getData(aspect, blockAddress).byteValue());
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		Object state = empty.serializeResumable(null, buffer, null);
		Assert.assertNull(state);
		
		buffer.flip();
		Assert.assertEquals(1, buffer.remaining());
		OctreeInflatedByte test = OctreeInflatedByte.empty();
		DeserializationContext context = DeserializationContext.empty(Environment.getShared()
			, buffer
		);
		state = test.deserializeResumable(null, context, null);
		Assert.assertNull(state);
		
		// Verify that we can safely walk the empty inflated byte.
		test.walkData((BlockAddress base, byte size, Byte value) -> {
			Assert.fail("There is no data to walk");
		}, (byte)0);
		
		buffer.clear();
		test.setData(blockAddress, (byte)5);
		state = test.serializeResumable(null, buffer, null);
		Assert.assertNull(state);
		buffer.flip();
		Assert.assertEquals(36, buffer.remaining());
		OctreeInflatedByte test2 = OctreeInflatedByte.empty();
		context = DeserializationContext.empty(Environment.getShared()
			, buffer
		);
		state = test2.deserializeResumable(null, context, null);
		Assert.assertNull(state);
		Assert.assertEquals((byte)5, test2.getData(aspect, blockAddress).byteValue());
		Assert.assertEquals((byte)0, test2.getData(aspect, BlockAddress.fromInt(13, 14, 15)).byteValue());
		
		// Verify that we correctly walk this data.
		test.walkData((BlockAddress base, byte size, Byte value) -> {
			Assert.assertEquals(blockAddress, base);
			Assert.assertEquals(1, size);
			Assert.assertEquals(5, value.byteValue());
		}, (byte)0);
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
		DeserializationContext context = DeserializationContext.empty(Environment.getShared()
			, buffer
		);
		state = output.deserializeResumable(null, context, null);
		Assert.assertNull(state);
		Assert.assertFalse(buffer.hasRemaining());
		return output;
	}

	private static void _setAllSubtrees(OctreeShort input, byte offset, short value)
	{
		input.setData(BlockAddress.fromInt(0, 0, (0 + offset)), value);
		input.setData(BlockAddress.fromInt(0, 0, (16 + offset)), value);
		input.setData(BlockAddress.fromInt(0, 16, (0 + offset)), value);
		input.setData(BlockAddress.fromInt(0, 16, (16 + offset)), value);
		input.setData(BlockAddress.fromInt(16, 0, (0 + offset)), value);
		input.setData(BlockAddress.fromInt(16, 0, (16 + offset)), value);
		input.setData(BlockAddress.fromInt(16, 16, (0 + offset)), value);
		input.setData(BlockAddress.fromInt(16, 16, (16 + offset)), value);
	}

	private static void _checkAllSubtrees(OctreeShort input, byte offset, short value)
	{
		Assert.assertEquals(value, (short)input.getData(AspectRegistry.BLOCK, BlockAddress.fromInt(0, 0, (0 + offset))));
		Assert.assertEquals(value, (short)input.getData(AspectRegistry.BLOCK, BlockAddress.fromInt(0, 0, (16 + offset))));
		Assert.assertEquals(value, (short)input.getData(AspectRegistry.BLOCK, BlockAddress.fromInt(0, 16, (0 + offset))));
		Assert.assertEquals(value, (short)input.getData(AspectRegistry.BLOCK, BlockAddress.fromInt(0, 16, (16 + offset))));
		Assert.assertEquals(value, (short)input.getData(AspectRegistry.BLOCK, BlockAddress.fromInt(16, 0, (0 + offset))));
		Assert.assertEquals(value, (short)input.getData(AspectRegistry.BLOCK, BlockAddress.fromInt(16, 0, (16 + offset))));
		Assert.assertEquals(value, (short)input.getData(AspectRegistry.BLOCK, BlockAddress.fromInt(16, 16, (0 + offset))));
		Assert.assertEquals(value, (short)input.getData(AspectRegistry.BLOCK, BlockAddress.fromInt(16, 16, (16 + offset))));
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
		DeserializationContext context = DeserializationContext.empty(Environment.getShared()
			, buffer
		);
		state = output.deserializeResumable(null, context, null);
		Assert.assertNull(state);
		Assert.assertFalse(buffer.hasRemaining());
		return output;
	}
}
