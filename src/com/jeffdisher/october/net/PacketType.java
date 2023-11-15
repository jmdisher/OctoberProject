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
	
	END_OF_LIST,
}
