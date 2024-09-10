package com.jeffdisher.october.net;



/**
 * The intermediary class for packets which come from the clients.  It exists purely for type system reasons.
 */
public abstract class PacketFromClient extends Packet
{
	protected PacketFromClient(PacketType type)
	{
		super(type);
	}
}
