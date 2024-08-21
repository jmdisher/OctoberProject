package com.jeffdisher.october.types;

import java.util.Map;
import java.util.Random;

import com.jeffdisher.october.utils.Assert;


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
	 * The seed value to use when configuring BasicWorldGenerator.  It is just a 32-bit int.
	 */
	public static final String KEY_BASIC_SEED = "basic_seed";
	public int basicSeed;

	/**
	 * The world spawn is where a player will first spawn in the world or will re-respawn after death.
	 */
	public static final String KEY_WORLD_SPAWN = "world_spawn";
	public AbsoluteLocation worldSpawn;

	/**
	 * Creates a world config with all default options.
	 */
	public WorldConfig()
	{
		this.difficulty = Difficulty.HOSTILE;
		this.hostilesPerCuboidTarget = 2;
		this.hostilesPerCuboidLimit = 4;
		// We default the seed to a random int.
		this.basicSeed = new Random().nextInt();
		this.worldSpawn = new AbsoluteLocation(0, 0, 0);
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
		if (overrides.containsKey(KEY_BASIC_SEED))
		{
			this.basicSeed = Integer.parseInt(overrides.get(KEY_BASIC_SEED));
		}
		if (overrides.containsKey(KEY_WORLD_SPAWN))
		{
			// Split this value over ","
			String raw = overrides.get(KEY_WORLD_SPAWN);
			String[] parts = raw.split(",");
			Assert.assertTrue(3 == parts.length);
			this.worldSpawn = new AbsoluteLocation(Integer.parseInt(parts[0])
					, Integer.parseInt(parts[1])
					, Integer.parseInt(parts[2])
			);
		}
	}

	public Map<String, String> getRawOptions()
	{
		String worldSpawn = this.worldSpawn.x() + "," + this.worldSpawn.y() + "," + this.worldSpawn.z();
		return Map.of(
				KEY_DIFFICULTY, this.difficulty.name()
				, KEY_HOSTILES_PER_CUBOID_TARGET, Integer.toString(this.hostilesPerCuboidTarget)
				, KEY_HOSTILES_PER_CUBOID_LIMIT, Integer.toString(this.hostilesPerCuboidLimit)
				, KEY_BASIC_SEED, Integer.toString(this.basicSeed)
				, KEY_WORLD_SPAWN, worldSpawn
		);
	}
}
