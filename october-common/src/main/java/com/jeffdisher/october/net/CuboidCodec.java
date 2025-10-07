package com.jeffdisher.october.net;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.data.IReadOnlyCuboidData;


/**
 * A helper class for converting between CuboidData and Packet[].  Since this can require multiple calls, this uses
 * stateful instances.
 */
public class CuboidCodec
{
	public static class Serializer
	{
		private final IReadOnlyCuboidData _cuboid;
		private boolean _hasStarted;
		private boolean _isDone;
		private Object _state;
		
		public Serializer(IReadOnlyCuboidData input)
		{
			_cuboid = input;
		}
		
		public PacketFromServer getNextPacket()
		{
			PacketFromServer ret;
			if (_isDone)
			{
				// We are finished so just return null.
				ret = null;
			}
			else if (_hasStarted)
			{
				// We need to send fragments until there is no resumable state left.
				ByteBuffer buffer = ByteBuffer.allocate(PacketCodec.MAX_PACKET_BYTES - PacketCodec.HEADER_BYTES);
				_state = _cuboid.serializeResumable(_state, buffer);
				_isDone = (null == _state);
				buffer.flip();
				byte[] payload = new byte[buffer.remaining()];
				buffer.get(payload);
				ret = new Packet_CuboidFragment(payload);
			}
			else
			{
				// Send the initial packet and then inter the started state.
				ret = new Packet_CuboidStart(_cuboid.getCuboidAddress());
				_hasStarted = true;
			}
			return ret;
		}
	}

	public static class Deserializer
	{
		private final CuboidData _cuboid;
		private Object _state;
		
		public Deserializer(Packet_CuboidStart start)
		{
			_cuboid = CuboidData.createEmpty(start.address);
		}
		
		public CuboidData processPacket(Packet_CuboidFragment fragment)
		{
			ByteBuffer buffer = ByteBuffer.wrap(fragment.payload);
			// This is network related so we can just use no special rules.
			DeserializationContext context = new DeserializationContext(Environment.getShared()
				, buffer
				, 0L
				, false
			);
			_state = _cuboid.deserializeResumable(_state, context);
			// We return the cuboid if this is the last fragment.
			return (null == _state)
					? _cuboid
					: null
			;
		}
	}
}
