package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.List;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.mutations.MutationBlockOverwriteInternal;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.ContextBuilder;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.IMutationBlock;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestPlantHelpers
{
	private static Environment ENV;
	private static Block SAPLING;
	private static Block WHEAT_YOUNG;
	private static Block WHEAT_MATURE;
	private static Block LEAF;
	private static Block LOG;
	@BeforeClass
	public static void setup() throws Throwable
	{
		ENV = Environment.createSharedInstance();
		SAPLING = ENV.blocks.fromItem(ENV.items.getItemById("op.sapling"));
		WHEAT_YOUNG = ENV.blocks.fromItem(ENV.items.getItemById("op.wheat_young"));
		WHEAT_MATURE = ENV.blocks.fromItem(ENV.items.getItemById("op.wheat_mature"));
		LEAF = ENV.blocks.fromItem(ENV.items.getItemById("op.leaf"));
		LOG = ENV.blocks.fromItem(ENV.items.getItemById("op.log"));
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void canGrow()
	{
		Assert.assertTrue(PlantHelpers.canGrow(ENV, SAPLING));
		Assert.assertTrue(PlantHelpers.canGrow(ENV, WHEAT_YOUNG));
		Assert.assertFalse(PlantHelpers.canGrow(ENV, WHEAT_MATURE));
		Assert.assertFalse(PlantHelpers.canGrow(ENV, LEAF));
		Assert.assertFalse(PlantHelpers.canGrow(ENV, LOG));
	}

	@Test
	public void growSapling()
	{
		// Show what happens when a sapling gets a growth event, whether in the light or dark.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		byte[] refLight = new byte[] {15};
		List<MutationBlockOverwriteInternal> mutations = new ArrayList<>();
		TickProcessingContext context = _buildContext(cuboid, refLight, mutations);
		AbsoluteLocation target = new AbsoluteLocation(10, 10, 10);
		
		// Light.
		MutableBlockProxy proxy = new MutableBlockProxy(target, cuboid);
		proxy.setBlockAndClear(SAPLING);
		boolean reschedule = PlantHelpers.shouldRescheduleAfterPlantPeriodic(ENV, context, target, proxy);
		Assert.assertFalse(reschedule);
		Assert.assertEquals(LOG, proxy.getBlock());
		Assert.assertEquals(5, mutations.size());
		mutations.clear();
		
		// Dark.
		refLight[0] = 0;
		proxy = new MutableBlockProxy(target, cuboid);
		proxy.setBlockAndClear(SAPLING);
		reschedule = PlantHelpers.shouldRescheduleAfterPlantPeriodic(ENV, context, target, proxy);
		Assert.assertTrue(reschedule);
		Assert.assertEquals(SAPLING, proxy.getBlock());
		Assert.assertEquals(0, mutations.size());
	}

	@Test
	public void growWheatYoung()
	{
		// Show what happens when a young wheat with no growth gets a growth event, whether in the light or dark.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		byte[] refLight = new byte[] {15};
		TickProcessingContext context = _buildContext(cuboid, refLight, null);
		AbsoluteLocation target = new AbsoluteLocation(10, 10, 10);
		
		// Light.
		MutableBlockProxy proxy = new MutableBlockProxy(target, cuboid);
		proxy.setBlockAndClear(WHEAT_YOUNG);
		boolean reschedule = PlantHelpers.shouldRescheduleAfterPlantPeriodic(ENV, context, target, proxy);
		Assert.assertTrue(reschedule);
		Assert.assertEquals(WHEAT_YOUNG, proxy.getBlock());
		Assert.assertEquals((byte)1, proxy.getBlockDefinedByte());
		
		// Dark.
		refLight[0] = 0;
		proxy = new MutableBlockProxy(target, cuboid);
		proxy.setBlockAndClear(WHEAT_YOUNG);
		reschedule = PlantHelpers.shouldRescheduleAfterPlantPeriodic(ENV, context, target, proxy);
		Assert.assertTrue(reschedule);
		Assert.assertEquals(WHEAT_YOUNG, proxy.getBlock());
		Assert.assertEquals((byte)0, proxy.getBlockDefinedByte());
	}

	@Test
	public void growWheatMature()
	{
		// Show what happens when a young wheat with full growth gets a growth event, whether in the light or dark.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		byte[] refLight = new byte[] {15};
		TickProcessingContext context = _buildContext(cuboid, refLight, null);
		AbsoluteLocation target = new AbsoluteLocation(10, 10, 10);
		byte finalGrowth = (byte)(ENV.plants.growthStagesForPlant(WHEAT_YOUNG) - 1);
		
		// Light.
		MutableBlockProxy proxy = new MutableBlockProxy(target, cuboid);
		proxy.setBlockAndClear(WHEAT_YOUNG);
		proxy.setBlockDefinedByte(finalGrowth);
		boolean reschedule = PlantHelpers.shouldRescheduleAfterPlantPeriodic(ENV, context, target, proxy);
		Assert.assertFalse(reschedule);
		Assert.assertEquals(WHEAT_MATURE, proxy.getBlock());
		Assert.assertEquals((byte)0, proxy.getBlockDefinedByte());
		
		// Dark.
		refLight[0] = 0;
		proxy = new MutableBlockProxy(target, cuboid);
		proxy.setBlockAndClear(WHEAT_YOUNG);
		proxy.setBlockDefinedByte(finalGrowth);
		reschedule = PlantHelpers.shouldRescheduleAfterPlantPeriodic(ENV, context, target, proxy);
		Assert.assertTrue(reschedule);
		Assert.assertEquals(WHEAT_YOUNG, proxy.getBlock());
		Assert.assertEquals(finalGrowth, proxy.getBlockDefinedByte());
	}


	private static TickProcessingContext _buildContext(CuboidData cuboid, byte[] refLight, List<MutationBlockOverwriteInternal> out_mutations)
	{
		TickProcessingContext context = ContextBuilder.build()
			.lookups(ContextBuilder.buildFetcher((AbsoluteLocation location) -> BlockProxy.load(location.getBlockAddress(), cuboid)), null, null)
			.fixedRandom(1)
			.skyLight((AbsoluteLocation location) -> refLight[0])
			.sinks(new TickProcessingContext.IMutationSink() {
				@Override
				public boolean next(IMutationBlock mutation)
				{
					out_mutations.add((MutationBlockOverwriteInternal) mutation);
					return true;
				}
				@Override
				public boolean future(IMutationBlock mutation, long millisToDelay)
				{
					throw new AssertionError("Not in test");
				}
			}, null)
			.finish()
		;
		return context;
	}
}
