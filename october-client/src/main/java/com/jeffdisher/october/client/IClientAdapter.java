package com.jeffdisher.october.client;

import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.mutations.IBlockStateUpdate;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;


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
	void sendChange(IMutationEntity change, long commitLevel);


	/**
	 * The interface of the calls coming back _from_ the adapter implementation into the caller.
	 */
	public static interface IListener
	{
		/**
		 * Called when the connection to the server has been established and the client has been given an ID.
		 * 
		 * @param assignedId The client ID assigned by the server.
		 */
		void adapterConnected(int assignedId);
		/**
		 * Called when the connection to the server has closed or failed to establish, in the first place.
		 */
		void adapterDisconnected();
		
		/**
		 * Called when a full entity is received from the server.
		 * 
		 * @param entity The entity.
		 */
		void receivedEntity(Entity entity);
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
		 * Called when an incremental change is received from the server.  Note that the server only sends us changes
		 * which it applied successfully so this can't fail to apply.
		 * 
		 * @param entityId The entity to which the change should be applied.
		 * @param change The change.
		 */
		void receivedChange(int entityId, IMutationEntity change);
		/**
		 * Called when a block state update is received from the server.
		 * 
		 * @param stateUpdate The update.
		 */
		void receivedBlockUpdate(IBlockStateUpdate stateUpdate);
		/**
		 * Called when the server sends us the end of tick message.  Any messages received since the previous end of
		 * tick are considered part of this tick.
		 * 
		 * @param tickNumber The tick number (always > 0L).
		 * @param latestLocalCommitIncluded The most recent change commit from this client included in the tick.
		 */
		void receivedEndOfTick(long tickNumber, long latestLocalCommitIncluded);
	}
}
