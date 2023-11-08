package com.jeffdisher.october.logic;

import com.jeffdisher.october.utils.Assert;


public class SyncPoint
{
	private final int _totalThreadCount;
	private int _threadsWaiting;

	public SyncPoint(int totalThreadCount)
	{
		_totalThreadCount = totalThreadCount;
		_threadsWaiting = 0;
	}

	public synchronized void synchronizeThreads()
	{
		_threadsWaiting += 1;
		if (_totalThreadCount == _threadsWaiting)
		{
			// This was the last thread so notify everyone.
			_threadsWaiting = 0;
			this.notifyAll();
		}
		else
		{
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

	public synchronized boolean synchronizeAndReleaseLast()
	{
		boolean isLast = false;
		_threadsWaiting += 1;
		
		// If we were the last thread, we will be the one which runs.
		isLast = (_totalThreadCount == _threadsWaiting);
		
		// If we are not the last thread, we need to wait.
		if (!isLast)
		{
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
		return isLast;
	}

	public synchronized void releaseWaitingThreads()
	{
		// This was the last thread so notify everyone.
		_threadsWaiting = 0;
		this.notifyAll();
	}
}
