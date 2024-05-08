package com.jeffdisher.october.server;

import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.mutations.IEntityUpdate;
import com.jeffdisher.october.mutations.IPartialEntityUpdate;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
import com.jeffdisher.october.net.Packet_MutationEntityFromClient;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.PartialEntity;


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
	Packet_MutationEntityFromClient peekOrRemoveNextMutationFromClient(int clientId, Packet_MutationEntityFromClient toRemove);
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
	void sendEntityUpdate(int clientId, int entityId, IEntityUpdate update);
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
	 * Sends the end of tick message to the given client.  All the messages which the client received since the previous
	 * end of tick are considered part of this tick.
	 * 
	 * @param clientId The ID of the client (as assigned by the adapter implementation).
	 * @param tickNumber The number of the tick completed (always > 1L).
	 * @param latestLocalCommitIncluded The latest local commit from the client which is included in this tick.
	 */
	void sendEndOfTick(int clientId, long tickNumber, long latestLocalCommitIncluded);
	/**
	 * Disconnects the given client.  Note that the implementation may still send messages from them, after this call,
	 * but will seek to disconnect them.
	 * 
	 * @param clientId The ID of the client (as assigned by the adapter implementation).
	 */
	void disconnectClient(int clientId);


	/**
	 * The interface of the calls coming back _from_ the adapter implementation into the caller.
	 */
	public static interface IListener
	{
		/**
		 * Called when a client connects.
		 * 
		 * @param clientId The ID of the client (as assigned by the adapter implementation).
		 */
		void clientConnected(int clientId);
		/**
		 * Called when a client disconnects.
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
