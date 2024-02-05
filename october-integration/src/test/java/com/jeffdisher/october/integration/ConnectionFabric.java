package com.jeffdisher.october.integration;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

import org.junit.Assert;

import com.jeffdisher.october.client.IClientAdapter;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.process.ClientProcess;
import com.jeffdisher.october.process.ServerProcess;
import com.jeffdisher.october.server.IServerAdapter;
import com.jeffdisher.october.server.ServerRunner;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;


/**
 * Used by integration tests which want to connect clients to a server.
 * Internally, creates a server and starts clients on request.
 */
public class ConnectionFabric
{
	public static final int PORT = 5678;

	private final LongSupplier _currentTimeMillis;
	private final ServerProcess _server;

	public ConnectionFabric(LongSupplier currentTimeMillis) throws IOException
	{
		_currentTimeMillis = currentTimeMillis;
		_server = new ServerProcess(PORT, ServerRunner.DEFAULT_MILLIS_PER_TICK, _currentTimeMillis);
	}

	public ServerProcess server()
	{
		return _server;
	}

	public ClientProcess newClient(ClientProcess.IListener listener) throws Exception
	{
		ClientProcess client = new ClientProcess(listener, InetAddress.getLocalHost(), PORT, "client");
		client.waitForLocalEntity(_currentTimeMillis.getAsLong());
		return client;
	}
}
