package com.jeffdisher.october.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.mutations.EntityChangeIncrementalBlockBreak;
import com.jeffdisher.october.mutations.EntityChangeMove;
import com.jeffdisher.october.mutations.EntityChangeTrickleInventory;
import com.jeffdisher.october.mutations.IEntityUpdate;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.mutations.IPartialEntityUpdate;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
import com.jeffdisher.october.mutations.MutationEntitySetEntity;
import com.jeffdisher.october.net.Packet_MutationEntityFromClient;
import com.jeffdisher.october.persistence.FlatWorldGenerator;
import com.jeffdisher.october.persistence.ResourceLoader;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.worldgen.CuboidGenerator;


public class TestServerRunner
{
	@ClassRule
	public static TemporaryFolder DIRECTORY = new TemporaryFolder();
	private static Environment ENV;
	private static Block STONE;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		STONE = ENV.blocks.fromItem(ENV.items.getItemById("op.stone"));
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void startStop() throws Throwable
	{
		// Get the starting number of threads in our group.
		int startingActiveCount = Thread.currentThread().getThreadGroup().activeCount();
		TestAdapter network = new TestAdapter();
		ServerRunner runner = new ServerRunner(ServerRunner.DEFAULT_MILLIS_PER_TICK, network, new ResourceLoader(DIRECTORY.newFolder(), null), () -> System.currentTimeMillis());
		// We expect to see an extra 6 threads:  ServerRunner, CuboidLoader, and 4xTickRunner.
		Assert.assertEquals(startingActiveCount + 2 + ServerRunner.TICK_RUNNER_THREAD_COUNT, Thread.currentThread().getThreadGroup().activeCount());
		runner.shutdown();
		
		// Verify that the threads have stopped.
		Assert.assertEquals(startingActiveCount, Thread.currentThread().getThreadGroup().activeCount());
	}

	@Test
	public void twoClients() throws Throwable
	{
		// Just connect two clients and verify that they see themselves and each other.
		TestAdapter network = new TestAdapter();
		ResourceLoader cuboidLoader = new ResourceLoader(DIRECTORY.newFolder(), null);
		_loadDefaultMap(cuboidLoader);
		ServerRunner runner = new ServerRunner(ServerRunner.DEFAULT_MILLIS_PER_TICK, network, cuboidLoader, () -> System.currentTimeMillis());
		IServerAdapter.IListener server = network.waitForServer(1);
		int clientId1 = 1;
		int clientId2 = 2;
		network.prepareForClient(clientId1);
		network.prepareForClient(clientId2);
		server.clientConnected(clientId1);
		server.clientConnected(clientId2);
		Entity entity1_1 = network.waitForThisEntity(clientId1);
		Assert.assertNotNull(entity1_1);
		PartialEntity entity1_2 = network.waitForPeerEntity(clientId1, clientId2);
		Assert.assertNotNull(entity1_2);
		PartialEntity entity2_1 = network.waitForPeerEntity(clientId2, clientId1);
		Assert.assertNotNull(entity2_1);
		Entity entity2_2 = network.waitForThisEntity(clientId2);
		Assert.assertNotNull(entity2_2);
		server.clientDisconnected(1);
		server.clientDisconnected(2);
		runner.shutdown();
	}

	@Test
	public void multiPhase() throws Throwable
	{
		TestAdapter network = new TestAdapter();
		ResourceLoader cuboidLoader = new ResourceLoader(DIRECTORY.newFolder(), null);
		_loadDefaultMap(cuboidLoader);
		ServerRunner runner = new ServerRunner(ServerRunner.DEFAULT_MILLIS_PER_TICK, network, cuboidLoader, () -> System.currentTimeMillis());
		IServerAdapter.IListener server = network.waitForServer(1);
		int clientId = 1;
		network.prepareForClient(clientId);
		server.clientConnected(clientId);
		Entity entity = network.waitForThisEntity(clientId);
		Assert.assertNotNull(entity);
		// (we also want to wait until the server has loaded the cuboid, since this change reads them)
		network.waitForCuboidAddedCount(clientId, 1);
		
		// Break a block in 2 steps, observing the changes coming out.
		AbsoluteLocation changeLocation = new AbsoluteLocation(0, 0, 0);
		EntityChangeIncrementalBlockBreak break1 = new EntityChangeIncrementalBlockBreak(changeLocation, (short) 100);
		network.receiveFromClient(clientId, break1, 1L);
		// Note that the EntityChangeIncrementalBlockBreak doesn't modify the entity so we will only see the block damage update.
		Object mutation = network.waitForUpdate(clientId, 0);
		Assert.assertTrue(mutation instanceof MutationBlockSetBlock);
		
		// Send it again and see the block break.
		network.receiveFromClient(clientId, break1, 2L);
		// Note that the EntityChangeIncrementalBlockBreak doesn't modify the entity so we will only see the block damage update.
		mutation = network.waitForUpdate(clientId, 1);
		Assert.assertTrue(mutation instanceof MutationBlockSetBlock);
		
		runner.shutdown();
	}

	@Test
	public void dependentEntityChanges() throws Throwable
	{
		// Send a basic dependent change to verify that the ServerRunner's internal calls are unwrapped correctly.
		TestAdapter network = new TestAdapter();
		ResourceLoader cuboidLoader = new ResourceLoader(DIRECTORY.newFolder(), null);
		_loadDefaultMap(cuboidLoader);
		ServerRunner runner = new ServerRunner(ServerRunner.DEFAULT_MILLIS_PER_TICK, network, cuboidLoader, () -> System.currentTimeMillis());
		IServerAdapter.IListener server = network.waitForServer(1);
		int clientId = 1;
		network.prepareForClient(clientId);
		server.clientConnected(clientId);
		Entity entity = network.waitForThisEntity(clientId);
		Assert.assertNotNull(entity);
		
		// Trickle in a few items to observe them showing up.
		network.receiveFromClient(clientId, new EntityChangeTrickleInventory(new Items(ENV.items.STONE, 3)), 1L);
		
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
		ResourceLoader cuboidLoader = new ResourceLoader(DIRECTORY.newFolder(), null);
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short) 0), ENV.special.AIR));
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)-1), ENV.special.AIR));
		ServerRunner runner = new ServerRunner(ServerRunner.DEFAULT_MILLIS_PER_TICK, network, cuboidLoader, () -> System.currentTimeMillis());
		IServerAdapter.IListener server = network.waitForServer(1);
		int clientId = 1;
		network.prepareForClient(clientId);
		server.clientConnected(clientId);
		Entity entity = network.waitForThisEntity(clientId);
		Assert.assertNotNull(entity);
		// We also want to wait for the world to load before we start moving (we run the local mutation against a fake
		// cuboid so we will need the server to have loaded something to get the same answer).
		network.waitForCuboidAddedCount(clientId, 2);
		
		// Empty move changes allow us to account for falling in a way that the client controls (avoids synchronized writers over the network).
		// We will send 2 full frames together since the server runner should handle that "bursty" behaviour in its change scheduler.
		EntityChangeMove<IMutablePlayerEntity> move1 = new EntityChangeMove<>(entity.location(), 100L, 0.0f, 0.0f);
		MutableEntity fake = MutableEntity.existing(entity);
		CuboidData fakeCuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short) 0), ENV.special.AIR);
		move1.applyChange(new TickProcessingContext(1L
				, (AbsoluteLocation loc) -> new BlockProxy(loc.getBlockAddress(), fakeCuboid)
				, null
				, null
				, null
		), fake);
		EntityChangeMove<IMutablePlayerEntity> move2 = new EntityChangeMove<>(fake.newLocation, 100L, 0.0f, 0.0f);
		network.receiveFromClient(clientId, move1, 1L);
		network.receiveFromClient(clientId, move2, 2L);
		
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
		ResourceLoader cuboidLoader = new ResourceLoader(DIRECTORY.newFolder(), null);
		_loadDefaultMap(cuboidLoader);
		ServerRunner runner = new ServerRunner(ServerRunner.DEFAULT_MILLIS_PER_TICK, network, cuboidLoader, () -> System.currentTimeMillis());
		IServerAdapter.IListener server = network.waitForServer(1);
		int clientId1 = 1;
		int clientId2 = 2;
		network.prepareForClient(clientId1);
		network.prepareForClient(clientId2);
		server.clientConnected(clientId1);
		server.clientConnected(clientId2);
		Entity entity1_1 = network.waitForThisEntity(clientId1);
		Assert.assertNotNull(entity1_1);
		PartialEntity entity1_2 = network.waitForPeerEntity(clientId1, clientId2);
		Assert.assertNotNull(entity1_2);
		PartialEntity entity2_1 = network.waitForPeerEntity(clientId2, clientId1);
		Assert.assertNotNull(entity2_1);
		Entity entity2_2 = network.waitForThisEntity(clientId2);
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
		// Connect a client and have them walk over a cuboid boundary so that cuboids are removed.
		TestAdapter network = new TestAdapter();
		ResourceLoader cuboidLoader = new ResourceLoader(DIRECTORY.newFolder(), null);
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)1, (short)0, (short)-1), STONE));
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)1, (short)0, (short)0), ENV.special.AIR));
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)-1), STONE));
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.special.AIR));
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)-1, (short)0, (short)-1), STONE));
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)-1, (short)0, (short)0), ENV.special.AIR));
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)-2, (short)0, (short)-1), STONE));
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)-2, (short)0, (short)0), ENV.special.AIR));
		ServerRunner runner = new ServerRunner(ServerRunner.DEFAULT_MILLIS_PER_TICK, network, cuboidLoader, () -> System.currentTimeMillis());
		IServerAdapter.IListener server = network.waitForServer(1);
		int clientId1 = 1;
		network.prepareForClient(clientId1);
		server.clientConnected(clientId1);
		Entity entity1 = network.waitForThisEntity(clientId1);
		Assert.assertNotNull(entity1);
		
		// We expect to see 6 cuboids loaded (not the -2).
		network.waitForCuboidAddedCount(clientId1, 6);
		
		// Now, we want to take a step to the West and see 2 new cuboids added and 2 removed.
		EntityChangeMove<IMutablePlayerEntity> move = new EntityChangeMove<>(entity1.location(), 0L, -0.4f, 0.0f);
		network.receiveFromClient(clientId1, move, 1L);
		
		network.waitForCuboidRemovedCount(clientId1, 2);
		network.waitForCuboidAddedCount(clientId1, 8);
		
		server.clientDisconnected(clientId1);
		runner.shutdown();
	}

	@Test
	public void clientRejoin() throws Throwable
	{
		// Connect a client, change something about them, disconnect, then reconnect and verify the change is still present.
		TestAdapter network = new TestAdapter();
		ResourceLoader cuboidLoader = new ResourceLoader(DIRECTORY.newFolder(), null);
		_loadDefaultMap(cuboidLoader);
		ServerRunner runner = new ServerRunner(ServerRunner.DEFAULT_MILLIS_PER_TICK, network, cuboidLoader, () -> System.currentTimeMillis());
		IServerAdapter.IListener server = network.waitForServer(1);
		int clientId1 = 1;
		
		// Connect.
		network.prepareForClient(clientId1);
		server.clientConnected(clientId1);
		Entity entity1 = network.waitForThisEntity(clientId1);
		Assert.assertNotNull(entity1);
		Assert.assertEquals(0, entity1.inventory().sortedKeys().size());
		
		// Change something - we will add items to the inventory.
		network.receiveFromClient(clientId1, new EntityChangeTrickleInventory(new Items(ENV.items.STONE, 1)), 1L);
		Object change0 = network.waitForUpdate(clientId1, 0);
		Assert.assertTrue(change0 instanceof MutationEntitySetEntity);
		
		// Disconnect.
		server.clientDisconnected(1);
		// (remove this manually since we can't be there to see ourselves be removed).
		network.resetClient(clientId1);
		
		// Reconnect and verify that the change is visible.
		network.prepareForClient(clientId1);
		server.clientConnected(clientId1);
		entity1 = network.waitForThisEntity(clientId1);
		Assert.assertNotNull(entity1);
		Assert.assertEquals(1, entity1.inventory().sortedKeys().size());
		
		server.clientDisconnected(1);
		runner.shutdown();
	}

	@Test
	public void clientRejoinCreatures() throws Throwable
	{
		// Connect a client, change something about them, disconnect, then reconnect and verify the change is still present.
		TestAdapter network = new TestAdapter();
		ResourceLoader cuboidLoader = new ResourceLoader(DIRECTORY.newFolder(), new FlatWorldGenerator(false));
		ServerRunner runner = new ServerRunner(ServerRunner.DEFAULT_MILLIS_PER_TICK, network, cuboidLoader, () -> System.currentTimeMillis());
		IServerAdapter.IListener server = network.waitForServer(1);
		int clientId1 = 1;
		
		// Connect.
		network.prepareForClient(clientId1);
		server.clientConnected(clientId1);
		Entity entity1 = network.waitForThisEntity(clientId1);
		Assert.assertNotNull(entity1);
		
		// Verify that we see the appropriate creatures - we expect 9 of them and they all be cows at the base of their cuboids.
		Set<EntityLocation> creatureLocations = new HashSet<>();
		for (int i = -1; i >= -9; --i)
		{
			PartialEntity creature = network.waitForPeerEntity(clientId1, i);
			Assert.assertEquals(new BlockAddress((byte)0, (byte)0, (byte)0), creature.location().getBlockLocation().getBlockAddress());
			Assert.assertEquals(EntityType.COW, creature.type());
			creatureLocations.add(creature.location());
		}
		
		// Disconnect.
		server.clientDisconnected(1);
		// (remove this manually since we can't be there to see ourselves be removed).
		network.resetClient(clientId1);
		
		// Reconnect and verify that the creatures are the same but with different IDs.
		network.prepareForClient(clientId1);
		server.clientConnected(clientId1);
		entity1 = network.waitForThisEntity(clientId1);
		Assert.assertNotNull(entity1);
		
		// NOTE:  In the future, this may not be a valid check since this assumes that they are renumbered since they are being reloaded.
		for (int i = -10; i >= -18; --i)
		{
			PartialEntity creature = network.waitForPeerEntity(clientId1, i);
			Assert.assertEquals(new BlockAddress((byte)0, (byte)0, (byte)0), creature.location().getBlockLocation().getBlockAddress());
			Assert.assertEquals(EntityType.COW, creature.type());
			Assert.assertTrue(creatureLocations.contains(creature.location()));
		}
		// We shouldn't see any other entities (no duplicated creature state).
		Assert.assertEquals(9, network.clientPartialEntities.get(clientId1).size());
		
		server.clientDisconnected(1);
		runner.shutdown();
	}


	private static void _loadDefaultMap(ResourceLoader cuboidLoader)
	{
		// Create and load the cuboid full of stone with no inventories.
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, STONE);
		cuboidLoader.preload(cuboid);
	}


	private static class TestAdapter implements IServerAdapter
	{
		public IServerAdapter.IListener server;
		public final Map<Integer, Queue<Packet_MutationEntityFromClient>> packets = new HashMap<>();
		// Currently, each client only sees a single full entity - itself.
		public final Map<Integer, Entity> clientFullEntities = new HashMap<>();
		public final Map<Integer, Map<Integer, PartialEntity>> clientPartialEntities = new HashMap<>();
		public final Map<Integer, List<Object>> clientUpdates = new HashMap<>();
		public final Map<Integer, Integer> clientCuboidAddedCount = new HashMap<>();
		public final Map<Integer, Integer> clientCuboidRemovedCount = new HashMap<>();
		public long lastTick = 0L;
		
		@Override
		public synchronized void readyAndStartListening(IServerAdapter.IListener listener)
		{
			Assert.assertNull(this.server);
			this.server = listener;
			this.notifyAll();
		}
		@Override
		public synchronized Packet_MutationEntityFromClient peekOrRemoveNextMutationFromClient(int clientId, Packet_MutationEntityFromClient toRemove)
		{
			Queue<Packet_MutationEntityFromClient> queue = this.packets.get(clientId);
			if (null != toRemove)
			{
				Assert.assertEquals(toRemove, queue.poll());
			}
			return queue.peek();
		}
		@Override
		public synchronized void sendFullEntity(int clientId, Entity entity)
		{
			Assert.assertFalse(this.clientFullEntities.containsKey(clientId));
			this.clientFullEntities.put(clientId, entity);
			this.notifyAll();
		}
		@Override
		public synchronized void sendPartialEntity(int clientId, PartialEntity entity)
		{
			int entityId = entity.id();
			Map<Integer, PartialEntity> clientMap = this.clientPartialEntities.get(clientId);
			Assert.assertFalse(clientMap.containsKey(entityId));
			clientMap.put(entityId, entity);
			this.notifyAll();
		}
		@Override
		public synchronized void removeEntity(int clientId, int entityId)
		{
			// This should only ever apply to partials.
			Map<Integer, PartialEntity> clientMap = this.clientPartialEntities.get(clientId);
			Assert.assertTrue(clientMap.containsKey(entityId));
			clientMap.remove(entityId);
			this.notifyAll();
		}
		@Override
		public synchronized void sendCuboid(int clientId, IReadOnlyCuboidData cuboid)
		{
			int count = this.clientCuboidAddedCount.get(clientId);
			this.clientCuboidAddedCount.put(clientId, count + 1);
			this.notifyAll();
		}
		@Override
		public synchronized void removeCuboid(int clientId, CuboidAddress address)
		{
			int count = this.clientCuboidRemovedCount.get(clientId);
			this.clientCuboidRemovedCount.put(clientId, count + 1);
			this.notifyAll();
		}
		@Override
		public synchronized void sendEntityUpdate(int clientId, int entityId, IEntityUpdate update)
		{
			List<Object> updates = this.clientUpdates.get(clientId);
			updates.add(update);
			this.notifyAll();
		}
		@Override
		public synchronized void sendPartialEntityUpdate(int clientId, int entityId, IPartialEntityUpdate update)
		{
			List<Object> updates = this.clientUpdates.get(clientId);
			updates.add(update);
			this.notifyAll();
		}
		@Override
		public synchronized void sendBlockUpdate(int clientId, MutationBlockSetBlock update)
		{
			List<Object> updates = this.clientUpdates.get(clientId);
			updates.add(update);
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
			Assert.assertFalse(this.packets.containsKey(clientId));
			Assert.assertFalse(this.clientFullEntities.containsKey(clientId));
			Assert.assertFalse(this.clientPartialEntities.containsKey(clientId));
			Assert.assertFalse(this.clientUpdates.containsKey(clientId));
			Assert.assertFalse(this.clientCuboidAddedCount.containsKey(clientId));
			Assert.assertFalse(this.clientCuboidRemovedCount.containsKey(clientId));
			this.packets.put(clientId, new LinkedList<>());
			this.clientPartialEntities.put(clientId, new HashMap<>());
			this.clientUpdates.put(clientId, new ArrayList<>());
			this.clientCuboidAddedCount.put(clientId, 0);
			this.clientCuboidRemovedCount.put(clientId, 0);
		}
		public synchronized Entity waitForThisEntity(int clientId) throws InterruptedException
		{
			while (!this.clientFullEntities.containsKey(clientId))
			{
				this.wait();
			}
			return this.clientFullEntities.get(clientId);
		}
		public synchronized PartialEntity waitForPeerEntity(int clientId, int entityId) throws InterruptedException
		{
			Map<Integer, PartialEntity> clientMap = this.clientPartialEntities.get(clientId);
			while (!clientMap.containsKey(entityId))
			{
				this.wait();
			}
			return clientMap.get(entityId);
		}
		public synchronized void waitForEntityRemoval(int clientId, int entityId) throws InterruptedException
		{
			// This only makes sense for partials.
			Map<Integer, PartialEntity> clientMap = this.clientPartialEntities.get(clientId);
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
		public synchronized void waitForCuboidAddedCount(int clientId, int count) throws InterruptedException
		{
			while (this.clientCuboidAddedCount.get(clientId) < count)
			{
				this.wait();
			}
		}
		public synchronized void waitForCuboidRemovedCount(int clientId, int count) throws InterruptedException
		{
			while (this.clientCuboidRemovedCount.get(clientId) < count)
			{
				this.wait();
			}
		}
		public void receiveFromClient(int clientId, IMutationEntity<IMutablePlayerEntity> mutation, long commitLevel)
		{
			boolean wasEmpty;
			synchronized (this)
			{
				wasEmpty = this.packets.get(clientId).isEmpty();
				this.packets.get(clientId).add(new Packet_MutationEntityFromClient(mutation, commitLevel));
			}
			if (wasEmpty)
			{
				this.server.clientReadReady(clientId);
			}
		}
		public synchronized void resetClient(int clientId)
		{
			this.packets.remove(clientId);
			this.clientFullEntities.remove(clientId);
			this.clientPartialEntities.remove(clientId);
			this.clientUpdates.remove(clientId);
			this.clientCuboidAddedCount.remove(clientId);
			this.clientCuboidRemovedCount.remove(clientId);
		}
	}
}
