package com.jeffdisher.october.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.changes.BeginBreakBlockChange;
import com.jeffdisher.october.changes.EndBreakBlockChange;
import com.jeffdisher.october.changes.IEntityChange;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.mutations.BreakBlockMutation;
import com.jeffdisher.october.mutations.IMutation;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;


public class TestServerRunner
{
	@Test
	public void startStop() throws Throwable
	{
		TestAdapter network = new TestAdapter();
		ServerRunner runner = new ServerRunner(100L, network, () -> System.currentTimeMillis());
		runner.shutdown();
	}

	@Test
	public void twoClients() throws Throwable
	{
		// Just connect two clients and verify that they see themselves and each other.
		TestAdapter network = new TestAdapter();
		ServerRunner runner = new ServerRunner(100L, network, () -> System.currentTimeMillis());
		_loadDefaultMap(runner);
		IServerAdapter.IListener server = network.waitForServer(1);
		int clientId1 = 1;
		int clientId2 = 2;
		network.prepareForClient(clientId1);
		network.prepareForClient(clientId2);
		server.clientConnected(clientId1);
		server.clientConnected(clientId2);
		Entity entity1_1 = network.waitForEntity(clientId1, clientId1);
		Assert.assertNotNull(entity1_1);
		Entity entity1_2 = network.waitForEntity(clientId1, clientId2);
		Assert.assertNotNull(entity1_2);
		Entity entity2_1 = network.waitForEntity(clientId2, clientId1);
		Assert.assertNotNull(entity2_1);
		Entity entity2_2 = network.waitForEntity(clientId2, clientId2);
		Assert.assertNotNull(entity2_2);
		server.clientDisconnected(1);
		server.clientDisconnected(2);
		runner.shutdown();
	}

	@Test
	public void multiPhase() throws Throwable
	{
		TestAdapter network = new TestAdapter();
		ServerRunner runner = new ServerRunner(100L, network, () -> System.currentTimeMillis());
		_loadDefaultMap(runner);
		IServerAdapter.IListener server = network.waitForServer(1);
		int clientId = 1;
		network.prepareForClient(clientId);
		server.clientConnected(clientId);
		Entity entity = network.waitForEntity(clientId, clientId);
		Assert.assertNotNull(entity);
		
		// Submit the first phase and wait to observe the output 2 changes and 1 mutation.
		AbsoluteLocation changeLocation = new AbsoluteLocation(0, 0, 0);
		BeginBreakBlockChange firstPhase = new BeginBreakBlockChange(changeLocation);
		server.changeReceived(clientId, firstPhase, 1, true);
		
		// Wait for the output and verify it is what is expected.
		Object phase1 = network.waitForUpdate(clientId, 0);
		Assert.assertTrue(firstPhase == phase1);
		Object phase2 = network.waitForUpdate(clientId, 1);
		Assert.assertTrue(phase2 instanceof EndBreakBlockChange);
		Object mutation = network.waitForUpdate(clientId, 2);
		Assert.assertTrue(mutation instanceof BreakBlockMutation);
		
		runner.shutdown();
	}


	private static void _loadDefaultMap(ServerRunner runner)
	{
		// Create and load the cuboid full of stone with no inventories.
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ItemRegistry.STONE);
		runner.loadCuboid(cuboid);
	}


	private static class TestAdapter implements IServerAdapter
	{
		public IServerAdapter.IListener server;
		public final Map<Integer, Map<Integer, Entity>> clientEntities = new HashMap<>();
		public final Map<Integer, List<Object>> clientUpdates = new HashMap<>();
		public long lastTick = 0L;
		
		@Override
		public synchronized void readyAndStartListening(IServerAdapter.IListener listener)
		{
			Assert.assertNull(this.server);
			this.server = listener;
			this.notifyAll();
		}
		@Override
		public synchronized void sendEntity(int clientId, Entity entity)
		{
			int entityId = entity.id();
			Map<Integer, Entity> clientMap = this.clientEntities.get(clientId);
			Assert.assertFalse(clientMap.containsKey(entityId));
			clientMap.put(entityId, entity);
			this.notifyAll();
		}
		@Override
		public synchronized void sendCuboid(int clientId, IReadOnlyCuboidData cuboid)
		{
		}
		@Override
		public synchronized void sendChange(int clientId, int entityId, IEntityChange change)
		{
			List<Object> updates = this.clientUpdates.get(clientId);
			updates.add(change);
			this.notifyAll();
		}
		@Override
		public synchronized void sendMutation(int clientId, IMutation mutation)
		{
			List<Object> updates = this.clientUpdates.get(clientId);
			updates.add(mutation);
			this.notifyAll();
		}
		@Override
		public synchronized void sendEndOfTick(int clientId, long tickNumber, long latestLocalCommitIncluded, long latestLocalActivityIncluded)
		{
			// We want to track the progress of the server, no matter who is connected.
			if (ServerRunner.FAKE_CLIENT_ID == clientId)
			{
				this.lastTick = tickNumber;
				this.notifyAll();
			}
		}
		@Override
		public synchronized void disconnectClient(int clientId)
		{
		}
		
		public synchronized IServerAdapter.IListener waitForServer(long ticksToAwait) throws InterruptedException
		{
			long targetTick = this.lastTick + ticksToAwait;
			while ((null == this.server) || (this.lastTick < targetTick))
			{
				this.wait();
			}
			return this.server;
		}
		public synchronized void prepareForClient(int clientId)
		{
			Assert.assertFalse(this.clientEntities.containsKey(clientId));
			Assert.assertFalse(this.clientUpdates.containsKey(clientId));
			this.clientEntities.put(clientId, new HashMap<>());
			this.clientUpdates.put(clientId, new ArrayList<>());
		}
		public synchronized Entity waitForEntity(int clientId, int entityId) throws InterruptedException
		{
			Map<Integer, Entity> clientMap = this.clientEntities.get(clientId);
			while (!clientMap.containsKey(entityId))
			{
				this.wait();
			}
			return clientMap.get(entityId);
		}
		public synchronized Object waitForUpdate(int clientId, int index) throws InterruptedException
		{
			List<Object> updates = this.clientUpdates.get(clientId);
			while (updates.size() <= index)
			{
				this.wait();
			}
			return updates.get(index);
		}
	}
}
