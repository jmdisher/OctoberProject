package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.List;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.mutations.MutationBlockOverwriteMisc;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.ContextBuilder;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.FacingDirection;
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
	private static Block BRANCH;
	private static Block TILLED_SOIL;
	private static Block WATER_SOURCE;
	@BeforeClass
	public static void setup() throws Throwable
	{
		ENV = Environment.createSharedInstance();
		SAPLING = ENV.blocks.fromItem(ENV.items.getItemById("op.sapling"));
		WHEAT_YOUNG = ENV.blocks.fromItem(ENV.items.getItemById("op.wheat_young"));
		WHEAT_MATURE = ENV.blocks.fromItem(ENV.items.getItemById("op.wheat_mature"));
		LEAF = ENV.blocks.fromItem(ENV.items.getItemById("op.leaf"));
		LOG = ENV.blocks.fromItem(ENV.items.getItemById("op.log"));
		BRANCH = ENV.blocks.fromItem(ENV.items.getItemById("op.branch"));
		TILLED_SOIL = ENV.blocks.fromItem(ENV.items.getItemById("op.tilled_soil"));
		WATER_SOURCE = ENV.blocks.fromItem(ENV.items.getItemById("op.water_source"));
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
		Assert.assertTrue(PlantHelpers.canGrow(ENV, BRANCH));
	}

	@Test
	public void growSapling()
	{
		// Show what happens when a sapling gets a growth event, whether in the light or dark.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		byte[] refLight = new byte[] {15};
		List<MutationBlockOverwriteMisc> mutations = new ArrayList<>();
		TickProcessingContext context = _buildContext(cuboid, refLight, mutations);
		AbsoluteLocation target = new AbsoluteLocation(10, 10, 10);
		
		// Light.
		MutableBlockProxy proxy = new MutableBlockProxy(target, cuboid);
		proxy.setBlockAndClear(SAPLING);
		PlantHelpers.runPlantPeriodic(ENV, context, target, proxy);
		Assert.assertEquals(LOG, proxy.getBlock());
		Assert.assertEquals(1, mutations.size());
		mutations.clear();
		
		// Dark.
		refLight[0] = 0;
		proxy = new MutableBlockProxy(target, cuboid);
		proxy.setBlockAndClear(SAPLING);
		PlantHelpers.runPlantPeriodic(ENV, context, target, proxy);
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
		cuboid.setData15(AspectRegistry.BLOCK, target.getRelative(0, 0, -1).getBlockAddress(), TILLED_SOIL.item().number());
		cuboid.setData7(AspectRegistry.BLOCK_DEFINED_BYTE, target.getRelative(0, 0, -1).getBlockAddress(), PlantHelpers.BLOCK_HYDRATED_BYTE);
		
		// Light.
		MutableBlockProxy proxy = new MutableBlockProxy(target, cuboid);
		proxy.setBlockAndClear(WHEAT_YOUNG);
		PlantHelpers.runPlantPeriodic(ENV, context, target, proxy);
		Assert.assertEquals(WHEAT_YOUNG, proxy.getBlock());
		Assert.assertEquals((byte)1, proxy.getBlockDefinedByte());
		
		// Dark.
		refLight[0] = 0;
		proxy = new MutableBlockProxy(target, cuboid);
		proxy.setBlockAndClear(WHEAT_YOUNG);
		PlantHelpers.runPlantPeriodic(ENV, context, target, proxy);
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
		cuboid.setData15(AspectRegistry.BLOCK, target.getRelative(0, 0, -1).getBlockAddress(), TILLED_SOIL.item().number());
		cuboid.setData7(AspectRegistry.BLOCK_DEFINED_BYTE, target.getRelative(0, 0, -1).getBlockAddress(), PlantHelpers.BLOCK_HYDRATED_BYTE);
		
		// Light.
		MutableBlockProxy proxy = new MutableBlockProxy(target, cuboid);
		proxy.setBlockAndClear(WHEAT_YOUNG);
		proxy.setBlockDefinedByte(finalGrowth);
		PlantHelpers.runPlantPeriodic(ENV, context, target, proxy);
		Assert.assertEquals(WHEAT_MATURE, proxy.getBlock());
		Assert.assertEquals((byte)0, proxy.getBlockDefinedByte());
		
		// Dark.
		refLight[0] = 0;
		proxy = new MutableBlockProxy(target, cuboid);
		proxy.setBlockAndClear(WHEAT_YOUNG);
		proxy.setBlockDefinedByte(finalGrowth);
		PlantHelpers.runPlantPeriodic(ENV, context, target, proxy);
		Assert.assertEquals(WHEAT_YOUNG, proxy.getBlock());
		Assert.assertEquals(finalGrowth, proxy.getBlockDefinedByte());
	}

	@Test
	public void growBranch()
	{
		// Show what happens when a branch gets a growth event, whether in the light or dark (should be the same).
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		byte[] refLight = new byte[] {15};
		List<MutationBlockOverwriteMisc> mutations = new ArrayList<>();
		TickProcessingContext context = _buildContext(cuboid, refLight, mutations);
		AbsoluteLocation target = new AbsoluteLocation(10, 10, 10);
		
		// Light.
		MutableBlockProxy proxy = new MutableBlockProxy(target, cuboid);
		proxy.setBlockAndClear(BRANCH);
		proxy.setOrientation(FacingDirection.DOWN);
		PlantHelpers.runPlantPeriodic(ENV, context, target, proxy);
		Assert.assertEquals(LOG, proxy.getBlock());
		Assert.assertEquals((byte)0, proxy.getBlockDefinedByte());
		Assert.assertEquals(5, mutations.size());
		mutations.clear();
		
		// Dark.
		refLight[0] = 0;
		proxy = new MutableBlockProxy(target, cuboid);
		proxy.setBlockAndClear(BRANCH);
		proxy.setOrientation(FacingDirection.DOWN);
		PlantHelpers.runPlantPeriodic(ENV, context, target, proxy);
		Assert.assertEquals(LOG, proxy.getBlock());
		Assert.assertEquals((byte)0, proxy.getBlockDefinedByte());
		Assert.assertEquals(5, mutations.size());
	}

	@Test
	public void hydrateGround()
	{
		// Show that we set the hydrated bit and then clear it based on checking tilled soil and finding water appear and then disappear.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		AbsoluteLocation soil = new AbsoluteLocation(1, 2, 3);
		AbsoluteLocation water = soil.getRelative(4, 4, 0);
		cuboid.setData15(AspectRegistry.BLOCK, soil.getBlockAddress(), TILLED_SOIL.item().number());
		
		TickProcessingContext context = ContextBuilder.build()
			.lookups(ContextBuilder.buildFetcher((AbsoluteLocation location) -> BlockProxy.load(location.getBlockAddress(), cuboid)), null, null)
			.finish()
		;
		
		// Set the block hydrated.
		cuboid.setData15(AspectRegistry.BLOCK, water.getBlockAddress(), WATER_SOURCE.item().number());
		MutableBlockProxy proxy = new MutableBlockProxy(soil, cuboid);
		PlantHelpers.runSoilHydrationPeriodic(ENV, context, water, proxy);
		Assert.assertTrue(PlantHelpers.isSoilHydrated(ENV, proxy));
		proxy.writeBack(cuboid);
		
		// Set the block dry.
		cuboid.setData15(AspectRegistry.BLOCK, water.getBlockAddress(), ENV.special.AIR.item().number());
		proxy = new MutableBlockProxy(soil, cuboid);
		PlantHelpers.runSoilHydrationPeriodic(ENV, context, water, proxy);
		Assert.assertFalse(PlantHelpers.isSoilHydrated(ENV, proxy));
		proxy.writeBack(cuboid);
	}

	@Test
	public void growFailGrowthDry()
	{
		// Show that we fail to grow when the soil under us is dry.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		TickProcessingContext context = ContextBuilder.build()
			.lookups(ContextBuilder.buildFetcher((AbsoluteLocation location) -> BlockProxy.load(location.getBlockAddress(), cuboid)), null, null)
			.fixedRandom(1)
			.skyLight((AbsoluteLocation location) -> 15)
			.finish()
		;
		AbsoluteLocation target = new AbsoluteLocation(10, 10, 10);
		cuboid.setData15(AspectRegistry.BLOCK, target.getRelative(0, 0, -1).getBlockAddress(), TILLED_SOIL.item().number());
		
		// Dry.
		MutableBlockProxy proxy = new MutableBlockProxy(target, cuboid);
		proxy.setBlockAndClear(WHEAT_YOUNG);
		PlantHelpers.runPlantPeriodic(ENV, context, target, proxy);
		Assert.assertEquals(WHEAT_YOUNG, proxy.getBlock());
		Assert.assertEquals((byte)0, proxy.getBlockDefinedByte());
		
		// Hydrated.
		cuboid.setData7(AspectRegistry.BLOCK_DEFINED_BYTE, target.getRelative(0, 0, -1).getBlockAddress(), PlantHelpers.BLOCK_HYDRATED_BYTE);
		proxy = new MutableBlockProxy(target, cuboid);
		proxy.setBlockAndClear(WHEAT_YOUNG);
		PlantHelpers.runPlantPeriodic(ENV, context, target, proxy);
		Assert.assertEquals(WHEAT_YOUNG, proxy.getBlock());
		Assert.assertEquals((byte)1, proxy.getBlockDefinedByte());
	}


	private static TickProcessingContext _buildContext(CuboidData cuboid, byte[] refLight, List<MutationBlockOverwriteMisc> out_mutations)
	{
		TickProcessingContext context = ContextBuilder.build()
			.lookups(ContextBuilder.buildFetcher((AbsoluteLocation location) -> BlockProxy.load(location.getBlockAddress(), cuboid)), null, null)
			.fixedRandom(1)
			.skyLight((AbsoluteLocation location) -> refLight[0])
			.sinks(new TickProcessingContext.IMutationSink() {
				@Override
				public boolean next(IMutationBlock mutation)
				{
					out_mutations.add((MutationBlockOverwriteMisc) mutation);
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
