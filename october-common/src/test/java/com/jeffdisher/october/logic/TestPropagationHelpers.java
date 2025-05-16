package com.jeffdisher.october.logic;

import java.util.ArrayList;
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
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.aspects.LightAspect;
import com.jeffdisher.october.aspects.LogicAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestPropagationHelpers
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
	public void lightSource()
	{
		// Create an empty cuboid with a lantern and verify the expected number of updates.
		Block blockLantern = ENV.blocks.fromItem(ENV.items.getItemById("op.lantern"));
		
		CuboidAddress address = CuboidAddress.fromInt(10, 10, 10);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		AbsoluteLocation lantern = address.getBase().getRelative(16, 16, 16);
		_setBlock(lantern, cuboid, blockLantern, false, true, (byte)0x0);
		
		Map<AbsoluteLocation, MutableBlockProxy> lazyLocalCache = new HashMap<>();
		Map<AbsoluteLocation, BlockProxy> lazyGlobalCache = new HashMap<>();
		PropagationHelpers.processPreviousTickLightUpdates(address
				, Map.of(address, List.of(lantern))
				, (AbsoluteLocation location) -> {
					MutableBlockProxy proxy = lazyLocalCache.get(location);
					if (null == proxy)
					{
						proxy = new MutableBlockProxy(location, cuboid);
						lazyLocalCache.put(location, proxy);
					}
					return proxy;
				}
				, (AbsoluteLocation location) -> {
					BlockProxy proxy = lazyGlobalCache.get(location);
					if (null == proxy)
					{
						proxy = new BlockProxy(location.getBlockAddress(), cuboid);
						lazyGlobalCache.put(location, proxy);
					}
					return proxy;
				}
		);
		
		// Check the updated proxies for light levels.
		int[] expectedValues = new int[] {
				0,
				786,
				678,
				578,
				486,
				402,
				326,
				258,
				198,
				146,
				102,
				66,
				38,
				18,
				6,
				1,
		};
		int[] lightLevels = new int[LightAspect.MAX_LIGHT + 1];
		for (MutableBlockProxy proxy : lazyLocalCache.values())
		{
			byte light = proxy.getLight();
			lightLevels[light] += 1;
		}
		for (int i = 0; i < lightLevels.length; ++i)
		{
			Assert.assertEquals(expectedValues[i], lightLevels[i]);
		}
	}

	@Test
	public void onOffSwitch()
	{
		// Write a few logic-related blocks into a cuboid and see what change bits are created.
		Block blockSwitch = ENV.blocks.fromItem(ENV.items.getItemById("op.switch"));
		Block blockLampOff = ENV.blocks.fromItem(ENV.items.getItemById("op.lamp"));
		
		CuboidAddress address = CuboidAddress.fromInt(10, 10, 10);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		Set<AbsoluteLocation> potentialLogicChanges = new HashSet<>();
		AbsoluteLocation switchOn = address.getBase().getRelative(16, 16, 16);
		potentialLogicChanges.addAll(_setBlock(switchOn, cuboid, blockSwitch, true, false, (byte)0x7E));
		AbsoluteLocation switchOff = address.getBase().getRelative(14, 14, 14);
		potentialLogicChanges.addAll(_setBlock(switchOff, cuboid, blockSwitch, false, false, (byte)0x7E));
		AbsoluteLocation lampOff = address.getBase().getRelative(18, 18, 18);
		potentialLogicChanges.addAll(_setBlock(lampOff, cuboid, blockLampOff, false, true, (byte)0x0));
		Assert.assertEquals(12, potentialLogicChanges.size());
		
		List<IMutationBlock> updateMutations = new ArrayList<>();
		Map<AbsoluteLocation, MutableBlockProxy> lazyLocalCache = new HashMap<>();
		Map<AbsoluteLocation, BlockProxy> lazyGlobalCache = new HashMap<>();
		PropagationHelpers.processPreviousTickLogicUpdates((IMutationBlock mutation) -> updateMutations.add(mutation)
			, address
			, Map.of(address, List.copyOf(potentialLogicChanges))
			, (AbsoluteLocation location) -> {
				MutableBlockProxy proxy = lazyLocalCache.get(location);
				if (null == proxy)
				{
					proxy = new MutableBlockProxy(location, cuboid);
					lazyLocalCache.put(location, proxy);
				}
				return proxy;
			}
			, (AbsoluteLocation location) -> {
				BlockProxy proxy = lazyGlobalCache.get(location);
				if (null == proxy)
				{
					proxy = new BlockProxy(location.getBlockAddress(), cuboid);
					lazyGlobalCache.put(location, proxy);
				}
				return proxy;
			}
		);
		
		// Verify we only tried looking at these 12 blocks but didn't change anything (no wires).
		Assert.assertEquals(12, lazyLocalCache.size());
		for (MutableBlockProxy proxy : lazyLocalCache.values())
		{
			Assert.assertFalse(proxy.didChange());
			proxy.writeBack(cuboid);
		}
		
		// We expect to see the 12 update events, since the implementation can only determine that these adjacent values MAY have changed.
		Assert.assertEquals(12, updateMutations.size());
		// Since none of these are wires, they shouldn't have logic values.
		Assert.assertEquals((byte) 0, new BlockProxy(switchOn.getBlockAddress(), cuboid).getLogic());
		Assert.assertEquals((byte) 0, new BlockProxy(switchOff.getBlockAddress(), cuboid).getLogic());
		Assert.assertEquals((byte) 0, new BlockProxy(lampOff.getBlockAddress(), cuboid).getLogic());
	}

	@Test
	public void blockTypePredicates()
	{
		// Just test a few of these predicates to show what they do.
		Block switc = ENV.blocks.fromItem(ENV.items.getItemById("op.switch"));
		Assert.assertTrue(ENV.logic.isSource(switc));
		Block lamp = ENV.blocks.fromItem(ENV.items.getItemById("op.lamp"));
		Assert.assertTrue(ENV.logic.isAware(lamp));
		Assert.assertTrue(ENV.logic.isSink(lamp));
		Block door = ENV.blocks.fromItem(ENV.items.getItemById("op.door"));
		Assert.assertTrue(ENV.logic.isManual(door));
		Assert.assertTrue(ENV.logic.isAware(door));
		Block logicWire = ENV.blocks.fromItem(ENV.items.getItemById("op.logic_wire"));
		Assert.assertTrue(ENV.logic.isAware(logicWire));
		Assert.assertTrue(ENV.logic.isConduit(logicWire));
	}

	@Test
	public void onOffSwitchWithWire()
	{
		// Create a wire and observe it turn on and send updates when an on switch is updated.
		Block blockSwitchOn = ENV.blocks.fromItem(ENV.items.getItemById("op.switch"));
		Block blockLogicWire = ENV.blocks.fromItem(ENV.items.getItemById("op.logic_wire"));
		
		CuboidAddress address = CuboidAddress.fromInt(10, 10, 10);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		Set<AbsoluteLocation> potentialLogicChanges = new HashSet<>();
		AbsoluteLocation switchLocation = address.getBase().getRelative(16, 16, 16);
		potentialLogicChanges.addAll(_setBlock(switchLocation, cuboid, blockSwitchOn, true, false, (byte)0x7E));
		AbsoluteLocation wireLocation = switchLocation.getRelative(1, 0, 0);
		potentialLogicChanges.addAll(_setBlock(wireLocation, cuboid, blockLogicWire, false, false, (byte)0x1));
		
		List<IMutationBlock> updateMutations = new ArrayList<>();
		Map<AbsoluteLocation, MutableBlockProxy> lazyLocalCache = new HashMap<>();
		Map<AbsoluteLocation, BlockProxy> lazyGlobalCache = new HashMap<>();
		PropagationHelpers.processPreviousTickLogicUpdates((IMutationBlock mutation) -> updateMutations.add(mutation)
			, address
			, Map.of(address, List.copyOf(potentialLogicChanges))
			, (AbsoluteLocation location) -> {
				MutableBlockProxy proxy = lazyLocalCache.get(location);
				if (null == proxy)
				{
					proxy = new MutableBlockProxy(location, cuboid);
					lazyLocalCache.put(location, proxy);
				}
				return proxy;
			}
			, (AbsoluteLocation location) -> {
				BlockProxy proxy = lazyGlobalCache.get(location);
				if (null == proxy)
				{
					proxy = new BlockProxy(location.getBlockAddress(), cuboid);
					lazyGlobalCache.put(location, proxy);
				}
				return proxy;
			}
		);
		
		// We only expect the write-back for the wire to actually change anything.
		Assert.assertEquals(6, lazyLocalCache.size());
		for (MutableBlockProxy proxy : lazyLocalCache.values())
		{
			Assert.assertEquals(wireLocation.equals(proxy.absoluteLocation), proxy.didChange());
			proxy.writeBack(cuboid);
		}
		
		// We expect to see the 12 update events:  All blocks adjacent to both updates (including the blocks themselves since there is no filtering).
		Assert.assertEquals(12, updateMutations.size());
		// The logic value should not be set in the switch, but will be site in the wire.
		Assert.assertEquals(0, new BlockProxy(switchLocation.getBlockAddress(), cuboid).getLogic());
		Assert.assertEquals(LogicAspect.MAX_LEVEL, new BlockProxy(wireLocation.getBlockAddress(), cuboid).getLogic());
	}

	@Test
	public void wireBreak()
	{
		// Create a wire a few blocks long, attached to an on switch, initialize the logic values and show the update when we break one near the switch.
		Block blockSwitchOn = ENV.blocks.fromItem(ENV.items.getItemById("op.switch"));
		Block blockLogicWire = ENV.blocks.fromItem(ENV.items.getItemById("op.logic_wire"));
		
		CuboidAddress address = CuboidAddress.fromInt(10, 10, 10);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		AbsoluteLocation switchLocation = address.getBase().getRelative(16, 16, 16);
		MutableBlockProxy mutable = new MutableBlockProxy(switchLocation, cuboid);
		mutable.setBlockAndClear(blockSwitchOn);
		mutable.setLogic(LogicAspect.MAX_LEVEL);
		mutable.writeBack(cuboid);
		AbsoluteLocation brokenWire = switchLocation.getRelative(1, 0, 0);
		mutable = new MutableBlockProxy(brokenWire, cuboid);
		mutable.setLogic((byte)(LogicAspect.MAX_LEVEL - 1));
		mutable.writeBack(cuboid);
		AbsoluteLocation finalWire = brokenWire.getRelative(1, 0, 0);
		mutable = new MutableBlockProxy(finalWire, cuboid);
		mutable.setBlockAndClear(blockLogicWire);
		mutable.setLogic((byte)(LogicAspect.MAX_LEVEL - 2));
		mutable.writeBack(cuboid);
		
		List<IMutationBlock> updateMutations = new ArrayList<>();
		Map<AbsoluteLocation, MutableBlockProxy> lazyLocalCache = new HashMap<>();
		Map<AbsoluteLocation, BlockProxy> lazyGlobalCache = new HashMap<>();
		PropagationHelpers.processPreviousTickLogicUpdates((IMutationBlock mutation) -> updateMutations.add(mutation)
			, address
			, Map.of(address, List.of(brokenWire))
			, (AbsoluteLocation location) -> {
				MutableBlockProxy proxy = lazyLocalCache.get(location);
				if (null == proxy)
				{
					proxy = new MutableBlockProxy(location, cuboid);
					lazyLocalCache.put(location, proxy);
				}
				return proxy;
			}
			, (AbsoluteLocation location) -> {
				BlockProxy proxy = lazyGlobalCache.get(location);
				if (null == proxy)
				{
					proxy = new BlockProxy(location.getBlockAddress(), cuboid);
					lazyGlobalCache.put(location, proxy);
				}
				return proxy;
			}
		);
		
		// Write-back the proxy changes - for the logic write-backs.
		Assert.assertEquals(2, lazyLocalCache.size());
		for (MutableBlockProxy proxy : lazyLocalCache.values())
		{
			proxy.writeBack(cuboid);
		}
		
		// We expect to see the 12 update events:  All blocks adjacent to both updates (including the blocks themselves since there is no filtering).
		Assert.assertEquals(12, updateMutations.size());
		// We expect to see the logic value set in the switch and wire.
		Assert.assertEquals(LogicAspect.MAX_LEVEL, new BlockProxy(switchLocation.getBlockAddress(), cuboid).getLogic());
		Assert.assertEquals(0, new BlockProxy(brokenWire.getBlockAddress(), cuboid).getLogic());
		Assert.assertEquals(0, new BlockProxy(finalWire.getBlockAddress(), cuboid).getLogic());
	}

	@Test
	public void skyLightValues()
	{
		// Just shows what the sky light or multiplier is at different times of day.
		long ticksPerDay = 100L;
		long dawn = 0L;
		long noon = ticksPerDay / 4L;
		long dusk = ticksPerDay / 2L;
		long nextDawn = ticksPerDay - 1L;
		
		// The default value shows the day starting at the brightest point.
		long startTickOffset = 0L;
		byte startValue = PropagationHelpers.currentSkyLightValue(dawn, ticksPerDay, startTickOffset);
		float startMultiplier = PropagationHelpers.skyLightMultiplier(dawn, ticksPerDay, startTickOffset);
		byte noonValue = PropagationHelpers.currentSkyLightValue(noon, ticksPerDay, startTickOffset);
		float noonMultiplier = PropagationHelpers.skyLightMultiplier(noon, ticksPerDay, startTickOffset);
		byte middleValue = PropagationHelpers.currentSkyLightValue(dusk, ticksPerDay, startTickOffset);
		float middleMultiplier = PropagationHelpers.skyLightMultiplier(dusk, ticksPerDay, startTickOffset);
		byte endValue = PropagationHelpers.currentSkyLightValue(nextDawn, ticksPerDay, startTickOffset);
		float endMultiplier = PropagationHelpers.skyLightMultiplier(nextDawn, ticksPerDay, startTickOffset);
		Assert.assertEquals((byte)7, startValue);
		Assert.assertEquals(0.5f, startMultiplier, 0.01f);
		Assert.assertEquals((byte)15, noonValue);
		Assert.assertEquals(1.0f, noonMultiplier, 0.01f);
		Assert.assertEquals((byte)7, middleValue);
		Assert.assertEquals(0.5f, middleMultiplier, 0.01f);
		Assert.assertEquals((byte)7, endValue);
		Assert.assertEquals(0.48f, endMultiplier, 0.01f);
		
		// Verify that this inverts correctly when the day starts half-way through.
		startTickOffset = 50L;
		startValue = PropagationHelpers.currentSkyLightValue(dawn, ticksPerDay, startTickOffset);
		startMultiplier = PropagationHelpers.skyLightMultiplier(dawn, ticksPerDay, startTickOffset);
		noonValue = PropagationHelpers.currentSkyLightValue(noon, ticksPerDay, startTickOffset);
		noonMultiplier = PropagationHelpers.skyLightMultiplier(noon, ticksPerDay, startTickOffset);
		middleValue = PropagationHelpers.currentSkyLightValue(dusk, ticksPerDay, startTickOffset);
		middleMultiplier = PropagationHelpers.skyLightMultiplier(dusk, ticksPerDay, startTickOffset);
		endValue = PropagationHelpers.currentSkyLightValue(nextDawn, ticksPerDay, startTickOffset);
		endMultiplier = PropagationHelpers.skyLightMultiplier(nextDawn, ticksPerDay, startTickOffset);
		Assert.assertEquals((byte)7, startValue);
		Assert.assertEquals(0.5f, startMultiplier, 0.01f);
		Assert.assertEquals((byte)0, noonValue);
		Assert.assertEquals(0.0f, noonMultiplier, 0.01f);
		Assert.assertEquals((byte)7, middleValue);
		Assert.assertEquals(0.5f, middleMultiplier, 0.01f);
		Assert.assertEquals((byte)7, endValue);
		Assert.assertEquals(0.52f, endMultiplier, 0.01f);
	}

	@Test
	public void saveOutAndRestoreDay()
	{
		// Show how we use the dayStartTick to make the day/night cycle continuous across restarts.
		long ticksPerDay = 100L;
		long dayStartTick = 0L;
		
		float multiplier = PropagationHelpers.skyLightMultiplier(0L, ticksPerDay, dayStartTick);
		Assert.assertEquals(0.5f, multiplier, 0.01f);
		multiplier = PropagationHelpers.skyLightMultiplier(10L, ticksPerDay, dayStartTick);
		Assert.assertEquals(0.7f, multiplier, 0.01f);
		dayStartTick = PropagationHelpers.resumableStartTick(10L, ticksPerDay, dayStartTick);
		Assert.assertEquals(10L, dayStartTick);
		multiplier = PropagationHelpers.skyLightMultiplier(0L, ticksPerDay, dayStartTick);
		Assert.assertEquals(0.7f, multiplier, 0.01f);
		multiplier = PropagationHelpers.skyLightMultiplier(10L, ticksPerDay, dayStartTick);
		Assert.assertEquals(0.9f, multiplier, 0.01f);
		dayStartTick = PropagationHelpers.resumableStartTick(15L, ticksPerDay, dayStartTick);
		Assert.assertEquals(25L, dayStartTick);
		multiplier = PropagationHelpers.skyLightMultiplier(0L, ticksPerDay, dayStartTick);
		Assert.assertEquals(1.0f, multiplier, 0.01f);
		// Show what happens if a day really shrinks down.
		ticksPerDay = 10;
		dayStartTick = PropagationHelpers.resumableStartTick(2050L, ticksPerDay, dayStartTick);
		Assert.assertEquals(5L, dayStartTick);
		multiplier = PropagationHelpers.skyLightMultiplier(0L, ticksPerDay, dayStartTick);
		Assert.assertEquals(0.6f, multiplier, 0.01f);
	}

	@Test
	public void timeOfDayAcrossResetsAndSaves()
	{
		// Show how we use the dayStartTick to make the day/night cycle continuous across restarts.
		long ticksPerDay = 100L;
		
		// Initial startup.
		long currentTick = 0L;
		long dayStartTick = 0L;
		float multiplier = PropagationHelpers.skyLightMultiplier(currentTick, ticksPerDay, dayStartTick);
		Assert.assertEquals(0.5f, multiplier, 0.01f);
		currentTick += 20L;
		multiplier = PropagationHelpers.skyLightMultiplier(currentTick, ticksPerDay, dayStartTick);
		Assert.assertEquals(0.9f, multiplier, 0.01f);
		
		// Restart server.
		dayStartTick = PropagationHelpers.resumableStartTick(currentTick, ticksPerDay, dayStartTick);
		Assert.assertEquals(20L, dayStartTick);
		currentTick = 0L;
		multiplier = PropagationHelpers.skyLightMultiplier(currentTick, ticksPerDay, dayStartTick);
		Assert.assertEquals(0.9f, multiplier, 0.01f);
		currentTick += 20L;
		multiplier = PropagationHelpers.skyLightMultiplier(currentTick, ticksPerDay, dayStartTick);
		Assert.assertEquals(0.7f, multiplier, 0.01f);
		
		// Restart server.
		dayStartTick = PropagationHelpers.resumableStartTick(currentTick, ticksPerDay, dayStartTick);
		Assert.assertEquals(40L, dayStartTick);
		currentTick = 0L;
		multiplier = PropagationHelpers.skyLightMultiplier(currentTick, ticksPerDay, dayStartTick);
		Assert.assertEquals(0.7f, multiplier, 0.01f);
		currentTick += 20L;
		multiplier = PropagationHelpers.skyLightMultiplier(currentTick, ticksPerDay, dayStartTick);
		Assert.assertEquals(0.3f, multiplier, 0.01f);
		
		// Reset day.
		dayStartTick = PropagationHelpers.startDayThisTick(currentTick, ticksPerDay);
		multiplier = PropagationHelpers.skyLightMultiplier(currentTick, ticksPerDay, dayStartTick);
		Assert.assertEquals(0.5f, multiplier, 0.01f);
		currentTick += 20L;
		multiplier = PropagationHelpers.skyLightMultiplier(currentTick, ticksPerDay, dayStartTick);
		Assert.assertEquals(0.9f, multiplier, 0.01f);
		
		// Reset day.
		dayStartTick = PropagationHelpers.startDayThisTick(currentTick, ticksPerDay);
		multiplier = PropagationHelpers.skyLightMultiplier(currentTick, ticksPerDay, dayStartTick);
		Assert.assertEquals(0.5f, multiplier, 0.01f);
		currentTick += 20L;
		multiplier = PropagationHelpers.skyLightMultiplier(currentTick, ticksPerDay, dayStartTick);
		Assert.assertEquals(0.9f, multiplier, 0.01f);
		
		// Restart server.
		dayStartTick = PropagationHelpers.resumableStartTick(currentTick, ticksPerDay, dayStartTick);
		Assert.assertEquals(20L, dayStartTick);
		currentTick = 0L;
		multiplier = PropagationHelpers.skyLightMultiplier(currentTick, ticksPerDay, dayStartTick);
		Assert.assertEquals(0.9f, multiplier, 0.01f);
		currentTick += 20L;
		multiplier = PropagationHelpers.skyLightMultiplier(currentTick, ticksPerDay, dayStartTick);
		Assert.assertEquals(0.7f, multiplier, 0.01f);
	}

	@Test
	public void removeBarrierAndLightInOneTick()
	{
		// Show that we correctly handle the case where the barrier to a light source and the light source are both removed in the same tick.
		Block blockStone = ENV.blocks.fromItem(ENV.items.getItemById("op.stone"));
		
		CuboidAddress address = CuboidAddress.fromInt(10, 10, 10);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, blockStone);
		AbsoluteLocation source = address.getBase().getRelative(16, 16, 16);
		AbsoluteLocation open = source.getRelative(1, 0, 0);
		AbsoluteLocation wall = source.getRelative(2, 0, 0);
		
		// Set the cuboid block and light states to what they would be after breaking the source and wall.
		cuboid.setData15(AspectRegistry.BLOCK, source.getBlockAddress(), ENV.special.AIR.item().number());
		cuboid.setData7(AspectRegistry.LIGHT, source.getBlockAddress(), (byte)15);
		cuboid.setData15(AspectRegistry.BLOCK, open.getBlockAddress(), ENV.special.AIR.item().number());
		cuboid.setData7(AspectRegistry.LIGHT, open.getBlockAddress(), (byte)14);
		cuboid.setData15(AspectRegistry.BLOCK, wall.getBlockAddress(), ENV.special.AIR.item().number());
		
		Map<AbsoluteLocation, MutableBlockProxy> lazyLocalCache = new HashMap<>();
		Map<AbsoluteLocation, BlockProxy> lazyGlobalCache = new HashMap<>();
		PropagationHelpers.processPreviousTickLightUpdates(address
				, Map.of(address, List.of(source, wall))
				, (AbsoluteLocation location) -> {
					MutableBlockProxy proxy = lazyLocalCache.get(location);
					if (null == proxy)
					{
						proxy = new MutableBlockProxy(location, cuboid);
						lazyLocalCache.put(location, proxy);
					}
					return proxy;
				}
				, (AbsoluteLocation location) -> {
					BlockProxy proxy = lazyGlobalCache.get(location);
					if (null == proxy)
					{
						proxy = new BlockProxy(location.getBlockAddress(), cuboid);
						lazyGlobalCache.put(location, proxy);
					}
					return proxy;
				}
		);
		
		// See that these have changed to all be dark.
		Assert.assertEquals(3, lazyLocalCache.size());
		for (MutableBlockProxy proxy : lazyLocalCache.values())
		{
			byte light = proxy.getLight();
			Assert.assertEquals(0, light);
		}
	}


	private Set<AbsoluteLocation> _setBlock(AbsoluteLocation location, CuboidData cuboid, Block block, boolean setActive, boolean checkLight, byte expectedLogicBits)
	{
		MutableBlockProxy mutable = new MutableBlockProxy(location, cuboid);
		mutable.setBlockAndClear(block);
		if (setActive)
		{
			mutable.setFlags(FlagsAspect.FLAG_ACTIVE);
		}
		if (checkLight)
		{
			Assert.assertTrue(mutable.mayTriggerLightingChange());
		}
		byte logicBits = mutable.potentialLogicChangeBits();
		Assert.assertTrue(expectedLogicBits == logicBits);
		mutable.writeBack(cuboid);
		
		Set<AbsoluteLocation> potentialLogicChangeLocations = new HashSet<>();
		LogicLayerHelpers.populateSetWithPotentialLogicChanges(potentialLogicChangeLocations, location, logicBits);
		return potentialLogicChangeLocations;
	}
}
