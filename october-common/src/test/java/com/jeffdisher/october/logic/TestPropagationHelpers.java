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
		
		CuboidAddress address = new CuboidAddress((short)10, (short)10, (short)10);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		AbsoluteLocation lantern = address.getBase().getRelative(16, 16, 16);
		MutableBlockProxy mutable = new MutableBlockProxy(lantern, cuboid);
		mutable.setBlockAndClear(blockLantern);
		mutable.writeBack(cuboid);
		
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
		
		CuboidAddress address = new CuboidAddress((short)10, (short)10, (short)10);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		AbsoluteLocation switchOn = address.getBase().getRelative(16, 16, 16);
		MutableBlockProxy mutable = new MutableBlockProxy(switchOn, cuboid);
		mutable.setBlockAndClear(blockSwitchOn);
		mutable.writeBack(cuboid);
		AbsoluteLocation switchOff = address.getBase().getRelative(14, 14, 14);
		mutable = new MutableBlockProxy(switchOff, cuboid);
		mutable.setBlockAndClear(blockSwitchOff);
		mutable.writeBack(cuboid);
		AbsoluteLocation lampOff = address.getBase().getRelative(18, 18, 18);
		mutable = new MutableBlockProxy(lampOff, cuboid);
		mutable.setBlockAndClear(blockLampOff);
		mutable.writeBack(cuboid);
		
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
}
