package com.jeffdisher.october.persistence;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.utils.Assert;
import com.jeffdisher.october.utils.MessageQueue;


/**
 * Handles loading or generating cuboids.  This is done asynchronously but results are exposed as call-return in order
 * to avoid cross-thread interaction details becoming part of the interface.
 */
public class CuboidLoader
{
	public static final int SERIALIZATION_BUFFER_SIZE_BYTES = 1024 * 1024;

	private final File _saveDirectory;
	private final Function<CuboidAddress, CuboidData> _cuboidGenerator;
	private final Map<CuboidAddress, CuboidData> _inFlight;
	private final MessageQueue _queue;
	private final Thread _background;
	private final ByteBuffer _backround_serializationBuffer;

	// Shared data for passing information back from the background thread.
	private final ReentrantLock _sharedDataLock;
	private Collection<CuboidData> _shared_resolved;

	public CuboidLoader(File saveDirectory, Function<CuboidAddress, CuboidData> generator)
	{
		// The save directory must exist as a directory before we get here.
		Assert.assertTrue(saveDirectory.isDirectory());
		
		_saveDirectory = saveDirectory;
		_cuboidGenerator = generator;
		_inFlight = new HashMap<>();
		_queue = new MessageQueue();
		_background = new Thread(() -> {
			_background_main();
		}, "Cuboid Loader");
		_backround_serializationBuffer = ByteBuffer.allocate(SERIALIZATION_BUFFER_SIZE_BYTES);
		
		_sharedDataLock = new ReentrantLock();
		
		_background.start();
	}

	public void shutdown()
	{
		// We want to make sure any other IO operations requested are completed.
		_queue.waitForEmptyQueue();
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
					// Priority of loads:
					// 1) Disk (since that is always considered authoritative)
					// 2) _inFlight (since these are supposed to be prioritized over generation
					// 3) The generator (if present).
					// 4) Return null (only happens in tests)
					
					// See if we can load this from disk.
					CuboidData data = _background_readFromDisk(address);
					if (null == data)
					{
						if (_inFlight.containsKey(address))
						{
							// We will "consume" this since we will load from disk on the next call.
							data = _inFlight.remove(address);
						}
						else if (null != _cuboidGenerator)
						{
							data = _cuboidGenerator.apply(address);
						}
					}
					// If we found anything, return it.
					if (null != data)
					{
						_background_storeResult(data);
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

	/**
	 * Requests that the given collection of cuboids be written back to disk.  This call will return immediately while
	 * the write-back will complete asynchronously.
	 * 
	 * @param cuboids The cuboids to write (cannot be empty).
	 */
	public void writeBackToDisk(Collection<IReadOnlyCuboidData> cuboids)
	{
		// This one should only be called if there are some to remove.
		Assert.assertTrue(!cuboids.isEmpty());
		_queue.enqueue(() -> {
			for (IReadOnlyCuboidData cuboid : cuboids)
			{
				_background_writeToDisk(cuboid);
			}
		});
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

	private CuboidData _background_readFromDisk(CuboidAddress address)
	{
		// These data files are relatively small so we can just read this in, completely.
		CuboidData cuboid;
		try (
				RandomAccessFile aFile = new RandomAccessFile(_getFile(address), "r");
				FileChannel inChannel = aFile.getChannel();
		)
		{
			MappedByteBuffer buffer = inChannel.map(FileChannel.MapMode.READ_ONLY, 0, inChannel.size());
			buffer.load();
			cuboid = CuboidData.createEmpty(address);
			Object state = cuboid.deserializeResumable(null, buffer);
			// There should be no resumable state since the file is complete.
			Assert.assertTrue(null == state);
		}
		catch (FileNotFoundException e)
		{
			// This is ok and means we should return null.
			cuboid = null;
		}
		catch (IOException e)
		{
			throw Assert.unexpected(e);
		}
		return cuboid;
	}

	private void _background_writeToDisk(IReadOnlyCuboidData cuboid)
	{
		// Serialize the entire cuboid into memory and write it out.
		Assert.assertTrue(0 == _backround_serializationBuffer.position());
		Object state = cuboid.serializeResumable(null, _backround_serializationBuffer);
		// We currently assume that we just do the write in a single call.
		Assert.assertTrue(null == state);
		_backround_serializationBuffer.flip();
		
		try (
				
				RandomAccessFile aFile = new RandomAccessFile(_getFile(cuboid.getCuboidAddress()), "rw");
				FileChannel outChannel = aFile.getChannel();
		)
		{
			outChannel.write(_backround_serializationBuffer);
			// We expect that this wrote fully.
			Assert.assertTrue(!_backround_serializationBuffer.hasRemaining());
			_backround_serializationBuffer.clear();
		}
		catch (FileNotFoundException e)
		{
			throw Assert.unexpected(e);
		}
		catch (IOException e)
		{
			throw Assert.unexpected(e);
		}
	}

	private File _getFile(CuboidAddress address)
	{
		String fileName = "cuboid_" + address.x() + "_" + address.y() + "_" + address.z() + ".cuboid";
		return new File(_saveDirectory, fileName);
	}
}
