package com.jeffdisher.october.logic;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestGroundCoverHelpers
{
	private static Environment ENV;
	private static Block DIRT;
	private static Block GRASS;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		DIRT = ENV.blocks.fromItem(ENV.items.getItemById("op.dirt"));
		GRASS = ENV.blocks.fromItem(ENV.items.getItemById("op.grass"));
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void checkSpreadTargets()
	{
		// Check spread targets in a mostly empty cuboid.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		Function<AbsoluteLocation, BlockProxy> previousBlockLookUp = (AbsoluteLocation location) -> {
			return new BlockProxy(location.getBlockAddress(), cuboid);
		};
		
		AbsoluteLocation centre = cuboid.getCuboidAddress().getBase().getRelative(16, 16, 16);
		cuboid.setData15(AspectRegistry.BLOCK, centre.getBlockAddress(), GRASS.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, centre.getRelative(0, 1, -1).getBlockAddress(), DIRT.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, centre.getRelative(1, 0, -1).getBlockAddress(), DIRT.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, centre.getRelative(0, -1, 1).getBlockAddress(), DIRT.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, centre.getRelative(-1, 1, 1).getBlockAddress(), DIRT.item().number());
		
		List<AbsoluteLocation> neighbours = GroundCoverHelpers.findSpreadNeighbours(ENV, previousBlockLookUp, centre, GRASS);
		Set<AbsoluteLocation> set = new HashSet<>(neighbours);
		Assert.assertEquals(3, set.size());
	}

	@Test
	public void canChange()
	{
		// Check that we can change when all conditions are met.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		Function<AbsoluteLocation, BlockProxy> previousBlockLookUp = (AbsoluteLocation location) -> {
			return new BlockProxy(location.getBlockAddress(), cuboid);
		};
		
		AbsoluteLocation centre = cuboid.getCuboidAddress().getBase().getRelative(16, 16, 16);
		cuboid.setData15(AspectRegistry.BLOCK, centre.getBlockAddress(), DIRT.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, centre.getRelative(0, 1, -1).getBlockAddress(), GRASS.item().number());
		
		boolean canChange = GroundCoverHelpers.canChangeToGroundCover(ENV, previousBlockLookUp, centre, DIRT, GRASS);
		Assert.assertTrue(canChange);
	}

	@Test
	public void cannotChange()
	{
		// Check the cases which will cause the change helper to fail.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		Function<AbsoluteLocation, BlockProxy> previousBlockLookUp = (AbsoluteLocation location) -> {
			return new BlockProxy(location.getBlockAddress(), cuboid);
		};
		
		AbsoluteLocation centre = cuboid.getCuboidAddress().getBase().getRelative(16, 16, 16);
		cuboid.setData15(AspectRegistry.BLOCK, centre.getBlockAddress(), DIRT.item().number());
		
		// No source.
		boolean canChange = GroundCoverHelpers.canChangeToGroundCover(ENV, previousBlockLookUp, centre, DIRT, GRASS);
		Assert.assertFalse(canChange);
		
		// No longer correct type.
		cuboid.setData15(AspectRegistry.BLOCK, centre.getBlockAddress(), GRASS.item().number());
		canChange = GroundCoverHelpers.canChangeToGroundCover(ENV, previousBlockLookUp, centre, GRASS, GRASS);
		Assert.assertFalse(canChange);
		cuboid.setData15(AspectRegistry.BLOCK, centre.getBlockAddress(), DIRT.item().number());
		
		// Now covered with something else.
		cuboid.setData15(AspectRegistry.BLOCK, centre.getRelative(0, 0, 1).getBlockAddress(), DIRT.item().number());
		canChange = GroundCoverHelpers.canChangeToGroundCover(ENV, previousBlockLookUp, centre, DIRT, GRASS);
		Assert.assertFalse(canChange);
	}

	@Test
	public void findPotentialType()
	{
		// Checks that we can find a potential ground cover type only when we see a source.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		Function<AbsoluteLocation, BlockProxy> previousBlockLookUp = (AbsoluteLocation location) -> {
			return new BlockProxy(location.getBlockAddress(), cuboid);
		};
		
		AbsoluteLocation centre = cuboid.getCuboidAddress().getBase().getRelative(16, 16, 16);
		cuboid.setData15(AspectRegistry.BLOCK, centre.getBlockAddress(), DIRT.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, centre.getRelative(0, 1, -1).getBlockAddress(), DIRT.item().number());
		
		// No source.
		Block potential = GroundCoverHelpers.findPotentialGroundCoverType(ENV, previousBlockLookUp, centre, DIRT);
		Assert.assertNull(potential);
		
		// Valid source should spread.
		cuboid.setData15(AspectRegistry.BLOCK, centre.getRelative(0, 1, 1).getBlockAddress(), GRASS.item().number());
		potential = GroundCoverHelpers.findPotentialGroundCoverType(ENV, previousBlockLookUp, centre, DIRT);
		Assert.assertEquals(GRASS, potential);
		
		// Should not spread if covered.
		cuboid.setData15(AspectRegistry.BLOCK, centre.getRelative(0, 0, 1).getBlockAddress(), DIRT.item().number());
		potential = GroundCoverHelpers.findPotentialGroundCoverType(ENV, previousBlockLookUp, centre, DIRT);
		Assert.assertNull(potential);
	}

	@Test
	public void checkRevert()
	{
		// Checks the behaviour of the revert check helper.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		Function<AbsoluteLocation, BlockProxy> previousBlockLookUp = (AbsoluteLocation location) -> {
			return new BlockProxy(location.getBlockAddress(), cuboid);
		};
		
		AbsoluteLocation centre = cuboid.getCuboidAddress().getBase().getRelative(16, 16, 16);
		cuboid.setData15(AspectRegistry.BLOCK, centre.getBlockAddress(), GRASS.item().number());
		
		// Should not revert if not covered.
		Block potential = GroundCoverHelpers.checkRevertGroundCover(ENV, previousBlockLookUp, centre, GRASS);
		Assert.assertNull(potential);
		
		// Should revert if covered.
		cuboid.setData15(AspectRegistry.BLOCK, centre.getRelative(0, 0, 1).getBlockAddress(), DIRT.item().number());
		potential = GroundCoverHelpers.checkRevertGroundCover(ENV, previousBlockLookUp, centre, GRASS);
		Assert.assertEquals(DIRT, potential);
	}
}
