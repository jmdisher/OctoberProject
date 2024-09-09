package com.jeffdisher.october.server;

import java.util.HashMap;
import java.util.Map;

import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.net.NetworkLayer;
import com.jeffdisher.october.net.NetworkServer;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.utils.Assert;


/**
 * Used by ConsoleHandler to read out the state of the system for informational commands.
 * Note that these calls to read or change state can come in from any thread so care must be taken to avoid concurrent
 * modifications or other racy errors.
 */
public class MonitoringAgent
{
	private final Map<Integer, String> _connectedClientIds = new HashMap<>();
	private final Map<Integer, NetworkLayer.PeerToken> _connectedClientTokens = new HashMap<>();
	private volatile NetworkServer<?> _network;
	private volatile TickRunner.Snapshot _lastSnapshot;
	private volatile OperatorCommandSink _commandSink;
	private volatile Runnable _configBroadcastRequester;
	private volatile ChatMessageSink _chatSink;

	public void setNetwork(NetworkServer<?> network)
	{
		// This should only be called once (and is ALWAYS called before anything else).
		Assert.assertTrue(null == _network);
		_network = network;
	}

	public synchronized void clientConnected(int clientId, NetworkLayer.PeerToken token, String name)
	{
		String old = _connectedClientIds.put(clientId, name);
		Assert.assertTrue(null == old);
		_connectedClientTokens.put(clientId, token);
	}

	public synchronized void clientDisconnected(int clientId)
	{
		String old = _connectedClientIds.remove(clientId);
		Assert.assertTrue(null != old);
		_connectedClientTokens.remove(clientId);
	}

	public void snapshotPublished(TickRunner.Snapshot snapshot)
	{
		_lastSnapshot = snapshot;
	}

	public NetworkServer<?> getNetwork()
	{
		return _network;
	}

	public void setOperatorCommandSink(OperatorCommandSink sink)
	{
		_commandSink = sink;
	}

	public OperatorCommandSink getCommandSink()
	{
		return _commandSink;
	}

	public void setConfigBroadcastRequester(Runnable configBroadcastRequester)
	{
		_configBroadcastRequester = configBroadcastRequester;
	}

	public void requestConfigBroadcast()
	{
		_configBroadcastRequester.run();
	}

	public void setChatSink(ChatMessageSink chatSink)
	{
		_chatSink = chatSink;
	}

	public synchronized Map<Integer, String> getClientsCopy()
	{
		return new HashMap<>(_connectedClientIds);
	}

	public synchronized NetworkLayer.PeerToken getTokenForClient(int clientId)
	{
		return _connectedClientTokens.get(clientId);
	}

	public TickRunner.Snapshot getLastSnapshot()
	{
		return _lastSnapshot;
	}

	public void sendChatMessage(int targetId, String message)
	{
		Assert.assertTrue(targetId >= 0);
		Assert.assertTrue(null != message);
		
		_chatSink.sendChatMessage(targetId, message);
	}


	public static interface OperatorCommandSink
	{
		void submit(int clientId, IMutationEntity<IMutablePlayerEntity> command);
	}

	public static interface ChatMessageSink
	{
		void sendChatMessage(int targetId, String message);
	}
}
