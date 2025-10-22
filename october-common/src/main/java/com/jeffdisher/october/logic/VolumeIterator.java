package com.jeffdisher.october.logic;

import java.util.Iterator;

import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.utils.Assert;


/**
 * An iterator to walk all the blocks intersected by a volume rooted at base.
 */
public class VolumeIterator implements Iterable<AbsoluteLocation>
{
	private final AbsoluteLocation _start;
	private final AbsoluteLocation _end;

	public VolumeIterator(EntityLocation base, EntityVolume volume)
	{
		EntityLocation entityEdge = new EntityLocation(base.x() + volume.width()
			, base.y() + volume.width()
			, base.z() + volume.height()
		);
		_start = base.getBlockLocation();
		_end = entityEdge.getBlockLocation();
	}

	@Override
	public Iterator<AbsoluteLocation> iterator()
	{
		return new Iterator<>() {
			private final int _startX = _start.x();
			private final int _startY = _start.y();
			private final int _startZ = _start.z();
			private final int _endX = _end.x();
			private final int _endY = _end.y();
			private final int _endZ = _end.z();
			private int _x = _startX;
			private int _y = _startY;
			private int _z = _startZ;
			
			@Override
			public boolean hasNext()
			{
				return (_x <= _endX) && (_y <= _endY) && (_z <= _endZ);
			}
			
			@Override
			public AbsoluteLocation next()
			{
				AbsoluteLocation next;
				if ((_x <= _endX) && (_y <= _endY) && (_z <= _endZ))
				{
					next = new AbsoluteLocation(_x, _y, _z);
				}
				else
				{
					// This would be a failure of hasNext().
					throw Assert.unreachable();
				}
				
				_x += 1;
				if (_x > _endX)
				{
					_x = _startX;
					_y += 1;
					if (_y > _endY)
					{
						_y = _startY;
						_z += 1;
					}
				}
				return next;
			}
		};
	}
}
