package com.jeffdisher.october.logic;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.ContextBuilder;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestFireHelpers
{
	private static Environment ENV;
	private static Block STONE;
	private static Block LOG;
	private static Block WATER_SOURCE;
	@BeforeClass
	public static void setup() throws Throwable
	{
		ENV = Environment.createSharedInstance();
		STONE = ENV.blocks.fromItem(ENV.items.getItemById("op.stone"));
		LOG = ENV.blocks.fromItem(ENV.items.getItemById("op.log"));
		WATER_SOURCE = ENV.blocks.fromItem(ENV.items.getItemById("op.water_source"));
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void allFlammableNeighbours()
	{
		// Check in a fully flammable cuboid.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), LOG);
		AbsoluteLocation centre = cuboid.getCuboidAddress().getBase().getRelative(16, 16, 16);
		TickProcessingContext context = ContextBuilder.build()
				.lookups((AbsoluteLocation location) -> {
					return BlockProxy.load(location.getBlockAddress(), cuboid);
				}, null, null)
				.finish()
		;
		List<AbsoluteLocation> neighbours = FireHelpers.findFlammableNeighbours(ENV, context, centre);
		Set<AbsoluteLocation> set = new HashSet<>(neighbours);
		Assert.assertEquals(11, set.size());
	}

	@Test
	public void noFlammableNeighbours()
	{
		// Check in a fully flammable cuboid.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), STONE);
		AbsoluteLocation centre = cuboid.getCuboidAddress().getBase().getRelative(16, 16, 16);
		TickProcessingContext context = ContextBuilder.build()
				.lookups((AbsoluteLocation location) -> {
					return BlockProxy.load(location.getBlockAddress(), cuboid);
				}, null, null)
				.finish()
		;
		List<AbsoluteLocation> neighbours = FireHelpers.findFlammableNeighbours(ENV, context, centre);
		Assert.assertEquals(0, neighbours.size());
	}

	@Test
	public void isNearFireSource()
	{
		// Check in a fully flammable cuboid.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), LOG);
		AbsoluteLocation centre = cuboid.getCuboidAddress().getBase().getRelative(16, 16, 16);
		cuboid.setData7(AspectRegistry.FLAGS, centre.getBlockAddress(), FlagsAspect.FLAG_BURNING);
		TickProcessingContext context = ContextBuilder.build()
				.lookups((AbsoluteLocation location) -> {
					return BlockProxy.load(location.getBlockAddress(), cuboid);
				}, null, null)
				.finish()
		;
		Assert.assertTrue(FireHelpers.isNearFireSource(ENV, context, centre.getRelative(1, 0, 0)));
		Assert.assertFalse(FireHelpers.isNearFireSource(ENV, context, centre.getRelative(2, 0, 0)));
	}

	@Test
	public void canIgnite()
	{
		// Check a few blocks next to a fire source with some under water and some already burning.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), LOG);
		AbsoluteLocation centre = cuboid.getCuboidAddress().getBase().getRelative(16, 16, 16);
		AbsoluteLocation canStart = centre.getRelative(1, 0, 0);
		AbsoluteLocation cannotStart = centre.getRelative(-1, 0, 0);
		AbsoluteLocation alreadyBurning = centre.getRelative(0, 1, 0);
		cuboid.setData7(AspectRegistry.FLAGS, centre.getBlockAddress(), FlagsAspect.FLAG_BURNING);
		cuboid.setData15(AspectRegistry.BLOCK, cannotStart.getRelative(0, 0, 1).getBlockAddress(), WATER_SOURCE.item().number());
		cuboid.setData7(AspectRegistry.FLAGS, alreadyBurning.getBlockAddress(), FlagsAspect.FLAG_BURNING);
		TickProcessingContext context = ContextBuilder.build()
				.lookups((AbsoluteLocation location) -> {
					return BlockProxy.load(location.getBlockAddress(), cuboid);
				}, null, null)
				.finish()
		;
		Assert.assertTrue(FireHelpers.canIgnite(ENV, context, canStart, BlockProxy.load(canStart.getBlockAddress(), cuboid)));
		Assert.assertFalse(FireHelpers.canIgnite(ENV, context, cannotStart, BlockProxy.load(cannotStart.getBlockAddress(), cuboid)));
		Assert.assertFalse(FireHelpers.canIgnite(ENV, context, alreadyBurning, BlockProxy.load(alreadyBurning.getBlockAddress(), cuboid)));
	}

	@Test
	public void shouldExtinguish()
	{
		// Check some burning and not blocks, some with water above.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), LOG);
		AbsoluteLocation burnNoWater = cuboid.getCuboidAddress().getBase().getRelative(16, 16, 16);
		AbsoluteLocation noBurnNoWater = burnNoWater.getRelative(1, 0, 0);
		AbsoluteLocation noBurnWater = burnNoWater.getRelative(2, 0, 0);
		AbsoluteLocation burnWater = burnNoWater.getRelative(3, 0, 0);
		cuboid.setData7(AspectRegistry.FLAGS, burnNoWater.getBlockAddress(), FlagsAspect.FLAG_BURNING);
		cuboid.setData15(AspectRegistry.BLOCK, noBurnWater.getRelative(0, 0, 1).getBlockAddress(), WATER_SOURCE.item().number());
		cuboid.setData7(AspectRegistry.FLAGS, burnWater.getBlockAddress(), FlagsAspect.FLAG_BURNING);
		cuboid.setData15(AspectRegistry.BLOCK, burnWater.getRelative(0, 0, 1).getBlockAddress(), WATER_SOURCE.item().number());
		TickProcessingContext context = ContextBuilder.build()
				.lookups((AbsoluteLocation location) -> {
					return BlockProxy.load(location.getBlockAddress(), cuboid);
				}, null, null)
				.finish()
		;
		Assert.assertFalse(FireHelpers.shouldExtinguish(ENV, context, burnNoWater, BlockProxy.load(burnNoWater.getBlockAddress(), cuboid)));
		Assert.assertFalse(FireHelpers.shouldExtinguish(ENV, context, noBurnNoWater, BlockProxy.load(noBurnNoWater.getBlockAddress(), cuboid)));
		Assert.assertFalse(FireHelpers.shouldExtinguish(ENV, context, noBurnWater, BlockProxy.load(noBurnWater.getBlockAddress(), cuboid)));
		Assert.assertTrue(FireHelpers.shouldExtinguish(ENV, context, burnWater, BlockProxy.load(burnWater.getBlockAddress(), cuboid)));
	}
}
