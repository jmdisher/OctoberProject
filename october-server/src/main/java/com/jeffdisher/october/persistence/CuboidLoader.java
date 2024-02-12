package com.jeffdisher.october.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.utils.Assert;
import com.jeffdisher.october.utils.MessageQueue;


/**
 * Handles loading or generating cuboids.  This is done asynchronously but results are exposed as call-return in order
 * to avoid cross-thread interaction details becoming part of the interface.
 */
public class CuboidLoader
{
	private final Map<CuboidAddress, CuboidData> _inFlight;
	private final MessageQueue _queue;
	private final Thread _background;

	// Shared data for passing information back from the background thread.
	private final ReentrantLock _sharedDataLock;
	private Collection<CuboidData> _shared_resolved;

	public CuboidLoader()
	{
		_inFlight = new HashMap<>();
		_queue = new MessageQueue();
		_background = new Thread(() -> {
			_background_main();
		}, "Cuboid Loader");
		
		_sharedDataLock = new ReentrantLock();
		
		_background.start();
	}

	public void shutdown()
	{
		_queue.shutdown();
		try
		{
			_background.join();
		}
		catch (InterruptedException e)
		{
			// We don't use interruption.
			throw Assert.unexpected(e);
		}
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
	 * @return A collection of previously-requested cuboids which have been satisfied in the background (null or non-empty).
	 */
	public Collection<CuboidData> getResultsAndIssueRequest(Collection<CuboidAddress> requestedCuboids)
	{
		// Send this request to the background thread.
		if (!requestedCuboids.isEmpty())
		{
			_queue.enqueue(() -> {
				for (CuboidAddress address : requestedCuboids)
				{
					if (_inFlight.containsKey(address))
					{
						_background_storeResult(_inFlight.get(address));
					}
					else
					{
						// TODO:  Generate anything missing once the generator is added.
					}
				}
			});
		}
		
		// Pass back anything we have completed.
		Collection<CuboidData> resolved;
		_sharedDataLock.lock();
		try
		{
			resolved = _shared_resolved;
			_shared_resolved = null;
		}
		finally
		{
			_sharedDataLock.unlock();
		}
		return resolved;
	}


	private void _background_main()
	{
		Runnable toRun = _queue.pollForNext(0L, null);
		while (null != toRun)
		{
			toRun.run();
			toRun = _queue.pollForNext(0L, null);
		}
	}

	private void _background_storeResult(CuboidData loaded)
	{
		_sharedDataLock.lock();
		try
		{
			if (null == _shared_resolved)
			{
				_shared_resolved = new ArrayList<>();
			}
			_shared_resolved.add(loaded);
		}
		finally
		{
			_sharedDataLock.unlock();
		}
	}
}
