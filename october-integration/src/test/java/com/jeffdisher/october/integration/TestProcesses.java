package com.jeffdisher.october.integration;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.mutations.EntityChangeMove;
import com.jeffdisher.october.persistence.ResourceLoader;
import com.jeffdisher.october.process.ClientProcess;
import com.jeffdisher.october.process.ServerProcess;
import com.jeffdisher.october.server.ServerRunner;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.worldgen.CuboidGenerator;


/**
 * Tests for the pairing of ServerProcess and ClientProcess.
 */
public class TestProcesses
{
	public static final int PORT = 5678;
	public static final long MILLIS_PER_TICK = 100L;
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
		ResourceLoader cuboidLoader = new ResourceLoader(DIRECTORY.newFolder(), null);
		ServerProcess server = new ServerProcess(PORT, MILLIS_PER_TICK, cuboidLoader, TIME_SUPPLIER);
		server.stop();
	}

	@Test(expected = IOException.class)
	public void noConnectClient() throws Throwable
	{
		_ClientListener listener = new _ClientListener();
		new ClientProcess(listener, InetAddress.getLocalHost(), PORT, "test");
	}

	@Test
	public void startStop() throws Throwable
	{
		// Start everything, connect and disconnect once the see the entity arrive.
		ResourceLoader cuboidLoader = new ResourceLoader(DIRECTORY.newFolder(), null);
		ServerProcess server = new ServerProcess(PORT, MILLIS_PER_TICK, cuboidLoader, TIME_SUPPLIER);
		_ClientListener listener = new _ClientListener();
		ClientProcess client = new ClientProcess(listener, InetAddress.getLocalHost(), PORT, "test");
		
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
		ResourceLoader cuboidLoader = new ResourceLoader(DIRECTORY.newFolder(), null);
		
		// Load a cuboid.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.blocks.AIR);
		cuboidLoader.preload(cuboid);
		ServerProcess server = new ServerProcess(PORT, MILLIS_PER_TICK, cuboidLoader, TIME_SUPPLIER);
		
		// Connect the client.
		_ClientListener listener = new _ClientListener();
		ClientProcess client = new ClientProcess(listener, InetAddress.getLocalHost(), PORT, "test");
		
		// Let some time pass and verify the data is loaded.
		long startTick = client.waitForLocalEntity(System.currentTimeMillis());
		// Wait until we have received the entity and cuboid.
		client.waitForTick(startTick + 3L, System.currentTimeMillis());
		Assert.assertNotNull(listener.getLocalEntity());
		Assert.assertEquals(1, listener.cuboids.size());
		
		// Move the client, slightly, and verify that we see the update.
		// (since we are using the real clock, wait for this move to be valid)
		EntityLocation newLocation = new EntityLocation(0.4f, 0.0f, 0.0f);
		Thread.sleep(EntityChangeMove.getTimeMostMillis(0.4f, 0.0f));
		client.moveHorizontal(0.4f, 0.0f, System.currentTimeMillis());
		long serverTick = server.waitForTicksToPass(2L);
		client.waitForTick(serverTick, System.currentTimeMillis());
		Assert.assertEquals(newLocation, listener.getLocalEntity().location());
		
		// Disconnect the client.
		client.disconnect();
		server.stop();
	}

	@Test
	public void falling() throws Throwable
	{
		// Demonstrate that a client will fall through air and this will make sense in the projection.
		ResourceLoader cuboidLoader = new ResourceLoader(DIRECTORY.newFolder(), null);
		
		// Load a cuboids.
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short) 0), ENV.blocks.AIR));
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)-1), ENV.blocks.AIR));
		ServerProcess server = new ServerProcess(PORT, MILLIS_PER_TICK, cuboidLoader, TIME_SUPPLIER);
		
		// Connect the client.
		_ClientListener listener = new _ClientListener();
		ClientProcess client = new ClientProcess(listener, InetAddress.getLocalHost(), PORT, "test");
		
		// Let some time pass and verify the data is loaded.
		long startTick = client.waitForLocalEntity(System.currentTimeMillis());
		// Wait until we have received the entity and cuboid.
		client.waitForTick(startTick + 3L, System.currentTimeMillis());
		Assert.assertNotNull(listener.getLocalEntity());
		Assert.assertEquals(2, listener.cuboids.size());
		
		// Wait a few ticks and verify that they are below the starting location.
		// Empty move changes allow us to account for falling in a way that the client controls (avoids synchronized writers over the network).
		client.doNothing(System.currentTimeMillis());
		client.waitForTick(startTick + 2, System.currentTimeMillis());
		// (we need to do 2 since the first move starts falling and the second will actually do the fall)
		client.doNothing(System.currentTimeMillis());
		client.waitForTick(startTick + 3, System.currentTimeMillis());
		
		EntityLocation location = listener.getLocalEntity().location();
		Assert.assertEquals(0.0f, location.x(), 0.01f);
		Assert.assertEquals(0.0f, location.y(), 0.01f);
		Assert.assertTrue(location.z() <= -0.098);
		
		// Disconnect the client.
		client.disconnect();
		server.stop();
	}

	@Test
	public void singleClientJoin() throws Throwable
	{
		// We want to create a server with a single cuboid, connect a client to it, and observe that the client sees everything.
		long currentTimeMillis = 1000L;
		ResourceLoader cuboidLoader = new ResourceLoader(DIRECTORY.newFolder(), null);
		
		// Create and load the cuboid full of stone with no inventories.
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.blocks.STONE);
		cuboidLoader.preload(cuboid);
		ServerProcess server = new ServerProcess(PORT, MILLIS_PER_TICK, cuboidLoader, () -> currentTimeMillis);
		
		// Connect a client and wait to receive their entity.
		_ClientListener listener = new _ClientListener();
		ClientProcess client = new ClientProcess(listener, InetAddress.getLocalHost(), PORT, "test");
		long tick = client.waitForLocalEntity(currentTimeMillis);
		// Wait until we have received the entity and cuboid.
		client.waitForTick(tick + 3L, currentTimeMillis);
		
		client.runPendingCalls(currentTimeMillis);
		Assert.assertNotNull(listener.entities.get("test".hashCode()));
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
		ResourceLoader cuboidLoader = new ResourceLoader(DIRECTORY.newFolder(), null);
		
		// Create and load the cuboids full of air (so we can walk through them) with no inventories.
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short) 0, (short)0), ENV.blocks.AIR));
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)-1, (short)0), ENV.blocks.AIR));
		ServerProcess server = new ServerProcess(PORT, MILLIS_PER_TICK, cuboidLoader, () -> currentTimeMillis[0]);
		
		// Create the first client.
		_ClientListener listener1 = new _ClientListener();
		ClientProcess client1 = new ClientProcess(listener1, InetAddress.getLocalHost(), PORT, "Client 1");
		client1.waitForLocalEntity(currentTimeMillis[0]);
		int clientId1 = client1.waitForClientId();
		
		// Create the second client.
		_ClientListener listener2 = new _ClientListener();
		ClientProcess client2 = new ClientProcess(listener2, InetAddress.getLocalHost(), PORT, "Client 2");
		client2.waitForLocalEntity(currentTimeMillis[0]);
		int clientId2 = client2.waitForClientId();
		
		// Wait until both of the clients have observed both entities for the first time.
		long serverTickNumber = server.waitForTicksToPass(1L);
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
			client1.moveHorizontal(0.4f, 0.0f, currentTimeMillis[0]);
			client2.moveHorizontal(0.0f, -0.4f, currentTimeMillis[0]);
			currentTimeMillis[0] += ServerRunner.DEFAULT_MILLIS_PER_TICK;
		}
		// We want client2 to walk further.
		for (int i = 0; i < 5; ++i)
		{
			location2 = new EntityLocation(location2.x(), location2.y() - 0.4f, location2.z());
			client2.moveHorizontal(0.0f, -0.4f, currentTimeMillis[0]);
			currentTimeMillis[0] += ServerRunner.DEFAULT_MILLIS_PER_TICK;
		}
		
		// The client flushes network operations after receiving end of tick, so let at least one tick pass for both so they flush.
		serverTickNumber = server.waitForTicksToPass(1L);
		client1.waitForTick(serverTickNumber, currentTimeMillis[0]);
		client2.waitForTick(serverTickNumber, currentTimeMillis[0]);
		currentTimeMillis[0] += ServerRunner.DEFAULT_MILLIS_PER_TICK;
		
		// Because this will always leave one tick of slack on the line, consume it before counting the ticks to see our changes.
		serverTickNumber = server.waitForTicksToPass(1L);
		client1.waitForTick(serverTickNumber, currentTimeMillis[0]);
		client2.waitForTick(serverTickNumber, currentTimeMillis[0]);
		currentTimeMillis[0] += ServerRunner.DEFAULT_MILLIS_PER_TICK;
		
		// We expect the first client to take an extra 5 ticks to be processed while the second takes a further 5.
		serverTickNumber = server.waitForTicksToPass(5L);
		client1.waitForTick(serverTickNumber, currentTimeMillis[0]);
		client2.waitForTick(serverTickNumber, currentTimeMillis[0]);
		currentTimeMillis[0] += ServerRunner.DEFAULT_MILLIS_PER_TICK;
		Assert.assertEquals(location1, listener1.entities.get(clientId1).location());
		Assert.assertEquals(location1, listener2.entities.get(clientId1).location());
		
		serverTickNumber = server.waitForTicksToPass(5L);
		client1.waitForTick(serverTickNumber, currentTimeMillis[0]);
		client2.waitForTick(serverTickNumber, currentTimeMillis[0]);
		currentTimeMillis[0] += ServerRunner.DEFAULT_MILLIS_PER_TICK;
		Assert.assertEquals(location2, listener1.entities.get(clientId2).location());
		Assert.assertEquals(location2, listener2.entities.get(clientId2).location());
		
		// We are done.
		client1.disconnect();
		client2.disconnect();
		server.stop();
	}

	@Test
	public void floodMovementCommands() throws Throwable
	{
		// Send lots of movement commands since the client should be disconnected.
		ResourceLoader cuboidLoader = new ResourceLoader(DIRECTORY.newFolder(), null);
		
		// Load a cuboid.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.blocks.AIR);
		cuboidLoader.preload(cuboid);
		ServerProcess server = new ServerProcess(PORT, MILLIS_PER_TICK, cuboidLoader, TIME_SUPPLIER);
		
		// We will connect a client just to watch what happens to the noisy one.
		_ClientListener passiveListener = new _ClientListener();
		ClientProcess passiveClient = new ClientProcess(passiveListener, InetAddress.getLocalHost(), PORT, "passive");
		passiveClient.waitForLocalEntity(System.currentTimeMillis());
		
		// Connect the client.
		_ClientListener listener = new _ClientListener();
		ClientProcess client = new ClientProcess(listener, InetAddress.getLocalHost(), PORT, "test");
		
		// Let some time pass and verify the data is loaded.
		long startTick = client.waitForLocalEntity(System.currentTimeMillis());
		// Wait until we have received the entity and cuboid.
		client.waitForTick(startTick + 3L, System.currentTimeMillis());
		Assert.assertNotNull(listener.getLocalEntity());
		Assert.assertEquals(1, listener.cuboids.size());
		
		// Verify that the passive sees the noisy client.
		passiveClient.runPendingCalls(System.currentTimeMillis());
		Assert.assertEquals(2, passiveListener.entities.size());
		
		// Send 30 move commands (since the limit is 20, there is little chance that we will observe the flakiness of this approach).
		// (we will just use the counter as the commit number since these aren't in a speculative projection but the server still expects them).
		EntityLocation oldLocation = listener.getLocalEntity().location();
		for (long i = 1L; i <= 30L; ++i)
		{
			// Note that we need to send using the low-level sendAction to bypass the ClientRunner checks and internal back-pressure in order to see how the server handles this.
			EntityChangeMove change = new EntityChangeMove(oldLocation, 0L, 0.4f, 0.0f);
			client.sendAction(change, System.currentTimeMillis());
			oldLocation = new EntityLocation(oldLocation.x() + 0.4f, oldLocation.y(), oldLocation.z());
		}
		// Note that the client will only flush these calls when it receives end of tick so wait for some ticks to happen so that we should see and end reach the client.
		server.waitForTicksToPass(2L);
		client.runPendingCalls(System.currentTimeMillis());
		
		// Wait for the server to process.
		server.waitForTicksToPass(10L);
		client.runPendingCalls(System.currentTimeMillis());
		
		// Make sure that the client connection was dropped.
		Assert.assertEquals(1, listener.connectionClosedCount);
		
		// We should see the client no longer present on the passive.
		passiveClient.runPendingCalls(System.currentTimeMillis());
		Assert.assertEquals(1, passiveListener.entities.size());
		
		// Disconnect the client (should be redundant).
		client.disconnect();
		passiveClient.disconnect();
		server.stop();
	}


	private static class _ClientListener implements ClientProcess.IListener
	{
		public final Map<CuboidAddress, IReadOnlyCuboidData> cuboids = new HashMap<>();
		public final Map<Integer, Entity> entities = new HashMap<>();
		public int assignedEntityId;
		public int connectionClosedCount;
		
		public Entity getLocalEntity()
		{
			return this.entities.get(this.assignedEntityId);
		}
		
		@Override
		public void connectionEstablished(int assignedEntityId)
		{
			Assert.assertEquals(0, this.assignedEntityId);
			Assert.assertTrue(assignedEntityId > 0);
			this.assignedEntityId = assignedEntityId;
		}
		@Override
		public void connectionClosed()
		{
			this.connectionClosedCount += 1;
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
