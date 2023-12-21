package com.jeffdisher.october.integration;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.client.ClientRunner;
import com.jeffdisher.october.client.SpeculativeProjection;
import com.jeffdisher.october.client.ClientRunner.IListener;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.server.CuboidGenerator;
import com.jeffdisher.october.server.ServerRunner;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;


public class TestIntegrationRunners
{
	@Test
	public void singleClientJoin() throws Throwable
	{
		// We want to create a server with a single cuboid, connect a client to it, and observe that the client sees everything.
		ConnectionFabric fabric = new ConnectionFabric();
		ServerRunner server = new ServerRunner(100L, fabric, () -> System.currentTimeMillis());
		fabric.waitForServer();
		
		// Create and load the cuboid full of stone with no inventories.
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ItemRegistry.STONE);
		server.loadCuboid(cuboid);
		// We want to wait a tick for this cuboid to be picked up.
		fabric.waitForTick(0, fabric.getLatestTick(0) + 1L);
		
		// Create a client and connect them to the server.
		ClientListener listener = new ClientListener();
		ClientRunner client = new ClientRunner(fabric.newClient(), listener, listener);
		// We know that this first client will get ID 1.
		int clientId1 = 1;
		fabric.waitForClient(clientId1);
		client.runPendingCalls(System.currentTimeMillis());
		
		// Wait until we see ourself and the cuboid where we are standing (we expect this within 4 ticks).
		fabric.waitForTick(clientId1, fabric.getLatestTick(clientId1) + 4L);
		client.runPendingCalls(System.currentTimeMillis());
		Assert.assertNotNull(listener.entities.get(1));
		Assert.assertNotNull(listener.cuboids.get(cuboid.getCuboidAddress()));
		
		// We are done.
		server.shutdown();
	}

	@Test
	public void twoClientsMove() throws Throwable
	{
		// We want to create a server with a single cuboid, connect a client to it, and observe that the client sees everything.
		ConnectionFabric fabric = new ConnectionFabric();
		// On the server, we will use real time, but the clients are directly controllable with a local time variable.
		long currentTimeMillis = 0L;
		ServerRunner server = new ServerRunner(100L, fabric, () -> System.currentTimeMillis());
		fabric.waitForServer();
		
		// Create and load the cuboid full of stone with no inventories.
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ItemRegistry.STONE);
		server.loadCuboid(cuboid);
		// We want to wait a tick for this cuboid to be picked up.
		fabric.waitForTick(0, fabric.getLatestTick(0) + 1L);
		
		// Create the first client.
		ClientListener listener1 = new ClientListener();
		ClientRunner client1 = new ClientRunner(fabric.newClient(), listener1, listener1);
		// We know that this first client will get ID 1.
		int clientId1 = 1;
		fabric.waitForClient(clientId1);
		client1.runPendingCalls(currentTimeMillis);
		currentTimeMillis += 100L;
		
		// Create the second client.
		ClientListener listener2 = new ClientListener();
		ClientRunner client2 = new ClientRunner(fabric.newClient(), listener2, listener2);
		int clientId2 = 2;
		fabric.waitForClient(clientId2);
		client2.runPendingCalls(currentTimeMillis);
		currentTimeMillis += 100L;
		
		// Wait until both of the clients have observed both entities for the first time.
		while (2 != listener1.entities.size())
		{
			fabric.waitForTick(clientId1, fabric.getLatestTick(clientId1) + 1L);
			client1.runPendingCalls(currentTimeMillis);
			currentTimeMillis += 100L;
		}
		while (2 != listener2.entities.size())
		{
			fabric.waitForTick(clientId2, fabric.getLatestTick(clientId2) + 1L);
			client2.runPendingCalls(currentTimeMillis);
			currentTimeMillis += 100L;
		}
		
		// Both of these clients need to move and then we can verify that both sides can see the correct outcome.
		// (we want this to be multiple steps, so we queue those up now)
		EntityLocation startLocation = listener1.entities.get(clientId1).location();
		EntityLocation location1 = startLocation;
		EntityLocation location2 = startLocation;
		for (int i = 0; i < 5; ++i)
		{
			// We can walk 0.4 horizontally in a single tick.
			location1 = new EntityLocation(location1.x() + 0.4f, location1.y(), location1.z());
			location2 = new EntityLocation(location2.x(), location2.y() - 0.4f, location2.z());
			client1.moveTo(location1, currentTimeMillis);
			client2.moveTo(location2, currentTimeMillis);
			currentTimeMillis += 100L;
		}
		// We want client2 to walk further.
		for (int i = 0; i < 5; ++i)
		{
			location2 = new EntityLocation(location2.x(), location2.y() - 0.4f, location2.z());
			client2.moveTo(location2, currentTimeMillis);
			currentTimeMillis += 100L;
		}
		
		// The client flushes network operations after receiving end of tick, so let at least one tick pass for both so they flush.
		fabric.waitForTick(clientId2, fabric.getLatestTick(clientId2) + 1L);
		fabric.waitForTick(clientId1, fabric.getLatestTick(clientId1) + 1L);
		client1.runPendingCalls(currentTimeMillis);
		client2.runPendingCalls(currentTimeMillis);
		currentTimeMillis += 100L;
		
		// Because this will always leave one tick of slack on the line, consume it before counting the ticks to see our changes.
		fabric.waitForTick(clientId2, fabric.getLatestTick(clientId2) + 1L);
		fabric.waitForTick(clientId1, fabric.getLatestTick(clientId1) + 1L);
		client1.runPendingCalls(currentTimeMillis);
		client2.runPendingCalls(currentTimeMillis);
		currentTimeMillis += 100L;
		
		// We expect the first client to take an extra 5 ticks to be processed while the second takes a further 5.
		fabric.waitForTick(clientId2, fabric.getLatestTick(clientId2) + 5L);
		fabric.waitForTick(clientId1, fabric.getLatestTick(clientId1) + 5L);
		client1.runPendingCalls(currentTimeMillis);
		client2.runPendingCalls(currentTimeMillis);
		currentTimeMillis += 100L;
		Assert.assertEquals(location2, listener1.entities.get(clientId2).location());
		Assert.assertEquals(location2, listener2.entities.get(clientId2).location());
		
		fabric.waitForTick(clientId2, fabric.getLatestTick(clientId2) + 5L);
		fabric.waitForTick(clientId1, fabric.getLatestTick(clientId1) + 5L);
		client1.runPendingCalls(currentTimeMillis);
		client2.runPendingCalls(currentTimeMillis);
		currentTimeMillis += 100L;
		Assert.assertEquals(location1, listener1.entities.get(clientId1).location());
		Assert.assertEquals(location1, listener2.entities.get(clientId1).location());
		
		// We are done.
		server.shutdown();
	}


	private final static class ClientListener implements SpeculativeProjection.IProjectionListener, IListener
	{
		public final Map<CuboidAddress, IReadOnlyCuboidData> cuboids = new HashMap<>();
		public final Map<Integer, Entity> entities = new HashMap<>();
		
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
			Object old = this.entities.put(entity.id(), entity);
			Assert.assertNull(old);
		}
		@Override
		public void entityDidChange(Entity entity)
		{
			Object old = this.entities.put(entity.id(), entity);
			Assert.assertNotNull(old);
		}
		@Override
		public void entityDidUnload(int id)
		{
			Object old = this.entities.remove(id);
			Assert.assertNotNull(old);
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
