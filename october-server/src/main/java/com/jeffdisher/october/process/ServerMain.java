package com.jeffdisher.october.process;

import java.io.File;
import java.io.IOException;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.persistence.FlatWorldGenerator;
import com.jeffdisher.october.persistence.ResourceLoader;
import com.jeffdisher.october.server.ServerRunner;
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
				Environment.createSharedInstance();
				// We will just use the flat world generator since it should be populated with what we need for testing.
				ResourceLoader cuboidLoader = new ResourceLoader(worldDirectory, new FlatWorldGenerator(true));
				WorldConfig config = new WorldConfig();
				cuboidLoader.populateWorldConfig(config);
				ServerProcess process = new ServerProcess(port
						, ServerRunner.DEFAULT_MILLIS_PER_TICK
						, cuboidLoader
						, () -> System.currentTimeMillis()
						, config
				);
				// We will just wait for input before shutting down.
				System.in.read();
				System.out.println("Shutting down...");
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
