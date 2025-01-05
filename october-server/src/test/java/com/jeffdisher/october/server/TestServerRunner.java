package com.jeffdisher.october.server;

import java.util.ArrayList;
import java.util.HashMap;
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

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.CuboidHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.CreatureIdAssigner;
import com.jeffdisher.october.logic.HeightMapHelpers;
import com.jeffdisher.october.mutations.EntityChangeChangeHotbarSlot;
import com.jeffdisher.october.mutations.EntityChangeIncrementalBlockBreak;
import com.jeffdisher.october.mutations.EntityChangeMove;
import com.jeffdisher.october.mutations.EntityChangeOperatorSetLocation;
import com.jeffdisher.october.mutations.EntityChangeSetDayAndSpawn;
import com.jeffdisher.october.mutations.IEntityUpdate;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.mutations.IPartialEntityUpdate;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
import com.jeffdisher.october.mutations.MutationEntityRequestItemPickUp;
import com.jeffdisher.october.mutations.MutationEntitySetEntity;
import com.jeffdisher.october.net.PacketFromClient;
import com.jeffdisher.october.net.Packet_MutationEntityFromClient;
import com.jeffdisher.october.net.Packet_SendChatMessage;
import com.jeffdisher.october.persistence.FlatWorldGenerator;
import com.jeffdisher.october.persistence.IWorldGenerator;
import com.jeffdisher.october.persistence.ResourceLoader;
import com.jeffdisher.october.persistence.SuspendedCuboid;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Difficulty;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityConstants;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.types.WorldConfig;
import com.jeffdisher.october.utils.Encoding;
import com.jeffdisher.october.worldgen.CuboidGenerator;


public class TestServerRunner
{
	@ClassRule
	public static TemporaryFolder DIRECTORY = new TemporaryFolder();
	private static Environment ENV;
	private static Item STONE_ITEM;
	private static Block STONE;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		STONE_ITEM = ENV.items.getItemById("op.stone");
		STONE = ENV.blocks.fromItem(STONE_ITEM);
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
		MonitoringAgent monitoringAgent = new MonitoringAgent();
		ServerRunner runner = new ServerRunner(ServerRunner.DEFAULT_MILLIS_PER_TICK
				, network
				, new ResourceLoader(DIRECTORY.newFolder(), null, null)
				, () -> System.currentTimeMillis()
				, monitoringAgent
				, new WorldConfig()
		);
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
		ResourceLoader cuboidLoader = new ResourceLoader(DIRECTORY.newFolder(), null, MutableEntity.TESTING_LOCATION);
		_loadDefaultMap(cuboidLoader);
		MonitoringAgent monitoringAgent = new MonitoringAgent();
		ServerRunner runner = new ServerRunner(ServerRunner.DEFAULT_MILLIS_PER_TICK
				, network
				, cuboidLoader
				, () -> System.currentTimeMillis()
				, monitoringAgent
				, new WorldConfig()
		);
		IServerAdapter.IListener server = network.waitForServer(1);
		int clientId1 = 1;
		int clientId2 = 2;
		network.prepareForClient(clientId1);
		network.prepareForClient(clientId2);
		server.clientConnected(clientId1, null, "name1");
		server.clientConnected(clientId2, null, "name1");
		Entity entity1_1 = network.waitForThisEntity(clientId1);
		Assert.assertNotNull(entity1_1);
		PartialEntity entity1_2 = network.waitForPeerEntity(clientId1, clientId2);
		Assert.assertNotNull(entity1_2);
		PartialEntity entity2_1 = network.waitForPeerEntity(clientId2, clientId1);
		Assert.assertNotNull(entity2_1);
		Entity entity2_2 = network.waitForThisEntity(clientId2);
		Assert.assertNotNull(entity2_2);
		Assert.assertEquals(2, monitoringAgent.getClientsCopy().size());
		server.clientDisconnected(1);
		network.resetClient(1);
		server.clientDisconnected(2);
		network.resetClient(2);
		Assert.assertEquals(0, monitoringAgent.getClientsCopy().size());
		runner.shutdown();
	}

	@Test
	public void multiPhase() throws Throwable
	{
		TestAdapter network = new TestAdapter();
		
		// Create a world with a stone cuboid and an air cuboid on top.
		ResourceLoader cuboidLoader = new ResourceLoader(DIRECTORY.newFolder(), null, MutableEntity.TESTING_LOCATION);
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR));
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), STONE));
		
		MonitoringAgent monitoringAgent = new MonitoringAgent();
		ServerRunner runner = new ServerRunner(ServerRunner.DEFAULT_MILLIS_PER_TICK
				, network
				, cuboidLoader
				, () -> System.currentTimeMillis()
				, monitoringAgent
				, new WorldConfig()
		);
		IServerAdapter.IListener server = network.waitForServer(1);
		int clientId = 1;
		network.prepareForClient(clientId);
		server.clientConnected(clientId, null, "name");
		Entity entity = network.waitForThisEntity(clientId);
		Assert.assertNotNull(entity);
		// (we also want to wait until the server has loaded the cuboids, since this change reads them)
		network.waitForCuboidAddedCount(clientId, 2);
		
		// Break a block in 2 steps, observing the changes coming out.
		AbsoluteLocation changeLocation = new AbsoluteLocation(0, 1, -1);
		EntityChangeIncrementalBlockBreak break1 = new EntityChangeIncrementalBlockBreak(changeLocation, (short) 100);
		network.receiveFromClient(clientId, break1, 1L);
		// EntityChangeIncrementalBlockBreak consumes energy and then breaks the block so we should see 2 changes.
		Object mutation = network.waitForUpdate(clientId, 0);
		Assert.assertTrue(mutation instanceof MutationEntitySetEntity);
		mutation = network.waitForUpdate(clientId, 1);
		Assert.assertTrue(mutation instanceof MutationBlockSetBlock);
		
		// Send it again and see the block break.
		network.receiveFromClient(clientId, break1, 2L);
		// EntityChangeIncrementalBlockBreak consumes energy and then breaks the block so we should see 2 changes.
		mutation = network.waitForUpdate(clientId, 2);
		Assert.assertTrue(mutation instanceof MutationEntitySetEntity);
		mutation = network.waitForUpdate(clientId, 3);
		Assert.assertTrue(mutation instanceof MutationBlockSetBlock);
		
		runner.shutdown();
	}

	@Test
	public void dependentEntityChanges() throws Throwable
	{
		// Send a basic dependent change to verify that the ServerRunner's internal calls are unwrapped correctly.
		TestAdapter network = new TestAdapter();
		ResourceLoader cuboidLoader = new ResourceLoader(DIRECTORY.newFolder(), null, MutableEntity.TESTING_LOCATION);
		
		// Fill the cuboid with chests and put some items into one, so we can pull them out.
		CuboidAddress addressAir = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboidAir = CuboidGenerator.createFilledCuboid(addressAir, ENV.special.AIR);
		cuboidLoader.preload(cuboidAir);
		CuboidAddress addressChest = CuboidAddress.fromInt(0, 0, -1);
		Block chest = ENV.blocks.fromItem(ENV.items.getItemById("op.chest"));
		CuboidData cuboidChest = CuboidGenerator.createFilledCuboid(addressChest, chest);
		cuboidLoader.preload(cuboidChest);
		
		Inventory inventory = Inventory.start(ENV.stations.getNormalInventorySize(chest)).addStackable(STONE.item(), 2).finish();
		cuboidChest.setDataSpecial(AspectRegistry.INVENTORY, BlockAddress.fromInt(0, 0, 31), inventory);
		MonitoringAgent monitoringAgent = new MonitoringAgent();
		ServerRunner runner = new ServerRunner(ServerRunner.DEFAULT_MILLIS_PER_TICK
				, network
				, cuboidLoader
				, () -> System.currentTimeMillis()
				, monitoringAgent
				, new WorldConfig()
		);
		IServerAdapter.IListener server = network.waitForServer(1);
		int clientId = 1;
		network.prepareForClient(clientId);
		server.clientConnected(clientId, null, "name");
		Entity entity = network.waitForThisEntity(clientId);
		Assert.assertNotNull(entity);
		
		// Wait until the cuboids are loaded.
		network.waitForCuboidAddedCount(clientId, 2);
		
		// Pick these from the inventory and observe that they appear 2 ticks later.
		// We will assume the inventory key is 1.
		int blockInventoryKey = 1;
		network.receiveFromClient(clientId, new MutationEntityRequestItemPickUp(new AbsoluteLocation(0, 0, -1), blockInventoryKey, 1, Inventory.INVENTORY_ASPECT_INVENTORY), 1L);
		
		// The first tick won't change anything (as requesting inventory doesn't change the entity).
		// The second will change the block (extracting from the inventory).
		// The third will change the entity (receiving the items).
		Object change0 = network.waitForUpdate(clientId, 0);
		Assert.assertTrue(change0 instanceof MutationBlockSetBlock);
		Object change1 = network.waitForUpdate(clientId, 1);
		Assert.assertTrue(change1 instanceof MutationEntitySetEntity);
		
		runner.shutdown();
	}

	@Test
	public void entityFalling() throws Throwable
	{
		// Do nothing and observe that we see location updates from the server as the entity falls.
		TestAdapter network = new TestAdapter();
		ResourceLoader cuboidLoader = new ResourceLoader(DIRECTORY.newFolder(), null, MutableEntity.TESTING_LOCATION);
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR));
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), ENV.special.AIR));
		MonitoringAgent monitoringAgent = new MonitoringAgent();
		ServerRunner runner = new ServerRunner(ServerRunner.DEFAULT_MILLIS_PER_TICK
				, network
				, cuboidLoader
				, () -> System.currentTimeMillis()
				, monitoringAgent
				, new WorldConfig()
		);
		IServerAdapter.IListener server = network.waitForServer(1);
		int clientId = 1;
		network.prepareForClient(clientId);
		server.clientConnected(clientId, null, "name");
		Entity entity = network.waitForThisEntity(clientId);
		Assert.assertNotNull(entity);
		EntityLocation start = entity.location();
		// We also want to wait for the world to load before we start moving (we run the local mutation against a fake
		// cuboid so we will need the server to have loaded something to get the same answer).
		network.waitForCuboidAddedCount(clientId, 2);
		
		// Watch the entity fall as a result of implicit changes.
		Object change0 = network.waitForUpdate(clientId, 1);
		Assert.assertTrue(change0 instanceof MutationEntitySetEntity);
		MutableEntity mutable = MutableEntity.existing(entity);
		((MutationEntitySetEntity)change0).applyToEntity(mutable);
		Assert.assertTrue(mutable.newLocation.z() < start.z());
		EntityLocation first = mutable.newLocation;
		Object change1 = network.waitForUpdate(clientId, 2);
		Assert.assertTrue(change1 instanceof MutationEntitySetEntity);
		((MutationEntitySetEntity)change1).applyToEntity(mutable);
		Assert.assertTrue(mutable.newLocation.z() < first.z());
		
		runner.shutdown();
	}

	@Test
	public void clientJoinAndDepart() throws Throwable
	{
		// Connect 2 clients and verify that 1 can see the other when they disconnect.
		TestAdapter network = new TestAdapter();
		ResourceLoader cuboidLoader = new ResourceLoader(DIRECTORY.newFolder(), null, MutableEntity.TESTING_LOCATION);
		_loadDefaultMap(cuboidLoader);
		MonitoringAgent monitoringAgent = new MonitoringAgent();
		ServerRunner runner = new ServerRunner(ServerRunner.DEFAULT_MILLIS_PER_TICK
				, network
				, cuboidLoader
				, () -> System.currentTimeMillis()
				, monitoringAgent
				, new WorldConfig()
		);
		IServerAdapter.IListener server = network.waitForServer(1);
		int clientId1 = 1;
		int clientId2 = 2;
		network.prepareForClient(clientId1);
		network.prepareForClient(clientId2);
		server.clientConnected(clientId1, null, "name1");
		server.clientConnected(clientId2, null, "name2");
		Assert.assertEquals("name2", network.waitForClientJoin(clientId1, clientId2));
		Assert.assertEquals("name1", network.waitForClientJoin(clientId2, clientId1));
		Entity entity1_1 = network.waitForThisEntity(clientId1);
		Assert.assertNotNull(entity1_1);
		PartialEntity entity1_2 = network.waitForPeerEntity(clientId1, clientId2);
		Assert.assertNotNull(entity1_2);
		PartialEntity entity2_1 = network.waitForPeerEntity(clientId2, clientId1);
		Assert.assertNotNull(entity2_1);
		Entity entity2_2 = network.waitForThisEntity(clientId2);
		Assert.assertNotNull(entity2_2);
		server.clientDisconnected(1);
		network.resetClient(1);
		network.waitForClientLeave(clientId2, clientId1);
		
		// For them to disappear.
		network.waitForEntityRemoval(clientId2, clientId1);
		
		server.clientDisconnected(2);
		network.resetClient(2);
		runner.shutdown();
	}

	@Test
	public void changesCuboidsWhileMoving() throws Throwable
	{
		// Connect a client and have them walk over a cuboid boundary so that cuboids are removed.
		TestAdapter network = new TestAdapter();
		ResourceLoader cuboidLoader = new ResourceLoader(DIRECTORY.newFolder(), null, MutableEntity.TESTING_LOCATION);
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(1, 0, -1), STONE));
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(1, 0, 0), ENV.special.AIR));
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), STONE));
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR));
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(-1, 0, -1), STONE));
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(-1, 0, 0), ENV.special.AIR));
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(-2, 0, -1), STONE));
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(-2, 0, 0), ENV.special.AIR));
		MonitoringAgent monitoringAgent = new MonitoringAgent();
		ServerRunner runner = new ServerRunner(ServerRunner.DEFAULT_MILLIS_PER_TICK
				, network
				, cuboidLoader
				, () -> System.currentTimeMillis()
				, monitoringAgent
				, new WorldConfig()
		);
		IServerAdapter.IListener server = network.waitForServer(1);
		int clientId1 = 1;
		network.prepareForClient(clientId1);
		server.clientConnected(clientId1, null, "name");
		Entity entity1 = network.waitForThisEntity(clientId1);
		Assert.assertNotNull(entity1);
		
		// We expect to see 6 cuboids loaded (not the -2).
		network.waitForCuboidAddedCount(clientId1, 6);
		
		// Now, we want to take a step to the West and see 2 new cuboids added and 2 removed.
		float speed = EntityConstants.SPEED_PLAYER;
		long millisInStep = EntityChangeMove.getTimeMostMillis(speed, -0.4f, 0.0f);
		EntityChangeMove<IMutablePlayerEntity> move = new EntityChangeMove<>(millisInStep, 1.0f, EntityChangeMove.Direction.WEST);
		network.receiveFromClient(clientId1, move, 1L);
		
		network.waitForCuboidRemovedCount(clientId1, 2);
		network.waitForCuboidAddedCount(clientId1, 8);
		
		server.clientDisconnected(clientId1);
		network.resetClient(clientId1);
		runner.shutdown();
	}

	@Test
	public void clientRejoin() throws Throwable
	{
		// Connect a client, change something about them, disconnect, then reconnect and verify the change is still present.
		TestAdapter network = new TestAdapter();
		ResourceLoader cuboidLoader = new ResourceLoader(DIRECTORY.newFolder(), null, MutableEntity.TESTING_LOCATION);
		_loadDefaultMap(cuboidLoader);
		MonitoringAgent monitoringAgent = new MonitoringAgent();
		ServerRunner runner = new ServerRunner(ServerRunner.DEFAULT_MILLIS_PER_TICK
				, network
				, cuboidLoader
				, () -> System.currentTimeMillis()
				, monitoringAgent
				, new WorldConfig()
		);
		IServerAdapter.IListener server = network.waitForServer(1);
		int clientId1 = 1;
		
		// Connect.
		network.prepareForClient(clientId1);
		server.clientConnected(clientId1, null, "name");
		Entity entity1 = network.waitForThisEntity(clientId1);
		Assert.assertNotNull(entity1);
		Assert.assertEquals(0, entity1.hotbarIndex());
		
		// Change something - just change the selected hotbar slot.
		network.receiveFromClient(clientId1, new EntityChangeChangeHotbarSlot(1), 1L);
		Object change0 = network.waitForUpdate(clientId1, 0);
		Assert.assertTrue(change0 instanceof MutationEntitySetEntity);
		
		// Disconnect.
		server.clientDisconnected(clientId1);
		// (remove this manually since we can't be there to see ourselves be removed).
		network.resetClient(clientId1);
		
		// Reconnect and verify that the change is visible.
		network.prepareForClient(clientId1);
		server.clientConnected(clientId1, null, "name");
		entity1 = network.waitForThisEntity(clientId1);
		Assert.assertNotNull(entity1);
		Assert.assertEquals(1, entity1.hotbarIndex());
		
		server.clientDisconnected(clientId1);
		network.resetClient(clientId1);
		runner.shutdown();
	}

	@Test
	public void clientRejoinCreatures() throws Throwable
	{
		// Connect a client, observe the creatures, then reconnect to see that they have been reloaded with new IDs but that there is the same number.
		TestAdapter network = new TestAdapter();
		// We will use a special cuboid generator which only generates the one cuboid with a well-defined population of creatures.
		_SkyBlockGenerator cuboidGenerator = new _SkyBlockGenerator(null);
		ResourceLoader cuboidLoader = new ResourceLoader(DIRECTORY.newFolder(), cuboidGenerator, MutableEntity.TESTING_LOCATION);
		MonitoringAgent monitoringAgent = new MonitoringAgent();
		WorldConfig config = new WorldConfig();
		// We use peaceful here so that the behaviour is deterministic (no dynamic spawning).
		config.difficulty = Difficulty.PEACEFUL;
		ServerRunner runner = new ServerRunner(ServerRunner.DEFAULT_MILLIS_PER_TICK
				, network
				, cuboidLoader
				, () -> System.currentTimeMillis()
				, monitoringAgent
				, config
		);
		IServerAdapter.IListener server = network.waitForServer(1);
		int clientId1 = 1;
		
		// Connect.
		network.prepareForClient(clientId1);
		server.clientConnected(clientId1, null, "name");
		Entity entity1 = network.waitForThisEntity(clientId1);
		Assert.assertNotNull(entity1);
		
		// We expect to see a cow.
		PartialEntity cow = network.waitForPeerEntity(clientId1, -1);
		Assert.assertEquals(EntityType.COW, cow.type());
		
		// Disconnect.
		server.clientDisconnected(clientId1);
		// (remove this manually since we can't be there to see ourselves be removed).
		network.resetClient(clientId1);
		
		// Reconnect and verify that the creatures are the same but with different IDs.
		network.prepareForClient(clientId1);
		server.clientConnected(clientId1, null, "name");
		entity1 = network.waitForThisEntity(clientId1);
		Assert.assertNotNull(entity1);
		
		// NOTE:  In the future, this may not be a valid check since this assumes that they are renumbered since they are being reloaded.
		cow = network.waitForPeerEntity(clientId1, -2);
		Assert.assertEquals(EntityType.COW, cow.type());
		
		// We shouldn't see any other entities (no duplicated creature state).
		Assert.assertEquals(1, network.clientPartialEntities.get(clientId1).size());
		
		// Verify that the monitoring agent sees consistent data.
		Assert.assertEquals(1, monitoringAgent.getClientsCopy().size());
		Assert.assertEquals(1, monitoringAgent.getLastSnapshot().creatures().size());
		
		server.clientDisconnected(clientId1);
		network.resetClient(clientId1);
		runner.shutdown();
	}

	@Test
	public void configBroadcast() throws Throwable
	{
		TestAdapter network = new TestAdapter();
		ResourceLoader cuboidLoader = new ResourceLoader(DIRECTORY.newFolder(), new FlatWorldGenerator(false), MutableEntity.TESTING_LOCATION);
		MonitoringAgent monitoringAgent = new MonitoringAgent();
		WorldConfig config = new WorldConfig();
		ServerRunner runner = new ServerRunner(ServerRunner.DEFAULT_MILLIS_PER_TICK
				, network
				, cuboidLoader
				, () -> System.currentTimeMillis()
				, monitoringAgent
				, config
		);
		IServerAdapter.IListener server = network.waitForServer(1);
		
		// We need to attach a single client.
		int clientId1 = 1;
		network.prepareForClient(clientId1);
		server.clientConnected(clientId1, null, "name");
		Entity entity1 = network.waitForThisEntity(clientId1);
		Assert.assertNotNull(entity1);
		
		// Now, we can request a broadcast.
		monitoringAgent.getCommandSink().requestConfigBroadcast();
		WorldConfig foundConfig = network.waitForConfig();
		
		// This config should be the same one we passed in (the shared instance).
		Assert.assertTrue(config == foundConfig);
		
		server.clientDisconnected(clientId1);
		network.resetClient(clientId1);
		runner.shutdown();
	}

	@Test
	public void entityVisibilityRange() throws Throwable
	{
		// We want to connect 2 clients and teleport them between different isolated cuboids, showing what comes in/out of visiblity.
		TestAdapter network = new TestAdapter();
		// We will use a special cuboid generator which only generates the one cuboid with a well-defined population of creatures.
		CuboidAddress otherIsland = CuboidAddress.fromInt(10, 10, 10);
		_SkyBlockGenerator cuboidGenerator = new _SkyBlockGenerator(otherIsland);
		ResourceLoader cuboidLoader = new ResourceLoader(DIRECTORY.newFolder(), cuboidGenerator, MutableEntity.TESTING_LOCATION);
		MonitoringAgent monitoringAgent = new MonitoringAgent();
		WorldConfig config = new WorldConfig();
		// We use peaceful here so that the behaviour is deterministic (no dynamic spawning).
		config.difficulty = Difficulty.PEACEFUL;
		ServerRunner runner = new ServerRunner(ServerRunner.DEFAULT_MILLIS_PER_TICK
				, network
				, cuboidLoader
				, () -> System.currentTimeMillis()
				, monitoringAgent
				, config
		);
		IServerAdapter.IListener server = network.waitForServer(1);
		
		// Connect 1.
		int clientId1 = 1;
		network.prepareForClient(clientId1);
		server.clientConnected(clientId1, null, "client1");
		Entity entity1 = network.waitForThisEntity(clientId1);
		Assert.assertNotNull(entity1);
		
		// Connect 2.
		int clientId2 = 2;
		network.prepareForClient(clientId2);
		server.clientConnected(clientId2, null, "client2");
		Entity entity2 = network.waitForThisEntity(clientId2);
		Assert.assertNotNull(entity2);
		
		// We expect them both to see a cow and the other client.
		Assert.assertEquals(EntityType.COW, network.waitForPeerEntity(clientId1, -1).type());
		Assert.assertEquals(EntityType.PLAYER, network.waitForPeerEntity(clientId1, clientId2).type());
		Assert.assertEquals(EntityType.COW, network.waitForPeerEntity(clientId2, -1).type());
		Assert.assertEquals(EntityType.PLAYER, network.waitForPeerEntity(clientId2, clientId1).type());
		
		// Now, teleport one of the entities to the other island.
		EntityLocation islandBase = otherIsland.getBase().toEntityLocation();
		EntityLocation teleportTarget = new EntityLocation(islandBase.x() + 1.0f, islandBase.y() + 1.0f, islandBase.z() + 1.0f);
		EntityChangeOperatorSetLocation command = new EntityChangeOperatorSetLocation(teleportTarget);
		monitoringAgent.getCommandSink().submitEntityMutation(clientId2, command);
		
		// Wait for the old entities to disappear and the new cow to appear.
		network.waitForEntityRemoval(clientId2, -1);
		network.waitForEntityRemoval(clientId2, clientId1);
		Assert.assertEquals(EntityType.COW, network.waitForPeerEntity(clientId2, -2).type());
		network.waitForEntityRemoval(clientId1, clientId2);
		
		// Move the other entity to this same location and observe the updates from their perspective.
		monitoringAgent.getCommandSink().submitEntityMutation(clientId1, command);
		network.waitForEntityRemoval(clientId1, -1);
		Assert.assertEquals(EntityType.COW, network.waitForPeerEntity(clientId1, -2).type());
		Assert.assertEquals(EntityType.PLAYER, network.waitForPeerEntity(clientId1, clientId2).type());
		Assert.assertEquals(EntityType.PLAYER, network.waitForPeerEntity(clientId2, clientId1).type());
		
		server.clientDisconnected(clientId1);
		server.clientDisconnected(clientId2);
		network.resetClient(clientId1);
		network.resetClient(clientId2);
		runner.shutdown();
	}

	@Test
	public void configBroadcastOnDayReset() throws Throwable
	{
		TestAdapter network = new TestAdapter();
		IWorldGenerator worldGen = new IWorldGenerator() {
			@Override
			public SuspendedCuboid<CuboidData> generateCuboid(CreatureIdAssigner creatureIdAssigner, CuboidAddress address)
			{
				// If this is above 0, make it air, if below, make it stone.
				Block fill = (address.z() >= 0)
						? ENV.special.AIR
						: STONE
					;
				CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, fill);
				// If it is 0, 0, 0, add a bed at 1,1,1.
				if (address.equals(CuboidAddress.fromInt(0, 0, 0)))
				{
					cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(1, 1, 1), ENV.items.getItemById("op.bed").number());
				}
				return new SuspendedCuboid<>(cuboid
						, HeightMapHelpers.buildHeightMap(cuboid)
						, List.of()
						, List.of()
				);
			}
			@Override
			public EntityLocation getDefaultSpawnLocation()
			{
				return MutableEntity.TESTING_LOCATION;
			}
		};
		ResourceLoader cuboidLoader = new ResourceLoader(DIRECTORY.newFolder(), worldGen, MutableEntity.TESTING_LOCATION);
		MonitoringAgent monitoringAgent = new MonitoringAgent();
		WorldConfig config = new WorldConfig();
		ServerRunner runner = new ServerRunner(ServerRunner.DEFAULT_MILLIS_PER_TICK
				, network
				, cuboidLoader
				, () -> System.currentTimeMillis()
				, monitoringAgent
				, config
		);
		IServerAdapter.IListener server = network.waitForServer(1);
		
		// We need to attach a single client.
		int clientId1 = 1;
		network.prepareForClient(clientId1);
		server.clientConnected(clientId1, null, "name");
		Entity entity1 = network.waitForThisEntity(clientId1);
		Assert.assertNotNull(entity1);
		
		// Move them slightly so that they aren't on the world spawn.
		EntityChangeMove<IMutablePlayerEntity> move = new EntityChangeMove<>(100L, 1.0f, EntityChangeMove.Direction.NORTH);
		network.receiveFromClient(clientId1, move, 1L);
		Object change0 = network.waitForUpdate(clientId1, 0);
		Assert.assertTrue(change0 instanceof MutationEntitySetEntity);
		MutableEntity mutable = MutableEntity.existing(entity1);
		((MutationEntitySetEntity) change0).applyToEntity(mutable);
		entity1 = mutable.freeze();
		Assert.assertEquals(MutableEntity.TESTING_LOCATION, entity1.spawnLocation());
		Assert.assertNotEquals(MutableEntity.TESTING_LOCATION, entity1.location());
		
		// Now, inject the action to reset the day and spawn for them.
		Assert.assertEquals(0, config.dayStartTick);
		network.receiveFromClient(clientId1, new EntityChangeSetDayAndSpawn(new AbsoluteLocation(1, 1, 1)), 2L);
		EntityLocation spawn = entity1.location();
		// Wait for 4 ticks for the broadcast to happen (it since it won't come until "after" the tick where this was scheduled and we may already be waiting for the previous tick).
		network.waitForServer(4L);
		Assert.assertNotEquals(0, network.config.dayStartTick);
		Object change1 = network.waitForUpdate(clientId1, 1);
		Assert.assertTrue(change1 instanceof MutationEntitySetEntity);
		mutable = MutableEntity.existing(entity1);
		((MutationEntitySetEntity) change1).applyToEntity(mutable);
		entity1 = mutable.freeze();
		Assert.assertEquals(spawn, entity1.spawnLocation());
		
		server.clientDisconnected(clientId1);
		network.resetClient(clientId1);
		runner.shutdown();
	}

	@Test
	public void clientMessages() throws Throwable
	{
		// Connect 2 clients and show different message uses.
		TestAdapter network = new TestAdapter();
		ResourceLoader cuboidLoader = new ResourceLoader(DIRECTORY.newFolder(), null, MutableEntity.TESTING_LOCATION);
		_loadDefaultMap(cuboidLoader);
		MonitoringAgent monitoringAgent = new MonitoringAgent();
		ServerRunner runner = new ServerRunner(ServerRunner.DEFAULT_MILLIS_PER_TICK
				, network
				, cuboidLoader
				, () -> System.currentTimeMillis()
				, monitoringAgent
				, new WorldConfig()
		);
		IServerAdapter.IListener server = network.waitForServer(1);
		int clientId1 = 1;
		int clientId2 = 2;
		network.prepareForClient(clientId1);
		network.prepareForClient(clientId2);
		server.clientConnected(clientId1, null, "name1");
		server.clientConnected(clientId2, null, "name2");
		Assert.assertEquals("name2", network.waitForClientJoin(clientId1, clientId2));
		Assert.assertEquals("name1", network.waitForClientJoin(clientId2, clientId1));
		Entity entity1_1 = network.waitForThisEntity(clientId1);
		Assert.assertNotNull(entity1_1);
		PartialEntity entity1_2 = network.waitForPeerEntity(clientId1, clientId2);
		Assert.assertNotNull(entity1_2);
		PartialEntity entity2_1 = network.waitForPeerEntity(clientId2, clientId1);
		Assert.assertNotNull(entity2_1);
		Entity entity2_2 = network.waitForThisEntity(clientId2);
		Assert.assertNotNull(entity2_2);
		
		// Now, send some basic messages.
		network.receiveMessageFromClient(clientId1, clientId2, "One to Two");
		network.receiveMessageFromClient(clientId1, 0, "One to All");
		network.receiveMessageFromClient(clientId2, clientId1, "Two to One");
		network.receiveMessageFromClient(clientId2, 0, "Two to All");
		monitoringAgent.getCommandSink().sendChatMessage(clientId1, "Root to One");
		monitoringAgent.getCommandSink().sendChatMessage(clientId2, "Root to Two");
		monitoringAgent.getCommandSink().sendChatMessage(0, "Root to All");
		Assert.assertEquals(clientId1 + ": One to All", network.waitForChatMessage(clientId1, 0));
		Assert.assertEquals(clientId2 + ": Two to One", network.waitForChatMessage(clientId1, 1));
		Assert.assertEquals(clientId2 + ": Two to All", network.waitForChatMessage(clientId1, 2));
		Assert.assertEquals("0: Root to One", network.waitForChatMessage(clientId1, 3));
		Assert.assertEquals("0: Root to All", network.waitForChatMessage(clientId1, 4));
		Assert.assertEquals(clientId1 + ": One to Two", network.waitForChatMessage(clientId2, 0));
		Assert.assertEquals(clientId1 + ": One to All", network.waitForChatMessage(clientId2, 1));
		Assert.assertEquals(clientId2 + ": Two to All", network.waitForChatMessage(clientId2, 2));
		Assert.assertEquals("0: Root to Two", network.waitForChatMessage(clientId2, 3));
		Assert.assertEquals("0: Root to All", network.waitForChatMessage(clientId2, 4));
		
		server.clientDisconnected(1);
		network.resetClient(1);
		server.clientDisconnected(2);
		network.resetClient(2);
		runner.shutdown();
	}

	@Test
	public void pauseWhileFalling() throws Throwable
	{
		// Show that pausing the server will stop updates from the server and resuming will allow them to continue as expected.
		TestAdapter network = new TestAdapter();
		ResourceLoader cuboidLoader = new ResourceLoader(DIRECTORY.newFolder(), null, MutableEntity.TESTING_LOCATION);
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR));
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), ENV.special.AIR));
		MonitoringAgent monitoringAgent = new MonitoringAgent();
		// We will use a faster tick speed but this can't get too small or rounding errors will impact movement.
		long millisPerTick = 20L;
		ServerRunner runner = new ServerRunner(millisPerTick
				, network
				, cuboidLoader
				, () -> System.currentTimeMillis()
				, monitoringAgent
				, new WorldConfig()
		);
		IServerAdapter.IListener server = network.waitForServer(1);
		int clientId = 1;
		network.prepareForClient(clientId);
		server.clientConnected(clientId, null, "name");
		Entity entity = network.waitForThisEntity(clientId);
		Assert.assertNotNull(entity);
		EntityLocation start = entity.location();
		network.waitForCuboidAddedCount(clientId, 2);
		
		// Pause, wait for several ticks, and verify that we saw fewer ticks arrive, then resume and observe that falling continues.
		monitoringAgent.getCommandSink().pauseTickProcessing();
		// We will get the last tick number since at most 1 more will be produced before the pause is observed.
		long lastTickNumber = monitoringAgent.getLastSnapshot().tickNumber();
		// Wait for some time and send a broadcast to verify that messages still work.
		int updates1 = network.countClientUpdates(clientId);
		Thread.sleep(5 * millisPerTick);
		network.receiveMessageFromClient(clientId, 0, "One to All");
		int updates2 = network.countClientUpdates(clientId);
		Assert.assertTrue((updates2 - updates1) < 3);
		long laterTickNumber = monitoringAgent.getLastSnapshot().tickNumber();
		Assert.assertTrue((laterTickNumber - lastTickNumber) <= 1);
		Assert.assertEquals(clientId + ": One to All", network.waitForChatMessage(clientId, 0));
		monitoringAgent.getCommandSink().resumeTickProcessing();
		
		Object change0 = network.waitForUpdate(clientId, updates2);
		Assert.assertTrue(change0 instanceof MutationEntitySetEntity);
		MutableEntity mutable = MutableEntity.existing(entity);
		((MutationEntitySetEntity)change0).applyToEntity(mutable);
		Assert.assertTrue(mutable.newLocation.z() < start.z());
		EntityLocation first = mutable.newLocation;
		Object change1 = network.waitForUpdate(clientId, updates2 + 1);
		Assert.assertTrue(change1 instanceof MutationEntitySetEntity);
		((MutationEntitySetEntity)change1).applyToEntity(mutable);
		Assert.assertTrue(mutable.newLocation.z() < first.z());
		
		runner.shutdown();
	}


	private static void _loadDefaultMap(ResourceLoader cuboidLoader)
	{
		// Create and load the cuboid full of stone with no inventories.
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, STONE);
		cuboidLoader.preload(cuboid);
	}


	private static class TestAdapter implements IServerAdapter
	{
		public IServerAdapter.IListener server;
		public final Map<Integer, Queue<PacketFromClient>> packets = new HashMap<>();
		// Currently, each client only sees a single full entity - itself.
		public final Map<Integer, Entity> clientFullEntities = new HashMap<>();
		public final Map<Integer, Map<Integer, PartialEntity>> clientPartialEntities = new HashMap<>();
		public final Map<Integer, List<Object>> clientUpdates = new HashMap<>();
		public final Map<Integer, List<String>> chatMessages = new HashMap<>();
		public final Map<Integer, Integer> clientCuboidAddedCount = new HashMap<>();
		public final Map<Integer, Integer> clientCuboidRemovedCount = new HashMap<>();
		public final Map<Integer, Map<Integer, String>> clientConnectedNames = new HashMap<>();
		public long lastTick = 0L;
		public WorldConfig config = null;
		
		// Internal interlock related to disconnect acks.
		private int _pendingDisconnect = 0;
		
		@Override
		public synchronized void readyAndStartListening(IServerAdapter.IListener listener)
		{
			Assert.assertNull(this.server);
			this.server = listener;
			this.notifyAll();
		}
		@Override
		public synchronized PacketFromClient peekOrRemoveNextPacketFromClient(int clientId, PacketFromClient toRemove)
		{
			Queue<PacketFromClient> queue = this.packets.get(clientId);
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
		public void sendBlockEvent(int clientId, EventRecord.Type type, AbsoluteLocation location, int entitySourceId)
		{
			throw new AssertionError("Unimplemented");
		}
		@Override
		public void sendEntityEvent(int clientId, EventRecord.Type type, EventRecord.Cause cause, AbsoluteLocation optionalLocation, int entityTargetId, int entitySourceId)
		{
			throw new AssertionError("Unimplemented");
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
		public synchronized void sendConfig(int clientId, WorldConfig config)
		{
			// For now, we will assume that there is only one client for tests using this callback
			Assert.assertNotNull(config);
			Assert.assertNull(this.config);
			this.config = config;
			this.notifyAll();
		}
		@Override
		public synchronized void sendClientJoined(int clientId, int joinedClientId, String name)
		{
			Map<Integer, String> thisClient = this.clientConnectedNames.get(clientId);
			Assert.assertFalse(thisClient.containsKey(joinedClientId));
			thisClient.put(joinedClientId, name);
			this.notifyAll();
		}
		@Override
		public synchronized void sendClientLeft(int clientId, int leftClientId)
		{
			Map<Integer, String> thisClient = this.clientConnectedNames.get(clientId);
			Assert.assertTrue(thisClient.containsKey(leftClientId));
			thisClient.remove(leftClientId);
			this.notifyAll();
		}
		@Override
		public synchronized void sendChatMessage(int clientId, int senderId, String message)
		{
			List<String> messages = this.chatMessages.get(clientId);
			messages.add(senderId + ": " + message);
			this.notifyAll();
		}
		@Override
		public synchronized void disconnectClient(int clientId)
		{
		}
		@Override
		public synchronized void acknowledgeDisconnect(int clientId)
		{
			while (0 != _pendingDisconnect)
			{
				try
				{
					this.wait();
				}
				catch (InterruptedException e)
				{
					throw new AssertionError(e);
				}
			}
			_pendingDisconnect = clientId;
			this.notifyAll();
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
			Assert.assertFalse(this.chatMessages.containsKey(clientId));
			Assert.assertFalse(this.clientCuboidAddedCount.containsKey(clientId));
			Assert.assertFalse(this.clientCuboidRemovedCount.containsKey(clientId));
			Assert.assertFalse(this.clientConnectedNames.containsKey(clientId));
			this.packets.put(clientId, new LinkedList<>());
			this.clientPartialEntities.put(clientId, new HashMap<>());
			this.clientUpdates.put(clientId, new ArrayList<>());
			this.chatMessages.put(clientId, new ArrayList<>());
			this.clientCuboidAddedCount.put(clientId, 0);
			this.clientCuboidRemovedCount.put(clientId, 0);
			this.clientConnectedNames.put(clientId, new HashMap<>());
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
		public synchronized int countClientUpdates(int clientId) throws InterruptedException
		{
			return this.clientUpdates.get(clientId).size();
		}
		public synchronized String waitForChatMessage(int clientId, int index) throws InterruptedException
		{
			List<String> messages = this.chatMessages.get(clientId);
			while (messages.size() <= index)
			{
				this.wait();
			}
			return messages.get(index);
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
			Packet_MutationEntityFromClient packet = new Packet_MutationEntityFromClient(mutation, commitLevel);
			boolean wasEmpty;
			synchronized (this)
			{
				wasEmpty = this.packets.get(clientId).isEmpty();
				this.packets.get(clientId).add(packet);
			}
			if (wasEmpty)
			{
				this.server.clientReadReady(clientId);
			}
		}
		public void receiveMessageFromClient(int clientId, int targetId, String message)
		{
			Packet_SendChatMessage packet = new Packet_SendChatMessage(targetId, message);
			boolean wasEmpty;
			synchronized (this)
			{
				wasEmpty = this.packets.get(clientId).isEmpty();
				this.packets.get(clientId).add(packet);
			}
			if (wasEmpty)
			{
				this.server.clientReadReady(clientId);
			}
		}
		public synchronized void resetClient(int clientId) throws InterruptedException
		{
			while (clientId != _pendingDisconnect)
			{
				this.wait();
			}
			this.packets.remove(clientId);
			this.clientFullEntities.remove(clientId);
			this.clientPartialEntities.remove(clientId);
			this.clientUpdates.remove(clientId);
			this.chatMessages.remove(clientId);
			this.clientCuboidAddedCount.remove(clientId);
			this.clientCuboidRemovedCount.remove(clientId);
			this.clientConnectedNames.remove(clientId);
			_pendingDisconnect = 0;
			this.notifyAll();
		}
		public synchronized WorldConfig waitForConfig() throws InterruptedException
		{
			while (null == this.config)
			{
				this.wait();
			}
			return this.config;
		}
		public synchronized String waitForClientJoin(int clientId, int otherClient) throws InterruptedException
		{
			while (!this.clientConnectedNames.get(clientId).containsKey(otherClient))
			{
				this.wait();
			}
			return this.clientConnectedNames.get(clientId).get(otherClient);
		}
		public synchronized void waitForClientLeave(int clientId, int otherClient) throws InterruptedException
		{
			while (this.clientConnectedNames.get(clientId).containsKey(otherClient))
			{
				this.wait();
			}
		}
	}

	private static class _SkyBlockGenerator implements IWorldGenerator
	{
		private final Set<CuboidAddress> _islands;
		public _SkyBlockGenerator(CuboidAddress extraIsland)
		{
			CuboidAddress origin = CuboidAddress.fromInt(0, 0, 0);
			_islands = (null != extraIsland)
					? Set.of(origin, extraIsland)
					: Set.of(origin)
			;
		}
		@Override
		public SuspendedCuboid<CuboidData> generateCuboid(CreatureIdAssigner creatureIdAssigner, CuboidAddress address)
		{
			// We will only give meaningful shape to the cuboid at 0,0,0.
			SuspendedCuboid<CuboidData> data;
			if (_islands.contains(address))
			{
				// An air cuboid with a layer of stone at the bottom.
				CuboidData raw = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
				for (int y = 0; y < Encoding.CUBOID_EDGE_SIZE; ++y)
				{
					for (int x = 0; x < Encoding.CUBOID_EDGE_SIZE; ++x)
					{
						raw.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(x, y, 0), STONE.item().number());
					}
				}
				CuboidHeightMap heightMap = HeightMapHelpers.buildHeightMap(raw);
				EntityLocation base = address.getBase().toEntityLocation();
				CreatureEntity cow = CreatureEntity.create(creatureIdAssigner.next()
						, EntityType.COW
						, new EntityLocation(base.x() + 30.0f, base.y() + 0.0f, base.z() + 1.0f)
						, (byte)100
				);
				data = new SuspendedCuboid<>(raw, heightMap, List.of(cow), List.of());
			}
			else
			{
				CuboidData raw = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
				CuboidHeightMap heightMap = HeightMapHelpers.buildHeightMap(raw);
				data = new SuspendedCuboid<>(raw, heightMap, List.of(), List.of());
			}
			return data;
		}
		@Override
		public EntityLocation getDefaultSpawnLocation()
		{
			return new EntityLocation(0.0f, 0.0f, 1.0f);
		}
	}
}
