package com.jeffdisher.october.process;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.concurrent.CountDownLatch;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.server.MonitoringAgent;


public class TestConsoleHandler
{
	@Test
	public void basicStop() throws Throwable
	{
		// The basic use-case where we just stop, inline.
		InputStream in = new ByteArrayInputStream("!stop\n".getBytes());
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PrintStream printer = new PrintStream(out);
		MonitoringAgent monitoringAgent = new MonitoringAgent();
		ConsoleHandler.readUntilStop(in, printer, monitoringAgent);
		Assert.assertArrayEquals("Shutting down...\n".getBytes(), out.toByteArray());
	}

	@Test
	public void backgroundThread() throws Throwable
	{
		// A use-case for cases where the existing thread cannot be blocked so the handler is run on another thread.
		InputStream in = new ByteArrayInputStream("!stop\n".getBytes());
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PrintStream printer = new PrintStream(out);
		MonitoringAgent monitoringAgent = new MonitoringAgent();
		CountDownLatch startLatch = new CountDownLatch(1);
		Thread background = new Thread(() -> {
			try
			{
				startLatch.countDown();
				ConsoleHandler.readUntilStop(in, printer, monitoringAgent);
			}
			catch (IOException e)
			{
				throw new AssertionError(e);
			}
		});
		background.start();
		// Wait for the thread to start.
		startLatch.await();
		background.join();
		Assert.assertArrayEquals("Shutting down...\n".getBytes(), out.toByteArray());
	}

	@Test
	public void interruptStdin() throws Throwable
	{
		// There doesn't seem to be a portable to do non-blocking reading of stdin (minimally, this requires
		// platform-specific console mode switching) so we use the interruptable path with thread interruption.
		InputStream in = System.in;
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PrintStream printer = new PrintStream(out);
		MonitoringAgent monitoringAgent = new MonitoringAgent();
		CountDownLatch startLatch = new CountDownLatch(1);
		CountDownLatch exceptionLatch = new CountDownLatch(1);
		Thread background = new Thread(() -> {
			try
			{
				startLatch.countDown();
				ConsoleHandler.readUntilStopInterruptable(in, printer, monitoringAgent);
			}
			catch (IOException e)
			{
				throw new AssertionError(e);
			}
			catch (InterruptedException e)
			{
				// This is what we expect.
				exceptionLatch.countDown();
			}
		});
		background.start();
		// Wait for the thread to start.
		startLatch.await();
		// Stall so that we actually get running.
		Thread.sleep(100L);
		background.interrupt();
		exceptionLatch.await();
		background.join();
		Assert.assertArrayEquals(new byte[0], out.toByteArray());
	}
}
