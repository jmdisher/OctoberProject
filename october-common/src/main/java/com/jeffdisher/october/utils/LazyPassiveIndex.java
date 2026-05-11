package com.jeffdisher.october.utils;

import java.util.Map;

import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.PartialPassive;
import com.jeffdisher.october.types.PassiveEntity;
import com.jeffdisher.october.types.PassiveType;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * An implementation of the passive search interface which lazily builds the related SpatialIndex when data is requested
 * from it (since it is somewhat expensive to create).
 * If this ends up being used more often (to the point where it is usually being generated, anyway), it would make more
 * sense to eagerly build the index while synchronized.
 */
public class LazyPassiveIndex implements TickProcessingContext.IPassiveSearch
{
	private final Map<Integer, PassiveEntity> _allPassives;
	private SpatialIndex _passiveSpatialIndex;

	public LazyPassiveIndex(Map<Integer, PassiveEntity> allPassives)
	{
		_allPassives = allPassives;
	}

	@Override
	public PartialPassive getById(int id)
	{
		PassiveEntity passive = _allPassives.get(id);
		return (null != passive)
			? PartialPassive.fromPassive(passive)
			: null
		;
	}

	@Override
	public PartialPassive[] findPassiveItemSlotsInRegion(EntityLocation base, EntityLocation edge)
	{
		// The _passiveSpatialIndex is lazily constructed since it is only used by hoppers and player entities.
		if (null == _passiveSpatialIndex)
		{
			// NOTE:  We only expose passive entities in the interface since we only have a use-case for them, at the moment.
			SpatialIndex.Builder builder = new SpatialIndex.Builder();
			for (PassiveEntity passive : _allPassives.values())
			{
				if (PassiveType.ITEM_SLOT == passive.type())
				{
					builder.add(passive.id(), passive.location());
				}
			}
			_passiveSpatialIndex = builder.finish(PassiveType.ITEM_SLOT.volume());
		}
		return _passiveSpatialIndex.idsIntersectingRegion(base, edge).stream()
			.map((Integer id) -> {
				PassiveEntity passive = _allPassives.get(id);
				return PartialPassive.fromPassive(passive);
			})
			.toArray((int size) -> new PartialPassive[size])
		;
	}
}
