package com.jeffdisher.october.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.List;

import com.jeffdisher.october.utils.Assert;
import com.jeffdisher.october.utils.MessageQueue;


/**
 * A wrapper over NetworkLayer client instances in order to support the specific server-polling interface used by the
 * client.
 */
public class PollingClient
{
	private final IListener _listener;
	private final MessageQueue _queue;
	private final Thread _thread;

	public PollingClient(IListener listener)
	{
		_listener = listener;
		_queue = new MessageQueue();
		_thread = new Thread(() -> {
			_backgroundThreadMain();
		}, "Polling Client");
		_thread.start();
	}

	public void pollServer(InetSocketAddress serverToPoll)
	{
		_queue.enqueue(() -> {
			_backgroundPollServer(serverToPoll);
		});
	}

	/**
	 * Waits for all the background operations to stop.
	 */
	public void shutdown()
	{
		_queue.waitForEmptyQueue();
		_queue.shutdown();
		try
		{
			_thread.join();
		}
		catch (InterruptedException e)
		{
			// We don't use interruption.
			throw Assert.unexpected(e);
		}
	}


	private void _backgroundThreadMain()
	{
		Runnable r = _queue.pollForNext(0, null);
		while (null != r)
		{
			r.run();
			r = _queue.pollForNext(0, null);
		}
	}

	private void _backgroundPollServer(InetSocketAddress serverToPoll)
	{
		try
		{
			// Internally, this will block and/or use the message queue to request further operations.
			new _OnePoll(serverToPoll);
		}
		catch (IOException e)
		{
			_listener.networkTimeout(serverToPoll);
		}
	}


	private class _OnePoll
	{
		private NetworkLayer.IPeerToken _token;
		private ByteBuffer _writeableBuffer;
		private final NetworkLayer<PacketFromServer> _network;
		private _OnePoll(InetSocketAddress serverToPoll) throws IOException
		{
			_network = NetworkLayer.connectToServer(new NetworkLayer.IListener()
			{
				@Override
				public void peerConnected(NetworkLayer.IPeerToken token, ByteBuffer byteBuffer)
				{
					// Since this is client mode, this is called before the connectToServer returns.
					Assert.assertTrue(null == _token);
					Assert.assertTrue(null == _writeableBuffer);
					_token = token;
					_writeableBuffer = byteBuffer;
				}
				@Override
				public void peerDisconnected(NetworkLayer.IPeerToken token)
				{
					_queue.enqueue(() -> {
						_network.stop();
					});
				}
				@Override
				public void peerReadyForWrite(NetworkLayer.IPeerToken token, ByteBuffer byteBuffer)
				{
					// We only start with the one write.
				}
				@Override
				public void peerReadyForRead(NetworkLayer.IPeerToken token)
				{
					long endMillis = System.currentTimeMillis();
					_queue.enqueue(() -> {
						List<PacketFromServer> packets = _network.receiveMessages(token);
						Assert.assertTrue(1 == packets.size());
						Packet_ServerReturnServerStatus safe = (Packet_ServerReturnServerStatus) packets.get(0);
						long deltaMillis = endMillis - safe.millis;
						_listener.serverReturnedStatus(serverToPoll, safe.version, safe.serverName, safe.clientCount, deltaMillis);
						_network.stop();
					});
				}
			}, serverToPoll.getAddress(), serverToPoll.getPort());
			
			// We expect that the token will be set before connectToServer returns.
			Assert.assertTrue(null != _token);
			Assert.assertTrue(null != _writeableBuffer);
			
			// The connection starts writable so kick-off the handshake.
			long startMillis = System.currentTimeMillis();
			PacketCodec.serializeToBuffer(_writeableBuffer, new Packet_ClientPollServerStatus(Packet_ClientPollServerStatus.NETWORK_POLL_VERSION, startMillis));
			_writeableBuffer.flip();
			_network.sendBuffer(_token, _writeableBuffer);
		}
	}

	/**
	 * The interface for listening to events from inside the client.  Note that all calls will be issued on the internal
	 * thread so they must return soon in order to avoid slowing the system.
	 */
	public static interface IListener
	{
		void serverReturnedStatus(InetSocketAddress serverToPoll, int version, String serverName, int clientCount, long millisDelay);
		void networkTimeout(InetSocketAddress serverToPoll);
	}
}
