package com.jeffdisher.october.process;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.function.Supplier;

import com.jeffdisher.october.net.NetworkLayer;
import com.jeffdisher.october.net.PacketFromClient;
import com.jeffdisher.october.net.PacketFromServer;
import com.jeffdisher.october.utils.Assert;


/**
 * The state and minimal logic required to manipulate the ServerProcess's per-client packet buffers.
 * Within the current design of the system, it assumes that the caller has already done any required locking to make
 * these calls safe.
 * Note that this is only NOT private to ServerProcess in order to expose the buffering logic to unit tests.
 */
public class ClientBuffer
{
	// We leave these immutable constants public.
	public final NetworkLayer.PeerToken token;
	public final int clientId;
	
	private final Queue<PacketFromServer> _outgoing;
	private boolean _isNetworkWriteable;
	private final Queue<PacketFromClient> _incoming;
	private PacketFromClient _lastPeeked;
	private boolean _isNetworkReadable;
	
	public ClientBuffer(NetworkLayer.PeerToken token, int clientId)
	{
		this.token = token;
		this.clientId = clientId;
		
		_outgoing = new LinkedList<>();
		_isNetworkWriteable = false;
		_incoming = new LinkedList<>();
		_isNetworkReadable = false;
	}

	public PacketFromServer removeOutgoingPacketForWriteableClient()
	{
		PacketFromServer immediateWrite;
		if (_outgoing.isEmpty())
		{
			// We have nothing to send but the client is writeable so set the flag.
			Assert.assertTrue(!_isNetworkWriteable);
			_isNetworkWriteable = true;
			immediateWrite = null;
		}
		else
		{
			// We have something buffered so we can directly handle this change of state without updating the flag.
			Assert.assertTrue(!_isNetworkWriteable);
			immediateWrite = _outgoing.poll();
		}
		return immediateWrite;
	}

	public boolean shouldImmediatelySendPacket(PacketFromServer packet)
	{
		boolean shouldSend;
		if (_isNetworkWriteable)
		{
			// The network is ready for this so send now and clear the flag.
			shouldSend = true;
			_isNetworkWriteable = false;
		}
		else
		{
			// The network isn't ready so buffer this instead of sending.
			shouldSend = false;
			_outgoing.add(packet);
		}
		return shouldSend;
	}

	public boolean becameReadableAfterNetworkReady()
	{
		// This can only be called if we aren't already readable.
		Assert.assertTrue(!_isNetworkReadable);
		_isNetworkReadable = true;
		
		// We need to notify the listener if there isn't already some readable content buffered here.
		return (null == _lastPeeked) && _incoming.isEmpty();
	}

	public PacketFromClient peekOrRemoveNextPacket(PacketFromClient toRemove, Supplier<List<PacketFromClient>> packetSource)
	{
		// If we have a previously peeked value, we want to check that.
		if (null != _lastPeeked)
		{
			if (null == toRemove)
			{
				// This is just a re-read so change nothing.
			}
			else
			{
				// Consume this (checking match).
				Assert.assertTrue(toRemove == _lastPeeked);
				_lastPeeked = null;
				
				// Advance the state machine.
				_advanceToNextPeek(packetSource);
			}
		}
		else
		{
			// Make sure that it is expected that there was no previous read.
			Assert.assertTrue(null == toRemove);
			// Make sure that we called this when there is something readable.
			Assert.assertTrue(_isNetworkReadable || !_incoming.isEmpty());
			// Advance the state machine.
			_advanceToNextPeek(packetSource);
			// In this case, we MUST now be pointing at something.
			Assert.assertTrue(null != _lastPeeked);
		}
		return _lastPeeked;
	}

	public boolean isNetworkWriteReady()
	{
		return _isNetworkWriteable;
	}


	private void _advanceToNextPeek(Supplier<List<PacketFromClient>> packetSource)
	{
		// See if we need to pull more from the lower level.
		if (_incoming.isEmpty() && _isNetworkReadable)
		{
			List<PacketFromClient> list = packetSource.get();
			// This can't be empty if we were told that this was readable.
			Assert.assertTrue(!list.isEmpty());
			_incoming.addAll(list);
			_isNetworkReadable = false;
		}
		
		// Now, handle this operation.
		if (!_incoming.isEmpty())
		{
			_lastPeeked = _incoming.poll();
		}
	}
}
