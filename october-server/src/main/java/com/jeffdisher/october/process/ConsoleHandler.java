package com.jeffdisher.october.process;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.jeffdisher.october.logic.PropagationHelpers;
import com.jeffdisher.october.mutations.EntityChangeOperatorSetCreative;
import com.jeffdisher.october.mutations.EntityChangeOperatorSetLocation;
import com.jeffdisher.october.net.NetworkLayer;
import com.jeffdisher.october.net.NetworkServer;
import com.jeffdisher.october.server.MonitoringAgent;
import com.jeffdisher.october.server.TickRunner;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Difficulty;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.WorldConfig;
import com.jeffdisher.october.utils.Assert;


/**
 * Handles the server's stdin, processing commands from it.
 */
public class ConsoleHandler
{
	/**
	 * Processes commands (on the calling thread) until a shutdown command is received.
	 * 
	 * @param in The input stream.
	 * @param out The output stream.
	 * @param monitoringAgent The shared agent structure which collects information from the rest of the system.
	 * @param mutableSharedConfig The shared config object used by the system (changes will immediately take effect).
	 * @throws IOException If there was an error reading the input.
	 */
	public static void readUntilStop(InputStream in
			, PrintStream out
			, MonitoringAgent monitoringAgent
			, WorldConfig mutableSharedConfig
	) throws IOException
	{
		// We will read lines until we get a stop command.
		BufferedReader reader = new BufferedReader(new InputStreamReader(in));
		_ConsoleState state = new _ConsoleState(monitoringAgent, mutableSharedConfig);
		while (state.canContinue)
		{
			_readAndProcessOneLine(out, reader, state);
		}
		out.println("Shutting down...");
	}

	/**
	 * Processes commands (on the calling thread) until a shutdown command is received or the calling thread is
	 * interrupted.
	 * Note that this is path has a 10ms latency on processing commands, compared to the non-interruptable version, so
	 * it should only be used when needed, such as when reading stdin (as this read cannot be interrupted in a portable
	 * way).
	 * 
	 * @param in The input stream.
	 * @param out The output stream.
	 * @param monitoringAgent The shared agent structure which collects information from the rest of the system.
	 * @param mutableSharedConfig The shared config object used by the system (changes will immediately take effect).
	 * @throws IOException If there was an error reading the input.
	 * @throws InterruptedException If this thread was interrupted.
	 */
	public static void readUntilStopInterruptable(InputStream in
			, PrintStream out
			, MonitoringAgent monitoringAgent
			, WorldConfig mutableSharedConfig
	) throws IOException, InterruptedException
	{
		// We will read lines until we get a stop command.
		BufferedReader reader = new BufferedReader(new InputStreamReader(in));
		_ConsoleState state = new _ConsoleState(monitoringAgent, mutableSharedConfig);
		while (state.canContinue)
		{
			while (0 == in.available())
			{
				Thread.sleep(10L);
			}
			_readAndProcessOneLine(out, reader, state);
		}
		out.println("Shutting down...");
	}


	private static void _readAndProcessOneLine(PrintStream out, BufferedReader reader, _ConsoleState state) throws IOException
	{
		String line = reader.readLine();
		String[] fragments = line.split(" ");
		String first = fragments[0];
		if (first.startsWith("!"))
		{
			String name = first.substring(1);
			
			// Drop any empty string fragments.
			List<String> nonEmpty = new ArrayList<>();
			for (int i = 1; i < fragments.length; ++i)
			{
				String fragment = fragments[i];
				if (fragment.length() > 0)
				{
					nonEmpty.add(fragment);
				}
			}
			String[] params = nonEmpty.toArray((int size) -> new String[size]);
			
			try
			{
				_Command command = _Command.valueOf(name.toUpperCase());
				command.handler.run(out, state, params);
			}
			catch (IllegalArgumentException e)
			{
				out.println("Command \"" + name + "\" unknown");
				_usage(out);
			}
		}
		else
		{
			_usage(out);
		}
	}

	private static void _usage(PrintStream out)
	{
		out.println("Run !help for commands");
	}


	private static class _ConsoleState
	{
		public boolean canContinue = true;
		public final MonitoringAgent monitoringAgent;
		public final WorldConfig mutableSharedConfig;
		public _ConsoleState(MonitoringAgent monitoringAgent, WorldConfig mutableSharedConfig)
		{
			this.monitoringAgent = monitoringAgent;
			this.mutableSharedConfig = mutableSharedConfig;
		}
	}

	private static interface _CommandHandler
	{
		void run(PrintStream out, _ConsoleState state, String[] parameters);
	}

	private static enum _Command
	{
		HELP((PrintStream out, _ConsoleState state, String[] parameters) -> {
			out.println("Commands:");
			for (_Command command : _Command.values())
			{
				out.println("!" + command.name());
			}
		}),
		STOP((PrintStream out, _ConsoleState state, String[] parameters) -> {
			state.canContinue = false;
		}),
		LIST_CLIENTS((PrintStream out, _ConsoleState state, String[] parameters) -> {
			Map<Integer, String> clientIds = state.monitoringAgent.getClientsCopy();
			out.println("Connected clients (" + clientIds.size() + "):");
			for (Map.Entry<Integer, String> elt : clientIds.entrySet())
			{
				out.println("\t" + elt.getKey() + " - " + elt.getValue());
			}
		}),
		LAST_SNAPSHOT((PrintStream out, _ConsoleState state, String[] parameters) -> {
			MonitoringAgent monitoringAgent = state.monitoringAgent;
			TickRunner.Snapshot snapshot = monitoringAgent.getLastSnapshot();
			long tickNumber = snapshot.tickNumber();
			TickRunner.TickStats stats = snapshot.stats();
			long processMillis = stats.millisTickPreamble() + stats.millisTickParallelPhase() + stats.millisTickPostamble();
			int entityCount = snapshot.completedEntities().size();
			int cuboidCount = snapshot.completedCuboids().size();
			int creatureCount = snapshot.completedCreatures().size();
			out.printf("Tick %d processed in %d ms:\n", tickNumber, processMillis);
			out.println("\tEntities: " + entityCount);
			out.println("\tCuboids: " + cuboidCount);
			out.println("\tCreatures: " + creatureCount);
		}),
		DISCONNECT((PrintStream out, _ConsoleState state, String[] parameters) -> {
			if (parameters.length > 0)
			{
				MonitoringAgent monitoringAgent = state.monitoringAgent;
				NetworkServer<?> network = monitoringAgent.getNetwork();
				for (String param : parameters)
				{
					int clientId = _readInt(parameters[0], -1);
					NetworkLayer.PeerToken token = (clientId > 0)
							? monitoringAgent.getTokenForClient(clientId)
							: null
					;
					
					if (null != token)
					{
						network.disconnectClient(token);
					}
					else
					{
						out.println("Error: \"" + param + "\" is not a valid ID");
					}
				}
			}
			else
			{
				out.println("You must specify at least 1 ID");
			}
		}),
		SET_MODE((PrintStream out, _ConsoleState state, String[] parameters) -> {
			// We expect <client_id> <creative/survival>.
			if (2 == parameters.length)
			{
				int clientId = _readInt(parameters[0], -1);
				String mode = parameters[1].toUpperCase();
				boolean setCreative;
				if ("CREATIVE".equals(mode))
				{
					setCreative = true;
				}
				else if ("SURVIVAL".equals(mode))
				{
					setCreative = false;
				}
				else
				{
					clientId = -1;
					setCreative = false;
				}
				
				if (clientId > 0)
				{
					// Pass in the operator command.
					MonitoringAgent monitoringAgent = state.monitoringAgent;
					EntityChangeOperatorSetCreative command = new EntityChangeOperatorSetCreative(setCreative);
					monitoringAgent.getCommandSink().submitEntityMutation(clientId, command);
				}
				else
				{
					out.println("Usage:  <client_id> <CREATIVE/SURVIVAL>");
				}
			}
			else
			{
				out.println("Usage:  <client_id> <CREATIVE/SURVIVAL>");
			}
		}),
		SET_DIFFICULTY((PrintStream out, _ConsoleState state, String[] parameters) -> {
			// We expect <peaceful/hostile>.
			if (1 == parameters.length)
			{
				String mode = parameters[0].toUpperCase();
				Difficulty target;
				try
				{
					target = Difficulty.valueOf(mode);
				}
				catch (IllegalArgumentException e)
				{
					target = Difficulty.ERROR;
				}
				if (Difficulty.ERROR != target)
				{
					state.mutableSharedConfig.difficulty = target;
				}
				else
				{
					out.println("Usage:  <peaceful/hostile>");
				}
			}
			else
			{
				out.println("Usage:  <peaceful/hostile>");
			}
		}),
		TP((PrintStream out, _ConsoleState state, String[] parameters) -> {
			// We expect <client_id> <x> <y> <z>.
			if (4 == parameters.length)
			{
				int clientId = _readInt(parameters[0], -1);
				int x = _readInt(parameters[1], Integer.MIN_VALUE);
				int y = _readInt(parameters[2], Integer.MIN_VALUE);
				int z = _readInt(parameters[3], Integer.MIN_VALUE);
				
				if ((-1 != clientId)
						&& (Integer.MIN_VALUE != x)
						&& (Integer.MIN_VALUE != y)
						&& (Integer.MIN_VALUE != z)
				)
				{
					// Pass in the operator command.
					MonitoringAgent monitoringAgent = state.monitoringAgent;
					EntityLocation location = new AbsoluteLocation(x, y, z).toEntityLocation();
					EntityChangeOperatorSetLocation command = new EntityChangeOperatorSetLocation(location);
					monitoringAgent.getCommandSink().submitEntityMutation(clientId, command);
				}
				else
				{
					out.println("Usage:  <client_id> <x> <y> <z>");
				}
			}
			else
			{
				out.println("Usage:  <client_id> <x> <y> <z>");
			}
		}),
		SET_DAY_LENGTH((PrintStream out, _ConsoleState state, String[] parameters) -> {
			// We expect <tick_count>.
			if (1 == parameters.length)
			{
				int ticksPerDay = _readInt(parameters[0], Integer.MIN_VALUE);
				if (ticksPerDay > 0)
				{
					// Update the config and broadcast the change to the clients.
					state.mutableSharedConfig.ticksPerDay = ticksPerDay;
					state.monitoringAgent.getCommandSink().requestConfigBroadcast();
				}
				else
				{
					out.println("Usage:  <tick_count>");
				}
			}
			else
			{
				out.println("Usage:  <tick_count>");
			}
		}),
		RESET_DAY((PrintStream out, _ConsoleState state, String[] parameters) -> {
			// We expect no parameters.
			if (0 == parameters.length)
			{
				// We will reset the day start time based on the current tick value and broadcast the change.
				long currentGameTick = state.monitoringAgent.getLastSnapshot().tickNumber();
				state.mutableSharedConfig.dayStartTick = (int)PropagationHelpers.resumableStartTick(currentGameTick, state.mutableSharedConfig.ticksPerDay, state.mutableSharedConfig.dayStartTick);
				state.monitoringAgent.getCommandSink().requestConfigBroadcast();
			}
			else
			{
				out.println("Error:  No parameters expected");
			}
		}),
		MESSAGE((PrintStream out, _ConsoleState state, String[] parameters) -> {
			// We treat the first parameter as the target ID number but the rest are just joined with a space and sent.
			if (parameters.length >= 2)
			{
				int targetId = Integer.parseInt(parameters[0]);
				String name = state.monitoringAgent.getClientsCopy().get(targetId);
				String message = _joinList(1, parameters);
				// The actual message must not be empty.
				if (!message.isEmpty() && (null != name))
				{
					state.monitoringAgent.getCommandSink().sendChatMessage(targetId, message);
					out.println("Message to " + name + ": "+ message);
				}
				else
				{
					out.println("Usage:  <target_id> message...");
				}
			}
			else
			{
				out.println("Usage:  <target_id> message...");
			}
		}),
		BROADCAST((PrintStream out, _ConsoleState state, String[] parameters) -> {
			// Just join the parameters with spaces and send them.
			String message = _joinList(0, parameters);
			// The actual message must not be empty.
			if (!message.isEmpty())
			{
				state.monitoringAgent.getCommandSink().sendChatMessage(0, message);
				out.println("Broadcast: "+ message);
			}
			else
			{
				out.println("Usage:  message...");
			}
		}),
		SAMPLE((PrintStream out, _ConsoleState state, String[] parameters) -> {
			out.println("Collecting sample for " + MonitoringAgent.Sampler.TICK_SAMPLE_SIZE + " ticks...");
			MonitoringAgent.Sampler sampler = new MonitoringAgent.Sampler();
			state.monitoringAgent.getCommandSink().installSampler(sampler);
			try
			{
				sampler.waitForSample();
			}
			catch (InterruptedException e)
			{
				// This is not expected in our system.
				throw Assert.unexpected(e);
			}
			sampler.logToStream(out);
		}),
		;
		
		public final _CommandHandler handler;
		
		private _Command(_CommandHandler handler)
		{
			this.handler = handler;
		}
		
		private static int _readInt(String value, int defaultValue)
		{
			int read = defaultValue;
			try
			{
				read = Integer.parseInt(value);
			}
			catch (IllegalArgumentException e)
			{
				// Not a valid int.
			}
			return read;
		}
		
		private static String _joinList(int start, String[] list)
		{
			String message = list[start];
			for (int i = (start + 1); i < list.length; ++i)
			{
				message += " " + list[i];
			}
			return message;
		}
	}
}
