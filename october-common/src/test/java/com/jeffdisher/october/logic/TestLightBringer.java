package com.jeffdisher.october.logic;

import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.LightAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.worldgen.CuboidGenerator;


public class TestLightBringer
{
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
	public void singleLightSource()
	{
		// Create a single light source in the middle of an air cuboid and check how it propagates.
		CuboidAddress address = new CuboidAddress((short)10, (short)10, (short)10);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
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
			final byte thisSourceLight = i;
			source.setLight(thisSourceLight);
			source.writeBack(cuboid);
			LightBringer.IByteLookup sourceLookup = (AbsoluteLocation location) ->
			{
				return centre.equals(location)
						? thisSourceLight
						: 0
				;
			};
			_OneCuboidLookupCache lookup = new _OneCuboidLookupCache(cuboid);
			Map<AbsoluteLocation, Byte> updates = LightBringer.spreadLight(lookup.lightLookup, lookup.opacityLookup, sourceLookup, centre, thisSourceLight);
			Assert.assertEquals(expectedValues[i], updates.size());
		}
	}

	@Test
	public void addRemoveTwoSource()
	{
		byte value = LightAspect.MAX_LIGHT;
		// Create a single light source in the middle of an air cuboid and check how it propagates.
		CuboidAddress address = new CuboidAddress((short)10, (short)10, (short)10);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		AbsoluteLocation centre = address.getBase().getRelative(16, 16, 16);
		
		Map<AbsoluteLocation, Byte> sources = new HashMap<>();
		LightBringer.IByteLookup sourceLookup = (AbsoluteLocation location) ->
		{
			return sources.containsKey(location)
					? sources.get(location)
					: 0
			;
		};
		
		cuboid.setData7(AspectRegistry.LIGHT, centre.getBlockAddress(), value);
		sources.put(centre, value);
		_OneCuboidLookupCache lookup = new _OneCuboidLookupCache(cuboid);
		Map<AbsoluteLocation, Byte> initialSet = LightBringer.spreadLight(lookup.lightLookup, lookup.opacityLookup, sourceLookup, centre, value);
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
		sources.put(secondLight, value);
		lookup = new _OneCuboidLookupCache(cuboid);
		Map<AbsoluteLocation, Byte> secondUpdates = LightBringer.spreadLight(lookup.lightLookup, lookup.opacityLookup, sourceLookup, secondLight, value);
		Assert.assertEquals(2359, secondUpdates.size());
		for (Map.Entry<AbsoluteLocation, Byte> update : secondUpdates.entrySet())
		{
			cuboid.setData7(AspectRegistry.LIGHT, update.getKey().getBlockAddress(), update.getValue());
		}
		_writeLight(System.out, cuboid, secondLight.getBlockAddress().z());
		
		// Now, remove the light source and watch the updates.
		cuboid.setData7(AspectRegistry.LIGHT, secondLight.getBlockAddress(), (byte)0);
		sources.remove(secondLight);
		lookup = new _OneCuboidLookupCache(cuboid);
		Map<AbsoluteLocation, Byte> resetUpdates = LightBringer.removeLight(lookup.lightLookup, lookup.opacityLookup, sourceLookup, secondLight, value);
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
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.blocks.fromItem(ENV.items.getItemById("op.stone")));
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
		
		Map<AbsoluteLocation, Byte> sources = new HashMap<>();
		LightBringer.IByteLookup sourceLookup = (AbsoluteLocation location) ->
		{
			return sources.containsKey(location)
					? sources.get(location)
					: 0
			;
		};
		
		AbsoluteLocation target = address.getBase().getRelative(source.x(), source.y(), source.z());
		cuboid.setData7(AspectRegistry.LIGHT, source, value);
		sources.put(target, value);
		_OneCuboidLookupCache lookup = new _OneCuboidLookupCache(cuboid);
		Map<AbsoluteLocation, Byte> updates = LightBringer.spreadLight(lookup.lightLookup, lookup.opacityLookup, sourceLookup, target, value);
		// We expect 38 updates to fill the maze.
		int expectedUpdates = 38;
		Assert.assertEquals(expectedUpdates, updates.size());
		for (Map.Entry<AbsoluteLocation, Byte> update : updates.entrySet())
		{
			cuboid.setData7(AspectRegistry.LIGHT, update.getKey().getBlockAddress(), update.getValue());
		}
		_writeLight(System.out, cuboid, source.z());
		
		cuboid.setData7(AspectRegistry.LIGHT, source, (byte)0);
		sources.remove(target);
		lookup = new _OneCuboidLookupCache(cuboid);
		updates = LightBringer.removeLight(lookup.lightLookup, lookup.opacityLookup, sourceLookup, target, value);
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
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		AbsoluteLocation centre = address.getBase().getRelative(16, 16, 16);
		
		// We will use the light values from the cuboid but a fixed high opacity.
		LightBringer.IByteLookup opacity = (AbsoluteLocation location) -> 4;
		
		Map<AbsoluteLocation, Byte> sources = new HashMap<>();
		LightBringer.IByteLookup sourceLookup = (AbsoluteLocation location) ->
		{
			return sources.containsKey(location)
					? sources.get(location)
					: 0
			;
		};
		
		// Set a light on either side of the centre.
		AbsoluteLocation west = centre.getRelative(-1, 0, 0);
		cuboid.setData7(AspectRegistry.LIGHT, west.getBlockAddress(), (byte)15);
		sources.put(west, (byte)15);
		_OneCuboidLookupCache lookup = new _OneCuboidLookupCache(cuboid);
		Map<AbsoluteLocation, Byte> updates = LightBringer.spreadLight(lookup.lightLookup, opacity, sourceLookup, west, (byte)15);
		// This update value was found experimentally.
		Assert.assertEquals(62, updates.size());
		for (Map.Entry<AbsoluteLocation, Byte> update : updates.entrySet())
		{
			cuboid.setData7(AspectRegistry.LIGHT, update.getKey().getBlockAddress(), update.getValue());
		}
		AbsoluteLocation east = centre.getRelative(1, 0, 0);
		cuboid.setData7(AspectRegistry.LIGHT, east.getBlockAddress(), (byte)15);
		sources.put(east, (byte)15);
		lookup = new _OneCuboidLookupCache(cuboid);
		updates = LightBringer.spreadLight(lookup.lightLookup, opacity, sourceLookup, east, (byte)15);
		// This update value was found experimentally.
		Assert.assertEquals(43, updates.size());
		for (Map.Entry<AbsoluteLocation, Byte> update : updates.entrySet())
		{
			cuboid.setData7(AspectRegistry.LIGHT, update.getKey().getBlockAddress(), update.getValue());
		}
		_writeLight(System.out, cuboid, centre.getBlockAddress().z());
		Assert.assertEquals((byte)11, cuboid.getData7(AspectRegistry.LIGHT, centre.getBlockAddress()));
		
		// Remove the light on either side of the centre.
		cuboid.setData7(AspectRegistry.LIGHT, west.getBlockAddress(), (byte)0);
		sources.remove(west);
		lookup = new _OneCuboidLookupCache(cuboid);
		updates = LightBringer.removeLight(lookup.lightLookup, opacity, sourceLookup, west, (byte)15);
		// This update value was found experimentally.
		Assert.assertEquals(57, updates.size());
		for (Map.Entry<AbsoluteLocation, Byte> update : updates.entrySet())
		{
			cuboid.setData7(AspectRegistry.LIGHT, update.getKey().getBlockAddress(), update.getValue());
		}
		cuboid.setData7(AspectRegistry.LIGHT, east.getBlockAddress(), (byte)0);
		sources.remove(east);
		lookup = new _OneCuboidLookupCache(cuboid);
		updates = LightBringer.removeLight(lookup.lightLookup, opacity, sourceLookup, east, (byte)15);
		// This update value was found experimentally.
		Assert.assertEquals(62, updates.size());
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
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
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
		
		Map<AbsoluteLocation, Byte> sources = new HashMap<>();
		LightBringer.IByteLookup sourceLookup = (AbsoluteLocation location) ->
		{
			return sources.containsKey(location)
					? sources.get(location)
					: 0
			;
		};
		
		// Set a light in the centre and make sure it makes sense.
		cuboid.setData7(AspectRegistry.LIGHT, centre.getBlockAddress(), LightAspect.MAX_LIGHT);
		sources.put(centre, LightAspect.MAX_LIGHT);
		_OneCuboidLookupCache lookup = new _OneCuboidLookupCache(cuboid);
		Map<AbsoluteLocation, Byte> updates = LightBringer.spreadLight(lookup.lightLookup, opacity, sourceLookup, centre, LightAspect.MAX_LIGHT);
		// This update value was found experimentally.
		int expectedUpdates = 143;
		Assert.assertEquals(expectedUpdates, updates.size());
		for (Map.Entry<AbsoluteLocation, Byte> update : updates.entrySet())
		{
			cuboid.setData7(AspectRegistry.LIGHT, update.getKey().getBlockAddress(), update.getValue());
		}
		_writeLight(System.out, cuboid, centre.getBlockAddress().z());
		
		// Now remove the light and make sure the area goes dark.
		cuboid.setData7(AspectRegistry.LIGHT, centre.getBlockAddress(), (byte)0);
		sources.remove(centre);
		lookup = new _OneCuboidLookupCache(cuboid);
		updates = LightBringer.removeLight(lookup.lightLookup, opacity, sourceLookup, centre, LightAspect.MAX_LIGHT);
		Assert.assertEquals(expectedUpdates, updates.size());
		for (Map.Entry<AbsoluteLocation, Byte> update : updates.entrySet())
		{
			cuboid.setData7(AspectRegistry.LIGHT, update.getKey().getBlockAddress(), update.getValue());
		}
		_writeLight(System.out, cuboid, centre.getBlockAddress().z());
	}

	@Test
	public void addRemoveDifferentIntensity()
	{
		// We want to add a strong light source, then a dim one next to it, then remove the original strong source and verify the dim one still works.
		byte strong = LightAspect.MAX_LIGHT;
		byte dim = 5;
		
		CuboidAddress address = new CuboidAddress((short)10, (short)10, (short)10);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		AbsoluteLocation centre = address.getBase().getRelative(16, 16, 16);
		AbsoluteLocation strongLocation = centre.getRelative(-1, 0, 0);
		AbsoluteLocation dimLocation = centre.getRelative(1, 0, 0);
		LightBringer.IByteLookup opacity = (AbsoluteLocation location) ->
		{
			// We will say that the entire cuboid is opaque, other than this single z-level.
			return (centre.getBlockAddress().z() == location.getBlockAddress().z())
					? (byte)1
					: LightAspect.MAX_LIGHT
			;
		};
		
		Map<AbsoluteLocation, Byte> sources = new HashMap<>();
		LightBringer.IByteLookup sourceLookup = (AbsoluteLocation location) ->
		{
			return sources.containsKey(location)
					? sources.get(location)
					: 0
			;
		};
		
		// Add the strong source.
		cuboid.setData7(AspectRegistry.LIGHT, strongLocation.getBlockAddress(), strong);
		sources.put(strongLocation, strong);
		_OneCuboidLookupCache lookup = new _OneCuboidLookupCache(cuboid);
		Map<AbsoluteLocation, Byte> updates = LightBringer.spreadLight(lookup.lightLookup, opacity, sourceLookup, strongLocation, strong);
		// This update value was found experimentally.
		int expectedUpdates = 420;
		Assert.assertEquals(expectedUpdates, updates.size());
		for (Map.Entry<AbsoluteLocation, Byte> update : updates.entrySet())
		{
			cuboid.setData7(AspectRegistry.LIGHT, update.getKey().getBlockAddress(), update.getValue());
		}
		_writeLight(System.out, cuboid, centre.getBlockAddress().z());
		
		// Add the dim source (note that we can't set the light value, as it would dim it).
		sources.put(dimLocation, dim);
		
		// Remove the strong source.
		cuboid.setData7(AspectRegistry.LIGHT, strongLocation.getBlockAddress(), (byte)0);
		lookup = new _OneCuboidLookupCache(cuboid);
		sources.remove(strongLocation);
		updates = LightBringer.removeLight(lookup.lightLookup, opacity, sourceLookup, strongLocation, strong);
		// We see one additional update for the source, itself.
		Assert.assertEquals(expectedUpdates + 1, updates.size());
		for (Map.Entry<AbsoluteLocation, Byte> update : updates.entrySet())
		{
			cuboid.setData7(AspectRegistry.LIGHT, update.getKey().getBlockAddress(), update.getValue());
		}
		_writeLight(System.out, cuboid, centre.getBlockAddress().z());
	}

	@Test
	public void batchProcess()
	{
		// We will add 3 light sources in one call, then remove 2 and add another 2 in the following call.
		CuboidAddress address = new CuboidAddress((short)10, (short)10, (short)10);
		AbsoluteLocation centre = address.getBase().getRelative(16, 16, 16);
		
		// In this case, we only use a real cuboid for the light levels and fixed values for opacity, based on location.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		LightBringer.IByteLookup opacity = (AbsoluteLocation location) ->
		{
			// We will say that the entire cuboid is opaque, other than this single z-level.
			return (centre.getBlockAddress().z() == location.getBlockAddress().z())
					? (byte)1
					: LightAspect.MAX_LIGHT
			;
		};
		
		Map<AbsoluteLocation, Byte> sources = new HashMap<>();
		LightBringer.IByteLookup sourceLookup = (AbsoluteLocation location) ->
		{
			return sources.containsKey(location)
					? sources.get(location)
					: 0
			;
		};
		
		// Set our initial lights.
		AbsoluteLocation light1 = centre.getRelative(-2, 1, 0);
		AbsoluteLocation light2 = centre.getRelative(0, -1, 0);
		AbsoluteLocation light3 = centre.getRelative(2, 1, 0);
		List<LightBringer.Light> toAdd1 = List.of(new LightBringer.Light(light1, LightAspect.MAX_LIGHT)
				, new LightBringer.Light(light2, (byte)8)
				, new LightBringer.Light(light3, LightAspect.MAX_LIGHT)
		);
		for (LightBringer.Light light : toAdd1)
		{
			cuboid.setData7(AspectRegistry.LIGHT, light.location().getBlockAddress(), light.level());
			sources.put(light.location(), light.level());
		}
		
		_OneCuboidLookupCache lookup = new _OneCuboidLookupCache(cuboid);
		Map<AbsoluteLocation, Byte> updates = LightBringer.batchProcessLight(lookup.lightLookup, opacity, sourceLookup, toAdd1, List.of());
		// This update value was found experimentally.
		Assert.assertEquals(527, updates.size());
		for (Map.Entry<AbsoluteLocation, Byte> update : updates.entrySet())
		{
			AbsoluteLocation location = update.getKey();
			if (address.equals(location.getCuboidAddress()))
			{
				cuboid.setData7(AspectRegistry.LIGHT, location.getBlockAddress(), update.getValue());
			}
		}
		_writeLight(System.out, cuboid, centre.getBlockAddress().z());
		
		// Create the new lights and remove some of the old ones.
		AbsoluteLocation light4 = centre.getRelative(-5, 5, 0);
		AbsoluteLocation light5 = centre.getRelative(3, -8, 0);
		List<LightBringer.Light> toAdd2 = List.of(new LightBringer.Light(light4, LightAspect.MAX_LIGHT)
				, new LightBringer.Light(light5, (byte)8)
		);
		List<LightBringer.Light> toRemove = List.of(new LightBringer.Light(light1, LightAspect.MAX_LIGHT)
				, new LightBringer.Light(light3, LightAspect.MAX_LIGHT)
		);
		for (LightBringer.Light light : toAdd2)
		{
			cuboid.setData7(AspectRegistry.LIGHT, light.location().getBlockAddress(), light.level());
			sources.put(light.location(), light.level());
		}
		for (LightBringer.Light light : toRemove)
		{
			cuboid.setData7(AspectRegistry.LIGHT, light.location().getBlockAddress(), (byte)0);
			sources.remove(light.location());
		}
		
		lookup = new _OneCuboidLookupCache(cuboid);
		updates = LightBringer.batchProcessLight(lookup.lightLookup, opacity, sourceLookup, toAdd2, toRemove);
		// This update value was found experimentally.
		Assert.assertEquals(656, updates.size());
		for (Map.Entry<AbsoluteLocation, Byte> update : updates.entrySet())
		{
			AbsoluteLocation location = update.getKey();
			if (address.equals(location.getCuboidAddress()))
			{
				cuboid.setData7(AspectRegistry.LIGHT, location.getBlockAddress(), update.getValue());
			}
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
					value = ENV.items.AIR.number();
					break;
				case 'S':
					value = ENV.items.STONE.number();
					break;
				case 'W':
					value = ENV.items.WATER_SOURCE.number();
					break;
				case 'X':
					value = ENV.items.AIR.number();
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
					? ENV.lighting.getOpacity(proxy.getBlock())
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
