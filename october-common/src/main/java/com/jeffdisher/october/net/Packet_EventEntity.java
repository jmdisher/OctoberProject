package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.EventRecord;


public class Packet_EventEntity extends PacketFromServer
{
	public static final PacketType TYPE = PacketType.EVENT_ENTITY;

	public static void register(Function<ByteBuffer, Packet>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (ByteBuffer buffer) -> {
			byte enumByte = buffer.get();
			EventRecord.Type eventType = EventRecord.Type.values()[enumByte];
			enumByte = buffer.get();
			EventRecord.Cause cause = EventRecord.Cause.values()[enumByte];
			boolean hasLocation = CodecHelpers.readBoolean(buffer);
			AbsoluteLocation optionalLocation = hasLocation
					? CodecHelpers.readAbsoluteLocation(buffer)
					: null
			;
			int entityTargetId = buffer.getInt();
			int entitySourceId = buffer.getInt();
			return new Packet_EventEntity(eventType, cause, optionalLocation, entityTargetId, entitySourceId);
		};
	}


	public final EventRecord.Type eventType;
	public final EventRecord.Cause cause;
	public final AbsoluteLocation optionalLocation;
	public final int entityTargetId;
	public final int entitySourceId;

	public Packet_EventEntity(EventRecord.Type eventType, EventRecord.Cause cause, AbsoluteLocation optionalLocation, int entityTargetId, int entitySourceId)
	{
		super(TYPE);
		this.eventType = eventType;
		this.cause = cause;
		this.optionalLocation = optionalLocation;
		this.entityTargetId = entityTargetId;
		this.entitySourceId = entitySourceId;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		byte enumByte = (byte)this.eventType.ordinal();
		buffer.put(enumByte);
		enumByte = (byte)this.cause.ordinal();
		buffer.put(enumByte);
		boolean hasLocation = (null != this.optionalLocation);
		CodecHelpers.writeBoolean(buffer, hasLocation);
		if (hasLocation)
		{
			CodecHelpers.writeAbsoluteLocation(buffer, this.optionalLocation);
		}
		buffer.putInt(this.entityTargetId);
		buffer.putInt(this.entitySourceId);
	}
}
