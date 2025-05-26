package com.jeffdisher.october.worldgen;

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
	 */
	public static IWorldGenerator createConfiguredWorldGenerator(Environment env, WorldConfig config)
	{
		IWorldGenerator worldGen;
		switch (config.worldGeneratorName)
		{
		case BASIC:
			worldGen = new BasicWorldGenerator(env, config.basicSeed);
			break;
		case FLAT:
			worldGen = new FlatWorldGenerator(true);
			break;
			default:
				throw Assert.unreachable();
		}
		return worldGen;
	}
}
