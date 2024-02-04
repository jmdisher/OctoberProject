package com.jeffdisher.october.integration;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.mutations.EntityChangeMove;
import com.jeffdisher.october.process.ClientProcess;
import com.jeffdisher.october.process.ServerProcess;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.worldgen.CuboidGenerator;


/**
 * Tests for the pairing of ServerProcess and ClientProcess.
 */
public class TestProcesses
{
	public static final int PORT = 5678;
	public static final long MILLIS_PER_TICK = 100L;
	public static final LongSupplier TIME_SUPPLIER = () -> System.currentTimeMillis();

	@Test
	public void startStopServer() throws Throwable
	{
		ServerProcess server = new ServerProcess(PORT, MILLIS_PER_TICK, TIME_SUPPLIER);
		server.stop();
	}

	@Test(expected = IOException.class)
	public void noConnectClient() throws Throwable
	{
		_ClientListener listener = new _ClientListener();
		new ClientProcess(listener, InetAddress.getLocalHost(), PORT, "test");
	}

	@Test
	public void startStop() throws Throwable
	{
		// Start everything, connect and disconnect once the see the entity arrive.
		ServerProcess server = new ServerProcess(PORT, MILLIS_PER_TICK, TIME_SUPPLIER);
		_ClientListener listener = new _ClientListener();
		ClientProcess client = new ClientProcess(listener, InetAddress.getLocalHost(), PORT, "test");
		
		// Wait until we see the entity arrive.
		client.waitForTickAdvance(3L, System.currentTimeMillis());
		listener.waitForEntity();
		
		// Disconnect the client and shut down the server.
		client.disconnect();
		server.stop();
	}

	@Test
	public void basicMovement() throws Throwable
	{
		// Demonstrate that a client can move around the server without issue.
		ServerProcess server = new ServerProcess(PORT, MILLIS_PER_TICK, TIME_SUPPLIER);
		
		// Load a cuboid.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ItemRegistry.AIR);
		server.loadCuboid(cuboid);
		
		// Connect the client.
		_ClientListener listener = new _ClientListener();
		ClientProcess client = new ClientProcess(listener, InetAddress.getLocalHost(), PORT, "test");
		
		// Let some time pass and verify the data is loaded.
		client.waitForTickAdvance(3L, System.currentTimeMillis());
		Assert.assertNotNull(listener.entity);
		Assert.assertEquals(1, listener.cuboids.size());
		
		// Move the client, slightly, and verify that we see the update.
		// (since we are using the real clock, wait for this move to be valid)
		EntityLocation newLocation = new EntityLocation(0.4f, 0.0f, 0.0f);
		Thread.sleep(EntityChangeMove.getTimeMostMillis(0.4f, 0.0f));
		client.moveHorizontal(0.4f, 0.0f, System.currentTimeMillis());
		client.waitForTickAdvance(2L, System.currentTimeMillis());
		Assert.assertEquals(newLocation, listener.entity.location());
		
		// Disconnect the client.
		client.disconnect();
		server.stop();
	}

	@Test
	public void falling() throws Throwable
	{
		// Demonstrate that a client will fall through air and this will make sense in the projection.
		ServerProcess server = new ServerProcess(PORT, MILLIS_PER_TICK, TIME_SUPPLIER);
		
		// Load a cuboids.
		server.loadCuboid(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short) 0), ItemRegistry.AIR));
		server.loadCuboid(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)-1), ItemRegistry.AIR));
		
		// Connect the client.
		_ClientListener listener = new _ClientListener();
		ClientProcess client = new ClientProcess(listener, InetAddress.getLocalHost(), PORT, "test");
		
		// Let some time pass and verify the data is loaded.
		client.waitForTickAdvance(3L, System.currentTimeMillis());
		Assert.assertNotNull(listener.entity);
		Assert.assertEquals(2, listener.cuboids.size());
		
		// Wait a few ticks and verify that they are below the starting location.
		// Empty move changes allow us to account for falling in a way that the client controls (avoids synchronized writers over the network).
		Thread.sleep(100L);
		client.doNothing(System.currentTimeMillis());
		// (we need to do 2 since the first move starts falling and the second will actually do the fall)
		Thread.sleep(100L);
		client.doNothing(System.currentTimeMillis());
		
		client.waitForTickAdvance(2L, System.currentTimeMillis());
		Assert.assertEquals(0.0f, listener.entity.location().x(), 0.01f);
		Assert.assertEquals(0.0f, listener.entity.location().y(), 0.01f);
		Assert.assertTrue(listener.entity.location().z() <= -0.098);
		
		// Disconnect the client.
		client.disconnect();
		server.stop();
	}


	private static class _ClientListener implements ClientProcess.IListener
	{
		public final Map<CuboidAddress, IReadOnlyCuboidData> cuboids = new HashMap<>();
		public Entity entity;
		
		public synchronized void waitForEntity() throws InterruptedException
		{
			while (null == this.entity)
			{
				this.wait();
			}
		}
		@Override
		public void connectionEstablished(int assignedEntityId)
		{
		}
		@Override
		public void connectionClosed()
		{
		}
		@Override
		public void cuboidDidLoad(IReadOnlyCuboidData cuboid)
		{
			Object old = this.cuboids.put(cuboid.getCuboidAddress(), cuboid);
			Assert.assertNull(old);
		}
		@Override
		public void cuboidDidChange(IReadOnlyCuboidData cuboid)
		{
			Object old = this.cuboids.put(cuboid.getCuboidAddress(), cuboid);
			Assert.assertNotNull(old);
		}
		@Override
		public void cuboidDidUnload(CuboidAddress address)
		{
			Object old = this.cuboids.remove(address);
			Assert.assertNotNull(old);
		}
		@Override
		public synchronized void entityDidLoad(Entity entity)
		{
			Assert.assertNull(this.entity);
			this.entity = entity;
			this.notifyAll();
		}
		@Override
		public void entityDidChange(Entity entity)
		{
			Assert.assertNotNull(this.entity);
			this.entity = entity;
		}
		@Override
		public void entityDidUnload(int id)
		{
		}
	}
}
