package com.jeffdisher.october.types;

import java.util.Map;


/**
 * A container of the configuration options for a world, designed to be persisted as part of the world directory.
 */
public class WorldConfig
{
	/**
	 * Difficulty currently describes whether or not hostile creatures can exist in the world.
	 */
	public static final String KEY_DIFFICULTY = "difficulty";
	public Difficulty difficulty;

	/**
	 * When determining if anything should be spawned, this number is used as a vague target per cuboid.
	 */
	public static final String KEY_HOSTILES_PER_CUBOID_TARGET = "hostiles_per_cuboid_target";
	public int hostilesPerCuboidTarget;

	/**
	 * When spawning in a specific cuboid, we will abort the spawn attempt if there are more than this many entities in
	 * that specific cuboid.
	 */
	public static final String KEY_HOSTILES_PER_CUBOID_LIMIT = "hostiles_per_cuboid_limit";
	public int hostilesPerCuboidLimit;

	/**
	 * Creates a world config with all default options.
	 */
	public WorldConfig()
	{
		this.difficulty = Difficulty.HOSTILE;
		this.hostilesPerCuboidTarget = 2;
		this.hostilesPerCuboidLimit = 4;
	}

	public void loadOverrides(Map<String, String> overrides)
	{
		if (overrides.containsKey(KEY_DIFFICULTY))
		{
			this.difficulty = Difficulty.valueOf(overrides.get(KEY_DIFFICULTY));
		}
		if (overrides.containsKey(KEY_HOSTILES_PER_CUBOID_TARGET))
		{
			this.hostilesPerCuboidTarget = Integer.parseInt(overrides.get(KEY_HOSTILES_PER_CUBOID_TARGET));
		}
		if (overrides.containsKey(KEY_HOSTILES_PER_CUBOID_LIMIT))
		{
			this.hostilesPerCuboidLimit = Integer.parseInt(overrides.get(KEY_HOSTILES_PER_CUBOID_LIMIT));
		}
	}

	public Map<String, String> getRawOptions()
	{
		return Map.of(
				KEY_DIFFICULTY, this.difficulty.name()
				, KEY_HOSTILES_PER_CUBOID_TARGET, Integer.toString(this.hostilesPerCuboidTarget)
				, KEY_HOSTILES_PER_CUBOID_LIMIT, Integer.toString(this.hostilesPerCuboidLimit)
		);
	}
}
