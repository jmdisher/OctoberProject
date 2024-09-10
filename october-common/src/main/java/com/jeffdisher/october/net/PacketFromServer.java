package com.jeffdisher.october.net;


/**
 * The intermediary class for packets which come from the server.  It exists purely for type system reasons.
 */
public abstract class PacketFromServer extends Packet
{
	protected PacketFromServer(PacketType type)
	{
		super(type);
	}
}
