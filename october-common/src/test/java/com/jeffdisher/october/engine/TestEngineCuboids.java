package com.jeffdisher.october.engine;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.CuboidHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.HeightMapHelpers;
import com.jeffdisher.october.logic.ScheduledMutation;
import com.jeffdisher.october.mutations.MutationBlockIncrementalBreak;
import com.jeffdisher.october.mutations.MutationBlockOverwriteByEntity;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.ContextBuilder;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestEngineCuboids
{
	private static Environment ENV;
	private static Block STONE;
	@BeforeClass
	public static void setup() throws Throwable
	{
		ENV = Environment.createSharedInstance();
		STONE = ENV.blocks.fromItem(ENV.items.getItemById("op.stone"));
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void placeBlockCheckHeight()
	{
		// Place a block with a height map to update.
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		CuboidHeightMap heightMap = HeightMapHelpers.buildHeightMap(cuboid);
		
		TickProcessingContext context = ContextBuilder.build()
			.lookups(ContextBuilder.buildFetcher((AbsoluteLocation location) -> {
				return (cuboid.getCuboidAddress().equals(location.getCuboidAddress()))
					? BlockProxy.load(location.getBlockAddress(), cuboid)
					: null
				;
			}), null, null)
			.eventSink((EventRecord event) -> {})
			.finish()
		;
		
		AbsoluteLocation target = new AbsoluteLocation(14, 15, 16);
		MutationBlockOverwriteByEntity mutation = new MutationBlockOverwriteByEntity(target, STONE, null, 1);
		
		EngineCuboids.SingleCuboidResult result = EngineCuboids.processOneCuboid(context
			, Set.of(address)
			, List.of(new ScheduledMutation(mutation, 0L))
			, Map.of()
			, Map.of()
			, Map.of()
			, Map.of()
			, Set.of()
			, address
			, cuboid
			, heightMap
		);
		IReadOnlyCuboidData newCuboid = result.changedCuboidOrNull();
		CuboidHeightMap newMap = result.changedHeightMap();
		
		Assert.assertEquals(STONE.item().number(), newCuboid.getData15(AspectRegistry.BLOCK, target.getBlockAddress()));
		Assert.assertEquals(target.z(), newMap.getHightestSolidBlock(target.x(), target.y()));
	}

	@Test
	public void placeBlockNoHeight()
	{
		// Show that we still progress correctly but don't return an updated map if we don't ask for one.
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		
		TickProcessingContext context = ContextBuilder.build()
			.lookups(ContextBuilder.buildFetcher((AbsoluteLocation location) -> {
				return (cuboid.getCuboidAddress().equals(location.getCuboidAddress()))
					? BlockProxy.load(location.getBlockAddress(), cuboid)
					: null
				;
			}), null, null)
			.eventSink((EventRecord event) -> {})
			.finish()
		;
		
		AbsoluteLocation target = new AbsoluteLocation(14, 15, 16);
		MutationBlockOverwriteByEntity mutation = new MutationBlockOverwriteByEntity(target, STONE, null, 1);
		
		EngineCuboids.SingleCuboidResult result = EngineCuboids.processOneCuboid(context
			, Set.of(address)
			, List.of(new ScheduledMutation(mutation, 0L))
			, Map.of()
			, Map.of()
			, Map.of()
			, Map.of()
			, Set.of()
			, address
			, cuboid
			, null
		);
		IReadOnlyCuboidData newCuboid = result.changedCuboidOrNull();
		CuboidHeightMap newMap = result.changedHeightMap();
		
		Assert.assertEquals(STONE.item().number(), newCuboid.getData15(AspectRegistry.BLOCK, target.getBlockAddress()));
		Assert.assertNull(newMap);
	}

	@Test
	public void updateBlockNoHeightChange()
	{
		// Change a block but not in a way which would change its height map and verify it is the same instance.
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		AbsoluteLocation target = new AbsoluteLocation(14, 15, 16);
		cuboid.setData15(AspectRegistry.BLOCK, target.getBlockAddress(), STONE.item().number());
		CuboidHeightMap heightMap = HeightMapHelpers.buildHeightMap(cuboid);
		
		TickProcessingContext context = ContextBuilder.build()
			.lookups(ContextBuilder.buildFetcher((AbsoluteLocation location) -> {
				return (cuboid.getCuboidAddress().equals(location.getCuboidAddress()))
					? BlockProxy.load(location.getBlockAddress(), cuboid)
					: null
				;
			}), null, null)
			.eventSink((EventRecord event) -> {})
			.finish()
		;
		
		int damage = 100;
		MutationBlockIncrementalBreak mutation = new MutationBlockIncrementalBreak(target, damage, 0);
		
		EngineCuboids.SingleCuboidResult result = EngineCuboids.processOneCuboid(context
			, Set.of(address)
			, List.of(new ScheduledMutation(mutation, 0L))
			, Map.of()
			, Map.of()
			, Map.of()
			, Map.of()
			, Set.of()
			, address
			, cuboid
			, heightMap
		);
		IReadOnlyCuboidData newCuboid = result.changedCuboidOrNull();
		CuboidHeightMap newMap = result.changedHeightMap();
		
		Assert.assertEquals(STONE.item().number(), newCuboid.getData15(AspectRegistry.BLOCK, target.getBlockAddress()));
		Assert.assertEquals(damage, newCuboid.getDataSpecial(AspectRegistry.DAMAGE, target.getBlockAddress()).intValue());
		Assert.assertNull(newMap);
	}
}
