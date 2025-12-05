package com.jeffdisher.october.server;

import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.mutations.IPartialEntityUpdate;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
import com.jeffdisher.october.net.MutationEntitySetEntity;
import com.jeffdisher.october.net.NetworkLayer;
import com.jeffdisher.october.net.PacketFromClient;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.types.PartialPassive;
import com.jeffdisher.october.types.WorldConfig;


/**
 * The interface used by the ServerRunner to interact with the network.  This allows for a higher-level abstraction over
 * network IO scheduling and is an interface so that testing implementations can be more easily injected into the
 * server.
 * The caller assumes that all of these calls are non-blocking, being executed by some background mechanism,
 * asynchronously.
 */
public interface IServerAdapter
{
	/**
	 * Attaches a server to the adapter, listening for client connections and changes via the given listener.
	 * 
	 * @param listener The listener which will receive the client changes.
	 */
	void readyAndStartListening(IListener listener);
	/**
	 * Used to check if the outgoing buffer is empty and the network is ready to write.
	 * 
	 * @param clientId The client.
	 * @return True if the network can send data to this client immediately.
	 */
	boolean isNetworkWriteReady(int clientId);
	/**
	 * Called after receiving clientReadReady in order to fetch the actual packets from the network layer.  If toRemove
	 * is not null, the next packet will be removed and discarded (after being checked that it instance-matches).
	 * Returns the next packet but doesn't discard it (meaning successive calls with null toRemove will return the
	 * same instance).
	 * If this returns null, due to being empty, the clientReadReady callback will arrive when more data is available.
	 * 
	 * @param clientId The client.
	 * @param toRemove If non-null, removes the next packet, checks that it matches, and discards it.
	 * @return The next packet in the list (null if empty).
	 */
	PacketFromClient peekOrRemoveNextPacketFromClient(int clientId, PacketFromClient toRemove);
	/**
	 * Sends a full entity to a given client.
	 * 
	 * @param clientId The ID of the client (as assigned by the adapter implementation).
	 * @param entity The entity to send.
	 */
	void sendFullEntity(int clientId, Entity entity);
	/**
	 * Sends a partial entity to a given client.
	 * 
	 * @param clientId The ID of the client (as assigned by the adapter implementation).
	 * @param entity The entity to send.
	 */
	void sendPartialEntity(int clientId, PartialEntity entity);
	/**
	 * Tells the given client that an entity should be discarded.
	 * 
	 * @param clientId The ID of the client (as assigned by the adapter implementation).
	 * @param entityId The ID of the entity to remove.
	 */
	void removeEntity(int clientId, int entityId);
	/**
	 * Sends a new passive to the client.
	 * 
	 * @param clientId The ID of the client (as assigned by the adapter implementation).
	 * @param partial The server's passive data.
	 */
	void sendPartialPassive(int clientId, PartialPassive partial);
	/**
	 * Tells the client to update the state of a passive.
	 * 
	 * @param clientId The ID of the client (as assigned by the adapter implementation).
	 * @param entityId The ID of the passive to change.
	 * @param location The new location to set.
	 * @param velocity The new velocity to set.
	 */
	void sendPartialPassiveUpdate(int clientId, int entityId, EntityLocation location, EntityLocation velocity);
	/**
	 * Tells the client that this passive should be unloaded
	 * 
	 * @param clientId The ID of the client (as assigned by the adapter implementation).
	 * @param entityId The ID of the passive to unload.
	 */
	void removePassive(int clientId, int entityId);
	/**
	 * Sends a full cuboid to the given client.
	 * 
	 * @param clientId The ID of the client (as assigned by the adapter implementation).
	 * @param cuboid The cuboid to send.
	 */
	void sendCuboid(int clientId, IReadOnlyCuboidData cuboid);
	/**
	 * Tells the given client that a cuboid should be discarded.
	 * 
	 * @param clientId The ID of the client (as assigned by the adapter implementation).
	 * @param address The address of the cuboid to remove.
	 */
	void removeCuboid(int clientId, CuboidAddress address);
	/**
	 * Sends an incremental entity update to the given client.
	 * 
	 * @param clientId The ID of the client (as assigned by the adapter implementation).
	 * @param entityId The ID of the entity impacted by the change.
	 * @param update The update to send.
	 */
	void sendEntityUpdate(int clientId, int entityId, MutationEntitySetEntity update);
	/**
	 * Sends an incremental partial entity update to the given client.
	 * 
	 * @param clientId The ID of the client (as assigned by the adapter implementation).
	 * @param entityId The ID of the entity impacted by the change.
	 * @param update The update to send.
	 */
	void sendPartialEntityUpdate(int clientId, int entityId, IPartialEntityUpdate update);
	/**
	 * Sends block state update to the given client.
	 * 
	 * @param clientId The ID of the client (as assigned by the adapter implementation).
	 * @param update The state update to send.
	 */
	void sendBlockUpdate(int clientId, MutationBlockSetBlock update);
	/**
	 * Delivers an event to clientId describing the block event at location.
	 * 
	 * @param clientId The ID of the client to receive the message (must be >0).
	 * @param type The event type (must be a block event type).
	 * @param location The location where the event occurred.
	 * @param entitySourceId The ID of the entity who was the source of the event (may be 0).
	 */
	void sendBlockEvent(int clientId, EventRecord.Type type, AbsoluteLocation location, int entitySourceId);
	/**
	 * Delivers an event to clientId describing the entity event with an optional location (can be null).
	 * 
	 * @param clientId The ID of the client to receive the message (must be >0).
	 * @param type The event type (must be an entity event type).
	 * @param cause The event cause.
	 * @param optionalLocation The location where the event happened or null if not visible.
	 * @param entityTargetId The ID of the entity who was the target of the event (must be >0).
	 * @param entitySourceId The ID of the entity who was the source of the event (may be 0).
	 */
	void sendEntityEvent(int clientId, EventRecord.Type type, EventRecord.Cause cause, AbsoluteLocation optionalLocation, int entityTargetId, int entitySourceId);
	/**
	 * Sends the end of tick message to the given client.  All the messages which the client received since the previous
	 * end of tick are considered part of this tick.
	 * 
	 * @param clientId The ID of the client (as assigned by the adapter implementation).
	 * @param tickNumber The number of the tick completed (always > 1L).
	 * @param latestLocalCommitIncluded The latest local commit from the client which is included in this tick.
	 */
	void sendEndOfTick(int clientId, long tickNumber, long latestLocalCommitIncluded);
	/**
	 * Sends a config update packet to the given clientId, derived from the given config.
	 * 
	 * @param clientId The ID of the client (as assigned by the adapter implementation).
	 * @param config The config object (although this is a shared instance the receiver likely has).
	 */
	void sendConfig(int clientId, WorldConfig config);
	/**
	 * Notifies the given clientId that a new client has joined (or was present when clientId joined).
	 * 
	 * @param clientId The ID of the client (as assigned by the adapter implementation).
	 * @param joinedClientId The ID of the new client.
	 * @param name The name of the new client.
	 */
	void sendClientJoined(int clientId, int joinedClientId, String name);
	/**
	 * Notifies the given clientId that another client has left.
	 * 
	 * @param clientId The ID of the client (as assigned by the adapter implementation).
	 * @param leftClientId The ID of the other client.
	 */
	void sendClientLeft(int clientId, int leftClientId);
	/**
	 * Delivers a message from senderId to clientId.
	 * 
	 * @param clientId The ID of the client to receive the message (must be >0).
	 * @param senderId The ID of the client which send the message (must be >=0 as 0 means "console").
	 * @param message The message to send.
	 */
	void sendChatMessage(int clientId, int senderId, String message);
	/**
	 * Disconnects the given client.  Note that the implementation may still send messages from them, after this call,
	 * but will seek to disconnect them.
	 * 
	 * @param clientId The ID of the client (as assigned by the adapter implementation).
	 */
	void disconnectClient(int clientId);
	/**
	 * MUST be called after the clientDisconnected(clientId) callback so that the network layer will know that the
	 * higher-level is done talking about this client.
	 * 
	 * @param clientId The ID of the client which was disconnected.
	 */
	void acknowledgeDisconnect(int clientId);


	/**
	 * The interface of the calls coming back _from_ the adapter implementation into the caller.
	 */
	public static interface IListener
	{
		/**
		 * Called when a client connects.
		 * 
		 * @param clientId The ID of the client (as assigned by the adapter implementation).
		 * @param token The token representing the connection to this client.
		 * @param name The client's human-readable name.
		 * @param cuboidViewDistance The initial cuboid view distance to attempt to use (may be restricted).
		 */
		void clientConnected(int clientId, NetworkLayer.PeerToken token, String name, int cuboidViewDistance);
		/**
		 * Called when a client disconnects.
		 * Note that a call receiving this MUST call acknowledgeDisconnect(clientId) once it has accounted for this.
		 * 
		 * @param clientId The ID of the client (as assigned by the adapter implementation).
		 */
		void clientDisconnected(int clientId);
		/**
		 * Called when a new changes comes in from the given client.  This means that the next call to
		 * peekOrRemoveNextMutationFromClient will return non-null.
		 * 
		 * @param clientId The ID of the client (as assigned by the adapter implementation).
		 */
		void clientReadReady(int clientId);
	}
}
