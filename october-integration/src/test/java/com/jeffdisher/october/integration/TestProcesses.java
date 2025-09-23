package com.jeffdisher.october.integration;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.client.MovementAccumulator;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.OrientationHelpers;
import com.jeffdisher.october.net.NetworkLayer;
import com.jeffdisher.october.persistence.ResourceLoader;
import com.jeffdisher.october.process.ClientProcess;
import com.jeffdisher.october.process.ServerProcess;
import com.jeffdisher.october.server.MonitoringAgent;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.types.WorldConfig;
import com.jeffdisher.october.utils.CuboidGenerator;


/**
 * Tests for the pairing of ServerProcess and ClientProcess.
 */
public class TestProcesses
{
	public static final int PORT = 5678;
	public static final long MILLIS_PER_TICK = 20L;
	public static final LongSupplier TIME_SUPPLIER = () -> System.currentTimeMillis();

	@ClassRule
	public static TemporaryFolder DIRECTORY = new TemporaryFolder();
	private static Environment ENV;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void startStopServer() throws Throwable
	{
		ResourceLoader cuboidLoader = new ResourceLoader(DIRECTORY.newFolder(), null, null);
		MonitoringAgent monitoringAgent = new MonitoringAgent();
		ServerProcess server = new ServerProcess(PORT, MILLIS_PER_TICK
				, cuboidLoader
				, TIME_SUPPLIER
				, monitoringAgent
				, new WorldConfig()
		);
		server.stop();
	}

	@Test(expected = IOException.class)
	public void noConnectClient() throws Throwable
	{
		_ClientListener listener = new _ClientListener();
		new ClientProcess(listener, InetAddress.getLocalHost(), PORT, "test", 1);
	}

	@Test
	public void startStop() throws Throwable
	{
		// Start everything, connect and disconnect once the see the entity arrive.
		WorldConfig config = new WorldConfig();
		config.worldSpawn = MutableEntity.TESTING_LOCATION.getBlockLocation();
		ResourceLoader cuboidLoader = new ResourceLoader(DIRECTORY.newFolder(), null, config);
		MonitoringAgent monitoringAgent = new MonitoringAgent();
		ServerProcess server = new ServerProcess(PORT, MILLIS_PER_TICK
				, cuboidLoader
				, TIME_SUPPLIER
				, monitoringAgent
				, new WorldConfig()
		);
		_ClientListener listener = new _ClientListener();
		ClientProcess client = new ClientProcess(listener, InetAddress.getLocalHost(), PORT, "test", 1);
		
		// Wait until we see the entity arrive.
		long startTick = client.waitForLocalEntity(System.currentTimeMillis());
		// (wait for an end of tick so that we know this is flushed).
		client.waitForTick(startTick + 1L, System.currentTimeMillis());
		Assert.assertNotNull(listener.getLocalEntity());
		
		// Disconnect the client and shut down the server.
		client.disconnect();
		server.stop();
	}

	@Test
	public void basicMovement() throws Throwable
	{
		// Demonstrate that a client can move around the server without issue.
		WorldConfig config = new WorldConfig();
		config.worldSpawn = MutableEntity.TESTING_LOCATION.getBlockLocation();
		ResourceLoader cuboidLoader = new ResourceLoader(DIRECTORY.newFolder(), null, config);
		
		// Load central cuboids.
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		Block stone = ENV.blocks.fromItem(ENV.items.getItemById("op.stone"));
		CuboidData stoneCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), stone);
		cuboidLoader.preload(airCuboid);
		cuboidLoader.preload(stoneCuboid);
		MonitoringAgent monitoringAgent = new MonitoringAgent();
		ServerProcess server = new ServerProcess(PORT, MILLIS_PER_TICK
				, cuboidLoader
				, TIME_SUPPLIER
				, monitoringAgent
				, new WorldConfig()
		);
		
		// Connect the client.
		_ClientListener listener = new _ClientListener();
		ClientProcess client = new ClientProcess(listener, InetAddress.getLocalHost(), PORT, "test", 1);
		
		// Let some time pass and verify the data is loaded.
		long currentTimeMillis = 1000L;
		long startTick = client.waitForLocalEntity(currentTimeMillis);
		// Wait until we have received the entity and cuboid.
		currentTimeMillis += MILLIS_PER_TICK;
		client.waitForTick(startTick + 3L, currentTimeMillis);
		Assert.assertNotNull(listener.getLocalEntity());
		Assert.assertEquals(2, listener.cuboids.size());
		
		// Move the client, slightly, and verify that we see the update.
		// (since we are using the real clock, wait for this move to be valid)
		EntityLocation newLocation = new EntityLocation(0.4f, 0.0f, 0.0f);
		long millisInStep = 100L;
		client.setOrientation(OrientationHelpers.YAW_EAST, OrientationHelpers.PITCH_FLAT);
		long millisRemaining = millisInStep;
		while (millisRemaining > 0L)
		{
			currentTimeMillis += MILLIS_PER_TICK;
			client.walk(MovementAccumulator.Relative.FORWARD, false, currentTimeMillis);
			millisRemaining -= MILLIS_PER_TICK;
		}
		long serverTick = server.waitForTicksToPass(millisInStep / MILLIS_PER_TICK);
		currentTimeMillis += MILLIS_PER_TICK;
		client.waitForTick(serverTick, currentTimeMillis);
		Assert.assertEquals(newLocation, listener.getLocalEntity().location());
		
		// Disconnect the client.
		client.disconnect();
		server.stop();
	}

	@Test
	public void falling() throws Throwable
	{
		// Demonstrate that a client will fall through air and this will make sense in the projection.
		WorldConfig config = new WorldConfig();
		config.worldSpawn = MutableEntity.TESTING_LOCATION.getBlockLocation();
		ResourceLoader cuboidLoader = new ResourceLoader(DIRECTORY.newFolder(), null, config);
		
		// Load a cuboids.
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0,  0), ENV.special.AIR));
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), ENV.special.AIR));
		MonitoringAgent monitoringAgent = new MonitoringAgent();
		ServerProcess server = new ServerProcess(PORT, MILLIS_PER_TICK
				, cuboidLoader
				, TIME_SUPPLIER
				, monitoringAgent
				, new WorldConfig()
		);
		
		// Connect the client.
		_ClientListener listener = new _ClientListener();
		ClientProcess client = new ClientProcess(listener, InetAddress.getLocalHost(), PORT, "test", 1);
		
		// Let some time pass and verify the data is loaded.
		long currentTimeMillis = 1000L;
		long startTick = client.waitForLocalEntity(currentTimeMillis);
		// Wait until we have received the entity and cuboid.
		currentTimeMillis += MILLIS_PER_TICK;
		long last = client.waitForTick(startTick + 3L, currentTimeMillis);
		Assert.assertTrue(last >= startTick + 3L);
		Assert.assertNotNull(listener.getLocalEntity());
		Assert.assertEquals(2, listener.cuboids.size());
		
		// Wait a few ticks and verify that they are below the starting location.
		// Empty move changes allow us to account for falling in a way that the client controls (avoids synchronized writers over the network).
		currentTimeMillis += MILLIS_PER_TICK;
		client.doNothing(currentTimeMillis);
		last = client.waitForTick(startTick + 4L, currentTimeMillis);
		Assert.assertTrue(last >= startTick + 4L);
		currentTimeMillis += MILLIS_PER_TICK;
		client.doNothing(currentTimeMillis);
		last = client.waitForTick(startTick + 5L, currentTimeMillis);
		Assert.assertTrue(last >= startTick + 5L);
		currentTimeMillis += MILLIS_PER_TICK;
		client.doNothing(currentTimeMillis);
		last = client.waitForTick(startTick + 6L, currentTimeMillis);
		Assert.assertTrue(last >= startTick + 6L);
		
		EntityLocation location = listener.getLocalEntity().location();
		Assert.assertEquals(0.0f, location.x(), 0.01f);
		Assert.assertEquals(0.0f, location.y(), 0.01f);
		Assert.assertEquals(-0.02f, location.z(), 0.01f);
		
		// Disconnect the client.
		client.disconnect();
		server.stop();
	}

	@Test
	public void singleClientJoin() throws Throwable
	{
		// We want to create a server with a single cuboid, connect a client to it, and observe that the client sees everything.
		long currentTimeMillis = 1000L;
		WorldConfig config = new WorldConfig();
		config.worldSpawn = MutableEntity.TESTING_LOCATION.getBlockLocation();
		ResourceLoader cuboidLoader = new ResourceLoader(DIRECTORY.newFolder(), null, config);
		
		// Create and load the cuboid full of stone with no inventories.
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.blocks.fromItem(ENV.items.getItemById("op.stone")));
		cuboidLoader.preload(cuboid);
		MonitoringAgent monitoringAgent = new MonitoringAgent();
		ServerProcess server = new ServerProcess(PORT, MILLIS_PER_TICK
				, cuboidLoader
				, () -> 100L
				, monitoringAgent
				, new WorldConfig()
		);
		
		// Connect a client and wait to receive their entity.
		_ClientListener listener = new _ClientListener();
		ClientProcess client = new ClientProcess(listener, InetAddress.getLocalHost(), PORT, "test", 1);
		long tick = client.waitForLocalEntity(currentTimeMillis);
		currentTimeMillis += 100L;
		// Wait until we have received the entity and cuboid.
		client.waitForTick(tick + 3L, currentTimeMillis);
		currentTimeMillis += 100L;
		
		client.runPendingCalls(currentTimeMillis);
		currentTimeMillis += 100L;
		Assert.assertNotNull(listener.getLocalEntity());
		Assert.assertNotNull(listener.cuboids.get(cuboid.getCuboidAddress()));
		
		// We are done.
		client.disconnect();
		server.stop();
	}

	@Test
	public void twoClientsMove() throws Throwable
	{
		// We want to create a server with a single cuboid, connect a client to it, and observe that the client sees everything.
		long[] currentTimeMillis = new long[] { 1000L };
		WorldConfig config = new WorldConfig();
		config.worldSpawn = MutableEntity.TESTING_LOCATION.getBlockLocation();
		ResourceLoader cuboidLoader = new ResourceLoader(DIRECTORY.newFolder(), null, config);
		
		// Create and load the cuboids full of air (so we can walk through them) with no inventories.
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0,  0, 0), ENV.special.AIR));
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, -1, 0), ENV.special.AIR));
		MonitoringAgent monitoringAgent = new MonitoringAgent();
		ServerProcess server = new ServerProcess(PORT, MILLIS_PER_TICK
				, cuboidLoader
				, () -> currentTimeMillis[0]
				, monitoringAgent
				, new WorldConfig()
		);
		
		// Create the first client.
		_ClientListener listener1 = new _ClientListener();
		ClientProcess client1 = new ClientProcess(listener1, InetAddress.getLocalHost(), PORT, "Client 1", 1);
		client1.waitForLocalEntity(currentTimeMillis[0]);
		int clientId1 = client1.waitForClientId();
		
		// Create the second client.
		_ClientListener listener2 = new _ClientListener();
		ClientProcess client2 = new ClientProcess(listener2, InetAddress.getLocalHost(), PORT, "Client 2", 1);
		client2.waitForLocalEntity(currentTimeMillis[0]);
		int clientId2 = client2.waitForClientId();
		
		// Wait until both of the clients have observed both entities for the first time.
		long serverTickNumber = server.waitForTicksToPass(1L);
		while (1 != listener1.otherEntities.size())
		{
			client1.waitForTick(serverTickNumber, currentTimeMillis[0]);
			currentTimeMillis[0] += MILLIS_PER_TICK;
		}
		while (1 != listener2.otherEntities.size())
		{
			client2.waitForTick(serverTickNumber, currentTimeMillis[0]);
			currentTimeMillis[0] += MILLIS_PER_TICK;
		}
		
		// Reset the time intervals for the clients.
		client1.doNothing(currentTimeMillis[0]);
		client2.doNothing(currentTimeMillis[0]);
		currentTimeMillis[0] += MILLIS_PER_TICK;
		
		// Both of these clients need to move and then we can verify that both sides can see the correct outcome.
		// (we want this to be multiple steps, so we queue those up now)
		EntityLocation startLocation = listener1.getLocalEntity().location();
		EntityLocation location1 = startLocation;
		EntityLocation location2 = startLocation;
		// We will manually account for movement based on the limit per tick.
		float horizontalDistancePerTick = ENV.creatures.PLAYER.blocksPerSecond() * (float)MILLIS_PER_TICK / 1000.0f;
		for (int i = 0; i < 5; ++i)
		{
			location1 = new EntityLocation(location1.x() + horizontalDistancePerTick, location1.y(), location1.z());
			location2 = new EntityLocation(location2.x(), location2.y() - horizontalDistancePerTick, location2.z());
			client1.setOrientation(OrientationHelpers.YAW_EAST, OrientationHelpers.PITCH_FLAT);
			client1.walk(MovementAccumulator.Relative.FORWARD, false, currentTimeMillis[0]);
			client2.setOrientation(OrientationHelpers.YAW_SOUTH, OrientationHelpers.PITCH_FLAT);
			client2.walk(MovementAccumulator.Relative.FORWARD, false, currentTimeMillis[0]);
			currentTimeMillis[0] += MILLIS_PER_TICK;
		}
		// We want client2 to walk further.
		for (int i = 0; i < 5; ++i)
		{
			location2 = new EntityLocation(location2.x(), location2.y() - horizontalDistancePerTick, location2.z());
			client2.setOrientation(OrientationHelpers.YAW_SOUTH, OrientationHelpers.PITCH_FLAT);
			client2.walk(MovementAccumulator.Relative.FORWARD, false, currentTimeMillis[0]);
			currentTimeMillis[0] += MILLIS_PER_TICK;
		}
		
		// The client flushes network operations after receiving end of tick, so let at least one tick pass for both so they flush.
		serverTickNumber = server.waitForTicksToPass(1L);
		client1.waitForTick(serverTickNumber, currentTimeMillis[0]);
		client2.waitForTick(serverTickNumber, currentTimeMillis[0]);
		currentTimeMillis[0] += MILLIS_PER_TICK;
		
		// We will need to wait for these commits to come back to both clients as committed (we send 5 and 10 above).
		long client1CommitLevel = 5;
		long client2CommitLevel = 10;
		long client1Tick = client1.waitForLocalCommitInTick(client1CommitLevel, currentTimeMillis[0]);
		long client2Tick = client2.waitForLocalCommitInTick(client2CommitLevel, currentTimeMillis[0]);
		// We need to make sure that both clients see both updates so take the max of whenever the last of either change was committed.
		long serverTickToObserve = Math.max(client1Tick, client2Tick);
		client1.waitForTick(serverTickToObserve, currentTimeMillis[0]);
		client2.waitForTick(serverTickToObserve, currentTimeMillis[0]);
		currentTimeMillis[0] += MILLIS_PER_TICK;
		
		// Because this will always leave one tick of slack on the line, consume it before counting the ticks to see our changes.
		serverTickNumber = server.waitForTicksToPass(1L);
		client1.waitForTick(serverTickNumber, currentTimeMillis[0]);
		client2.waitForTick(serverTickNumber, currentTimeMillis[0]);
		currentTimeMillis[0] += MILLIS_PER_TICK;
		
		// By this point, they should both have seen each other move so verify final result.
		_compareLocation(location1, listener1.getLocalEntity().location());
		_compareLocation(location1, listener2.otherEntities.get(clientId1).location());
		_compareLocation(location2, listener1.otherEntities.get(clientId2).location());
		_compareLocation(location2, listener2.getLocalEntity().location());
		
		// We are done.
		client1.disconnect();
		client2.disconnect();
		server.stop();
		
		// Verify that we did see a config update packet at some point here.
		Assert.assertEquals(WorldConfig.DEFAULT_TICKS_PER_DAY, listener1.ticksPerDay);
		Assert.assertEquals(WorldConfig.DEFAULT_TICKS_PER_DAY, listener2.ticksPerDay);
	}

	@Test
	public void forceDisconnectClient() throws Throwable
	{
		// Connect a client and then use the agent to request that they be disconnected.
		long currentTimeMillis = 1000L;
		WorldConfig config = new WorldConfig();
		config.worldSpawn = MutableEntity.TESTING_LOCATION.getBlockLocation();
		ResourceLoader cuboidLoader = new ResourceLoader(DIRECTORY.newFolder(), null, config);
		
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.blocks.fromItem(ENV.items.getItemById("op.stone")));
		cuboidLoader.preload(cuboid);
		MonitoringAgent monitoringAgent = new MonitoringAgent();
		ServerProcess server = new ServerProcess(PORT, MILLIS_PER_TICK
				, cuboidLoader
				, () -> 100L
				, monitoringAgent
				, new WorldConfig()
		);
		
		// Connect a client and wait to receive their entity.
		_ClientListener listener = new _ClientListener();
		String clientName = "test";
		int cuboidViewDistance = 1;
		ClientProcess client = new ClientProcess(listener, InetAddress.getLocalHost(), PORT, clientName, cuboidViewDistance);
		long tick = client.waitForLocalEntity(currentTimeMillis);
		currentTimeMillis += 100L;
		// Wait until we have received the entity and cuboid.
		client.waitForTick(tick + 3L, currentTimeMillis);
		currentTimeMillis += 100L;
		
		client.runPendingCalls(currentTimeMillis);
		currentTimeMillis += 100L;
		Assert.assertNotNull(listener.getLocalEntity());
		Assert.assertNotNull(listener.cuboids.get(cuboid.getCuboidAddress()));
		
		// Disconnect them.
		Assert.assertEquals(1, monitoringAgent.getClientsCopy().size());
		int clientId = monitoringAgent.getClientsCopy().keySet().iterator().next();
		NetworkLayer.PeerToken token = monitoringAgent.getTokenForClient(clientId);
		monitoringAgent.getNetwork().disconnectClient(token);
		
		// Wait for disconnect.
		while (-1 != listener.assignedEntityId)
		{
			long millis = 10L;
			Thread.sleep(millis);
			currentTimeMillis += millis;
			client.runPendingCalls(currentTimeMillis);
		}
		server.stop();
		
		// Verify that we did see a config update packet at some point here.
		Assert.assertEquals(WorldConfig.DEFAULT_TICKS_PER_DAY, listener.ticksPerDay);
		Assert.assertEquals(cuboidViewDistance, listener.currentViewDistance);
	}

	@Test
	public void basicChatSystem() throws Throwable
	{
		// We need 2 clients and we want to verify that messages arrive at the correct targets.
		long[] currentTimeMillis = new long[] { 1000L };
		WorldConfig config = new WorldConfig();
		config.worldSpawn = MutableEntity.TESTING_LOCATION.getBlockLocation();
		ResourceLoader cuboidLoader = new ResourceLoader(DIRECTORY.newFolder(), null, config);
		
		// Create and load the cuboids full of air (so we can walk through them) with no inventories.
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0,  0, 0), ENV.special.AIR));
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, -1, 0), ENV.special.AIR));
		MonitoringAgent monitoringAgent = new MonitoringAgent();
		ServerProcess server = new ServerProcess(PORT, MILLIS_PER_TICK
				, cuboidLoader
				, () -> currentTimeMillis[0]
				, monitoringAgent
				, new WorldConfig()
		);
		
		// Create the first client.
		_ClientListener listener1 = new _ClientListener();
		ClientProcess client1 = new ClientProcess(listener1, InetAddress.getLocalHost(), PORT, "Client 1", 1);
		client1.waitForLocalEntity(currentTimeMillis[0]);
		int clientId1 = client1.waitForClientId();
		
		// Create the second client.
		_ClientListener listener2 = new _ClientListener();
		ClientProcess client2 = new ClientProcess(listener2, InetAddress.getLocalHost(), PORT, "Client 2", 1);
		client2.waitForLocalEntity(currentTimeMillis[0]);
		int clientId2 = client2.waitForClientId();
		
		// We now want to send 4 messages to test the cases:
		// -from 1 to 2
		// -from 2 to 0 (all)
		// -from 0 (console) to 1
		// -from 0 (console) to 0 (all)
		String message1 = "Message from 1 to 2";
		client1.sentChatMessage(clientId2, message1);
		Assert.assertEquals(_combineMessage(clientId1, message1), _waitForChat(client2, listener2));
		String message2 = "Message from 2 to 0";
		client2.sentChatMessage(0, message2);
		Assert.assertEquals(_combineMessage(clientId2, message2), _waitForChat(client1, listener1));
		Assert.assertEquals(_combineMessage(clientId2, message2), _waitForChat(client2, listener2));
		String message3 = "Message 0 to 1";
		monitoringAgent.getCommandSink().sendChatMessage(clientId1, message3);
		Assert.assertEquals(_combineMessage(0, message3), _waitForChat(client1, listener1));
		String message4 = "Message from 0 to 0";
		monitoringAgent.getCommandSink().sendChatMessage(0, message4);
		Assert.assertEquals(_combineMessage(0, message4), _waitForChat(client1, listener1));
		Assert.assertEquals(_combineMessage(0, message4), _waitForChat(client2, listener2));
		
		// Now, wait for the involved parties to see the expected messages.
		
		// We are done.
		client1.disconnect();
		client2.disconnect();
		server.stop();
	}


	// We want to compare locations with 0.01 precision, since small rounding errors are unavoidable with floats but
	// aren't a problem, so we use this helper.
	private static void _compareLocation(EntityLocation expected, EntityLocation test)
	{
		Assert.assertEquals(expected.x(), test.x(), 0.01f);
		Assert.assertEquals(expected.y(), test.y(), 0.01f);
		Assert.assertEquals(expected.z(), test.z(), 0.01f);
	}

	private static String _waitForChat(ClientProcess client, _ClientListener listener) throws InterruptedException
	{
		while (null == listener.lastMessage)
		{
			long millis = 10L;
			Thread.sleep(millis);
			client.runPendingCalls(System.currentTimeMillis());
		}
		String message = listener.lastMessage;
		listener.lastMessage = null;
		return message;
	}

	private static String _combineMessage(int senderId, String message)
	{
		return senderId + ": " + message;
	}


	private static class _ClientListener implements ClientProcess.IListener
	{
		public final Map<CuboidAddress, IReadOnlyCuboidData> cuboids = new HashMap<>();
		private Entity _thisEntity = null;
		public final Map<Integer, PartialEntity> otherEntities = new HashMap<>();
		public int assignedEntityId;
		public int currentViewDistance;
		public long lastTickCompleted = 0L;
		public int ticksPerDay;
		public final Map<Integer, String> otherClients = new HashMap<>();
		public String lastMessage;
		
		public Entity getLocalEntity()
		{
			Assert.assertEquals(this.assignedEntityId, _thisEntity.id());
			return _thisEntity;
		}
		
		@Override
		public void connectionEstablished(int assignedEntityId, int currentViewDistance)
		{
			Assert.assertEquals(0, this.assignedEntityId);
			Assert.assertTrue(assignedEntityId > 0);
			this.assignedEntityId = assignedEntityId;
			
			Assert.assertEquals(0, this.currentViewDistance);
			Assert.assertTrue(currentViewDistance >= 0);
			this.currentViewDistance = currentViewDistance;
		}
		@Override
		public void connectionClosed()
		{
			Assert.assertTrue(assignedEntityId > 0);
			assignedEntityId = -1;
		}
		@Override
		public void cuboidDidLoad(IReadOnlyCuboidData cuboid, ColumnHeightMap heightMap)
		{
			Object old = this.cuboids.put(cuboid.getCuboidAddress(), cuboid);
			Assert.assertNull(old);
		}
		@Override
		public void cuboidDidChange(IReadOnlyCuboidData cuboid
				, ColumnHeightMap heightMap
				, Set<BlockAddress> changedBlocks
				, Set<Aspect<?, ?>> changedAspects
		)
		{
			Assert.assertFalse(changedBlocks.isEmpty());
			Assert.assertFalse(changedAspects.isEmpty());
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
		public void thisEntityDidLoad(Entity authoritativeEntity)
		{
			Assert.assertNull(_thisEntity);
			_thisEntity = authoritativeEntity;
		}
		@Override
		public void thisEntityDidChange(Entity authoritativeEntity, Entity projectedEntity)
		{
			Assert.assertNotNull(_thisEntity);
			_thisEntity = projectedEntity;
		}
		@Override
		public void otherEntityDidLoad(PartialEntity entity)
		{
			Object old = this.otherEntities.put(entity.id(), entity);
			Assert.assertNull(old);
		}
		@Override
		public void otherEntityDidChange(PartialEntity entity)
		{
			Object old = this.otherEntities.put(entity.id(), entity);
			Assert.assertNotNull(old);
		}
		@Override
		public void otherEntityDidUnload(int id)
		{
			Object old = this.otherEntities.remove(id);
			Assert.assertNotNull(old);
		}
		@Override
		public void tickDidComplete(long gameTick)
		{
			Assert.assertTrue(gameTick > this.lastTickCompleted);
			this.lastTickCompleted = gameTick;
		}
		@Override
		public void handleEvent(EventRecord event)
		{
			// Not in this test.
			throw new AssertionError("handleEvent");
		}
		@Override
		public void configUpdated(int ticksPerDay, int dayStartTick)
		{
			this.ticksPerDay = ticksPerDay;
		}
		@Override
		public void otherClientJoined(int clientId, String name)
		{
			Object old = this.otherClients.put(clientId, name);
			Assert.assertNull(old);
		}
		@Override
		public void otherClientLeft(int clientId)
		{
			Object old = this.otherClients.remove(clientId);
			Assert.assertNotNull(old);
		}
		@Override
		public void receivedChatMessage(int senderId, String message)
		{
			Assert.assertNull(this.lastMessage);
			this.lastMessage = _combineMessage(senderId, message);
		}
	}
}
