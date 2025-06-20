package com.jeffdisher.october.integration;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Set;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.OrientationHelpers;
import com.jeffdisher.october.mutations.EntityChangeMove;
import com.jeffdisher.october.mutations.EntityChangeTopLevelMovement;
import com.jeffdisher.october.mutations.MutationEntitySelectItem;
import com.jeffdisher.october.mutations.MutationPlaceSelectedBlock;
import com.jeffdisher.october.process.ClientProcess;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.utils.Assert;


/**
 * A testing program which joins a world and will then wait for a message from the server to tell it what to do.  This
 * command takes the form "ACTION DIRECTION":
 * -ACTION is one of "WALK", "BREAK", "BRICK", or "LANTERN".
 * -DIRECTION is one of the cardinal directions
 * Once receiving this command, the client will begin walking in that direction and potentially breaking or placing
 * blocks behind it, as it moves.
 * Note that it assumes that it has been put into creative mode if breaking or placing, since it needs to immediately
 * break a block and have an infinite amount of stone brick blocks, if placing.
 * 
 * Starting the program takes 3 arguments:
 * -hostname
 * -port
 * -client name
 * 
 * The program will exit when the server disconnects.
 * 
 * The point of this is to act as a stress test as multiple instances of this client can be connected and they will all
 * move and potentially interact with the world as they go, allowing insight into server scalability.
 */
public class AutoWalkingClient
{
	/**
	 * We will report current location every 10 seconds.
	 */
	public static final long REPORT_INTERVAL_MILLIS = 10_000L;

	public static void main(String[] args) throws IOException, InterruptedException, ClientProcess.DisconnectException
	{
		if (3 == args.length)
		{
			String hostname = args[0];
			int port = Integer.parseInt(args[1]);
			String clientName = args[2];
			InetSocketAddress serverAddress = new InetSocketAddress(hostname, port);
			
			_Packaged packaged = _connectAndWaitForCommand(serverAddress, clientName);
			if (null != packaged)
			{
				_runClient(packaged.listener, packaged.client, packaged.command);
			}
		}
		else
		{
			System.out.println("Usage:  <hostname> <port> <client_name>");
			System.exit(1);
		}
	}


	private static _Packaged _connectAndWaitForCommand(InetSocketAddress serverAddress, String clientName) throws IOException, InterruptedException, ClientProcess.DisconnectException
	{
		Environment.createSharedInstance();
		_Listener listener = new _Listener();
		ClientProcess client = new ClientProcess(listener, serverAddress.getAddress(), serverAddress.getPort(), clientName);
		client.waitForLocalEntity(System.currentTimeMillis());
		
		// Wait until we get a command.
		System.out.println("Connected to server as " + listener.thisAssignedId + ".  Remember to set creative mode: \"!set_mode " + listener.thisAssignedId + " CREATIVE\"");
		System.out.println("Waiting for server command of the flavour: \"!message " + listener.thisAssignedId + " ACTION DIRECTION\"...");
		_Command command = null;
		while ((null == command) && listener.isConnected)
		{
			Thread.sleep(100L);
			client.runPendingCalls(System.currentTimeMillis());
			if (null != listener.lastCommandFromServer)
			{
				command = _parseCommand(listener.lastCommandFromServer);
			}
		}
		// If we didn't get a command, just disconnect.
		if (null == command)
		{
			client.disconnect();
		}
		return (null != command)
				? new _Packaged(listener, client, command)
				: null
		;
	}

	private static void _runClient(_Listener listener, ClientProcess client, _Command command) throws InterruptedException
	{
		// We can now create the environment.
		Environment env = Environment.getShared();
		
		// Select the block (note that this assumes that we are in creative mode).
		Block stoneBrick = env.blocks.getAsPlaceableBlock(env.items.getItemById("op.stone_brick"));
		Block lantern = env.blocks.getAsPlaceableBlock(env.items.getItemById("op.lantern"));
		Item toSelect = (_Action.LANTERN == command.action)
				? lantern.item()
				: stoneBrick.item()
		;
		MutationEntitySelectItem select = new MutationEntitySelectItem(toSelect.number());
		client.sendAction(select, System.currentTimeMillis());
		
		// We can now run the loop.
		System.out.println("Running command " + command.action + " " + command.direction);
		long lastReport = 0L;
		AbsoluteLocation lastBlock = listener.thisEntity.location().getBlockLocation();
		while (listener.isConnected)
		{
			Thread.sleep(100L);
			long currentTimeMillis = System.currentTimeMillis();
			if (currentTimeMillis >= (lastReport + REPORT_INTERVAL_MILLIS))
			{
				System.out.println("Location: " + listener.thisEntity.location());
				lastReport = currentTimeMillis;
			}
			if (_isOutsideOfBlock(env, lastBlock, listener.thisEntity))
			{
				// Take special action.
				switch (command.action)
				{
				case WALK:
					// Do nothing special.
					break;
				case BREAK:
					client.hitBlock(lastBlock.getRelative(0, 0, -1), currentTimeMillis);
					break;
				case BRICK:
				case LANTERN:
					client.sendAction(new MutationPlaceSelectedBlock(lastBlock, lastBlock), currentTimeMillis);
					break;
					default:
						throw Assert.unreachable();
				}
				lastBlock = listener.thisEntity.location().getBlockLocation();
			}
			else
			{
				byte yaw;
				switch (command.direction)
				{
				case EAST:
					yaw = OrientationHelpers.YAW_EAST;
					break;
				case NORTH:
					yaw = OrientationHelpers.YAW_NORTH;
					break;
				case SOUTH:
					yaw = OrientationHelpers.YAW_SOUTH;
					break;
				case WEST:
					yaw = OrientationHelpers.YAW_WEST;
					break;
				default:
					throw Assert.unreachable();
				}
				client.setOrientation(yaw, OrientationHelpers.PITCH_FLAT);
				client.moveHorizontal(EntityChangeTopLevelMovement.Relative.FORWARD, currentTimeMillis);
			}
		}
		client.disconnect();
	}

	private static _Command _parseCommand(String commandText)
	{
		String[] parts = commandText.split(" ");
		// We only expect the 2 strings: "ACTION DIRECTION"
		Assert.assertTrue(2 == parts.length);
		_Action action = _Action.valueOf(parts[0]);
		Assert.assertTrue(null != action);
		EntityChangeMove.Direction direction = EntityChangeMove.Direction.valueOf(parts[1]);
		Assert.assertTrue(null != direction);
		return new _Command(action, direction);
	}

	private static boolean _isOutsideOfBlock(Environment env, AbsoluteLocation block, Entity entity)
	{
		EntityLocation base = entity.location();
		EntityVolume volume = env.creatures.PLAYER.volume();
		EntityLocation edge = new EntityLocation(base.x() + volume.width(), base.y() + volume.width(), base.z() + volume.height());
		
		// We will say that we are out of this block if neither the base nor the edge are within this block (note that this could fail if the volume is larger than 1 block in any direction).
		AbsoluteLocation baseBlock = base.getBlockLocation();
		AbsoluteLocation edgeBlock = edge.getBlockLocation();
		return !block.equals(baseBlock) && !block.equals(edgeBlock);
	}


	private static class _Listener implements ClientProcess.IListener
	{
		public int thisAssignedId;
		public boolean isConnected;
		public Entity thisEntity;
		public String lastCommandFromServer;
		
		@Override
		public void connectionEstablished(int assignedEntityId)
		{
			System.out.println("Connected as " + assignedEntityId);
			this.thisAssignedId = assignedEntityId;
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
		public void cuboidDidChange(IReadOnlyCuboidData cuboid
				, ColumnHeightMap heightMap
				, Set<BlockAddress> changedBlocks
				, Set<Aspect<?, ?>> changedAspects
		)
		{
			Assert.assertTrue(!changedBlocks.isEmpty());
			Assert.assertTrue(!changedAspects.isEmpty());
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
		public void handleEvent(EventRecord event)
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
		@Override
		public void receivedChatMessage(int senderId, String message)
		{
			if (senderId != this.thisAssignedId)
			{
				System.out.println("Chat from " + senderId + ": " + message);
			}
			if (0 == senderId)
			{
				this.lastCommandFromServer = message;
			}
		}
	}

	private static enum _Action
	{
		WALK,
		BREAK,
		BRICK,
		LANTERN,
	}

	private static record _Command(_Action action
			, EntityChangeMove.Direction direction
	) {}

	private static record _Packaged(_Listener listener
			, ClientProcess client
			, _Command command
	) {}
}
