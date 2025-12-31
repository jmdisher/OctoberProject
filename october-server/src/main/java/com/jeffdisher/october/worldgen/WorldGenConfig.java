package com.jeffdisher.october.worldgen;

import com.jeffdisher.october.aspects.Environment;


/**
 * The Environment, bindings, and structure definitions required to support world generation.
 */
public class WorldGenConfig
{
	public final Environment environment;
	public final TerrainBindings terrainBindings;
	public final SpecialItemReferences specialItems;
	public final CommonStructures commonStructures;
	public final CreatureBindings creatureBindings;

	public WorldGenConfig(Environment environment
		, TerrainBindings terrainBindings
		, SpecialItemReferences specialItems
		, CommonStructures commonStructures
		, CreatureBindings creatureBindings
	)
	{
		this.environment = environment;
		this.terrainBindings = terrainBindings;
		this.specialItems = specialItems;
		this.commonStructures = commonStructures;
		this.creatureBindings = creatureBindings;
	}
}
