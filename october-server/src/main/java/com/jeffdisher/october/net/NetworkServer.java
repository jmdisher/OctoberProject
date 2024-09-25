package com.jeffdisher.october.net;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.LongSupplier;

import com.jeffdisher.october.utils.Assert;


/**
 * A wrapper over NetworkLayer which adapts its interface for server-specific use-cases.
 * Note that all calls issued on the IListener interface are run on the internal network thread and must return quickly.
 */
public class NetworkServer<L>
{
	/**
	 * We will default to 10 second for the handshake since some stress tests may take longer than 1 second for hundreds
	 * of clients to fully connect.
	 */
	public static final long DEFAULT_NEW_CONNECTION_TIMEOUT_MILLIS = 10_000L;

	private final IListener<L> _listener;
	private final NetworkLayer<PacketFromClient, PacketFromServer> _network;

	// This collection is owned by the network background thread.
	// We add to it whenever a new connection arrive.  That is also the only point where we walk the list (to avoid extra work in the critical path for this rare case).
	private final List<_AwaitingHandshake> _awaitingHandshakes = new ArrayList<>();

	/**
	 * Creates a new server, returning once the port is bound.
	 * 
	 * @param listener The listener which will receive callbacks related to the server (on the network thread).
	 * @param currentTimeMillisProvider Returns the current system time, in milliseconds.
	 * @param port The port which should be bound for accepting incoming connections.
	 * @param serverMillisPerTick The number of milliseconds per tick for this server instance.
	 * @throws IOException An error occurred while configuring the network.
	 */
	public NetworkServer(IListener<L> listener, LongSupplier currentTimeMillisProvider, int port, long serverMillisPerTick) throws IOException
	{
		_listener = listener;
		
		_network = NetworkLayer.startListening(new NetworkLayer.IListener()
		{
			@Override
			public void peerConnected(NetworkLayer.PeerToken token)
			{
				// Create the empty state.
				token.setData(new _ClientState<L>());
				
				// Do we need to clean up any connections?
				long currentTimeMillis = currentTimeMillisProvider.getAsLong();
				_cleanupAwaitingHandshake(currentTimeMillis);
				
				// Add this to the list awaiting handshakes.
				long limitTimeMillis = currentTimeMillis + DEFAULT_NEW_CONNECTION_TIMEOUT_MILLIS;
				_awaitingHandshakes.add(new _AwaitingHandshake(limitTimeMillis, token));
			}
			@Override
			public void peerDisconnected(NetworkLayer.PeerToken token)
			{
				_ClientState<L> state = _downcastData(token);
				if (null != state.data)
				{
					_listener.userLeft(state.data);
					// We will also clear the data so we know that this was disconnected.
					state.data = null;
				}
			}
			@Override
			public void peerReadyForWrite(NetworkLayer.PeerToken token)
			{
				_ClientState<L> state = _downcastData(token);
				// Given that we start in a writable state, and we send the last message in the handshake, we should only get here if the handshake is complete.
				Assert.assertTrue(null != state.data);
				_listener.networkWriteReady(state.data);
			}
			@Override
			public void peerReadyForRead(NetworkLayer.PeerToken token)
			{
				_ClientState<L> state = _downcastData(token);
				if (null != state.data)
				{
					_listener.networkReadReady(state.data);
				}
				else
				{
					// This MUST be the client's introduction (and nothing else).
					List<PacketFromClient> messages = _network.receiveMessages(token);
					PacketFromClient packet = messages.get(0);
					if ((1 == messages.size()) && (PacketType.CLIENT_SEND_DESCRIPTION == packet.type))
					{
						Packet_ClientSendDescription safe = (Packet_ClientSendDescription)packet;
						if (Packet_ClientSendDescription.NETWORK_PROTOCOL_VERSION == safe.version)
						{
							// Make sure that we can resolve this user (and that they aren't already here).
							ConnectingClientDescription<L> description = _listener.userJoined(token, safe.name);
							if (null != description)
							{
								state.data = description.data;
								
								// Send out description and consider the handshake completed.
								_network.sendMessage(token, new Packet_ServerSendClientId(description.clientId, serverMillisPerTick));
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
							System.err.println("Client requested unsupported protocol version: " + safe.version);
							_network.disconnectPeer(token);
						}
					}
					else
					{
						// This is bogus so disconnect them.
						System.err.println("Client failed handshake with type: " + packet.type.name());
						_network.disconnectPeer(token);
					}
				}
			}
			@SuppressWarnings("unchecked")
			private _ClientState<L> _downcastData(NetworkLayer.PeerToken token)
			{
				return (_ClientState<L>) token.getData();
			}
			private void _cleanupAwaitingHandshake(long currentTimeMillis)
			{
				if (!_awaitingHandshakes.isEmpty())
				{
					boolean keepWalking = true;
					Iterator<_AwaitingHandshake> walker = _awaitingHandshakes.iterator();
					while (keepWalking && walker.hasNext())
					{
						_AwaitingHandshake current = walker.next();
						if (current.limitTimeMillis <= currentTimeMillis)
						{
							// See if we should disconnect this or if they proceeded correctly.
							_ClientState<L> state = _downcastData(current.token);
							if (null == state.data)
							{
								// Not fully connected so disconnect.
								// Even if there is a pending handshake, right now, we will still disconnect so we will
								// see them connect and disconnect since disconnect callbacks always arrive, even if we
								// initiated.
								System.err.println("Client timeout awaiting handshake");
								_network.disconnectPeer(current.token);
							}
							else
							{
								// This is connected so we just drop it from this list.
							}
							// Either way, we are done tracking this.
							walker.remove();
						}
						else
						{
							// These are sorted by time so just end.
							keepWalking = false;
						}
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
	 * @param token The token of a specific attached client.
	 * @param packet The message to send.
	 */
	public void sendMessage(NetworkLayer.PeerToken token, PacketFromServer packet)
	{
		_network.sendMessage(token, packet);
	}

	/**
	 * Reads all of the buffered packets associated with the given clientId.  Note that calling this will also allow
	 * reads from this client to resume in the background.
	 * 
	 * @param token The token of a specific attached client.
	 * @return The list of packets buffered from them.
	 */
	public List<PacketFromClient> readBufferedPackets(NetworkLayer.PeerToken token)
	{
		return _network.receiveMessages(token);
	}

	/**
	 * Disconnects the client with the given ID.
	 * 
	 * @param token The token of a specific attached client.
	 */
	public void disconnectClient(NetworkLayer.PeerToken token)
	{
		_network.disconnectPeer(token);
	}

	/**
	 * The interface for listening to events from inside the server.  Note that all calls will be issued on the internal
	 * thread so they must return soon in order to avoid slowing the system.
	 */
	public static interface IListener<T>
	{
		/**
		 * Called when a user joins, in order to complete its handshake.  Note that the network is neither readable nor
		 * writable when first called.
		 * 
		 * @param token The token to use when interacting with the network.
		 * @param name The client's human name.
		 * @return The description of the client, null if the connection should be rejected.
		 */
		ConnectingClientDescription<T> userJoined(NetworkLayer.PeerToken token, String name);
		/**
		 * Called when a user has disconnected.
		 * 
		 * @param token The token to use when interacting with the network.
		 */
		void userLeft(T data);
		/**
		 * Called when the network is free to send more messages to this client.
		 * 
		 * @param token The token to use when interacting with the network.
		 */
		void networkWriteReady(T data);
		/**
		 * Called when the network is waiting for messages to be read from this client.
		 * 
		 * @param token The token to use when interacting with the network.
		 */
		void networkReadReady(T data);
	}

	public static record ConnectingClientDescription<T>(int clientId
			, T data
	) {}

	private static class _ClientState<T>
	{
		// This value is set non-null after a successful handshake and joining.
		public T data;
	}

	private static record _AwaitingHandshake(long limitTimeMillis
			, NetworkLayer.PeerToken token
	) {}
}
