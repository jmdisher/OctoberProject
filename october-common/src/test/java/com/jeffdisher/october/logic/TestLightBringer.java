package com.jeffdisher.october.logic;

import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import com.jeffdisher.october.types.IByteLookup;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.utils.CuboidGenerator;


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
		CuboidAddress address = CuboidAddress.fromInt(10, 10, 10);
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
			IByteLookup<AbsoluteLocation> sourceLookup = (AbsoluteLocation location) ->
			{
				return centre.equals(location)
						? thisSourceLight
						: 0
				;
			};
			_OneCuboidLookupCache lookup = new _OneCuboidLookupCache(cuboid);
			_BlockDataOverlay overlay = new _BlockDataOverlay(lookup.lightLookup, lookup.opacityLookup, sourceLookup);
			LightBringer.batchProcessLight(overlay, List.of(new LightBringer.Light(centre, thisSourceLight)), List.of());
			Map<AbsoluteLocation, Byte> updates = overlay._changedValues;
			Assert.assertEquals(expectedValues[i], updates.size());
		}
	}

	@Test
	public void addRemoveTwoSource()
	{
		byte value = LightAspect.MAX_LIGHT;
		// Create a single light source in the middle of an air cuboid and check how it propagates.
		CuboidAddress address = CuboidAddress.fromInt(10, 10, 10);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		AbsoluteLocation centre = address.getBase().getRelative(16, 16, 16);
		
		Map<AbsoluteLocation, Byte> sources = new HashMap<>();
		IByteLookup<AbsoluteLocation> sourceLookup = (AbsoluteLocation location) ->
		{
			return sources.containsKey(location)
					? sources.get(location)
					: 0
			;
		};
		
		cuboid.setData7(AspectRegistry.LIGHT, centre.getBlockAddress(), value);
		sources.put(centre, value);
		_OneCuboidLookupCache lookup = new _OneCuboidLookupCache(cuboid);
		_BlockDataOverlay overlay = new _BlockDataOverlay(lookup.lightLookup, lookup.opacityLookup, sourceLookup);
		LightBringer.batchProcessLight(overlay, List.of(new LightBringer.Light(centre, value)), List.of());
		Map<AbsoluteLocation, Byte> initialSet = overlay._changedValues;
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
		overlay = new _BlockDataOverlay(lookup.lightLookup, lookup.opacityLookup, sourceLookup);
		LightBringer.batchProcessLight(overlay, List.of(new LightBringer.Light(secondLight, value)), List.of());
		Map<AbsoluteLocation, Byte> secondUpdates = overlay._changedValues;
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
		overlay = new _BlockDataOverlay(lookup.lightLookup, lookup.opacityLookup, sourceLookup);
		LightBringer.batchProcessLight(overlay, List.of(), List.of(new LightBringer.Light(secondLight, value)));
		Map<AbsoluteLocation, Byte> resetUpdates = overlay._changedValues;
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
		CuboidAddress address = CuboidAddress.fromInt(10, 10, 10);
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
		IByteLookup<AbsoluteLocation> sourceLookup = (AbsoluteLocation location) ->
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
		_BlockDataOverlay overlay = new _BlockDataOverlay(lookup.lightLookup, lookup.opacityLookup, sourceLookup);
		LightBringer.batchProcessLight(overlay, List.of(new LightBringer.Light(target, value)), List.of());
		Map<AbsoluteLocation, Byte> updates = overlay._changedValues;
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
		overlay = new _BlockDataOverlay(lookup.lightLookup, lookup.opacityLookup, sourceLookup);
		LightBringer.batchProcessLight(overlay, List.of(), List.of(new LightBringer.Light(target, value)));
		updates = overlay._changedValues;
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
		CuboidAddress address = CuboidAddress.fromInt(10, 10, 10);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		AbsoluteLocation centre = address.getBase().getRelative(16, 16, 16);
		
		// We will use the light values from the cuboid but a fixed high opacity.
		IByteLookup<AbsoluteLocation> opacity = (AbsoluteLocation location) -> 4;
		
		Map<AbsoluteLocation, Byte> sources = new HashMap<>();
		IByteLookup<AbsoluteLocation> sourceLookup = (AbsoluteLocation location) ->
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
		_BlockDataOverlay overlay = new _BlockDataOverlay(lookup.lightLookup, opacity, sourceLookup);
		LightBringer.batchProcessLight(overlay, List.of(new LightBringer.Light(west, (byte)15)), List.of());
		Map<AbsoluteLocation, Byte> updates = overlay._changedValues;
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
		overlay = new _BlockDataOverlay(lookup.lightLookup, opacity, sourceLookup);
		LightBringer.batchProcessLight(overlay, List.of(new LightBringer.Light(east, (byte)15)), List.of());
		updates = overlay._changedValues;
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
		overlay = new _BlockDataOverlay(lookup.lightLookup, opacity, sourceLookup);
		LightBringer.batchProcessLight(overlay, List.of(), List.of(new LightBringer.Light(west, (byte)15)));
		updates = overlay._changedValues;
		// This update value was found experimentally.
		Assert.assertEquals(57, updates.size());
		for (Map.Entry<AbsoluteLocation, Byte> update : updates.entrySet())
		{
			cuboid.setData7(AspectRegistry.LIGHT, update.getKey().getBlockAddress(), update.getValue());
		}
		cuboid.setData7(AspectRegistry.LIGHT, east.getBlockAddress(), (byte)0);
		sources.remove(east);
		lookup = new _OneCuboidLookupCache(cuboid);
		overlay = new _BlockDataOverlay(lookup.lightLookup, opacity, sourceLookup);
		LightBringer.batchProcessLight(overlay, List.of(), List.of(new LightBringer.Light(east, (byte)15)));
		updates = overlay._changedValues;
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
		CuboidAddress address = CuboidAddress.fromInt(10, 10, 10);
		AbsoluteLocation centre = address.getBase().getRelative(16, 16, 16);
		
		// In this case, we only use a real cuboid for the light levels and fixed values for opacity, based on location.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		IByteLookup<AbsoluteLocation> opacity = (AbsoluteLocation location) ->
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
		IByteLookup<AbsoluteLocation> sourceLookup = (AbsoluteLocation location) ->
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
		_BlockDataOverlay overlay = new _BlockDataOverlay(lookup.lightLookup, opacity, sourceLookup);
		LightBringer.batchProcessLight(overlay, List.of(new LightBringer.Light(centre, LightAspect.MAX_LIGHT)), List.of());
		Map<AbsoluteLocation, Byte> updates = overlay._changedValues;
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
		overlay = new _BlockDataOverlay(lookup.lightLookup, opacity, sourceLookup);
		LightBringer.batchProcessLight(overlay, List.of(), List.of(new LightBringer.Light(centre, LightAspect.MAX_LIGHT)));
		updates = overlay._changedValues;
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
		
		CuboidAddress address = CuboidAddress.fromInt(10, 10, 10);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		AbsoluteLocation centre = address.getBase().getRelative(16, 16, 16);
		AbsoluteLocation strongLocation = centre.getRelative(-1, 0, 0);
		AbsoluteLocation dimLocation = centre.getRelative(1, 0, 0);
		IByteLookup<AbsoluteLocation> opacity = (AbsoluteLocation location) ->
		{
			// We will say that the entire cuboid is opaque, other than this single z-level.
			return (centre.getBlockAddress().z() == location.getBlockAddress().z())
					? (byte)1
					: LightAspect.MAX_LIGHT
			;
		};
		
		Map<AbsoluteLocation, Byte> sources = new HashMap<>();
		IByteLookup<AbsoluteLocation> sourceLookup = (AbsoluteLocation location) ->
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
		_BlockDataOverlay overlay = new _BlockDataOverlay(lookup.lightLookup, opacity, sourceLookup);
		LightBringer.batchProcessLight(overlay, List.of(new LightBringer.Light(strongLocation, strong)), List.of());
		Map<AbsoluteLocation, Byte> updates = overlay._changedValues;
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
		overlay = new _BlockDataOverlay(lookup.lightLookup, opacity, sourceLookup);
		LightBringer.batchProcessLight(overlay, List.of(), List.of(new LightBringer.Light(strongLocation, strong)));
		updates = overlay._changedValues;
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
		CuboidAddress address = CuboidAddress.fromInt(10, 10, 10);
		AbsoluteLocation centre = address.getBase().getRelative(16, 16, 16);
		
		// In this case, we only use a real cuboid for the light levels and fixed values for opacity, based on location.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		IByteLookup<AbsoluteLocation> opacity = (AbsoluteLocation location) ->
		{
			// We will say that the entire cuboid is opaque, other than this single z-level.
			return (centre.getBlockAddress().z() == location.getBlockAddress().z())
					? (byte)1
					: LightAspect.MAX_LIGHT
			;
		};
		
		Map<AbsoluteLocation, Byte> sources = new HashMap<>();
		IByteLookup<AbsoluteLocation> sourceLookup = (AbsoluteLocation location) ->
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
		_BlockDataOverlay overlay = new _BlockDataOverlay(lookup.lightLookup, opacity, sourceLookup);
		LightBringer.batchProcessLight(overlay, toAdd1, List.of());
		Map<AbsoluteLocation, Byte> updates = overlay._changedValues;
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
		overlay = new _BlockDataOverlay(lookup.lightLookup, opacity, sourceLookup);
		LightBringer.batchProcessLight(overlay, toAdd2, toRemove);
		updates = overlay._changedValues;
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

	@Test
	public void lightInconsistencyBoundary()
	{
		// This demonstrates what happens when a lighting update is made inconsistent by cuboid load/unload operations.
		byte strong = LightAspect.MAX_LIGHT;
		byte dark = 0;
		
		CuboidAddress address1 = CuboidAddress.fromInt(10, 10, 10);
		CuboidAddress address2 = CuboidAddress.fromInt(11, 10, 10);
		CuboidData cuboid1 = CuboidGenerator.createFilledCuboid(address1, ENV.special.AIR);
		CuboidData cuboid2 = CuboidGenerator.createFilledCuboid(address2, ENV.special.AIR);
		AbsoluteLocation firstLight = address1.getBase().getRelative(25, 16, 16);
		AbsoluteLocation secondLight = address1.getBase().getRelative(26, 16, 16);
		IByteLookup<AbsoluteLocation> opacityLookup = (AbsoluteLocation location) ->
		{
			// We will say that the entire cuboid is opaque, other than this z-level
			return (firstLight.getBlockAddress().z() == location.getBlockAddress().z())
					? 1
					: LightAspect.MAX_LIGHT
			;
		};
		
		Map<AbsoluteLocation, Byte> sources = new HashMap<>();
		IByteLookup<AbsoluteLocation> sourceLookup = (AbsoluteLocation location) ->
		{
			return sources.containsKey(location)
					? sources.get(location)
					: 0
			;
		};
		
		// Add the first light source, applying to both cuboids.
		cuboid1.setData7(AspectRegistry.LIGHT, firstLight.getBlockAddress(), strong);
		sources.put(firstLight, strong);
		_MultiCuboidCache multi = new _MultiCuboidCache(cuboid1, cuboid2);
		_BlockDataOverlay overlay = new _BlockDataOverlay(multi.lightLookup, opacityLookup, sourceLookup);
		LightBringer.batchProcessLight(overlay, List.of(new LightBringer.Light(firstLight, strong)), List.of());
		Map<AbsoluteLocation, Byte> updates = overlay._changedValues;
		// This update value was found experimentally.
		Assert.assertEquals(420, updates.size());
		for (Map.Entry<AbsoluteLocation, Byte> update : updates.entrySet())
		{
			AbsoluteLocation location = update.getKey();
			if (location.getCuboidAddress().equals(cuboid1.getCuboidAddress()))
			{
				cuboid1.setData7(AspectRegistry.LIGHT, location.getBlockAddress(), update.getValue());
			}
			else
			{
				cuboid2.setData7(AspectRegistry.LIGHT, location.getBlockAddress(), update.getValue());
			}
		}
		
		// Remove this light, but only with cuboid1 loaded.
		cuboid1.setData7(AspectRegistry.LIGHT, firstLight.getBlockAddress(), dark);
		sources.remove(firstLight);
		_OneCuboidLookupCache single = new _OneCuboidLookupCache(cuboid1);
		overlay = new _BlockDataOverlay(single.lightLookup, opacityLookup, sourceLookup);
		LightBringer.batchProcessLight(overlay, List.of(), List.of(new LightBringer.Light(firstLight, strong)));
		updates = overlay._changedValues;
		// This update value was found experimentally.
		Assert.assertEquals(356, updates.size());
		for (Map.Entry<AbsoluteLocation, Byte> update : updates.entrySet())
		{
			AbsoluteLocation location = update.getKey();
			if (location.getCuboidAddress().equals(cuboid1.getCuboidAddress()))
			{
				cuboid1.setData7(AspectRegistry.LIGHT, location.getBlockAddress(), update.getValue());
			}
			else
			{
			}
		}
		
		// Add the second light source, applying only to cuboid1.
		cuboid1.setData7(AspectRegistry.LIGHT, secondLight.getBlockAddress(), strong);
		sources.put(secondLight, strong);
		single = new _OneCuboidLookupCache(cuboid1);
		overlay = new _BlockDataOverlay(single.lightLookup, opacityLookup, sourceLookup);
		LightBringer.batchProcessLight(overlay, List.of(new LightBringer.Light(secondLight, strong)), List.of());
		updates = overlay._changedValues;
		// This update value was found experimentally.
		Assert.assertEquals(420, updates.size());
		for (Map.Entry<AbsoluteLocation, Byte> update : updates.entrySet())
		{
			AbsoluteLocation location = update.getKey();
			if (location.getCuboidAddress().equals(cuboid1.getCuboidAddress()))
			{
				cuboid1.setData7(AspectRegistry.LIGHT, location.getBlockAddress(), update.getValue());
			}
			else
			{
			}
		}
		
		// Remove the new light with both cuboids
		cuboid1.setData7(AspectRegistry.LIGHT, secondLight.getBlockAddress(), dark);
		sources.remove(secondLight);
		multi = new _MultiCuboidCache(cuboid1, cuboid2);
		overlay = new _BlockDataOverlay(multi.lightLookup, opacityLookup, sourceLookup);
		LightBringer.batchProcessLight(overlay, List.of(), List.of(new LightBringer.Light(secondLight, strong)));
		updates = overlay._changedValues;
		// This update value was found experimentally.
		Assert.assertEquals(339, updates.size());
		for (Map.Entry<AbsoluteLocation, Byte> update : updates.entrySet())
		{
			AbsoluteLocation location = update.getKey();
			if (location.getCuboidAddress().equals(cuboid1.getCuboidAddress()))
			{
				cuboid1.setData7(AspectRegistry.LIGHT, location.getBlockAddress(), update.getValue());
			}
			else
			{
				cuboid2.setData7(AspectRegistry.LIGHT, location.getBlockAddress(), update.getValue());
			}
		}
	}

	@Test
	public void lightInconsistencyDefinitionChange()
	{
		// This demonstrates the lighting glitch created when the light opacity data definition is changed under an existing world (a warning is logged).
		byte strong = LightAspect.MAX_LIGHT;
		byte dark = 0;
		
		CuboidAddress address = CuboidAddress.fromInt(10, 10, 10);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		AbsoluteLocation centre = address.getBase().getRelative(16, 16, 16);
		AbsoluteLocation testLocation1 = centre.getRelative(-2, 0, 0);
		AbsoluteLocation testLocation2 = centre.getRelative(3, 0, 0);
		byte[] opacityRef = new byte[] { (byte)4 };
		boolean[] isTestOccupied = new boolean[] { false };
		IByteLookup<AbsoluteLocation> opacityLookup = (AbsoluteLocation location) ->
		{
			// We will say that the entire cuboid is opaque, other than (X, 16, 16), which we will say is a high opacity.
			byte opacity = ((centre.getBlockAddress().z() == location.getBlockAddress().z()) && (centre.getBlockAddress().y() == location.getBlockAddress().y()))
					? opacityRef[0]
					: LightAspect.MAX_LIGHT
			;
			if (location.equals(testLocation1) && isTestOccupied[0])
			{
				opacity = LightAspect.MAX_LIGHT;
			}
			return opacity;
		};
		
		Map<AbsoluteLocation, Byte> sources = new HashMap<>();
		IByteLookup<AbsoluteLocation> sourceLookup = (AbsoluteLocation location) ->
		{
			return sources.containsKey(location)
					? sources.get(location)
					: 0
			;
		};
		
		// Add the light source using the start opacity.
		cuboid.setData7(AspectRegistry.LIGHT, centre.getBlockAddress(), strong);
		sources.put(centre, strong);
		_OneCuboidLookupCache lookup = new _OneCuboidLookupCache(cuboid);
		_BlockDataOverlay overlay = new _BlockDataOverlay(lookup.lightLookup, opacityLookup, sourceLookup);
		LightBringer.batchProcessLight(overlay, List.of(new LightBringer.Light(centre, strong)), List.of());
		Map<AbsoluteLocation, Byte> updates = overlay._changedValues;
		// This update value was found experimentally.
		Assert.assertEquals(6, updates.size());
		for (Map.Entry<AbsoluteLocation, Byte> update : updates.entrySet())
		{
			cuboid.setData7(AspectRegistry.LIGHT, update.getKey().getBlockAddress(), update.getValue());
		}
		
		// Add the barrier using the new opacity.
		opacityRef[0] = 3;
		isTestOccupied[0] = true;
		byte initial = cuboid.getData7(AspectRegistry.LIGHT, testLocation1.getBlockAddress());
		cuboid.setData7(AspectRegistry.LIGHT, testLocation1.getBlockAddress(), dark);
		lookup = new _OneCuboidLookupCache(cuboid);
		overlay = new _BlockDataOverlay(lookup.lightLookup, opacityLookup, sourceLookup);
		LightBringer.batchProcessLight(overlay, List.of(), List.of(new LightBringer.Light(testLocation1, initial)));
		Map<AbsoluteLocation, Byte> resetUpdates = overlay._changedValues;
		Assert.assertEquals(0, resetUpdates.size());
		for (Map.Entry<AbsoluteLocation, Byte> update : resetUpdates.entrySet())
		{
			cuboid.setData7(AspectRegistry.LIGHT, update.getKey().getBlockAddress(), update.getValue());
		}
		
		// Add another light source using the ending opacity.
		cuboid.setData7(AspectRegistry.LIGHT, testLocation2.getBlockAddress(), strong);
		sources.put(testLocation2, strong);
		lookup = new _OneCuboidLookupCache(cuboid);
		overlay = new _BlockDataOverlay(lookup.lightLookup, opacityLookup, sourceLookup);
		LightBringer.batchProcessLight(overlay, List.of(new LightBringer.Light(testLocation2, strong)), List.of());
		updates = overlay._changedValues;
		// This update value was found experimentally.
		Assert.assertEquals(5, updates.size());
		for (Map.Entry<AbsoluteLocation, Byte> update : updates.entrySet())
		{
			cuboid.setData7(AspectRegistry.LIGHT, update.getKey().getBlockAddress(), update.getValue());
		}
	}

	@Test
	public void addRemoveInLine()
	{
		// We want to show what happens when we add a light source (like block removal) at the same time we remove the light source illuminating it at the same time.
		
		// This test only involves 4 blocks:  Empty block (light 0), removed block (light from 0 to 2), existing block (light 3), removed source (light from 4 to 0).
		int emptyX = 7;
		int litX = 8;
		int existingX = 9;
		int darkenedX = 10;
		int fixedY = 5;
		int fixedZ = 6;
		Set<AbsoluteLocation> darkUpdateSet = new HashSet<>();
		
		HashMap<AbsoluteLocation, Byte> existingLights = new HashMap<>();
		LightBringer.IBlockDataOverlay overlay = new LightBringer.IBlockDataOverlay() {
			private HashMap<AbsoluteLocation, Byte> _lights = new HashMap<>();
			@Override
			public byte getLight(AbsoluteLocation location)
			{
				return _lights.containsKey(location)
						? _lights.get(location)
						: existingLights.containsKey(location)
							? existingLights.get(location)
							: (byte)0
				;
			}
			@Override
			public void setLight(AbsoluteLocation location, byte value)
			{
				_lights.put(location, value);
			}
			@Override
			public void setDark(AbsoluteLocation location)
			{
				boolean isSet = _lights.containsKey(location);
				if (isSet)
				{
					// We want to record which blocks are cleared after being set.
					Assert.assertTrue(darkUpdateSet.add(location));
				}
				_lights.put(location, (byte)0);
			}
			@Override
			public byte getOpacity(AbsoluteLocation location)
			{
				// Our 3 blocks have opacity 1 while everything else is max.
				byte opacity = LightAspect.MAX_LIGHT;
				int locX = location.x();
				if ((fixedY == location.y()) && (fixedZ == location.z())
						&& ((emptyX == locX) || (litX == locX) || (existingX == locX) || (darkenedX == locX))
				)
				{
					opacity = 1;
				}
				return opacity;
			}
			@Override
			public byte getLightSource(AbsoluteLocation location)
			{
				// There are no sources in this test.
				return 0;
			}
		};
		// Set the existing light locations.
		existingLights.put(new AbsoluteLocation(emptyX, fixedY, fixedZ), (byte)0);
		existingLights.put(new AbsoluteLocation(litX, fixedY, fixedZ), (byte)0);
		existingLights.put(new AbsoluteLocation(existingX, fixedY, fixedZ), (byte)3);
		existingLights.put(new AbsoluteLocation(darkenedX, fixedY, fixedZ), (byte)4);
		
		// We also need to update the ones we are explicitly changing, in the overlay.
		overlay.setLight(new AbsoluteLocation(litX, fixedY, fixedZ), (byte)2);
		overlay.setDark(new AbsoluteLocation(darkenedX, fixedY, fixedZ));
		LightBringer.batchProcessLight(overlay
				, List.of(new LightBringer.Light(new AbsoluteLocation(litX, fixedY, fixedZ), (byte)2))
				, List.of(new LightBringer.Light(new AbsoluteLocation(darkenedX, fixedY, fixedZ), (byte)4))
		);
		
		// We expect to see 2 cases where we redundantly darkened this:  The block we light and the block behind it (since both were lit before their light source was removed).
		Assert.assertEquals(2, darkUpdateSet.size());
		Assert.assertTrue(darkUpdateSet.contains(new AbsoluteLocation(emptyX, fixedY, fixedZ)));
		Assert.assertTrue(darkUpdateSet.contains(new AbsoluteLocation(litX, fixedY, fixedZ)));
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
		Item stoneItem = ENV.items.getItemById("op.stone");
		short waterSourceItemNumber = ENV.items.getItemById("op.water_source").number();
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
					value = ENV.special.AIR.item().number();
					break;
				case 'S':
					value = stoneItem.number();
					break;
				case 'W':
					value = waterSourceItemNumber;
					break;
				case 'X':
					value = ENV.special.AIR.item().number();
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
		public final IByteLookup<AbsoluteLocation> lightLookup = (AbsoluteLocation location) -> 
		{
			BlockProxy proxy = _readOrPopulateCache(location);
			return (null != proxy)
					? proxy.getLight()
					: IByteLookup.NOT_FOUND
			;
		};
		public final IByteLookup<AbsoluteLocation> opacityLookup = (AbsoluteLocation location) -> 
		{
			BlockProxy proxy = _readOrPopulateCache(location);
			return (null != proxy)
					? ENV.lighting.getOpacity(proxy.getBlock())
					: IByteLookup.NOT_FOUND
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

	private static class _MultiCuboidCache
	{
		private final CuboidData[] _cuboids;
		private final Map<AbsoluteLocation, BlockProxy> _cache;
		public final IByteLookup<AbsoluteLocation> lightLookup = (AbsoluteLocation location) -> 
		{
			BlockProxy proxy = _readOrPopulateCache(location);
			return (null != proxy)
					? proxy.getLight()
					: IByteLookup.NOT_FOUND
			;
		};
		public _MultiCuboidCache(CuboidData... cuboids)
		{
			_cuboids = cuboids;
			_cache = new HashMap<>();
		}
		private BlockProxy _readOrPopulateCache(AbsoluteLocation location)
		{
			if (!_cache.containsKey(location))
			{
				BlockProxy proxy = null;
				CuboidAddress address = location.getCuboidAddress();
				for (CuboidData cuboid : _cuboids)
				{
					if (address.equals(cuboid.getCuboidAddress()))
					{
						proxy = new BlockProxy(location.getBlockAddress(), cuboid);
						break;
					}
				}
				if (null != proxy)
				{
					_cache.put(location, proxy);
				}
			}
			return _cache.get(location);
		}
	}

	private static class _BlockDataOverlay implements LightBringer.IBlockDataOverlay
	{
		private final IByteLookup<AbsoluteLocation> _lightLookup;
		private final IByteLookup<AbsoluteLocation> _opacityLookup;
		private final IByteLookup<AbsoluteLocation> _sourceLookup;
		private final Map<AbsoluteLocation, Byte> _changedValues;
		
		public _BlockDataOverlay(IByteLookup<AbsoluteLocation> lightLookup, IByteLookup<AbsoluteLocation> opacityLookup, IByteLookup<AbsoluteLocation> sourceLookup)
		{
			_lightLookup = lightLookup;
			_opacityLookup = opacityLookup;
			_sourceLookup = sourceLookup;
			_changedValues = new HashMap<>();
		}
		@Override
		public byte getLight(AbsoluteLocation location)
		{
			return _changedValues.containsKey(location)
					? _changedValues.get(location)
					: _lightLookup.lookup(location)
			;
		}
		@Override
		public void setLight(AbsoluteLocation location, byte value)
		{
			Byte previous = _changedValues.put(location, value);
			// This entry-point can only increase brightness.
			if (null != previous)
			{
				Assert.assertTrue(value > previous);
			}
		}
		@Override
		public void setDark(AbsoluteLocation location)
		{
			// We expect not to see this happen redundantly.
			Assert.assertTrue(null == _changedValues.put(location, (byte)0));
		}
		@Override
		public byte getOpacity(AbsoluteLocation location)
		{
			return _opacityLookup.lookup(location);
		}
		@Override
		public byte getLightSource(AbsoluteLocation location)
		{
			return _sourceLookup.lookup(location);
		}
	}
}
