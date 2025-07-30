package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.utils.Assert;


/**
 * Sent by a client to the server.  This contains a list of per-client options.
 */
public class Packet_ClientUpdateOptions extends PacketFromClient
{
	public static final PacketType TYPE = PacketType.CLIENT_UPDATE_OPTIONS;

	public static void register(Function<ByteBuffer, Packet>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (ByteBuffer buffer) -> {
			int clientViewDistance = buffer.getInt();
			// This cannot be negative.
			Assert.assertTrue(clientViewDistance >= 0);
			return new Packet_ClientUpdateOptions(clientViewDistance);
		};
	}


	public final int clientViewDistance;

	public Packet_ClientUpdateOptions(int clientViewDistance)
	{
		super(TYPE);
		this.clientViewDistance = clientViewDistance;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		buffer.putInt(this.clientViewDistance);
	}
}
