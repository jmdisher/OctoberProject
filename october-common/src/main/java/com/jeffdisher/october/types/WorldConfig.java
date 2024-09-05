package com.jeffdisher.october.types;

import java.util.Map;
import java.util.Random;

import com.jeffdisher.october.utils.Assert;


/**
 * A container of the configuration options for a world, designed to be persisted as part of the world directory.
 * WARNING:  This is a shared mutable instance so care must be taken when modifying fields (marked volatile to make
 * this clear).
 */
public class WorldConfig
{
	/**
	 * Difficulty currently describes whether or not hostile creatures can exist in the world.
	 */
	public static final String KEY_DIFFICULTY = "difficulty";
	public volatile Difficulty difficulty;

	/**
	 * When determining if anything should be spawned, this number is used as a vague target per cuboid.
	 */
	public static final String KEY_HOSTILES_PER_CUBOID_TARGET = "hostiles_per_cuboid_target";
	public volatile int hostilesPerCuboidTarget;

	/**
	 * When spawning in a specific cuboid, we will abort the spawn attempt if there are more than this many entities in
	 * that specific cuboid.
	 */
	public static final String KEY_HOSTILES_PER_CUBOID_LIMIT = "hostiles_per_cuboid_limit";
	public volatile int hostilesPerCuboidLimit;

	/**
	 * The seed value to use when configuring BasicWorldGenerator.  It is just a 32-bit int.
	 */
	public static final String KEY_BASIC_SEED = "basic_seed";
	public volatile int basicSeed;

	/**
	 * The world spawn is where a player will first spawn in the world or will re-respawn after death.
	 */
	public static final String KEY_WORLD_SPAWN = "world_spawn";
	public volatile AbsoluteLocation worldSpawn;

	/**
	 * The number of ticks per day (must be > 0).
	 */
	public static final String KEY_TICKS_PER_DAY = "ticks_per_day";
	/**
	 * We currently run at 1 tick per 100ms so this will give us 20 minutes in a day.
	 */
	public static final int DEFAULT_TICKS_PER_DAY = 12_000;
	public volatile int ticksPerDay;

	/**
	 * The tick within a day where the day "starts" (must be >= 0).
	 */
	public static final String KEY_DAY_START_TICK = "day_start_tick";
	public volatile int dayStartTick;

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
		this.ticksPerDay = DEFAULT_TICKS_PER_DAY;
		// Default to 0 as the start tick (the brightest part of the day).
		this.dayStartTick = 0;
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
		if (overrides.containsKey(KEY_TICKS_PER_DAY))
		{
			this.ticksPerDay = Integer.parseInt(overrides.get(KEY_TICKS_PER_DAY));
			Assert.assertTrue(this.ticksPerDay > 0);
		}
		if (overrides.containsKey(KEY_DAY_START_TICK))
		{
			this.dayStartTick = Integer.parseInt(overrides.get(KEY_DAY_START_TICK));
			Assert.assertTrue(this.dayStartTick >= 0);
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
				, KEY_TICKS_PER_DAY, Integer.toString(this.ticksPerDay)
				, KEY_DAY_START_TICK, Integer.toString(this.dayStartTick)
		);
	}
}
