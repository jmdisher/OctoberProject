package com.jeffdisher.october.net;

import java.nio.ByteBuffer;


public abstract class Packet
{
	public final PacketType type;

	protected Packet(PacketType type)
	{
		this.type = type;
	}

	public abstract void serializeToBuffer(ByteBuffer buffer);
}
