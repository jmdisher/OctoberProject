package com.jeffdisher.october.server;

import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.types.Entity;


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
	 * Sends a full entity to a given client.
	 * 
	 * @param clientId The ID of the client (as assigned by the adapter implementation).
	 * @param entity The entity to send.
	 */
	void sendEntity(int clientId, Entity entity);
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
	 * Sends an incremental entity change to the given client.
	 * 
	 * @param clientId The ID of the client (as assigned by the adapter implementation).
	 * @param entityId The ID of the entity impacted by the change.
	 * @param change The change to send.
	 */
	void sendChange(int clientId, int entityId, IMutationEntity change);
	/**
	 * Sends an incremental cuboid mutation to the given client.
	 * 
	 * @param clientId The ID of the client (as assigned by the adapter implementation).
	 * @param mutation The mutation to send.
	 */
	void sendMutation(int clientId, IMutationBlock mutation);
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
		 * Called when a new changes comes in from the given client.  Note that the change is assumed to apply to the
		 * entity associated with the client.
		 * 
		 * @param clientId The ID of the client (as assigned by the adapter implementation).
		 * @param change The change.
		 * @param commitLevel The client's local commit level represented by this change.
		 */
		void changeReceived(int clientId, IMutationEntity change, long commitLevel);
	}
}