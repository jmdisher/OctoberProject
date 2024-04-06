package com.jeffdisher.october.net;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
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
	private final NetworkLayer _network;

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
		
		_network = NetworkLayer.startListening(new NetworkLayer.IListener()
		{
			@Override
			public void peerConnected(NetworkLayer.PeerToken token)
			{
				// Create the empty state.
				token.setData(new _ClientState(token));
			}
			@Override
			public void peerDisconnected(NetworkLayer.PeerToken token)
			{
				int clientId = ((_ClientState)token.getData()).clientId;
				// Note that we still see this disconnect if we failed the handshake and we disconnect them.
				if (clientId > 0)
				{
					synchronized(NetworkServer.this)
					{
						_ClientState channel = _channelsById.remove(clientId);
						Assert.assertTrue(null != channel);
					}
					_listener.userLeft(clientId);
				}
			}
			@Override
			public void peerReadyForWrite(NetworkLayer.PeerToken token)
			{
				_ClientState state = (_ClientState) token.getData();
				// Given that we start in a writable state, and we send the last message in the handshake, we should only get here if the handshake is complete.
				Assert.assertTrue(state.didHandshake);
				_listener.networkWriteReady(state.clientId);
			}
			@Override
			public void peerReadyForRead(NetworkLayer.PeerToken token)
			{
				_ClientState state = (_ClientState) token.getData();
				if (state.didHandshake)
				{
					_listener.networkReadReady(state.clientId);
				}
				else
				{
					// This MUST be the client's introduction (and nothing else).
					List<Packet> messages = _network.receiveMessages(token);
					Packet packet = messages.get(0);
					if ((1 == messages.size()) && (PacketType.CLIENT_SEND_DESCRIPTION == packet.type))
					{
						Packet_ClientSendDescription safe = (Packet_ClientSendDescription)packet;
						if (0 == safe.version)
						{
							// Make sure that we can resolve this user (and that they aren't already here).
							int clientId  = _listener.userJoined(safe.name);
							if (clientId > 0)
							{
								state.clientId = clientId;
								state.didHandshake = true;
								synchronized(NetworkServer.this)
								{
									_channelsById.put(clientId, state);
								}
								
								// Send out description and consider the handshake completed.
								// TODO:  Pass this in as some kind of configuration once we care about that - this is mostly just to show that we can pass config data here.
								long millisPerTick = 100L;
								_network.sendMessage(token, new Packet_ServerSendConfiguration(state.clientId, millisPerTick));
							}
							else
							{
								// This user is not allowed to join.
								System.err.println("Client \"" + safe.name + "\" rejected during handshake");
								_network.disconnectPeer(token);
							}
						}
						else
						{
							// Unknown version so just disconnect them.
							System.err.println("Client " + state.clientId + ": Requested unknown protocol version: " + safe.version);
							_network.disconnectPeer(token);
						}
					}
					else
					{
						// This is bogus so disconnect them.
						System.err.println("Client " + state.clientId + ": Failed handshake with type: " + packet.type.name());
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
		_network.sendMessage(client.token, packet);
	}

	/**
	 * Reads all of the buffered packets associated with the given clientId.  Note that calling this will also allow
	 * reads from this client to resume in the background.
	 * 
	 * @param clientId The ID of a specific attached client.
	 * @return The list of packets buffered from them.
	 */
	public List<Packet> readBufferedPackets(int clientId)
	{
		_ClientState client;
		synchronized(this)
		{
			client = _channelsById.get(clientId);
			Assert.assertTrue(null != client);
		}
		return _network.receiveMessages(client.token);
	}

	/**
	 * Disconnects the client with the given ID.
	 * 
	 * @param clientId The ID of a specific attached client.
	 */
	public void disconnectClient(int clientId)
	{
		_ClientState client;
		synchronized(this)
		{
			// We will not remove this until we get the callback from the other side, so we don't have multiple paths causing races.
			client = _channelsById.get(clientId);
		}
		// This could be racy or already gone.
		if (null != client)
		{
			_network.disconnectPeer(client.token);
		}
	}

	/**
	 * The interface for listening to events from inside the server.  Note that all calls will be issued on the internal
	 * thread so they must return soon in order to avoid slowing the system.
	 */
	public static interface IListener
	{
		/**
		 * Called when a user joins, in order to complete its handshake.  Note that the network is still not ready until
		 * networkReady(int) is received.
		 * 
		 * @param name The client's human name.
		 * @return The ID to use for this user (<=0 implies the client should be rejected).
		 */
		int userJoined(String name);
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
		void networkWriteReady(int id);
		/**
		 * Called when the network is waiting for messages to be read from this client.
		 * 
		 * @param id The ID of the client.
		 */
		void networkReadReady(int id);
	}


	private static class _ClientState
	{
		public final NetworkLayer.PeerToken token;
		public int clientId;
		public boolean didHandshake;
		public _ClientState(NetworkLayer.PeerToken token)
		{
			this.token = token;
		}
	}
}
