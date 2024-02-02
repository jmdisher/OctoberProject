package com.jeffdisher.october.net;

import java.io.IOException;
import java.net.InetAddress;

import com.jeffdisher.october.utils.Assert;


/**
 * A wrapper over NetworkLayer which adapts its interface for client-specific use-cases.
 * Note that all calls issued on the IListener interface are run on the internal network thread and must return quickly.
 */
public class NetworkClient
{
	private final IListener _listener;
	private final NetworkLayer<_State> _network;
	private final _State _token;

	/**
	 * Creates a new client, returning once the connection has been established, but before the handshake is complete.
	 * 
	 * @param listener The listener which will receive callbacks related to the client (on the network thread).
	 * @param host The remote host to contact.
	 * @param port The port to use when connecting to the remote host.
	 * @param clientName The name to send to the server to identify ourselves.
	 * @throws IOException An error occurred while configuring the network.
	 */
	public NetworkClient(IListener listener, InetAddress host, int port, String clientName) throws IOException
	{
		_listener = listener;
		_token = new _State();
		_network = NetworkLayer.connectToServer(new NetworkLayer.IListener<_State>()
		{
			@Override
			public _State buildToken()
			{
				// We only have the one connection in client mode.
				return _token;
			}
			@Override
			public void peerConnected(_State token)
			{
				// Not called in client mode.
				throw Assert.unreachable();
			}
			@Override
			public void peerDisconnected(_State token)
			{
				// Not called in client mode.
				throw Assert.unreachable();
			}
			@Override
			public void peerReadyForWrite(_State token)
			{
				// We can pass this back, even if right after handshake, since we sent the last handshake message, meaning it is now complete.
				_listener.networkReady();
			}
			@Override
			public void packetReceived(_State token, Packet packet)
			{
				if (_token.didHandshake())
				{
					// Pass this on to the listener.
					_listener.packetReceived(packet);
				}
				else
				{
					// This MUST be the ID assignment (the actual ID isn't relevant, just the message).
					Assert.assertTrue(PacketType.ASSIGN_CLIENT_ID == packet.type);
					int assignedId = ((Packet_AssignClientId) packet).clientId;
					token.setHandshakeCompleted(assignedId);
					_listener.handshakeCompleted(assignedId);
					
					// Send our name back as the response.
					_network.sendMessage(_token, new Packet_SetClientName(clientName));
				}
			}
		}, host, port);
		
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
	public void sendMessage(Packet packet)
	{
		// We need to wait for handshake before trying to use the client.
		Assert.assertTrue(_token.didHandshake());
		// TODO: Buffer this.
		_network.sendMessage(_token, packet);
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
		return _token.getClientId();
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
		 */
		void handshakeCompleted(int assignedId);
		/**
		 * Called when the network is free to send more messages to the server.  Note that this is first called once the
		 * handshake with the server is complete.
		 */
		void networkReady();
		/**
		 * Called when a new message has arrived from the server.
		 * 
		 * @param packet The new message received.
		 */
		void packetReceived(Packet packet);
	}

	private static class _State
	{
		private boolean _didHandshake;
		private int _clientId;
		
		public synchronized void setHandshakeCompleted(int clientId)
		{
			Assert.assertTrue(!_didHandshake);
			_didHandshake = true;
			_clientId = clientId;
			this.notifyAll();
		}
		public boolean didHandshake()
		{
			return _didHandshake;
		}
		public synchronized int getClientId() throws InterruptedException
		{
			while (!_didHandshake)
			{
				this.wait();
			}
			return _clientId;
		}
	}
}
