package com.jeffdisher.october.worldgen;

import java.io.IOException;

import com.jeffdisher.october.aspects.Environment;
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
	 * @throws IOException There was an error while reading generated structure definitions.
	 */
	public static IWorldGenerator createConfiguredWorldGenerator(Environment env, WorldConfig config) throws IOException
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

	public static WorldGenConfig buildDefaultWorldGenConfig(Environment env) throws IOException
	{
		return _buildDefaultWorldGenConfig(env);
	}


	private static WorldGenConfig _buildDefaultWorldGenConfig(Environment env) throws IOException
	{
		// Look up the various data required for world gen.
		TerrainBindings terrainBindings = new TerrainBindings(env);
		SpecialItemReferences specialItems = new SpecialItemReferences(env);
		CommonStructures commonStructures = new CommonStructures(env, terrainBindings, specialItems);
		CreatureBindings creatureBindings = new CreatureBindings(env);
		WorldGenConfig worldGenConfig = new WorldGenConfig(env
			, terrainBindings
			, specialItems
			, commonStructures
			, creatureBindings
		);
		return worldGenConfig;
	}
}
