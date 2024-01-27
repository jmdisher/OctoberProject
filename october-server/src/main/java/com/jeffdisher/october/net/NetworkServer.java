package com.jeffdisher.october.net;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.jeffdisher.october.utils.Assert;


/**
 * A wrapper over NetworkLayer which adapts its interface for server-specific use-cases.
 * Note that all calls issued on the IListener interface are run on the internal network thread and must return quickly.
 */
public class NetworkServer
{
	private final IListener _listener;
	private final Map<Integer, _ClientState> _channelsById;
	private int _nextClientId;
	private final NetworkLayer<_ClientState> _network;

	/**
	 * Creates a new server, returning once the port is bound.
	 * 
	 * @param listener The listener which will receive callbacks related to the server (on the network thread).
	 * @param port The port which should be bound for accepting incoming connections.
	 * @throws IOException An error occurred while configuring the network.
	 */
	public NetworkServer(IListener listener, int port) throws IOException
	{
		_listener = listener;
		_channelsById = new HashMap<>();
		_nextClientId = 1;
		
		_network = NetworkLayer.startListening(new NetworkLayer.IListener<_ClientState>()
		{
			@Override
			public _ClientState buildToken()
			{
				int clientId = _nextClientId;
				_nextClientId += 1;
				_ClientState token = new _ClientState(clientId);
				synchronized(NetworkServer.this)
				{
					_channelsById.put(clientId, token);
				}
				return token;
			}
			@Override
			public void peerConnected(_ClientState token)
			{
				// We start by immediately sending the handshake.
				Assert.assertTrue(!token.didHandshake());
				_network.sendMessage(token, new Packet_AssignClientId(token.clientId));
			}
			@Override
			public void peerDisconnected(_ClientState token)
			{
				int clientId = token.clientId;
				_ClientState channel = _channelsById.remove(clientId);
				Assert.assertTrue(null != channel);
				_listener.userLeft(clientId);
			}
			@Override
			public void peerReadyForWrite(_ClientState token)
			{
				// TODO:  Handle anything still pending to send out.
				
				// We will only pass this back if the handshake has completed, otherwise it is just the echo of the first handshake message we sent (which we want to consume, here).
				if (token.didHandshake())
				{
					_listener.networkReady(token.clientId);
				}
			}
			@Override
			public void packetReceived(_ClientState token, Packet packet)
			{
				if (token.didHandshake())
				{
					_listener.packetReceived(token.clientId, packet);
				}
				else
				{
					// This MUST be the response type.
					if (PacketType.SET_CLIENT_NAME == packet.type)
					{
						token.setHandshakeCompleted();
						_listener.userJoined(token.clientId, ((Packet_SetClientName)packet).name);
					}
					else
					{
						// This is bogus so disconnect them.
						System.err.println("Client " + token.clientId + ": Failed handshake with type: " + packet.type.name());
						_network.disconnectPeer(token);
					}
				}
			}
		}, port);
	}

	/**
	 * Requests that the network layer shut down and release all resources.
	 */
	public void stop()
	{
		_network.stop();
	}

	/**
	 * Sends a message to the client with the given ID.
	 * 
	 * @param clientId The ID of a specific attached client.
	 * @param packet The message to send.
	 */
	public void sendMessage(int clientId, Packet packet)
	{
		_ClientState client;
		synchronized(this)
		{
			client = _channelsById.get(clientId);
			Assert.assertTrue(null != client);
			// TODO:  Buffer this if needed.
		}
		_network.sendMessage(client, packet);
	}


	/**
	 * The interface for listening to events from inside the server.  Note that all calls will be issued on the internal
	 * thread so they must return soon in order to avoid slowing the system.
	 */
	public static interface IListener
	{
		/**
		 * Called when a user joins, once its handshake is complete.
		 * 
		 * @param id The ID of the new client.
		 * @param name The client's human name.
		 */
		void userJoined(int id, String name);
		/**
		 * Called when a user has disconnected.
		 * 
		 * @param id The ID of the client.
		 */
		void userLeft(int id);
		/**
		 * Called when the network is free to send more messages to this client.
		 * 
		 * @param id The ID of the client.
		 */
		void networkReady(int id);
		/**
		 * Called when a new message has arrived from this client.
		 * 
		 * @param id The ID of the client.
		 * @param packet The new message received.
		 */
		void packetReceived(int id, Packet packet);
	}


	private static class _ClientState
	{
		public final int clientId;
		private boolean _didHandshake;
		
		public _ClientState(int clientId)
		{
			this.clientId = clientId;
		}
		
		public void setHandshakeCompleted()
		{
			Assert.assertTrue(!_didHandshake);
			_didHandshake = true;
		}
		
		public boolean didHandshake()
		{
			return _didHandshake;
		}
	}
}
