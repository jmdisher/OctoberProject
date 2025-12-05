package com.jeffdisher.october.client;

import com.jeffdisher.october.actions.EntityActionSimpleMove;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
import com.jeffdisher.october.net.EntityUpdatePerField;
import com.jeffdisher.october.net.PartialEntityUpdate;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.types.PartialPassive;


/**
 * The interface used by the ClientRunner to interact with the network.  This allows for a higher-level abstraction over
 * network IO scheduling and is an interface so that testing implementations can be more easily injected into the
 * client.
 * The caller assumes that all of these calls are non-blocking, being executed by some background mechanism,
 * asynchronously.
 */
public interface IClientAdapter
{
	/**
	 * Instructs the adapter to begin a connection attempt to the server, listening for changes via the given listener.
	 * 
	 * @param listener The listener which will receive network messages.
	 */
	void connectAndStartListening(IListener listener);
	/**
	 * Disconnects the receiver.  Note that the implementation may still send messages from the network, after this
	 * call, but will initiate the disconnect.
	 */
	void disconnect();
	/**
	 * Sends the given change to the server.
	 * 
	 * @param change The change to send.
	 * @param commitLevel The client's local commit level represented by this change.
	 */
	void sendChange(EntityActionSimpleMove<IMutablePlayerEntity> change, long commitLevel);
	/**
	 * Sends the message to the given client ID via the server.
	 * 
	 * @param targetClientId The ID of the target client (0 for "everyone").
	 * @param message The message.
	 */
	void sendChatMessage(int targetClientId, String message);
	/**
	 * Sends a message to the server to update this client's options.
	 * 
	 * @param clientViewDistance The view distance around this client, in cuboids.
	 */
	void updateOptions(int clientViewDistance);


	/**
	 * The interface of the calls coming back _from_ the adapter implementation into the caller.
	 */
	public static interface IListener
	{
		/**
		 * Called when the connection to the server has been established and the client has been given an ID.
		 * 
		 * @param assignedId The client ID assigned by the server.
		 * @param millisPerTick The server's tick rate.
		 * @param currentViewDistance The starting view distance for this client.
		 * @param viewDistanceMaximum The maximum view distance a client can request (as a new client defaults to "1").
		 */
		void adapterConnected(int assignedId, long millisPerTick, int currentViewDistance, int viewDistanceMaximum);
		/**
		 * Called when the connection to the server has closed or failed to establish, in the first place.
		 */
		void adapterDisconnected();
		
		/**
		 * Called when a full entity is received from the server.
		 * 
		 * @param entity The entity.
		 */
		void receivedFullEntity(Entity entity);
		/**
		 * Called when a partial entity is received from the server.
		 * 
		 * @param entity The entity.
		 */
		void receivedPartialEntity(PartialEntity entity);
		/**
		 * Called when an entity should be removed.
		 * 
		 * @param entityId The ID of the entity to remove.
		 */
		void removeEntity(int entityId);
		/**
		 * Called when a passive entity is loaded for the first time.
		 * 
		 * @param partial The server's passive data.
		 */
		void receivedPassive(PartialPassive partial);
		/**
		 * Called when a previously-loaded passive's state changes.
		 * 
		 * @param entityId The ID of the passive to change.
		 * @param location The new location to set.
		 * @param velocity The new velocity to set.
		 */
		void receivedPassiveUpdate(int entityId, EntityLocation location, EntityLocation velocity);
		/**
		 * Called when a passive should be unloaded as the server is no longer sending us updates.
		 * 
		 * @param entityId The ID of the passive to unload.
		 */
		void removePassive(int entityId);
		/**
		 * Called when a full cuboid is received from the server.
		 * 
		 * @param cuboid The cuboid.
		 */
		void receivedCuboid(IReadOnlyCuboidData cuboid);
		/**
		 * Called when a cuboid should be removed.
		 * 
		 * @param address The address of the cuboid to remove.
		 */
		void removeCuboid(CuboidAddress address);
		
		/**
		 * Called when an incremental entity update is received from the server.  Note that these are to update the
		 * state of existing Entity objects on the client.
		 * 
		 * @param entityId The entity to which the change should be applied.
		 * @param update The entity update.
		 */
		void receivedEntityUpdate(int entityId, EntityUpdatePerField update);
		/**
		 * Called when an incremental partial entity update is received from the server.  Note that these are to update
		 * the state of existing PartialEntity objects on the client.
		 * 
		 * @param entityId The entity to which the change should be applied.
		 * @param update The entity update.
		 */
		void receivedPartialEntityUpdate(int entityId, PartialEntityUpdate update);
		/**
		 * Called when a block state update is received from the server.
		 * 
		 * @param stateUpdate The update.
		 */
		void receivedBlockUpdate(MutationBlockSetBlock stateUpdate);
		/**
		 * Called when we receive a block event from the server.
		 * 
		 * @param type The event type (must be a block event type).
		 * @param location The location where the event occurred.
		 * @param entitySourceId The ID of the entity who was the source of the event (may be 0).
		 */
		void receivedBlockEvent(EventRecord.Type type, AbsoluteLocation location, int entitySourceId);
		/**
		 * Called when we receive an entity event from the server.
		 * 
		 * @param type The event type (must be an entity event type).
		 * @param cause The event cause.
		 * @param optionalLocation The location where the event happened or null if not visible.
		 * @param entityTargetId The ID of the entity who was the target of the event (must be >0).
		 * @param entitySourceId The ID of the entity who was the source of the event (may be 0).
		 */
		void receivedEntityEvent(EventRecord.Type type, EventRecord.Cause cause, AbsoluteLocation optionalLocation, int entityTargetId, int entitySourceId);
		/**
		 * Called when the server sends us the end of tick message.  Any messages received since the previous end of
		 * tick are considered part of this tick.
		 * 
		 * @param tickNumber The tick number (always > 0L).
		 * @param latestLocalCommitIncluded The most recent change commit from this client included in the tick.
		 */
		void receivedEndOfTick(long tickNumber, long latestLocalCommitIncluded);
		/**
		 * Called when the server sends us a config update message.  Note that this is typically the first message
		 * received immediately after connection but can arrive at any time.
		 * 
		 * @param ticksPerDay The number of ticks in a full day cycle.
		 * @param dayStartTick The tick offset into ticksPerDay where the day "starts".
		 */
		void receivedConfigUpdate(int ticksPerDay, int dayStartTick);
		/**
		 * Called when the server tells us another client has connected (or was connected when we joined).
		 * 
		 * @param clientId The ID of the other client.
		 * @param name The name of the other client.
		 */
		void receivedOtherClientJoined(int clientId, String name);
		/**
		 * Called when the server tells us another client has disconnected.
		 * 
		 * @param clientId The ID of the other client.
		 */
		void receivedOtherClientLeft(int clientId);
		/**
		 * Called when we receive a chat message from another client.
		 * 
		 * @param senderId The ID of the client which sent the message (0 means "server console").
		 * @param message The message.
		 */
		void receivedChatMessage(int senderId, String message);
	}
}
