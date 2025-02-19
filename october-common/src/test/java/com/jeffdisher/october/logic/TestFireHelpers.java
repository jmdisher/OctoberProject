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
import com.jeffdisher.october.worldgen.CuboidGenerator;


public class TestFireHelpers
{
	private static Environment ENV;
	private static Block STONE;
	private static Block LOG;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		STONE = ENV.blocks.fromItem(ENV.items.getItemById("op.stone"));
		LOG = ENV.blocks.fromItem(ENV.items.getItemById("op.log"));
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
					return new BlockProxy(location.getBlockAddress(), cuboid);
				}, null)
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
					return new BlockProxy(location.getBlockAddress(), cuboid);
				}, null)
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
					return new BlockProxy(location.getBlockAddress(), cuboid);
				}, null)
				.finish()
		;
		Assert.assertTrue(FireHelpers.isNearFireSource(ENV, context, centre.getRelative(1, 0, 0)));
		Assert.assertFalse(FireHelpers.isNearFireSource(ENV, context, centre.getRelative(2, 0, 0)));
	}
}
