package com.jeffdisher.october.client;

import java.util.HashMap;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.mutations.EntityChangeIncrementalBlockBreak;
import com.jeffdisher.october.mutations.EntityChangeJump;
import com.jeffdisher.october.mutations.EntityChangeMove;
import com.jeffdisher.october.mutations.EntityMutationWrapper;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.mutations.MutationBlockIncrementalBreak;
import com.jeffdisher.october.mutations.MutationEntityPushItems;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.worldgen.CuboidGenerator;


public class TestClientRunner
{
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
	public void testInitialConnection() throws Throwable
	{
		long currentTimeMillis = 100L;
		TestAdapter network = new TestAdapter();
		TestProjection projection = new TestProjection();
		ClientListener clientListener = new ClientListener();
		ClientRunner runner = new ClientRunner(network, projection, clientListener);
		
		// Connect them.
		int clientId = 1;
		network.client.adapterConnected(clientId);
		runner.runPendingCalls(currentTimeMillis);
		currentTimeMillis += 100L;
		Assert.assertEquals(clientId, clientListener.assignedLocalEntityId);
		
		// Send them an entity.
		network.client.receivedFullEntity(MutableEntity.create(clientId).freeze());
		runner.runPendingCalls(currentTimeMillis);
		currentTimeMillis += 100L;
		// (this requires and end of tick for the projection to be rebuilt)
		network.client.receivedEndOfTick(1L, 0L);
		runner.runPendingCalls(currentTimeMillis);
		currentTimeMillis += 100L;
		Assert.assertEquals(clientId, projection.thisEntity.id());
		
		// Disconnect them.
		network.client.adapterDisconnected();
		runner.runPendingCalls(currentTimeMillis);
		currentTimeMillis += 100L;
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
		long currentTimeMillis = 100L;
		int clientId = 1;
		network.client.adapterConnected(clientId);
		runner.runPendingCalls(currentTimeMillis);
		currentTimeMillis += 100L;
		Assert.assertEquals(clientId, clientListener.assignedLocalEntityId);
		
		// Send them their own entity and another one.
		network.client.receivedFullEntity(MutableEntity.create(clientId).freeze());
		network.client.receivedPartialEntity(PartialEntity.fromEntity(MutableEntity.create(2).freeze()));
		runner.runPendingCalls(currentTimeMillis);
		currentTimeMillis += 100L;
		// (this requires and end of tick for the projection to be rebuilt)
		network.client.receivedEndOfTick(1L, 0L);
		runner.runPendingCalls(currentTimeMillis);
		currentTimeMillis += 100L;
		Assert.assertEquals(clientId, projection.thisEntity.id());
		Assert.assertEquals(2, projection.otherEnties.get(2).id());
		
		// Disconnect them.
		network.client.adapterDisconnected();
		runner.runPendingCalls(currentTimeMillis);
		currentTimeMillis += 100L;
		Assert.assertEquals(0, clientListener.assignedLocalEntityId);
	}

	@Test
	public void multiPhase() throws Throwable
	{
		CuboidAddress cuboidAddress = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(cuboidAddress, STONE);
		
		TestAdapter network = new TestAdapter();
		TestProjection projection = new TestProjection();
		ClientListener clientListener = new ClientListener();
		ClientRunner runner = new ClientRunner(network, projection, clientListener);
		
		// Connect them.
		int clientId = 1;
		long currentTimeMillis = 1000L;
		AbsoluteLocation changeLocation = new AbsoluteLocation(0, 0, 0);
		network.client.adapterConnected(clientId);
		runner.runPendingCalls(currentTimeMillis);
		currentTimeMillis += 100L;
		Assert.assertEquals(clientId, clientListener.assignedLocalEntityId);
		
		// Send them an entity and a cuboid.
		network.client.receivedFullEntity(MutableEntity.create(clientId).freeze());
		network.client.receivedCuboid(cuboid);
		network.client.receivedEndOfTick(1L, 0L);
		runner.runPendingCalls(currentTimeMillis);
		Assert.assertTrue(projection.loadedCuboids.containsKey(cuboidAddress));
		Assert.assertEquals(ENV.items.STONE.number(), projection.loadedCuboids.get(cuboidAddress).getData15(AspectRegistry.BLOCK, changeLocation.getBlockAddress()));
		Assert.assertEquals((short)0, projection.loadedCuboids.get(cuboidAddress).getData15(AspectRegistry.DAMAGE, changeLocation.getBlockAddress()));
		
		// Start the multi-phase - we will assume that we need 2 hits to break this block, if we assign 500 ms each time.
		currentTimeMillis += 500L;
		runner.hitBlock(changeLocation, currentTimeMillis);
		currentTimeMillis += 100L;
		// (they only send this after the next tick).
		network.client.receivedEndOfTick(2L, 0L);
		runner.runPendingCalls(currentTimeMillis);
		
		// Observe that this came out in the network.
		Assert.assertTrue(network.toSend instanceof EntityChangeIncrementalBlockBreak);
		Assert.assertTrue(1L == network.commitLevel);
		
		// The would normally send a setBlock but we will just echo the normal mutation, to keep this simple.
		network.client.receivedEntityUpdate(clientId, new EntityMutationWrapper(network.toSend));
		network.client.receivedEndOfTick(3L, 1L);
		runner.runPendingCalls(currentTimeMillis);
		network.client.receivedBlockUpdate(FakeBlockUpdate.applyUpdate(cuboid, new MutationBlockIncrementalBreak(changeLocation, (short)500, MutationBlockIncrementalBreak.NO_STORAGE_ENTITY)));
		network.client.receivedEndOfTick(4L, 1L);
		runner.runPendingCalls(currentTimeMillis);
		
		// Verify that the block isn't broken, but is damaged.
		Assert.assertEquals(ENV.items.STONE.number(), projection.loadedCuboids.get(cuboidAddress).getData15(AspectRegistry.BLOCK, changeLocation.getBlockAddress()));
		Assert.assertEquals((short)500, projection.loadedCuboids.get(cuboidAddress).getData15(AspectRegistry.DAMAGE, changeLocation.getBlockAddress()));
		
		// Send the second hit and wait for the same operation.
		currentTimeMillis += 500L;
		runner.hitBlock(changeLocation, currentTimeMillis);
		network.client.receivedEndOfTick(5L, 1L);
		runner.runPendingCalls(currentTimeMillis);
		Assert.assertTrue(network.toSend instanceof EntityChangeIncrementalBlockBreak);
		Assert.assertTrue(2L == network.commitLevel);
		network.client.receivedEntityUpdate(clientId, new EntityMutationWrapper(network.toSend));
		network.client.receivedEndOfTick(6L, 2L);
		runner.runPendingCalls(currentTimeMillis);
		network.client.receivedBlockUpdate(FakeBlockUpdate.applyUpdate(cuboid, new MutationBlockIncrementalBreak(changeLocation, (short)500, MutationBlockIncrementalBreak.NO_STORAGE_ENTITY)));
		network.client.receivedEndOfTick(7L, 2L);
		runner.runPendingCalls(currentTimeMillis);
		
		// Verify the final state of the projection.
		Assert.assertEquals(ENV.items.AIR.number(), projection.loadedCuboids.get(cuboidAddress).getData15(AspectRegistry.BLOCK, changeLocation.getBlockAddress()));
		
		// Disconnect them.
		network.client.adapterDisconnected();
		runner.runPendingCalls(System.currentTimeMillis());
		Assert.assertEquals(0, clientListener.assignedLocalEntityId);
	}

	@Test
	public void accidentalInterruption() throws Throwable
	{
		// We will start a crafting operation and then move before it completes, verifying that it cancels it.
		TestAdapter network = new TestAdapter();
		TestProjection projection = new TestProjection();
		ClientListener clientListener = new ClientListener();
		ClientRunner runner = new ClientRunner(network, projection, clientListener);
		
		// Connect them and send a default entity and basic cuboid.
		int clientId = 1;
		long currentTimeMillis = 100L;
		network.client.adapterConnected(clientId);
		runner.runPendingCalls(currentTimeMillis);
		currentTimeMillis += 100L;
		Assert.assertEquals(clientId, clientListener.assignedLocalEntityId);
		MutableEntity mutable = MutableEntity.create(clientId);
		mutable.newInventory.addAllItems(ENV.items.LOG, 2);
		Entity startEntity = mutable.freeze();
		network.client.receivedFullEntity(startEntity);
		network.client.receivedCuboid(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.special.AIR));
		network.client.receivedCuboid(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)-1), STONE));
		network.client.receivedEndOfTick(1L, 0L);
		runner.runPendingCalls(currentTimeMillis);
		
		// Start crafting, but not with enough time to complete it.
		currentTimeMillis += 500L;
		runner.craft(ENV.crafting.getCraftById("op.log_to_planks"), currentTimeMillis);
		// Verify that we now see this in the entity.
		Assert.assertNotNull(projection.thisEntity.localCraftOperation());
		
		currentTimeMillis += 100L;
		runner.moveHorizontalFully(1.0f, 0.0f, currentTimeMillis);
		// Verify that the craft operation was aborted and that we moved.
		Assert.assertNull(projection.thisEntity.localCraftOperation());
		Assert.assertEquals(2, projection.thisEntity.inventory().getCount(ENV.items.LOG));
		float stepDistance = EntityChangeMove.ENTITY_MOVE_FLAT_LIMIT_PER_SECOND / 10.0f;
		Assert.assertEquals(new EntityLocation(stepDistance, 0.0f, 0.0f), projection.thisEntity.location());
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
		long currentTimeMillis = 100L;
		network.client.adapterConnected(clientId);
		runner.runPendingCalls(currentTimeMillis);
		currentTimeMillis += 100L;
		Assert.assertEquals(clientId, clientListener.assignedLocalEntityId);
		network.client.receivedFullEntity(MutableEntity.create(clientId).freeze());
		// We will stand on the ground, in air, but there will be a wall directly to the West.
		network.client.receivedCuboid(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)-1), STONE));
		network.client.receivedCuboid(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)-1, (short)0, (short)-1), STONE));
		network.client.receivedCuboid(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.special.AIR));
		network.client.receivedCuboid(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)-1, (short)0, (short)0), STONE));
		network.client.receivedEndOfTick(1L, 0L);
		runner.runPendingCalls(currentTimeMillis);
		currentTimeMillis += 100L;
		
		// Jump and then try to move to the West and observe the updated location.
		EntityChangeJump<IMutablePlayerEntity> jumpChange = new EntityChangeJump<>();
		runner.commonApplyEntityAction(jumpChange, currentTimeMillis);
		currentTimeMillis += 100L;
		runner.moveHorizontalFully(-1.0f, 0.0f, currentTimeMillis);
		currentTimeMillis += 100L;
		
		// See where they are - we expect them to have jumped slightly, despite hitting the wall.
		EntityLocation location = projection.thisEntity.location();
		Assert.assertEquals(0.0f, location.x(), 0.001f);
		Assert.assertEquals(0.0f, location.y(), 0.001f);
		Assert.assertEquals(0.441f, location.z(), 0.001f);
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
		long currentTimeMillis = 100L;
		network.client.adapterConnected(clientId);
		runner.runPendingCalls(currentTimeMillis);
		currentTimeMillis += 100L;
		Assert.assertEquals(clientId, clientListener.assignedLocalEntityId);
		// We want to position ourselves above the ground and drop onto the ground and observe that we no longer move.
		MutableEntity mutable = MutableEntity.create(clientId);
		mutable.newLocation = new EntityLocation(0.0f, 0.0f, 2.0f);
		Entity entity = mutable.freeze();
		network.client.receivedFullEntity(entity);
		network.client.receivedCuboid(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)-1), STONE));
		network.client.receivedCuboid(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.special.AIR));
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
		EntityLocation location = projection.thisEntity.location();
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, -0.0f), location);
		Assert.assertEquals(-6.86f, projection.thisEntity.zVelocityPerSecond(), 0.01f);
		
		// We will see one more change in order to cancel the z-velocity.
		currentTimeMillis += 100L;
		expectedChangeCount += 1;
		runner.doNothing(currentTimeMillis);
		location = projection.thisEntity.location();
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, -0.0f), location);
		Assert.assertEquals(0.0f, projection.thisEntity.zVelocityPerSecond(), 0.01f);
		
		// Make sure we won't send another action.
		long changeCount = projection.allEntityChangeCount;
		Assert.assertEquals(expectedChangeCount, changeCount);
		currentTimeMillis += 100L;
		runner.doNothing(currentTimeMillis);
		Assert.assertEquals(changeCount, projection.allEntityChangeCount);
	}

	@Test
	public void craftInTable() throws Throwable
	{
		// We will run a multi-step crafting operation in a crafting table.
		TestAdapter network = new TestAdapter();
		TestProjection projection = new TestProjection();
		ClientListener clientListener = new ClientListener();
		ClientRunner runner = new ClientRunner(network, projection, clientListener);
		
		// Connect them and send a default entity and basic cuboid.
		int clientId = 1;
		long currentTimeMillis = 100L;
		network.client.adapterConnected(clientId);
		runner.runPendingCalls(currentTimeMillis);
		currentTimeMillis += 100L;
		Assert.assertEquals(clientId, clientListener.assignedLocalEntityId);
		MutableEntity mutable = MutableEntity.create(clientId);
		mutable.newInventory.addAllItems(ENV.items.LOG, 2);
		int logKey = mutable.newInventory.getIdOfStackableType(ENV.items.LOG);
		Entity startEntity = mutable.freeze();
		network.client.receivedFullEntity(startEntity);
		network.client.receivedCuboid(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.special.AIR));
		// We will just make one of the cuboids out of crafting tables to give us somewhere to craft.
		network.client.receivedCuboid(CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)-1), ENV.blocks.fromItem(ENV.items.getItemById("op.crafting_table"))));
		network.client.receivedEndOfTick(1L, 0L);
		runner.runPendingCalls(currentTimeMillis);
		
		// Select a table and load an item into it.
		AbsoluteLocation table = new AbsoluteLocation(0, 0, -1);
		MutationEntityPushItems push = new MutationEntityPushItems(table, logKey, 1, Inventory.INVENTORY_ASPECT_INVENTORY);
		currentTimeMillis += 100L;
		runner.commonApplyEntityAction(push, currentTimeMillis);
		
		// Start crafting, but not with enough time to complete it (the table has 10x efficiency bonus).
		currentTimeMillis += 50L;
		runner.craftInBlock(table, ENV.crafting.getCraftById("op.log_to_planks"), currentTimeMillis);
		// Verify that we now see this is in progress in the block.
		BlockProxy proxy = projection.readBlock(table);
		Assert.assertEquals(500L, proxy.getCrafting().completedMillis());
		
		// Now, complete the craft.
		currentTimeMillis += 50L;
		runner.craftInBlock(table, null, currentTimeMillis);
		proxy = projection.readBlock(table);
		Assert.assertNull(proxy.getCrafting());
		Assert.assertEquals(2, proxy.getInventory().getCount(ENV.items.PLANK));
	}


	private static class TestAdapter implements IClientAdapter
	{
		public IClientAdapter.IListener client;
		public IMutationEntity<IMutablePlayerEntity> toSend;
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
		public void sendChange(IMutationEntity<IMutablePlayerEntity> change, long commitLevel)
		{
			this.toSend = change;
			this.commitLevel = commitLevel;
		}
	}

	private static class TestProjection implements SpeculativeProjection.IProjectionListener
	{
		public Entity thisEntity = null;
		public Map<Integer, PartialEntity> otherEnties = new HashMap<>();
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
		public void thisEntityDidLoad(Entity entity)
		{
			int id = entity.id();
			Assert.assertFalse(this.otherEnties.containsKey(id));
			Assert.assertNull(this.thisEntity);
			this.thisEntity = entity;
		}
		@Override
		public void thisEntityDidChange(Entity entity)
		{
			int id = entity.id();
			Assert.assertFalse(this.otherEnties.containsKey(id));
			Assert.assertNotNull(this.thisEntity);
			this.thisEntity = entity;
			this.allEntityChangeCount += 1;
		}
		@Override
		public void otherEntityDidLoad(PartialEntity entity)
		{
			int id = entity.id();
			Assert.assertFalse(this.otherEnties.containsKey(id));
			this.otherEnties.put(id, entity);
		}
		@Override
		public void otherEntityDidChange(PartialEntity entity)
		{
			int id = entity.id();
			Assert.assertTrue(this.otherEnties.containsKey(id));
			this.otherEnties.put(id, entity);
			this.allEntityChangeCount += 1;
		}
		@Override
		public void otherEntityDidUnload(int id)
		{
			Assert.assertTrue(this.otherEnties.containsKey(id));
			this.otherEnties.remove(id);
		}
		public BlockProxy readBlock(AbsoluteLocation block)
		{
			IReadOnlyCuboidData cuboid = this.loadedCuboids.get(block.getCuboidAddress());
			return new BlockProxy(block.getBlockAddress(), cuboid);
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
