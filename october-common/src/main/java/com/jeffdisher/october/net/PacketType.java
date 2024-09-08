package com.jeffdisher.october.net;


public enum PacketType
{
	ERROR,
	
	/**
	 * Sent from the client to the server as the first message in the handshake.
	 */
	CLIENT_SEND_DESCRIPTION,
	/**
	 * Send from the server to the client in response to CLIENT_SENT_DESCRIPTION to complete handshake (or disconnects).
	 */
	SERVER_SEND_CLIENT_ID,
	
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
	/**
	 * A complete entity in the system.  This includes all details such as volume and inventory, as well.  This is
	 * currently only used to tell the client about themselves.
	 */
	ENTITY,
	/**
	 * A partial entity in the system.  This is currently used for all the other entities on the server, other than this
	 * client.
	 */
	PARTIAL_ENTITY,
	/**
	 * A mutation targeting an entity when sent from the client to the server.  Note that the server will always
	 * interpret these mutations as targeting the client's own entity.
	 */
	MUTATION_ENTITY_FROM_CLIENT,
	/**
	 * An entity update sent from the server to a client.  This will include an entity ID which will identify which
	 * entity is being updated.
	 */
	ENTITY_UPDATE_FROM_SERVER,
	/**
	 * A partial entity update sent from the server to a client.  This will include an entity ID which will identify
	 * which entity is being updated.
	 */
	PARTIAL_ENTITY_UPDATE_FROM_SERVER,
	/**
	 * The packet sent by the server at the end of each logic game tick.  It includes the tick number but also the last
	 * incorporated commit number from the target client.
	 */
	END_OF_TICK,
	/**
	 * Contains just the ID of an entity which should be removed.
	 */
	REMOVE_ENTITY,
	/**
	 * Contains just the address of a cuboid which should be removed.
	 */
	REMOVE_CUBOID,
	/**
	 * Contains an IBlockStateUpdate object.  These are only send from server to client.
	 */
	BLOCK_STATE_UPDATE,
	/**
	 * Sent by the server to describe basic config variables to the client.  This happens early-on to send the current
	 * config but also later if the config changes.
	 */
	SERVER_SEND_CONFIG_UPDATE,
	/**
	 * Sent by the server to notify all clients when a new client has joined.
	 */
	CLIENT_JOINED,
	/**
	 * Sent by the server to notify all clients when a client has left.
	 */
	CLIENT_LEFT,
	
	END_OF_LIST,
}
