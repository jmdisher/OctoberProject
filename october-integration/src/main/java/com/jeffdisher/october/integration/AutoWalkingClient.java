package com.jeffdisher.october.integration;

import java.io.IOException;
import java.net.InetSocketAddress;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.mutations.EntityChangeMove;
import com.jeffdisher.october.process.ClientProcess;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.PartialEntity;


/**
 * A testing program which will join a world and walk in a single cardinal direction forever.  This is intended as a
 * network stress test as it is easy to instantiate many of these into the same world.
 * It takes 4 argumens:
 * -hostname
 * -port
 * -client name
 * -direction
 * The program will exit when the server disconnects.
 */
public class AutoWalkingClient
{
	/**
	 * We will report current location every 10 seconds.
	 */
	public static final long REPORT_INTERVAL_MILLIS = 10_000L;

	public static void main(String[] args) throws IOException, InterruptedException
	{
		if (4 == args.length)
		{
			String hostname = args[0];
			int port = Integer.parseInt(args[1]);
			String clientName = args[2];
			EntityChangeMove.Direction direction = EntityChangeMove.Direction.valueOf(args[3]);
			InetSocketAddress serverAddress = new InetSocketAddress(hostname, port);
			_runClient(serverAddress, clientName, direction);
		}
		else
		{
			System.out.println("Usage:  <hostname> <port> <client_name> <direction>");
			System.exit(1);
		}
	}


	private static void _runClient(InetSocketAddress serverAddress, String clientName, EntityChangeMove.Direction direction) throws IOException, InterruptedException
	{
		Environment.createSharedInstance();
		_Listener listener = new _Listener();
		ClientProcess client = new ClientProcess(listener, serverAddress.getAddress(), serverAddress.getPort(), clientName);
		client.waitForLocalEntity(System.currentTimeMillis());
		
		long lastReport = 0L;
		while (listener.isConnected)
		{
			Thread.sleep(100L);
			long currentTimeMillis = System.currentTimeMillis();
			client.moveHorizontalFully(direction, currentTimeMillis);
			if (currentTimeMillis >= (lastReport + REPORT_INTERVAL_MILLIS))
			{
				System.out.println("Location: " + listener.thisEntity.location());
				lastReport = currentTimeMillis;
			}
		}
		client.disconnect();
	}


	private static class _Listener implements ClientProcess.IListener
	{
		public boolean isConnected;
		public Entity thisEntity;
		
		@Override
		public void connectionEstablished(int assignedEntityId)
		{
			System.out.println("Connected as " + assignedEntityId);
			this.isConnected = true;
		}
		@Override
		public void connectionClosed()
		{
			System.out.println("Connection closed");
			this.isConnected = false;
		}
		@Override
		public void cuboidDidLoad(IReadOnlyCuboidData cuboid, ColumnHeightMap heightMap)
		{
		}
		@Override
		public void cuboidDidChange(IReadOnlyCuboidData cuboid, ColumnHeightMap heightMap)
		{
		}
		@Override
		public void cuboidDidUnload(CuboidAddress address)
		{
		}
		@Override
		public void thisEntityDidLoad(Entity authoritativeEntity)
		{
		}
		@Override
		public void thisEntityDidChange(Entity authoritativeEntity, Entity projectedEntity)
		{
			this.thisEntity = projectedEntity;
		}
		@Override
		public void otherEntityDidLoad(PartialEntity entity)
		{
			System.out.println(">Entity " + entity.id() + " -- " + entity.type());
		}
		@Override
		public void otherEntityDidChange(PartialEntity entity)
		{
		}
		@Override
		public void otherEntityDidUnload(int id)
		{
			System.out.println("<Entity " + id);
		}
		@Override
		public void tickDidComplete(long gameTick)
		{
		}
		@Override
		public void configUpdated(int ticksPerDay, int dayStartTick)
		{
		}
		@Override
		public void otherClientLeft(int clientId)
		{
		}
		@Override
		public void otherClientJoined(int clientId, String name)
		{
		}
	}
}
