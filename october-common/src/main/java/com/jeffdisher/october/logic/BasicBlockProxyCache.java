package com.jeffdisher.october.logic;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.types.AbsoluteLocation;


/**
 * A simple read-through cache of BlockProxy instances since creating them does have a cost and the same instances are
 * often requested multiple times in the same tick.
 */
public class BasicBlockProxyCache implements Function<AbsoluteLocation, BlockProxy>
{
	private final Function<AbsoluteLocation, BlockProxy> _loader;
	private final Map<AbsoluteLocation, BlockProxy> _cache;

	public BasicBlockProxyCache(Function<AbsoluteLocation, BlockProxy> loader)
	{
		_loader = loader;
		_cache = new HashMap<>();
	}

	@Override
	public BlockProxy apply(AbsoluteLocation location)
	{
		BlockProxy proxy;
		if (_cache.containsKey(location))
		{
			proxy = _cache.get(location);
		}
		else
		{
			proxy = _loader.apply(location);
			_cache.put(location, proxy);
		}
		return proxy;
	}

}
