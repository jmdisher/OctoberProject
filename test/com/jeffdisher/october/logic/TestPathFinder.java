package com.jeffdisher.october.logic;

import java.util.List;
import java.util.function.Function;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;


public class TestPathFinder
{
	private static final EntityVolume VOLUME = new EntityVolume(1.8f, 0.5f);

	@Test
	public void flatPlane()
	{
		// The block location is the "base" of the block, much like the entity z is the base of the block where it is standing.
		EntityLocation source = new EntityLocation(-10.5f, -6.5f, 5.0f);
		EntityLocation target = new EntityLocation(4.5f, 6.5f, 5.0f);
		int floor = 4;
		Function<AbsoluteLocation, Short> blockTypeReader = (AbsoluteLocation l) -> (floor == l.z()) ? BlockAspect.STONE : BlockAspect.AIR;
		List<AbsoluteLocation> path = PathFinder.findPath(blockTypeReader, VOLUME, source, target);
		
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
		Function<AbsoluteLocation, Short> blockTypeReader = (AbsoluteLocation l) -> (l.y() == l.z()) ? BlockAspect.STONE : BlockAspect.AIR;
		List<AbsoluteLocation> path = PathFinder.findPath(blockTypeReader, VOLUME, source, target);
		
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
		Function<AbsoluteLocation, Short> blockTypeReader = (AbsoluteLocation l) -> {
			return ((floor == l.z()) || (0 == l.y()))
					? BlockAspect.STONE
					: BlockAspect.AIR
			;
		};
		List<AbsoluteLocation> path = PathFinder.findPath(blockTypeReader, VOLUME, source, target);
		Assert.assertNull(path);
	}

	@Test
	public void gap()
	{
		// The block location is the "base" of the block, much like the entity z is the base of the block where it is standing.
		EntityLocation source = new EntityLocation(-10.5f, -6.5f, 5.0f);
		EntityLocation target = new EntityLocation(4.5f, 6.5f, 5.0f);
		int floor = 4;
		Function<AbsoluteLocation, Short> blockTypeReader = (AbsoluteLocation l) -> {
			return ((floor == l.z()) && (0 == l.y()))
					? BlockAspect.STONE
					: BlockAspect.AIR
			;
		};
		List<AbsoluteLocation> path = PathFinder.findPath(blockTypeReader, VOLUME, source, target);
		Assert.assertNull(path);
	}

	@Test
	public void maze()
	{
		// The block location is the "base" of the block, much like the entity z is the base of the block where it is standing.
		EntityLocation source = new EntityLocation(0.0f, 1.0f, 5.0f);
		EntityLocation target = new EntityLocation(4.0f, 6.0f, 5.0f);
		int floor = 4;
		Function<AbsoluteLocation, Short> blockTypeReader = new MapResolver(floor, new String[] {
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
		List<AbsoluteLocation> path = PathFinder.findPath(blockTypeReader, VOLUME, source, target);
		_printMap2D(9, 9, path);
	}

	@Test
	public void fallThroughHole()
	{
		// We want to fall through a small hole, go down a few layers, and then catch the ledge.
		EntityLocation source = new EntityLocation(1.5f, 2.5f, 4.0f);
		EntityLocation target = new EntityLocation(3.5f, 2.5f, 1.0f);
		Function<AbsoluteLocation, Short> blockTypeReader = new MapResolver3D(new String[][] {
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
		List<AbsoluteLocation> path = PathFinder.findPath(blockTypeReader, VOLUME, source, target);
		// This is a direct walk, with a fall in the middle, so it should just be the difference in locations +1 to start.
		int xSteps = 2;
		int ySteps = 0;
		int zSteps = 3;
		Assert.assertEquals(1 + xSteps + ySteps + zSteps, path.size());
	}


	private static void _printMap2D(int x, int y, List<AbsoluteLocation> path)
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


	private static record MapResolver(int floorZ, String[] map) implements Function<AbsoluteLocation, Short>
	{
		@Override
		public Short apply(AbsoluteLocation l)
		{
			short value = BlockAspect.AIR;
			if (this.floorZ == l.z())
			{
				value = BlockAspect.STONE;
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
						value = BlockAspect.STONE;
					}
				}
				else
				{
					value = BlockAspect.STONE;
				}
			}
			return value;
		}
	}


	private static record MapResolver3D(String[][] map) implements Function<AbsoluteLocation, Short>
	{
		@Override
		public Short apply(AbsoluteLocation l)
		{
			String[] layer = this.map[l.z()];
			int x = l.x();
			int y = l.y();
			Assert.assertTrue((x >= 0) && (x < layer[0].length())
					&& (y >= 0) && (y < layer.length)
			);
			char c = layer[l.y()].charAt(l.x());
			return ('S' == c)
					? BlockAspect.STONE
					: BlockAspect.AIR
			;
		}
	}
}
