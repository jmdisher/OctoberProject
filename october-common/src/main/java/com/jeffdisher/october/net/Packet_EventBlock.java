package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.EventRecord;


public class Packet_EventBlock extends PacketFromServer
{
	public static final PacketType TYPE = PacketType.EVENT_BLOCK;

	public static void register(Function<ByteBuffer, Packet>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (ByteBuffer buffer) -> {
			byte enumByte = buffer.get();
			EventRecord.Type eventType = EventRecord.Type.values()[enumByte];
			AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
			int entitySourceId = buffer.getInt();
			return new Packet_EventBlock(eventType, location, entitySourceId);
		};
	}


	public final EventRecord.Type eventType;
	public final AbsoluteLocation location;
	public final int entitySourceId;

	public Packet_EventBlock(EventRecord.Type eventType, AbsoluteLocation location, int entitySourceId)
	{
		super(TYPE);
		this.eventType = eventType;
		this.location = location;
		this.entitySourceId = entitySourceId;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		byte enumByte = (byte)this.eventType.ordinal();
		buffer.put(enumByte);
		CodecHelpers.writeAbsoluteLocation(buffer, this.location);
		buffer.putInt(this.entitySourceId);
	}
}
