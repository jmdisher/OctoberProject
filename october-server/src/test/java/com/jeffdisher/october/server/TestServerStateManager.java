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

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.HeightMapHelpers;
import com.jeffdisher.october.mutations.IEntityUpdate;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.mutations.IPartialEntityUpdate;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
import com.jeffdisher.october.net.Packet_MutationEntityFromClient;
import com.jeffdisher.october.persistence.PackagedCuboid;
import com.jeffdisher.october.persistence.SuspendedCuboid;
import com.jeffdisher.october.persistence.SuspendedEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.CuboidColumnAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.types.WorldConfig;
import com.jeffdisher.october.worldgen.CuboidGenerator;


public class TestServerStateManager
{
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
	public void shutdown()
	{
		_Callouts callouts = new _Callouts();
		ServerStateManager manager = new ServerStateManager(callouts);
		manager.shutdown();
	}

	@Test
	public void runEmptyTick()
	{
		_Callouts callouts = new _Callouts();
		ServerStateManager manager = new ServerStateManager(callouts);
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
		ServerStateManager manager = new ServerStateManager(callouts);
		int clientId = 1;
		String clientName = "client";
		manager.clientConnected(clientId, clientName);
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
				, _convertToEntityMap(changes.newEntities())
				, _convertToCuboidMap(changes.newCuboids())
				, _convertToCuboidHeightMap(changes.newCuboids())
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
				, Collections.emptyMap()
				, snapshot.completedCuboids()
				, snapshot.completedHeightMaps()
		);
		
		// Load one of the requested cuboids and verify it appears as loaded.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(callouts.requestedCuboidAddresses.iterator().next(), ENV.special.AIR);
		callouts.loadedCuboids.add(new SuspendedCuboid<CuboidData>(cuboid
				, HeightMapHelpers.buildHeightMap(cuboid)
				, List.of()
				, List.of()
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
				, snapshot.completedEntities()
				, _convertToCuboidMap(changes.newCuboids())
				, _convertToCuboidHeightMap(changes.newCuboids())
		);
		
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
		ServerStateManager manager = new ServerStateManager(callouts);
		int clientId = 1;
		String clientName = "client";
		manager.clientConnected(clientId, clientName);
		boolean[] connectedRef = new boolean[] {true};
		TickRunner.Snapshot snapshot = _createEmptySnapshot();
		manager.setupNextTickAfterCompletion(snapshot);
		
		// We need to setup the callouts to not fully satisfy this.
		Packet_MutationEntityFromClient packet = new Packet_MutationEntityFromClient(null, 1L);
		callouts.peekHandler = (Packet_MutationEntityFromClient toRemove) -> {
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
		ServerStateManager manager = new ServerStateManager(callouts);
		int clientId1 = 1;
		String clientName1 = "client1";
		int clientId2 = 2;
		String clientName2 = "client2";
		manager.clientConnected(clientId1, clientName1);
		manager.clientConnected(clientId2, clientName2);
		
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


	private TickRunner.Snapshot _createEmptySnapshot()
	{
		return new TickRunner.Snapshot(0L
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyMap()
				
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyMap()
				
				, Collections.emptyMap()
				, Collections.emptyMap()
				
				// Information related to tick behaviour and performance statistics.
				, 0L
				, 0L
				, 0L
				, null
				, 0
				, 0
		);
	}

	private TickRunner.Snapshot _modifySnapshot(TickRunner.Snapshot snapshot
			, Map<Integer, Entity> completedEntities
			, Map<CuboidAddress, IReadOnlyCuboidData> completedCuboids
			, Map<CuboidColumnAddress, ColumnHeightMap> completedHeightMaps
	)
	{
		return new TickRunner.Snapshot(snapshot.tickNumber()
				, completedEntities
				, snapshot.commitLevels()
				, completedCuboids
				, completedHeightMaps
				, snapshot.completedCreatures()
				
				, snapshot.updatedEntities()
				, snapshot.resultantBlockChangesByCuboid()
				, snapshot.visiblyChangedCreatures()
				
				, snapshot.scheduledBlockMutations()
				, snapshot.scheduledEntityMutations()
				
				// Information related to tick behaviour and performance statistics.
				, snapshot.millisTickPreamble()
				, snapshot.millisTickParallelPhase()
				, snapshot.millisTickPostamble()
				, snapshot.threadStats()
				, snapshot.committedEntityMutationCount()
				, snapshot.committedCuboidMutationCount()
		);
	}

	private Map<CuboidAddress, IReadOnlyCuboidData> _convertToCuboidMap(Collection<SuspendedCuboid<IReadOnlyCuboidData>> cuboids)
	{
		Map<CuboidAddress, IReadOnlyCuboidData> completedCuboids = new HashMap<>();
		for (SuspendedCuboid<IReadOnlyCuboidData> suspended : cuboids)
		{
			IReadOnlyCuboidData cuboid = suspended.cuboid();
			Assert.assertTrue(suspended.mutations().isEmpty());
			completedCuboids.put(cuboid.getCuboidAddress(), cuboid);
		}
		return completedCuboids;
	}

	private Map<CuboidColumnAddress, ColumnHeightMap> _convertToCuboidHeightMap(Collection<SuspendedCuboid<IReadOnlyCuboidData>> cuboids)
	{
		Map<CuboidColumnAddress, ColumnHeightMap> completedMaps = new HashMap<>();
		for (SuspendedCuboid<IReadOnlyCuboidData> suspended : cuboids)
		{
			IReadOnlyCuboidData cuboid = suspended.cuboid();
			Assert.assertTrue(suspended.mutations().isEmpty());
			Object old = completedMaps.put(cuboid.getCuboidAddress().getColumn(), ColumnHeightMap.build().consume(HeightMapHelpers.buildHeightMap(cuboid), cuboid.getCuboidAddress().z()).freeze());
			Assert.assertNull(old);
		}
		return completedMaps;
	}

	private Map<Integer, Entity> _convertToEntityMap(Collection<SuspendedEntity> entities)
	{
		Map<Integer, Entity> completedEntities = new HashMap<>();
		for (SuspendedEntity suspended : entities)
		{
			Entity entity = suspended.entity();
			Assert.assertTrue(suspended.changes().isEmpty());
			completedEntities.put(entity.id(), entity);
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
		public Set<Integer> fullEntitiesSent = new HashSet<>();
		public Function<Packet_MutationEntityFromClient, Packet_MutationEntityFromClient> peekHandler = null;
		public boolean didEnqueue = false;
		public Map<Integer, Map<Integer, String>> joinedClients = new HashMap<>();
		
		@Override
		public void resources_writeToDisk(Collection<PackagedCuboid> cuboids, Collection<SuspendedEntity> entities)
		{
			Assert.assertFalse(this.cuboidsToWrite.removeAll(cuboids));
			this.cuboidsToWrite.addAll(cuboids);
			Assert.assertFalse(this.entitiesToWrite.removeAll(entities));
			this.entitiesToWrite.addAll(entities);
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
		public Packet_MutationEntityFromClient network_peekOrRemoveNextMutationFromClient(int clientId, Packet_MutationEntityFromClient toRemove)
		{
			return peekHandler.apply(toRemove);
		}
		@Override
		public void network_sendFullEntity(int clientId, Entity entity)
		{
			Assert.assertTrue(this.fullEntitiesSent.add(clientId));
		}
		@Override
		public void network_sendPartialEntity(int clientId, PartialEntity entity)
		{
			throw new AssertionError("networkSendPartialEntity");
		}
		@Override
		public void network_removeEntity(int clientId, int entityId)
		{
			throw new AssertionError("networkRemoveEntity");
		}
		@Override
		public void network_sendCuboid(int clientId, IReadOnlyCuboidData cuboid)
		{
			throw new AssertionError("networkSendCuboid");
		}
		@Override
		public void network_removeCuboid(int clientId, CuboidAddress address)
		{
			throw new AssertionError("networkRemoveCuboid");
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
		public boolean runner_enqueueEntityChange(int entityId, IMutationEntity<IMutablePlayerEntity> change, long commitLevel)
		{
			return this.didEnqueue;
		}
	}
}
