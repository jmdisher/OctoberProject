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
import com.jeffdisher.october.types.EntityConstants;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.types.WorldConfig;
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
		ServerProcess server = new ServerProcess(PORT, MILLIS_PER_TICK
				, cuboidLoader
				, TIME_SUPPLIER
				, new WorldConfig()
		);
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
		ServerProcess server = new ServerProcess(PORT, MILLIS_PER_TICK
				, cuboidLoader
				, TIME_SUPPLIER
				, new WorldConfig()
		);
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
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.special.AIR);
		cuboidLoader.preload(cuboid);
		ServerProcess server = new ServerProcess(PORT, MILLIS_PER_TICK
				, cuboidLoader
				, TIME_SUPPLIER
				, new WorldConfig()
		);
		
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
		float speed = EntityConstants.SPEED_PLAYER;
		long millisInStep = EntityChangeMove.getTimeMostMillis(speed, 0.4f, 0.0f);
		Thread.sleep(millisInStep);
		client.moveHorizontalFully(EntityChangeMove.Direction.EAST, System.currentTimeMillis());
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
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short) 0), ENV.special.AIR));
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)-1), ENV.special.AIR));
		ServerProcess server = new ServerProcess(PORT, MILLIS_PER_TICK
				, cuboidLoader
				, TIME_SUPPLIER
				, new WorldConfig()
		);
		
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
		Assert.assertTrue(location.z() <= -0.049);
		
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
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.blocks.fromItem(ENV.items.getItemById("op.stone")));
		cuboidLoader.preload(cuboid);
		ServerProcess server = new ServerProcess(PORT, MILLIS_PER_TICK
				, cuboidLoader
				, () -> 100L
				, new WorldConfig()
		);
		
		// Connect a client and wait to receive their entity.
		_ClientListener listener = new _ClientListener();
		ClientProcess client = new ClientProcess(listener, InetAddress.getLocalHost(), PORT, "test");
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
		ResourceLoader cuboidLoader = new ResourceLoader(DIRECTORY.newFolder(), null);
		
		// Create and load the cuboids full of air (so we can walk through them) with no inventories.
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short) 0, (short)0), ENV.special.AIR));
		cuboidLoader.preload(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)-1, (short)0), ENV.special.AIR));
		ServerProcess server = new ServerProcess(PORT, MILLIS_PER_TICK
				, cuboidLoader
				, () -> currentTimeMillis[0]
				, new WorldConfig()
		);
		
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
		while (1 != listener1.otherEntities.size())
		{
			client1.waitForTick(serverTickNumber, currentTimeMillis[0]);
			currentTimeMillis[0] += ServerRunner.DEFAULT_MILLIS_PER_TICK;
		}
		while (1 != listener2.otherEntities.size())
		{
			client2.waitForTick(serverTickNumber, currentTimeMillis[0]);
			currentTimeMillis[0] += ServerRunner.DEFAULT_MILLIS_PER_TICK;
		}
		
		// Both of these clients need to move and then we can verify that both sides can see the correct outcome.
		// (we want this to be multiple steps, so we queue those up now)
		EntityLocation startLocation = listener1.getLocalEntity().location();
		EntityLocation location1 = startLocation;
		EntityLocation location2 = startLocation;
		for (int i = 0; i < 5; ++i)
		{
			// We can walk 0.4 horizontally in a single tick.
			location1 = new EntityLocation(location1.x() + 0.4f, location1.y(), location1.z());
			location2 = new EntityLocation(location2.x(), location2.y() - 0.4f, location2.z());
			client1.moveHorizontalFully(EntityChangeMove.Direction.EAST, currentTimeMillis[0]);
			client2.moveHorizontalFully(EntityChangeMove.Direction.SOUTH, currentTimeMillis[0]);
			currentTimeMillis[0] += ServerRunner.DEFAULT_MILLIS_PER_TICK;
		}
		// We want client2 to walk further.
		for (int i = 0; i < 5; ++i)
		{
			location2 = new EntityLocation(location2.x(), location2.y() - 0.4f, location2.z());
			client2.moveHorizontalFully(EntityChangeMove.Direction.SOUTH, currentTimeMillis[0]);
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
		_compareLocation(location1, listener1.getLocalEntity().location());
		_compareLocation(location1, listener2.otherEntities.get(clientId1).location());
		
		serverTickNumber = server.waitForTicksToPass(5L);
		client1.waitForTick(serverTickNumber, currentTimeMillis[0]);
		client2.waitForTick(serverTickNumber, currentTimeMillis[0]);
		currentTimeMillis[0] += ServerRunner.DEFAULT_MILLIS_PER_TICK;
		_compareLocation(location2, listener1.otherEntities.get(clientId2).location());
		_compareLocation(location2, listener2.getLocalEntity().location());
		
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


	private static class _ClientListener implements ClientProcess.IListener
	{
		public final Map<CuboidAddress, IReadOnlyCuboidData> cuboids = new HashMap<>();
		private Entity _thisEntity = null;
		public final Map<Integer, PartialEntity> otherEntities = new HashMap<>();
		public int assignedEntityId;
		
		public Entity getLocalEntity()
		{
			Assert.assertEquals(this.assignedEntityId, _thisEntity.id());
			return _thisEntity;
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
	}
}
