package com.jeffdisher.october.logic;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;


public class TestPathFinder
{
	private static final EntityVolume VOLUME = new EntityVolume(1.8f, 0.5f);
	@BeforeClass
	public static void setup()
	{
		Environment.createSharedInstance();
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
		Function<AbsoluteLocation, PathFinder.BlockKind> blockPermitsUser = (AbsoluteLocation location) -> (floor == location.z()) ? PathFinder.BlockKind.SOLID : PathFinder.BlockKind.WALKABLE;
		List<AbsoluteLocation> path = PathFinder.findPath(blockPermitsUser, VOLUME, source, target);
		
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
		Function<AbsoluteLocation, PathFinder.BlockKind> blockPermitsUser = (AbsoluteLocation location) -> (location.y() == location.z()) ? PathFinder.BlockKind.SOLID : PathFinder.BlockKind.WALKABLE;
		List<AbsoluteLocation> path = PathFinder.findPath(blockPermitsUser, VOLUME, source, target);
		
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
		Function<AbsoluteLocation, PathFinder.BlockKind> blockPermitsUser = (AbsoluteLocation location) -> {
			return ((floor == location.z()) || (0 == location.y()))
					? PathFinder.BlockKind.SOLID
					: PathFinder.BlockKind.WALKABLE
			;
		};
		List<AbsoluteLocation> path = PathFinder.findPath(blockPermitsUser, VOLUME, source, target);
		Assert.assertNull(path);
	}

	@Test
	public void gap()
	{
		// The block location is the "base" of the block, much like the entity z is the base of the block where it is standing.
		EntityLocation source = new EntityLocation(-10.5f, -6.5f, 5.0f);
		EntityLocation target = new EntityLocation(4.5f, 6.5f, 5.0f);
		int floor = 4;
		Function<AbsoluteLocation, PathFinder.BlockKind> blockPermitsUser = (AbsoluteLocation location) -> {
			return ((floor == location.z()) || (0 == location.y()))
					? PathFinder.BlockKind.SOLID
					: PathFinder.BlockKind.WALKABLE
			;
		};
		List<AbsoluteLocation> path = PathFinder.findPath(blockPermitsUser, VOLUME, source, target);
		Assert.assertNull(path);
	}

	@Test
	public void maze()
	{
		// The block location is the "base" of the block, much like the entity z is the base of the block where it is standing.
		EntityLocation source = new EntityLocation(0.0f, 1.0f, 5.0f);
		EntityLocation target = new EntityLocation(4.0f, 6.0f, 5.0f);
		int floor = 4;
		Function<AbsoluteLocation, PathFinder.BlockKind> blockPermitsUser = new MapResolver(floor, new String[] {
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
		List<AbsoluteLocation> path = PathFinder.findPath(blockPermitsUser, VOLUME, source, target);
		_printMap2D(9, 9, path);
	}

	@Test
	public void fallThroughHole()
	{
		// We want to fall through a small hole, go down a few layers, and then catch the ledge.
		EntityLocation source = new EntityLocation(1.5f, 2.5f, 4.0f);
		EntityLocation target = new EntityLocation(3.5f, 2.5f, 1.0f);
		Function<AbsoluteLocation, PathFinder.BlockKind> blockPermitsUser = new MapResolver3D(new String[][] {
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
		List<AbsoluteLocation> path = PathFinder.findPath(blockPermitsUser, VOLUME, source, target);
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
		Function<AbsoluteLocation, PathFinder.BlockKind> blockPermitsUser = new MapResolver(floor, new String[] {
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
		Map<AbsoluteLocation, AbsoluteLocation> places = PathFinder.findPlacesWithinLimit(blockPermitsUser, VOLUME, source, 2.0f);
		_printStepMap2D(10, 9, 5, places);
		places = PathFinder.findPlacesWithinLimit(blockPermitsUser, VOLUME, source, 4.0f);
		_printStepMap2D(10, 9, 5, places);
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


	private static record MapResolver(int floorZ, String[] map) implements Function<AbsoluteLocation, PathFinder.BlockKind>
	{
		@Override
		public PathFinder.BlockKind apply(AbsoluteLocation l)
		{
			PathFinder.BlockKind kind = PathFinder.BlockKind.WALKABLE;
			if (this.floorZ == l.z())
			{
				kind = PathFinder.BlockKind.SOLID;
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
						kind = PathFinder.BlockKind.SOLID;
					}
				}
				else
				{
					kind = PathFinder.BlockKind.SOLID;
				}
			}
			return kind;
		}
	}


	private static record MapResolver3D(String[][] map) implements Function<AbsoluteLocation, PathFinder.BlockKind>
	{
		@Override
		public PathFinder.BlockKind apply(AbsoluteLocation l)
		{
			String[] layer = this.map[l.z()];
			int x = l.x();
			int y = l.y();
			Assert.assertTrue((x >= 0) && (x < layer[0].length())
					&& (y >= 0) && (y < layer.length)
			);
			char c = layer[l.y()].charAt(l.x());
			return ('S' == c)
					? PathFinder.BlockKind.SOLID
					: PathFinder.BlockKind.WALKABLE
			;
		}
	}
}
