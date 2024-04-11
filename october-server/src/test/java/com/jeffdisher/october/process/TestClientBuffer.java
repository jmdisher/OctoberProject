package com.jeffdisher.october.process;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.net.NetworkLayer;
import com.jeffdisher.october.net.Packet;
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
		Packet newPacket = buffer.peekOrRemoveNextPacket(null, () -> List.of(new _Packet()));
		Assert.assertNotNull(newPacket);
	}

	@Test
	public void becomeWriteable() throws IOException
	{
		// Buffer something and show that we try to send it when the buffer becomes writeable.
		ClientBuffer buffer = new ClientBuffer(new _Token(), 1);
		Packet out = buffer.removeOutgoingPacketForWriteableClient();
		Assert.assertNull(out);
		boolean shouldSend = buffer.shouldImmediatelySendPacket(new _Packet());
		Assert.assertTrue(shouldSend);
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

	private static class _Packet extends Packet
	{
		public _Packet()
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
