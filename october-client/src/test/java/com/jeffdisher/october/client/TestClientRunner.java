package com.jeffdisher.october.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.OrientationHelpers;
import com.jeffdisher.october.mutations.EntityChangeAccelerate;
import com.jeffdisher.october.mutations.EntityChangeIncrementalBlockBreak;
import com.jeffdisher.october.mutations.EntityChangeIncrementalBlockRepair;
import com.jeffdisher.october.mutations.EntityChangeJump;
import com.jeffdisher.october.mutations.EntityChangeMove;
import com.jeffdisher.october.mutations.EntityChangeSetOrientation;
import com.jeffdisher.october.mutations.EntityChangeUseSelectedItemOnSelf;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.mutations.MutationBlockIncrementalBreak;
import com.jeffdisher.october.mutations.MutationBlockIncrementalRepair;
import com.jeffdisher.october.mutations.MutationEntityPushItems;
import com.jeffdisher.october.mutations.MutationEntitySetEntity;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestClientRunner
{
	public static final long MILLIS_PER_TICK = 100L;
	private static Environment ENV;
	private static Item STONE_ITEM;
	private static Item LOG_ITEM;
	private static Item PLANK_ITEM;
	private static Block STONE;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		STONE_ITEM = ENV.items.getItemById("op.stone");
		LOG_ITEM = ENV.items.getItemById("op.log");
		PLANK_ITEM = ENV.items.getItemById("op.plank");
		STONE = ENV.blocks.fromItem(STONE_ITEM);
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
		network.client.adapterConnected(clientId, MILLIS_PER_TICK, MiscConstants.DEFAULT_CUBOID_VIEW_DISTANCE);
		int ticksPerDay = 1000;
		network.client.receivedConfigUpdate(ticksPerDay, 0);
		runner.runPendingCalls(currentTimeMillis);
		currentTimeMillis += 100L;
		Assert.assertEquals(clientId, clientListener.assignedLocalEntityId);
		Assert.assertEquals(ticksPerDay, clientListener.ticksPerDay);
		
		// Send them an entity.
		network.client.receivedFullEntity(MutableEntity.createForTest(clientId).freeze());
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
		Assert.assertTrue(projection.events.isEmpty());
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
		network.client.adapterConnected(clientId, MILLIS_PER_TICK, MiscConstants.DEFAULT_CUBOID_VIEW_DISTANCE);
		runner.runPendingCalls(currentTimeMillis);
		currentTimeMillis += 100L;
		Assert.assertEquals(clientId, clientListener.assignedLocalEntityId);
		
		// Send them their own entity and another one.
		network.client.receivedFullEntity(MutableEntity.createForTest(clientId).freeze());
		network.client.receivedPartialEntity(PartialEntity.fromEntity(MutableEntity.createForTest(2).freeze()));
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
		Assert.assertTrue(projection.events.isEmpty());
	}

	@Test
	public void multiPhase() throws Throwable
	{
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(cuboidAddress, STONE);
		CuboidData serverCuboid = CuboidData.mutableClone(cuboid);
		
		TestAdapter network = new TestAdapter();
		TestProjection projection = new TestProjection();
		ClientListener clientListener = new ClientListener();
		ClientRunner runner = new ClientRunner(network, projection, clientListener);
		
		// Connect them.
		int clientId = 1;
		long currentTimeMillis = 1000L;
		AbsoluteLocation changeLocation = new AbsoluteLocation(0, 0, 0);
		network.client.adapterConnected(clientId, MILLIS_PER_TICK, MiscConstants.DEFAULT_CUBOID_VIEW_DISTANCE);
		runner.runPendingCalls(currentTimeMillis);
		currentTimeMillis += 100L;
		Assert.assertEquals(clientId, clientListener.assignedLocalEntityId);
		
		// Send them an entity and a cuboid.
		network.client.receivedFullEntity(MutableEntity.createForTest(clientId).freeze());
		network.client.receivedCuboid(cuboid);
		network.client.receivedEndOfTick(1L, 0L);
		runner.runPendingCalls(currentTimeMillis);
		Assert.assertTrue(projection.loadedCuboids.containsKey(cuboidAddress));
		Assert.assertEquals(STONE_ITEM.number(), projection.loadedCuboids.get(cuboidAddress).getData15(AspectRegistry.BLOCK, changeLocation.getBlockAddress()));
		Assert.assertEquals((short)0, projection.loadedCuboids.get(cuboidAddress).getData15(AspectRegistry.DAMAGE, changeLocation.getBlockAddress()));
		
		// Start the multi-phase - we will assume that we need 2 hits to break this block, if we assign 1000 ms each time.
		currentTimeMillis += 1000L;
		runner.hitBlock(changeLocation, currentTimeMillis);
		currentTimeMillis += 100L;
		// (they only send this after the next tick).
		network.client.receivedEndOfTick(2L, 0L);
		runner.runPendingCalls(currentTimeMillis);
		
		// Observe that this came out in the network.
		Assert.assertTrue(network.toSend instanceof EntityChangeIncrementalBlockBreak);
		Assert.assertTrue(1L == network.commitLevel);
		
		// The would normally send a setBlock but we will just echo the normal mutation, to keep this simple.
		network.client.receivedEntityUpdate(clientId, FakeUpdateFactories.entityUpdate(projection.loadedCuboids, projection.authoritativeEntity, network.toSend));
		network.client.receivedEndOfTick(3L, 1L);
		runner.runPendingCalls(currentTimeMillis);
		network.client.receivedBlockUpdate(FakeUpdateFactories.blockUpdate(serverCuboid, new MutationBlockIncrementalBreak(changeLocation, (short)1000, MutationBlockIncrementalBreak.NO_STORAGE_ENTITY)));
		network.client.receivedEndOfTick(4L, 1L);
		runner.runPendingCalls(currentTimeMillis);
		
		// Verify that the block isn't broken, but is damaged.
		Assert.assertEquals(STONE_ITEM.number(), projection.loadedCuboids.get(cuboidAddress).getData15(AspectRegistry.BLOCK, changeLocation.getBlockAddress()));
		Assert.assertEquals((short)1000, projection.loadedCuboids.get(cuboidAddress).getData15(AspectRegistry.DAMAGE, changeLocation.getBlockAddress()));
		
		// Send the second hit and wait for the same operation.
		currentTimeMillis += 1000L;
		runner.hitBlock(changeLocation, currentTimeMillis);
		network.client.receivedEndOfTick(5L, 1L);
		runner.runPendingCalls(currentTimeMillis);
		Assert.assertTrue(network.toSend instanceof EntityChangeIncrementalBlockBreak);
		Assert.assertTrue(2L == network.commitLevel);
		network.client.receivedEntityUpdate(clientId, FakeUpdateFactories.entityUpdate(projection.loadedCuboids, projection.authoritativeEntity, network.toSend));
		network.client.receivedEndOfTick(6L, 2L);
		runner.runPendingCalls(currentTimeMillis);
		network.client.receivedBlockUpdate(FakeUpdateFactories.blockUpdate(serverCuboid, new MutationBlockIncrementalBreak(changeLocation, (short)1000, MutationBlockIncrementalBreak.NO_STORAGE_ENTITY)));
		network.client.receivedEndOfTick(7L, 2L);
		runner.runPendingCalls(currentTimeMillis);
		
		// Verify the final state of the projection.
		Assert.assertEquals(ENV.special.AIR.item().number(), projection.loadedCuboids.get(cuboidAddress).getData15(AspectRegistry.BLOCK, changeLocation.getBlockAddress()));
		
		// Disconnect them.
		network.client.adapterDisconnected();
		runner.runPendingCalls(System.currentTimeMillis());
		Assert.assertEquals(0, clientListener.assignedLocalEntityId);
		
		// We should see the event for the block being broken.
		Assert.assertEquals(1, projection.events.size());
		EventRecord event = projection.events.get(0);
		Assert.assertEquals(EventRecord.Type.BLOCK_BROKEN, event.type());
		Assert.assertEquals(EventRecord.Cause.NONE, event.cause());
		Assert.assertEquals(changeLocation, event.location());
		Assert.assertEquals(0, event.entityTarget());
		Assert.assertEquals(clientId, event.entitySource());
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
		network.client.adapterConnected(clientId, MILLIS_PER_TICK, MiscConstants.DEFAULT_CUBOID_VIEW_DISTANCE);
		runner.runPendingCalls(currentTimeMillis);
		currentTimeMillis += 100L;
		Assert.assertEquals(clientId, clientListener.assignedLocalEntityId);
		MutableEntity mutable = MutableEntity.createForTest(clientId);
		mutable.newInventory.addAllItems(LOG_ITEM, 2);
		Entity startEntity = mutable.freeze();
		network.client.receivedFullEntity(startEntity);
		network.client.receivedCuboid(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR));
		network.client.receivedCuboid(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), STONE));
		network.client.receivedEndOfTick(1L, 0L);
		runner.runPendingCalls(currentTimeMillis);
		
		// Start crafting, but not with enough time to complete it.
		currentTimeMillis += 500L;
		runner.craft(ENV.crafting.getCraftById("op.log_to_planks"), currentTimeMillis);
		// Verify that we now see this in the entity.
		Assert.assertNotNull(projection.thisEntity.localCraftOperation());
		
		currentTimeMillis += 100L;
		runner.moveHorizontalFully(EntityChangeMove.Direction.EAST, currentTimeMillis);
		// Verify that the craft operation was aborted and that we moved.
		Assert.assertNull(projection.thisEntity.localCraftOperation());
		Assert.assertEquals(2, projection.thisEntity.inventory().getCount(LOG_ITEM));
		float stepDistance = ENV.creatures.PLAYER.blocksPerSecond() / 10.0f;
		Assert.assertEquals(new EntityLocation(stepDistance, 0.0f, 0.0f), projection.thisEntity.location());
		Assert.assertTrue(projection.events.isEmpty());
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
		network.client.adapterConnected(clientId, MILLIS_PER_TICK, MiscConstants.DEFAULT_CUBOID_VIEW_DISTANCE);
		runner.runPendingCalls(currentTimeMillis);
		currentTimeMillis += 100L;
		Assert.assertEquals(clientId, clientListener.assignedLocalEntityId);
		network.client.receivedFullEntity(MutableEntity.createForTest(clientId).freeze());
		// We will stand on the ground, in air, but there will be a wall directly to the West.
		network.client.receivedCuboid(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), STONE));
		network.client.receivedCuboid(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(-1, 0, -1), STONE));
		network.client.receivedCuboid(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR));
		network.client.receivedCuboid(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(-1, 0, 0), STONE));
		network.client.receivedEndOfTick(1L, 0L);
		runner.runPendingCalls(currentTimeMillis);
		currentTimeMillis += 100L;
		
		// Jump and then try to move to the West and observe the updated location.
		EntityChangeJump<IMutablePlayerEntity> jumpChange = new EntityChangeJump<>();
		runner.commonApplyEntityAction(jumpChange, currentTimeMillis);
		currentTimeMillis += 100L;
		runner.moveHorizontalFully(EntityChangeMove.Direction.WEST, currentTimeMillis);
		currentTimeMillis += 100L;
		
		// See where they are - we expect them to have jumped slightly, despite hitting the wall.
		EntityLocation location = projection.thisEntity.location();
		Assert.assertEquals(0.0f, location.x(), 0.0001f);
		Assert.assertEquals(0.0f, location.y(), 0.0001f);
		Assert.assertEquals(0.44f, location.z(), 0.0001f);
		Assert.assertTrue(projection.events.isEmpty());
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
		network.client.adapterConnected(clientId, MILLIS_PER_TICK, MiscConstants.DEFAULT_CUBOID_VIEW_DISTANCE);
		runner.runPendingCalls(currentTimeMillis);
		currentTimeMillis += 100L;
		Assert.assertEquals(clientId, clientListener.assignedLocalEntityId);
		// We want to position ourselves above the ground and drop onto the ground and observe that we no longer move.
		MutableEntity mutable = MutableEntity.createForTest(clientId);
		mutable.newLocation = new EntityLocation(0.0f, 0.0f, 2.0f);
		Entity entity = mutable.freeze();
		network.client.receivedFullEntity(entity);
		network.client.receivedCuboid(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), STONE));
		network.client.receivedCuboid(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR));
		network.client.receivedEndOfTick(1L, 0L);
		runner.runPendingCalls(currentTimeMillis);
		
		// Allow ourselves to fall onto the ground with the expected number of ticks.
		int expectedChangeCount = 7;
		for (int i = 0; i < expectedChangeCount; ++i)
		{
			currentTimeMillis += 100L;
			runner.doNothing(currentTimeMillis);
		}
		
		// We should now be standing on the floor.
		EntityLocation location = projection.thisEntity.location();
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, -0.0f), location);
		Assert.assertEquals(0.0f, projection.thisEntity.velocity().z(), 0.01f);
		
		// Make sure we won't send another action.
		long changeCount = projection.allEntityChangeCount;
		Assert.assertEquals(expectedChangeCount, changeCount);
		currentTimeMillis += 100L;
		runner.doNothing(currentTimeMillis);
		Assert.assertEquals(changeCount, projection.allEntityChangeCount);
		Assert.assertTrue(projection.events.isEmpty());
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
		network.client.adapterConnected(clientId, MILLIS_PER_TICK, MiscConstants.DEFAULT_CUBOID_VIEW_DISTANCE);
		runner.runPendingCalls(currentTimeMillis);
		currentTimeMillis += 100L;
		Assert.assertEquals(clientId, clientListener.assignedLocalEntityId);
		MutableEntity mutable = MutableEntity.createForTest(clientId);
		mutable.newInventory.addAllItems(LOG_ITEM, 2);
		int logKey = mutable.newInventory.getIdOfStackableType(LOG_ITEM);
		Entity startEntity = mutable.freeze();
		network.client.receivedFullEntity(startEntity);
		network.client.receivedCuboid(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR));
		// We will just make one of the cuboids out of crafting tables to give us somewhere to craft.
		network.client.receivedCuboid(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), ENV.blocks.fromItem(ENV.items.getItemById("op.crafting_table"))));
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
		Assert.assertEquals(2, proxy.getInventory().getCount(PLANK_ITEM));
		Assert.assertTrue(projection.events.isEmpty());
	}

	@Test
	public void walkWithFrameRate() throws Throwable
	{
		// Walk in a horizontal path emulating the frame rate-based updates from a client (17 ms).
		TestAdapter network = new TestAdapter();
		TestProjection projection = new TestProjection();
		ClientListener clientListener = new ClientListener();
		ClientRunner runner = new ClientRunner(network, projection, clientListener);
		
		// Connect them and send a default entity and basic cuboid.
		int clientId = 1;
		long currentTimeMillis = 100L;
		network.client.adapterConnected(clientId, MILLIS_PER_TICK, MiscConstants.DEFAULT_CUBOID_VIEW_DISTANCE);
		runner.runPendingCalls(currentTimeMillis);
		currentTimeMillis += 100L;
		Assert.assertEquals(clientId, clientListener.assignedLocalEntityId);
		network.client.receivedFullEntity(MutableEntity.createForTest(clientId).freeze());
		// We will stand on the ground, in air, but there will be a wall directly to the West.
		network.client.receivedCuboid(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), STONE));
		network.client.receivedCuboid(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR));
		network.client.receivedEndOfTick(1L, 0L);
		runner.runPendingCalls(currentTimeMillis);
		
		// Walk east for 300 frames.
		runner.moveHorizontalFully(EntityChangeMove.Direction.EAST, currentTimeMillis);
		EntityLocation afterMove = projection.thisEntity.location();
		for (int i = 0; i < 300; ++i)
		{
			network.client.receivedEndOfTick(i + 2L, i);
			EntityLocation afterTick = projection.thisEntity.location();
			// These values should be the same since the projection should be maintained on top.
			Assert.assertEquals(afterMove, afterTick);
			currentTimeMillis += 17L;
			runner.moveHorizontalFully(EntityChangeMove.Direction.EAST, currentTimeMillis);
			afterMove = projection.thisEntity.location();
			Assert.assertNotNull(network.toSend);
			network.client.receivedEntityUpdate(clientId, FakeUpdateFactories.entityUpdate(projection.loadedCuboids, projection.authoritativeEntity, network.toSend));
			network.toSend = null;
		}
		// Compare this walked distance to what we have experimentally verified.
		Assert.assertEquals(new EntityLocation(21.0f, 0.0f, 0.0f), projection.thisEntity.location());
		Assert.assertTrue(projection.events.isEmpty());
	}

	@Test
	public void cooldownOnEating() throws Throwable
	{
		// We will try to eat multiple pieces of bread, showing that there is a cooldown period between such "use" operations.
		TestAdapter network = new TestAdapter();
		TestProjection projection = new TestProjection();
		ClientListener clientListener = new ClientListener();
		ClientRunner runner = new ClientRunner(network, projection, clientListener);
		
		// Connect them and send a default entity and basic cuboid.
		int clientId = 1;
		long currentTimeMillis = 100L;
		network.client.adapterConnected(clientId, MILLIS_PER_TICK, MiscConstants.DEFAULT_CUBOID_VIEW_DISTANCE);
		runner.runPendingCalls(currentTimeMillis);
		currentTimeMillis += 100L;
		Assert.assertEquals(clientId, clientListener.assignedLocalEntityId);
		Item bread = ENV.items.getItemById("op.bread");
		MutableEntity mutable = MutableEntity.createForTest(clientId);
		mutable.newInventory.addAllItems(bread, 2);
		int itemKey = mutable.newInventory.getIdOfStackableType(bread);
		mutable.setSelectedKey(itemKey);
		Entity startEntity = mutable.freeze();
		network.client.receivedFullEntity(startEntity);
		network.client.receivedCuboid(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR));
		// We will just make one of the cuboids out of crafting tables to give us somewhere to craft.
		network.client.receivedCuboid(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), STONE));
		network.client.receivedEndOfTick(1L, 0L);
		runner.runPendingCalls(currentTimeMillis);
		
		// Eat the bread, showing that it worked.
		EntityChangeUseSelectedItemOnSelf eatChange = new EntityChangeUseSelectedItemOnSelf();
		currentTimeMillis += 100L;
		runner.commonApplyEntityAction(eatChange, currentTimeMillis);
		Assert.assertEquals(1, projection.thisEntity.inventory().getCount(bread));
		
		// Show that another attempt fails.
		runner.commonApplyEntityAction(eatChange, currentTimeMillis);
		Assert.assertEquals(1, projection.thisEntity.inventory().getCount(bread));
		
		// Unless we pass some time.
		currentTimeMillis += EntityChangeUseSelectedItemOnSelf.COOLDOWN_MILLIS;
		runner.commonApplyEntityAction(eatChange, currentTimeMillis);
		Assert.assertEquals(0, projection.thisEntity.inventory().getCount(bread));
		Assert.assertTrue(projection.events.isEmpty());
	}

	@Test
	public void orientationAcceleration() throws Throwable
	{
		// Show that the orientation and acceleration helpers work.
		TestAdapter network = new TestAdapter();
		TestProjection projection = new TestProjection();
		ClientListener clientListener = new ClientListener();
		ClientRunner runner = new ClientRunner(network, projection, clientListener);
		
		// Connect them and send a default entity and basic cuboid.
		int clientId = 1;
		long currentTimeMillis = 100L;
		network.client.adapterConnected(clientId, MILLIS_PER_TICK, MiscConstants.DEFAULT_CUBOID_VIEW_DISTANCE);
		runner.runPendingCalls(currentTimeMillis);
		currentTimeMillis += 100L;
		Assert.assertEquals(clientId, clientListener.assignedLocalEntityId);
		network.client.receivedFullEntity(MutableEntity.createForTest(clientId).freeze());
		network.client.receivedCuboid(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR));
		network.client.receivedCuboid(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(-1, 0, 0), STONE));
		network.client.receivedEndOfTick(1L, 0L);
		runner.runPendingCalls(currentTimeMillis);
		currentTimeMillis += 100L;
		
		// Set orientation and walk.
		EntityChangeSetOrientation<IMutablePlayerEntity> jumpChange = new EntityChangeSetOrientation<>(OrientationHelpers.YAW_EAST, OrientationHelpers.PITCH_FLAT);
		runner.commonApplyEntityAction(jumpChange, currentTimeMillis);
		currentTimeMillis += 100L;
		runner.accelerateHorizontally(EntityChangeAccelerate.Relative.LEFT, currentTimeMillis);
		currentTimeMillis += 100L;
		
		EntityLocation location = projection.thisEntity.location();
		Assert.assertEquals(0.0f, location.x(), 0.0001f);
		Assert.assertEquals(0.32f, location.y(), 0.0001f);
		Assert.assertEquals(0.0f, location.z(), 0.0001f);
		Assert.assertEquals(OrientationHelpers.YAW_EAST, projection.thisEntity.yaw());
		Assert.assertEquals(OrientationHelpers.PITCH_FLAT, projection.thisEntity.pitch());
		Assert.assertTrue(projection.events.isEmpty());
	}

	@Test
	public void repair() throws Throwable
	{
		AbsoluteLocation changeLocation = new AbsoluteLocation(0, 0, 0);
		CuboidAddress cuboidAddress = changeLocation.getCuboidAddress();
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(cuboidAddress, STONE);
		cuboid.setData15(AspectRegistry.DAMAGE, changeLocation.getBlockAddress(), (short)150);
		CuboidData serverCuboid = CuboidData.mutableClone(cuboid);
		
		TestAdapter network = new TestAdapter();
		TestProjection projection = new TestProjection();
		ClientListener clientListener = new ClientListener();
		ClientRunner runner = new ClientRunner(network, projection, clientListener);
		
		// Connect them.
		int clientId = 1;
		long currentTimeMillis = 1000L;
		network.client.adapterConnected(clientId, MILLIS_PER_TICK, MiscConstants.DEFAULT_CUBOID_VIEW_DISTANCE);
		runner.runPendingCalls(currentTimeMillis);
		currentTimeMillis += 100L;
		Assert.assertEquals(clientId, clientListener.assignedLocalEntityId);
		
		// Send them an entity and a cuboid.
		network.client.receivedFullEntity(MutableEntity.createForTest(clientId).freeze());
		network.client.receivedCuboid(cuboid);
		network.client.receivedEndOfTick(1L, 0L);
		runner.runPendingCalls(currentTimeMillis);
		Assert.assertTrue(projection.loadedCuboids.containsKey(cuboidAddress));
		Assert.assertEquals(STONE_ITEM.number(), projection.loadedCuboids.get(cuboidAddress).getData15(AspectRegistry.BLOCK, changeLocation.getBlockAddress()));
		Assert.assertEquals((short)150, projection.loadedCuboids.get(cuboidAddress).getData15(AspectRegistry.DAMAGE, changeLocation.getBlockAddress()));
		
		// Run a repair call and observe the damage value change.
		currentTimeMillis += 100L;
		runner.repairBlock(changeLocation, currentTimeMillis);
		currentTimeMillis += 100L;
		// (they only send this after the next tick).
		network.client.receivedEndOfTick(2L, 0L);
		runner.runPendingCalls(currentTimeMillis);
		
		// Observe that this came out in the network.
		Assert.assertTrue(network.toSend instanceof EntityChangeIncrementalBlockRepair);
		Assert.assertTrue(1L == network.commitLevel);
		
		// They would normally send a setBlock but we will just echo the normal mutation, to keep this simple.
		network.client.receivedEntityUpdate(clientId, FakeUpdateFactories.entityUpdate(projection.loadedCuboids, projection.authoritativeEntity, network.toSend));
		network.client.receivedEndOfTick(3L, 1L);
		runner.runPendingCalls(currentTimeMillis);
		network.client.receivedBlockUpdate(FakeUpdateFactories.blockUpdate(serverCuboid, new MutationBlockIncrementalRepair(changeLocation, (short)100)));
		network.client.receivedEndOfTick(4L, 1L);
		runner.runPendingCalls(currentTimeMillis);
		
		// Verify that the block has been partially repaired.
		Assert.assertEquals(STONE_ITEM.number(), projection.loadedCuboids.get(cuboidAddress).getData15(AspectRegistry.BLOCK, changeLocation.getBlockAddress()));
		Assert.assertEquals((short)50, projection.loadedCuboids.get(cuboidAddress).getData15(AspectRegistry.DAMAGE, changeLocation.getBlockAddress()));
		
		// Disconnect them.
		network.client.adapterDisconnected();
		runner.runPendingCalls(System.currentTimeMillis());
		Assert.assertEquals(0, clientListener.assignedLocalEntityId);
		Assert.assertTrue(projection.events.isEmpty());
	}

	@Test
	public void testUpdateOptions() throws Throwable
	{
		long currentTimeMillis = 100L;
		TestAdapter network = new TestAdapter();
		TestProjection projection = new TestProjection();
		ClientListener clientListener = new ClientListener();
		ClientRunner runner = new ClientRunner(network, projection, clientListener);
		
		// Connect them.
		int clientId = 1;
		int viewDistanceLimit = 3;
		network.client.adapterConnected(clientId, MILLIS_PER_TICK, viewDistanceLimit);
		int ticksPerDay = 1000;
		network.client.receivedConfigUpdate(ticksPerDay, 0);
		runner.runPendingCalls(currentTimeMillis);
		currentTimeMillis += 100L;
		Assert.assertEquals(clientId, clientListener.assignedLocalEntityId);
		Assert.assertEquals(ticksPerDay, clientListener.ticksPerDay);
		
		// Send the updated options a few different ways and verify we get the right responses.
		Assert.assertTrue(runner.updateOptions(3));
		Assert.assertEquals(3, network.clientViewDistance);
		runner.runPendingCalls(currentTimeMillis);
		currentTimeMillis += 100L;
		
		Assert.assertFalse(runner.updateOptions(4));
		Assert.assertEquals(3, network.clientViewDistance);
		runner.runPendingCalls(currentTimeMillis);
		currentTimeMillis += 100L;
		
		Assert.assertTrue(runner.updateOptions(0));
		Assert.assertEquals(0, network.clientViewDistance);
		runner.runPendingCalls(currentTimeMillis);
		currentTimeMillis += 100L;
		
		// Disconnect them.
		network.client.adapterDisconnected();
		runner.runPendingCalls(currentTimeMillis);
		currentTimeMillis += 100L;
		Assert.assertEquals(0, clientListener.assignedLocalEntityId);
		Assert.assertTrue(projection.events.isEmpty());
	}

	@Test
	public void jumpAndFall() throws Throwable
	{
		// We want to see how we move, over time, after jumping in place.
		TestAdapter network = new TestAdapter();
		TestProjection projection = new TestProjection();
		ClientListener clientListener = new ClientListener();
		ClientRunner runner = new ClientRunner(network, projection, clientListener);
		
		// Connect them and send a default entity and basic cuboid.
		int clientId = 1;
		long currentTimeMillis = 100L;
		long tick = 1L;
		long serverCommit = 0L;
		network.client.adapterConnected(clientId, MILLIS_PER_TICK, MiscConstants.DEFAULT_CUBOID_VIEW_DISTANCE);
		runner.runPendingCalls(currentTimeMillis);
		currentTimeMillis += 100L;
		Assert.assertEquals(clientId, clientListener.assignedLocalEntityId);
		network.client.receivedFullEntity(MutableEntity.createForTest(clientId).freeze());
		// We will stand on the ground, in air, but there will be a wall directly to the West.
		network.client.receivedCuboid(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), STONE));
		network.client.receivedCuboid(CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR));
		network.client.receivedEndOfTick(tick, serverCommit);
		runner.runPendingCalls(currentTimeMillis);
		currentTimeMillis += 100L;
		
		// Jump and watch how we move over time.
		EntityChangeJump<IMutablePlayerEntity> jumpChange = new EntityChangeJump<>();
		runner.commonApplyEntityAction(jumpChange, currentTimeMillis);
		currentTimeMillis += 100L;
		
		// We will apply the entity changes one behind our local client.
		// Because we are capturing serverEntity from the local projection, this will fail in a sort of oscillating
		// state unless "doNothing" actually allows real forward progress.
		Entity serverEntity = projection.thisEntity;
		serverCommit = 1L;
		
		for (int i = 0; i < 11; ++i)
		{
			runner.doNothing(currentTimeMillis);
			
			// Grab this entity and apply server change.
			Entity temp = projection.thisEntity;
			tick += 1L;
			network.client.receivedEntityUpdate(clientId, new MutationEntitySetEntity(serverEntity));
			network.client.receivedEndOfTick(tick, serverCommit);
			runner.runPendingCalls(currentTimeMillis);
			serverEntity = temp;
			serverCommit += 1L;
			currentTimeMillis += 100L;
			
			// These views of the entity should be the same.
			Assert.assertEquals(serverEntity.location().z(), projection.thisEntity.location().z(), 0.01f);
		}
		
		// Make sure that we have touched down.
		Assert.assertEquals(0.0f, projection.thisEntity.location().z(), 0.01f);
		Assert.assertEquals(0.0f, projection.thisEntity.velocity().z(), 0.01f);
	}


	private static class TestAdapter implements IClientAdapter
	{
		public IClientAdapter.IListener client;
		public IMutationEntity<IMutablePlayerEntity> toSend;
		public long commitLevel;
		public int clientViewDistance = -1;
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
		@Override
		public void sendChatMessage(int targetClientId, String message)
		{
			throw new AssertionError("sendChatMessage");
		}
		@Override
		public void updateOptions(int clientViewDistance)
		{
			this.clientViewDistance = clientViewDistance;
		}
	}

	private static class TestProjection implements IProjectionListener
	{
		public Entity thisEntity = null;
		public Entity authoritativeEntity = null;
		public Map<Integer, PartialEntity> otherEnties = new HashMap<>();
		public int allEntityChangeCount = 0;
		public Map<CuboidAddress, IReadOnlyCuboidData> loadedCuboids = new HashMap<>();
		public long lastTickCompleted = 0L;
		public List<EventRecord> events = new ArrayList<>();
		@Override
		public void cuboidDidLoad(IReadOnlyCuboidData cuboid, ColumnHeightMap heightMap)
		{
			CuboidAddress cuboidAddress = cuboid.getCuboidAddress();
			Assert.assertFalse(this.loadedCuboids.containsKey(cuboidAddress));
			this.loadedCuboids.put(cuboidAddress, cuboid);
		}
		@Override
		public void cuboidDidChange(IReadOnlyCuboidData cuboid
				, ColumnHeightMap heightMap
				, Set<BlockAddress> changedBlocks
				, Set<Aspect<?, ?>> changedAspects
		)
		{
			CuboidAddress cuboidAddress = cuboid.getCuboidAddress();
			Assert.assertTrue(this.loadedCuboids.containsKey(cuboidAddress));
			Assert.assertFalse(changedBlocks.isEmpty());
			Assert.assertFalse(changedAspects.isEmpty());
			this.loadedCuboids.put(cuboidAddress, cuboid);
		}
		@Override
		public void cuboidDidUnload(CuboidAddress address)
		{
		}
		@Override
		public void thisEntityDidLoad(Entity authoritativeEntity)
		{
			int id = authoritativeEntity.id();
			Assert.assertFalse(this.otherEnties.containsKey(id));
			Assert.assertNull(this.thisEntity);
			this.thisEntity = authoritativeEntity;
			this.authoritativeEntity = authoritativeEntity;
		}
		@Override
		public void thisEntityDidChange(Entity authoritativeEntity, Entity projectedEntity)
		{
			int id = projectedEntity.id();
			Assert.assertFalse(this.otherEnties.containsKey(id));
			Assert.assertNotNull(this.thisEntity);
			this.thisEntity = projectedEntity;
			this.authoritativeEntity = authoritativeEntity;
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
		@Override
		public void tickDidComplete(long gameTick)
		{
			Assert.assertTrue(gameTick > this.lastTickCompleted);
			this.lastTickCompleted = gameTick;
		}
		@Override
		public void handleEvent(EventRecord event)
		{
			this.events.add(event);
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
		public int ticksPerDay = 0;
		public final Map<Integer, String> otherClients = new HashMap<>();
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
		@Override
		public void configUpdated(int ticksPerDay, int dayStartTick)
		{
			this.ticksPerDay = ticksPerDay;
		}
		@Override
		public void otherClientJoined(int clientId, String name)
		{
			Assert.assertFalse(this.otherClients.containsKey(clientId));
			this.otherClients.put(clientId, name);
		}
		@Override
		public void otherClientLeft(int clientId)
		{
			Assert.assertTrue(this.otherClients.containsKey(clientId));
			this.otherClients.remove(clientId);
		}
		@Override
		public void receivedChatMessage(int senderId, String message)
		{
			throw new AssertionError("chatMessage");
		}
	}
}
