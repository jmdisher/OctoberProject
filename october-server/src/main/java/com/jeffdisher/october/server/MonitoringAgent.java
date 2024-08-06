package com.jeffdisher.october.server;

import java.util.HashSet;
import java.util.Set;

import com.jeffdisher.october.utils.Assert;


/**
 * Used by ConsoleHandler to read out the state of the system for informational commands.
 * Note that these calls to read or change state can come in from any thread so care must be taken to avoid concurrent
 * modifications or other racy errors.
 */
public class MonitoringAgent
{
	private final Set<Integer> _connectedClients = new HashSet<>();
	private volatile TickRunner.Snapshot _lastSnapshot;

	public synchronized void clientConnected(int clientId)
	{
		boolean didAdd = _connectedClients.add(clientId);
		Assert.assertTrue(didAdd);
	}

	public synchronized void clientDisconnected(int clientId)
	{
		boolean didRemove = _connectedClients.remove(clientId);
		Assert.assertTrue(didRemove);
	}

	public void snapshotPublished(TickRunner.Snapshot snapshot)
	{
		_lastSnapshot = snapshot;
	}

	public synchronized Set<Integer> getClientsCopy()
	{
		return new HashSet<>(_connectedClients);
	}

	public TickRunner.Snapshot getLastSnapshot()
	{
		return _lastSnapshot;
	}
}
