package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.LightAspect;
import com.jeffdisher.october.aspects.LogicAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.worldgen.CuboidGenerator;


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
		_setBlock(lantern, cuboid, blockLantern, true, false);
		
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
		// Create a bunch of "potential" updates and verify that events only appear around the new high source.
		Block blockSwitchOn = ENV.blocks.fromItem(ENV.items.getItemById("op.switch_on"));
		Block blockSwitchOff = ENV.blocks.fromItem(ENV.items.getItemById("op.switch_off"));
		Block blockLampOff = ENV.blocks.fromItem(ENV.items.getItemById("op.lamp_off"));
		
		CuboidAddress address = CuboidAddress.fromInt(10, 10, 10);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		AbsoluteLocation switchOn = address.getBase().getRelative(16, 16, 16);
		_setBlock(switchOn, cuboid, blockSwitchOn, false, true);
		AbsoluteLocation switchOff = address.getBase().getRelative(14, 14, 14);
		_setBlock(switchOff, cuboid, blockSwitchOff, false, false);
		AbsoluteLocation lampOff = address.getBase().getRelative(18, 18, 18);
		_setBlock(lampOff, cuboid, blockLampOff, true, false);
		
		List<IMutationBlock> updateMutations = new ArrayList<>();
		Map<AbsoluteLocation, MutableBlockProxy> lazyLocalCache = new HashMap<>();
		Map<AbsoluteLocation, BlockProxy> lazyGlobalCache = new HashMap<>();
		PropagationHelpers.processPreviousTickLogicUpdates((IMutationBlock mutation) -> updateMutations.add(mutation)
			, address
			, Map.of(address, List.of(switchOn, switchOff, lampOff))
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
		
		// Write-back the proxy changes.
		Assert.assertEquals(3, lazyLocalCache.size());
		for (MutableBlockProxy proxy : lazyLocalCache.values())
		{
			proxy.writeBack(cuboid);
		}
		
		// We expect to see the 6 update events, only.
		Assert.assertEquals(6, updateMutations.size());
		// And the logic level should also be updated.
		BlockProxy immutable = new BlockProxy(switchOn.getBlockAddress(), cuboid);
		Assert.assertEquals((byte) LogicAspect.MAX_LEVEL, immutable.getLogic());
	}

	@Test
	public void blockTypePredicates()
	{
		// Just test a few of these predicates to show what they do.
		Block switchOff = ENV.blocks.fromItem(ENV.items.getItemById("op.switch_off"));
		Assert.assertTrue(ENV.logic.isSource(switchOff));
		Block switchOn = ENV.blocks.fromItem(ENV.items.getItemById("op.switch_on"));
		Assert.assertTrue(ENV.logic.isHigh(switchOn));
		Block lampOff = ENV.blocks.fromItem(ENV.items.getItemById("op.lamp_off"));
		Assert.assertTrue(ENV.logic.isAware(lampOff));
		Block lampOn = ENV.blocks.fromItem(ENV.items.getItemById("op.lamp_on"));
		Assert.assertTrue(ENV.logic.isSink(lampOn));
		Block doorClosed = ENV.blocks.fromItem(ENV.items.getItemById("op.door_closed"));
		Assert.assertTrue(ENV.logic.isManual(doorClosed));
		Block doorOpen = ENV.blocks.fromItem(ENV.items.getItemById("op.door_open"));
		Assert.assertTrue(ENV.logic.isAware(doorOpen));
		Block logicWire = ENV.blocks.fromItem(ENV.items.getItemById("op.logic_wire"));
		Assert.assertTrue(ENV.logic.isAware(logicWire));
		Assert.assertTrue(ENV.logic.isConduit(logicWire));
	}

	@Test
	public void onOffSwitchWithWire()
	{
		// Create a wire and observe it turn on and send updates when an on switch is updated.
		Block blockSwitchOn = ENV.blocks.fromItem(ENV.items.getItemById("op.switch_on"));
		Block blockLogicWire = ENV.blocks.fromItem(ENV.items.getItemById("op.logic_wire"));
		
		CuboidAddress address = CuboidAddress.fromInt(10, 10, 10);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		AbsoluteLocation switchLocation = address.getBase().getRelative(16, 16, 16);
		_setBlock(switchLocation, cuboid, blockSwitchOn, false, true);
		AbsoluteLocation wireLocation = switchLocation.getRelative(1, 0, 0);
		_setBlock(wireLocation, cuboid, blockLogicWire, false, false);
		
		List<IMutationBlock> updateMutations = new ArrayList<>();
		Map<AbsoluteLocation, MutableBlockProxy> lazyLocalCache = new HashMap<>();
		Map<AbsoluteLocation, BlockProxy> lazyGlobalCache = new HashMap<>();
		PropagationHelpers.processPreviousTickLogicUpdates((IMutationBlock mutation) -> updateMutations.add(mutation)
			, address
			, Map.of(address, List.of(switchLocation))
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
		Assert.assertEquals(LogicAspect.MAX_LEVEL - 1, new BlockProxy(wireLocation.getBlockAddress(), cuboid).getLogic());
	}

	@Test
	public void wireBreak()
	{
		// Create a wire a few blocks long, attached to an on switch, initialize the logic values and show the update when we break one near the switch.
		Block blockSwitchOn = ENV.blocks.fromItem(ENV.items.getItemById("op.switch_on"));
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


	private void _setBlock(AbsoluteLocation location, CuboidData cuboid, Block block, boolean checkLight, boolean checkLogic)
	{
		MutableBlockProxy mutable = new MutableBlockProxy(location, cuboid);
		mutable.setBlockAndClear(block);
		if (checkLight)
		{
			Assert.assertTrue(mutable.mayTriggerLightingChange());
		}
		if (checkLogic)
		{
			Assert.assertTrue(mutable.mayTriggerLogicChange());
		}
		mutable.writeBack(cuboid);
	}
}
