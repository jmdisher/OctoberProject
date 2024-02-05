package com.jeffdisher.october.integration;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.process.ClientProcess;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.server.ServerRunner;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.worldgen.CuboidGenerator;


public class TestIntegrationRunners
{
	@Test
	public void singleClientJoin() throws Throwable
	{
		// We want to create a server with a single cuboid, connect a client to it, and observe that the client sees everything.
		long currentTimeMillis = 1000L;
		ConnectionFabric fabric = new ConnectionFabric(() -> currentTimeMillis);
		
		// Create and load the cuboid full of stone with no inventories.
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ItemRegistry.STONE);
		fabric.server().loadCuboid(cuboid);
		
		// Connect a client and wait to receive their entity.
		ClientListener listener = new ClientListener();
		ClientProcess client = fabric.newClient(listener);
		long tick = client.waitForLocalEntity(currentTimeMillis);
		// Now, wait for a tick to make sure that the cuboid is loaded.
		client.waitForTick(tick + 1, currentTimeMillis);
		
		client.runPendingCalls(currentTimeMillis);
		Assert.assertNotNull(listener.entities.get(1));
		Assert.assertNotNull(listener.cuboids.get(cuboid.getCuboidAddress()));
		
		// We are done.
		client.disconnect();
		fabric.server().stop();
	}

	@Test
	public void twoClientsMove() throws Throwable
	{
		// We want to create a server with a single cuboid, connect a client to it, and observe that the client sees everything.
		long[] currentTimeMillis = new long[] { 1000L };
		ConnectionFabric fabric = new ConnectionFabric(() -> currentTimeMillis[0]);
		
		// Create and load the cuboids full of air (so we can walk through them) with no inventories.
		fabric.server().loadCuboid(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short) 0, (short)0), ItemRegistry.AIR));
		fabric.server().loadCuboid(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)-1, (short)0), ItemRegistry.AIR));
		
		// Create the first client.
		ClientListener listener1 = new ClientListener();
		ClientProcess client1 = fabric.newClient(listener1);
		int clientId1 = client1.waitForClientId();
		
		// Create the second client.
		ClientListener listener2 = new ClientListener();
		ClientProcess client2 = fabric.newClient(listener2);
		int clientId2 = client2.waitForClientId();
		
		// Wait until both of the clients have observed both entities for the first time.
		long serverTickNumber = fabric.server().waitForTicksToPass(1L);
		while (2 != listener1.entities.size())
		{
			client1.waitForTick(serverTickNumber, currentTimeMillis[0]);
			currentTimeMillis[0] += ServerRunner.DEFAULT_MILLIS_PER_TICK;
		}
		while (2 != listener2.entities.size())
		{
			client2.waitForTick(serverTickNumber, currentTimeMillis[0]);
			currentTimeMillis[0] += ServerRunner.DEFAULT_MILLIS_PER_TICK;
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
			Assert.assertFalse(client1.isActivityInProgress(currentTimeMillis[0]));
			client1.moveHorizontal(0.4f, 0.0f, currentTimeMillis[0]);
			Assert.assertFalse(client2.isActivityInProgress(currentTimeMillis[0]));
			client2.moveHorizontal(0.0f, -0.4f, currentTimeMillis[0]);
			currentTimeMillis[0] += ServerRunner.DEFAULT_MILLIS_PER_TICK;
		}
		// We want client2 to walk further.
		for (int i = 0; i < 5; ++i)
		{
			location2 = new EntityLocation(location2.x(), location2.y() - 0.4f, location2.z());
			Assert.assertFalse(client2.isActivityInProgress(currentTimeMillis[0]));
			client2.moveHorizontal(0.0f, -0.4f, currentTimeMillis[0]);
			currentTimeMillis[0] += ServerRunner.DEFAULT_MILLIS_PER_TICK;
		}
		
		// The client flushes network operations after receiving end of tick, so let at least one tick pass for both so they flush.
		serverTickNumber = fabric.server().waitForTicksToPass(1L);
		client1.waitForTick(serverTickNumber, currentTimeMillis[0]);
		client2.waitForTick(serverTickNumber, currentTimeMillis[0]);
		currentTimeMillis[0] += ServerRunner.DEFAULT_MILLIS_PER_TICK;
		
		// Because this will always leave one tick of slack on the line, consume it before counting the ticks to see our changes.
		serverTickNumber = fabric.server().waitForTicksToPass(1L);
		client1.waitForTick(serverTickNumber, currentTimeMillis[0]);
		client2.waitForTick(serverTickNumber, currentTimeMillis[0]);
		currentTimeMillis[0] += ServerRunner.DEFAULT_MILLIS_PER_TICK;
		
		// We expect the first client to take an extra 5 ticks to be processed while the second takes a further 5.
		serverTickNumber = fabric.server().waitForTicksToPass(5L);
		client1.waitForTick(serverTickNumber, currentTimeMillis[0]);
		client2.waitForTick(serverTickNumber, currentTimeMillis[0]);
		currentTimeMillis[0] += ServerRunner.DEFAULT_MILLIS_PER_TICK;
		Assert.assertEquals(location1, listener1.entities.get(clientId1).location());
		Assert.assertEquals(location1, listener2.entities.get(clientId1).location());
		
		serverTickNumber = fabric.server().waitForTicksToPass(5L);
		client1.waitForTick(serverTickNumber, currentTimeMillis[0]);
		client2.waitForTick(serverTickNumber, currentTimeMillis[0]);
		currentTimeMillis[0] += ServerRunner.DEFAULT_MILLIS_PER_TICK;
		Assert.assertEquals(location2, listener1.entities.get(clientId2).location());
		Assert.assertEquals(location2, listener2.entities.get(clientId2).location());
		
		// We are done.
		client1.disconnect();
		client2.disconnect();
		fabric.server().stop();
	}


	private final static class ClientListener implements ClientProcess.IListener
	{
		public final Map<CuboidAddress, IReadOnlyCuboidData> cuboids = new HashMap<>();
		public final Map<Integer, Entity> entities = new HashMap<>();
		
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
	}
}
