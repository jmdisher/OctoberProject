package com.jeffdisher.october.net;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.october.utils.Assert;


/**
 * The server manages connections coming in from clients, the asynchronous IO of the data with the network, and the
 * serialization/deserialization of messages.
 */
public class NetworkLayer
{
	// The buffer must be large enough to hold precisely 1 packet.
	public static final int BUFFER_SIZE_BYTES = PacketCodec.MAX_PACKET_BYTES;
	// The ID to use when running in client mode.
	public static final int CLIENT_MODE_ID = 0;

	public static NetworkLayer startListening(IListener listener, int port) throws IOException
	{
		InetSocketAddress address = new InetSocketAddress(port);
		ServerSocketChannel socket = ServerSocketChannel.open();
		socket.bind(address);
		return new NetworkLayer(listener, socket, null);
	}

	public static NetworkLayer connectToServer(IListener listener, InetAddress host, int port) throws IOException
	{
		SocketChannel client = SocketChannel.open(new InetSocketAddress(host, port));
		// TODO:  Manage the handshake sequence once the network is refactored into client and server elements.
		return new NetworkLayer(listener, null, client);
	}


	private final Thread _internalThread;
	private final IListener _listener;
	private final Selector _selector;
	private final Map<SelectionKey, _PeerState> _channelsByKey;
	private final Map<Integer, _PeerState> _channelsById;

	// Data related to the hand-off between internal and background threads.
	private boolean _keepRunning;
	private Map<Integer, Packet> _shared_outgoingByClientId;
	
	// Server mode details.
	private final ServerSocketChannel _acceptorSocket;
	private final SelectionKey _acceptorKey;
	private int _nextClientId;

	private NetworkLayer(IListener listener, ServerSocketChannel serverSocket, SocketChannel clientSocket) throws IOException
	{
		// We can only be running in server mode OR client mode.
		Assert.assertTrue((null != serverSocket) != (null != clientSocket));
		
		// Set up the selector and add input types.
		
		_internalThread = new Thread(() -> {
			_backgroundThreadMain();
		}, "Server IO thread");
		_listener = listener;
		_selector = Selector.open();
		_channelsByKey = new HashMap<>();
		_channelsById = new HashMap<>();
		
		// Initialize shared data.
		_keepRunning = true;
		_shared_outgoingByClientId = null;
		
		// Do any server-specific or client-specific start-up.
		if (null != serverSocket)
		{
			serverSocket.configureBlocking(false);
			int serverSocketOps = serverSocket.validOps();
			Assert.assertTrue(SelectionKey.OP_ACCEPT == serverSocketOps);
			SelectionKey acceptor = serverSocket.register(_selector, serverSocketOps, null);
			
			_acceptorSocket = serverSocket;
			_acceptorKey = acceptor;
			_nextClientId = 1;
		}
		else
		{
			clientSocket.configureBlocking(false);
			_PeerState state = new _PeerState(clientSocket, CLIENT_MODE_ID);
			SelectionKey newKey = clientSocket.register(_selector, SelectionKey.OP_READ, state);
			_channelsByKey.put(newKey, state);
			_channelsById.put(state.clientId, state);
			Assert.assertTrue(_channelsByKey.size() == _channelsById.size());
			state.setKey(newKey);
			state.setHandshakeCompleted();
			
			_acceptorSocket = null;
			_acceptorKey = null;
			_nextClientId = -1;
		}
		
		// Start the internal thread since we are now initialized.
		_internalThread.start();
	}

	public void stop()
	{
		_keepRunning = false;
		_selector.wakeup();
		try
		{
			_internalThread.join();
		}
		catch (InterruptedException e)
		{
			// We don't use interruption.
			Assert.unexpected(e);
		}
		if (null != _acceptorKey)
		{
			_acceptorKey.cancel();
		}
		try
		{
			_selector.selectNow();
		}
		catch (IOException e)
		{
			// We don't expect errors here.
			Assert.unexpected(e);
		}
		if (null != _acceptorSocket)
		{
			try
			{
				_acceptorSocket.close();
			}
			catch (IOException e)
			{
				// We don't expect errors here.
				Assert.unexpected(e);
			}
		}
		Assert.assertTrue(_selector.keys().isEmpty());
	}

	public synchronized void sendMessage(int clientId, Packet packet)
	{
		// This should only be enqueued if we there was nothing there and we notified them.
		if (null == _shared_outgoingByClientId)
		{
			_shared_outgoingByClientId = new HashMap<>();
		}
		Assert.assertTrue(!_shared_outgoingByClientId.containsKey(clientId));
		_shared_outgoingByClientId.put(clientId, packet);
		_selector.wakeup();
	}


	private void _backgroundThreadMain()
	{
		while (_keepRunning)
		{
			int selectedKeyCount = 0;
			try {
				selectedKeyCount = _selector.select();
			} catch (IOException e) {
				// TODO:  Determine how we want to handle this once we observe what kind of IO error can happen here.
				throw Assert.unexpected(e);
			}
			// We are often just woken up to update the set of interested operations so this may be empty.
			if (selectedKeyCount > 0) {
				_backgroundProcessSelectedKeys();
			}
			// Check the handoff map.
			Map<Integer, Packet> packetsToSerialize = null;
			synchronized (this)
			{
				packetsToSerialize = _shared_outgoingByClientId;
				_shared_outgoingByClientId = null;
			}
			if (null != packetsToSerialize)
			{
				for (Map.Entry<Integer, Packet> elt : packetsToSerialize.entrySet())
				{
					_PeerState client = _channelsById.get(elt.getKey());
					// This should already be empty.
					Assert.assertTrue(0 == client.outgoing.position());
					Packet packet = elt.getValue();
					_backgroundSerializePacket(client, packet);
				}
			}
		}
		
		// We are shutting down so close all clients.
		for (Map.Entry<SelectionKey, _PeerState> elt : _channelsByKey.entrySet()) {
			try {
				elt.getValue().channel.close();
			} catch (IOException e) {
				// This is a shutdown so just drop the exception and proceed.
			}
			elt.getKey().cancel();
		}
		_channelsByKey.clear();
		_channelsById.clear();
	}

	private void _backgroundProcessSelectedKeys()
	{
		Set<SelectionKey> keys = _selector.selectedKeys();
		for (SelectionKey key : keys)
		{
			// The key "isValid" will only be set false by our attempts to cancel on disconnect, below in this method, but it should start out valid.
			Assert.assertTrue(key.isValid());
			if (key == _acceptorKey) {
				_backgroundProcessAcceptorKey(key);
			} else {
				// This is normal data movement so get the state out of the attachment.
				_PeerState state = (_PeerState)key.attachment();
				// We can't fail to find this since we put it in the collection.
				Assert.assertTrue(null != state);
				
				// See what operation we wanted to perform.
				boolean shouldClose = false;
				if (key.isReadable()) {
					boolean didRead = _backgroundProcessReadableKey(key, state);
					shouldClose = !didRead;
				}
				if (key.isWritable()) {
					boolean didWrite = _backgroundProcessWritableKey(key, state);
					shouldClose = !didWrite;
				}
				if (shouldClose)
				{
					_backgroundDisconnectClient(key, state);
				}
			}
		}
		keys.clear();
	}

	private void _backgroundProcessAcceptorKey(SelectionKey key)
	{
		// This must be a new node connecting.
		Assert.assertTrue(SelectionKey.OP_ACCEPT == key.readyOps());
		// This cannot be null if we matched the acceptor key.
		Assert.assertTrue(null != _acceptorSocket);
		SocketChannel newNode;
		try {
			newNode = _acceptorSocket.accept();
		} catch (IOException e) {
			// We don't know what problem would result in an IOException during accept so flag this as a bug.
			throw Assert.unexpected(e);
		}
		
		// Configure this new node for our selection set - by default, it starts only waiting for read.
		try {
			newNode.configureBlocking(false);
		} catch (IOException e) {
			// Changing this state shouldn't involve an IOException so flag that as fatal, if it happens.
			throw Assert.unexpected(e);
		}
		_PeerState newClient = new _PeerState(newNode, _nextClientId);
		_nextClientId += 1;
		SelectionKey newKey;
		try
		{
			// We always want to be ready to read the client (we assume that the listener can drink from the firehose - may need changes in the future).
			newKey = newNode.register(_selector, SelectionKey.OP_READ, newClient);
		}
		catch (ClosedChannelException e)
		{
			// We just created this channel so this can't happen.
			throw Assert.unexpected(e);
		}
		_channelsByKey.put(newKey, newClient);
		_channelsById.put(newClient.clientId, newClient);
		Assert.assertTrue(_channelsByKey.size() == _channelsById.size());
		newClient.setKey(newKey);
		
		// We start off by sending them the handshake, before we even return to the list.
		_backgroundSerializePacket(newClient, new Packet_AssignClientId(newClient.clientId));
	}

	private boolean _backgroundProcessReadableKey(SelectionKey key, _PeerState state)
	{
		// Read the available bytes into our local buffer.
		boolean didRead = false;
		try
		{
			int read = state.channel.read(state.incoming);
			if (-1 == read)
			{
				didRead = false;
			}
			else
			{
				didRead = true;
			}
		}
		catch (IOException e)
		{
			// This is typically a "Connection reset by peer".
			didRead = false;
		}
		
		if (didRead)
		{
			// See if we can parse the data in the buffer.
			state.incoming.flip();
			Packet packet = PacketCodec.parseAndSeekFlippedBuffer(state.incoming);
			while (null != packet)
			{
				// Make sure that this client is ready.
				if (state.didHandshake())
				{
					_listener.packetReceived(state.clientId, packet);
				}
				else
				{
					// This MUST be the response type.
					if (PacketType.SET_CLIENT_NAME == packet.type)
					{
						state.setHandshakeCompleted();
						_listener.userJoined(state.clientId, ((Packet_SetClientName)packet).name);
					}
					else
					{
						// This is bogus so disconnect them.
						System.err.println("Client " + state.clientId + ": Failed handshake with type: " + packet.type.name());
						didRead = false;
					}
				}
				packet = PacketCodec.parseAndSeekFlippedBuffer(state.incoming);
			}
			state.incoming.compact();
			
			// We never want to stop reading since the buffer should never fill (we should process the opcode before).
			Assert.assertTrue(state.incoming.hasRemaining());
		}
		return didRead;
	}

	private boolean _backgroundProcessWritableKey(SelectionKey key, _PeerState state)
	{
		boolean didWrite = false;
		// The buffer must have something in it if we got here.
		Assert.assertTrue(state.outgoing.hasRemaining());
		try
		{
			int written = state.channel.write(state.outgoing);
			// We must have written something or thrown.
			Assert.assertTrue(written > 0);
			didWrite = true;
		}
		catch (IOException e)
		{
			// We will just close this.
			didWrite = false;
		}
		
		if (didWrite)
		{
			// See if there is more to write.
			if (state.outgoing.hasRemaining())
			{
				// We will leave the key waiting for writable state.
			}
			else
			{
				// Clear the buffer and remote the writable state from the key.
				state.outgoing.clear();
				state.getKey().interestOps(state.getKey().interestOps() & ~SelectionKey.OP_WRITE);
				
				// We can also notify the listener that they are ready to write something to the buffer.
				if (state.didHandshake())
				{
					_listener.networkReady(state.clientId);
				}
			}
		}
		return didWrite;
	}

	private void _backgroundSerializePacket(_PeerState client, Packet packet)
	{
		PacketCodec.serializeToBuffer(client.outgoing, packet);
		client.getKey().interestOps(client.getKey().interestOps() | SelectionKey.OP_WRITE);
		// Flip the buffer so we can write it.
		client.outgoing.flip();
	}

	private void _backgroundDisconnectClient(SelectionKey key, _PeerState state)
	{
		// Remove this from our list and send the callback.
		_channelsByKey.remove(key);
		_channelsById.remove(state.clientId);
		Assert.assertTrue(_channelsByKey.size() == _channelsById.size());
		try
		{
			state.channel.close();
		}
		catch (IOException e)
		{
			// We are dropping this so we don't care.
		}
		_listener.userLeft(state.clientId);
	}


	public static interface IListener
	{
		void userJoined(int id, String name);
		void userLeft(int id);
		void networkReady(int id);
		void packetReceived(int id, Packet packet);
	}


	private static class _PeerState
	{
		public final SocketChannel channel;
		public final int clientId;
		public final ByteBuffer incoming;
		public final ByteBuffer outgoing;
		private SelectionKey _key;
		private boolean _didHandshake;
		
		public _PeerState(SocketChannel channel, int clientId)
		{
			this.channel = channel;
			this.clientId = clientId;
			this.incoming = ByteBuffer.allocate(BUFFER_SIZE_BYTES);
			this.outgoing = ByteBuffer.allocate(BUFFER_SIZE_BYTES);
		}
		
		public void setKey(SelectionKey key)
		{
			Assert.assertTrue(null == _key);
			_key = key;
		}
		
		public SelectionKey getKey()
		{
			return _key;
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
