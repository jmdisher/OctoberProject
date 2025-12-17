package com.jeffdisher.october.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.net.PacketCodec;
import com.jeffdisher.october.net.PacketFromServer;
import com.jeffdisher.october.net.PacketType;


public class TestOutpacketBuffer
{
	@Test
	public void fillAndBacklog() throws IOException
	{
		// Buffer a bunch of packets which will quickly overflow the buffer and make sure that they are copied out as expected.
		ByteBuffer inlineBuffer = ByteBuffer.allocate(256);
		OutpacketBuffer buffer = new OutpacketBuffer(inlineBuffer, 0);
		int size = 100;
		buffer.writePacket(new _OutPacket(size));
		Assert.assertEquals(inlineBuffer.capacity() - (size + PacketCodec.HEADER_BYTES), buffer.getImmediateBufferRemaining());
		buffer.writePacket(new _OutPacket(size));
		Assert.assertEquals(inlineBuffer.capacity() - 2 * (size + PacketCodec.HEADER_BYTES), buffer.getImmediateBufferRemaining());
		buffer.writePacket(new _OutPacket(size));
		Assert.assertEquals(0, buffer.getImmediateBufferRemaining());
		buffer.writePacket(new _OutPacket(size));
		Assert.assertEquals(0, buffer.getImmediateBufferRemaining());
		
		ByteBuffer toWrite = buffer.flipAndRemoveBuffer();
		Assert.assertEquals(0, toWrite.position());
		Assert.assertEquals(2 * (PacketCodec.HEADER_BYTES + size), toWrite.remaining());
		List<PacketFromServer> packets = buffer.removeOverflow();
		Assert.assertEquals(2, packets.size());
	}

	@Test
	public void bufferRemainingLimits() throws IOException
	{
		// We see the buffer space remaining so long as there is a buffer and it hasn't overflowed.
		ByteBuffer inlineBuffer = ByteBuffer.allocate(256);
		OutpacketBuffer buffer = new OutpacketBuffer(inlineBuffer, 0);
		Assert.assertEquals(inlineBuffer.capacity(), buffer.getImmediateBufferRemaining());
		
		int size = 100;
		buffer.writePacket(new _OutPacket(size));
		Assert.assertEquals(inlineBuffer.capacity() - (size + PacketCodec.HEADER_BYTES), buffer.getImmediateBufferRemaining());
		buffer.writePacket(new _OutPacket(size));
		Assert.assertEquals(inlineBuffer.capacity() - 2 * (size + PacketCodec.HEADER_BYTES), buffer.getImmediateBufferRemaining());
		buffer.writePacket(new _OutPacket(size));
		Assert.assertEquals(0, buffer.getImmediateBufferRemaining());
		
		OutpacketBuffer noSpaceBuffer = new OutpacketBuffer(null, 0);
		Assert.assertEquals(0, noSpaceBuffer.getImmediateBufferRemaining());
	}


	private static class _OutPacket extends PacketFromServer
	{
		private final int _size;
		public _OutPacket(int size)
		{
			super(PacketType.ERROR);
			_size = size;
		}
		@Override
		public void serializeToBuffer(ByteBuffer buffer)
		{
			byte[] buff = new byte[_size];
			buffer.put(buff);
		}
	}
}
