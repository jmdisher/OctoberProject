package com.jeffdisher.october.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;

import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.ScheduledChange;
import com.jeffdisher.october.logic.ScheduledMutation;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.mutations.IEntityUpdate;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.mutations.IPartialEntityUpdate;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
import com.jeffdisher.october.mutations.MutationEntitySetEntity;
import com.jeffdisher.october.mutations.MutationEntitySetPartialEntity;
import com.jeffdisher.october.net.Packet;
import com.jeffdisher.october.net.PacketFromClient;
import com.jeffdisher.october.net.Packet_MutationEntityFromClient;
import com.jeffdisher.october.net.Packet_SendChatMessage;
import com.jeffdisher.october.persistence.PackagedCuboid;
import com.jeffdisher.october.persistence.SuspendedCuboid;
import com.jeffdisher.october.persistence.SuspendedEntity;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.types.WorldConfig;
import com.jeffdisher.october.utils.Assert;


/**
 * Relevant state-changing calls come in here so that the state machine can make decisions.
 * Note that all calls are expected to come in on the same thread, so there is never any concern around locking or race
 * conditions.
 */
public class ServerStateManager
{
	/**
	 * Entities further than this distance away (horizontal sums, for now) are not visible to the clients.
	 */
	public static final float ENTITY_VISIBLE_DISTANCE = 50.0f;
	/**
	 * How often, in terms of ticks, that the remaining (not being unloaded) resources are written to disk.
	 */
	public static final int FORCE_FLUSH_TICK_FREQUENCY = 1000;

	private final ICallouts _callouts;
	private final Map<Integer, ClientState> _connectedClients;
	private final Map<Integer, String> _newClients;
	private final Queue<Integer> _removedClients;
	private final Set<Integer> _clientsToRead;
	private final Map<Integer, String> _clientsPendingLoad;
	private Thread _ownerThread;

	// It could take several ticks for a cuboid to be loaded/generated and we don't want to redundantly load them so track what is pending.
	private Set<CuboidAddress> _requestedCuboids = new HashSet<>();
	// We capture the collection of loaded cuboids at each tick so that we can write them back to disk when we shut down.
	private Collection<IReadOnlyCuboidData> _completedCuboids = Collections.emptySet();
	private Map<CuboidAddress, List<ScheduledMutation>> _scheduledBlockMutations = Collections.emptyMap();
	private Map<Integer, List<ScheduledChange>> _scheduledEntityMutations = Collections.emptyMap();
	// Same thing with entities.
	private Collection<Entity> _completedEntities = Collections.emptySet();
	private Collection<CreatureEntity> _completedCreatures = Collections.emptySet();

	public ServerStateManager(ICallouts callouts)
	{
		_callouts = callouts;
		_connectedClients = new HashMap<>();
		_newClients = new HashMap<>();
		_removedClients = new LinkedList<>();
		_clientsToRead = new HashSet<>();
		_clientsPendingLoad = new HashMap<>();
		
		_requestedCuboids = new HashSet<>();
		_completedCuboids = Collections.emptySet();
		_scheduledBlockMutations = Collections.emptyMap();
		_scheduledEntityMutations = Collections.emptyMap();
		_completedEntities = Collections.emptySet();
		_completedCreatures = Collections.emptySet();
	}

	public void setOwningThread()
	{
		_ownerThread = Thread.currentThread();
	}

	public void clientConnected(int clientId, String name)
	{
		Assert.assertTrue(Thread.currentThread() == _ownerThread);
		// We don't want to allow non-positive entity IDs (since those will be reserved for errors or future uses).
		Assert.assertTrue(clientId > 0);
		
		// Add this to the list of new clients (we will send them the snapshot and inject them after the the current tick is done tick).
		_newClients.put(clientId, name);
		System.out.println("Client connected: " + clientId);
	}

	public void clientDisconnected(int clientId)
	{
		Assert.assertTrue(Thread.currentThread() == _ownerThread);
		// Remove them from the list of connected clients (we can do this immediately).
		_connectedClients.remove(clientId);
		// We also want to add them to the list of clients which must be unloaded in the logic engine.
		_removedClients.add(clientId);
		// Make sure that we won't try to read this.
		_clientsToRead.remove(clientId);
		System.out.println("Client disconnected: " + clientId);
		
		// Notify the other clients that they left.
		for (Integer existingClient : _connectedClients.keySet())
		{
			_callouts.network_sendClientLeft(existingClient, clientId);
		}
	}

	public void clientReadReady(int clientId)
	{
		Assert.assertTrue(Thread.currentThread() == _ownerThread);
		// We shouldn't already think that this is readable.
		Assert.assertTrue(!_clientsToRead.contains(clientId));
		boolean isReadable = _drainPacketsIntoRunner(clientId);
		if (isReadable)
		{
			_clientsToRead.add(clientId);
		}
	}

	public TickChanges setupNextTickAfterCompletion(TickRunner.Snapshot snapshot)
	{
		Assert.assertTrue(Thread.currentThread() == _ownerThread);
		_completedCuboids = snapshot.completedCuboids().values();
		_completedEntities = snapshot.completedEntities().values();
		_completedCreatures = snapshot.completedCreatures().values();
		_scheduledBlockMutations = snapshot.scheduledBlockMutations();
		_scheduledEntityMutations = snapshot.scheduledEntityMutations();
		
		// Remove any of the cuboids in the snapshot from any that we had requested.
		_requestedCuboids.removeAll(snapshot.completedCuboids().keySet());
		
		// We want to create a set of all the cuboids which are actually required, based on the entities.
		// (we will use this for load/unload decisions)
		Set<CuboidAddress> referencedCuboids = new HashSet<>();
		for (Map.Entry<Integer, ClientState> elt : _connectedClients.entrySet())
		{
			int clientId = elt.getKey();
			ClientState state = elt.getValue();
			
			// Update the location snapshot in the ClientState in case the entity moved.
			Entity entity = snapshot.completedEntities().get(clientId);
			// This may not be here if they just joined.
			if (null != entity)
			{
				state.location = entity.location();
			}
			
			_sendUpdatesToClient(clientId, state, snapshot, referencedCuboids);
		}
		
		
		// Note that we will clear the removed clients BEFORE adding new ones since it is theoretically possible for
		// a client to disconnect and reconnect in the same tick and the logic has this implicit assumption.
		// Create a copy of the removed clients so we can clear the long-lived container.
		// (we removed this from the connected clients, earlier).
		Collection<Integer> removedClients = new ArrayList<>(_removedClients);
		_removedClients.clear();
		
		// Determine what we should unload.
		// -start with the currently loaded cuboids
		Set<CuboidAddress> orphanedCuboids = new HashSet<>(snapshot.completedCuboids().keySet());
		// -remove any which we referenced via entities
		orphanedCuboids.removeAll(referencedCuboids);
		// -remove any which we already know are pending loads
		orphanedCuboids.removeAll(_requestedCuboids);
		
		// Determine what cuboids were referenced which are not yet loaded.
		Set<CuboidAddress> cuboidsToLoad = new HashSet<>(referencedCuboids);
		// -remove those we already requested
		cuboidsToLoad.removeAll(_requestedCuboids);
		// -remove those which are already loaded.
		cuboidsToLoad.removeAll(snapshot.completedCuboids().keySet());
		
		// Request that we save back anything we are unloading before we request that anything new be loaded.
		// (this is most important in the corner-case where the same entity left and rejoined in the same tick)
		if (!orphanedCuboids.isEmpty() || !removedClients.isEmpty())
		{
			List<IReadOnlyCuboidData> cuboidsToPackage = orphanedCuboids.stream().map(
					(CuboidAddress address) -> snapshot.completedCuboids().get(address)
			).toList();
			Map<CuboidAddress, List<CreatureEntity>> creaturesToUnload = _findCreaturesToUnload(cuboidsToPackage);
			Collection<PackagedCuboid> saveCuboids = _packageCuboidsForUnloading(cuboidsToPackage, creaturesToUnload);
			List<Entity> entitiesToPackage = removedClients.stream().map(
					(Integer id) -> snapshot.completedEntities().get(id)
			).filter(
					// Note that these entities might be missing if they disconnect before they appear in a snapshot (very unusual but can happen in unit tests).
					(Entity entity) -> (null != entity)
			).toList();
			Collection<SuspendedEntity> saveEntities = _packageEntitiesForUnloading(entitiesToPackage);
			_callouts.resources_writeToDisk(saveCuboids, saveEntities);
		}
		
		// Package up anything else which is loaded and pass them off to the resource loader for best-efforts eventual serialization (these may be ignored if the loader is busy).
		if (0 == (snapshot.tickNumber() % FORCE_FLUSH_TICK_FREQUENCY))
		{
			_writeRemainingToDisk(snapshot, orphanedCuboids, removedClients);
		}
		
		// Request any missing cuboids or new entities and see what we got back from last time.
		Collection<SuspendedCuboid<CuboidData>> newCuboids = new ArrayList<>();
		Collection<SuspendedEntity> newEntities = new ArrayList<>();
		_callouts.resources_getAndRequestBackgroundLoad(newCuboids, newEntities, cuboidsToLoad, _newClients.keySet());
		_requestedCuboids.addAll(cuboidsToLoad);
		// We have requested the clients so drop this, now.
		_clientsPendingLoad.putAll(_newClients);
		_newClients.clear();
		
		// Walk through any new clients, adding them to the world.
		for (SuspendedEntity suspended : newEntities)
		{
			// Adding this here means that the client will see their entity join the world in a future tick, after they receive the snapshot from the previous tick.
			// This client is now connected and can receive events.
			Entity newEntity = suspended.entity();
			int clientId = newEntity.id();
			String clientName = _clientsPendingLoad.remove(clientId);
			Assert.assertTrue(null != clientName);
			
			// Notify the other clients that they joined and tell them about the other clients.
			for (Map.Entry<Integer, ClientState> existingClient : _connectedClients.entrySet())
			{
				int existingClientId = existingClient.getKey();
				String existingClientName = existingClient.getValue().name;
				Assert.assertTrue(null != existingClientName);
				
				// Tell the old client about the new client.
				_callouts.network_sendClientJoined(existingClientId, clientId, clientName);
				// Tell the new client about the old client.
				_callouts.network_sendClientJoined(clientId, existingClientId, existingClientName);
			}
			
			// We can now add them to the fully-connected clients.
			_connectedClients.put(clientId, new ClientState(clientName, newEntity.location()));
		}
		
		// Push any required data into the TickRunner before we kick-off the tick.
		// We need to run through these to make them the read-only variants for the TickRunner.
		Collection<SuspendedCuboid<IReadOnlyCuboidData>> readOnlyCuboids = newCuboids.stream().map(
				(SuspendedCuboid<CuboidData> readWrite) -> new SuspendedCuboid<IReadOnlyCuboidData>(readWrite.cuboid(), readWrite.heightMap(), readWrite.creatures(), readWrite.mutations())
		).toList();
		
		// Feed in any new data from the network.
		Iterator<Integer> readableClientIterator = _clientsToRead.iterator();
		while (readableClientIterator.hasNext())
		{
			boolean isStillReadable = _drainPacketsIntoRunner(readableClientIterator.next());
			if (!isStillReadable)
			{
				readableClientIterator.remove();
			}
		}
		
		return new TickChanges(readOnlyCuboids, orphanedCuboids, newEntities, removedClients);
	}

	public void broadcastConfig(WorldConfig config)
	{
		Assert.assertTrue(Thread.currentThread() == _ownerThread);
		for (Integer clientId : _connectedClients.keySet())
		{
			_callouts.network_sendConfig(clientId, config);
		}
	}

	public void sendConsoleMessage(int targetId, String message)
	{
		Assert.assertTrue(Thread.currentThread() == _ownerThread);
		int consoleId = 0;
		_relayChatMessage(targetId, consoleId, message);
	}

	public void shutdown()
	{
		Assert.assertTrue(Thread.currentThread() == _ownerThread);
		// Finish any remaining write-back.
		if (!_completedCuboids.isEmpty() || !_completedEntities.isEmpty())
		{
			// We need to package up the cuboids with any suspended operations.
			Map<CuboidAddress, List<CreatureEntity>> creaturesToUnload = _findCreaturesToUnload(_completedCuboids);
			Collection<PackagedCuboid> cuboidResources = _packageCuboidsForUnloading(_completedCuboids, creaturesToUnload);
			Collection<SuspendedEntity> entityResources = _packageEntitiesForUnloading(_completedEntities);
			_callouts.resources_writeToDisk(cuboidResources, entityResources);
		}
	}


	private void _sendUpdatesToClient(int clientId
			, ClientState state
			, TickRunner.Snapshot snapshot
			, Set<CuboidAddress> out_referencedCuboids
	)
	{
		// We want to send the mutations for any of the cuboids and entities which are already loaded.
		_sendEntityUpdates(clientId, state, snapshot);
		
		_sendCuboidUpdates(clientId, state, snapshot, out_referencedCuboids);
		
		// Finally, send them the end of tick.
		// (note that the commit level won't be in the snapshot if they just joined).
		long commitLevel = snapshot.commitLevels().containsKey(clientId)
				? snapshot.commitLevels().get(clientId)
				: 0L
		;
		_callouts.network_sendEndOfTick(clientId, snapshot.tickNumber(), commitLevel);
	}

	private boolean _drainPacketsIntoRunner(int clientId)
	{
		// Consume as many of these mutations as we can fit into the TickRunner.
		PacketFromClient packet = _callouts.network_peekOrRemoveNextPacketFromClient(clientId, null);
		// We are calling this in response to readability so this cannot be null.
		Assert.assertTrue(null != packet);
		
		boolean didHandle = _handleIncomingPacket(clientId, packet);
		while (didHandle)
		{
			packet = _callouts.network_peekOrRemoveNextPacketFromClient(clientId, packet);
			if (null != packet)
			{
				didHandle = _handleIncomingPacket(clientId, packet);
			}
			else
			{
				didHandle = false;
			}
		}
		// If we didn't consume everything, there is still more to read.
		return (null != packet);
	}

	private boolean _handleIncomingPacket(int clientId, Packet packet)
	{
		boolean didHandle;
		
		// We know that there are only 2 packet types currently passed through to this point.
		// TODO:  Find a more elegant way to restrict this in the future - maybe different sub-interfaces, or something.
		if (packet instanceof Packet_MutationEntityFromClient)
		{
			Packet_MutationEntityFromClient mutation = (Packet_MutationEntityFromClient) packet;
			// This doesn't need to enter the TickRunner at any particular time so we can add it here and it will be rolled into the next tick.
			didHandle = _callouts.runner_enqueueEntityChange(clientId, mutation.mutation, mutation.commitLevel);
		}
		else if (packet instanceof Packet_SendChatMessage)
		{
			Packet_SendChatMessage chat = (Packet_SendChatMessage) packet;
			_relayChatMessage(chat.targetId, clientId, chat.message);
			didHandle = true;
		}
		else
		{
			// This means that a new message type made its way to the server.
			throw Assert.unreachable();
		}
		return didHandle;
	}

	private void _sendEntityUpdates(int clientId, ClientState state, TickRunner.Snapshot snapshot)
	{
		// Add any entities this client hasn't seen.
		Consumer<Entity> seenEntityHandler = (Entity entity) -> {
			// TODO:  This should only send the changed data, not entire entities or partials.
			int entityId = entity.id();
			if (clientId == entityId)
			{
				// The client has the full entity so send it.
				IEntityUpdate update = new MutationEntitySetEntity(entity);
				_callouts.network_sendEntityUpdate(clientId, entityId, update);
			}
			else
			{
				// The client will have a partial so just send that.
				IPartialEntityUpdate update = new MutationEntitySetPartialEntity(PartialEntity.fromEntity(entity));
				_callouts.network_sendPartialEntityUpdate(clientId, entityId, update);
			}
		};
		Consumer<Entity> unseenEntityHandler = (Entity entity) -> {
			// See if this is "them" or someone else.
			int entityId = entity.id();
			if (clientId == entityId)
			{
				_callouts.network_sendFullEntity(clientId, entity);
			}
			else
			{
				PartialEntity partial = PartialEntity.fromEntity(entity);
				_callouts.network_sendPartialEntity(clientId, partial);
			}
		};
		IntConsumer movedAwayHandler = (int entityId) -> {
			_callouts.network_removeEntity(clientId, entityId);
		};
		Function<Entity, EntityLocation> entityLocationFetcher = (Entity entity) -> entity.location();
		_sendNewAndUpdatedEntities(state
				, seenEntityHandler
				, unseenEntityHandler
				, movedAwayHandler
				, snapshot.completedEntities()
				, snapshot.updatedEntities()
				, entityLocationFetcher
		);
		
		Consumer<CreatureEntity> seenCreatureHandler = (CreatureEntity entity) -> {
			// Creatures are always partial.
			int entityId = entity.id();
			IPartialEntityUpdate update = new MutationEntitySetPartialEntity(PartialEntity.fromCreature(entity));
			_callouts.network_sendPartialEntityUpdate(clientId, entityId, update);
		};
		Consumer<CreatureEntity> unseenCreatureHandler = (CreatureEntity entity) -> {
			// Creatures are always partial.
			PartialEntity partial = PartialEntity.fromCreature(entity);
			_callouts.network_sendPartialEntity(clientId, partial);
		};
		Function<CreatureEntity, EntityLocation> creatureLocationFetcher = (CreatureEntity entity) -> entity.location();
		_sendNewAndUpdatedEntities(state
				, seenCreatureHandler
				, unseenCreatureHandler
				, movedAwayHandler
				, snapshot.completedCreatures()
				, snapshot.visiblyChangedCreatures()
				, creatureLocationFetcher
		);
		
		// If there are any entities in the state which aren't in the snapshot, remove them since they died or disconnected.
		Set<Integer> allEntityIds = new HashSet<>(snapshot.completedEntities().keySet());
		allEntityIds.addAll(snapshot.completedCreatures().keySet());
		Iterator<Integer> entityIterator = state.knownEntities.iterator();
		while (entityIterator.hasNext())
		{
			int entityId = entityIterator.next();
			if (!allEntityIds.contains(entityId))
			{
				_callouts.network_removeEntity(clientId, entityId);
				entityIterator.remove();
			}
		}
	}

	private <E> void _sendNewAndUpdatedEntities(ClientState state
			, Consumer<E> seenHandler
			, Consumer<E> unseenHandler
			, IntConsumer movedAwayHandler
			, Map<Integer, E> completed
			, Map<Integer, E> updated
			, Function<E, EntityLocation> locationFetcher
	)
	{
		for (Map.Entry<Integer, E> entry : completed.entrySet())
		{
			int entityId = entry.getKey();
			E entity = entry.getValue();
			EntityLocation location = locationFetcher.apply(entity);
			float distance = SpatialHelpers.distanceBetween(state.location, location);
			if (state.knownEntities.contains(entityId))
			{
				// See if they are too far away.
				if (distance > ENTITY_VISIBLE_DISTANCE)
				{
					// This is too far away so discard it.
					movedAwayHandler.accept(entityId);
					state.knownEntities.remove(entityId);
				}
				else
				{
					// We know this entity so generate the update for this client.
					E newEntity = updated.get(entityId);
					if (null != newEntity)
					{
						seenHandler.accept(newEntity);
					}
				}
			}
			else if (distance <= ENTITY_VISIBLE_DISTANCE)
			{
				// We don't know this entity, and they are close by, so send them.
				unseenHandler.accept(entity);
				state.knownEntities.add(entityId);
			}
		}
	}
	
	private void _sendCuboidUpdates(int clientId
			, ClientState state
			, TickRunner.Snapshot snapshot
			, Set<CuboidAddress> out_referencedCuboids
	)
	{
		// See if this entity has seen the cuboid where they are standing or the surrounding cuboids.
		// TODO:  This should be optimized around entity movement and cuboid generation, as opposed to this "spinning" approach.
		
		CuboidAddress currentCuboid = state.location.getBlockLocation().getCuboidAddress();
		
		// Walk the existing cuboids and remove any which are out of range.
		Iterator<CuboidAddress> iter = state.knownCuboids.iterator();
		while (iter.hasNext())
		{
			CuboidAddress address = iter.next();
			int xDelta = Math.abs(currentCuboid.x() - address.x());
			int yDelta = Math.abs(currentCuboid.y() - address.y());
			int zDelta = Math.abs(currentCuboid.z() - address.z());
			if ((xDelta > 1) || (yDelta > 1) || (zDelta > 1))
			{
				_callouts.network_removeCuboid(clientId, address);
				iter.remove();
			}
		}
		
		// Check the cuboids immediately around the entity and send any which are missing.
		for (int i = -1; i <= 1; ++i)
		{
			for (int j = -1; j <= 1; ++j)
			{
				for (int k = -1; k <= 1; ++k)
				{
					CuboidAddress oneCuboid = currentCuboid.getRelative(i, j, k);
					if (state.knownCuboids.contains(oneCuboid))
					{
						// We know about this cuboid so send any updates.
						List<MutationBlockSetBlock> mutations = snapshot.resultantBlockChangesByCuboid().get(oneCuboid);
						if (null != mutations)
						{
							for (MutationBlockSetBlock mutation : mutations)
							{
								_callouts.network_sendBlockUpdate(clientId, mutation);
							}
						}
					}
					else
					{
						// We haven't seen this yet so just send it.
						IReadOnlyCuboidData cuboidData = snapshot.completedCuboids().get(oneCuboid);
						// This may not yet be loaded.
						if (null != cuboidData)
						{
							_callouts.network_sendCuboid(clientId, cuboidData);
							state.knownCuboids.add(oneCuboid);
						}
						else
						{
							// Not yet loaded - we will either request this based on out_referencedCuboids or already did.
						}
					}
					// Record that we referenced this.
					out_referencedCuboids.add(oneCuboid);
				}
			}
		}
	}

	private Map<CuboidAddress, List<CreatureEntity>> _findCreaturesToUnload(Collection<IReadOnlyCuboidData> cuboidsToPackage)
	{
		Map<CuboidAddress, List<CreatureEntity>> creaturesToUnload = new HashMap<>();
		for (IReadOnlyCuboidData cuboid : cuboidsToPackage)
		{
			CuboidAddress address = cuboid.getCuboidAddress();
			creaturesToUnload.put(address, new ArrayList<>());
		}
		// TODO:  Change this to use some sort of spatial look-up mechanism since this loop is attrocious.
		for (CreatureEntity creature : _completedCreatures)
		{
			CuboidAddress address = creature.location().getBlockLocation().getCuboidAddress();
			List<CreatureEntity> list = creaturesToUnload.get(address);
			if (null != list)
			{
				list.add(creature);
			}
		}
		return creaturesToUnload;
	}

	private Collection<PackagedCuboid> _packageCuboidsForUnloading(Collection<IReadOnlyCuboidData> cuboidsToPackage, Map<CuboidAddress, List<CreatureEntity>> creaturesToUnload)
	{
		Collection<PackagedCuboid> cuboidResources = new ArrayList<>();
		for (IReadOnlyCuboidData cuboid : cuboidsToPackage)
		{
			CuboidAddress address = cuboid.getCuboidAddress();
			List<CreatureEntity> entities = creaturesToUnload.get(cuboid.getCuboidAddress());
			List<ScheduledMutation> suspended = _scheduledBlockMutations.get(address);
			if (null == suspended)
			{
				suspended = List.of();
			}
			cuboidResources.add(new PackagedCuboid(cuboid, entities, suspended));
		}
		return cuboidResources;
	}

	private Collection<SuspendedEntity> _packageEntitiesForUnloading(Collection<Entity> entitiesToPackage)
	{
		Collection<SuspendedEntity> entityResources = new ArrayList<>();
		for (Entity entity : entitiesToPackage)
		{
			int id = entity.id();
			List<ScheduledChange> suspended = _scheduledEntityMutations.get(id);
			if (null == suspended)
			{
				suspended = List.of();
			}
			entityResources.add(new SuspendedEntity(entity, suspended));
		}
		return entityResources;
	}

	public void _relayChatMessage(int targetId, int senderId, String message)
	{
		// We interpret 0 as "all" but otherwise just make sure that they are here.
		// (note that this is allowed to silently fail due to network races, etc).
		if (0 == targetId)
		{
			for (Integer clientId : _connectedClients.keySet())
			{
				_callouts.network_sendChatMessage(clientId, senderId, message);
			}
		}
		else if (_connectedClients.containsKey(targetId))
		{
			_callouts.network_sendChatMessage(targetId, senderId, message);
		}
	}

	private void _writeRemainingToDisk(TickRunner.Snapshot snapshot, Set<CuboidAddress> orphanedCuboids, Collection<Integer> removedClients)
	{
		Map<CuboidAddress, IReadOnlyCuboidData> remainingCuboids = new HashMap<>(snapshot.completedCuboids());
		remainingCuboids.keySet().removeAll(orphanedCuboids);
		Map<Integer, Entity> remainingEntities = new HashMap<>(snapshot.completedEntities());
		remainingEntities.keySet().removeAll(removedClients);
		
		if (!remainingCuboids.isEmpty() || !remainingEntities.isEmpty())
		{
			Map<CuboidAddress, List<CreatureEntity>> remainingCreatures = _findCreaturesToUnload(remainingCuboids.values());
			Collection<PackagedCuboid> saveCuboids = _packageCuboidsForUnloading(remainingCuboids.values(), remainingCreatures);
			Collection<SuspendedEntity> saveEntities = _packageEntitiesForUnloading(remainingEntities.values());
			_callouts.resources_tryWriteToDisk(saveCuboids, saveEntities);
		}
	}


	public static interface ICallouts
	{
		// ResourceLoader.
		void resources_writeToDisk(Collection<PackagedCuboid> cuboids, Collection<SuspendedEntity> entities);
		/**
		 * Similar to resources_writeToDisk but this call is allowed to silently fail, so long as it does so atomically.
		 * 
		 * @param cuboids Cuboids which are remaining loaded.
		 * @param entities Entities which are remaining loaded.
		 */
		void resources_tryWriteToDisk(Collection<PackagedCuboid> cuboids, Collection<SuspendedEntity> entities);
		void resources_getAndRequestBackgroundLoad(Collection<SuspendedCuboid<CuboidData>> out_loadedCuboids
				, Collection<SuspendedEntity> out_loadedEntities
				, Collection<CuboidAddress> requestedCuboids
				, Collection<Integer> requestedEntityIds
		);
		
		// IServerAdapter.
		PacketFromClient network_peekOrRemoveNextPacketFromClient(int clientId, PacketFromClient toRemove);
		void network_sendFullEntity(int clientId, Entity entity);
		void network_sendPartialEntity(int clientId, PartialEntity entity);
		void network_removeEntity(int clientId, int entityId);
		void network_sendCuboid(int clientId, IReadOnlyCuboidData cuboid);
		void network_removeCuboid(int clientId, CuboidAddress address);
		void network_sendEntityUpdate(int clientId, int entityId, IEntityUpdate update);
		void network_sendPartialEntityUpdate(int clientId, int entityId, IPartialEntityUpdate update);
		void network_sendBlockUpdate(int clientId, MutationBlockSetBlock update);
		void network_sendEndOfTick(int clientId, long tickNumber, long latestLocalCommitIncluded);
		void network_sendConfig(int clientId, WorldConfig config);
		void network_sendClientJoined(int clientId, int joinedClientId, String name);
		void network_sendClientLeft(int clientId, int leftClientId);
		void network_sendChatMessage(int clientId, int senderId, String message);
		
		// TickRunner.
		boolean runner_enqueueEntityChange(int entityId, IMutationEntity<IMutablePlayerEntity> change, long commitLevel);
	}

	public static record TickChanges(Collection<SuspendedCuboid<IReadOnlyCuboidData>> newCuboids
			, Collection<CuboidAddress> cuboidsToUnload
			, Collection<SuspendedEntity> newEntities
			, Collection<Integer> entitiesToUnload
	) {}

	private static final class ClientState
	{
		private final String name;
		public EntityLocation location;
		
		// The data we think that this client already has.  These are used for determining what they should be told to load/drop as well as filtering updates to what they can apply.
		public final Set<Integer> knownEntities;
		public final Set<CuboidAddress> knownCuboids;
		
		public ClientState(String name, EntityLocation initialLocation)
		{
			this.name = name;
			this.location = initialLocation;
			
			// Create empty containers for what this client has observed.
			this.knownEntities = new HashSet<>();
			this.knownCuboids = new HashSet<>();
		}
	}
}
