package com.jeffdisher.october.client;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.locks.ReentrantLock;


/**
 * Used as a common utility in the client-side classes which receive callbacks on a background/network thread but need
 * to actually run the meaning of these callbacks on the main UI thread.
 * This creates a locked queue of actions to run which all expect the current time (when they are being run) to be
 * passed in.
 */
public class TimeRunnerList
{
	private final ReentrantLock _internalsLock = new ReentrantLock();
	private final Queue<ITimeConsumer> _calls = new LinkedList<>();

	public void enqueue(ITimeConsumer runnable)
	{
		_internalsLock.lock();
		try
		{
			_calls.add(runnable);
		}
		finally
		{
			_internalsLock.unlock();
		}
	}

	public int runFullQueue(long currentTimeMillis)
	{
		_internalsLock.lock();
		List<ITimeConsumer> copy;
		try
		{
			copy = new LinkedList<>(_calls);
			_calls.clear();
		}
		finally
		{
			_internalsLock.unlock();
		}
		
		int count = 0;
		for (ITimeConsumer consumer : copy)
		{
			consumer.accept(currentTimeMillis);
			count += 1;
		}
		return count;
	}


	public static interface ITimeConsumer
	{
		void accept(long currentTimeMillis);
	}
}
