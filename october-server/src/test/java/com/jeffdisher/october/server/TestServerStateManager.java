package com.jeffdisher.october.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.actions.EntityChangeTopLevelMovement;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.CuboidHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.HeightMapHelpers;
import com.jeffdisher.october.logic.OrientationHelpers;
import com.jeffdisher.october.mutations.IEntityUpdate;
import com.jeffdisher.october.mutations.IPartialEntityUpdate;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
import com.jeffdisher.october.net.PacketFromClient;
import com.jeffdisher.october.net.Packet_MutationEntityFromClient;
import com.jeffdisher.october.persistence.PackagedCuboid;
import com.jeffdisher.october.persistence.SuspendedCuboid;
import com.jeffdisher.october.persistence.SuspendedEntity;
import com.jeffdisher.october.subactions.MutationEntitySelectItem;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.CuboidColumnAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.types.WorldConfig;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestServerStateManager
{
	private static Environment ENV;
	private static EntityType COW;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		COW = ENV.creatures.getTypeById("op.cow");
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}


	@Test
	public void shutdown()
	{
		_Callouts callouts = new _Callouts();
		ServerStateManager manager = new ServerStateManager(callouts, ServerRunner.DEFAULT_MILLIS_PER_TICK);
		manager.setOwningThread();
		manager.shutdown();
	}

	@Test
	public void runEmptyTick()
	{
		_Callouts callouts = new _Callouts();
		ServerStateManager manager = new ServerStateManager(callouts, ServerRunner.DEFAULT_MILLIS_PER_TICK);
		manager.setOwningThread();
		TickRunner.Snapshot snapshot = _createEmptySnapshot();
		ServerStateManager.TickChanges changes = manager.setupNextTickAfterCompletion(snapshot);
		manager.shutdown();
		
		// This should request nothing be set up for the next tick.
		Assert.assertTrue(changes.newCuboids().isEmpty());
		Assert.assertTrue(changes.cuboidsToUnload().isEmpty());
		Assert.assertTrue(changes.newEntities().isEmpty());
		Assert.assertTrue(changes.entitiesToUnload().isEmpty());
	}

	@Test
	public void connectClient()
	{
		_Callouts callouts = new _Callouts();
		ServerStateManager manager = new ServerStateManager(callouts, ServerRunner.DEFAULT_MILLIS_PER_TICK);
		manager.setOwningThread();
		int clientId = 1;
		String clientName = "client";
		manager.clientConnected(clientId, clientName, 1);
		TickRunner.Snapshot snapshot = _createEmptySnapshot();
		
		ServerStateManager.TickChanges changes = manager.setupNextTickAfterCompletion(snapshot);
		Assert.assertEquals(1, callouts.requestedEntityIds.size());
		Assert.assertTrue(changes.newCuboids().isEmpty());
		Assert.assertTrue(changes.cuboidsToUnload().isEmpty());
		Assert.assertTrue(changes.newEntities().isEmpty());
		Assert.assertTrue(changes.entitiesToUnload().isEmpty());
		callouts.requestedEntityIds.clear();
		
		// Load this entity.
		callouts.loadedEntities.add(new SuspendedEntity(MutableEntity.createForTest(clientId).freeze(), List.of()));
		changes = manager.setupNextTickAfterCompletion(snapshot);
		Assert.assertTrue(changes.newCuboids().isEmpty());
		Assert.assertTrue(changes.cuboidsToUnload().isEmpty());
		Assert.assertEquals(1, changes.newEntities().size());
		Assert.assertTrue(changes.entitiesToUnload().isEmpty());
		Assert.assertTrue(callouts.requestedEntityIds.isEmpty());
		Assert.assertTrue(callouts.requestedCuboidAddresses.isEmpty());
		Assert.assertTrue(callouts.lastFinishedCommitPerClient.isEmpty());
		snapshot = _modifySnapshot(snapshot
				, _convertToCuboidMap(changes.newCuboids())
				, _convertToEntityMap(changes.newEntities())
				, snapshot.creatures()
				, _convertToCuboidHeightMap(changes.newCuboids())
				, Set.of()
		);
		
		// Verify that we see the surrounding cuboid load requests on the next tick.
		changes = manager.setupNextTickAfterCompletion(snapshot);
		Assert.assertTrue(changes.newCuboids().isEmpty());
		Assert.assertTrue(changes.cuboidsToUnload().isEmpty());
		Assert.assertTrue(changes.newEntities().isEmpty());
		Assert.assertTrue(changes.entitiesToUnload().isEmpty());
		Assert.assertTrue(callouts.requestedEntityIds.isEmpty());
		Assert.assertEquals(27, callouts.requestedCuboidAddresses.size());
		Assert.assertEquals(0L, callouts.lastFinishedCommitPerClient.get(clientId).longValue());
		Assert.assertTrue(callouts.cuboidsToTryWrite.isEmpty());
		Assert.assertEquals(1, callouts.entitiesToTryWrite.size());
		Assert.assertEquals(1, callouts.entitiesToTryWrite.iterator().next().entity().id());
		
		// Disconnect the client and see the entity unload.
		manager.clientDisconnected(clientId);
		changes = manager.setupNextTickAfterCompletion(snapshot);
		Assert.assertTrue(changes.newCuboids().isEmpty());
		Assert.assertTrue(changes.cuboidsToUnload().isEmpty());
		Assert.assertTrue(changes.newEntities().isEmpty());
		Assert.assertEquals(1, changes.entitiesToUnload().size());
		Assert.assertTrue(callouts.requestedEntityIds.isEmpty());
		Assert.assertEquals(27, callouts.requestedCuboidAddresses.size());
		Assert.assertEquals(0L, callouts.lastFinishedCommitPerClient.get(clientId).longValue());
		Assert.assertEquals(1, callouts.entitiesToWrite.size());
		callouts.entitiesToWrite.clear();
		snapshot = _modifySnapshot(snapshot
				, snapshot.cuboids()
				, Collections.emptyMap()
				, snapshot.creatures()
				, snapshot.completedHeightMaps()
				, Set.of()
		);
		
		// Load one of the requested cuboids and verify it appears as loaded.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(callouts.requestedCuboidAddresses.iterator().next(), ENV.special.AIR);
		callouts.loadedCuboids.add(new SuspendedCuboid<CuboidData>(cuboid
				, HeightMapHelpers.buildHeightMap(cuboid)
				, List.of()
				, List.of()
				, Map.of()
		));
		changes = manager.setupNextTickAfterCompletion(snapshot);
		Assert.assertEquals(1, changes.newCuboids().size());
		Assert.assertTrue(changes.cuboidsToUnload().isEmpty());
		Assert.assertTrue(changes.newEntities().isEmpty());
		Assert.assertTrue(changes.entitiesToUnload().isEmpty());
		Assert.assertEquals(27, callouts.requestedCuboidAddresses.size());
		Assert.assertEquals(0L, callouts.lastFinishedCommitPerClient.get(clientId).longValue());
		Assert.assertTrue(callouts.entitiesToWrite.isEmpty());
		Assert.assertTrue(callouts.cuboidsToWrite.isEmpty());
		snapshot = _modifySnapshot(snapshot
				, _convertToCuboidMap(changes.newCuboids())
				, snapshot.entities()
				, snapshot.creatures()
				, _convertToCuboidHeightMap(changes.newCuboids())
				, Set.of()
		);
		
		// Stall until the keep-alive timeout.
		for (int i = 0; i < (manager.test_getCuboidKeepAliveTicks() - 1); ++i)
		{
			changes = manager.setupNextTickAfterCompletion(snapshot);
			Assert.assertTrue(changes.newCuboids().isEmpty());
			Assert.assertEquals(0, changes.cuboidsToUnload().size());
			// (in this test, we aren't interested in periodic write-back).
			callouts.cuboidsToTryWrite.clear();
		}
		
		// In another call, it should appear as unloaded.
		changes = manager.setupNextTickAfterCompletion(snapshot);
		Assert.assertTrue(changes.newCuboids().isEmpty());
		Assert.assertEquals(1, changes.cuboidsToUnload().size());
		Assert.assertTrue(changes.newEntities().isEmpty());
		Assert.assertTrue(changes.entitiesToUnload().isEmpty());
		Assert.assertEquals(27, callouts.requestedCuboidAddresses.size());
		Assert.assertEquals(0L, callouts.lastFinishedCommitPerClient.get(clientId).longValue());
		Assert.assertTrue(callouts.entitiesToWrite.isEmpty());
		Assert.assertEquals(1, callouts.cuboidsToWrite.size());
		callouts.cuboidsToWrite.clear();
		
		manager.shutdown();
	}

	@Test
	public void disconnectWhileReadable()
	{
		// Connect a client, mark them as readable, then disconnect them, finally end a tick.
		_Callouts callouts = new _Callouts();
		ServerStateManager manager = new ServerStateManager(callouts, ServerRunner.DEFAULT_MILLIS_PER_TICK);
		manager.setOwningThread();
		int clientId = 1;
		String clientName = "client";
		manager.clientConnected(clientId, clientName, 1);
		boolean[] connectedRef = new boolean[] {true};
		TickRunner.Snapshot snapshot = _createEmptySnapshot();
		manager.setupNextTickAfterCompletion(snapshot);
		
		// We need to setup the callouts to not fully satisfy this.
		Entity entity = MutableEntity.createForTest(clientId).freeze();
		MutationEntitySelectItem subAction = new MutationEntitySelectItem(1);
		EntityChangeTopLevelMovement<IMutablePlayerEntity> change = new EntityChangeTopLevelMovement<>(entity.location()
			, entity.velocity()
			, EntityChangeTopLevelMovement.Intensity.STANDING
			, OrientationHelpers.YAW_NORTH
			, OrientationHelpers.PITCH_FLAT
			, subAction
		);
		Packet_MutationEntityFromClient packet = new Packet_MutationEntityFromClient(change, 1L);
		callouts.peekHandler = (PacketFromClient toRemove) -> {
			Assert.assertTrue(connectedRef[0]);
			return packet;
		};
		
		manager.clientReadReady(clientId);
		manager.clientDisconnected(clientId);
		connectedRef[0] = false;
		manager.setupNextTickAfterCompletion(snapshot);
	}

	@Test
	public void twoClientsSeeEachOther()
	{
		// We just want to connect 2 clients and verify that they see each other join and the first one to disconnect is seen.
		_Callouts callouts = new _Callouts();
		ServerStateManager manager = new ServerStateManager(callouts, ServerRunner.DEFAULT_MILLIS_PER_TICK);
		manager.setOwningThread();
		int clientId1 = 1;
		String clientName1 = "client1";
		int clientId2 = 2;
		String clientName2 = "client2";
		manager.clientConnected(clientId1, clientName1, 1);
		manager.clientConnected(clientId2, clientName2, 1);
		
		TickRunner.Snapshot snapshot = _createEmptySnapshot();
		manager.setupNextTickAfterCompletion(snapshot);
		
		callouts.loadedEntities.add(new SuspendedEntity(MutableEntity.createForTest(clientId1).freeze(), List.of()));
		callouts.loadedEntities.add(new SuspendedEntity(MutableEntity.createForTest(clientId2).freeze(), List.of()));
		manager.setupNextTickAfterCompletion(snapshot);
		Assert.assertEquals(1, callouts.joinedClients.get(clientId1).size());
		Assert.assertEquals(1, callouts.joinedClients.get(clientId2).size());
		Assert.assertEquals(clientName2, callouts.joinedClients.get(clientId1).get(clientId2));
		Assert.assertEquals(clientName1, callouts.joinedClients.get(clientId2).get(clientId1));
		
		manager.clientDisconnected(clientId1);
		manager.setupNextTickAfterCompletion(snapshot);
		Assert.assertEquals(0, callouts.joinedClients.get(clientId2).size());
		
		manager.clientDisconnected(clientId2);
		manager.setupNextTickAfterCompletion(snapshot);
	}

	@Test
	public void periodicWriteBack()
	{
		// Create a snapshot with an entity and some cuboids, showing what write-backs are requested based on different situations and tick numbers.
		_Callouts callouts = new _Callouts();
		ServerStateManager manager = new ServerStateManager(callouts, ServerRunner.DEFAULT_MILLIS_PER_TICK);
		manager.setOwningThread();
		int clientId = 1;
		manager.clientConnected(clientId, "client", 1);
		TickRunner.Snapshot snapshot = _createEmptySnapshot();
		
		// We need to run a tick so that the client load request is made.
		manager.setupNextTickAfterCompletion(snapshot);
		Assert.assertEquals(1, callouts.requestedEntityIds.size());
		
		// Allow the client load to be acknowledged.
		Entity entity = MutableEntity.createForTest(clientId).freeze();
		callouts.loadedEntities.add(new SuspendedEntity(entity, List.of()));
		manager.setupNextTickAfterCompletion(snapshot);
		
		CuboidAddress near = CuboidAddress.fromInt(0, 0, 0);
		CuboidData nearCuboid = CuboidGenerator.createFilledCuboid(near, ENV.special.AIR);
		CuboidAddress far = CuboidAddress.fromInt(10, 0, 0);
		CuboidData farCuboid = CuboidGenerator.createFilledCuboid(far, ENV.special.AIR);
		snapshot = _modifySnapshot(snapshot
				, Map.of(
						near, new TickRunner.SnapshotCuboid(nearCuboid, null, List.of(), Map.of()),
						far, new TickRunner.SnapshotCuboid(farCuboid, null, List.of(), Map.of())
				)
				, Map.of(1, new TickRunner.SnapshotEntity(entity, 1L, null, List.of()))
				, snapshot.creatures()
				, Map.of(
						near.getColumn(), ColumnHeightMap.build().consume(HeightMapHelpers.buildHeightMap(nearCuboid), near).freeze(),
						far.getColumn(), ColumnHeightMap.build().consume(HeightMapHelpers.buildHeightMap(farCuboid), far).freeze()
				)
				, Set.of()
		);
		
		// Note that we will try to unload far and write-back everything else due to the tick number.
		manager.test_setAlreadyAlive(snapshot.cuboids().keySet());
		manager.setupNextTickAfterCompletion(snapshot);
		
		// Note that we will need to wait for the keep-alive timeout to drop this.
		Assert.assertEquals(0, callouts.cuboidsToWrite.size());
		Assert.assertTrue(callouts.entitiesToWrite.isEmpty());
		Assert.assertEquals(2, callouts.cuboidsToTryWrite.size());
		Assert.assertEquals(1, callouts.entitiesToTryWrite.size());
		callouts.cuboidsToWrite.clear();
		callouts.entitiesToWrite.clear();
		callouts.cuboidsToTryWrite.clear();
		callouts.entitiesToTryWrite.clear();
		for (int i = 0; i < (manager.test_getCuboidKeepAliveTicks() - 2); ++i)
		{
			manager.setupNextTickAfterCompletion(snapshot);
			Assert.assertTrue(callouts.cuboidsToWrite.isEmpty());
			Assert.assertTrue(callouts.entitiesToWrite.isEmpty());
			callouts.cuboidsToWrite.clear();
			callouts.entitiesToWrite.clear();
			callouts.cuboidsToTryWrite.clear();
			callouts.entitiesToTryWrite.clear();
		}
		manager.setupNextTickAfterCompletion(snapshot);
		Assert.assertEquals(1, callouts.cuboidsToWrite.size());
		Assert.assertTrue(callouts.entitiesToWrite.isEmpty());
		Assert.assertEquals(1, callouts.cuboidsToTryWrite.size());
		Assert.assertEquals(1, callouts.entitiesToTryWrite.size());
		callouts.cuboidsToWrite.clear();
		callouts.entitiesToWrite.clear();
		callouts.cuboidsToTryWrite.clear();
		callouts.entitiesToTryWrite.clear();
		
		// Now, proceed with the next updated snapshot.
		snapshot = _modifySnapshot(snapshot
				, Map.of(
						near, new TickRunner.SnapshotCuboid(nearCuboid, null, List.of(), Map.of())
				)
				, Map.of(1, new TickRunner.SnapshotEntity(entity, 1L, null, List.of()))
				, snapshot.creatures()
				, Map.of(
						near.getColumn(), ColumnHeightMap.build().consume(HeightMapHelpers.buildHeightMap(nearCuboid), near).freeze()
				)
				, Set.of()
		);
		
		// The next tick shouldn't do anything.
		snapshot = _advanceSnapshot(snapshot, 1L);
		manager.setupNextTickAfterCompletion(snapshot);
		Assert.assertTrue(callouts.cuboidsToWrite.isEmpty());
		Assert.assertTrue(callouts.entitiesToWrite.isEmpty());
		Assert.assertTrue(callouts.cuboidsToTryWrite.isEmpty());
		Assert.assertTrue(callouts.entitiesToTryWrite.isEmpty());
		
		// If we cue up to the next flush tick, we should see the existing data written.
		snapshot = _advanceSnapshot(snapshot, ServerStateManager.FORCE_FLUSH_TICK_FREQUENCY - 1L);
		manager.setupNextTickAfterCompletion(snapshot);
		Assert.assertTrue(callouts.cuboidsToWrite.isEmpty());
		Assert.assertTrue(callouts.entitiesToWrite.isEmpty());
		Assert.assertEquals(1, callouts.cuboidsToTryWrite.size());
		Assert.assertEquals(1, callouts.entitiesToTryWrite.size());
		
		manager.shutdown();
	}

	@Test
	public void observeCreatureDeath()
	{
		// Connect 2 clients, each near a creature, verify that each only sees the closer one, then show that one of them dying is only observed by the nearer.
		_Callouts callouts = new _Callouts();
		ServerStateManager manager = new ServerStateManager(callouts, ServerRunner.DEFAULT_MILLIS_PER_TICK);
		manager.setOwningThread();
		int clientId1 = 1;
		String clientName1 = "client1";
		int clientId2 = 2;
		String clientName2 = "client2";
		manager.clientConnected(clientId1, clientName1, 1);
		manager.clientConnected(clientId2, clientName2, 1);
		
		TickRunner.Snapshot snapshot = _createEmptySnapshot();
		ServerStateManager.TickChanges changes = manager.setupNextTickAfterCompletion(snapshot);
		Assert.assertEquals(0, changes.newCuboids().size());
		Assert.assertEquals(0, changes.cuboidsToUnload().size());
		Assert.assertEquals(0, changes.newEntities().size());
		Assert.assertEquals(0, changes.entitiesToUnload().size());
		
		MutableEntity near = MutableEntity.createForTest(clientId1);
		near.setLocation(new EntityLocation(5.0f, 5.0f, 0.0f));
		MutableEntity far = MutableEntity.createForTest(clientId2);
		far.setLocation(new EntityLocation(100.0f, 100.0f, 0.0f));
		callouts.loadedEntities.add(new SuspendedEntity(near.freeze(), List.of()));
		callouts.loadedEntities.add(new SuspendedEntity(far.freeze(), List.of()));
		changes = manager.setupNextTickAfterCompletion(snapshot);
		Assert.assertEquals(0, changes.newCuboids().size());
		Assert.assertEquals(0, changes.cuboidsToUnload().size());
		Assert.assertEquals(2, changes.newEntities().size());
		Assert.assertEquals(0, changes.entitiesToUnload().size());
		Assert.assertEquals(1, callouts.joinedClients.get(clientId1).size());
		Assert.assertEquals(1, callouts.joinedClients.get(clientId2).size());
		
		// Load in the cuboids and creatures.
		CreatureEntity nearCreature = new CreatureEntity(-1, COW, near.newLocation, new EntityLocation(0.0f, 0.0f, 0.0f), (byte)0, (byte)0, (byte)1, (byte)100
				, CreatureEntity.EMPTY_DATA);
		CreatureEntity farCreature = new CreatureEntity(-2, COW, far.newLocation, new EntityLocation(0.0f, 0.0f, 0.0f), (byte)0, (byte)0, (byte)1, (byte)100
				, CreatureEntity.EMPTY_DATA);
		
		CuboidData nearCuboid = CuboidGenerator.createFilledCuboid(near.newLocation.getBlockLocation().getCuboidAddress(), ENV.special.AIR);
		CuboidData farCuboid = CuboidGenerator.createFilledCuboid(far.newLocation.getBlockLocation().getCuboidAddress(), ENV.special.AIR);
		callouts.loadedCuboids.add(new SuspendedCuboid<>(nearCuboid
				, HeightMapHelpers.buildHeightMap(nearCuboid)
				, List.of(nearCreature)
				, List.of()
				, Map.of()
		));
		callouts.loadedCuboids.add(new SuspendedCuboid<>(farCuboid
				, HeightMapHelpers.buildHeightMap(farCuboid)
				, List.of(farCreature)
				, List.of()
				, Map.of()
		));
		snapshot = _modifySnapshot(snapshot
				, Map.of(
				)
				, Map.of(clientId1, new TickRunner.SnapshotEntity(near.freeze(), 1L, null, List.of()), clientId2, new TickRunner.SnapshotEntity(far.freeze(), 1L, null, List.of()))
				, snapshot.creatures()
				, Map.of(
				)
				, Set.of()
		);
		changes = manager.setupNextTickAfterCompletion(snapshot);
		Assert.assertEquals(2, changes.newCuboids().size());
		Assert.assertEquals(0, changes.cuboidsToUnload().size());
		Assert.assertEquals(0, changes.newEntities().size());
		Assert.assertEquals(0, changes.entitiesToUnload().size());
		
		snapshot = _modifySnapshot(snapshot
				, Map.of(
						nearCuboid.getCuboidAddress(), new TickRunner.SnapshotCuboid(nearCuboid, null, List.of(), Map.of()),
						farCuboid.getCuboidAddress(), new TickRunner.SnapshotCuboid(farCuboid, null, List.of(), Map.of())
				)
				, snapshot.entities()
				, Map.of(
						nearCreature.id(), new TickRunner.SnapshotCreature(nearCreature, null),
						farCreature.id(), new TickRunner.SnapshotCreature(farCreature, null)
				)
				, HeightMapHelpers.buildColumnMaps(Map.of(
						nearCuboid.getCuboidAddress(), HeightMapHelpers.buildHeightMap(nearCuboid),
						farCuboid.getCuboidAddress(), HeightMapHelpers.buildHeightMap(farCuboid)
				))
				, Set.of()
		);
		changes = manager.setupNextTickAfterCompletion(_advanceSnapshot(snapshot, 1L));
		Assert.assertEquals(0, changes.newCuboids().size());
		Assert.assertEquals(0, changes.cuboidsToUnload().size());
		Assert.assertEquals(0, changes.newEntities().size());
		Assert.assertEquals(0, changes.entitiesToUnload().size());
		// We expect to see each client having received only their closest creature.
		Assert.assertEquals(1, callouts.partialEntitiesPerClient.get(clientId1).size());
		Assert.assertEquals(1, callouts.partialEntitiesPerClient.get(clientId2).size());
		
		// Now, make one of these entities die and see that it disappears only from the nearest creature.
		snapshot = _modifySnapshot(snapshot
				, snapshot.cuboids()
				, snapshot.entities()
				, Map.of(
						farCreature.id(), new TickRunner.SnapshotCreature(farCreature, null)
				)
				, snapshot.completedHeightMaps()
				, Set.of()
		);
		changes = manager.setupNextTickAfterCompletion(_advanceSnapshot(snapshot, 1L));
		Assert.assertEquals(0, changes.newCuboids().size());
		Assert.assertEquals(0, changes.cuboidsToUnload().size());
		Assert.assertEquals(0, changes.newEntities().size());
		Assert.assertEquals(0, changes.entitiesToUnload().size());
		Assert.assertEquals(0, callouts.partialEntitiesPerClient.get(clientId1).size());
		Assert.assertEquals(1, callouts.partialEntitiesPerClient.get(clientId2).size());
		
		manager.clientDisconnected(clientId1);
		manager.clientDisconnected(clientId2);
		manager.setupNextTickAfterCompletion(snapshot);
	}

	@Test
	public void cuboidLoadRadius()
	{
		// This test demonstrates how cuboid loading is done around a player.
		_Callouts callouts = new _Callouts();
		ServerStateManager manager = new ServerStateManager(callouts, ServerRunner.DEFAULT_MILLIS_PER_TICK);
		manager.setOwningThread();
		int clientId = 1;
		manager.clientConnected(clientId, "client", 1);
		TickRunner.Snapshot snapshot = _createEmptySnapshot();
		
		// We need to run a tick so that the client load request is made.
		manager.setupNextTickAfterCompletion(snapshot);
		Assert.assertEquals(1, callouts.requestedEntityIds.size());
		
		// Allow the client load to be acknowledged.
		EntityLocation entityLocation = new EntityLocation(-101.2f, 678.1f, 55.0f);
		CuboidAddress entityCuboid = entityLocation.getBlockLocation().getCuboidAddress();
		MutableEntity mutable = MutableEntity.createForTest(clientId);
		mutable.newLocation = entityLocation;
		Entity entity = mutable.freeze();
		callouts.loadedEntities.add(new SuspendedEntity(entity, List.of()));
		manager.setupNextTickAfterCompletion(snapshot);
		
		// We shouldn't see the cuboid load request until the next tick.
		Assert.assertEquals(0, callouts.requestedCuboidAddresses.size());
		manager.setupNextTickAfterCompletion(snapshot);
		Assert.assertEquals(27, callouts.requestedCuboidAddresses.size());
		
		// Load these cuboids and show that the loaded cuboids are sent over the network.
		CuboidAddress two = entityCuboid.getRelative(-1, -1, -1);
		CuboidAddress outside = entityCuboid.getRelative(2, 0, 0);
		Assert.assertTrue(callouts.requestedCuboidAddresses.contains(entityCuboid));
		Assert.assertTrue(callouts.requestedCuboidAddresses.contains(two));
		Assert.assertFalse(callouts.requestedCuboidAddresses.contains(outside));
		CuboidData oneCuboid = CuboidGenerator.createFilledCuboid(entityCuboid, ENV.special.AIR);
		CuboidData twoCuboid = CuboidGenerator.createFilledCuboid(two, ENV.special.AIR);
		snapshot = _modifySnapshot(snapshot
				, Map.of(
						oneCuboid.getCuboidAddress(), new TickRunner.SnapshotCuboid(oneCuboid, null, List.of(), Map.of()),
						twoCuboid.getCuboidAddress(), new TickRunner.SnapshotCuboid(twoCuboid, null, List.of(), Map.of())
				)
				, Map.of(clientId, new TickRunner.SnapshotEntity(entity, 1L, null, List.of()))
				, snapshot.creatures()
				, Map.of(
						oneCuboid.getCuboidAddress().getColumn(), ColumnHeightMap.build().consume(HeightMapHelpers.buildHeightMap(oneCuboid), oneCuboid.getCuboidAddress()).freeze(),
						twoCuboid.getCuboidAddress().getColumn(), ColumnHeightMap.build().consume(HeightMapHelpers.buildHeightMap(twoCuboid), twoCuboid.getCuboidAddress()).freeze()
				)
				, Set.of()
		);
		manager.test_setAlreadyAlive(snapshot.cuboids().keySet());
		manager.setupNextTickAfterCompletion(snapshot);
		Assert.assertEquals(2, callouts.cuboidsSentToClient.get(clientId).size());
		
		// Move over slightly and show that this requests an unload over the network.
		mutable.newLocation = new EntityLocation(-71.2f, 678.1f, 55.0f);
		entity = mutable.freeze();
		snapshot = _advanceSnapshot(_modifySnapshot(snapshot
				, snapshot.cuboids()
				, Map.of(clientId, new TickRunner.SnapshotEntity(entity, 1L, null, List.of()))
				, snapshot.creatures()
				, snapshot.completedHeightMaps()
				, Set.of()
		), 1L);
		manager.setupNextTickAfterCompletion(snapshot);
		Assert.assertEquals(1, callouts.cuboidsSentToClient.get(clientId).size());
		
		manager.shutdown();
	}

	@Test
	public void cuboidRadiusChange()
	{
		// This shows how changing the view distance around a player will cause its known cuboids to be reconsidered.
		_Callouts callouts = new _Callouts();
		ServerStateManager manager = new ServerStateManager(callouts, ServerRunner.DEFAULT_MILLIS_PER_TICK);
		manager.setOwningThread();
		int clientId = 1;
		manager.clientConnected(clientId, "client", 1);
		TickRunner.Snapshot snapshot = _createEmptySnapshot();
		
		// We need to run a tick so that the client load request is made.
		manager.setupNextTickAfterCompletion(snapshot);
		Assert.assertEquals(1, callouts.requestedEntityIds.size());
		
		// If we change the view distance here, it will be ignored since they aren't yet loaded.
		manager.setClientViewDistance(clientId, 5);
		
		// Allow the client load to be acknowledged.
		EntityLocation entityLocation = new EntityLocation(-101.2f, 678.1f, 55.0f);
		CuboidAddress entityCuboid = entityLocation.getBlockLocation().getCuboidAddress();
		MutableEntity mutable = MutableEntity.createForTest(clientId);
		mutable.newLocation = entityLocation;
		Entity entity = mutable.freeze();
		callouts.loadedEntities.add(new SuspendedEntity(entity, List.of()));
		manager.setupNextTickAfterCompletion(snapshot);
		
		// We shouldn't see the cuboid load request until the next tick.
		Assert.assertEquals(0, callouts.requestedCuboidAddresses.size());
		manager.setupNextTickAfterCompletion(snapshot);
		// (see that this is still the default 3x3x3)
		Assert.assertEquals(27, callouts.requestedCuboidAddresses.size());
		
		// Load a bunch of cuboids at different distances so we can see how the loaded set changes with view distance.
		CuboidAddress plus1 = entityCuboid.getRelative(-1, -1, -1);
		CuboidAddress plus2 = entityCuboid.getRelative(-2, -2, -2);
		CuboidAddress plus3 = entityCuboid.getRelative(-3, -3, -3);
		Assert.assertTrue(callouts.requestedCuboidAddresses.contains(entityCuboid));
		Assert.assertTrue(callouts.requestedCuboidAddresses.contains(plus1));
		Assert.assertFalse(callouts.requestedCuboidAddresses.contains(plus2));
		Assert.assertFalse(callouts.requestedCuboidAddresses.contains(plus3));
		CuboidData cuboid0 = CuboidGenerator.createFilledCuboid(entityCuboid, ENV.special.AIR);
		CuboidData cuboid1 = CuboidGenerator.createFilledCuboid(plus1, ENV.special.AIR);
		CuboidData cuboid2 = CuboidGenerator.createFilledCuboid(plus2, ENV.special.AIR);
		CuboidData cuboid3 = CuboidGenerator.createFilledCuboid(plus3, ENV.special.AIR);
		snapshot = _modifySnapshot(snapshot
				, Map.of(
						cuboid0.getCuboidAddress(), new TickRunner.SnapshotCuboid(cuboid0, null, List.of(), Map.of()),
						cuboid1.getCuboidAddress(), new TickRunner.SnapshotCuboid(cuboid1, null, List.of(), Map.of()),
						cuboid2.getCuboidAddress(), new TickRunner.SnapshotCuboid(cuboid2, null, List.of(), Map.of()),
						cuboid3.getCuboidAddress(), new TickRunner.SnapshotCuboid(cuboid3, null, List.of(), Map.of())
				)
				, Map.of(clientId, new TickRunner.SnapshotEntity(entity, 1L, null, List.of()))
				, snapshot.creatures()
				, Map.of(
						cuboid0.getCuboidAddress().getColumn(), ColumnHeightMap.build().consume(HeightMapHelpers.buildHeightMap(cuboid0), cuboid0.getCuboidAddress()).freeze(),
						cuboid1.getCuboidAddress().getColumn(), ColumnHeightMap.build().consume(HeightMapHelpers.buildHeightMap(cuboid1), cuboid1.getCuboidAddress()).freeze(),
						cuboid2.getCuboidAddress().getColumn(), ColumnHeightMap.build().consume(HeightMapHelpers.buildHeightMap(cuboid2), cuboid2.getCuboidAddress()).freeze(),
						cuboid3.getCuboidAddress().getColumn(), ColumnHeightMap.build().consume(HeightMapHelpers.buildHeightMap(cuboid3), cuboid3.getCuboidAddress()).freeze()
				)
				, Set.of()
		);
		// Saturate the network to block the next cuboid.
		callouts.isNetworkWriteReady = false;
		manager.test_setAlreadyAlive(snapshot.cuboids().keySet());
		manager.setupNextTickAfterCompletion(snapshot);
		Assert.assertNull(callouts.cuboidsSentToClient.get(clientId));
		Assert.assertEquals(27, callouts.requestedCuboidAddresses.size());
		callouts.cuboidsToWrite.clear();
		callouts.entitiesToWrite.clear();
		callouts.cuboidsToTryWrite.clear();
		callouts.entitiesToTryWrite.clear();
		// Resume the network to continue.
		callouts.isNetworkWriteReady = true;
		manager.setupNextTickAfterCompletion(snapshot);
		Assert.assertEquals(2, callouts.cuboidsSentToClient.get(clientId).size());
		
		// Change the distance and see the cuboid collection grow.
		// (note that we ignore the cuboids to write-back since we just want to see how the network works here)
		callouts.cuboidsToWrite.clear();
		callouts.requestedCuboidAddresses.clear();
		snapshot = _advanceSnapshot(snapshot, 1L);
		manager.setClientViewDistance(clientId, 2);
		manager.setupNextTickAfterCompletion(snapshot);
		Assert.assertEquals(3, callouts.cuboidsSentToClient.get(clientId).size());
		// (see that this is now 5x5x5 minus what was previously requested or pre-loaded)
		Assert.assertEquals(125 - 27 - 1, callouts.requestedCuboidAddresses.size());
		
		callouts.cuboidsToWrite.clear();
		snapshot = _advanceSnapshot(snapshot, 1L);
		manager.setClientViewDistance(clientId, 0);
		manager.setupNextTickAfterCompletion(snapshot);
		Assert.assertEquals(1, callouts.cuboidsSentToClient.get(clientId).size());
		
		callouts.cuboidsToWrite.clear();
		manager.shutdown();
	}

	@Test
	public void cuboidRadiusStart()
	{
		// Show that we can connect with a higher than default view distance.
		_Callouts callouts = new _Callouts();
		ServerStateManager manager = new ServerStateManager(callouts, ServerRunner.DEFAULT_MILLIS_PER_TICK);
		manager.setOwningThread();
		int clientId = 1;
		int requestedRaduis = 2;
		manager.clientConnected(clientId, "client", requestedRaduis);
		TickRunner.Snapshot snapshot = _createEmptySnapshot();
		
		// We need to run a tick so that the client load request is made.
		manager.setupNextTickAfterCompletion(snapshot);
		Assert.assertEquals(1, callouts.requestedEntityIds.size());
		
		// Allow the client load to be acknowledged.
		EntityLocation entityLocation = new EntityLocation(-101.2f, 678.1f, 55.0f);
		MutableEntity mutable = MutableEntity.createForTest(clientId);
		mutable.newLocation = entityLocation;
		Entity entity = mutable.freeze();
		callouts.loadedEntities.add(new SuspendedEntity(entity, List.of()));
		manager.setupNextTickAfterCompletion(snapshot);
		
		// We shouldn't see the cuboid load request until the next tick.
		Assert.assertEquals(0, callouts.requestedCuboidAddresses.size());
		manager.setupNextTickAfterCompletion(snapshot);
		// (we should now see that the 5x5x5 is requested since we asked for a radius of 2)
		Assert.assertEquals(125, callouts.requestedCuboidAddresses.size());
		manager.shutdown();
	}

	@Test
	public void cuboidInternalKeepAlive()
	{
		// Show that an internal keep-alive in the snapshot causes a load request.
		_Callouts callouts = new _Callouts();
		ServerStateManager manager = new ServerStateManager(callouts, ServerRunner.DEFAULT_MILLIS_PER_TICK);
		manager.setOwningThread();
		CuboidAddress internalAddress = CuboidAddress.fromInt(-5, 7, 0);
		TickRunner.Snapshot snapshot = _modifySnapshot(_createEmptySnapshot()
			, Map.of()
			, Map.of()
			, Map.of()
			, Map.of()
			, Set.of(internalAddress)
		);
		
		// Show that the cuboid is requested.
		manager.setupNextTickAfterCompletion(snapshot);
		Assert.assertEquals(1, callouts.requestedCuboidAddresses.size());
		Assert.assertEquals(internalAddress, callouts.requestedCuboidAddresses.iterator().next());
		callouts.requestedCuboidAddresses.clear();
		
		// Load this cuboid and wait for it to timeout and be unloaded.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(internalAddress, ENV.special.AIR);
		CuboidHeightMap heightMap = HeightMapHelpers.buildHeightMap(cuboid);
		callouts.loadedCuboids.add(new SuspendedCuboid<CuboidData>(cuboid
			, heightMap
			, List.of()
			, List.of()
			, Map.of()
		));
		snapshot = _modifySnapshot(_advanceSnapshot(snapshot, 1)
			, Map.of()
			, Map.of()
			, Map.of()
			, Map.of()
			, Set.of()
		);
		manager.setupNextTickAfterCompletion(snapshot);
		
		// Now, just show them loaded.
		snapshot = _modifySnapshot(snapshot
			, Map.of(
				internalAddress, new TickRunner.SnapshotCuboid(cuboid, null, List.of(), Map.of())
			)
			, Map.of()
			, snapshot.creatures()
			, Map.of(
				internalAddress.getColumn(), ColumnHeightMap.build().consume(heightMap, internalAddress).freeze()
			)
			, Set.of()
		);
		manager.setupNextTickAfterCompletion(snapshot);
		Assert.assertEquals(0, callouts.requestedCuboidAddresses.size());
		Assert.assertEquals(0, callouts.cuboidsToWrite.size());
		Assert.assertEquals(0, callouts.cuboidsToTryWrite.size());
		
		// Nothing should happen while waiting for timeout.
		for (int i = 0; i < (manager.test_getCuboidKeepAliveTicks() - 2); ++i)
		{
			manager.setupNextTickAfterCompletion(snapshot);
			Assert.assertEquals(0, callouts.cuboidsToWrite.size());
			Assert.assertEquals(0, callouts.cuboidsToTryWrite.size());
		}
		
		// Now, we should see the unload since it has timed out.
		manager.setupNextTickAfterCompletion(snapshot);
		Assert.assertEquals(1, callouts.cuboidsToWrite.size());
		Assert.assertEquals(0, callouts.cuboidsToTryWrite.size());
		callouts.cuboidsToWrite.clear();
		
		snapshot = _modifySnapshot(_advanceSnapshot(snapshot, 1)
			, Map.of()
			, Map.of()
			, Map.of()
			, Map.of()
			, Set.of()
		);
		manager.setupNextTickAfterCompletion(snapshot);
		Assert.assertEquals(0, callouts.cuboidsToWrite.size());
		Assert.assertEquals(0, callouts.cuboidsToTryWrite.size());
		
		manager.shutdown();
	}


	private TickRunner.Snapshot _createEmptySnapshot()
	{
		return new TickRunner.Snapshot(0L
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyMap()
				
				, Collections.emptyMap()
				
				, List.of()
				// internallyMarkedAlive
				, Set.of()
				
				// Information related to tick behaviour and performance statistics.
				, new TickRunner.TickStats(0L
						, 0L
						, 0L
						, 0L
						, null
						, 0
						, 0
				)
		);
	}

	private TickRunner.Snapshot _modifySnapshot(TickRunner.Snapshot snapshot
			, Map<CuboidAddress, TickRunner.SnapshotCuboid> cuboids
			, Map<Integer, TickRunner.SnapshotEntity> entities
			, Map<Integer, TickRunner.SnapshotCreature> completedCreatures
			, Map<CuboidColumnAddress, ColumnHeightMap> completedHeightMaps
			, Set<CuboidAddress> internallyMarkedAlive
	)
	{
		TickRunner.TickStats stats = snapshot.stats();
		return new TickRunner.Snapshot(snapshot.tickNumber()
				, cuboids
				, entities
				, completedCreatures
				, completedHeightMaps
				
				, snapshot.postedEvents()
				, internallyMarkedAlive
				
				// Information related to tick behaviour and performance statistics.
				, stats
		);
	}

	private TickRunner.Snapshot _advanceSnapshot(TickRunner.Snapshot snapshot, long count)
	{
		TickRunner.TickStats stats = snapshot.stats();
		return new TickRunner.Snapshot(snapshot.tickNumber() + count
				, snapshot.cuboids()
				, snapshot.entities()
				, snapshot.creatures()
				, snapshot.completedHeightMaps()
				
				, snapshot.postedEvents()
				, snapshot.internallyMarkedAlive()
				
				// Information related to tick behaviour and performance statistics.
				, stats
		);
	}

	private Map<CuboidAddress, TickRunner.SnapshotCuboid> _convertToCuboidMap(Collection<SuspendedCuboid<IReadOnlyCuboidData>> cuboids)
	{
		Map<CuboidAddress, TickRunner.SnapshotCuboid> completedCuboids = new HashMap<>();
		for (SuspendedCuboid<IReadOnlyCuboidData> suspended : cuboids)
		{
			IReadOnlyCuboidData cuboid = suspended.cuboid();
			Assert.assertTrue(suspended.pendingMutations().isEmpty());
			Assert.assertTrue(suspended.periodicMutationMillis().isEmpty());
			TickRunner.SnapshotCuboid wrapper = new TickRunner.SnapshotCuboid(cuboid, null, List.of(), Map.of());
			completedCuboids.put(cuboid.getCuboidAddress(), wrapper);
		}
		return completedCuboids;
	}

	private Map<CuboidColumnAddress, ColumnHeightMap> _convertToCuboidHeightMap(Collection<SuspendedCuboid<IReadOnlyCuboidData>> cuboids)
	{
		Map<CuboidColumnAddress, ColumnHeightMap> completedMaps = new HashMap<>();
		for (SuspendedCuboid<IReadOnlyCuboidData> suspended : cuboids)
		{
			IReadOnlyCuboidData cuboid = suspended.cuboid();
			Assert.assertTrue(suspended.pendingMutations().isEmpty());
			Assert.assertTrue(suspended.periodicMutationMillis().isEmpty());
			Object old = completedMaps.put(cuboid.getCuboidAddress().getColumn(), ColumnHeightMap.build().consume(HeightMapHelpers.buildHeightMap(cuboid), cuboid.getCuboidAddress()).freeze());
			Assert.assertNull(old);
		}
		return completedMaps;
	}

	private Map<Integer, TickRunner.SnapshotEntity> _convertToEntityMap(Collection<SuspendedEntity> entities)
	{
		Map<Integer, TickRunner.SnapshotEntity> completedEntities = new HashMap<>();
		for (SuspendedEntity suspended : entities)
		{
			Entity entity = suspended.entity();
			Assert.assertTrue(suspended.changes().isEmpty());
			TickRunner.SnapshotEntity wrapper = new TickRunner.SnapshotEntity(entity, 0L, null, List.of());
			completedEntities.put(entity.id(), wrapper);
		}
		return completedEntities;
	}


	private static class _Callouts implements ServerStateManager.ICallouts
	{
		public Set<Integer> requestedEntityIds = new HashSet<>();
		public Set<CuboidAddress> requestedCuboidAddresses = new HashSet<>();
		public List<SuspendedEntity> loadedEntities = new ArrayList<>();
		public List<SuspendedCuboid<CuboidData>> loadedCuboids = new ArrayList<>();
		public Map<Integer, Long> lastFinishedCommitPerClient = new HashMap<>();
		public Set<PackagedCuboid> cuboidsToWrite = new HashSet<>();
		public Set<SuspendedEntity> entitiesToWrite = new HashSet<>();
		public Set<PackagedCuboid> cuboidsToTryWrite = new HashSet<>();
		public Set<SuspendedEntity> entitiesToTryWrite = new HashSet<>();
		public Set<Integer> fullEntitiesSent = new HashSet<>();
		public Function<PacketFromClient, PacketFromClient> peekHandler = null;
		public boolean didEnqueue = false;
		public Map<Integer, Map<Integer, String>> joinedClients = new HashMap<>();
		public Map<Integer, Set<Integer>> partialEntitiesPerClient = new HashMap<>();
		public Map<Integer, Set<CuboidAddress>> cuboidsSentToClient = new HashMap<>();
		public boolean isNetworkWriteReady = true;
		
		@Override
		public void resources_writeToDisk(Collection<PackagedCuboid> cuboids, Collection<SuspendedEntity> entities)
		{
			Assert.assertFalse(this.cuboidsToWrite.removeAll(cuboids));
			this.cuboidsToWrite.addAll(cuboids);
			Assert.assertFalse(this.entitiesToWrite.removeAll(entities));
			this.entitiesToWrite.addAll(entities);
		}
		@Override
		public void resources_tryWriteToDisk(Collection<PackagedCuboid> cuboids, Collection<SuspendedEntity> entities)
		{
			Assert.assertFalse(this.cuboidsToTryWrite.removeAll(cuboids));
			this.cuboidsToTryWrite.addAll(cuboids);
			Assert.assertFalse(this.entitiesToTryWrite.removeAll(entities));
			this.entitiesToTryWrite.addAll(entities);
		}
		@Override
		public void resources_getAndRequestBackgroundLoad(Collection<SuspendedCuboid<CuboidData>> out_loadedCuboids
				, Collection<SuspendedEntity> out_loadedEntities
				, Collection<CuboidAddress> requestedCuboids
				, Collection<Integer> requestedEntityIds
		)
		{
			// There should be no duplicates.
			Assert.assertFalse(this.requestedCuboidAddresses.removeAll(requestedCuboids));
			this.requestedCuboidAddresses.addAll(requestedCuboids);
			Assert.assertFalse(this.requestedEntityIds.removeAll(requestedEntityIds));
			this.requestedEntityIds.addAll(requestedEntityIds);
			
			out_loadedCuboids.addAll(this.loadedCuboids);
			this.loadedCuboids.clear();
			out_loadedEntities.addAll(this.loadedEntities);
			this.loadedEntities.clear();
		}
		@Override
		public PacketFromClient network_peekOrRemoveNextPacketFromClient(int clientId, PacketFromClient toRemove)
		{
			return peekHandler.apply(toRemove);
		}
		@Override
		public boolean network_isNetworkWriteReady(int clientId)
		{
			return this.isNetworkWriteReady;
		}
		@Override
		public void network_sendFullEntity(int clientId, Entity entity)
		{
			Assert.assertTrue(this.fullEntitiesSent.add(clientId));
		}
		@Override
		public void network_sendPartialEntity(int clientId, PartialEntity entity)
		{
			Set<Integer> ids = this.partialEntitiesPerClient.get(clientId);
			if (null == ids)
			{
				ids = new HashSet<>();
				this.partialEntitiesPerClient.put(clientId, ids);
			}
			boolean didAdd = ids.add(entity.id());
			Assert.assertTrue(didAdd);
		}
		@Override
		public void network_removeEntity(int clientId, int entityId)
		{
			if (entityId > 0)
			{
				throw new AssertionError("networkRemoveEntity");
			}
			else
			{
				Set<Integer> ids = this.partialEntitiesPerClient.get(clientId);
				boolean didRemove = ids.remove(entityId);
				Assert.assertTrue(didRemove);
			}
		}
		@Override
		public void network_sendCuboid(int clientId, IReadOnlyCuboidData cuboid)
		{
			if (!this.cuboidsSentToClient.containsKey(clientId))
			{
				this.cuboidsSentToClient.put(clientId, new HashSet<>());
			}
			boolean didAdd = this.cuboidsSentToClient.get(clientId).add(cuboid.getCuboidAddress());
			Assert.assertTrue(didAdd);
		}
		@Override
		public void network_removeCuboid(int clientId, CuboidAddress address)
		{
			boolean didRemove = this.cuboidsSentToClient.get(clientId).remove(address);
			Assert.assertTrue(didRemove);
		}
		@Override
		public void network_sendEntityUpdate(int clientId, int entityId, IEntityUpdate update)
		{
			throw new AssertionError("networkSendEntityUpdate");
		}
		@Override
		public void network_sendPartialEntityUpdate(int clientId, int entityId, IPartialEntityUpdate update)
		{
			throw new AssertionError("network_sendPartialEntityUpdate");
		}
		@Override
		public void network_sendBlockUpdate(int clientId, MutationBlockSetBlock update)
		{
			throw new AssertionError("networkSendBlockUpdate");
		}
		@Override
		public void network_sendBlockEvent(int clientId, EventRecord.Type type, AbsoluteLocation location, int entitySource)
		{
			throw new AssertionError("network_sendBlockEvent");
		}
		@Override
		public void network_sendEntityEvent(int clientId, EventRecord.Type type, EventRecord.Cause cause, AbsoluteLocation optionalLocation, int entityTarget, int entitySource)
		{
			throw new AssertionError("network_sendEntityEvent");
		}
		@Override
		public void network_sendEndOfTick(int clientId, long tickNumber, long latestLocalCommitIncluded)
		{
			// For now, just track the commit number.
			this.lastFinishedCommitPerClient.put(clientId, latestLocalCommitIncluded);
		}
		@Override
		public void network_sendConfig(int clientId, WorldConfig config)
		{
			throw new AssertionError("network_sendConfig");
		}
		@Override
		public void network_sendClientJoined(int clientId, int joinedClientId, String name)
		{
			Map<Integer, String> clients = this.joinedClients.get(clientId);
			if (null == clients)
			{
				clients = new HashMap<>();
				this.joinedClients.put(clientId, clients);
			}
			Object old = clients.put(joinedClientId, name);
			Assert.assertNull(old);
		}
		@Override
		public void network_sendClientLeft(int clientId, int leftClientId)
		{
			Map<Integer, String> clients = this.joinedClients.get(clientId);
			Object old = clients.remove(leftClientId);
			Assert.assertNotNull(old);
		}
		@Override
		public void network_sendChatMessage(int clientId, int senderId, String message)
		{
			throw new AssertionError("network_sendChatMessage");
		}
		@Override
		public boolean runner_enqueueEntityChange(int entityId, EntityChangeTopLevelMovement<IMutablePlayerEntity> change, long commitLevel)
		{
			return this.didEnqueue;
		}
		@Override
		public void handleClientUpdateOptions(int clientId, int clientViewDistance)
		{
			throw new AssertionError("handleClientUpdateOptions");
		}
	}
}
