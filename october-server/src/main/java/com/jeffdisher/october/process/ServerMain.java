package com.jeffdisher.october.process;

import java.io.File;
import java.io.IOException;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.persistence.BasicWorldGenerator;
import com.jeffdisher.october.persistence.ResourceLoader;
import com.jeffdisher.october.server.MonitoringAgent;
import com.jeffdisher.october.server.ServerRunner;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.WorldConfig;
import com.jeffdisher.october.utils.Assert;


public class ServerMain
{
	public static void main(String[] args)
	{
		// For now, we will only assume 1 argument:  port number.
		if (1 == args.length)
		{
			int port = Integer.parseInt(args[0]);
			System.out.println("Starting server on port " + port);
			try
			{
				// We will just store the world in the current directory.
				File worldDirectory = new File("world");
				if (!worldDirectory.isDirectory())
				{
					Assert.assertTrue(worldDirectory.mkdirs());
				}
				Environment env = Environment.createSharedInstance();
				// We will just use the flat world generator since it should be populated with what we need for testing.
				MonitoringAgent monitoringAgent = new MonitoringAgent();
				WorldConfig config = new WorldConfig();
				boolean didLoadConfig = ResourceLoader.populateWorldConfig(worldDirectory, config);
				BasicWorldGenerator worldGen = new BasicWorldGenerator(env, config.basicSeed);
				if (!didLoadConfig)
				{
					// There is no config so ask the world-gen for the default spawn.
					EntityLocation spawnLocation = worldGen.getDefaultSpawnLocation();
					config.worldSpawn = spawnLocation.getBlockLocation();
				}
				ResourceLoader cuboidLoader = new ResourceLoader(worldDirectory
						, worldGen
						, config.worldSpawn.toEntityLocation()
				);
				ServerProcess process = new ServerProcess(port
						, ServerRunner.DEFAULT_MILLIS_PER_TICK
						, cuboidLoader
						, () -> System.currentTimeMillis()
						, monitoringAgent
						, config
				);
				// Hand over control to the ConsoleHandler.  Once it returns, we can shut down.
				ConsoleHandler.readUntilStop(System.in, System.out, monitoringAgent);
				// We returned, so we can stop the ServerProcess.
				process.stop();
				// Everything has stopped so now write-back the config.
				cuboidLoader.storeWorldConfig(config);
				System.out.println("Exiting normally");
				Environment.clearSharedInstance();
			}
			catch (IOException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		else
		{
			System.err.println("Usage:  ServerMain PORT");
			System.exit(1);
		}
	}
}
