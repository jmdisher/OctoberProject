package com.jeffdisher.october.client;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.aspects.InventoryAspect;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.EntityActionValidator;
import com.jeffdisher.october.mutations.BreakBlockMutation;
import com.jeffdisher.october.mutations.EndBreakBlockChange;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.registries.AspectRegistry;
import com.jeffdisher.october.registries.Craft;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.worldgen.CuboidGenerator;


public class TestClientRunner
{
	@Test
	public void testInitialConnection() throws Throwable
	{
		TestAdapter network = new TestAdapter();
		TestProjection projection = new TestProjection();
		ClientListener clientListener = new ClientListener();
		ClientRunner runner = new ClientRunner(network, projection, clientListener);
		
		// Connect them.
		int clientId = 1;
		network.client.adapterConnected(clientId);
		runner.runPendingCalls(System.currentTimeMillis());
		Assert.assertEquals(clientId, clientListener.assignedLocalEntityId);
		
		// Send them an entity.
		network.client.receivedEntity(EntityActionValidator.buildDefaultEntity(clientId));
		runner.runPendingCalls(System.currentTimeMillis());
		// (this requires and end of tick for the projection to be rebuilt)
		network.client.receivedEndOfTick(1L, 0L);
		runner.runPendingCalls(System.currentTimeMillis());
		Assert.assertEquals(clientId, projection.loadedEnties.get(clientId).id());
		
		// Disconnect them.
		network.client.adapterDisconnected();
		runner.runPendingCalls(System.currentTimeMillis());
		Assert.assertEquals(0, clientListener.assignedLocalEntityId);
	}

	@Test
	public void observeOtherClient() throws Throwable
	{
		TestAdapter network = new TestAdapter();
		TestProjection projection = new TestProjection();
		ClientListener clientListener = new ClientListener();
		ClientRunner runner = new ClientRunner(network, projection, clientListener);
		
		// Connect them.
		int clientId = 1;
		network.client.adapterConnected(clientId);
		runner.runPendingCalls(System.currentTimeMillis());
		Assert.assertEquals(clientId, clientListener.assignedLocalEntityId);
		
		// Send them their own entity and another one.
		network.client.receivedEntity(EntityActionValidator.buildDefaultEntity(clientId));
		network.client.receivedEntity(EntityActionValidator.buildDefaultEntity(2));
		runner.runPendingCalls(System.currentTimeMillis());
		// (this requires and end of tick for the projection to be rebuilt)
		network.client.receivedEndOfTick(1L, 0L);
		runner.runPendingCalls(System.currentTimeMillis());
		Assert.assertEquals(clientId, projection.loadedEnties.get(clientId).id());
		Assert.assertEquals(2, projection.loadedEnties.get(2).id());
		
		// Disconnect them.
		network.client.adapterDisconnected();
		runner.runPendingCalls(System.currentTimeMillis());
		Assert.assertEquals(0, clientListener.assignedLocalEntityId);
	}

	@Test
	public void multiPhase() throws Throwable
	{
		TestAdapter network = new TestAdapter();
		TestProjection projection = new TestProjection();
		ClientListener clientListener = new ClientListener();
		ClientRunner runner = new ClientRunner(network, projection, clientListener);
		
		// Connect them.
		int clientId = 1;
		network.client.adapterConnected(clientId);
		runner.runPendingCalls(System.currentTimeMillis());
		Assert.assertEquals(clientId, clientListener.assignedLocalEntityId);
		
		// Send them an entity and a cuboid.
		network.client.receivedEntity(EntityActionValidator.buildDefaultEntity(clientId));
		CuboidAddress cuboidAddress = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(cuboidAddress, ItemRegistry.STONE);
		network.client.receivedCuboid(cuboid);
		network.client.receivedEndOfTick(1L, 0L);
		runner.runPendingCalls(System.currentTimeMillis());
		Assert.assertTrue(projection.loadedCuboids.containsKey(cuboidAddress));
		
		// Start a multi-phase locally.
		AbsoluteLocation changeLocation = new AbsoluteLocation(0, 0, 0);
		// (we create the change we expect the ClientRunner to create).
		EndBreakBlockChange longRunningAction = new EndBreakBlockChange(changeLocation, ItemRegistry.STONE);
		runner.beginBreakBlock(changeLocation, ItemRegistry.STONE, System.currentTimeMillis());
		// (they only send this after the next tick).
		network.client.receivedEndOfTick(2L, 1L);
		runner.runPendingCalls(System.currentTimeMillis());
		
		// Observe that this came out in the network and then send back the 3 ticks associated with it, applying each.
		Assert.assertTrue(network.toSend instanceof EndBreakBlockChange);
		Assert.assertTrue(1L == network.commitLevel);
		
		// The server won't send anything back until this runs.
		network.client.receivedEndOfTick(3L, 1L);
		runner.runPendingCalls(System.currentTimeMillis());
		
		// It will send it once complete.
		network.client.receivedChange(clientId, longRunningAction);
		network.client.receivedEndOfTick(4L, 1L);
		runner.runPendingCalls(System.currentTimeMillis());
		
		// Finally, we will see the actual mutation to break the block.
		BreakBlockMutation mutation = new BreakBlockMutation(changeLocation, ItemRegistry.STONE);
		network.client.receivedMutation(mutation);
		network.client.receivedEndOfTick(5L, 1L);
		runner.runPendingCalls(System.currentTimeMillis());
		
		// Verify the final state of the projection.
		Assert.assertEquals(BlockAspect.AIR, projection.loadedCuboids.get(cuboidAddress).getData15(AspectRegistry.BLOCK, changeLocation.getBlockAddress()));
		
		// Disconnect them.
		network.client.adapterDisconnected();
		runner.runPendingCalls(System.currentTimeMillis());
		Assert.assertEquals(0, clientListener.assignedLocalEntityId);
	}

	@Test
	public void accidentalInterruption() throws Throwable
	{
		// Verify that we see assertion failures if there is still an in-progress activity when we ask the ClientRunner to do something new.
		TestAdapter network = new TestAdapter();
		TestProjection projection = new TestProjection();
		ClientListener clientListener = new ClientListener();
		ClientRunner runner = new ClientRunner(network, projection, clientListener);
		
		// Connect them and send a default entity and basic cuboid.
		int clientId = 1;
		long currentTimeMillis = 1L;
		network.client.adapterConnected(clientId);
		runner.runPendingCalls(currentTimeMillis);
		Assert.assertEquals(clientId, clientListener.assignedLocalEntityId);
		network.client.receivedEntity(EntityActionValidator.buildDefaultEntity(clientId));
		network.client.receivedCuboid(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ItemRegistry.AIR));
		network.client.receivedCuboid(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)-1), ItemRegistry.STONE));
		network.client.receivedEndOfTick(1L, 0L);
		runner.runPendingCalls(currentTimeMillis);
		
		// Ask them to start crafting something, since that will put them in a pending activity.
		runner.craft(Craft.LOG_TO_PLANKS, currentTimeMillis);
		
		// Make sure that any change made now will assert fail.
		Assert.assertTrue(runner.isActivityInProgress(currentTimeMillis));
		AbsoluteLocation changeLocation = new AbsoluteLocation(0, 0, -1);
		boolean didFail = false;
		try
		{
			runner.beginBreakBlock(changeLocation, ItemRegistry.STONE, currentTimeMillis);
		}
		catch (AssertionError e)
		{
			didFail = true;
		}
		Assert.assertTrue(didFail);
		
		currentTimeMillis += 100L;
		didFail = false;
		try
		{
			runner.moveHorizontal(0.2f, 0.0f, currentTimeMillis);
		}
		catch (AssertionError e)
		{
			didFail = true;
		}
		Assert.assertTrue(didFail);
		
		// Now, advance time and verify that we can call one of these.
		currentTimeMillis += 1000L;
		Assert.assertFalse(runner.isActivityInProgress(currentTimeMillis));
		runner.beginBreakBlock(changeLocation, ItemRegistry.STONE, currentTimeMillis);
	}

	@Test
	public void jumpAgainstWall() throws Throwable
	{
		// Verify that we see assertion failures if there is still an in-progress activity when we ask the ClientRunner to do something new.
		TestAdapter network = new TestAdapter();
		TestProjection projection = new TestProjection();
		ClientListener clientListener = new ClientListener();
		ClientRunner runner = new ClientRunner(network, projection, clientListener);
		
		// Connect them and send a default entity and basic cuboid.
		int clientId = 1;
		long currentTimeMillis = 1L;
		network.client.adapterConnected(clientId);
		runner.runPendingCalls(currentTimeMillis);
		Assert.assertEquals(clientId, clientListener.assignedLocalEntityId);
		network.client.receivedEntity(EntityActionValidator.buildDefaultEntity(clientId));
		// We will stand on the ground, in air, but there will be a wall directly to the West.
		network.client.receivedCuboid(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)-1), ItemRegistry.STONE));
		network.client.receivedCuboid(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)-1, (short)0, (short)-1), ItemRegistry.STONE));
		network.client.receivedCuboid(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ItemRegistry.AIR));
		network.client.receivedCuboid(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)-1, (short)0, (short)0), ItemRegistry.STONE));
		network.client.receivedEndOfTick(1L, 0L);
		runner.runPendingCalls(currentTimeMillis);
		
		// Jump and then try to move to the West and observe the updated location.
		runner.jump(currentTimeMillis);
		currentTimeMillis += 100L;
		runner.moveHorizontal(-0.2f, 0.0f, currentTimeMillis);
		
		// See where they are - we expect them to have jumped slightly, despite hitting the wall.
		EntityLocation location = projection.loadedEnties.get(clientId).location();
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.49f), location);
	}

	@Test
	public void fallAndStop() throws Throwable
	{
		TestAdapter network = new TestAdapter();
		TestProjection projection = new TestProjection();
		ClientListener clientListener = new ClientListener();
		ClientRunner runner = new ClientRunner(network, projection, clientListener);
		
		// Connect them and send a default entity and basic cuboid.
		int clientId = 1;
		long currentTimeMillis = 1L;
		network.client.adapterConnected(clientId);
		runner.runPendingCalls(currentTimeMillis);
		Assert.assertEquals(clientId, clientListener.assignedLocalEntityId);
		// We want to position ourselves above the ground and drop onto the ground and observe that we no longer move.
		Entity entity = new Entity(clientId
				, new EntityLocation(0.0f, 0.0f, 2.0f)
				, 0.0f
				, EntityActionValidator.DEFAULT_VOLUME
				, EntityActionValidator.DEFAULT_BLOCKS_PER_TICK_SPEED
				, Inventory.start(InventoryAspect.CAPACITY_PLAYER).finish()
				, null
				, null
		);
		network.client.receivedEntity(entity);
		network.client.receivedCuboid(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)-1), ItemRegistry.STONE));
		network.client.receivedCuboid(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ItemRegistry.AIR));
		network.client.receivedEndOfTick(1L, 0L);
		runner.runPendingCalls(currentTimeMillis);
		
		// Allow ourselves to fall onto the ground with the expected number of ticks.
		int expectedChangeCount = 7;
		for (int i = 0; i < expectedChangeCount; ++i)
		{
			currentTimeMillis += 100L;
			runner.doNothing(currentTimeMillis);
		}
		
		// We should no be standing on the floor.
		EntityLocation location = projection.loadedEnties.get(clientId).location();
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), location);
		
		// Make sure we won't send another action.
		long changeCount = projection.allEntityChangeCount;
		Assert.assertEquals(expectedChangeCount, changeCount);
		currentTimeMillis += 100L;
		runner.doNothing(currentTimeMillis);
		Assert.assertEquals(changeCount, projection.allEntityChangeCount);
	}


	private static class TestAdapter implements IClientAdapter
	{
		public IClientAdapter.IListener client;
		public IMutationEntity toSend;
		public long commitLevel;
		@Override
		public void connectAndStartListening(IListener listener)
		{
			Assert.assertNull(this.client);
			this.client = listener;
		}
		@Override
		public void disconnect()
		{
		}
		@Override
		public void sendChange(IMutationEntity change, long commitLevel)
		{
			this.toSend = change;
			this.commitLevel = commitLevel;
		}
	}

	private static class TestProjection implements SpeculativeProjection.IProjectionListener
	{
		public Map<Integer, Entity> loadedEnties = new HashMap<>();
		public int allEntityChangeCount = 0;
		public Map<CuboidAddress, IReadOnlyCuboidData> loadedCuboids = new HashMap<>();
		@Override
		public void cuboidDidLoad(IReadOnlyCuboidData cuboid)
		{
			CuboidAddress cuboidAddress = cuboid.getCuboidAddress();
			Assert.assertFalse(this.loadedCuboids.containsKey(cuboidAddress));
			this.loadedCuboids.put(cuboidAddress, cuboid);
		}
		@Override
		public void cuboidDidChange(IReadOnlyCuboidData cuboid)
		{
			CuboidAddress cuboidAddress = cuboid.getCuboidAddress();
			Assert.assertTrue(this.loadedCuboids.containsKey(cuboidAddress));
			this.loadedCuboids.put(cuboidAddress, cuboid);
		}
		@Override
		public void cuboidDidUnload(CuboidAddress address)
		{
		}
		@Override
		public void entityDidLoad(Entity entity)
		{
			int id = entity.id();
			Assert.assertFalse(this.loadedEnties.containsKey(id));
			this.loadedEnties.put(id, entity);
		}
		@Override
		public void entityDidChange(Entity entity)
		{
			int id = entity.id();
			Assert.assertTrue(this.loadedEnties.containsKey(id));
			this.loadedEnties.put(id, entity);
			this.allEntityChangeCount += 1;
		}
		@Override
		public void entityDidUnload(int id)
		{
			Assert.assertTrue(this.loadedEnties.containsKey(id));
			this.loadedEnties.remove(id);
		}
	}

	private static class ClientListener implements ClientRunner.IListener
	{
		public int assignedLocalEntityId = 0;
		@Override
		public void clientDidConnectAndLogin(int assignedLocalEntityId)
		{
			Assert.assertEquals(0, this.assignedLocalEntityId);
			this.assignedLocalEntityId = assignedLocalEntityId;
		}
		@Override
		public void clientDisconnected()
		{
			Assert.assertNotEquals(0, this.assignedLocalEntityId);
			this.assignedLocalEntityId = 0;
		}
	}
}
