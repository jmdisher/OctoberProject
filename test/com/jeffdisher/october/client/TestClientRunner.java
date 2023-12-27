package com.jeffdisher.october.client;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.changes.EndBreakBlockChange;
import com.jeffdisher.october.changes.IEntityChange;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.EntityActionValidator;
import com.jeffdisher.october.mutations.BreakBlockMutation;
import com.jeffdisher.october.registries.AspectRegistry;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
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
		EndBreakBlockChange longRunningAction = new EndBreakBlockChange(changeLocation, ItemRegistry.STONE.number());
		runner.beginBreakBlock(changeLocation, System.currentTimeMillis());
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
		BreakBlockMutation mutation = new BreakBlockMutation(changeLocation, BlockAspect.STONE);
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


	private static class TestAdapter implements IClientAdapter
	{
		public IClientAdapter.IListener client;
		public IEntityChange toSend;
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
		public void sendChange(IEntityChange change, long commitLevel)
		{
			this.toSend = change;
			this.commitLevel = commitLevel;
		}
	}

	private static class TestProjection implements SpeculativeProjection.IProjectionListener
	{
		public Map<Integer, Entity> loadedEnties = new HashMap<>();
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
