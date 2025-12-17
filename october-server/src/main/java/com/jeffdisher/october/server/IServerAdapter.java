package com.jeffdisher.october.server;

import com.jeffdisher.october.net.NetworkLayer;
import com.jeffdisher.october.net.PacketFromClient;
import com.jeffdisher.october.net.PacketFromServer;


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
	 * Sends a Packet to a client.  Note that this has no failure mode but a disconnect could be triggered
	 * asynchronously.  However, the caller should assume that the packet is either sent or buffered to be sent.
	 * 
	 * @param clientId The ID of the client (as assigned by the adapter implementation).
	 * @param packet The Packet instance to send.
	 */
	void sendPacket(int clientId, PacketFromServer packet);
	/**
	 * Requests a buffer for outgoing packets to be sent to clientId.  This allows writes to be serialized, inline,
	 * without going through multiple levels of the stack or requiring monitor access.  An instance will be returned,
	 * even if the client has been disconnected.
	 * Note that the returned value MUST be passed back to closeOutputBuffer().
	 * 
	 * @param clientId The ID of the client (as assigned by the adapter implementation).
	 * @return A buffer which can serialize or collect a sequence of PacketFromServer to send to the client, when
	 * closed.
	 */
	OutpacketBuffer openOutputBuffer(int clientId);
	/**
	 * Closes the given buffer, allowing it to be written back to the network for the given clientId.
	 * 
	 * @param clientId The ID of the client (as assigned by the adapter implementation).
	 * @param buffer The instance previously returned by openOutputBuffer().
	 */
	void closeOutputBuffer(int clientId, OutpacketBuffer buffer);
	/**
	 * Called when an end of tick is received, with the tick number.  This call is purely for tests as this tick isn't
	 * being sent anywhere.
	 * 
	 * @param tickNumber The tick number which just completed.
	 */
	void testingEndOfTick(long tickNumber);
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
