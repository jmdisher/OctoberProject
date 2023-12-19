package com.jeffdisher.october.integration;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;

import com.jeffdisher.october.changes.IEntityChange;
import com.jeffdisher.october.client.IClientAdapter;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.mutations.IMutation;
import com.jeffdisher.october.server.IServerAdapter;
import com.jeffdisher.october.server.ServerRunner;
import com.jeffdisher.october.types.Entity;


/**
 * Used by integration tests which want to connect clients to a server.
 * All calls from one are issued directly through to the other (since their listener interfaces assume the calling
 * thread is borrowed and should return without doing heavy work).
 */
public class ConnectionFabric implements IServerAdapter
{
	public int nextClientId = 1;

	private IServerAdapter.IListener _server;
	private Map<Integer, IClientAdapter.IListener> _clients = new HashMap<>();
	private Map<Integer, Long> _tickObservedByClient = new HashMap<>(Map.of(ServerRunner.FAKE_CLIENT_ID, 0L));

	// ---------------------------------- Server adapter.
	@Override
	public synchronized void readyAndStartListening(IServerAdapter.IListener listener)
	{
		Assert.assertNull(_server);
		_server = listener;
		this.notifyAll();
	}

	@Override
	public synchronized void sendEntity(int clientId, Entity entity)
	{
		_clients.get(clientId).receivedEntity(entity);
	}

	@Override
	public synchronized void sendCuboid(int clientId, IReadOnlyCuboidData cuboid)
	{
		_clients.get(clientId).receivedCuboid(cuboid);
	}

	@Override
	public synchronized void sendChange(int clientId, int entityId, IEntityChange change)
	{
		_clients.get(clientId).receivedChange(entityId, change);
	}

	@Override
	public synchronized void sendMutation(int clientId, IMutation mutation)
	{
		_clients.get(clientId).receivedMutation(mutation);
	}

	@Override
	public synchronized void sendEndOfTick(int clientId, long tickNumber, long latestLocalCommitIncluded)
	{
		if (ServerRunner.FAKE_CLIENT_ID != clientId)
		{
			_clients.get(clientId).receivedEndOfTick(tickNumber, latestLocalCommitIncluded);
		}
		_tickObservedByClient.put(clientId, tickNumber);
		this.notifyAll();
	}

	@Override
	public synchronized void disconnectClient(int clientId)
	{
		_clients.remove(clientId);
	}

	public synchronized int connectAndStartListening(IClientAdapter.IListener listener)
	{
		int thisClient = this.nextClientId;
		this.nextClientId += 1;
		
		_clients.put(thisClient, listener);
		listener.adapterConnected(thisClient);
		_tickObservedByClient.put(thisClient, 0L);
		_server.clientConnected(thisClient);
		this.notifyAll();
		return thisClient;
	}

	public synchronized void disconnect(int clientId)
	{
	}

	public synchronized void sendChange(int clientId, IEntityChange change, long commitLevel)
	{
		_server.changeReceived(clientId, change, commitLevel);
	}

	public synchronized void waitForServer() throws InterruptedException
	{
		while (null == _server)
		{
			this.wait();
		}
	}

	public synchronized void waitForClient(int clientId) throws InterruptedException
	{
		while(!_clients.containsKey(clientId))
		{
			this.wait();
		}
	}

	public synchronized long getLatestTick(int clientId) throws InterruptedException
	{
		return _tickObservedByClient.get(clientId);
	}

	public synchronized void waitForTick(int clientId, long tickNumber) throws InterruptedException
	{
		while(_tickObservedByClient.get(clientId) < tickNumber)
		{
			this.wait();
		}
	}

	/**
	 * A factory method to create a new client bound to the server on the receiver.
	 * 
	 * @return A new per-client adapter.
	 */
	public PerClient newClient()
	{
		return new PerClient();
	}


	/**
	 * The per-client adapter created when connecting a new client to this test network.
	 */
	public class PerClient implements IClientAdapter
	{
		public int clientId = 0;
		
		@Override
		public void connectAndStartListening(IListener listener)
		{
			this.clientId = ConnectionFabric.this.connectAndStartListening(listener);
		}
		@Override
		public void disconnect()
		{
			ConnectionFabric.this.disconnect(this.clientId);
		}
		@Override
		public void sendChange(IEntityChange change, long commitLevel)
		{
			ConnectionFabric.this.sendChange(this.clientId, change, commitLevel);
		}
	}
}
