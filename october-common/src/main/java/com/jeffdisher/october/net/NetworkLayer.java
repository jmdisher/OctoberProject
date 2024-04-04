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
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import com.jeffdisher.october.utils.Assert;


/**
 * This is the low-level network layer which manages synchronous IO multiplexing across multiple connections and an
 * acceptor socket.
 * It internally runs on a background thread and all callbacks issued through the IListener interface are issued on that
 * thread so they are expected to return quickly.
 */
public class NetworkLayer
{
	// The buffer must be large enough to hold precisely 1 packet.
	public static final int BUFFER_SIZE_BYTES = PacketCodec.MAX_PACKET_BYTES;

	/**
	 * Creates a layer which is listening as a server.  Returns once the requested port has been bound and the internal
	 * thread has started.
	 * 
	 * @param  The type used as a token to describe connections.
	 * @param listener The callback interface (which will be called on the internal thread).
	 * @param port The port on which to listen for connections.
	 * @return The network layer abstraction.
	 * @throws IOException An error occurred while configuring the network.
	 */
	public static  NetworkLayer startListening(IListener listener, int port) throws IOException
	{
		InetSocketAddress address = new InetSocketAddress(port);
		ServerSocketChannel socket = ServerSocketChannel.open();
		socket.bind(address);
		return new NetworkLayer(listener, socket, null);
	}

	/**
	 * Creates a layer which contains a single connection to a server.
	 * 
	 * @param  The type used as a token to describe connections.
	 * @param listener The callback interface (which will be called on the internal thread).
	 * @param host The remote host to contact.
	 * @param port The port to use when connecting to the remote host.
	 * @return The network layer abstraction.
	 * @throws IOException An error occurred while configuring the network.
	 */
	public static  NetworkLayer connectToServer(IListener listener, InetAddress host, int port) throws IOException
	{
		SocketChannel client = SocketChannel.open(new InetSocketAddress(host, port));
		return new NetworkLayer(listener, null, client);
	}


	private final Thread _internalThread;
	private final IListener _listener;
	private final Selector _selector;
	private final IdentityHashMap<PeerToken, _PeerState> _connectedPeers;

	// Data related to the hand-off between internal and background threads.
	private boolean _keepRunning;
	private IdentityHashMap<PeerToken, Packet> _shared_outgoingPackets;
	private Queue<PeerToken> _shared_disconnectRequests;
	
	// Server mode details.
	private final ServerSocketChannel _acceptorSocket;
	private final SelectionKey _acceptorKey;

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
		_connectedPeers = new IdentityHashMap<>();
		
		// Initialize shared data.
		_keepRunning = true;
		_shared_outgoingPackets = null;
		_shared_disconnectRequests = null;
		
		// Do any server-specific or client-specific start-up.
		if (null != serverSocket)
		{
			serverSocket.configureBlocking(false);
			int serverSocketOps = serverSocket.validOps();
			Assert.assertTrue(SelectionKey.OP_ACCEPT == serverSocketOps);
			SelectionKey acceptor = serverSocket.register(_selector, serverSocketOps, null);
			
			_acceptorSocket = serverSocket;
			_acceptorKey = acceptor;
		}
		else
		{
			clientSocket.configureBlocking(false);
			SelectionKey newKey = clientSocket.register(_selector, SelectionKey.OP_READ, null);
			_PeerState state = new _PeerState(clientSocket, newKey);
			newKey.attach(state);
			_connectedPeers.put(state, state);
			
			_acceptorSocket = null;
			_acceptorKey = null;
			
			// Notify the listener that this is connected, so they see the token.
			listener.peerConnected(state);
		}
		
		// Start the internal thread since we are now initialized.
		_internalThread.start();
	}

	/**
	 * Stops the network abstraction.  This will close all sockets, cancel any pending operations, and stop the internal
	 * thread.
	 * Note that any outgoing messages sent before this call will be left in an undefined send state.
	 */
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

	/**
	 * Requests that a packet be sent to the given peer.  Note that only a single message can be outgoing for a given
	 * peer at a given time.
	 * 
	 * @param peer The target of the message.
	 * @param packet The message to serialize and send.
	 */
	public synchronized void sendMessage(PeerToken peer, Packet packet)
	{
		// This should only be enqueued if we there was nothing there and we notified them.
		if (null == _shared_outgoingPackets)
		{
			_shared_outgoingPackets = new IdentityHashMap<>();
		}
		Assert.assertTrue(!_shared_outgoingPackets.containsKey(peer));
		_shared_outgoingPackets.put(peer, packet);
		_selector.wakeup();
	}

	/**
	 * Forcibly disconnects the referenced peer.
	 * 
	 * @param token The peer to disconnect.
	 */
	public synchronized void disconnectPeer(PeerToken token)
	{
		if (null == _shared_disconnectRequests)
		{
			_shared_disconnectRequests = new LinkedList<>();
		}
		_shared_disconnectRequests.add(token);
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
			IdentityHashMap<PeerToken, Packet> packetsToSerialize = null;
			Queue<PeerToken> peersToDisconnect = null;
			synchronized (this)
			{
				packetsToSerialize = _shared_outgoingPackets;
				_shared_outgoingPackets = null;
				peersToDisconnect = _shared_disconnectRequests;
				_shared_disconnectRequests = null;
			}
			// First, disconnect anyone we can.
			if (null != peersToDisconnect)
			{
				for (PeerToken peer : peersToDisconnect)
				{
					_PeerState client = _connectedPeers.get(peer);
					// This may have already disconnected elsewhere.
					if (null != client)
					{
						_backgroundDisconnectClient(client);
					}
				}
			}
			// Now, send any outgoing packets.
			if (null != packetsToSerialize)
			{
				for (Map.Entry<PeerToken, Packet> elt : packetsToSerialize.entrySet())
				{
					_PeerState client = _connectedPeers.get(elt.getKey());
					// The peer may have disconnected.
					if (null != client)
					{
						// This should already be empty.
						Assert.assertTrue(0 == client.outgoing.position());
						Packet packet = elt.getValue();
						_backgroundSerializePacket(client, packet);
					}
				}
			}
		}
		
		// We are shutting down so close all clients.
		for (_PeerState elt : _connectedPeers.values()) {
			try {
				elt.channel.close();
			} catch (IOException e) {
				// This is a shutdown so just drop the exception and proceed.
			}
			elt.key.cancel();
		}
		_connectedPeers.clear();
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
					_backgroundDisconnectClient(state);
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
		SelectionKey newKey;
		try
		{
			// We always want to be ready to read the client (we assume that the listener can drink from the firehose - may need changes in the future).
			newKey = newNode.register(_selector, SelectionKey.OP_READ, null);
		}
		catch (ClosedChannelException e)
		{
			// We just created this channel so this can't happen.
			throw Assert.unexpected(e);
		}
		_PeerState newClient = new _PeerState(newNode, newKey);
		newKey.attach(newClient);
		_connectedPeers.put(newClient, newClient);
		_listener.peerConnected(newClient);
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
				_listener.packetReceived(state, packet);
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
				state.key.interestOps(state.key.interestOps() & ~SelectionKey.OP_WRITE);
				
				// We can also notify the listener that they are ready to write something to the buffer.
				_listener.peerReadyForWrite(state);
			}
		}
		return didWrite;
	}

	private void _backgroundSerializePacket(_PeerState client, Packet packet)
	{
		PacketCodec.serializeToBuffer(client.outgoing, packet);
		client.key.interestOps(client.key.interestOps() | SelectionKey.OP_WRITE);
		// Flip the buffer so we can write it.
		client.outgoing.flip();
	}

	private void _backgroundDisconnectClient(_PeerState state)
	{
		// Remove this from our set and send the callback.
		_PeerState removed = _connectedPeers.remove(state);
		Assert.assertTrue(removed == state);
		try
		{
			state.channel.close();
		}
		catch (IOException e)
		{
			// We are dropping this so we don't care.
		}
		state.key.cancel();
		_listener.peerDisconnected(state);
	}


	/**
	 * The interface for listening to events from inside the layer.  Note that all calls will be issued on the internal
	 * thread so they must return soon in order to avoid slowing the system.
	 * 
	 * The instances of PeerToken are managed by the NetworkLayer and are deliberately opaque.
	 */
	public static interface IListener
	{
		/**
		 * Called when a peer has connected.
		 * 
		 * @param token The peer's opaque token.
		 */
		void peerConnected(PeerToken token);
		/**
		 * Called when a peer has disconnected.
		 * 
		 * @param token The peer's opaque token.
		 */
		void peerDisconnected(PeerToken token);
		/**
		 * Called when the connection to a peer is ready to receive new messages to send.
		 * 
		 * @param token The peer's opaque token.
		 */
		void peerReadyForWrite(PeerToken token);
		/**
		 * Called when a new message has arrived from a peer.
		 * 
		 * @param token The peer's opaque token.
		 * @param packet The packet from the peer.
		 */
		void packetReceived(PeerToken token, Packet packet);
	}

	/**
	 * This only exists to be an opaque token for the public interface token.
	 */
	public static interface PeerToken {}

	private static class _PeerState implements PeerToken
	{
		public final SocketChannel channel;
		public final SelectionKey key;
		public final ByteBuffer incoming;
		public final ByteBuffer outgoing;
		
		public _PeerState(SocketChannel channel, SelectionKey key)
		{
			this.channel = channel;
			this.key = key;
			this.incoming = ByteBuffer.allocate(BUFFER_SIZE_BYTES);
			this.outgoing = ByteBuffer.allocate(BUFFER_SIZE_BYTES);
		}
	}
}
