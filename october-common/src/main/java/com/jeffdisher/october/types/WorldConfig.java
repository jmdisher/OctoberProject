package com.jeffdisher.october.types;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import com.jeffdisher.october.aspects.MiscConstants;
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
	 * We use some special constants for this, describing the name of the generator:  "BASIC", "FLAT".
	 * BASIC should always be used in actual play but FLAT is provided for some testing cases.
	 */
	public static final String KEY_WORLD_GENERATOR_NAME = "world_generator_name";
	public volatile WorldGeneratorName worldGeneratorName;

	/**
	 * True if we should synthesize block updates for all blocks adjacent to a cuboid load boundary.
	 * This is disabled by default since it is incredibly expensive and rarely relevant.  Enabling this can avoid "world
	 * stitching inconsistencies" caused by the world being segmented into cuboids (water not updating across a cuboid
	 * boundary, etc).
	 * In the future, we will replace this very heavy-weight mechanism with a more precise one where boundary events
	 * opt-in for this treatment, instead of applying it to all.
	 */
	public static final String KEY_SHOULD_SYNTHESIZE_UPDATES_ON_LOAD = "should_synthesize_updates_on_load";
	public volatile boolean shouldSynthesizeUpdatesOnLoad;

	/**
	 * The maximum cuboid view distance for a new client (0 would mean only the cuboid they are in and is the implicit
	 * minimum).  We impose a hard limit of 5 on this value (which would be 11^3 cuboids per client).
	 * NOTE:  Users of this value assume that it does NOT change during an active run.
	 */
	public static final String KEY_CLIENT_VIEW_DISTANCE_MAXIMUM = "client_view_distance_maximum";
	public static final int MAX_CLIENT_VIEW_DISTANCE_MAXIMUM = 5;
	public int clientViewDistanceMaximum;

	/**
	 * A human-readable name for the server.
	 */
	public static final String KEY_SERVER_NAME = "server_name";
	public volatile String serverName;

	/**
	 * This option controls whether new players spawn in survival or creative modes.
	 * This can still be over-ridden, on a per-entity basis, using commands.
	 */
	public static final String KEY_DEFAULT_PLAYER_MODE = "default_player_mode";
	public volatile DefaultPlayerMode defaultPlayerMode;

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
		this.worldGeneratorName = WorldGeneratorName.BASIC;
		// We default to no synthetic updates as they wash out all performance analysis attempts.
		this.shouldSynthesizeUpdatesOnLoad = false;
		// We will default our config maximum to the maximum constant since reducing this is more just an option for servers having issues.
		this.clientViewDistanceMaximum = MAX_CLIENT_VIEW_DISTANCE_MAXIMUM;
		this.serverName = "OctoberProject Server";
		this.defaultPlayerMode = DefaultPlayerMode.SURVIVAL;
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
		if (overrides.containsKey(KEY_WORLD_GENERATOR_NAME))
		{
			this.worldGeneratorName = WorldGeneratorName.valueOf(overrides.get(KEY_WORLD_GENERATOR_NAME));
		}
		if (overrides.containsKey(KEY_SHOULD_SYNTHESIZE_UPDATES_ON_LOAD))
		{
			this.shouldSynthesizeUpdatesOnLoad = Boolean.parseBoolean(overrides.get(KEY_SHOULD_SYNTHESIZE_UPDATES_ON_LOAD));
		}
		if (overrides.containsKey(KEY_CLIENT_VIEW_DISTANCE_MAXIMUM))
		{
			this.clientViewDistanceMaximum = Integer.parseInt(overrides.get(KEY_CLIENT_VIEW_DISTANCE_MAXIMUM));
			Assert.assertTrue(this.clientViewDistanceMaximum >= MiscConstants.DEFAULT_CUBOID_VIEW_DISTANCE);
			Assert.assertTrue(this.clientViewDistanceMaximum <= MAX_CLIENT_VIEW_DISTANCE_MAXIMUM);
		}
		if (overrides.containsKey(KEY_SERVER_NAME))
		{
			this.serverName = overrides.get(KEY_SERVER_NAME);
		}
		if (overrides.containsKey(KEY_DEFAULT_PLAYER_MODE))
		{
			this.defaultPlayerMode = DefaultPlayerMode.valueOf(overrides.get(KEY_DEFAULT_PLAYER_MODE));
		}
	}

	public Map<String, String> getRawOptions()
	{
		String worldSpawn = this.worldSpawn.x() + "," + this.worldSpawn.y() + "," + this.worldSpawn.z();
		Map<String, String> map = new HashMap<>();
		map.put(KEY_DIFFICULTY, this.difficulty.name());
		map.put(KEY_HOSTILES_PER_CUBOID_TARGET, Integer.toString(this.hostilesPerCuboidTarget));
		map.put(KEY_HOSTILES_PER_CUBOID_LIMIT, Integer.toString(this.hostilesPerCuboidLimit));
		map.put(KEY_BASIC_SEED, Integer.toString(this.basicSeed));
		map.put(KEY_WORLD_SPAWN, worldSpawn);
		map.put(KEY_TICKS_PER_DAY, Integer.toString(this.ticksPerDay));
		map.put(KEY_DAY_START_TICK, Integer.toString(this.dayStartTick));
		map.put(KEY_WORLD_GENERATOR_NAME, this.worldGeneratorName.name());
		map.put(KEY_SHOULD_SYNTHESIZE_UPDATES_ON_LOAD, Boolean.toString(this.shouldSynthesizeUpdatesOnLoad));
		map.put(KEY_CLIENT_VIEW_DISTANCE_MAXIMUM, Integer.toString(this.clientViewDistanceMaximum));
		map.put(KEY_SERVER_NAME, this.serverName);
		map.put(KEY_DEFAULT_PLAYER_MODE, this.defaultPlayerMode.name());
		return Collections.unmodifiableMap(map);
	}


	public static enum WorldGeneratorName
	{
		BASIC,
		FLAT,
	}

	public static enum DefaultPlayerMode
	{
		SURVIVAL,
		CREATIVE,
	}
}
