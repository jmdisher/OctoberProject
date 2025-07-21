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
import java.util.stream.Collectors;

import com.jeffdisher.october.actions.EntityChangeTopLevelMovement;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.ScheduledChange;
import com.jeffdisher.october.logic.ScheduledMutation;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.mutations.IEntityUpdate;
import com.jeffdisher.october.mutations.IPartialEntityUpdate;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
import com.jeffdisher.october.mutations.MutationEntitySetEntity;
import com.jeffdisher.october.mutations.MutationEntitySetPartialEntity;
import com.jeffdisher.october.net.Packet;
import com.jeffdisher.october.net.PacketFromClient;
import com.jeffdisher.october.net.Packet_ClientUpdateOptions;
import com.jeffdisher.october.net.Packet_MutationEntityFromClient;
import com.jeffdisher.october.net.Packet_SendChatMessage;
import com.jeffdisher.october.persistence.PackagedCuboid;
import com.jeffdisher.october.persistence.SuspendedCuboid;
import com.jeffdisher.october.persistence.SuspendedEntity;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.MinimalEntity;
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
	/**
	 * The number of cuboids which will be attempted to be sent when the network is idle.
	 */
	public static final int CUBOIDS_SENT_PER_TICK = 10;
	/**
	 * Cuboids within this distance of an entity will be considered "high priority" and aggressively sent.
	 */
	public static final int PRIORITY_CUBOID_VIEW_DISTANCE = 1;

	private final ICallouts _callouts;
	private final Map<Integer, ClientState> _connectedClients;
	private final Map<Integer, String> _newClients;
	private final Queue<Integer> _removedClients;
	private final Set<Integer> _clientsToRead;
	private final Map<Integer, String> _clientsPendingLoad;
	private Thread _ownerThread;

	// It could take several ticks for a cuboid to be loaded/generated and we don't want to redundantly load them so track what is pending.
	private Set<CuboidAddress> _requestedCuboids;
	
	// We store the elements we need from the most recent TickRunner.Snapshot locally.
	private long _tickNumber;
	private Map<CuboidAddress, IReadOnlyCuboidData> _completedCuboids;
	private Map<CuboidAddress, List<ScheduledMutation>> _scheduledBlockMutations;
	private Map<CuboidAddress, Map<BlockAddress, Long>> _periodicBlockMutations;
	private Map<Integer, List<ScheduledChange>> _scheduledEntityMutations;
	private Map<Integer, Entity> _completedEntities;
	private Map<Integer, Entity> _updatedEntities;
	private Map<Integer, Long> _commitLevels;
	private Map<Integer, CreatureEntity> _completedCreatures;
	private Map<Integer, CreatureEntity> _visiblyChangedCreatures;
	private Map<CuboidAddress, List<MutationBlockSetBlock>> _blockChanges;

	public ServerStateManager(ICallouts callouts)
	{
		_callouts = callouts;
		_connectedClients = new HashMap<>();
		_newClients = new HashMap<>();
		_removedClients = new LinkedList<>();
		_clientsToRead = new HashSet<>();
		_clientsPendingLoad = new HashMap<>();
		
		_tickNumber = 0L;
		_requestedCuboids = new HashSet<>();
		_completedCuboids = Collections.emptyMap();
		_scheduledBlockMutations = Collections.emptyMap();
		_periodicBlockMutations = Collections.emptyMap();
		_scheduledEntityMutations = Collections.emptyMap();
		_completedEntities = Collections.emptyMap();
		_updatedEntities = Collections.emptyMap();
		_commitLevels = Collections.emptyMap();
		_completedCreatures = Collections.emptyMap();
		_visiblyChangedCreatures = Collections.emptyMap();
		_blockChanges = Collections.emptyMap();
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
		
		// We will first tear the snapshot apart and cache the relevant parts of it in our state (then base all decisions on the state).
		_tickNumber = snapshot.tickNumber();
		_completedCuboids = snapshot.cuboids().entrySet().stream().collect(Collectors.toMap(
				(Map.Entry<CuboidAddress, TickRunner.SnapshotCuboid> elt) -> elt.getKey()
				, (Map.Entry<CuboidAddress, TickRunner.SnapshotCuboid> elt) -> elt.getValue().completed()
		));
		_completedEntities = snapshot.entities().entrySet().stream().collect(Collectors.toMap(
				(Map.Entry<Integer, TickRunner.SnapshotEntity> elt) -> elt.getKey()
				, (Map.Entry<Integer, TickRunner.SnapshotEntity> elt) -> elt.getValue().completed()
		));
		_updatedEntities = snapshot.entities().entrySet().stream().filter(
				(Map.Entry<Integer, TickRunner.SnapshotEntity> elt) -> (null != elt.getValue().updated())
		).collect(Collectors.toMap(
				(Map.Entry<Integer, TickRunner.SnapshotEntity> elt) -> elt.getKey()
				, (Map.Entry<Integer, TickRunner.SnapshotEntity> elt) -> elt.getValue().completed()
		));
		_commitLevels = snapshot.entities().entrySet().stream().collect(Collectors.toMap(
				(Map.Entry<Integer, TickRunner.SnapshotEntity> elt) -> elt.getKey()
				, (Map.Entry<Integer, TickRunner.SnapshotEntity> elt) -> elt.getValue().commitLevel()
		));
		_completedCreatures = snapshot.creatures().entrySet().stream().collect(Collectors.toMap(
				(Map.Entry<Integer, TickRunner.SnapshotCreature> elt) -> elt.getKey()
				, (Map.Entry<Integer, TickRunner.SnapshotCreature> elt) -> elt.getValue().completed()
		));
		_visiblyChangedCreatures = snapshot.creatures().entrySet().stream().filter(
				(Map.Entry<Integer, TickRunner.SnapshotCreature> elt) -> (null != elt.getValue().visiblyChanged())
		).collect(Collectors.toMap(
				(Map.Entry<Integer, TickRunner.SnapshotCreature> elt) -> elt.getKey()
				, (Map.Entry<Integer, TickRunner.SnapshotCreature> elt) -> elt.getValue().visiblyChanged()
		));
		_blockChanges = snapshot.cuboids().entrySet().stream().filter(
				(Map.Entry<CuboidAddress, TickRunner.SnapshotCuboid> elt) -> (null != elt.getValue().blockChanges())
		).collect(Collectors.toMap(
				(Map.Entry<CuboidAddress, TickRunner.SnapshotCuboid> elt) -> elt.getKey()
				, (Map.Entry<CuboidAddress, TickRunner.SnapshotCuboid> elt) -> elt.getValue().blockChanges()
		));
		_scheduledBlockMutations = snapshot.cuboids().entrySet().stream().collect(Collectors.toMap(
				(Map.Entry<CuboidAddress, TickRunner.SnapshotCuboid> elt) -> elt.getKey()
				, (Map.Entry<CuboidAddress, TickRunner.SnapshotCuboid> elt) -> elt.getValue().scheduledBlockMutations()
		));
		_periodicBlockMutations = snapshot.cuboids().entrySet().stream().collect(Collectors.toMap(
				(Map.Entry<CuboidAddress, TickRunner.SnapshotCuboid> elt) -> elt.getKey()
				, (Map.Entry<CuboidAddress, TickRunner.SnapshotCuboid> elt) -> elt.getValue().periodicMutationMillis()
		));
		_scheduledEntityMutations = snapshot.entities().entrySet().stream().collect(Collectors.toMap(
				(Map.Entry<Integer, TickRunner.SnapshotEntity> elt) -> elt.getKey()
				, (Map.Entry<Integer, TickRunner.SnapshotEntity> elt) -> elt.getValue().scheduledMutations()
		));
		
		Set<CuboidAddress> completedCuboidAddresses = _completedCuboids.keySet();
		
		// Remove any of the cuboids in the snapshot from any that we had requested.
		_requestedCuboids.removeAll(completedCuboidAddresses);
		
		// We want to create a set of all the cuboids which are actually required, based on the entities.
		// (we will use this for load/unload decisions)
		Set<CuboidAddress> referencedCuboids = _findReferencedCuboids(_connectedClients.values());
		
		for (Map.Entry<Integer, ClientState> elt : _connectedClients.entrySet())
		{
			int clientId = elt.getKey();
			ClientState state = elt.getValue();
			
			// Update the location snapshot in the ClientState in case the entity moved.
			Entity entity = _completedEntities.get(clientId);
			// This may not be here if they just joined.
			CuboidAddress newCuboidLocation = null;
			if (null != entity)
			{
				state.location = entity.location();
				CuboidAddress newAddress = state.location.getBlockLocation().getCuboidAddress();
				if ((null == state.lastComputedAddress) || !state.lastComputedAddress.equals(newAddress))
				{
					newCuboidLocation = newAddress;
				}
			}
			
			_sendUpdatesToClient(clientId, state, newCuboidLocation, snapshot.postedEvents());
		}
		
		// Note that we will clear the removed clients BEFORE adding new ones since it is theoretically possible for
		// a client to disconnect and reconnect in the same tick and the logic has this implicit assumption.
		// Create a copy of the removed clients so we can clear the long-lived container.
		// (we removed this from the connected clients, earlier).
		Collection<Integer> removedClients = new ArrayList<>(_removedClients);
		_removedClients.clear();
		
		// Determine what we should unload.
		// -start with the currently loaded cuboids
		Set<CuboidAddress> orphanedCuboids = new HashSet<>(completedCuboidAddresses);
		// -remove any which we referenced via entities
		orphanedCuboids.removeAll(referencedCuboids);
		// -remove any which we already know are pending loads
		orphanedCuboids.removeAll(_requestedCuboids);
		
		// Determine what cuboids were referenced which are not yet loaded.
		Set<CuboidAddress> cuboidsToLoad = new HashSet<>(referencedCuboids);
		// -remove those we already requested
		cuboidsToLoad.removeAll(_requestedCuboids);
		// -remove those which are already loaded.
		cuboidsToLoad.removeAll(completedCuboidAddresses);
		
		// Request that we save back anything we are unloading before we request that anything new be loaded.
		_handleEndOfTickWriteBack(removedClients, orphanedCuboids);
		
		// Request any missing cuboids or new entities and see what we got back from last time.
		Collection<SuspendedCuboid<CuboidData>> newlyLoadedCuboids = new ArrayList<>();
		Collection<SuspendedEntity> newlyLoadedEntities = new ArrayList<>();
		_handleResourceLoading(newlyLoadedCuboids, newlyLoadedEntities, cuboidsToLoad);
		
		// Walk through any new clients, adding them to the world.
		_handleConnectingClientsForNewEntities(newlyLoadedEntities);
		
		// Push any required data into the TickRunner before we kick-off the tick.
		// We need to run through these to make them the read-only variants for the TickRunner.
		Collection<SuspendedCuboid<IReadOnlyCuboidData>> readOnlyCuboids = newlyLoadedCuboids.stream().map(
				(SuspendedCuboid<CuboidData> readWrite) -> new SuspendedCuboid<IReadOnlyCuboidData>(readWrite.cuboid(), readWrite.heightMap(), readWrite.creatures(), readWrite.pendingMutations(), readWrite.periodicMutationMillis())
		).toList();
		
		// Feed in any new data from the network.
		_drainAllClientPacketsAndUpdateClients();
		
		return new TickChanges(readOnlyCuboids, orphanedCuboids, newlyLoadedEntities, removedClients);
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

	public void setClientViewDistance(int clientId, int distance)
	{
		// Check the static assumptions.
		Assert.assertTrue(Thread.currentThread() == _ownerThread);
		Assert.assertTrue(distance >= 0);
		
		// If the client sends this before they are connected (meaning we loaded their data, we just ignore this).
		ClientState state = _connectedClients.get(clientId);
		if ((null != state) && (state.cuboidViewDistance != distance))
		{
			// Set the distance and clear the latest address so we rebuild it.
			state.cuboidViewDistance = distance;
			state.lastComputedAddress = null;
		}
	}

	public void shutdown()
	{
		Assert.assertTrue(Thread.currentThread() == _ownerThread);
		// Finish any remaining write-back.
		if (!_completedCuboids.isEmpty() || !_completedEntities.isEmpty())
		{
			// We need to package up the cuboids with any suspended operations.
			Map<CuboidAddress, List<CreatureEntity>> creaturesToUnload = _findCreaturesToUnload(_completedCuboids.values());
			Collection<PackagedCuboid> cuboidResources = _packageCuboidsForUnloading(_completedCuboids.values(), creaturesToUnload);
			Collection<SuspendedEntity> entityResources = _packageEntitiesForUnloading(_completedEntities.values());
			_callouts.resources_writeToDisk(cuboidResources, entityResources);
		}
	}


	private Set<CuboidAddress> _findReferencedCuboids(Collection<ClientState> clientStates)
	{
		Set<CuboidAddress> referencedCuboids = new HashSet<>();
		for (ClientState state : clientStates)
		{
			CuboidAddress currentCuboid = state.location.getBlockLocation().getCuboidAddress();
			int minDistance = -state.cuboidViewDistance;
			int maxDistance = state.cuboidViewDistance;
			for (int i = minDistance; i <= maxDistance; ++i)
			{
				for (int j = minDistance; j <= maxDistance; ++j)
				{
					for (int k = minDistance; k <= maxDistance; ++k)
					{
						CuboidAddress oneCuboid = currentCuboid.getRelative(i, j, k);
						referencedCuboids.add(oneCuboid);
					}
				}
			}
		}
		return referencedCuboids;
	}

	private void _handleEndOfTickWriteBack(Collection<Integer> removedClients, Set<CuboidAddress> orphanedCuboids)
	{
		// (this is most important in the corner-case where the same entity left and rejoined in the same tick)
		if (!orphanedCuboids.isEmpty() || !removedClients.isEmpty())
		{
			List<IReadOnlyCuboidData> cuboidsToPackage = orphanedCuboids.stream().map(
					(CuboidAddress address) -> _completedCuboids.get(address)
			).toList();
			Map<CuboidAddress, List<CreatureEntity>> creaturesToUnload = _findCreaturesToUnload(cuboidsToPackage);
			Collection<PackagedCuboid> saveCuboids = _packageCuboidsForUnloading(cuboidsToPackage, creaturesToUnload);
			List<Entity> entitiesToPackage = removedClients.stream().map(
					(Integer id) -> _completedEntities.get(id)
			).filter(
					// Note that these entities might be missing if they disconnect before they appear in a snapshot (very unusual but can happen in unit tests).
					(Entity entity) -> (null != entity)
			).toList();
			Collection<SuspendedEntity> saveEntities = _packageEntitiesForUnloading(entitiesToPackage);
			_callouts.resources_writeToDisk(saveCuboids, saveEntities);
		}
		
		// Package up anything else which is loaded and pass them off to the resource loader for best-efforts eventual serialization (these may be ignored if the loader is busy).
		if (0 == (_tickNumber % FORCE_FLUSH_TICK_FREQUENCY))
		{
			_writeRemainingToDisk(orphanedCuboids, removedClients);
		}
	}

	private void _handleResourceLoading(Collection<SuspendedCuboid<CuboidData>> out_loadedCuboids
			, Collection<SuspendedEntity> out_loadedEntities
			, Set<CuboidAddress> cuboidsToLoad
	)
	{
		_callouts.resources_getAndRequestBackgroundLoad(out_loadedCuboids, out_loadedEntities, cuboidsToLoad, _newClients.keySet());
		_requestedCuboids.addAll(cuboidsToLoad);
		// We have requested the clients so drop this, now.
		_clientsPendingLoad.putAll(_newClients);
		_newClients.clear();
	}

	private void _handleConnectingClientsForNewEntities(Collection<SuspendedEntity> newEntities)
	{
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
	}

	private void _sendUpdatesToClient(int clientId
			, ClientState state
			, CuboidAddress newCuboidLocation
			, List<EventRecord> postedEvents
	)
	{
		// We want to send information to this client if they can see it, potentially sending whole data or just
		// incremental updates, based on whether or not they already have this data.
		boolean shouldSendNewCuboids = _callouts.network_isNetworkWriteReady(clientId);
		
		if (null != newCuboidLocation)
		{
			// Before we send anything, we want to update our set of known and missing cuboids so we know what they should see.
			_updateRangeAndSendRemoves(clientId, state, newCuboidLocation);
		}
		
		// We send events, filtered by what they can currently see.
		_sendEvents(clientId, state, postedEvents);
		
		// We send entities based on how far away they are.
		_sendEntityUpdates(clientId, state);
		
		// We send cuboids based on how far away they are (also capture an update on what cuboids are visible).
		_sendCuboidUpdates(clientId, state, shouldSendNewCuboids);
		
		// Finally, send them the end of tick.
		// (note that the commit level won't be in the snapshot if they just joined).
		long commitLevel = _commitLevels.containsKey(clientId)
				? _commitLevels.get(clientId)
				: 0L
		;
		_callouts.network_sendEndOfTick(clientId, _tickNumber, commitLevel);
	}

	private void _drainAllClientPacketsAndUpdateClients()
	{
		Iterator<Integer> readableClientIterator = _clientsToRead.iterator();
		while (readableClientIterator.hasNext())
		{
			boolean isStillReadable = _drainPacketsIntoRunner(readableClientIterator.next());
			if (!isStillReadable)
			{
				readableClientIterator.remove();
			}
		}
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
		else if (packet instanceof Packet_ClientUpdateOptions)
		{
			Packet_ClientUpdateOptions message = (Packet_ClientUpdateOptions) packet;
			_callouts.handleClientUpdateOptions(clientId, message.clientViewDistance);
			didHandle = true;
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

	private void _sendEvents(int clientId, ClientState state, List<EventRecord> postedEvents)
	{
		for (EventRecord event : postedEvents)
		{
			// Based on the type, we determine whether we send this and how we send it (since some events may not want to include a location).
			switch (event.type())
			{
			case BLOCK_BROKEN:
			case BLOCK_PLACED:
			case LIQUID_REMOVED:
			case LIQUID_PLACED:
				// Include these if the cuboid is known to the client.
				if (state.knownCuboids.contains(event.location().getCuboidAddress()))
				{
					_callouts.network_sendBlockEvent(clientId, event.type(), event.location(), event.entitySource());
				}
				break;
			case ENTITY_HURT:
				// Include this if the entity is known to the client.
				if (state.knownEntities.contains(event.entityTarget()))
				{
					_callouts.network_sendEntityEvent(clientId, event.type(), event.cause(), event.location(), event.entityTarget(), event.entitySource());
				}
				break;
			case ENTITY_KILLED:
				// Include this all the time but only include location if they are known to the client.
				AbsoluteLocation targetLocation = state.knownEntities.contains(event.entityTarget())
						? event.location()
						: null
				;
				_callouts.network_sendEntityEvent(clientId, event.type(), event.cause(), targetLocation, event.entityTarget(), event.entitySource());
				break;
			default:
				// We must handle all types.
				throw Assert.unreachable();
			}
		}
	}

	private void _sendEntityUpdates(int clientId, ClientState state)
	{
		_sendNewAndUpdatedEntities(clientId, state);
		_sendNewAndUpdatedCreatures(clientId, state);
		
		// If there are any entities in the state which aren't in the snapshot, remove them since they died or disconnected.
		Set<Integer> allEntityIds = new HashSet<>(_completedEntities.keySet());
		allEntityIds.addAll(_completedCreatures.keySet());
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

	private void _sendNewAndUpdatedEntities(int clientId, ClientState state)
	{
		// Note that this is similar to _sendNewAndUpdatedCreatures but duplicated to avoid spreading logic with extra levels of indirection.
		EntityType playerType  = Environment.getShared().creatures.PLAYER;
		for (Map.Entry<Integer, Entity> entry : _completedEntities.entrySet())
		{
			int entityId = entry.getKey();
			Entity entity = entry.getValue();
			float distance = SpatialHelpers.distanceFromPlayerEyeToEntitySurface(state.location, playerType, MinimalEntity.fromEntity(entity));
			if (state.knownEntities.contains(entityId))
			{
				// See if they are too far away.
				if (distance > ENTITY_VISIBLE_DISTANCE)
				{
					// This is too far away so discard it.
					_callouts.network_removeEntity(clientId, entityId);
					state.knownEntities.remove(entityId);
				}
				else
				{
					// We know this entity so generate the update for this client.
					Entity newEntity = _updatedEntities.get(entityId);
					if (null != newEntity)
					{
						// TODO:  This should only send the changed data, not entire entities or partials.
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
					}
				}
			}
			else if (distance <= ENTITY_VISIBLE_DISTANCE)
			{
				// We don't know this entity, and they are close by, so send them.
				// See if this is "them" or someone else.
				if (clientId == entityId)
				{
					// This only won't be already known during the first tick after they join.
					_callouts.network_sendFullEntity(clientId, entity);
				}
				else
				{
					PartialEntity partial = PartialEntity.fromEntity(entity);
					_callouts.network_sendPartialEntity(clientId, partial);
				}
				state.knownEntities.add(entityId);
			}
		}
	}

	private void _sendNewAndUpdatedCreatures(int clientId, ClientState state)
	{
		// Note that this is similar to _sendNewAndUpdatedEntities but duplicated to avoid spreading logic with extra levels of indirection.
		EntityType playerType  = Environment.getShared().creatures.PLAYER;
		for (Map.Entry<Integer, CreatureEntity> entry : _completedCreatures.entrySet())
		{
			int entityId = entry.getKey();
			CreatureEntity entity = entry.getValue();
			float distance = SpatialHelpers.distanceFromPlayerEyeToEntitySurface(state.location, playerType, MinimalEntity.fromCreature(entity));
			if (state.knownEntities.contains(entityId))
			{
				// See if they are too far away.
				if (distance > ENTITY_VISIBLE_DISTANCE)
				{
					// This is too far away so discard it.
					_callouts.network_removeEntity(clientId, entityId);
					state.knownEntities.remove(entityId);
				}
				else
				{
					// We know this entity so generate the update for this client.
					CreatureEntity newEntity = _visiblyChangedCreatures.get(entityId);
					if (null != newEntity)
					{
						// Creatures are always partial.
						IPartialEntityUpdate update = new MutationEntitySetPartialEntity(PartialEntity.fromCreature(entity));
						_callouts.network_sendPartialEntityUpdate(clientId, entityId, update);
					}
				}
			}
			else if (distance <= ENTITY_VISIBLE_DISTANCE)
			{
				// We don't know this entity, and they are close by, so send them.
				// Creatures are always partial.
				PartialEntity partial = PartialEntity.fromCreature(entity);
				_callouts.network_sendPartialEntity(clientId, partial);
				state.knownEntities.add(entityId);
			}
		}
	}

	private void _sendCuboidUpdates(int clientId
			, ClientState state
			, boolean shouldSendNewCuboids
	)
	{
		// Send block updates in anything known to us.
		for (Map.Entry<CuboidAddress, List<MutationBlockSetBlock>> elt : _blockChanges.entrySet())
		{
			CuboidAddress address = elt.getKey();
			if (state.knownCuboids.contains(address))
			{
				List<MutationBlockSetBlock> mutations = elt.getValue();
				for (MutationBlockSetBlock mutation : mutations)
				{
					_callouts.network_sendBlockUpdate(clientId, mutation);
				}
			}
		}
		
		// Send cuboids if the network is idle.
		if (shouldSendNewCuboids && (!state.priorityMissingCuboids.isEmpty() || !state.outerMissingCuboids.isEmpty()))
		{
			int cuboidsSent = 0;
			
			// Send any high-priority cuboids we have.
			Iterator<CuboidAddress> iter = state.priorityMissingCuboids.iterator();
			while (iter.hasNext())
			{
				CuboidAddress address = iter.next();
				// We haven't seen this yet so just send it.
				IReadOnlyCuboidData cuboidData = _completedCuboids.get(address);
				// This may not yet be loaded.
				if (null != cuboidData)
				{
					_callouts.network_sendCuboid(clientId, cuboidData);
					state.knownCuboids.add(address);
					iter.remove();
					cuboidsSent += 1;
				}
				else
				{
					// Not yet loaded.
				}
			}
			
			// Attempt any more we can fit.
			if ((cuboidsSent < CUBOIDS_SENT_PER_TICK) && !state.outerMissingCuboids.isEmpty())
			{
				iter = state.outerMissingCuboids.iterator();
				while ((cuboidsSent < CUBOIDS_SENT_PER_TICK) && iter.hasNext())
				{
					CuboidAddress address = iter.next();
					// We haven't seen this yet so just send it.
					IReadOnlyCuboidData cuboidData = _completedCuboids.get(address);
					// This may not yet be loaded.
					if (null != cuboidData)
					{
						_callouts.network_sendCuboid(clientId, cuboidData);
						state.knownCuboids.add(address);
						iter.remove();
					}
					// We don't want to search this list too aggressively (it can be large) so we count not just how many we send but how many we attempt.
					cuboidsSent += 1;
				}
			}
		}
	}

	private void _updateRangeAndSendRemoves(int clientId, ClientState state, CuboidAddress currentCuboid)
	{
		int minDistance = -state.cuboidViewDistance;
		int maxDistance = state.cuboidViewDistance;
		
		// Walk the existing cuboids and remove any which are out of range.
		Iterator<CuboidAddress> iter = state.knownCuboids.iterator();
		while (iter.hasNext())
		{
			CuboidAddress address = iter.next();
			int xDelta = Math.abs(currentCuboid.x() - address.x());
			int yDelta = Math.abs(currentCuboid.y() - address.y());
			int zDelta = Math.abs(currentCuboid.z() - address.z());
			if ((xDelta > maxDistance) || (yDelta > maxDistance) || (zDelta > maxDistance))
			{
				_callouts.network_removeCuboid(clientId, address);
				iter.remove();
			}
		}
		
		// Check the cuboids immediately around the entity and add any which are missing to our list.
		// We will prioritize the closer cuboids.
		@SuppressWarnings("unchecked")
		List<CuboidAddress>[] sublists = new List[maxDistance + 1];
		for (int i = 0; i < sublists.length; ++i)
		{
			sublists[i] = new ArrayList<>();
		}
		for (int i = minDistance; i <= maxDistance; ++i)
		{
			for (int j = minDistance; j <= maxDistance; ++j)
			{
				for (int k = minDistance; k <= maxDistance; ++k)
				{
					CuboidAddress oneCuboid = currentCuboid.getRelative(i, j, k);
					if (!state.knownCuboids.contains(oneCuboid))
					{
						int max = Math.max(Math.max(Math.abs(i), Math.abs(j)), Math.abs(k));
						sublists[max].add(oneCuboid);
					}
				}
			}
		}
		state.priorityMissingCuboids.clear();
		int priorityLimit = Math.min(PRIORITY_CUBOID_VIEW_DISTANCE + 1, sublists.length);
		for (int i = 0; i < priorityLimit; ++i)
		{
			state.priorityMissingCuboids.addAll(sublists[i]);
		}
		state.outerMissingCuboids.clear();
		for (int i = priorityLimit; i < sublists.length; ++i)
		{
			state.outerMissingCuboids.addAll(sublists[i]);
		}
		
		// Update this location.
		state.lastComputedAddress = currentCuboid;
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
		for (CreatureEntity creature : _completedCreatures.values())
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
			List<ScheduledMutation> pendingMutations = _scheduledBlockMutations.get(address);
			if (null == pendingMutations)
			{
				pendingMutations = List.of();
			}
			Map<BlockAddress, Long> periodicMutationMillis = _periodicBlockMutations.get(address);
			if (null == periodicMutationMillis)
			{
				periodicMutationMillis = Map.of();
			}
			cuboidResources.add(new PackagedCuboid(cuboid, entities, pendingMutations, periodicMutationMillis));
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

	private void _writeRemainingToDisk(Set<CuboidAddress> orphanedCuboids, Collection<Integer> removedClients)
	{
		Map<CuboidAddress, IReadOnlyCuboidData> remainingCuboids = new HashMap<>(_completedCuboids);
		remainingCuboids.keySet().removeAll(orphanedCuboids);
		Map<Integer, Entity> remainingEntities = new HashMap<>(_completedEntities);
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
		boolean network_isNetworkWriteReady(int clientId);
		void network_sendFullEntity(int clientId, Entity entity);
		void network_sendPartialEntity(int clientId, PartialEntity entity);
		void network_removeEntity(int clientId, int entityId);
		void network_sendCuboid(int clientId, IReadOnlyCuboidData cuboid);
		void network_removeCuboid(int clientId, CuboidAddress address);
		void network_sendEntityUpdate(int clientId, int entityId, IEntityUpdate update);
		void network_sendPartialEntityUpdate(int clientId, int entityId, IPartialEntityUpdate update);
		void network_sendBlockUpdate(int clientId, MutationBlockSetBlock update);
		void network_sendBlockEvent(int clientId, EventRecord.Type type, AbsoluteLocation location, int entitySource);
		void network_sendEntityEvent(int clientId, EventRecord.Type type, EventRecord.Cause cause, AbsoluteLocation optionalLocation, int entityTarget, int entitySource);
		void network_sendEndOfTick(int clientId, long tickNumber, long latestLocalCommitIncluded);
		void network_sendConfig(int clientId, WorldConfig config);
		void network_sendClientJoined(int clientId, int joinedClientId, String name);
		void network_sendClientLeft(int clientId, int leftClientId);
		void network_sendChatMessage(int clientId, int senderId, String message);
		
		// TickRunner.
		boolean runner_enqueueEntityChange(int entityId, EntityChangeTopLevelMovement<IMutablePlayerEntity> change, long commitLevel);
		
		// Misc.
		void handleClientUpdateOptions(int clientId, int clientViewDistance);
	}

	public static record TickChanges(Collection<SuspendedCuboid<IReadOnlyCuboidData>> newCuboids
			, Collection<CuboidAddress> cuboidsToUnload
			, Collection<SuspendedEntity> newEntities
			, Collection<Integer> entitiesToUnload
	) {}

	private static final class ClientState
	{
		public final String name;
		public EntityLocation location;
		
		// The data we think that this client already has.  These are used for determining what they should be told to load/drop as well as filtering updates to what they can apply.
		public int cuboidViewDistance;
		public CuboidAddress lastComputedAddress;
		public final Set<Integer> knownEntities;
		public final Set<CuboidAddress> knownCuboids;
		public final List<CuboidAddress> priorityMissingCuboids;
		public final List<CuboidAddress> outerMissingCuboids;
		
		public ClientState(String name, EntityLocation initialLocation)
		{
			this.name = name;
			this.location = initialLocation;
			
			// Create empty containers for what this client has observed.
			this.cuboidViewDistance = MiscConstants.DEFAULT_CUBOID_VIEW_DISTANCE;
			this.lastComputedAddress = null;
			this.knownEntities = new HashSet<>();
			this.knownCuboids = new HashSet<>();
			this.priorityMissingCuboids = new ArrayList<>();
			this.outerMissingCuboids = new ArrayList<>();
		}
	}
}
