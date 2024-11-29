package com.jeffdisher.october.process;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.concurrent.CountDownLatch;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.server.MonitoringAgent;
import com.jeffdisher.october.server.TickRunner;
import com.jeffdisher.october.types.Difficulty;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.WorldConfig;


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
		ConsoleHandler.readUntilStop(in, printer, monitoringAgent, new WorldConfig());
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
				ConsoleHandler.readUntilStop(in, printer, monitoringAgent, new WorldConfig());
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
				ConsoleHandler.readUntilStopInterruptable(in, printer, monitoringAgent, new WorldConfig());
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

	@Test
	public void operatorSetCreative() throws Throwable
	{
		// The basic use-case where we just stop, inline.
		InputStream in = new ByteArrayInputStream("!set_mode 123 creative\n!stop\n".getBytes());
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PrintStream printer = new PrintStream(out);
		MonitoringAgent monitoringAgent = new MonitoringAgent();
		boolean[] didCheck = new boolean[1];
		monitoringAgent.setOperatorCommandSink(new _TestCommandSink()
		{
			@Override
			public void submitEntityMutation(int clientId, IMutationEntity<IMutablePlayerEntity> command)
			{
				Assert.assertEquals(123, clientId);
				Assert.assertNotNull(command);
				didCheck[0] = true;
			}
		});
		ConsoleHandler.readUntilStop(in, printer, monitoringAgent, new WorldConfig());
		Assert.assertArrayEquals("Shutting down...\n".getBytes(), out.toByteArray());
		Assert.assertTrue(didCheck[0]);
	}

	@Test
	public void operatorSetDifficulty() throws Throwable
	{
		// The basic use-case where we just stop, inline.
		InputStream in = new ByteArrayInputStream("!set_difficulty peaceful\n!stop\n".getBytes());
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PrintStream printer = new PrintStream(out);
		MonitoringAgent monitoringAgent = new MonitoringAgent();
		WorldConfig config = new WorldConfig();
		Assert.assertEquals(Difficulty.HOSTILE, config.difficulty);
		ConsoleHandler.readUntilStop(in, printer, monitoringAgent, config);
		Assert.assertArrayEquals("Shutting down...\n".getBytes(), out.toByteArray());
		Assert.assertEquals(Difficulty.PEACEFUL, config.difficulty);
	}

	@Test
	public void operatorTeleport() throws Throwable
	{
		// Just verify that the call is sent.
		InputStream in = new ByteArrayInputStream("!tp 123 5 -6 12\n!stop\n".getBytes());
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PrintStream printer = new PrintStream(out);
		MonitoringAgent monitoringAgent = new MonitoringAgent();
		boolean[] didCheck = new boolean[1];
		monitoringAgent.setOperatorCommandSink(new _TestCommandSink()
		{
			@Override
			public void submitEntityMutation(int clientId, IMutationEntity<IMutablePlayerEntity> command)
			{
				Assert.assertEquals(123, clientId);
				Assert.assertNotNull(command);
				didCheck[0] = true;
			}
		});
		ConsoleHandler.readUntilStop(in, printer, monitoringAgent, new WorldConfig());
		Assert.assertArrayEquals("Shutting down...\n".getBytes(), out.toByteArray());
		Assert.assertTrue(didCheck[0]);
	}

	@Test
	public void setDayLength() throws Throwable
	{
		// Just verify that the call is sent.
		InputStream in = new ByteArrayInputStream("!set_day_length 100\n!stop\n".getBytes());
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PrintStream printer = new PrintStream(out);
		MonitoringAgent monitoringAgent = new MonitoringAgent();
		boolean[] didBroadcast = new boolean[1];
		monitoringAgent.setOperatorCommandSink(new _TestCommandSink()
		{
			@Override
			public void requestConfigBroadcast()
			{
				didBroadcast[0] = true;
			}
		});
		ConsoleHandler.readUntilStop(in, printer, monitoringAgent, new WorldConfig());
		Assert.assertArrayEquals("Shutting down...\n".getBytes(), out.toByteArray());
		Assert.assertTrue(didBroadcast[0]);
	}

	@Test
	public void messageBehaviour() throws Throwable
	{
		// The basic use-case where we just stop, inline.
		InputStream in = new ByteArrayInputStream(("!message 1 one message\n"
				+ "!message 2 two message\n"
				+ "!stop\n"
		).getBytes());
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PrintStream printer = new PrintStream(out);
		MonitoringAgent monitoringAgent = new MonitoringAgent();
		monitoringAgent.clientConnected(1, null, "Client");
		String[] outMessage = new String[1];
		monitoringAgent.setOperatorCommandSink(new _TestCommandSink()
		{
			@Override
			public void sendChatMessage(int targetId, String message)
			{
				Assert.assertEquals(1, targetId);
				Assert.assertNull(outMessage[0]);
				outMessage[0] = message;
			}
		});
		ConsoleHandler.readUntilStop(in, printer, monitoringAgent, new WorldConfig());
		Assert.assertArrayEquals("Message to Client: one message\nUsage:  <target_id> message...\nShutting down...\n".getBytes(), out.toByteArray());
		Assert.assertEquals("one message", outMessage[0]);
	}

	@Test
	public void broadcastCollapseParameters() throws Throwable
	{
		// The basic use-case where we just stop, inline.
		InputStream in = new ByteArrayInputStream("!broadcast one two      three\n!stop\n".getBytes());
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PrintStream printer = new PrintStream(out);
		MonitoringAgent monitoringAgent = new MonitoringAgent();
		String[] outMessage = new String[1];
		monitoringAgent.setOperatorCommandSink(new _TestCommandSink()
		{
			@Override
			public void sendChatMessage(int targetId, String message)
			{
				Assert.assertEquals(0, targetId);
				Assert.assertNull(outMessage[0]);
				outMessage[0] = message;
			}
		});
		ConsoleHandler.readUntilStop(in, printer, monitoringAgent, new WorldConfig());
		Assert.assertArrayEquals("Broadcast: one two three\nShutting down...\n".getBytes(), out.toByteArray());
		Assert.assertEquals("one two three", outMessage[0]);
	}

	@Test
	public void resetDay() throws Throwable
	{
		// Verify that the config is appropriately changed and the broadcast is requested.
		InputStream in = new ByteArrayInputStream("!reset_day\n!stop\n".getBytes());
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PrintStream printer = new PrintStream(out);
		MonitoringAgent monitoringAgent = new MonitoringAgent();
		long tickNumber = 50L;
		monitoringAgent.snapshotPublished(new TickRunner.Snapshot(tickNumber, null, null, null, null, null, null, null, null, null, null, null, null));
		boolean[] didBroadcast = new boolean[1];
		monitoringAgent.setOperatorCommandSink(new _TestCommandSink()
		{
			@Override
			public void requestConfigBroadcast()
			{
				didBroadcast[0] = true;
			}
		});
		WorldConfig config = new WorldConfig();
		config.dayStartTick = 5;
		config.ticksPerDay = 40;
		ConsoleHandler.readUntilStop(in, printer, monitoringAgent, config);
		Assert.assertArrayEquals("Shutting down...\n".getBytes(), out.toByteArray());
		Assert.assertTrue(didBroadcast[0]);
		Assert.assertEquals(30, config.dayStartTick);
	}


	// Since these tests usually just want to test a single callback, this is provided so they can override a failing implementation.
	private static class _TestCommandSink implements MonitoringAgent.OperatorCommandSink
	{
		@Override
		public void submitEntityMutation(int clientId, IMutationEntity<IMutablePlayerEntity> command)
		{
			throw new AssertionError("submitEntityMutation");
		}
		@Override
		public void requestConfigBroadcast()
		{
			throw new AssertionError("requestConfigBroadcast");
		}
		@Override
		public void sendChatMessage(int targetId, String message)
		{
			throw new AssertionError("sendChatMessage");
		}
		@Override
		public void installSampler(MonitoringAgent.Sampler sampler)
		{
			throw new AssertionError("installSampler");
		}
		@Override
		public void pauseTickProcessing()
		{
			throw new AssertionError("pauseTickProcessing");
		}
		@Override
		public void resumeTickProcessing()
		{
			throw new AssertionError("resumeTickProcessing");
		}
	}
}
