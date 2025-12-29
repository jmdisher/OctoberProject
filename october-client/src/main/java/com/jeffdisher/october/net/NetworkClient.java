package com.jeffdisher.october.net;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.List;

import com.jeffdisher.october.utils.Assert;


/**
 * A wrapper over NetworkLayer which adapts its interface for client-specific use-cases.
 * Note that all calls issued on the IListener interface are run on the internal network thread and must return quickly.
 */
public class NetworkClient
{
	private final NetworkLayer<PacketFromServer> _network;
	private final _NetworkWrapper _wrapper;

	/**
	 * Creates a new client, returning once the connection has been established, but before the handshake is complete.
	 * 
	 * @param listener The listener which will receive callbacks related to the client (on the network thread).
	 * @param host The remote host to contact.
	 * @param port The port to use when connecting to the remote host.
	 * @param clientName The name to send to the server to identify ourselves.
	 * @param cuboidViewDistance The client's preferred view distance.
	 * @throws IOException An error occurred while configuring the network.
	 */
	public NetworkClient(IListener listener, InetAddress host, int port, String clientName, int cuboidViewDistance) throws IOException
	{
		_NetworkLayerListener internalListener = new _NetworkLayerListener(listener);
		_network = NetworkLayer.connectToServer(internalListener
			, host
			, port
		);
		
		_wrapper = internalListener.buildWrapper(_network);
		
		// The connection starts writable so kick-off the handshake.
		_wrapper.sendHandshake(clientName, cuboidViewDistance);
	}

	/**
	 * Requests that the network layer shut down and release all resources.
	 */
	public void stop()
	{
		_network.stop();
	}

	/**
	 * Sends a message to the server..
	 * 
	 * @param packet The message to send.
	 */
	public void sendMessage(PacketFromClient packet)
	{
		_wrapper.sendMessage(packet);
	}

	/**
	 * This is only exposed for tests to verify connection sequence behaviour.
	 * This will wait for the handshake to complete.
	 * 
	 * @return The ID assigned to this client when it completed its handshake with the server.
	 * @throws InterruptedException The thread was interrupted while waiting.
	 */
	public int getClientId() throws InterruptedException
	{
		return _wrapper._token.getClientId();
	}


	/**
	 * The interface for listening to events from inside the client.  Note that all calls will be issued on the internal
	 * thread so they must return soon in order to avoid slowing the system.
	 */
	public static interface IListener
	{
		/**
		 * Called when the handshake with the server is complete.  Note that the network isn't yet ready for write.
		 * 
		 * @param assignedId The ID the server assigned to this client's entity.
		 * @param millisPerTick The server's tick rate.
		 * @param currentViewDistance The starting view distance for this client.
		 * @param viewDistanceMaximum The maximum view distance a client can request (as a new client defaults to "1").
		 */
		void handshakeCompleted(int assignedId, long millisPerTick, int currentViewDistance, int viewDistanceMaximum);
		/**
		 * Called when the network is free to send more messages to the server.  Note that this is first called once the
		 * handshake with the server is complete.
		 */
		void networkWriteReady();
		/**
		 * Called when a new message has arrived from the server.
		 * 
		 * @param packet The new message received.
		 */
		void packetReceived(Packet packet);
		/**
		 * Called when the server has disconnected the client or a network error broke the connection.
		 */
		void serverDisconnected();
	}

	private static class _State
	{
		public final NetworkLayer.IPeerToken token;
		
		private boolean _didFinishHandshake;
		private int _clientId;
		
		public _State(NetworkLayer.IPeerToken token)
		{
			this.token = token;
		}
		public synchronized void setHandshakeCompleted(int clientId)
		{
			Assert.assertTrue(!_didFinishHandshake);
			_didFinishHandshake = true;
			_clientId = clientId;
			this.notifyAll();
		}
		public boolean didFinishHandshake()
		{
			return _didFinishHandshake;
		}
		public synchronized int getClientId() throws InterruptedException
		{
			while (!_didFinishHandshake)
			{
				this.wait();
			}
			return _clientId;
		}
	}

	private static class _NetworkLayerListener implements NetworkLayer.IListener
	{
		private final IListener _listener;
		private _State _token;
		private ByteBuffer _writeableBuffer;
		private _NetworkWrapper _wrapper;
		public _NetworkLayerListener(IListener listener)
		{
			_listener = listener;
		}
		public _NetworkWrapper buildWrapper(NetworkLayer<PacketFromServer> network)
		{
			// We expect the connection to be up and writeable.
			Assert.assertTrue(null != _token);
			Assert.assertTrue(null != _writeableBuffer);
			Assert.assertTrue(null == _wrapper);
			_wrapper = new _NetworkWrapper(network, _listener, _token, _writeableBuffer);
			_token = null;
			_writeableBuffer = null;
			return _wrapper;
		}
		@Override
		public void peerConnected(NetworkLayer.IPeerToken token, ByteBuffer byteBuffer)
		{
			// Since this is client mode, this is called before the connectToServer returns.
			Assert.assertTrue(null == _token);
			_token = new _State(token);
			Assert.assertTrue(null == _writeableBuffer);
			_writeableBuffer = byteBuffer;
		}
		@Override
		public void peerDisconnected(NetworkLayer.IPeerToken token)
		{
			_listener.serverDisconnected();
		}
		@Override
		public void peerReadyForWrite(NetworkLayer.IPeerToken token, ByteBuffer byteBuffer)
		{
			_wrapper.writeReady(byteBuffer);
		}
		@Override
		public void peerReadyForRead(NetworkLayer.IPeerToken token)
		{
			_wrapper.receiveMessages();
		}
	}

	private static class _NetworkWrapper
	{
		private final NetworkLayer<PacketFromServer> _network;
		private final IListener _listener;
		private final _State _token;
		private ByteBuffer _writeableBuffer;
		
		public _NetworkWrapper(NetworkLayer<PacketFromServer> network, IListener listener, _State token, ByteBuffer writeableBuffer)
		{
			_network = network;
			_listener = listener;
			_token = token;
			_writeableBuffer = writeableBuffer;
		}
		public void writeReady(ByteBuffer byteBuffer)
		{
			Assert.assertTrue(null == _writeableBuffer);
			if (_token.didFinishHandshake())
			{
				_writeableBuffer = byteBuffer;
				// Just pass this back since we are done with it.
				_listener.networkWriteReady();
			}
			else
			{
				// We already started the handshake so record the network is ready while we wait for it to finish.
				_writeableBuffer = byteBuffer;
			}
		}
		public void sendHandshake(String clientName, int cuboidViewDistance)
		{
			PacketCodec.serializeToBuffer(_writeableBuffer, new Packet_ClientSendDescription(Packet_ClientSendDescription.NETWORK_PROTOCOL_VERSION, clientName, cuboidViewDistance));
			_writeableBuffer.flip();
			ByteBuffer buffer = _writeableBuffer;
			_writeableBuffer = null;
			_network.sendBuffer(_token.token, buffer);
		}
		public void sendMessage(PacketFromClient packet)
		{
			// We need to wait for handshake before trying to use the client.
			Assert.assertTrue(_token.didFinishHandshake());
			Assert.assertTrue(null != _writeableBuffer);
			
			PacketCodec.serializeToBuffer(_writeableBuffer, packet);
			_writeableBuffer.flip();
			
			// NOTE:  We need to clear the write buffer BEFORE sending the message since the NEXT ready callback could come in between the lines of code.
			ByteBuffer buffer = _writeableBuffer;
			_writeableBuffer = null;
			_network.sendBuffer(_token.token, buffer);
		}
		public void receiveMessages()
		{
			List<PacketFromServer> packets = _network.receiveMessages(_token.token);
			Assert.assertTrue(!packets.isEmpty());
			for (Packet packet : packets)
			{
				if (_token.didFinishHandshake())
				{
					// Pass this on to the listener.
					_listener.packetReceived(packet);
				}
				else
				{
					// We are expected to consume this as the completion of the handshake.
					// This MUST be the ID assignment (the actual ID isn't relevant, just the message).
					Assert.assertTrue(PacketType.SERVER_SEND_CLIENT_ID == packet.type);
					Packet_ServerSendClientId safe = (Packet_ServerSendClientId) packet;
					int assignedId = safe.clientId;
					_token.setHandshakeCompleted(assignedId);
					_listener.handshakeCompleted(assignedId, safe.millisPerTick, safe.currentViewDistance, safe.viewDistanceMaximum);
					
					// See if the network is ready yet (since there was likely a race here).
					if (null != _writeableBuffer)
					{
						_listener.networkWriteReady();
					}
				}
			}
		}
	}
}
