package com.jeffdisher.october.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

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
import com.jeffdisher.october.worldgen.CuboidGenerator;


public class TestServerRunner
{
	@Test
	public void startStop() throws Throwable
	{
		// Get the starting number of threads in our group.
		int startingActiveCount = Thread.currentThread().getThreadGroup().activeCount();
		TestAdapter network = new TestAdapter();
		ServerRunner runner = new ServerRunner(ServerRunner.DEFAULT_MILLIS_PER_TICK, network, () -> System.currentTimeMillis());
		// We expect to see an extra 2 threads:  One for ServerRunner and one for TickRunner.
		Assert.assertEquals(startingActiveCount + 2, Thread.currentThread().getThreadGroup().activeCount());
		runner.shutdown();
		
		// Verify that the threads have stopped.
		Assert.assertEquals(startingActiveCount, Thread.currentThread().getThreadGroup().activeCount());
	}

	@Test
	public void twoClients() throws Throwable
	{
		// Just connect two clients and verify that they see themselves and each other.
		TestAdapter network = new TestAdapter();
		ServerRunner runner = new ServerRunner(ServerRunner.DEFAULT_MILLIS_PER_TICK, network, () -> System.currentTimeMillis());
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
		ServerRunner runner = new ServerRunner(ServerRunner.DEFAULT_MILLIS_PER_TICK, network, () -> System.currentTimeMillis());
		_loadDefaultMap(runner);
		IServerAdapter.IListener server = network.waitForServer(1);
		int clientId = 1;
		network.prepareForClient(clientId);
		server.clientConnected(clientId);
		Entity entity = network.waitForEntity(clientId, clientId);
		Assert.assertNotNull(entity);
		
		// Submit the requests to break the block and observe that the change and mutation come back after they have delayed.
		AbsoluteLocation changeLocation = new AbsoluteLocation(0, 0, 0);
		EndBreakBlockChange longRunningChange = new EndBreakBlockChange(changeLocation, ItemRegistry.STONE.number());
		server.changeReceived(clientId, longRunningChange, 1L);
		
		// Wait for the output and verify it is what is expected.
		Object change = network.waitForUpdate(clientId, 0);
		Assert.assertTrue(longRunningChange == change);
		Object mutation = network.waitForUpdate(clientId, 1);
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
		public synchronized void sendEndOfTick(int clientId, long tickNumber, long latestLocalCommitIncluded)
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
