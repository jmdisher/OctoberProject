package com.jeffdisher.october.worldgen;

import java.io.IOException;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.types.WorldConfig;
import com.jeffdisher.october.utils.Assert;


/**
 * Just a container of miscellaneous helper functions related to world generator usage.
 */
public class WorldGenHelpers
{
	/**
	 * Instantiates a new IWorldGenerator using the given env and populated config.
	 * 
	 * @param env The environment.
	 * @param config The config, containing information related to the world generator.
	 * @return The new world generator instance (never null).
	 */
	public static IWorldGenerator createConfiguredWorldGenerator(Environment env, WorldConfig config)
	{
		WorldGenConfig worldGenConfig = _buildDefaultWorldGenConfig(env);
		
		IWorldGenerator worldGen;
		switch (config.worldGeneratorName)
		{
		case BASIC:
			worldGen = new BasicWorldGenerator(worldGenConfig, config.basicSeed);
			break;
		case FLAT:
			worldGen = new FlatWorldGenerator(worldGenConfig, true);
			break;
			default:
				throw Assert.unreachable();
		}
		return worldGen;
	}

	public static WorldGenConfig buildDefaultWorldGenConfig(Environment env)
	{
		return _buildDefaultWorldGenConfig(env);
	}


	private static WorldGenConfig _buildDefaultWorldGenConfig(Environment env)
	{
		// Look up the various data required for world gen.
		TerrainBindings terrainBindings;
		CommonStructures commonStructures;
		CreatureBindings creatureBindings;
		try
		{
			terrainBindings = new TerrainBindings(env);
			commonStructures = new CommonStructures(env);
			creatureBindings = new CreatureBindings(env);
		}
		catch (TabListReader.TabListException e)
		{
			// We consider these structure definitions part of the core system so a failure to load them is a static error in the installation.
			throw Assert.unexpected(e);
		}
		catch (IOException e)
		{
			// We will treat this as a static error in start-up.
			throw Assert.unexpected(e);
		}
		WorldGenConfig worldGenConfig = new WorldGenConfig(env
			, terrainBindings
			, commonStructures
			, creatureBindings
		);
		return worldGenConfig;
	}
}
