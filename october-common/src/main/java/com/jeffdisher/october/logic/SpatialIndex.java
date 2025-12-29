package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;


/**
 * Used to look-up entities within a volume.
 */
public class SpatialIndex
{
	private final EntityVolume _volume;

	private final int[] _xId;
	private final float[] _xBase;

	private final int[] _yId;
	private final float[] _yBase;

	private final int[] _zId;
	private final float[] _zBase;

	private SpatialIndex(EntityVolume volume
		, int[] xId, float[] xBase
		, int[] yId, float[] yBase
		, int[] zId, float[] zBase
	)
	{
		_volume = volume;
		_xId = xId;
		_xBase = xBase;
		_yId = yId;
		_yBase = yBase;
		_zId = zId;
		_zBase = zBase;
	}

	/**
	 * Finds all the added entities in the given volume.  Note that both the base and edge are considered inclusive so
	 * that even a volume which is a 2-dimensional plane or 1-dimensional line will still be able to intersect.
	 * 
	 * @param base The inclusive base (west, south, down) corner of the search volume.
	 * @param edge The inclusive edge (east, north, up) corner of the search volume.
	 * @return The set of IDs of the entities in this volume.
	 */
	public Set<Integer> idsIntersectingRegion(EntityLocation base, EntityLocation edge)
	{
		// We want to walk from before the list, since we need to see what ends inside the range.
		float startX = base.x() - _volume.width();
		float startY = base.y() - _volume.width();
		float startZ = base.z() - _volume.height();
		
		Set<Integer> inX = _walkOneDimension(_xId, _xBase, startX, edge.x());
		Set<Integer> inY = _walkOneDimension(_yId, _yBase, startY, edge.y());
		Set<Integer> inZ = _walkOneDimension(_zId, _zBase, startZ, edge.z());
		
		Set<Integer> intersection = new HashSet<>(inX);
		intersection.retainAll(inY);
		intersection.retainAll(inZ);
		return intersection;
	}


	private static Set<Integer> _walkOneDimension(int[] ids, float[] bases, float start, float end)
	{
		Set<Integer> set = new HashSet<>();
		int startIndex = Arrays.binarySearch(bases, start);
		int walk;
		if (startIndex >= 0)
		{
			set.add(ids[startIndex]);
			walk = startIndex + 1;
		}
		else
		{
			walk = -startIndex - 1;
		}
		
		while ((walk < bases.length) && (bases[walk] <= end))
		{
			set.add(ids[walk]);
			walk += 1;
		}
		return set;
	}


	public static class Builder
	{
		private final List<_Collector> _baseX;
		private final List<_Collector> _baseY;
		private final List<_Collector> _baseZ;
		public Builder()
		{
			_baseX = new ArrayList<>();
			_baseY = new ArrayList<>();
			_baseZ = new ArrayList<>();
		}
		public Builder add(int id, EntityLocation base)
		{
			_baseX.add(new _Collector(id, base.x()));
			_baseY.add(new _Collector(id, base.y()));
			_baseZ.add(new _Collector(id, base.z()));
			return this;
		}
		public SpatialIndex finish(EntityVolume volume)
		{
			_Comparator comparator = new _Comparator();
			_baseX.sort(comparator);
			_baseY.sort(comparator);
			_baseZ.sort(comparator);
			
			int size = _baseX.size();
			int[] xId = new int[size];
			float[] xBase = new float[size];
			for (int i = 0; i < size; ++i)
			{
				_Collector elt = _baseX.get(i);
				xId[i] = elt.id;
				xBase[i] = elt.f;
			}
			
			int[] yId = new int[size];
			float[] yBase = new float[size];
			for (int i = 0; i < size; ++i)
			{
				_Collector elt = _baseY.get(i);
				yId[i] = elt.id;
				yBase[i] = elt.f;
			}
			
			int[] zId = new int[size];
			float[] zBase = new float[size];
			for (int i = 0; i < size; ++i)
			{
				_Collector elt = _baseZ.get(i);
				zId[i] = elt.id;
				zBase[i] = elt.f;
			}
			
			return new SpatialIndex(volume
				, xId
				, xBase
				, yId
				, yBase
				, zId
				, zBase
			);
		}
	}

	private static class _Comparator implements Comparator<_Collector>
	{
		@Override
		public int compare(_Collector one, _Collector two)
		{
			float delta = one.f - two.f;
			int comp;
			if (delta > 0.0f)
			{
				comp = 1;
			}
			else if (delta < 0.0f)
			{
				comp = -1;
			}
			else
			{
				comp = 0;
			}
			return comp;
		}
	}

	private static record _Collector(int id
		, float f
	) {}
}
