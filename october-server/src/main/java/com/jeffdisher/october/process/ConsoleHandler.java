package com.jeffdisher.october.process;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.Map;

import com.jeffdisher.october.server.MonitoringAgent;
import com.jeffdisher.october.server.TickRunner;


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
	 * @throws IOException If there was an error reading the input.
	 */
	public static void readUntilStop(InputStream in, PrintStream out, MonitoringAgent monitoringAgent) throws IOException
	{
		// We will read lines until we get a stop command.
		BufferedReader reader = new BufferedReader(new InputStreamReader(in));
		_ConsoleState state = new _ConsoleState(monitoringAgent);
		while (state.canContinue)
		{
			String line = reader.readLine();
			String[] fragments = line.split(" ");
			String first = fragments[0];
			if (first.startsWith("!"))
			{
				String name = first.substring(1);
				try
				{
					_Command command = _Command.valueOf(name.toUpperCase());
					command.handler.run(out, state);
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
		out.println("Shutting down...");
	}

	private static void _usage(PrintStream out)
	{
		out.println("Run !help for commands");
	}


	private static class _ConsoleState
	{
		public boolean canContinue = true;
		public final MonitoringAgent monitoringAgent;
		public _ConsoleState(MonitoringAgent monitoringAgent)
		{
			this.monitoringAgent = monitoringAgent;
		}
	}

	private static interface _CommandHandler
	{
		void run(PrintStream out, _ConsoleState state);
	}

	private static enum _Command
	{
		HELP((PrintStream out, _ConsoleState state) -> {
			out.println("Commands:");
			for (_Command command : _Command.values())
			{
				out.println("!" + command.name());
			}
		}),
		STOP((PrintStream out, _ConsoleState state) -> {
			state.canContinue = false;
		}),
		LIST_CLIENTS((PrintStream out, _ConsoleState state) -> {
			Map<Integer, String> clientIds = state.monitoringAgent.getClientsCopy();
			out.println("Connected clients (" + clientIds.size() + "):");
			for (Map.Entry<Integer, String> elt : clientIds.entrySet())
			{
				out.println("\t" + elt.getKey() + " - " + elt.getValue());
			}
		}),
		LAST_SNAPSHOT((PrintStream out, _ConsoleState state) -> {
			MonitoringAgent monitoringAgent = state.monitoringAgent;
			TickRunner.Snapshot snapshot = monitoringAgent.getLastSnapshot();
			long tickNumber = snapshot.tickNumber();
			long processMillis = snapshot.millisTickPreamble() + snapshot.millisTickParallelPhase() + snapshot.millisTickPostamble();
			int entityCount = snapshot.completedEntities().size();
			int cuboidCount = snapshot.completedCuboids().size();
			int creatureCount = snapshot.completedCreatures().size();
			out.printf("Tick %d processed in %d ms:\n", tickNumber, processMillis);
			out.println("\tEntities: " + entityCount);
			out.println("\tCuboids: " + cuboidCount);
			out.println("\tCreatures: " + creatureCount);
		}),
		;
		
		public final _CommandHandler handler;
		
		private _Command(_CommandHandler handler)
		{
			this.handler = handler;
		}
	}
}
