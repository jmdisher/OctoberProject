package com.jeffdisher.october.integration;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.client.ClientRunner;
import com.jeffdisher.october.client.SpeculativeProjection;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.server.ServerRunner;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.worldgen.CuboidGenerator;


public class TestLocalServerShim
{
	@Test
	public void startStop() throws Throwable
	{
		// Just start and stop the shim.
		LocalServerShim shim = LocalServerShim.startedServerShim(ServerRunner.DEFAULT_MILLIS_PER_TICK, () -> System.currentTimeMillis());
		
		// Connect the client.
		ClientListener listener = new ClientListener();
		ClientRunner client = new ClientRunner(shim.getClientAdapter(), listener, listener);
		shim.waitForClient();
		
		// Let some time pass and verify the entity is loaded.
		shim.waitForTickAdvance(3L);
		client.runPendingCalls(System.currentTimeMillis());
		Assert.assertNotNull(listener.entity);
		
		// Disconnect the client.
		client.disconnect();
		shim.waitForServerShutdown();
	}

	@Test
	public void basicMovement() throws Throwable
	{
		// Demonstrate that a client can move around the server without issue.
		LocalServerShim shim = LocalServerShim.startedServerShim(ServerRunner.DEFAULT_MILLIS_PER_TICK, () -> System.currentTimeMillis());
		
		// Load a cuboid.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ItemRegistry.AIR);
		shim.injectCuboidToServer(cuboid);
		shim.waitForTickAdvance(1L);
		
		// Connect the client.
		ClientListener listener = new ClientListener();
		ClientRunner client = new ClientRunner(shim.getClientAdapter(), listener, listener);
		shim.waitForClient();
		
		// Let some time pass and verify the data is loaded.
		shim.waitForTickAdvance(3L);
		client.runPendingCalls(System.currentTimeMillis());
		Assert.assertNotNull(listener.entity);
		Assert.assertEquals(1, listener.cuboids.size());
		
		// Move the client, slightly, and verify that we see the update.
		EntityLocation newLocation = new EntityLocation(0.4f, 0.0f, 0.0f);
		client.moveTo(newLocation, System.currentTimeMillis());
		shim.waitForTickAdvance(2L);
		client.runPendingCalls(System.currentTimeMillis());
		Assert.assertEquals(newLocation, listener.entity.location());
		
		// Disconnect the client.
		client.disconnect();
		shim.waitForServerShutdown();
	}

	@Test
	public void falling() throws Throwable
	{
		// Demonstrate that a client will fall through air and this will make sense in the projection.
		LocalServerShim shim = LocalServerShim.startedServerShim(ServerRunner.DEFAULT_MILLIS_PER_TICK, () -> System.currentTimeMillis());
		
		// Load a cuboids.
		shim.injectCuboidToServer(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short) 0), ItemRegistry.AIR));
		shim.injectCuboidToServer(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)-1), ItemRegistry.AIR));
		shim.waitForTickAdvance(1L);
		
		// Connect the client.
		ClientListener listener = new ClientListener();
		ClientRunner client = new ClientRunner(shim.getClientAdapter(), listener, listener);
		shim.waitForClient();
		
		// Let some time pass and verify the data is loaded.
		shim.waitForTickAdvance(3L);
		client.runPendingCalls(System.currentTimeMillis());
		Assert.assertNotNull(listener.entity);
		Assert.assertEquals(2, listener.cuboids.size());
		
		// Wait a few ticks and verify that they are below the starting location.
		shim.waitForTickAdvance(2L);
		client.runPendingCalls(System.currentTimeMillis());
		Assert.assertEquals(0.0f, listener.entity.location().x(), 0.01f);
		Assert.assertEquals(0.0f, listener.entity.location().y(), 0.01f);
		Assert.assertTrue(listener.entity.location().z() < -2.0f);
		
		// Disconnect the client.
		client.disconnect();
		shim.waitForServerShutdown();
	}


	private final static class ClientListener implements SpeculativeProjection.IProjectionListener, ClientRunner.IListener
	{
		public final Map<CuboidAddress, IReadOnlyCuboidData> cuboids = new HashMap<>();
		public Entity entity;
		
		// SpeculativeProjection.IProjectionListener
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
		public void entityDidLoad(Entity entity)
		{
			Assert.assertNull(this.entity);
			this.entity = entity;
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
			Assert.assertEquals(this.entity.id(), id);
			this.entity = null;
		}
		
		// IListener
		@Override
		public void clientDidConnectAndLogin(int assignedLocalEntityId)
		{
		}
		@Override
		public void clientDisconnected()
		{
		}
	}
}
