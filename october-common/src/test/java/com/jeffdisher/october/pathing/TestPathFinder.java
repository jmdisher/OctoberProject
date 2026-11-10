package com.jeffdisher.october.pathing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.ContextBuilder;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.CuboidGenerator;
import com.jeffdisher.october.utils.Encoding;


public class TestPathFinder
{
	private static Environment ENV;
	private static Block STONE;
	private static Block WATER;
	private static EntityVolume VOLUME;
	@BeforeClass
	public static void setup() throws Throwable
	{
		ENV = Environment.createSharedInstance();
		STONE = ENV.blocks.fromItem(ENV.items.getItemById("op.stone"));
		WATER = ENV.blocks.fromItem(ENV.items.getItemById("op.water_source"));
		VOLUME = new EntityVolume(0.1f, 0.1f);
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}


	@Test
	public void flatPlane()
	{
		// The block location is the "base" of the block, much like the entity z is the base of the block where it is standing.
		EntityLocation source = new EntityLocation(-10.5f, -6.5f, 5.0f);
		EntityLocation target = new EntityLocation(4.5f, 6.5f, 5.0f);
		int floor = 4;
		CuboidData cuboidNN = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(-1, -1, 0), ENV.special.AIR);
		CuboidData cuboidNZ = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(-1, 0, 0), ENV.special.AIR);
		CuboidData cuboidZN = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, -1, 0), ENV.special.AIR);
		CuboidData cuboidZZ = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidGenerator.fillPlane(cuboidNN, (byte)floor, STONE);
		CuboidGenerator.fillPlane(cuboidNZ, (byte)floor, STONE);
		CuboidGenerator.fillPlane(cuboidZN, (byte)floor, STONE);
		CuboidGenerator.fillPlane(cuboidZZ, (byte)floor, STONE);
		
		float limit = _getTestingDistance(source, target);
		WalkingNavigator navigator = new WalkingNavigator(_createKindLookup(List.of(cuboidNN, cuboidNZ, cuboidZN, cuboidZZ))
			, VOLUME
			, limit
			, true
			, true
			, true
			, true
		);
		List<PathLocation> path = PathFinder.findPathWithLimit(navigator, source, target);
		
		// We expect to see 57 steps, since the source counts as a step.
		int xSteps = 21 + 9;
		int ySteps = 13 + 13;
		Assert.assertEquals(1 + xSteps + ySteps, path.size());
	}

	@Test
	public void incline()
	{
		// The block location is the "base" of the block, much like the entity z is the base of the block where it is standing.
		EntityLocation source = new EntityLocation(9.5f, 13.5f, 15.0f);
		EntityLocation target = new EntityLocation(24.5f, 26.5f, 27.0f);
		
		short stoneNumber = STONE.item().number();
		CuboidData cuboidZZ = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		for (int x = 0; x < Encoding.CUBOID_EDGE_SIZE; ++x)
		{
			for (int y = 0; y < Encoding.CUBOID_EDGE_SIZE; ++y)
			{
				cuboidZZ.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(x, y, y), stoneNumber);
			}
		}
		
		float limit = _getTestingDistance(source, target);
		WalkingNavigator navigator = new WalkingNavigator(_createKindLookup(List.of(cuboidZZ))
			, VOLUME
			, limit
			, true
			, true
			, true
			, true
		);
		List<PathLocation> path = PathFinder.findPathWithLimit(navigator, source, target);
		
		// This is walking directly so the path should involve as many steps as difference in each axis (+1 for the start).
		int xSteps = 21 + 9;
		int ySteps = 13 + 13;
		int zSteps = 10 + 14;
		Assert.assertEquals(1 + xSteps + ySteps + zSteps, path.size());
	}

	@Test
	public void barrier()
	{
		// The block location is the "base" of the block, much like the entity z is the base of the block where it is standing.
		EntityLocation source = new EntityLocation(-10.5f, -6.5f, 5.0f);
		EntityLocation target = new EntityLocation(4.5f, 6.5f, 5.0f);
		int floor = 4;
		CuboidData cuboidNN = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(-1, -1, 0), ENV.special.AIR);
		CuboidGenerator.fillPlane(cuboidNN, (byte)floor, STONE);
		CuboidData cuboidNZ = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(-1, 0, 0), ENV.special.AIR);
		CuboidGenerator.fillPlane(cuboidNZ, (byte)floor, STONE);
		CuboidData cuboidZN = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, -1, 0), ENV.special.AIR);
		CuboidGenerator.fillPlane(cuboidZN, (byte)floor, STONE);
		CuboidData cuboidZZ = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidGenerator.fillPlane(cuboidZZ, (byte)floor, STONE);
		
		// We want to draw a barrier where y=0.
		short stoneNumber = STONE.item().number();
		for (int z = floor + 1; z <= (floor + 2); ++z)
		{
			for (int x = 0; x < Encoding.CUBOID_EDGE_SIZE; ++x)
			{
				BlockAddress address = BlockAddress.fromInt(x, 0, z);
				cuboidNZ.setData15(AspectRegistry.BLOCK, address, stoneNumber);
				cuboidZZ.setData15(AspectRegistry.BLOCK, address, stoneNumber);
			}
		}
		
		float limit = _getTestingDistance(source, target);
		WalkingNavigator navigator = new WalkingNavigator(_createKindLookup(List.of(cuboidNN, cuboidNZ, cuboidZN, cuboidZZ))
			, VOLUME
			, limit
			, true
			, true
			, true
			, true
		);
		List<PathLocation> path = PathFinder.findPathWithLimit(navigator, source, target);
		Assert.assertNull(path);
	}

	@Test
	public void maze()
	{
		// The block location is the "base" of the block, much like the entity z is the base of the block where it is standing.
		EntityLocation source = new EntityLocation(0.0f, 1.0f, 5.0f);
		EntityLocation target = new EntityLocation(4.0f, 6.0f, 5.0f);
		int floor = 4;
		CuboidData cuboid = _cuboidFrom2D(CuboidAddress.fromInt(0, 0, 0), floor, new String[] {
				"AAAAAAAAAA",
				"AAAAAAAAAA",
				"ASSSSSSSSS",
				"AAAAAAAAAA",
				"AAASSSSSAA",
				"AAASAAAAAA",
				"AAASASAAAA",
				"AAAASAAAAA",
				"AAAAAAAAAA",
		});
		float limit = _getTestingDistance(source, target);
		WalkingNavigator navigator = new WalkingNavigator(_createKindLookup(List.of(cuboid))
			, VOLUME
			, limit
			, true
			, true
			, true
			, true
		);
		List<PathLocation> path = PathFinder.findPathWithLimit(navigator, source, target);
		_printMap2D(9, 9, path);
	}

	@Test
	public void fallThroughHole()
	{
		// We want to fall through a small hole, go down a few layers, and then catch the ledge.
		EntityLocation source = new EntityLocation(1.5f, 2.5f, 4.0f);
		EntityLocation target = new EntityLocation(3.5f, 2.5f, 1.0f);
		CuboidData cuboid = _cuboidFrom3D(CuboidAddress.fromInt(0, 0, 0), new String[][] {
			new String[] {
					"SSSSS",
					"SSSSS",
					"SSSSS",
					"SSSSS",
					"SSSSS",
			}, new String[] {
					"SSSSS",
					"SSSSS",
					"SSAAS",
					"SSSSS",
					"SSSSS",
			}, new String[] {
					"SSSSS",
					"SSSSS",
					"SSAAS",
					"SSSSS",
					"SSSSS",
			}, new String[] {
					"SSSSS",
					"SSSSS",
					"SSASS",
					"SSSSS",
					"SSSSS",
			}, new String[] {
					"SSSSS",
					"SSSSS",
					"SAASS",
					"SSSSS",
					"SSSSS",
			}, new String[] {
					"SSSSS",
					"SSSSS",
					"SAASS",
					"SSSSS",
					"SSSSS",
			}, new String[] {
					"SSSSS",
					"SSSSS",
					"SAASS",
					"SSSSS",
					"SSSSS",
			}
		});
		float limit = _getTestingDistance(source, target);
		WalkingNavigator navigator = new WalkingNavigator(_createKindLookup(List.of(cuboid))
			, VOLUME
			, limit
			, true
			, true
			, true
			, true
		);
		List<PathLocation> path = PathFinder.findPathWithLimit(navigator, source, target);
		// This is a direct walk, with a fall in the middle, so it should just be the difference in locations +1 to start.
		int xSteps = 4;
		int ySteps = 0;
		int zSteps = 6;
		Assert.assertEquals(1 + xSteps + ySteps + zSteps, path.size());
	}

	@Test
	public void reachableMaze()
	{
		// The block location is the "base" of the block, much like the entity z is the base of the block where it is standing.
		EntityLocation source = new EntityLocation(5.5f, 5.5f, 5.0f);
		int floor = 4;
		CuboidData cuboid = _cuboidFrom2D(CuboidAddress.fromInt(0, 0, 0), floor, new String[] {
				"AAAAAAAAAA",
				"AAAAAAAAAA",
				"ASSSSSSSSS",
				"AAAAAAAAAA",
				"AAASSSSSAA",
				"AAASAAAASA",
				"AAASASSAAA",
				"AAAASAAAAA",
				"AAAAAAAAAA",
		});
		// We want to show what is reachable in the maze for different distances.
		WalkingNavigator navigator = new WalkingNavigator(_createKindLookup(List.of(cuboid))
			, VOLUME
			, 2.0f
			, true
			, true
			, true
			, true
		);
		Map<PathLocation, PathLocation> places = PathFinder.findPlacesWithinLimit(navigator
			, source
		);
		_printStepMap2D(10, 9, 5, places);
		navigator = new WalkingNavigator(_createKindLookup(List.of(cuboid))
			, VOLUME
			, 4.0f
			, true
			, true
			, true
			, true
		);
		places = PathFinder.findPlacesWithinLimit(navigator
			, source
		);
		_printStepMap2D(10, 9, 5, places);
	}

	@Test
	public void swimUpTube()
	{
		// We want to swim up a tube in some stone.
		EntityLocation source = new EntityLocation(3.5f, 2.5f, 1.0f);
		EntityLocation target = new EntityLocation(1.5f, 2.5f, 4.0f);
		CuboidData cuboid = _cuboidFrom3D(CuboidAddress.fromInt(0, 0, 0), new String[][] {
			new String[] {
					"SSSSS",
					"SSSSS",
					"SSSSS",
					"SSSSS",
					"SSSSS",
			}, new String[] {
					"SSSSS",
					"SSSSS",
					"SSWWS",
					"SSSSS",
					"SSSSS",
			}, new String[] {
					"SSSSS",
					"SSSSS",
					"SSWWS",
					"SSSSS",
					"SSSSS",
			}, new String[] {
					"SSSSS",
					"SSSSS",
					"SSWSS",
					"SSSSS",
					"SSSSS",
			}, new String[] {
					"SSSSS",
					"SSSSS",
					"SWWSS",
					"SSSSS",
					"SSSSS",
			}, new String[] {
					"SSSSS",
					"SSSSS",
					"SAASS",
					"SSSSS",
					"SSSSS",
			}, new String[] {
					"SSSSS",
					"SSSSS",
					"SAASS",
					"SSSSS",
					"SSSSS",
			}
		});
		float limit = _getTestingDistance(source, target);
		WalkingNavigator navigator = new WalkingNavigator(_createKindLookup(List.of(cuboid))
			, VOLUME
			, limit
			, true
			, true
			, true
			, true
		);
		List<PathLocation> path = PathFinder.findPathWithLimit(navigator, source, target);
		int xSteps = 4;
		int ySteps = 0;
		int zSteps = 6;
		Assert.assertEquals(1 + xSteps + ySteps + zSteps, path.size());
	}

	@Test
	public void swimOverGap()
	{
		// Show that we can swim over an air block hole.
		EntityLocation source = new EntityLocation(1.5f, 2.5f, 2.0f);
		EntityLocation target = new EntityLocation(3.5f, 2.5f, 2.0f);
		CuboidData cuboid = _cuboidFrom3D(CuboidAddress.fromInt(0, 0, 0), new String[][] {
			new String[] {
					"SSSSS",
					"SSSSS",
					"SSSSS",
					"SSSSS",
					"SSSSS",
			}, new String[] {
					"SSSSS",
					"SSSSS",
					"SSASS",
					"SSSSS",
					"SSSSS",
			}, new String[] {
					"SSSSS",
					"SSSSS",
					"SWWWS",
					"SSSSS",
					"SSSSS",
			}, new String[] {
					"SSSSS",
					"SSSSS",
					"SAAAS",
					"SSSSS",
					"SSSSS",
			}, new String[] {
					"SSSSS",
					"SSSSS",
					"SAAAS",
					"SSSSS",
					"SSSSS",
			}
		});
		float limit = _getTestingDistance(source, target);
		WalkingNavigator navigator = new WalkingNavigator(_createKindLookup(List.of(cuboid))
			, VOLUME
			, limit
			, true
			, true
			, true
			, true
		);
		List<PathLocation> path = PathFinder.findPathWithLimit(navigator, source, target);
		int xSteps = 4;
		int ySteps = 0;
		int zSteps = 0;
		Assert.assertEquals(1 + xSteps + ySteps + zSteps, path.size());
	}

	@Test
	public void startStuckInBlock()
	{
		// Show that we can't do any path creation if we are stuck in a block.
		EntityLocation source = new EntityLocation(-10.5f, -6.5f, 5.0f);
		EntityLocation target = new EntityLocation(4.5f, 6.5f, 5.0f);
		AbsoluteLocation startLocation = source.getBlockLocation();
		int floor = 4;
		CuboidData cuboidNN = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(-1, -1, 0), ENV.special.AIR);
		CuboidGenerator.fillPlane(cuboidNN, (byte)floor, STONE);
		cuboidNN.setData15(AspectRegistry.BLOCK, startLocation.getBlockAddress(), STONE.item().number());
		CuboidData cuboidNZ = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(-1, 0, 0), ENV.special.AIR);
		CuboidGenerator.fillPlane(cuboidNZ, (byte)floor, STONE);
		CuboidData cuboidZN = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, -1, 0), ENV.special.AIR);
		CuboidGenerator.fillPlane(cuboidZN, (byte)floor, STONE);
		CuboidData cuboidZZ = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidGenerator.fillPlane(cuboidZZ, (byte)floor, STONE);
		
		float limit = _getTestingDistance(source, target);
		WalkingNavigator navigator = new WalkingNavigator(_createKindLookup(List.of(cuboidNN, cuboidNZ, cuboidZN, cuboidZZ))
			, VOLUME
			, limit
			, true
			, true
			, true
			, true
		);
		List<PathLocation> path = PathFinder.findPathWithLimit(navigator, source, target);
		Assert.assertNull(path);
		
		navigator = new WalkingNavigator(_createKindLookup(List.of(cuboidNN, cuboidNZ, cuboidZN, cuboidZZ))
			, VOLUME
			, 50.0f
			, true
			, true
			, true
			, true
		);
		Map<PathLocation, PathLocation> possiblePaths = PathFinder.findPlacesWithinLimit(navigator
			, source
		);
		Assert.assertEquals(0, possiblePaths.size());
	}

	@Test
	public void findPossibleWithRestrictions()
	{
		// Show that we can constrain possible places with control flags.
		EntityLocation source = new EntityLocation(-10.5f, -6.5f, 5.0f);
		int floor = 4;
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(-1, -1, 0), ENV.special.AIR);
		CuboidGenerator.fillPlane(cuboid, (byte)floor, STONE);
		
		// Get normal count, unrestricted.
		float limitSteps = 4.0f;
		WalkingNavigator navigator = new WalkingNavigator(_createKindLookup(List.of(cuboid))
			, VOLUME
			, limitSteps
			, true
			, true
			, true
			, true
		);
		Map<PathLocation, PathLocation> allPaths = PathFinder.findPlacesWithinLimit(navigator
			, source
		);
		Assert.assertEquals(291, allPaths.size());
		
		// Only allow north-west.
		navigator = new WalkingNavigator(_createKindLookup(List.of(cuboid))
			, VOLUME
			, limitSteps
			, true
			, false
			, false
			, true
		);
		Map<PathLocation, PathLocation> restrictedPaths = PathFinder.findPlacesWithinLimit(navigator
			, source
		);
		Assert.assertEquals(94, restrictedPaths.size());
		
		EntityLocation start = PathLocation.forEntityBase(source).getBaseLocation();
		for (PathLocation key : restrictedPaths.keySet())
		{
			EntityLocation base = key.getBaseLocation();
			Assert.assertTrue(base.x() <= start.x());
			Assert.assertTrue(base.y() >= start.y());
		}
	}

	@Test
	public void gapHandlingCommonKindLookup()
	{
		// We want to show how the CommonKindLookup handles gaps.
		EntityLocation sourceBase = new EntityLocation(10.0f, 6.0f, 5.0f);
		EntityLocation sourceCentre = new EntityLocation(10.5f, 6.5f, 5.0f);
		EntityLocation targetBase = new EntityLocation(16.0f, 6.0f, 5.0f);
		EntityLocation targetCentre = new EntityLocation(16.5f, 6.5f, 5.0f);
		EntityVolume volume = new EntityVolume(1.2f, 0.9f);
		
		// Generate a cuboid for this test.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0,0, 0), ENV.special.AIR);
		CuboidGenerator.fillPlane(cuboid, (byte)4, STONE);
		for (byte y = 0; y < Encoding.CUBOID_EDGE_SIZE; ++y)
		{
			if (6 != y)
			{
				cuboid.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)13, y, (byte)5), STONE.item().number());
				cuboid.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)13, y, (byte)6), STONE.item().number());
			}
		}
		
		float limit = 8.0f;
		TickProcessingContext.IBlockFetcher blockFetcher = ContextBuilder.buildSingleFetcher(cuboid);
		
		WalkingNavigator baseNavigator = new WalkingNavigator(blockFetcher
			, volume
			, limit
			, true
			, true
			, true
			, true
		);
		List<PathLocation> basePath = PathFinder.findPathWithLimit(baseNavigator, sourceBase, targetBase);
		Assert.assertEquals(13, basePath.size());
		
		WalkingNavigator centreNavigator = new WalkingNavigator(blockFetcher
			, volume
			, limit
			, true
			, true
			, true
			, true
		);
		// Note that the centre path takes an extra few steps so that it can shift over and clear the gap.
		List<PathLocation> centrePath = PathFinder.findPathWithLimit(centreNavigator, sourceCentre, targetCentre);
		Assert.assertEquals(15, centrePath.size());
	}

	@Test
	public void noJumpingInViscosity()
	{
		// Verify that we don't try to jump up a block when in non-zero viscosity (as we are unlikely to make the jump).
		EntityLocation source = new EntityLocation(10.0f, 6.0f, 5.0f);
		EntityLocation target = new EntityLocation(11.0f, 6.0f, 6.0f);
		EntityVolume volume = new EntityVolume(1.2f, 0.9f);
		
		// Generate a cuboid for this test.
		Block wheat = ENV.blocks.fromItem(ENV.items.getItemById("op.wheat_mature"));
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0,0, 0), ENV.special.AIR);
		CuboidGenerator.fillPlane(cuboid, (byte)4, STONE);
		cuboid.setData15(AspectRegistry.BLOCK, target.getBlockLocation().getRelative(0, 0, -1).getBlockAddress(), STONE.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, source.getBlockLocation().getBlockAddress(), wheat.item().number());
		
		float limit = 8.0f;
		TickProcessingContext.IBlockFetcher blockFetcher = ContextBuilder.buildSingleFetcher(cuboid);
		
		// We should see 9 steps:  That is enough to walk around to another side, whereas 5 would be a direct jump.
		WalkingNavigator navigator = new WalkingNavigator(blockFetcher
			, volume
			, limit
			, true
			, true
			, true
			, true
		);
		List<PathLocation> basePath = PathFinder.findPathWithLimit(navigator, source, target);
		Assert.assertEquals(9, basePath.size());
	}

	@Test
	public void walkUpSlab()
	{
		// Show that we can walk up slabs.
		EntityLocation source = new EntityLocation(1.2f, 2.2f, 2.0f);
		EntityLocation target = new EntityLocation(3.2f, 2.2f, 3.0f);
		EntityVolume volume = new EntityVolume(1.2f, 0.7f);
		AbsoluteLocation start = source.getBlockLocation();
		AbsoluteLocation end = target.getBlockLocation();
		
		// Generate a cuboid for this test.
		Block slab = ENV.blocks.fromItem(ENV.items.getItemById("op.stone_brick_slab"));
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0,0, 0), ENV.special.AIR);
		CuboidGenerator.fillPlane(cuboid, (byte)1, STONE);
		cuboid.setData15(AspectRegistry.BLOCK, end.getRelative(0, 0, -1).getBlockAddress(), STONE.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, start.getRelative(1, 0, 0).getBlockAddress(), slab.item().number());
		cuboid.setData7(AspectRegistry.ORIENTATION, start.getRelative(1, 0, 0).getBlockAddress(), FacingDirection.directionToByte(FacingDirection.DOWN));
		
		float limit = 5.0f;
		TickProcessingContext.IBlockFetcher blockFetcher = ContextBuilder.buildSingleFetcher(cuboid);
		
		// We should see 4 steps.
		WalkingNavigator navigator = new WalkingNavigator(blockFetcher
			, volume
			, limit
			, true
			, true
			, true
			, true
		);
		List<PathLocation> basePath = PathFinder.findPathWithLimit(navigator, source, target);
		PathLocation sourceLocation = PathLocation.forEntityBase(source);
		Assert.assertEquals(7, basePath.size());
		Assert.assertEquals(sourceLocation, basePath.get(0));
		Assert.assertEquals(sourceLocation.getRelative(0, 0, 1), basePath.get(1));
		Assert.assertEquals(sourceLocation.getRelative(1, 0, 1), basePath.get(2));
		Assert.assertEquals(sourceLocation.getRelative(2, 0, 1), basePath.get(3));
		Assert.assertEquals(sourceLocation.getRelative(2, 0, 2), basePath.get(4));
		Assert.assertEquals(sourceLocation.getRelative(3, 0, 2), basePath.get(5));
		Assert.assertEquals(sourceLocation.getRelative(4, 0, 2), basePath.get(6));
		Assert.assertEquals(PathLocation.forEntityBase(target), basePath.get(6));
	}

	@Test
	public void walkDownSlab()
	{
		// Show that we can walk down slabs.
		EntityLocation source = new EntityLocation(3.2f, 2.2f, 3.0f);
		EntityLocation target = new EntityLocation(1.2f, 2.2f, 2.0f);
		EntityVolume volume = new EntityVolume(1.2f, 0.7f);
		AbsoluteLocation start = source.getBlockLocation();
		AbsoluteLocation end = target.getBlockLocation();
		
		// Generate a cuboid for this test.
		Block slab = ENV.blocks.fromItem(ENV.items.getItemById("op.stone_brick_slab"));
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0,0, 0), ENV.special.AIR);
		CuboidGenerator.fillPlane(cuboid, (byte)1, STONE);
		cuboid.setData15(AspectRegistry.BLOCK, start.getRelative(0, 0, -1).getBlockAddress(), STONE.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, end.getRelative(1, 0, 0).getBlockAddress(), slab.item().number());
		cuboid.setData7(AspectRegistry.ORIENTATION, end.getRelative(1, 0, 0).getBlockAddress(), FacingDirection.directionToByte(FacingDirection.DOWN));
		
		float limit = 5.0f;
		TickProcessingContext.IBlockFetcher blockFetcher = ContextBuilder.buildSingleFetcher(cuboid);
		
		// We should see 4 steps.
		WalkingNavigator navigator = new WalkingNavigator(blockFetcher
			, volume
			, limit
			, true
			, true
			, true
			, true
		);
		List<PathLocation> basePath = PathFinder.findPathWithLimit(navigator, source, target);
		PathLocation sourceLocation = PathLocation.forEntityBase(source);
		Assert.assertEquals(7, basePath.size());
		Assert.assertEquals(sourceLocation, basePath.get(0));
		Assert.assertEquals(sourceLocation.getRelative(-1, 0, 0), basePath.get(1));
		Assert.assertEquals(sourceLocation.getRelative(-2, 0, 0), basePath.get(2));
		Assert.assertEquals(sourceLocation.getRelative(-2, 0, -1), basePath.get(3));
		Assert.assertEquals(sourceLocation.getRelative(-3, 0, -1), basePath.get(4));
		Assert.assertEquals(sourceLocation.getRelative(-4, 0, -1), basePath.get(5));
		Assert.assertEquals(sourceLocation.getRelative(-4, 0, -2), basePath.get(6));
		Assert.assertEquals(PathLocation.forEntityBase(target), basePath.get(6));
	}

	@Test
	public void walkUpStairs()
	{
		// Show that we can walk up stairs.
		EntityLocation source = new EntityLocation(1.2f, 2.2f, 2.0f);
		EntityLocation target = new EntityLocation(3.2f, 2.2f, 3.0f);
		EntityVolume volume = new EntityVolume(1.2f, 0.7f);
		AbsoluteLocation start = source.getBlockLocation();
		AbsoluteLocation end = target.getBlockLocation();
		
		// Generate a cuboid for this test.
		Block stair = ENV.blocks.fromItem(ENV.items.getItemById("op.stone_brick_stair"));
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0,0, 0), ENV.special.AIR);
		CuboidGenerator.fillPlane(cuboid, (byte)1, STONE);
		cuboid.setData15(AspectRegistry.BLOCK, end.getRelative(0, 0, -1).getBlockAddress(), STONE.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, start.getRelative(1, 0, 0).getBlockAddress(), stair.item().number());
		cuboid.setData7(AspectRegistry.ORIENTATION, start.getRelative(1, 0, 0).getBlockAddress(), FacingDirection.directionToByte(FacingDirection.EAST));
		
		float limit = 5.0f;
		TickProcessingContext.IBlockFetcher blockFetcher = ContextBuilder.buildSingleFetcher(cuboid);
		
		// We should see 4 steps.
		WalkingNavigator navigator = new WalkingNavigator(blockFetcher
			, volume
			, limit
			, true
			, true
			, true
			, true
		);
		List<PathLocation> basePath = PathFinder.findPathWithLimit(navigator, source, target);
		PathLocation sourceLocation = PathLocation.forEntityBase(source);
		Assert.assertEquals(7, basePath.size());
		Assert.assertEquals(sourceLocation, basePath.get(0));
		Assert.assertEquals(sourceLocation.getRelative(0, 0, 1), basePath.get(1));
		Assert.assertEquals(sourceLocation.getRelative(1, 0, 1), basePath.get(2));
		Assert.assertEquals(sourceLocation.getRelative(1, 0, 2), basePath.get(3));
		Assert.assertEquals(sourceLocation.getRelative(2, 0, 2), basePath.get(4));
		Assert.assertEquals(sourceLocation.getRelative(3, 0, 2), basePath.get(5));
		Assert.assertEquals(sourceLocation.getRelative(4, 0, 2), basePath.get(6));
		Assert.assertEquals(PathLocation.forEntityBase(target), basePath.get(6));
	}

	@Test
	public void walkDownStairs()
	{
		// Show that we can walk down stairs.
		EntityLocation source = new EntityLocation(3.2f, 2.2f, 3.0f);
		EntityLocation target = new EntityLocation(1.2f, 2.2f, 2.0f);
		EntityVolume volume = new EntityVolume(1.2f, 0.7f);
		AbsoluteLocation start = source.getBlockLocation();
		AbsoluteLocation end = target.getBlockLocation();
		
		// Generate a cuboid for this test.
		Block stair = ENV.blocks.fromItem(ENV.items.getItemById("op.stone_brick_stair"));
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0,0, 0), ENV.special.AIR);
		CuboidGenerator.fillPlane(cuboid, (byte)1, STONE);
		cuboid.setData15(AspectRegistry.BLOCK, start.getRelative(0, 0, -1).getBlockAddress(), STONE.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, end.getRelative(1, 0, 0).getBlockAddress(), stair.item().number());
		cuboid.setData7(AspectRegistry.ORIENTATION, end.getRelative(1, 0, 0).getBlockAddress(), FacingDirection.directionToByte(FacingDirection.EAST));
		
		float limit = 5.0f;
		TickProcessingContext.IBlockFetcher blockFetcher = ContextBuilder.buildSingleFetcher(cuboid);
		
		// We should see 4 steps.
		WalkingNavigator navigator = new WalkingNavigator(blockFetcher
			, volume
			, limit
			, true
			, true
			, true
			, true
		);
		List<PathLocation> basePath = PathFinder.findPathWithLimit(navigator, source, target);
		PathLocation sourceLocation = PathLocation.forEntityBase(source);
		Assert.assertEquals(7, basePath.size());
		Assert.assertEquals(sourceLocation, basePath.get(0));
		Assert.assertEquals(sourceLocation.getRelative(-1, 0, 0), basePath.get(1));
		Assert.assertEquals(sourceLocation.getRelative(-2, 0, 0), basePath.get(2));
		Assert.assertEquals(sourceLocation.getRelative(-3, 0, 0), basePath.get(3));
		Assert.assertEquals(sourceLocation.getRelative(-3, 0, -1), basePath.get(4));
		Assert.assertEquals(sourceLocation.getRelative(-4, 0, -1), basePath.get(5));
		Assert.assertEquals(sourceLocation.getRelative(-4, 0, -2), basePath.get(6));
		Assert.assertEquals(PathLocation.forEntityBase(target), basePath.get(6));
	}


	private static void _printMap2D(int gx, int gy, Collection<PathLocation> path)
	{
		int x = gx * PathLocation.PATH_PER_BLOCK;
		int y = gy * PathLocation.PATH_PER_BLOCK;
		char[][] map = new char[y][x];
		for (int i = 0; i < y; ++i)
		{
			for (int j = 0; j < x; ++j)
			{
				map[i][j] = ' ';
			}
		}
		for (PathLocation step : path)
		{
			int thisX = Math.round(step.getBaseLocation().x() / PathLocation.PATH_UNIT);
			int thisY = Math.round(step.getBaseLocation().y() / PathLocation.PATH_UNIT);
			map[thisY][thisX] = 'X';
		}
		for (int i = 0; i < y; ++i)
		{
			for (int j = 0; j < x; ++j)
			{
				System.out.print(map[i][j]);
			}
			System.out.println();
		}
	}

	private static void _printStepMap2D(int gx, int gy, int fixedZ, Map<PathLocation, PathLocation> backStepPath)
	{
		int x = gx * PathLocation.PATH_PER_BLOCK;
		int y = gy * PathLocation.PATH_PER_BLOCK;
		char[][] map = new char[y][x];
		for (int i = 0; i < y; ++i)
		{
			for (int j = 0; j < x; ++j)
			{
				map[i][j] = ' ';
			}
		}
		for (PathLocation step : backStepPath.keySet())
		{
			int thisX = Math.round(step.getBaseLocation().x() / PathLocation.PATH_UNIT);
			int thisY = Math.round(step.getBaseLocation().y() / PathLocation.PATH_UNIT);
			int thisZ = Math.round(step.getBaseLocation().z() / PathLocation.PATH_UNIT);
			if (fixedZ == thisZ)
			{
				// Count how many steps this walks.
				int count = 0;
				PathLocation back = backStepPath.get(step);
				while (null != back)
				{
					count += 1;
					back = backStepPath.get(back);
				}
				map[thisY][thisX] = Integer.toString(count).charAt(0);
			}
		}
		for (int i = 0; i < y; ++i)
		{
			for (int j = 0; j < x; ++j)
			{
				System.out.print(map[i][j]);
			}
			System.out.println();
		}
	}

	private static float _getTestingDistance(EntityLocation source, EntityLocation target)
	{
		// We will limit algorithm by a limit of 2x the manhattan distance, so we don't walk too far around obstacles.
		float manhattan = Math.abs(source.x() - target.x())
				+ Math.abs(source.y() - target.y())
				+ Math.abs(source.z() - target.z())
		;
		return 2 * manhattan;
	}

	private static TickProcessingContext.IBlockFetcher _createKindLookup(Collection<IReadOnlyCuboidData> cuboids)
	{
		Map<CuboidAddress, IReadOnlyCuboidData> map = cuboids.stream()
			.collect(Collectors.toMap((IReadOnlyCuboidData cuboid) -> cuboid.getCuboidAddress(), (IReadOnlyCuboidData cuboid) -> cuboid))
		;
		return ContextBuilder.buildFunctionFetcher((CuboidAddress address) -> map.get(address));
	}

	private static CuboidData _cuboidFrom2D(CuboidAddress address, int floorZ, String[] map)
	{
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		short walkableNumber = ENV.special.AIR.item().number();
		short swimmableNumber = WATER.item().number();
		short solidNumber = STONE.item().number();
		
		List<_Update> updates = new ArrayList<>();
		for (byte z = 0; z < Encoding.CUBOID_EDGE_SIZE; ++z)
		{
			for (byte y = 0; y < Encoding.CUBOID_EDGE_SIZE; ++y)
			{
				for (byte x = 0; x < Encoding.CUBOID_EDGE_SIZE; ++x)
				{
					short block = walkableNumber;
					if (floorZ == z)
					{
						block = solidNumber;
					}
					else
					{
						if ((y < map.length) && (x < map[y].length()))
						{
							char c = map[y].charAt(x);
							if ('S' == c)
							{
								block = solidNumber;
							}
							else if ('W' == c)
							{
								block = swimmableNumber;
							}
						}
						else
						{
							block = solidNumber;
						}
					}
					
					if (walkableNumber != block)
					{
						updates.add(new _Update(new BlockAddress(x, y, z), block));
					}
				}
			}
		}
		_batchWrite(cuboid, updates);
		
		return cuboid;
	}

	private static CuboidData _cuboidFrom3D(CuboidAddress address, String[][] map)
	{
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		short walkableNumber = ENV.special.AIR.item().number();
		short swimmableNumber = WATER.item().number();
		short solidNumber = STONE.item().number();
		
		List<_Update> updates = new ArrayList<>();
		for (byte z = 0; z < Encoding.CUBOID_EDGE_SIZE; ++z)
		{
			for (byte y = 0; y < Encoding.CUBOID_EDGE_SIZE; ++y)
			{
				for (byte x = 0; x < Encoding.CUBOID_EDGE_SIZE; ++x)
				{
					short block = walkableNumber;
					
					if ((z < map.length) && (y < map[z].length) && (x < map[z][y].length()))
					{
						char c = map[z][y].charAt(x);
						if ('S' == c)
						{
							block = solidNumber;
						}
						else if ('W' == c)
						{
							block = swimmableNumber;
						}
						
					}
					else
					{
						block = solidNumber;
					}
					
					if (walkableNumber != block)
					{
						updates.add(new _Update(new BlockAddress(x, y, z), block));
					}
				}
			}
		}
		_batchWrite(cuboid, updates);
		
		return cuboid;
	}

	private static void _batchWrite(CuboidData cuboid, List<_Update> updates)
	{
		_UpdateComparator writeComparator = new _UpdateComparator();
		updates.sort(writeComparator);
		BlockAddress[] addresses = new BlockAddress[updates.size()];
		short[] values = new short[updates.size()];
		for (int i = 0; i < values.length; ++i)
		{
			_Update update = updates.get(i);
			addresses[i] = update.address;
			values[i] = update.blockValue;
		}
		cuboid.batchWiteData15(AspectRegistry.BLOCK, addresses, values);
	}


	private static record _Update(BlockAddress address, short blockValue) {}

	private static class _UpdateComparator implements Comparator<_Update>
	{
		@Override
		public int compare(_Update o1, _Update o2)
		{
			int sort1 = IReadOnlyCuboidData.getBatchSortOrder(o1.address);
			int sort2 = IReadOnlyCuboidData.getBatchSortOrder(o2.address);
			return sort1 - sort2;
		}
	}
}
