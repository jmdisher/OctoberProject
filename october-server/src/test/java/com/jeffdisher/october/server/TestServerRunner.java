package com.jeffdisher.october.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.mutations.EndBreakBlockChange;
import com.jeffdisher.october.mutations.EntityChangeMove;
import com.jeffdisher.october.mutations.EntityChangeTrickleInventory;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
import com.jeffdisher.october.mutations.MutationEntitySetEntity;
import com.jeffdisher.october.persistence.CuboidLoader;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.worldgen.CuboidGenerator;


public class TestServerRunner
{
	@Test
	public void startStop() throws Throwable
	{
		// Get the starting number of threads in our group.
		int startingActiveCount = Thread.currentThread().getThreadGroup().activeCount();
		TestAdapter network = new TestAdapter();
		ServerRunner runner = new ServerRunner(ServerRunner.DEFAULT_MILLIS_PER_TICK, network, new CuboidLoader(null), () -> System.currentTimeMillis());
		// We expect to see an extra 3 threads:  ServerRunner, TickRunner, CuboidLoader.
		Assert.assertEquals(startingActiveCount + 3, Thread.currentThread().getThreadGroup().activeCount());
		runner.shutdown();
		
		// Verify that the threads have stopped.
		Assert.assertEquals(startingActiveCount, Thread.currentThread().getThreadGroup().activeCount());
	}

	@Test
	public void twoClients() throws Throwable
	{
		// Just connect two clients and verify that they see themselves and each other.
		TestAdapter network = new TestAdapter();
		CuboidLoader cuboidLoader = new CuboidLoader(null);
		_loadDefaultMap(cuboidLoader);
		ServerRunner runner = new ServerRunner(ServerRunner.DEFAULT_MILLIS_PER_TICK, network, cuboidLoader, () -> System.currentTimeMillis());
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
		CuboidLoader cuboidLoader = new CuboidLoader(null);
		_loadDefaultMap(cuboidLoader);
		ServerRunner runner = new ServerRunner(ServerRunner.DEFAULT_MILLIS_PER_TICK, network, cuboidLoader, () -> System.currentTimeMillis());
		IServerAdapter.IListener server = network.waitForServer(1);
		int clientId = 1;
		network.prepareForClient(clientId);
		server.clientConnected(clientId);
		Entity entity = network.waitForEntity(clientId, clientId);
		Assert.assertNotNull(entity);
		
		// Submit the requests to break the block and observe that the change and mutation come back after they have delayed.
		AbsoluteLocation changeLocation = new AbsoluteLocation(0, 0, 0);
		EndBreakBlockChange longRunningChange = new EndBreakBlockChange(changeLocation, ItemRegistry.STONE);
		server.changeReceived(clientId, longRunningChange, 1L);
		
		// Note that the EndBreakBlockChange doesn't modify the entity so we will only see the block break.
		Object mutation = network.waitForUpdate(clientId, 0);
		Assert.assertTrue(mutation instanceof MutationBlockSetBlock);
		
		runner.shutdown();
	}

	@Test
	public void dependentEntityChanges() throws Throwable
	{
		// Send a basic dependent change to verify that the ServerRunner's internal calls are unwrapped correctly.
		TestAdapter network = new TestAdapter();
		CuboidLoader cuboidLoader = new CuboidLoader(null);
		_loadDefaultMap(cuboidLoader);
		ServerRunner runner = new ServerRunner(ServerRunner.DEFAULT_MILLIS_PER_TICK, network, cuboidLoader, () -> System.currentTimeMillis());
		IServerAdapter.IListener server = network.waitForServer(1);
		int clientId = 1;
		network.prepareForClient(clientId);
		server.clientConnected(clientId);
		Entity entity = network.waitForEntity(clientId, clientId);
		Assert.assertNotNull(entity);
		
		// Trickle in a few items to observe them showing up.
		server.changeReceived(clientId, new EntityChangeTrickleInventory(new Items(ItemRegistry.STONE, 3)), 1L);
		
		Object change0 = network.waitForUpdate(clientId, 0);
		Assert.assertTrue(change0 instanceof MutationEntitySetEntity);
		Object change1 = network.waitForUpdate(clientId, 1);
		Assert.assertTrue(change1 instanceof MutationEntitySetEntity);
		Object change2 = network.waitForUpdate(clientId, 2);
		Assert.assertTrue(change2 instanceof MutationEntitySetEntity);
		
		runner.shutdown();
	}

	@Test
	public void entityFalling() throws Throwable
	{
		// Send some empty move changes to see that the entity is falling over time.
		TestAdapter network = new TestAdapter();
		CuboidLoader cuboidLoader = new CuboidLoader(null);
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short) 0), ItemRegistry.AIR));
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)-1), ItemRegistry.AIR));
		ServerRunner runner = new ServerRunner(ServerRunner.DEFAULT_MILLIS_PER_TICK, network, cuboidLoader, () -> System.currentTimeMillis());
		IServerAdapter.IListener server = network.waitForServer(1);
		int clientId = 1;
		network.prepareForClient(clientId);
		server.clientConnected(clientId);
		Entity entity = network.waitForEntity(clientId, clientId);
		Assert.assertNotNull(entity);
		// We also want to wait for the world to load before we start moving (we run the local mutation against a fake
		// cuboid so we will need the server to have loaded something to get the same answer).
		network.waitForCuboidCount(clientId, 2);
		
		// Empty move changes allow us to account for falling in a way that the client controls (avoids synchronized writers over the network).
		// We will send 2 full frames together since the server runner should handle that "bursty" behaviour in its change scheduler.
		EntityChangeMove move1 = new EntityChangeMove(entity.location(), 100L, 0.0f, 0.0f);
		MutableEntity fake = new MutableEntity(entity);
		CuboidData fakeCuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short) 0), ItemRegistry.AIR);
		move1.applyChange(new TickProcessingContext(1L
				, (AbsoluteLocation loc) -> new BlockProxy(loc.getBlockAddress(), fakeCuboid)
				, null
				, null
		), fake);
		EntityChangeMove move2 = new EntityChangeMove(fake.newLocation, 100L, 0.0f, 0.0f);
		server.changeReceived(clientId, move1, 1L);
		server.changeReceived(clientId, move2, 2L);
		
		// Watch the entity fall as a result of implicit changes.
		Object change0 = network.waitForUpdate(clientId, 0);
		Assert.assertTrue(change0 instanceof MutationEntitySetEntity);
		Object change1 = network.waitForUpdate(clientId, 1);
		Assert.assertTrue(change1 instanceof MutationEntitySetEntity);
		
		runner.shutdown();
	}

	@Test
	public void clientJoinAndDepart() throws Throwable
	{
		// Connect 2 clients and verify that 1 can see the other when they disconnect.
		TestAdapter network = new TestAdapter();
		CuboidLoader cuboidLoader = new CuboidLoader(null);
		_loadDefaultMap(cuboidLoader);
		ServerRunner runner = new ServerRunner(ServerRunner.DEFAULT_MILLIS_PER_TICK, network, cuboidLoader, () -> System.currentTimeMillis());
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
		
		// For them to disappear.
		network.waitForEntityRemoval(clientId2, clientId1);
		
		server.clientDisconnected(2);
		runner.shutdown();
	}

	@Test
	public void changesCuboidsWhileMoving() throws Throwable
	{
		// Connect a client and have them walk over a cuboid boundary so that new cuboids are loaded.
		TestAdapter network = new TestAdapter();
		CuboidLoader cuboidLoader = new CuboidLoader(null);
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)1, (short)0, (short)-1), ItemRegistry.STONE));
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)1, (short)0, (short)0), ItemRegistry.AIR));
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)-1), ItemRegistry.STONE));
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ItemRegistry.AIR));
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)-1, (short)0, (short)-1), ItemRegistry.STONE));
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)-1, (short)0, (short)0), ItemRegistry.AIR));
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)-2, (short)0, (short)-1), ItemRegistry.STONE));
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)-2, (short)0, (short)0), ItemRegistry.AIR));
		ServerRunner runner = new ServerRunner(ServerRunner.DEFAULT_MILLIS_PER_TICK, network, cuboidLoader, () -> System.currentTimeMillis());
		IServerAdapter.IListener server = network.waitForServer(1);
		int clientId1 = 1;
		network.prepareForClient(clientId1);
		server.clientConnected(clientId1);
		Entity entity1 = network.waitForEntity(clientId1, clientId1);
		Assert.assertNotNull(entity1);
		
		// We expect to see 6 cuboids loaded (not the -2).
		network.waitForCuboidCount(clientId1, 6);
		
		// Now, we want to take a step to the West and see 2 new cuboids added.
		EntityChangeMove move = new EntityChangeMove(entity1.location(), 0L, -0.4f, 0.0f);
		server.changeReceived(clientId1, move, 1L);
		
		network.waitForCuboidCount(clientId1, 8);
		
		server.clientDisconnected(clientId1);
		runner.shutdown();
	}


	private static void _loadDefaultMap(CuboidLoader cuboidLoader)
	{
		// Create and load the cuboid full of stone with no inventories.
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ItemRegistry.STONE);
		cuboidLoader.preload(cuboid);
	}


	private static class TestAdapter implements IServerAdapter
	{
		public IServerAdapter.IListener server;
		public final Map<Integer, Map<Integer, Entity>> clientEntities = new HashMap<>();
		public final Map<Integer, List<Object>> clientUpdates = new HashMap<>();
		public final Map<Integer, Integer> clientCuboidCount = new HashMap<>();
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
		public synchronized void removeEntity(int clientId, int entityId)
		{
			Map<Integer, Entity> clientMap = this.clientEntities.get(clientId);
			Assert.assertTrue(clientMap.containsKey(entityId));
			clientMap.remove(entityId);
			this.notifyAll();
		}
		@Override
		public synchronized void sendCuboid(int clientId, IReadOnlyCuboidData cuboid)
		{
			int count = this.clientCuboidCount.get(clientId);
			this.clientCuboidCount.put(clientId, count + 1);
			this.notifyAll();
		}
		@Override
		public synchronized void sendChange(int clientId, int entityId, IMutationEntity change)
		{
			List<Object> updates = this.clientUpdates.get(clientId);
			updates.add(change);
			this.notifyAll();
		}
		@Override
		public synchronized void sendMutation(int clientId, IMutationBlock mutation)
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
			Assert.assertFalse(this.clientCuboidCount.containsKey(clientId));
			this.clientEntities.put(clientId, new HashMap<>());
			this.clientUpdates.put(clientId, new ArrayList<>());
			this.clientCuboidCount.put(clientId, 0);
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
		public synchronized void waitForEntityRemoval(int clientId, int entityId) throws InterruptedException
		{
			Map<Integer, Entity> clientMap = this.clientEntities.get(clientId);
			while (clientMap.containsKey(entityId))
			{
				this.wait();
			}
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
		public synchronized void waitForCuboidCount(int clientId, int count) throws InterruptedException
		{
			while (this.clientCuboidCount.get(clientId) < count)
			{
				this.wait();
			}
		}

	}
}
