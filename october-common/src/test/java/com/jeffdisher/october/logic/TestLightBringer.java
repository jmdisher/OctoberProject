package com.jeffdisher.october.logic;

import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.aspects.LightAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.registries.AspectRegistry;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.worldgen.CuboidGenerator;


public class TestLightBringer
{
	@Test
	public void singleLightSource()
	{
		// Create a single light source in the middle of an air cuboid and check how it propagates.
		CuboidAddress address = new CuboidAddress((short)10, (short)10, (short)10);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ItemRegistry.AIR);
		AbsoluteLocation centre = address.getBase().getRelative(16, 16, 16);
		MutableBlockProxy source = new MutableBlockProxy(centre, cuboid);
		source.setLight((byte)15);
		source.writeBack(cuboid);
		
		// We will show how this scales up with different light intensities (but not write back).
		int[] expectedValues = new int[] {
				0,
				0,
				6,
				24,
				62,
				128,
				230,
				376,
				574,
				832,
				1158,
				1560,
				2046,
				2624,
				3302,
				4088,
		};
		for (byte i = 2; i <= LightAspect.MAX_LIGHT; ++i)
		{
			source.setLight(i);
			source.writeBack(cuboid);
			_OneCuboidLookupCache lookup = new _OneCuboidLookupCache(cuboid);
			Map<AbsoluteLocation, Byte> updates = LightBringer.spreadLight(lookup.lightLookup, lookup.opacityLookup, centre, i);
			Assert.assertEquals(expectedValues[i], updates.size());
		}
	}

	@Test
	public void addRemoveTwoSource()
	{
		byte value = LightAspect.MAX_LIGHT;
		// Create a single light source in the middle of an air cuboid and check how it propagates.
		CuboidAddress address = new CuboidAddress((short)10, (short)10, (short)10);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ItemRegistry.AIR);
		AbsoluteLocation centre = address.getBase().getRelative(16, 16, 16);
		cuboid.setData7(AspectRegistry.LIGHT, centre.getBlockAddress(), value);
		
		_OneCuboidLookupCache lookup = new _OneCuboidLookupCache(cuboid);
		Map<AbsoluteLocation, Byte> initialSet = LightBringer.spreadLight(lookup.lightLookup, lookup.opacityLookup, centre, value);
		Assert.assertEquals(4088, initialSet.size());
		for (Map.Entry<AbsoluteLocation, Byte> update : initialSet.entrySet())
		{
			cuboid.setData7(AspectRegistry.LIGHT, update.getKey().getBlockAddress(), update.getValue());
		}
		ByteBuffer buffer = ByteBuffer.allocate(40000);
		Assert.assertNull(cuboid.serializeResumable(null, buffer));
		
		// Add a second light source near the first.
		AbsoluteLocation secondLight = centre.getRelative(1, 1, 1);
		_writeLight(System.out, cuboid, secondLight.getBlockAddress().z());
		cuboid.setData7(AspectRegistry.LIGHT, secondLight.getBlockAddress(), value);
		lookup = new _OneCuboidLookupCache(cuboid);
		Map<AbsoluteLocation, Byte> secondUpdates = LightBringer.spreadLight(lookup.lightLookup, lookup.opacityLookup, secondLight, value);
		Assert.assertEquals(2359, secondUpdates.size());
		for (Map.Entry<AbsoluteLocation, Byte> update : secondUpdates.entrySet())
		{
			cuboid.setData7(AspectRegistry.LIGHT, update.getKey().getBlockAddress(), update.getValue());
		}
		_writeLight(System.out, cuboid, secondLight.getBlockAddress().z());
		
		// Now, remove the light source and watch the updates.
		cuboid.setData7(AspectRegistry.LIGHT, secondLight.getBlockAddress(), (byte)0);
		lookup = new _OneCuboidLookupCache(cuboid);
		Map<AbsoluteLocation, Byte> resetUpdates = LightBringer.removeLight(lookup.lightLookup, lookup.opacityLookup, centre.getRelative(1, 1, 1), value);
		Assert.assertEquals(2360, resetUpdates.size());
		for (Map.Entry<AbsoluteLocation, Byte> update : resetUpdates.entrySet())
		{
			cuboid.setData7(AspectRegistry.LIGHT, update.getKey().getBlockAddress(), update.getValue());
		}
		_writeLight(System.out, cuboid, secondLight.getBlockAddress().z());
		
		ByteBuffer buffer2 = ByteBuffer.allocate(40000);
		Assert.assertNull(cuboid.serializeResumable(null, buffer2));
		Assert.assertArrayEquals(buffer.array(), buffer2.array());
		
		// We expect that the reset will include 1 additional update since it re-lights the second block source.
		int missingCount = 0;
		for (AbsoluteLocation loc : resetUpdates.keySet())
		{
			if (!secondUpdates.containsKey(loc))
			{
				// This should only happen for the second light location, since it is re-lit by the first light.
				missingCount += 1;
				Assert.assertEquals(secondLight, loc);
			}
		}
		Assert.assertEquals(1, missingCount);
	}

	@Test
	public void maze()
	{
		// Add and them remove a single light source in a 2D maze, in a block of stone.
		byte value = LightAspect.MAX_LIGHT;
		CuboidAddress address = new CuboidAddress((short)10, (short)10, (short)10);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ItemRegistry.STONE);
		BlockAddress source = _loadBlockMap(cuboid, (byte)16, ""
				+ "SSSSSSSSSSSSSSS SSSSSSSSSSSSSSSS"
				+ "SSSSSSSSSSSSSSS SSSSSSSSSSSSSSSS"
				+ "SSSSSSSSSSSSSSS WWWSSSSSSSSSSSSS"
				+ "SSSSSSSSSSSSSSS SSSSSSSSSSSSSSSS"
				+ "SSSSSSSSSS   WW SSSSSSSSSSSSSSSS"
				+ "SSSSSSSSSSSSSSS SSSSSSSSSSSSSSSS"
				+ "SSSSSSSSSSSSS W SSSSSSSSSSSSSSSS"
				+ "SSSSSSSSSSSSS S W   SSSSSSSSSSSS"
				+ "SSSSSSSSSSSSS S SSSSSSSSSSSSSSSS"
				+ "SSSSSSSSSSSSS SXSSSSSSSSSSSSSSSS"
				+ "SSSSSSSSSS  W W  SSSSSSSSSSSSSSS"
				+ "SSSSSSSSSSSSSSSS SSSSSSSSSSSSSSS"
				+ "SSSSSSSSSSSSSSSS  SSSSSSSSSSSSSS"
				+ "SSSSSSSSSSSSSSSSS  SSSSSSSSSSSSS"
				+ "SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS"
				+ "SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS"
				+ "SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS"
				+ "SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS"
				+ "SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS"
				+ "SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS"
				+ "SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS"
				+ "SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS"
				+ "SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS"
				+ "SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS"
				+ "SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS"
				+ "SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS"
				+ "SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS"
				+ "SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS"
				+ "SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS"
				+ "SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS"
				+ "SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS"
				+ "SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS"
		);
		ByteBuffer buffer = ByteBuffer.allocate(40000);
		Assert.assertNull(cuboid.serializeResumable(null, buffer));
		
		cuboid.setData7(AspectRegistry.LIGHT, source, value);
		AbsoluteLocation target = address.getBase().getRelative(source.x(), source.y(), source.z());
		
		_OneCuboidLookupCache lookup = new _OneCuboidLookupCache(cuboid);
		Map<AbsoluteLocation, Byte> updates = LightBringer.spreadLight(lookup.lightLookup, lookup.opacityLookup, target, value);
		// We expect 38 updates to fill the maze.
		int expectedUpdates = 38;
		Assert.assertEquals(expectedUpdates, updates.size());
		for (Map.Entry<AbsoluteLocation, Byte> update : updates.entrySet())
		{
			cuboid.setData7(AspectRegistry.LIGHT, update.getKey().getBlockAddress(), update.getValue());
		}
		_writeLight(System.out, cuboid, source.z());
		
		cuboid.setData7(AspectRegistry.LIGHT, source, (byte)0);
		lookup = new _OneCuboidLookupCache(cuboid);
		updates = LightBringer.removeLight(lookup.lightLookup, lookup.opacityLookup, target, value);
		// The same number to clear the maze.
		Assert.assertEquals(expectedUpdates, updates.size());
		for (Map.Entry<AbsoluteLocation, Byte> update : updates.entrySet())
		{
			cuboid.setData7(AspectRegistry.LIGHT, update.getKey().getBlockAddress(), update.getValue());
		}
		
		ByteBuffer buffer2 = ByteBuffer.allocate(40000);
		Assert.assertNull(cuboid.serializeResumable(null, buffer2));
		Assert.assertArrayEquals(buffer.array(), buffer2.array());
	}

	@Test
	public void murky()
	{
		// We want to check adding a few light sources to a high-opacity environment and then remove some.
		CuboidAddress address = new CuboidAddress((short)10, (short)10, (short)10);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ItemRegistry.AIR);
		AbsoluteLocation centre = address.getBase().getRelative(16, 16, 16);
		
		// We will use the light values from the cuboid but a fixed high opacity.
		LightBringer.IByteLookup opacity = (AbsoluteLocation location) -> 4;
		
		// Set a light on either side of the centre.
		_OneCuboidLookupCache lookup = new _OneCuboidLookupCache(cuboid);
		AbsoluteLocation west = centre.getRelative(-1, 0, 0);
		cuboid.setData7(AspectRegistry.LIGHT, west.getBlockAddress(), (byte)15);
		Map<AbsoluteLocation, Byte> updates = LightBringer.spreadLight(lookup.lightLookup, opacity, west, (byte)15);
		for (Map.Entry<AbsoluteLocation, Byte> update : updates.entrySet())
		{
			cuboid.setData7(AspectRegistry.LIGHT, update.getKey().getBlockAddress(), update.getValue());
		}
		lookup = new _OneCuboidLookupCache(cuboid);
		AbsoluteLocation east = centre.getRelative(1, 0, 0);
		cuboid.setData7(AspectRegistry.LIGHT, east.getBlockAddress(), (byte)15);
		updates = LightBringer.spreadLight(lookup.lightLookup, opacity, east, (byte)15);
		for (Map.Entry<AbsoluteLocation, Byte> update : updates.entrySet())
		{
			cuboid.setData7(AspectRegistry.LIGHT, update.getKey().getBlockAddress(), update.getValue());
		}
		_writeLight(System.out, cuboid, centre.getBlockAddress().z());
		Assert.assertEquals((byte)11, cuboid.getData7(AspectRegistry.LIGHT, centre.getBlockAddress()));
		
		// Remove the light on either side of the centre.
		lookup = new _OneCuboidLookupCache(cuboid);
		cuboid.setData7(AspectRegistry.LIGHT, west.getBlockAddress(), (byte)0);
		updates = LightBringer.removeLight(lookup.lightLookup, opacity, west, (byte)15);
		for (Map.Entry<AbsoluteLocation, Byte> update : updates.entrySet())
		{
			cuboid.setData7(AspectRegistry.LIGHT, update.getKey().getBlockAddress(), update.getValue());
		}
		lookup = new _OneCuboidLookupCache(cuboid);
		cuboid.setData7(AspectRegistry.LIGHT, east.getBlockAddress(), (byte)0);
		updates = LightBringer.removeLight(lookup.lightLookup, opacity, east, (byte)15);
		for (Map.Entry<AbsoluteLocation, Byte> update : updates.entrySet())
		{
			cuboid.setData7(AspectRegistry.LIGHT, update.getKey().getBlockAddress(), update.getValue());
		}
		_writeLight(System.out, cuboid, centre.getBlockAddress().z());
		Assert.assertEquals((byte)0, cuboid.getData7(AspectRegistry.LIGHT, centre.getBlockAddress()));
	}

	@Test
	public void differentOpacity()
	{
		// Make sure that the evaluation order is correct, even when opacity differs via different paths.
		CuboidAddress address = new CuboidAddress((short)10, (short)10, (short)10);
		AbsoluteLocation centre = address.getBase().getRelative(16, 16, 16);
		
		// In this case, we only use a real cuboid for the light levels and fixed values for opacity, based on location.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ItemRegistry.AIR);
		_OneCuboidLookupCache lookup = new _OneCuboidLookupCache(cuboid);
		LightBringer.IByteLookup opacity = (AbsoluteLocation location) ->
		{
			// We will say that only the centre z-level is not fully opaque but say that south or west directions are more opaque.
			byte value = LightAspect.MAX_LIGHT;
			if (centre.z() == location.z())
			{
				value = 1;
				if (location.y() < centre.y())
				{
					value += 5;
				}
				if (location.x() < centre.x())
				{
					value += 5;
				}
			}
			return value;
		};
		
		// Set a light in the centre and make sure it makes sense.
		cuboid.setData7(AspectRegistry.LIGHT, centre.getBlockAddress(), LightAspect.MAX_LIGHT);
		Map<AbsoluteLocation, Byte> updates = LightBringer.spreadLight(lookup.lightLookup, opacity, centre, LightAspect.MAX_LIGHT);
		for (Map.Entry<AbsoluteLocation, Byte> update : updates.entrySet())
		{
			cuboid.setData7(AspectRegistry.LIGHT, update.getKey().getBlockAddress(), update.getValue());
		}
		_writeLight(System.out, cuboid, centre.getBlockAddress().z());
		
		// Now remove the light and make sure the area goes dark.
		cuboid.setData7(AspectRegistry.LIGHT, centre.getBlockAddress(), (byte)0);
		updates = LightBringer.removeLight(lookup.lightLookup, opacity, centre, LightAspect.MAX_LIGHT);
		for (Map.Entry<AbsoluteLocation, Byte> update : updates.entrySet())
		{
			cuboid.setData7(AspectRegistry.LIGHT, update.getKey().getBlockAddress(), update.getValue());
		}
		_writeLight(System.out, cuboid, centre.getBlockAddress().z());
	}


	private static void _writeLight(PrintStream stream, CuboidData cuboid, byte z)
	{
		stream.println("--------------------------------");
		for (byte y = 31; y >= 0; --y)
		{
			for (byte x = 0; x < 32; ++x)
			{
				BlockAddress address = new BlockAddress(x, y, z);
				byte value = cuboid.getData7(AspectRegistry.LIGHT, address);
				stream.print(Integer.toHexString(value));
			}
			stream.println();
		}
	}

	private static BlockAddress _loadBlockMap(CuboidData cuboid, byte z, String map)
	{
		BlockAddress match = null;
		int index = 0;
		for (byte y = 31; y >= 0; --y)
		{
			for (byte x = 0; x < 32; ++x)
			{
				char c = map.charAt(index);
				index += 1;
				short value;
				switch(c)
				{
				case ' ':
					value = ItemRegistry.AIR.number();
					break;
				case 'S':
					value = ItemRegistry.STONE.number();
					break;
				case 'W':
					value = ItemRegistry.WATER_SOURCE.number();
					break;
				case 'X':
					value = ItemRegistry.AIR.number();
					Assert.assertNull(match);
					match = new BlockAddress(x, y, z);
					break;
					default:
						throw new AssertionError("Unknown");
				}
				BlockAddress address = new BlockAddress(x, y, z);
				cuboid.setData15(AspectRegistry.BLOCK, address, value);
			}
		}
		return match;
	}

	private static class _OneCuboidLookupCache
	{
		private final CuboidData _cuboid;
		private final Map<AbsoluteLocation, BlockProxy> _cache;
		public final LightBringer.IByteLookup lightLookup = (AbsoluteLocation location) -> 
		{
			BlockProxy proxy = _readOrPopulateCache(location);
			return (null != proxy)
					? proxy.getLight()
					: LightBringer.IByteLookup.NOT_FOUND
			;
		};
		public final LightBringer.IByteLookup opacityLookup = (AbsoluteLocation location) -> 
		{
			BlockProxy proxy = _readOrPopulateCache(location);
			return (null != proxy)
					? LightAspect.getOpacity(proxy.getBlock().asItem())
					: LightBringer.IByteLookup.NOT_FOUND
			;
		};
		public _OneCuboidLookupCache(CuboidData cuboid)
		{
			_cuboid = cuboid;
			_cache = new HashMap<>();
		}
		private BlockProxy _readOrPopulateCache(AbsoluteLocation location)
		{
			if (!_cache.containsKey(location))
			{
				_cache.put(location, new BlockProxy(location.getBlockAddress(), _cuboid));
			}
			return _cache.get(location);
		}
	}
}
