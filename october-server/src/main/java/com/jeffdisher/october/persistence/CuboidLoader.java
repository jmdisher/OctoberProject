package com.jeffdisher.october.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.types.CuboidAddress;


/**
 * Handles loading or generating cuboids.  This is done asynchronously but results are exposed as call-return in order
 * to avoid cross-thread interaction details becoming part of the interface.
 */
public class CuboidLoader
{
	private final Map<CuboidAddress, CuboidData> _inFlight;

	public CuboidLoader()
	{
		_inFlight = new HashMap<>();
	}

	/**
	 * Loads the given cuboid.  Note that this must be called before any consumer of the loader starts up as there is
	 * no synchronization on this path.
	 * Note that this is a temporary interface and will be replaced by a generator and/or pre-constructed world save in
	 * the future.
	 * 
	 * @param cuboid The cuboid to add to the pre-loaded data set.
	 */
	public void preload(CuboidData cuboid)
	{
		_inFlight.put(cuboid.getCuboidAddress(), cuboid);
	}

	/**
	 * Queues up a background request for the given collection of cuboids.  These will be returned in a future call as
	 * they will be internally loaded, asynchronously.
	 * Consequently, this call will return a collection of previously-requested cuboids which have been loaded/generated
	 * in the background.
	 * 
	 * @param requestedCuboids The collection of cuboids to load/generate, by address.
	 * @return A collection of previously-requested cuboids which have been satisfied in the background.
	 */
	public Collection<CuboidData> getResultsAndIssueRequest(Collection<CuboidAddress> requestedCuboids)
	{
		// Note that we are currently faking this as a purely synchronous request.
		// TODO:  Generate anything missing once the generator is added.
		List<CuboidData> loaded = new ArrayList<>();
		for (CuboidAddress address : requestedCuboids)
		{
			if (_inFlight.containsKey(address))
			{
				loaded.add(_inFlight.get(address));
			}
		}
		return loaded;
	}
}
