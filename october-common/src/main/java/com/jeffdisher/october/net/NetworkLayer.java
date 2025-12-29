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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import com.jeffdisher.october.utils.Assert;


/**
 * This is the low-level network layer which manages synchronous IO multiplexing across multiple connections and an
 * acceptor socket.
 * It internally runs on a background thread and all callbacks issued through the IListener interface are issued on that
 * thread so they are expected to return quickly.
 * Note that it internally decodes the incoming packets and sends them back as high-level data but outgoing packets are
 * serialized by the caller, into the buffer.  This asymmetry may not be permanent but is currently being used in order
 * to avoid so many small back-and-forth calls on the server when sending a stream of potentially hundreds of packets to
 * each client in a tick while also allowing the caller to know exactly how aggressively it can serialize.
 * @param <IN> The incoming packet type.
 */
public class NetworkLayer<IN extends Packet>
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
	public static NetworkLayer<PacketFromClient> startListening(IListener listener, int port) throws IOException
	{
		InetSocketAddress address = new InetSocketAddress(port);
		ServerSocketChannel socket = ServerSocketChannel.open();
		socket.bind(address);
		return new NetworkLayer<>(PacketFromClient.class, listener, socket, null, "Server Network Layer");
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
	public static NetworkLayer<PacketFromServer> connectToServer(IListener listener, InetAddress host, int port) throws IOException
	{
		SocketChannel client = SocketChannel.open(new InetSocketAddress(host, port));
		return new NetworkLayer<>(PacketFromServer.class, listener, null, client, "Client Network Layer");
	}


	private final Class<IN> _inClass;
	private final Thread _internalThread;
	private final IListener _listener;
	private final Selector _selector;
	private final IdentityHashMap<IPeerToken, _PeerState> _connectedPeers;
	private final ReentrantLock _lock;

	// Data related to the hand-off between internal and background threads.
	private boolean _keepRunning;
	private IdentityHashMap<IPeerToken, ByteBuffer> _shared_outgoingBuffers;
	private final IdentityHashMap<IPeerToken, List<IN>> _shared_incomingPackets;
	private Set<IPeerToken> _shared_resumeReads;
	private Queue<IPeerToken> _shared_disconnectRequests;
	
	// Server mode details.
	private final ServerSocketChannel _acceptorSocket;
	private final SelectionKey _acceptorKey;

	private NetworkLayer(Class<IN> inClass, IListener listener, ServerSocketChannel serverSocket, SocketChannel clientSocket, String threadName) throws IOException
	{
		// We can only be running in server mode OR client mode.
		Assert.assertTrue((null != serverSocket) != (null != clientSocket));
		
		// We need the incoming class for casting.
		_inClass = inClass;
		
		// Set up the selector and add input types.
		
		_internalThread = new Thread(() -> {
			_backgroundThreadMain();
		}, threadName);
		_listener = listener;
		_selector = Selector.open();
		_connectedPeers = new IdentityHashMap<>();
		_lock = new ReentrantLock();
		
		// Initialize shared data.
		_keepRunning = true;
		_shared_outgoingBuffers = null;
		_shared_incomingPackets = new IdentityHashMap<>();
		_shared_resumeReads = null;
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
			listener.peerConnected(state, state.releaseOutgoing());
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
	 * @param buffer The buffer previously sent back to be populated.
	 */
	public void sendBuffer(IPeerToken peer, ByteBuffer buffer)
	{
		try
		{
			_lock.lock();
			// This should only be enqueued if we there was nothing there and we notified them.
			if (null == _shared_outgoingBuffers)
			{
				_shared_outgoingBuffers = new IdentityHashMap<>();
			}
			Assert.assertTrue(!_shared_outgoingBuffers.containsKey(peer));
			_shared_outgoingBuffers.put(peer, buffer);
			_selector.wakeup();
		}
		finally
		{
			_lock.unlock();
		}
	}

	/**
	 * Retrieves the messages received from the given peer.  Calling this will drain all the buffered packets and allow
	 * the network layer to resume receiving data from the peer.
	 * 
	 * @param peer The target of the message.
	 */
	public List<IN> receiveMessages(IPeerToken peer)
	{
		try
		{
			_lock.lock();
			// This should only be called if there are packets to receive.
			List<IN> packets = _shared_incomingPackets.remove(peer);
			Assert.assertTrue(packets.size() > 0);
			
			// We need to record that this peer should start reading again.
			if (null == _shared_resumeReads)
			{
				_shared_resumeReads = new HashSet<>();
			}
			_shared_resumeReads.add(peer);
			_selector.wakeup();
			return packets;
		}
		finally
		{
			_lock.unlock();
		}
	}

	/**
	 * Forcibly disconnects the referenced peer.
	 * 
	 * @param token The peer to disconnect.
	 */
	public void disconnectPeer(IPeerToken token)
	{
		try
		{
			_lock.lock();
			if (null == _shared_disconnectRequests)
			{
				_shared_disconnectRequests = new LinkedList<>();
			}
			_shared_disconnectRequests.add(token);
			_selector.wakeup();
		}
		finally
		{
			_lock.unlock();
		}
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
			Map<IPeerToken, List<IN>> peersAwaitingRead = null;
			if (selectedKeyCount > 0) {
				peersAwaitingRead = _backgroundProcessSelectedKeys();
			}
			// Check the handoff map.
			IdentityHashMap<IPeerToken, ByteBuffer> buffersToWrite;
			Queue<IPeerToken> peersToDisconnect;
			Set<IPeerToken> readsToResume;
			// We want to notify the listener outside of the lock so we build this list.
			List<IPeerToken> peersToNotify = new ArrayList<>();
			try
			{
				_lock.lock();
				buffersToWrite = _shared_outgoingBuffers;
				_shared_outgoingBuffers = null;
				peersToDisconnect = _shared_disconnectRequests;
				_shared_disconnectRequests = null;
				readsToResume = _shared_resumeReads;
				_shared_resumeReads = null;
				
				if (null != peersAwaitingRead)
				{
					// We want to pass back the packets we read, but we also want to prove that we didn't read from something already awaiting read.
					for (Map.Entry<IPeerToken, List<IN>> elt : peersAwaitingRead.entrySet())
					{
						IPeerToken peer = elt.getKey();
						Assert.assertTrue(!_shared_incomingPackets.containsKey(peer));
						_shared_incomingPackets.put(peer, elt.getValue());
						// We also want to notify that they have data to read.
						peersToNotify.add(peer);
					}
				}
			}
			finally
			{
				_lock.unlock();
			}
			// Notify any readable listeners.
			for (IPeerToken peer : peersToNotify)
			{
				_listener.peerReadyForRead(peer);
			}
			
			// Re-enable reads.
			if (null != readsToResume)
			{
				for (IPeerToken peer : readsToResume)
				{
					_PeerState client = _connectedPeers.get(peer);
					Assert.assertTrue(0 == (client.key.interestOps() & SelectionKey.OP_READ));
					client.key.interestOps(client.key.interestOps() | SelectionKey.OP_READ);
				}
			}
			// First, disconnect anyone we can.
			if (null != peersToDisconnect)
			{
				for (IPeerToken peer : peersToDisconnect)
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
			if (null != buffersToWrite)
			{
				for (Map.Entry<IPeerToken, ByteBuffer> elt : buffersToWrite.entrySet())
				{
					_PeerState client = _connectedPeers.get(elt.getKey());
					// The peer may have disconnected.
					if (null != client)
					{
						// This should already be empty.
						Assert.assertTrue(null == client.outgoing);
						client.outgoing = elt.getValue();
						client.key.interestOps(client.key.interestOps() | SelectionKey.OP_WRITE);
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

	private Map<IPeerToken, List<IN>> _backgroundProcessSelectedKeys()
	{
		Map<IPeerToken, List<IN>> peersWaitingOnPacketRead = new IdentityHashMap<>();
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
				try
				{
					if (key.isReadable()) {
						List<IN> parsedPacketsFromRead = _backgroundProcessReadableKey(key, state);
						// This list is often empty but null means a failure.
						shouldClose = (null == parsedPacketsFromRead);
						// See if this should be communicated back as waiting for packet read.
						if ((null != parsedPacketsFromRead) && !parsedPacketsFromRead.isEmpty())
						{
							peersWaitingOnPacketRead.put(state, parsedPacketsFromRead);
						}
					}
					if (key.isWritable()) {
						boolean didWrite = _backgroundProcessWritableKey(key, state);
						shouldClose = !didWrite;
					}
				}
				catch (Throwable t)
				{
					// If _anything_ went wrong, close the connection.
					shouldClose = true;
				}
				if (shouldClose)
				{
					_backgroundDisconnectClient(state);
				}
			}
		}
		keys.clear();
		return peersWaitingOnPacketRead;
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
		_listener.peerConnected(newClient, newClient.releaseOutgoing());
	}

	private List<IN> _backgroundProcessReadableKey(SelectionKey key, _PeerState state)
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
		
		// We will return non-null if we succeeded in the read and it will contain packets if we should stop reading until consumed.
		List<IN> packets = null;
		if (didRead)
		{
			// See if we can parse the data in the buffer.
			state.incoming.flip();
			
			packets = new ArrayList<>();
			IN packet = _inClass.cast(PacketCodec.parseAndSeekFlippedBuffer(state.incoming));
			while (null != packet)
			{
				packets.add(packet);
				packet = _inClass.cast(PacketCodec.parseAndSeekFlippedBuffer(state.incoming));
			}
			state.incoming.compact();
			
			// We never want to stop reading since the buffer should never fill (we should process the opcode before).
			Assert.assertTrue(state.incoming.hasRemaining());
			
			// If we parsed any packets, stop reading until they are consumed.
			if (!packets.isEmpty())
			{
				state.key.interestOps(state.key.interestOps() & ~SelectionKey.OP_READ);
			}
		}
		return packets;
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
				state.key.interestOps(state.key.interestOps() & ~SelectionKey.OP_WRITE);
				
				// We can also notify the listener that they are ready to write something to the buffer.
				_listener.peerReadyForWrite(state, state.releaseOutgoing());
			}
		}
		return didWrite;
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
		 * @param byteBuffer The buffer which can be used for serializing outgoing data.
		 */
		void peerConnected(IPeerToken token, ByteBuffer byteBuffer);
		/**
		 * Called when a peer has disconnected.
		 * 
		 * @param token The peer's opaque token.
		 */
		void peerDisconnected(IPeerToken token);
		/**
		 * Called when the connection to a peer is ready to receive new messages to send.
		 * 
		 * @param token The peer's opaque token.
		 * @param byteBuffer The buffer which can be used for serializing outgoing data.
		 */
		void peerReadyForWrite(IPeerToken token, ByteBuffer byteBuffer);
		/**
		 * Called when the connection to a peer has messages to read.
		 * 
		 * @param token The peer's opaque token.
		 */
		void peerReadyForRead(IPeerToken token);
	}

	/**
	 * Mostly just an opaque token, but does allow for get/set of user data for caller use.
	 * NOTE:  The internal interface is expected to assert that this user data is never changed once non-null.
	 */
	public static interface IPeerToken
	{
		Object getData();
		void setData(Object userData);
	}

	/**
	 * WARNING:  "outgoing" is actually passed back and forth to the external consumer:  It serializes whatever it wants
	 * into the buffer and passes it over here so that we enter the "writeable" state and can then dump it to the
	 * network, passing it back to the consumer when done.  In this sense, its presence here is the same as needing to
	 * be writeable in the selector (with the exception of initial startup where it is instantiated here, for symmetry,
	 * but quickly sent elsewhere).
	 */
	private static class _PeerState implements IPeerToken
	{
		public final SocketChannel channel;
		public final SelectionKey key;
		public final ByteBuffer incoming;
		public ByteBuffer outgoing;
		private Object _userData;
		
		public _PeerState(SocketChannel channel, SelectionKey key)
		{
			this.channel = channel;
			this.key = key;
			this.incoming = ByteBuffer.allocate(BUFFER_SIZE_BYTES);
			this.outgoing = ByteBuffer.allocate(BUFFER_SIZE_BYTES);
		}
		public ByteBuffer releaseOutgoing()
		{
			this.outgoing.clear();
			ByteBuffer buffer = this.outgoing;
			this.outgoing = null;
			return buffer;
		}
		@Override
		public Object getData()
		{
			return _userData;
		}
		@Override
		public void setData(Object userData)
		{
			Assert.assertTrue(null != userData);
			Assert.assertTrue(null == _userData);
			_userData = userData;
		}
	}
}
