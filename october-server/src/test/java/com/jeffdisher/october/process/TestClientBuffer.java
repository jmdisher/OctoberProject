package com.jeffdisher.october.process;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.net.NetworkLayer;
import com.jeffdisher.october.net.Packet;
import com.jeffdisher.october.net.PacketFromClient;
import com.jeffdisher.october.net.PacketFromServer;
import com.jeffdisher.october.net.PacketType;


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
		Packet newPacket = buffer.peekOrRemoveNextPacket(null, () -> List.of(new _InPacket()));
		Assert.assertNotNull(newPacket);
	}

	@Test
	public void becomeWriteable() throws IOException
	{
		// Buffer something and show that we try to send it when the buffer becomes writeable.
		ClientBuffer buffer = new ClientBuffer(new _Token(), 1);
		Packet out = buffer.removeOutgoingPacketForWriteableClient();
		Assert.assertNull(out);
		boolean shouldSend = buffer.shouldImmediatelySendPacket(new _OutPacket());
		Assert.assertTrue(shouldSend);
	}

	@Test
	public void stallBug() throws IOException
	{
		// Verifies the fix for the network stall bug where we may return null instead of rebuffering.
		ClientBuffer buffer = new ClientBuffer(new _Token(), 1);
		// -set it is readable
		Assert.assertTrue(buffer.becameReadableAfterNetworkReady());
		// -read the packet
		Packet newPacket = buffer.peekOrRemoveNextPacket(null, () -> List.of(new _InPacket()));
		Assert.assertNotNull(newPacket);
		// -set it readable (no callout since we didn't yet consume)
		Assert.assertFalse(buffer.becameReadableAfterNetworkReady());
		// -try to remove and read
		newPacket = buffer.peekOrRemoveNextPacket(newPacket, () -> List.of(new _InPacket()));
		// -verify we see a valid packet
		Assert.assertNotNull(newPacket);
	}


	private static class _Token implements NetworkLayer.PeerToken
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
		public _OutPacket()
		{
			super(PacketType.ERROR);
		}
		@Override
		public void serializeToBuffer(ByteBuffer buffer)
		{
			Assert.fail("Note part of the test");
		}
	}
}
