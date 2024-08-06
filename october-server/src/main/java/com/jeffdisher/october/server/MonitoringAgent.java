package com.jeffdisher.october.server;

import java.util.HashMap;
import java.util.Map;

import com.jeffdisher.october.net.NetworkLayer;
import com.jeffdisher.october.utils.Assert;


/**
 * Used by ConsoleHandler to read out the state of the system for informational commands.
 * Note that these calls to read or change state can come in from any thread so care must be taken to avoid concurrent
 * modifications or other racy errors.
 */
public class MonitoringAgent
{
	private final Map<Integer, String> _connectedClients = new HashMap<>();
	private volatile TickRunner.Snapshot _lastSnapshot;

	public synchronized void clientConnected(int clientId, NetworkLayer.PeerToken token, String name)
	{
		String old = _connectedClients.put(clientId, name);
		Assert.assertTrue(null == old);
	}

	public synchronized void clientDisconnected(int clientId)
	{
		String old = _connectedClients.remove(clientId);
		Assert.assertTrue(null != old);
	}

	public void snapshotPublished(TickRunner.Snapshot snapshot)
	{
		_lastSnapshot = snapshot;
	}

	public synchronized Map<Integer, String> getClientsCopy()
	{
		return new HashMap<>(_connectedClients);
	}

	public TickRunner.Snapshot getLastSnapshot()
	{
		return _lastSnapshot;
	}
}
