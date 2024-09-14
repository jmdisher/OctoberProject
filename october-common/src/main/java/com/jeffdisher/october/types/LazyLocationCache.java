package com.jeffdisher.october.types;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;


/**
 * A basic read-through cache which lazily constructs data for a given AbsoluteLocation.
 */
public class LazyLocationCache<T> implements Function<AbsoluteLocation, T>
{
	private final Function<AbsoluteLocation, T> _elementFactory;
	private final Map<AbsoluteLocation, T> _cache;
	private final Set<AbsoluteLocation> _misses;

	public LazyLocationCache(Function<AbsoluteLocation, T> elementFactory)
	{
		_elementFactory = elementFactory;
		_cache = new HashMap<>();
		_misses = new HashSet<>();
	}

	public Collection<T> getCachedValues()
	{
		return _cache.values();
	}

	@Override
	public T apply(AbsoluteLocation location)
	{
		T data = _cache.get(location);
		if ((null == data) && !_misses.contains(location))
		{
			data = _elementFactory.apply(location);
			if (null != data)
			{
				_cache.put(location, data);
			}
			else
			{
				_misses.add(location);
			}
		}
		return data;
	}
}
