package com.jeffdisher.october.types;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.jeffdisher.october.utils.Assert;


/**
 * A basic read-through cache which lazily constructs data for a given AbsoluteLocation.
 */
public class LazyLocationCache<T> implements Function<AbsoluteLocation, T>
{
	private final Function<AbsoluteLocation, T> _elementFactory;
	private final Map<AbsoluteLocation, T> _cache;
	private final Set<AbsoluteLocation> _misses;
	private boolean _isValid;

	public LazyLocationCache(Function<AbsoluteLocation, T> elementFactory)
	{
		_elementFactory = elementFactory;
		_cache = new HashMap<>();
		_misses = new HashSet<>();
		_isValid = true;
	}

	@Override
	public T apply(AbsoluteLocation location)
	{
		Assert.assertTrue(_isValid);
		
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

	/**
	 * Allows users of this cache to extract what has been internally populated for other use or analysis.  Note that
	 * calling this method will result in all other attempted uses of the cache throwing AssertionError.
	 * 
	 * @return The core cache instance.
	 */
	public Map<AbsoluteLocation, T> extractCache()
	{
		Assert.assertTrue(_isValid);
		
		_isValid = false;
		return _cache;
	}
}
