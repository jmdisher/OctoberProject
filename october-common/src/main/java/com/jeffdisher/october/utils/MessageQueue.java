package com.jeffdisher.october.utils;

import java.util.LinkedList;
import java.util.Queue;


/**
 * A blocking queue of Runnable objects for use in message passing between threads.
 */
public class MessageQueue
{
	private Queue<Runnable> _queue = new LinkedList<>();
	private boolean _running = true;

	/**
	 * Polls for the next runnable, blocking until one exists, the millisToWait have expired, or the queue is shut down.
	 * Note that this will return null on shutdown, even if there are still Runnable objects in the queue.
	 * 
	 * @param millisToWait The time to wait, in milliseconds, before returning timeoutRunnable (ignored if null
	 * timeoutRunnable).  This must always be >= 0L (even if timeoutRunnable is null) and > 0L if timeoutRunnable is
	 * non-null.
	 * @param timeoutRunnable The runnable to return if the timeout elapses without anything else happening.
	 * @return The next Runnable or null, if the queue is shut down.
	 */
	public synchronized Runnable pollForNext(long millisToWait, Runnable timeoutRunnable)
	{
		// Millis to wait must be a reasonable value.
		if (null != timeoutRunnable)
		{
			Assert.assertTrue(millisToWait > 0L);
		}
		else
		{
			Assert.assertTrue(millisToWait >= 0L);
		}
		
		// Determine whether or not to honour the timeout.
		long realWaitMillis = (null != timeoutRunnable)
				? millisToWait
				: 0L
		;
		// We want to break out if we wanted a timeout and it expired so we use a local flag.
		boolean continueToWait = true;
		while (_running && continueToWait && _queue.isEmpty())
		{
			try
			{
				this.wait(realWaitMillis);
				if (realWaitMillis > 0L)
				{
					// If we were waiting with a timeout, we want to drop out now (either the timer expired or we were notified of new data).
					continueToWait = false;
				}
			}
			catch (InterruptedException e)
			{
				// We don't use interruption.
				throw Assert.unexpected(e);
			}
		}
		Runnable runnable = _running
				? (!_queue.isEmpty()
						? _queue.remove()
						: timeoutRunnable
				)
				: null
		;
		// We want to notify anyone waiting for the queue to drain.
		if (_queue.isEmpty())
		{
			this.notifyAll();
		}
		return runnable;
	}
 
	/**
	 * Enqueues the next runnable task.
	 * 
	 * @param r The runnable task.
	 * @return True if this was enqueued, false if the receiver has been shut down.
	 */
	public synchronized boolean enqueue(Runnable r)
	{
		if (_running)
		{
			_queue.add(r);
			this.notifyAll();
		}
		return _running;
	}

	/**
	 * Called by a thread which should not normally be consuming messages in order to wait for the consuming thread to
	 * drain the queue.  This will block until the queue is empty.
	 * Note that the queue must NOT be shut down during this operation (as that would negate the purpose).
	 */
	public synchronized void waitForEmptyQueue()
	{
		while (!_queue.isEmpty())
		{
			Assert.assertTrue(_running);
			try
			{
				this.wait();
			}
			catch (InterruptedException e)
			{
				// We don't use interruption.
				throw Assert.unexpected(e);
			}
		}
	}

	/**
	 * Shuts down the queue.  Note that this will cause future calls to enqueue() to fail and will allow any threads
	 * blocked in pollForNext() to return null.
	 */
	public synchronized void shutdown()
	{
		_running = false;
		this.notifyAll();
	}
}
