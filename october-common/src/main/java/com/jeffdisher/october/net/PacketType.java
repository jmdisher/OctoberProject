package com.jeffdisher.october.net;


public enum PacketType
{
	ERROR,
	
	/**
	 * Sent from server to client as the first message in handshake.
	 */
	ASSIGN_CLIENT_ID,
	/**
	 * Sent from client to server in response to ASSIGN_CLIENT_ID to complete handshake.
	 */
	SET_CLIENT_NAME,
	/**
	 * Sent both to/from server, but the ID is ignored when sent TO the server.
	 */
	CHAT,
	/**
	 * Sent when we will start sending cuboid data to a client.  This puts the receiver into a mode where it can start
	 * processing CUBOID_FRAGMENT messages.  The receiver knows when it has received the last fragment.
	 */
	CUBOID_START,
	/**
	 * A single fragment of data to load into a cuboid.  Depending on the complexity of the cuboid, there could be many
	 * of these or only 1.  The receiver will know, based on the content of the data, when all the data has been
	 * received.
	 */
	CUBOID_FRAGMENT,
	
	END_OF_LIST,
}
