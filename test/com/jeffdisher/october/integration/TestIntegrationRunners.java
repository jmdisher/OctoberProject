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
		client1.runPendingCalls(System.currentTimeMillis());
		
		// Create the second client.
		ClientListener listener2 = new ClientListener();
		ClientRunner client2 = new ClientRunner(fabric.newClient(), listener2, listener2);
		int clientId2 = 2;
		fabric.waitForClient(clientId2);
		client2.runPendingCalls(System.currentTimeMillis());
		
		// Wait until both of the clients have observed both entities for the first time.
		while (2 != listener1.entities.size())
		{
			fabric.waitForTick(clientId1, fabric.getLatestTick(clientId1) + 1L);
			client1.runPendingCalls(System.currentTimeMillis());
		}
		while (2 != listener2.entities.size())
		{
			fabric.waitForTick(clientId2, fabric.getLatestTick(clientId2) + 1L);
			client2.runPendingCalls(System.currentTimeMillis());
		}
		
		// Both of these clients need to move and then we can verify that both sides can see the correct outcome.
		EntityLocation location1 = new EntityLocation(1.0f, 1.0f, 1.0f);
		EntityLocation location2 = new EntityLocation(-1.0f, -1.0f, -1.0f);
		client1.moveTo(location1, System.currentTimeMillis());
		client2.moveTo(location2, System.currentTimeMillis());
		
		// The client flushes network operations after receiving end of tick, so let at least one tick pass for both so they flush.
		fabric.waitForTick(clientId2, fabric.getLatestTick(clientId2) + 1L);
		fabric.waitForTick(clientId1, fabric.getLatestTick(clientId1) + 1L);
		client1.runPendingCalls(System.currentTimeMillis());
		client2.runPendingCalls(System.currentTimeMillis());
		
		// We expect that client1 will take 1.0 seconds and client2 will take 0.45 to reach their destinations so wait for 10 or 5 ticks to make sure these passed.
		fabric.waitForTick(clientId2, fabric.getLatestTick(clientId2) + 5L);
		fabric.waitForTick(clientId1, fabric.getLatestTick(clientId1) + 5L);
		client1.runPendingCalls(System.currentTimeMillis());
		client2.runPendingCalls(System.currentTimeMillis());
		Assert.assertEquals(location2, listener1.entities.get(clientId2).location());
		Assert.assertEquals(location2, listener2.entities.get(clientId2).location());
		
		fabric.waitForTick(clientId2, fabric.getLatestTick(clientId2) + 5L);
		fabric.waitForTick(clientId1, fabric.getLatestTick(clientId1) + 5L);
		client1.runPendingCalls(System.currentTimeMillis());
		client2.runPendingCalls(System.currentTimeMillis());
		Assert.assertEquals(location1, listener1.entities.get(clientId1).location());
		Assert.assertEquals(location1, listener2.entities.get(clientId1).location());
		
		// We are done.
		server.shutdown();
	}

	@Test
	public void walkAroundWorld555() throws Throwable
	{
		System.out.println("WARNING:  This test uses real-time so will drop many ticks and take nearly 40 seconds to complete");
		// We want to create a server with the 555 world, connect a client to it, walk to the 4 corners of the world.
		ConnectionFabric fabric = new ConnectionFabric();
		long[] currentTimeMillis = new long[] { 0L };
		ServerRunner server = new ServerRunner(100L, fabric, () -> {
			// We will increment time each time we are asked, to simulate time passing.
			currentTimeMillis[0] += 100L;
			return currentTimeMillis[0];
		});
		fabric.waitForServer();
		
		// Create and load the 555 world and wait for the server to pick it up.
		CuboidData[] world = CuboidGenerator.generateStatic555World();
		for (CuboidData cuboid : world)
		{
			server.loadCuboid(cuboid);
		}
		fabric.waitForTick(0, fabric.getLatestTick(0) + 1L);
		
		// Create and connect the client, waiting for it to load.
		ClientListener listener = new ClientListener();
		ClientRunner client = new ClientRunner(fabric.newClient(), listener, listener);
		int clientId = 1;
		fabric.waitForClient(clientId);
		client.runPendingCalls(currentTimeMillis[0]);
		while (listener.entities.isEmpty())
		{
			fabric.waitForTick(clientId, fabric.getLatestTick(clientId) + 1L);
			currentTimeMillis[0] += 100L;
			client.runPendingCalls(currentTimeMillis[0]);
		}
		
		// Start the move commands, waiting until we see the client reach that point before continuing.
		// (this will need to change once the movement change is imposing speed and reachability checks).
		EntityLocation cornerMM = new EntityLocation(-32.0f, -32.0f, 0.0f);
		client.moveTo(cornerMM, currentTimeMillis[0]);
		while (!listener.entities.get(clientId).location().equals(cornerMM))
		{
			fabric.waitForTick(clientId, fabric.getLatestTick(clientId) + 1L);
			currentTimeMillis[0] += 100L;
			client.runPendingCalls(currentTimeMillis[0]);
		}
		EntityLocation cornerMP = new EntityLocation(-32.0f, 47.0f, 0.0f);
		client.moveTo(cornerMP, currentTimeMillis[0]);
		while (!listener.entities.get(clientId).location().equals(cornerMP))
		{
			fabric.waitForTick(clientId, fabric.getLatestTick(clientId) + 1L);
			currentTimeMillis[0] += 100L;
			client.runPendingCalls(currentTimeMillis[0]);
		}
		EntityLocation cornerPP = new EntityLocation(47.0f, 47.0f, 0.0f);
		client.moveTo(cornerPP, currentTimeMillis[0]);
		while (!listener.entities.get(clientId).location().equals(cornerPP))
		{
			fabric.waitForTick(clientId, fabric.getLatestTick(clientId) + 1L);
			currentTimeMillis[0] += 100L;
			client.runPendingCalls(currentTimeMillis[0]);
		}
		EntityLocation cornerPM = new EntityLocation(47.0f, -32.0f, 0.0f);
		client.moveTo(cornerPM, currentTimeMillis[0]);
		while (!listener.entities.get(clientId).location().equals(cornerPM))
		{
			fabric.waitForTick(clientId, fabric.getLatestTick(clientId) + 1L);
			currentTimeMillis[0] += 100L;
			client.runPendingCalls(currentTimeMillis[0]);
		}
		
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
