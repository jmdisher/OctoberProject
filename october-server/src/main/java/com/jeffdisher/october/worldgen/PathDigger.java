package com.jeffdisher.october.worldgen;

import java.util.List;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IOctree;
import com.jeffdisher.october.logic.RayCastHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.utils.Encoding;


/**
 * Can hollow-out paths in cuboids during generation.
 */
public class PathDigger
{
	/**
	 * Hollows out any instances of blockToRemove in data which intersect with the sphere at centre, of radius,
	 * replacing them with blockToAdd.
	 * 
	 * @param data The cuboid to read and write (only the BLOCK aspect will be modified).
	 * @param centre The centre of the sphere.
	 * @param radius The radius of the sphere.
	 * @param blockToRemove The block value of the blocks we should be replacing with blockToAdd.
	 * @param blockToAdd The block value of the blocks we should be writing over instances of blockToRemove.
	 */
	public static void hollowOutSphere(CuboidData data, AbsoluteLocation centre, int radius, short blockToRemove, short blockToAdd)
	{
		// First, we want to convert the data into a local cache we can use for quickly making decisions.
		_CuboidWrapper wrapper = _CuboidWrapper.wrap(data, blockToRemove, blockToAdd);
		
		_hollowOutSphere(wrapper, centre, radius);
	}

	/**
	 * Hollows out any instances of blockToRemove in data which intersect with a path consisting of spheres from start
	 * to end, with corresponding radii, replacing them with blockToAdd.
	 * 
	 * @param data The cuboid to read and write (only the BLOCK aspect will be modified).
	 * @param start The start of the sphere path.
	 * @param startRadius The radius at the start.
	 * @param end The end of the sphere path.
	 * @param endRadius The radius at the end.
	 * @param blockToRemove The block value of the blocks we should be replacing with blockToAdd.
	 * @param blockToAdd The block value of the blocks we should be writing over instances of blockToRemove.
	 */
	public static void hollowOutPath(CuboidData data, AbsoluteLocation start, int startRadius, AbsoluteLocation end, int endRadius, short blockToRemove, short blockToAdd)
	{
		// First, we want to convert the data into a local cache we can use for quickly making decisions.
		_CuboidWrapper wrapper = _CuboidWrapper.wrap(data, blockToRemove, blockToAdd);
		
		// We will walk the path from the start to end, hollowing out a sphere at each step, and will interpolate radii between start and end.
		List<AbsoluteLocation> path = RayCastHelpers.findFullLine(start, end);
		float oneRadius = (float)startRadius;
		float radiusChange = ((float)endRadius - oneRadius) / (float)path.size();
		
		for (AbsoluteLocation step : path)
		{
			_hollowOutSphere(wrapper, step, oneRadius);
			oneRadius += radiusChange;
		}
	}


	private static void _hollowOutSphere(_CuboidWrapper wrapper, AbsoluteLocation centre, float radius)
	{
		// For now, we will just use a simple algorithm, nothing too clever:
		// 1) Consider the sphere as a stack of circles (stacked in z)
		// 2) Fill each circle, and fill its reflection (in the xy plane)
		float mainRadiusSquared = radius * radius;
		int limitRadius = Math.round(radius);
		_hollowOutCircle(wrapper, centre, mainRadiusSquared, 0);
		for (int z = 1; z <= limitRadius; ++z)
		{
			float oneRadiusSquared = mainRadiusSquared - (z * z);
			_hollowOutCircle(wrapper, centre.getRelative(0, 0, z), oneRadiusSquared, -2 * z);
		}
	}

	private static void _hollowOutCircle(_CuboidWrapper wrapper, AbsoluteLocation centre, float radiusSquared, int zOffset)
	{
		// We are solving y = sqrt(x^2 + r^2)
		int edge = Math.round((float)Math.sqrt(radiusSquared));
		for (int x = 0; x <= edge; ++x)
		{
			float xSquared = (x * x);
			int y = Math.round((float)Math.sqrt(radiusSquared - xSquared));
			
			// We need to populate every block in this x column, up to y, but in all 4 quadrants and also on the other z.
			for (int inY = 0; inY <= y; ++inY)
			{
				wrapper.set(centre.x() + x, centre.y() + inY, centre.z());
				wrapper.set(centre.x() + x, centre.y() - inY, centre.z());
				wrapper.set(centre.x() - x, centre.y() - inY, centre.z());
				wrapper.set(centre.x() - x, centre.y() + inY, centre.z());
				
				wrapper.set(centre.x() + x, centre.y() + inY, centre.z() + zOffset);
				wrapper.set(centre.x() + x, centre.y() - inY, centre.z() + zOffset);
				wrapper.set(centre.x() - x, centre.y() - inY, centre.z() + zOffset);
				wrapper.set(centre.x() - x, centre.y() + inY, centre.z() + zOffset);
			}
		}
	}


	private static class _CuboidWrapper
	{
		public static _CuboidWrapper wrap(CuboidData data, short blockToRemove, short blockToAdd)
		{
			// ZYX order.
			boolean[][][] shouldWrite = new boolean[Encoding.CUBOID_EDGE_SIZE][][];
			data.walkData(AspectRegistry.BLOCK, new IOctree.IWalkerCallback<>() {
				@Override
				public void visit(BlockAddress base, byte size, Short value)
				{
					if (blockToRemove == value)
					{
						for (int z = 0; z < size; ++z)
						{
							for (int y = 0; y < size; ++y)
							{
								for (int x = 0; x < size; ++x)
								{
									int ez = base.z() + z;
									int ey = base.y() + y;
									int ex = base.x() + x;
									if (null == shouldWrite[ez])
									{
										shouldWrite[ez] = new boolean[Encoding.CUBOID_EDGE_SIZE][];
									}
									if (null == shouldWrite[ez][ey])
									{
										shouldWrite[ez][ey] = new boolean[Encoding.CUBOID_EDGE_SIZE];
									}
									shouldWrite[ez][ey][ex] = true;
								}
							}
						}
					}
				}
			}, blockToAdd);
			return new _CuboidWrapper(data, shouldWrite, blockToAdd);
		}
		
		private final CuboidData _data;
		private final AbsoluteLocation _cuboidBase;
		private final boolean[][][] _shouldWriteZYX;
		private final short _blockToAdd;
		private _CuboidWrapper(CuboidData data, boolean[][][] shouldWrite, short blockToAdd)
		{
			_data = data;
			_cuboidBase = data.getCuboidAddress().getBase();
			_shouldWriteZYX = shouldWrite;
			_blockToAdd = blockToAdd;
		}
		public void set(int globalX, int globalY, int globalZ)
		{
			int x = globalX - _cuboidBase.x();
			int y = globalY - _cuboidBase.y();
			int z = globalZ - _cuboidBase.z();
			if ((x >= 0) && (x < Encoding.CUBOID_EDGE_SIZE)
					&& (y >= 0) && (y < Encoding.CUBOID_EDGE_SIZE)
					&& (z >= 0) && (z < Encoding.CUBOID_EDGE_SIZE)
					&& (null != _shouldWriteZYX[z])
					&& (null != _shouldWriteZYX[z][y])
					&& _shouldWriteZYX[z][y][x]
			)
			{
				_data.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(x, y, z), _blockToAdd);
				_shouldWriteZYX[z][y][x] = false;
			}
		}
	}
}
