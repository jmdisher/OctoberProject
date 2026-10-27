package com.jeffdisher.october.pathing;

import java.util.Collection;
import java.util.List;
import java.util.Map;
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
		Function<AbsoluteLocation, _BlockKind> blockPermitsUser = (AbsoluteLocation location) -> (floor == location.z()) ? _BlockKind.SOLID : _BlockKind.WALKABLE;
		float limit = _getTestingDistance(source, target);
		WalkingNavigator navigator = new WalkingNavigator(_createKindLookup(blockPermitsUser)
			, VOLUME
			, limit
			, true
			, true
			, true
			, true
		);
		List<AbsoluteLocation> path = PathFinder.findPathWithLimit(navigator, source, target);
		
		// We expect to see 29 steps, since the source counts as a step.
		int xSteps = 4 + 11;
		int ySteps = 6 + 7;
		Assert.assertEquals(1 + xSteps + ySteps, path.size());
	}

	@Test
	public void incline()
	{
		// The block location is the "base" of the block, much like the entity z is the base of the block where it is standing.
		EntityLocation source = new EntityLocation(-10.5f, -6.5f, -5.0f);
		EntityLocation target = new EntityLocation(4.5f, 6.5f, 7.0f);
		Function<AbsoluteLocation, _BlockKind> blockPermitsUser = (AbsoluteLocation location) -> (location.y() == location.z()) ? _BlockKind.SOLID : _BlockKind.WALKABLE;
		float limit = _getTestingDistance(source, target);
		WalkingNavigator navigator = new WalkingNavigator(_createKindLookup(blockPermitsUser)
			, VOLUME
			, limit
			, true
			, true
			, true
			, true
		);
		List<AbsoluteLocation> path = PathFinder.findPathWithLimit(navigator, source, target);
		
		// This is walking directly so the path should involve as many steps as difference in each axis (+1 for the start).
		int xSteps = 4 + 11;
		int ySteps = 6 + 7;
		int zSteps = 5 + 7;
		Assert.assertEquals(1 + xSteps + ySteps + zSteps, path.size());
	}

	@Test
	public void barrier()
	{
		// The block location is the "base" of the block, much like the entity z is the base of the block where it is standing.
		EntityLocation source = new EntityLocation(-10.5f, -6.5f, 5.0f);
		EntityLocation target = new EntityLocation(4.5f, 6.5f, 5.0f);
		int floor = 4;
		Function<AbsoluteLocation, _BlockKind> blockPermitsUser = (AbsoluteLocation location) -> {
			return ((floor == location.z()) || (0 == location.y()))
					? _BlockKind.SOLID
					: _BlockKind.WALKABLE
			;
		};
		float limit = _getTestingDistance(source, target);
		WalkingNavigator navigator = new WalkingNavigator(_createKindLookup(blockPermitsUser)
			, VOLUME
			, limit
			, true
			, true
			, true
			, true
		);
		List<AbsoluteLocation> path = PathFinder.findPathWithLimit(navigator, source, target);
		Assert.assertNull(path);
	}

	@Test
	public void gap()
	{
		// The block location is the "base" of the block, much like the entity z is the base of the block where it is standing.
		EntityLocation source = new EntityLocation(-10.5f, -6.5f, 5.0f);
		EntityLocation target = new EntityLocation(4.5f, 6.5f, 5.0f);
		int floor = 4;
		Function<AbsoluteLocation, _BlockKind> blockPermitsUser = (AbsoluteLocation location) -> {
			return ((floor == location.z()) || (0 == location.y()))
					? _BlockKind.SOLID
					: _BlockKind.WALKABLE
			;
		};
		float limit = _getTestingDistance(source, target);
		WalkingNavigator navigator = new WalkingNavigator(_createKindLookup(blockPermitsUser)
			, VOLUME
			, limit
			, true
			, true
			, true
			, true
		);
		List<AbsoluteLocation> path = PathFinder.findPathWithLimit(navigator, source, target);
		Assert.assertNull(path);
	}

	@Test
	public void maze()
	{
		// The block location is the "base" of the block, much like the entity z is the base of the block where it is standing.
		EntityLocation source = new EntityLocation(0.0f, 1.0f, 5.0f);
		EntityLocation target = new EntityLocation(4.0f, 6.0f, 5.0f);
		int floor = 4;
		Function<AbsoluteLocation, _BlockKind> blockPermitsUser = new MapResolver(floor, new String[] {
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
		WalkingNavigator navigator = new WalkingNavigator(_createKindLookup(blockPermitsUser)
			, VOLUME
			, limit
			, true
			, true
			, true
			, true
		);
		List<AbsoluteLocation> path = PathFinder.findPathWithLimit(navigator, source, target);
		_printMap2D(9, 9, path);
	}

	@Test
	public void fallThroughHole()
	{
		// We want to fall through a small hole, go down a few layers, and then catch the ledge.
		EntityLocation source = new EntityLocation(1.5f, 2.5f, 4.0f);
		EntityLocation target = new EntityLocation(3.5f, 2.5f, 1.0f);
		Function<AbsoluteLocation, _BlockKind> blockPermitsUser = new MapResolver3D(new String[][] {
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
		WalkingNavigator navigator = new WalkingNavigator(_createKindLookup(blockPermitsUser)
			, VOLUME
			, limit
			, true
			, true
			, true
			, true
		);
		List<AbsoluteLocation> path = PathFinder.findPathWithLimit(navigator, source, target);
		// This is a direct walk, with a fall in the middle, so it should just be the difference in locations +1 to start.
		int xSteps = 2;
		int ySteps = 0;
		int zSteps = 3;
		Assert.assertEquals(1 + xSteps + ySteps + zSteps, path.size());
	}

	@Test
	public void reachableMaze()
	{
		// The block location is the "base" of the block, much like the entity z is the base of the block where it is standing.
		EntityLocation source = new EntityLocation(5.5f, 5.5f, 5.0f);
		int floor = 4;
		Function<AbsoluteLocation, _BlockKind> blockPermitsUser = new MapResolver(floor, new String[] {
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
		WalkingNavigator navigator = new WalkingNavigator(_createKindLookup(blockPermitsUser)
			, VOLUME
			, 2.0f
			, true
			, true
			, true
			, true
		);
		Map<AbsoluteLocation, AbsoluteLocation> places = PathFinder.findPlacesWithinLimit(navigator
			, source
		);
		_printStepMap2D(10, 9, 5, places);
		navigator = new WalkingNavigator(_createKindLookup(blockPermitsUser)
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
		Function<AbsoluteLocation, _BlockKind> blockPermitsUser = new MapResolver3D(new String[][] {
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
		WalkingNavigator navigator = new WalkingNavigator(_createKindLookup(blockPermitsUser)
			, VOLUME
			, limit
			, true
			, true
			, true
			, true
		);
		List<AbsoluteLocation> path = PathFinder.findPathWithLimit(navigator, source, target);
		int xSteps = 2;
		int ySteps = 0;
		int zSteps = 3;
		Assert.assertEquals(1 + xSteps + ySteps + zSteps, path.size());
	}

	@Test
	public void swimOverGap()
	{
		// Show that we can swim over an air block hole.
		EntityLocation source = new EntityLocation(1.5f, 2.5f, 2.0f);
		EntityLocation target = new EntityLocation(3.5f, 2.5f, 2.0f);
		Function<AbsoluteLocation, _BlockKind> blockPermitsUser = new MapResolver3D(new String[][] {
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
		WalkingNavigator navigator = new WalkingNavigator(_createKindLookup(blockPermitsUser)
			, VOLUME
			, limit
			, true
			, true
			, true
			, true
		);
		List<AbsoluteLocation> path = PathFinder.findPathWithLimit(navigator, source, target);
		int xSteps = 2;
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
		Function<AbsoluteLocation, _BlockKind> blockPermitsUser = (AbsoluteLocation location) -> ((floor == location.z()) || location.equals(startLocation))
			? _BlockKind.SOLID
			: _BlockKind.WALKABLE
		;
		
		float limit = _getTestingDistance(source, target);
		WalkingNavigator navigator = new WalkingNavigator(_createKindLookup(blockPermitsUser)
			, VOLUME
			, limit
			, true
			, true
			, true
			, true
		);
		List<AbsoluteLocation> path = PathFinder.findPathWithLimit(navigator, source, target);
		Assert.assertNull(path);
		
		navigator = new WalkingNavigator(_createKindLookup(blockPermitsUser)
			, VOLUME
			, 50.0f
			, true
			, true
			, true
			, true
		);
		Map<AbsoluteLocation, AbsoluteLocation> possiblePaths = PathFinder.findPlacesWithinLimit(navigator
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
		Function<AbsoluteLocation, _BlockKind> blockPermitsUser = (AbsoluteLocation location) -> (floor == location.z())
			? _BlockKind.SOLID
			: _BlockKind.WALKABLE
		;
		
		// Get normal count, unrestricted.
		float limitSteps = 4.0f;
		WalkingNavigator navigator = new WalkingNavigator(_createKindLookup(blockPermitsUser)
			, VOLUME
			, limitSteps
			, true
			, true
			, true
			, true
		);
		Map<AbsoluteLocation, AbsoluteLocation> allPaths = PathFinder.findPlacesWithinLimit(navigator
			, source
		);
		Assert.assertEquals(54, allPaths.size());
		
		// Only allow north-west.
		navigator = new WalkingNavigator(_createKindLookup(blockPermitsUser)
			, VOLUME
			, limitSteps
			, true
			, false
			, false
			, true
		);
		Map<AbsoluteLocation, AbsoluteLocation> restrictedPaths = PathFinder.findPlacesWithinLimit(navigator
			, source
		);
		Assert.assertEquals(21, restrictedPaths.size());
		
		AbsoluteLocation start = source.getBlockLocation();
		for (AbsoluteLocation key : restrictedPaths.keySet())
		{
			Assert.assertTrue(key.x() <= start.x());
			Assert.assertTrue(key.y() >= start.y());
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
		TickProcessingContext.IBlockFetcher blockFetcher = ContextBuilder.buildFetcher((AbsoluteLocation location) -> {
			return (location.getCuboidAddress().equals(cuboid.getCuboidAddress()))
				? BlockProxy.load(location.getBlockAddress(), cuboid)
				: null
			;
		});
		
		WalkingNavigator baseNavigator = new WalkingNavigator(blockFetcher
			, volume
			, limit
			, true
			, true
			, true
			, true
		);
		List<AbsoluteLocation> basePath = PathFinder.findPathWithLimit(baseNavigator, sourceBase, targetBase);
		Assert.assertEquals(7, basePath.size());
		
		WalkingNavigator centreNavigator = new WalkingNavigator(blockFetcher
			, volume
			, limit
			, true
			, true
			, true
			, true
		);
		List<AbsoluteLocation> centrePath = PathFinder.findPathWithLimit(centreNavigator, sourceCentre, targetCentre);
		Assert.assertEquals(7, centrePath.size());
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
		TickProcessingContext.IBlockFetcher blockFetcher = ContextBuilder.buildFetcher((AbsoluteLocation location) -> {
			return (location.getCuboidAddress().equals(cuboid.getCuboidAddress()))
				? BlockProxy.load(location.getBlockAddress(), cuboid)
				: null
			;
		});
		
		// We should see 5 steps:  That is enough to walk around to another side, whereas 3 would be a direct jump.
		WalkingNavigator navigator = new WalkingNavigator(blockFetcher
			, volume
			, limit
			, true
			, true
			, true
			, true
		);
		List<AbsoluteLocation> basePath = PathFinder.findPathWithLimit(navigator, source, target);
		Assert.assertEquals(5, basePath.size());
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
		TickProcessingContext.IBlockFetcher blockFetcher = ContextBuilder.buildFetcher((AbsoluteLocation location) -> {
			return (location.getCuboidAddress().equals(cuboid.getCuboidAddress()))
				? BlockProxy.load(location.getBlockAddress(), cuboid)
				: null
			;
		});
		
		// We should see 4 steps.
		WalkingNavigator navigator = new WalkingNavigator(blockFetcher
			, volume
			, limit
			, true
			, true
			, true
			, true
		);
		List<AbsoluteLocation> basePath = PathFinder.findPathWithLimit(navigator, source, target);
		Assert.assertEquals(4, basePath.size());
		Assert.assertEquals(start, basePath.get(0));
		Assert.assertEquals(start.getRelative(1, 0, 0), basePath.get(1));
		Assert.assertEquals(start.getRelative(1, 0, 1), basePath.get(2));
		Assert.assertEquals(end, basePath.get(3));
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
		TickProcessingContext.IBlockFetcher blockFetcher = ContextBuilder.buildFetcher((AbsoluteLocation location) -> {
			return (location.getCuboidAddress().equals(cuboid.getCuboidAddress()))
				? BlockProxy.load(location.getBlockAddress(), cuboid)
				: null
			;
		});
		
		// We should see 4 steps.
		WalkingNavigator navigator = new WalkingNavigator(blockFetcher
			, volume
			, limit
			, true
			, true
			, true
			, true
		);
		List<AbsoluteLocation> basePath = PathFinder.findPathWithLimit(navigator, source, target);
		Assert.assertEquals(4, basePath.size());
		Assert.assertEquals(start, basePath.get(0));
		Assert.assertEquals(start.getRelative(1, 0, 0), basePath.get(1));
		Assert.assertEquals(start.getRelative(1, 0, 1), basePath.get(2));
		Assert.assertEquals(end, basePath.get(3));
	}


	private static void _printMap2D(int x, int y, Collection<AbsoluteLocation> path)
	{
		char[][] map = new char[y][x];
		for (int i = 0; i < y; ++i)
		{
			for (int j = 0; j < x; ++j)
			{
				map[i][j] = ' ';
			}
		}
		for (AbsoluteLocation step : path)
		{
			map[step.y()][step.x()] = 'X';
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

	private static void _printStepMap2D(int x, int y, int fixedZ, Map<AbsoluteLocation, AbsoluteLocation> backStepPath)
	{
		char[][] map = new char[y][x];
		for (int i = 0; i < y; ++i)
		{
			for (int j = 0; j < x; ++j)
			{
				map[i][j] = ' ';
			}
		}
		for (AbsoluteLocation step : backStepPath.keySet())
		{
			if (fixedZ == step.z())
			{
				// Count how many steps this walks.
				int count = 0;
				AbsoluteLocation back = backStepPath.get(step);
				while (null != back)
				{
					count += 1;
					back = backStepPath.get(back);
				}
				map[step.y()][step.x()] = Integer.toString(count).charAt(0);
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

	private static TickProcessingContext.IBlockFetcher _createKindLookup(Function<AbsoluteLocation, _BlockKind> reader)
	{
		// Note that we fake up a reader from a basic BlockKind as a stop-gap while this interface is changed.
		// WARNING:  We cheat this cuboid just so we can look-up flags.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		return ContextBuilder.buildFetcher((AbsoluteLocation location) -> {
			_BlockKind kind = reader.apply(location);
			Block type;
			switch (kind)
			{
			case SOLID:
				type = STONE;
				break;
			case SWIMMABLE:
				type = WATER;
				break;
			case WALKABLE:
				type = ENV.special.AIR;
				break;
			default:
				throw new AssertionError("Unknown");
			}
			return BlockProxy.init(location.getBlockAddress(), cuboid, type.item().number());
		});
	}


	private static record MapResolver(int floorZ, String[] map) implements Function<AbsoluteLocation, _BlockKind>
	{
		@Override
		public _BlockKind apply(AbsoluteLocation l)
		{
			_BlockKind kind = _BlockKind.WALKABLE;
			if (this.floorZ == l.z())
			{
				kind = _BlockKind.SOLID;
			}
			else
			{
				int x = l.x();
				int y = l.y();
				if ((x >= 0) && (x < this.map[0].length())
						&& (y >= 0) && (y < this.map.length))
				{
					char c = this.map[l.y()].charAt(l.x());
					if ('S' == c)
					{
						kind = _BlockKind.SOLID;
					}
					else if ('W' == c)
					{
						kind = _BlockKind.SWIMMABLE;
					}
				}
				else
				{
					kind = _BlockKind.SOLID;
				}
			}
			return kind;
		}
	}


	private static record MapResolver3D(String[][] map) implements Function<AbsoluteLocation, _BlockKind>
	{
		@Override
		public _BlockKind apply(AbsoluteLocation l)
		{
			String[] layer = this.map[l.z()];
			int x = l.x();
			int y = l.y();
			Assert.assertTrue((x >= 0) && (x < layer[0].length())
					&& (y >= 0) && (y < layer.length)
			);
			_BlockKind kind;
			char c = layer[l.y()].charAt(l.x());
			switch (c)
			{
				case 'S':
					kind = _BlockKind.SOLID;
					break;
				case 'W':
					kind = _BlockKind.SWIMMABLE;
					break;
				default:
					kind = _BlockKind.WALKABLE;
			}
			return kind;
		}
	}

	/**
	 * This is to replace _BlockKind since it no longer exists but these tests are simplified by having
	 * it.
	 */
	private static enum _BlockKind
	{
		SOLID,
		SWIMMABLE,
		WALKABLE,
	}
}
