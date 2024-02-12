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
	/**
	 * A complete entity in the system.  This includes all details such as volume and inventory, as well.
	 */
	ENTITY,
	/**
	 * A mutation targeting an entity when sent from the client to the server.  Note that the server will always
	 * interpret these mutations as targeting the client's own entity.
	 */
	MUTATION_ENTITY_FROM_CLIENT,
	/**
	 * A mutation targeting an entity when sent from the server to a client.  This will include an entity ID which will
	 * identify which entity is changing.
	 */
	MUTATION_ENTITY_FROM_SERVER,
	/**
	 * A mutation targeting a block in the world.  These are only send from server to client.
	 */
	MUTATION_BLOCK,
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
	
	END_OF_LIST,
}
