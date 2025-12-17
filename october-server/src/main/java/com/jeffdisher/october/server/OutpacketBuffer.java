package com.jeffdisher.october.server;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import com.jeffdisher.october.net.PacketCodec;
import com.jeffdisher.october.net.PacketFromServer;
import com.jeffdisher.october.utils.Assert;


/**
 * This is used to avoid long calls through the stack from ServerStateManager to ServerRunner to ServerProcess, into
 * ClientBuffer.  Calling this on every packet, combined with the monitor protecting the map of ClientBuffer instances,
 * it makes more sense to just open a sort of "inline buffer" for writing output packets and then rationalize this with
 * ClientBuffer, at the end.
 * If the ClientBuffer was writeable, this instance will be given the output buffer so that it can inline the writes,
 * immediately.  Otherwise (or if it fills), it will just buffer them as a list to rationalize with ClientBuffer on
 * close.
 */
public class OutpacketBuffer
{
	/**
	 * The client list size is just used to verify that we didn't inject packets into the ClientBuffer while this
	 * instance was open (since that would put packets out of order).
	 */
	public final int clientListSize;
	private ByteBuffer _inlineBuffer;
	private List<PacketFromServer> _overflow;

	public OutpacketBuffer(ByteBuffer inlineBuffer, int clientListSize)
	{
		this.clientListSize = clientListSize;
		_inlineBuffer = inlineBuffer;
		_overflow = new ArrayList<>();
	}

	public void writePacket(PacketFromServer packet)
	{
		boolean didWrite = false;
		if (_overflow.isEmpty() && (null != _inlineBuffer))
		{
			int position = _inlineBuffer.position();
			try
			{
				PacketCodec.serializeToBuffer(_inlineBuffer, packet);
				didWrite = true;
			}
			catch (BufferOverflowException | IllegalArgumentException e)
			{
				// We get BufferOverflowException when we write over the end of the buffer and IllegalArgumentException
				// when we set the position beyond the bounds.
				// We can't fit this last one so reset the position and spill to overflow.
				_inlineBuffer.position(position);
			}
		}
		if (!didWrite)
		{
			// Write to overflow.
			_overflow.add(packet);
		}
	}

	public ByteBuffer flipAndRemoveBuffer()
	{
		ByteBuffer buffer = _inlineBuffer;
		if (null != _inlineBuffer)
		{
			// We expect that something was written here.
			Assert.assertTrue(_inlineBuffer.position() > 0);
			
			_inlineBuffer.flip();
			_inlineBuffer = null;
		}
		return buffer;
	}

	public List<PacketFromServer> removeOverflow()
	{
		List<PacketFromServer> overflow = _overflow;
		_overflow = null;
		return overflow;
	}

	public int getImmediateBufferRemaining()
	{
		return ((null != _inlineBuffer) && _overflow.isEmpty())
			? _inlineBuffer.remaining()
			: 0
		;
	}
}
