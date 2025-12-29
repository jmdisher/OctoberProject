package com.jeffdisher.october.process;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.net.NetworkLayer;
import com.jeffdisher.october.net.PacketCodec;
import com.jeffdisher.october.net.PacketFromClient;
import com.jeffdisher.october.net.PacketFromServer;
import com.jeffdisher.october.net.PacketType;
import com.jeffdisher.october.server.OutpacketBuffer;


/**
 * ClientBuffer is only NOT part of ServerProcess so that these tests of its buffering logic can exist.
 */
public class TestClientBuffer
{
	@Test
	public void readThrough() throws IOException
	{
		// Show that we read through to the source when nothing buffered.
		ClientBuffer buffer = new ClientBuffer(new _Token(), 1);
		boolean isNewlyReadable = buffer.becameReadableAfterNetworkReady();
		Assert.assertTrue(isNewlyReadable);
		PacketFromClient newPacket = buffer.peekOrRemoveNextPacket(null, () -> List.of(new _InPacket()));
		Assert.assertNotNull(newPacket);
	}

	@Test
	public void becomeWriteable() throws IOException
	{
		// Buffer something and show that we try to send it when the buffer becomes writeable.
		ClientBuffer buffer = new ClientBuffer(new _Token(), 1);
		ByteBuffer writeBuffer = ByteBuffer.allocate(256);
		ByteBuffer send = buffer.writeImmediateForWriteableClient(writeBuffer);
		Assert.assertNull(send);
		send = buffer.shouldImmediatelySendBuffer(new _OutPacket(1));
		Assert.assertNotNull(send);
	}

	@Test
	public void stallBug() throws IOException
	{
		// Verifies the fix for the network stall bug where we may return null instead of rebuffering.
		ClientBuffer buffer = new ClientBuffer(new _Token(), 1);
		// -set it is readable
		Assert.assertTrue(buffer.becameReadableAfterNetworkReady());
		// -read the packet
		PacketFromClient newPacket = buffer.peekOrRemoveNextPacket(null, () -> List.of(new _InPacket()));
		Assert.assertNotNull(newPacket);
		// -set it readable (no callout since we didn't yet consume)
		Assert.assertFalse(buffer.becameReadableAfterNetworkReady());
		// -try to remove and read
		newPacket = buffer.peekOrRemoveNextPacket(newPacket, () -> List.of(new _InPacket()));
		// -verify we see a valid packet
		Assert.assertNotNull(newPacket);
	}

	@Test
	public void becomeWriteableWithBigBacklog() throws IOException
	{
		// Buffer a bunch of packets which will quickly overflow the buffer and make sure that they are copied out as expected.
		ClientBuffer buffer = new ClientBuffer(new _Token(), 1);
		int size = 100;
		ByteBuffer send = buffer.shouldImmediatelySendBuffer(new _OutPacket(size));
		Assert.assertNull(send);
		send = buffer.shouldImmediatelySendBuffer(new _OutPacket(size));
		Assert.assertNull(send);
		send = buffer.shouldImmediatelySendBuffer(new _OutPacket(size));
		Assert.assertNull(send);
		send = buffer.shouldImmediatelySendBuffer(new _OutPacket(size));
		Assert.assertNull(send);
		
		// Now, we expect this to be written in 2 buffer iterations.
		ByteBuffer writeBuffer = ByteBuffer.allocate(256);
		send = buffer.writeImmediateForWriteableClient(writeBuffer);
		Assert.assertEquals(0, send.position());
		Assert.assertEquals(2 * (PacketCodec.HEADER_BYTES + size), send.remaining());
		writeBuffer.clear();
		send = buffer.writeImmediateForWriteableClient(writeBuffer);
		Assert.assertEquals(0, send.position());
		Assert.assertEquals(2 * (PacketCodec.HEADER_BYTES + size), send.remaining());
		writeBuffer.clear();
		send = buffer.writeImmediateForWriteableClient(writeBuffer);
		Assert.assertNull(send);
	}

	@Test
	public void useOutpacketBuffer() throws IOException
	{
		// Buffer a bunch of packets which will quickly overflow the buffer and make sure that they are copied out as expected.
		ClientBuffer buffer = new ClientBuffer(new _Token(), 1);
		ByteBuffer writeBuffer = ByteBuffer.allocate(256);
		ByteBuffer toSend = buffer.writeImmediateForWriteableClient(writeBuffer);
		Assert.assertNull(toSend);
		OutpacketBuffer outpackets = buffer.openOutpacketBuffer();
		
		int size = 100;
		outpackets.writePacket(new _OutPacket(size));
		outpackets.writePacket(new _OutPacket(size));
		outpackets.writePacket(new _OutPacket(size));
		outpackets.writePacket(new _OutPacket(size));
		
		toSend = buffer.shouldImmediatelySendAfterClosingOutpacket(outpackets);
		Assert.assertEquals(0, toSend.position());
		Assert.assertEquals(2 * (PacketCodec.HEADER_BYTES + size), toSend.remaining());
		
		writeBuffer.clear();
		toSend = buffer.writeImmediateForWriteableClient(writeBuffer);
		Assert.assertEquals(0, toSend.position());
		Assert.assertEquals(2 * (PacketCodec.HEADER_BYTES + size), toSend.remaining());
		
		toSend = buffer.writeImmediateForWriteableClient(writeBuffer);
		Assert.assertNull(toSend);
	}


	private static class _Token implements NetworkLayer.IPeerToken
	{
		private Object _userData;
		@Override
		public void setData(Object userData)
		{
			_userData = userData;
		}
		@Override
		public Object getData()
		{
			return _userData;
		}
	}

	private static class _InPacket extends PacketFromClient
	{
		public _InPacket()
		{
			super(PacketType.ERROR);
		}
		@Override
		public void serializeToBuffer(ByteBuffer buffer)
		{
			Assert.fail("Note part of the test");
		}
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
