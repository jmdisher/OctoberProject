package com.jeffdisher.october.utils;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import org.junit.Assert;
import org.junit.Test;


public class TestMessageQueue
{
	@Test
	public void startStop() throws Throwable
	{
		// Start and stop the queue without doing anything, on one thread.
		MessageQueue queue = new MessageQueue();
		queue.shutdown();
		Assert.assertNull(queue.pollForNext(0L, null));
	}

	@Test
	public void basicConsumer() throws Throwable
	{
		MessageQueue queue = new MessageQueue();
		Thread thread = new Thread(() -> {
			Runnable r = queue.pollForNext(0L, null);
			while (null != r)
			{
				r.run();
				r = queue.pollForNext(0L, null);
			}
		});
		thread.start();
		
		int count[] = new int[1];
		for (int i = 0; i < 10; ++i)
		{
			queue.enqueue(() -> {
				count[0] += 1;
			});
		}
		CyclicBarrier barrier = new CyclicBarrier(2);
		queue.enqueue(() -> {
			try
			{
				barrier.await();
			}
			catch (InterruptedException | BrokenBarrierException e)
			{
				// Not expected.
				Assert.fail();
			}
		});
		barrier.await();
		queue.shutdown();
		thread.join();
		Assert.assertEquals(10, count[0]);
	}

	@Test
	public void testWithTimeout() throws Throwable
	{
		// Pre-populate the queue and then add a timeout runnable to await on the barrier - it should only run when everything else is done.
		MessageQueue queue = new MessageQueue();
		int count[] = new int[1];
		for (int i = 0; i < 10; ++i)
		{
			queue.enqueue(() -> {
				count[0] += 1;
			});
		}
		
		CyclicBarrier barrier = new CyclicBarrier(2);
		Runnable timeout = () -> {
			try
			{
				barrier.await();
			}
			catch (InterruptedException | BrokenBarrierException e)
			{
				// Not expected.
				Assert.fail();
			}
		};
		
		Thread thread = new Thread(() -> {
			Runnable r = queue.pollForNext(1L, timeout);
			while (null != r)
			{
				r.run();
				r = queue.pollForNext(1L, timeout);
			}
		});
		thread.start();
		
		barrier.await();
		queue.shutdown();
		thread.join();
		Assert.assertEquals(10, count[0]);
	}

	@Test
	public void shutdownFull() throws Throwable
	{
		MessageQueue queue = new MessageQueue();
		Thread thread = new Thread(() -> {
			Runnable r = queue.pollForNext(0L, null);
			while (null != r)
			{
				r.run();
				r = queue.pollForNext(0L, null);
			}
		});
		
		int count[] = new int[1];
		for (int i = 0; i < 10; ++i)
		{
			queue.enqueue(() -> {
				count[0] += 1;
			});
		}
		// Now that everything is set up, shut down the queue before we start, since that will leave it full, without
		// running anything.
		queue.shutdown();
		thread.start();
		thread.join();
		Assert.assertEquals(0, count[0]);
	}

	@Test
	public void drainBeforeShutdown() throws Throwable
	{
		MessageQueue queue = new MessageQueue();
		Thread thread = new Thread(() -> {
			Runnable r = queue.pollForNext(0L, null);
			while (null != r)
			{
				r.run();
				r = queue.pollForNext(0L, null);
			}
		});
		
		int count[] = new int[1];
		for (int i = 0; i < 10; ++i)
		{
			queue.enqueue(() -> {
				count[0] += 1;
			});
		}
		// Now that everything is set up, start the thread and immediately wait for the queue to drain.
		thread.start();
		queue.waitForEmptyQueue();
		queue.shutdown();
		thread.join();
		Assert.assertEquals(10, count[0]);
	}
}
