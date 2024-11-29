package com.jeffdisher.october.net;

import java.nio.ByteBuffer;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.EventRecord;


public class TestPacketCodec
{
	@Test
	public void eventEntity() throws Throwable
	{
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		AbsoluteLocation location = new AbsoluteLocation(-1, 0, 1);
		int targetId = 1;
		int sourceId = 2;
		Packet_EventEntity event = new Packet_EventEntity(EventRecord.Type.ENTITY_HURT, EventRecord.Cause.ATTACKED, location, targetId, sourceId);
		PacketCodec.serializeToBuffer(buffer, event);
		buffer.flip();
		Packet read = PacketCodec.parseAndSeekFlippedBuffer(buffer);
		Packet_EventEntity safe = (Packet_EventEntity) read;
		Assert.assertEquals(event.type, safe.type);
		Assert.assertEquals(event.cause, safe.cause);
		Assert.assertEquals(event.optionalLocation, safe.optionalLocation);
		Assert.assertEquals(event.entityTargetId, safe.entityTargetId);
		Assert.assertEquals(event.entitySourceId, safe.entitySourceId);
	}

	@Test
	public void eventEntityNull() throws Throwable
	{
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		int targetId = 1;
		int sourceId = 2;
		Packet_EventEntity event = new Packet_EventEntity(EventRecord.Type.ENTITY_KILLED, EventRecord.Cause.ATTACKED, null, targetId, sourceId);
		PacketCodec.serializeToBuffer(buffer, event);
		buffer.flip();
		Packet read = PacketCodec.parseAndSeekFlippedBuffer(buffer);
		Packet_EventEntity safe = (Packet_EventEntity) read;
		Assert.assertEquals(event.type, safe.type);
		Assert.assertEquals(event.cause, safe.cause);
		Assert.assertEquals(event.optionalLocation, safe.optionalLocation);
		Assert.assertEquals(event.entityTargetId, safe.entityTargetId);
		Assert.assertEquals(event.entitySourceId, safe.entitySourceId);
	}

	@Test
	public void eventBlock() throws Throwable
	{
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		AbsoluteLocation location = new AbsoluteLocation(-1, 0, 1);
		int sourceId = 2;
		Packet_EventBlock event = new Packet_EventBlock(EventRecord.Type.BLOCK_PLACED, location, sourceId);
		PacketCodec.serializeToBuffer(buffer, event);
		buffer.flip();
		Packet read = PacketCodec.parseAndSeekFlippedBuffer(buffer);
		Packet_EventBlock safe = (Packet_EventBlock) read;
		Assert.assertEquals(event.type, safe.type);
		Assert.assertEquals(event.location, safe.location);
		Assert.assertEquals(event.entitySourceId, safe.entitySourceId);
	}
}
