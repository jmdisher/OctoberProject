package com.jeffdisher.october.client;

import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.mutations.IEntityUpdate;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.mutations.IPartialEntityUpdate;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.PartialEntity;


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
	void sendChange(IMutationEntity<IMutablePlayerEntity> change, long commitLevel);
	/**
	 * Sends the message to the given client ID via the server.
	 * 
	 * @param targetClientId The ID of the target client (0 for "everyone").
	 * @param message The message.
	 */
	void sendChatMessage(int targetClientId, String message);


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
		 */
		void adapterConnected(int assignedId, long millisPerTick);
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
		void receivedEntityUpdate(int entityId, IEntityUpdate update);
		/**
		 * Called when an incremental partial entity update is received from the server.  Note that these are to update
		 * the state of existing PartialEntity objects on the client.
		 * 
		 * @param entityId The entity to which the change should be applied.
		 * @param update The entity update.
		 */
		void receivedPartialEntityUpdate(int entityId, IPartialEntityUpdate update);
		/**
		 * Called when a block state update is received from the server.
		 * 
		 * @param stateUpdate The update.
		 */
		void receivedBlockUpdate(MutationBlockSetBlock stateUpdate);
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
