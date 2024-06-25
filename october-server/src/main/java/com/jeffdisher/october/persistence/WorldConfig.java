package com.jeffdisher.october.persistence;

import java.util.Map;

import com.jeffdisher.october.types.Difficulty;


/**
 * A container of the configuration options for a world, designed to be persisted as part of the world directory.
 */
public class WorldConfig
{
	public static final String KEY_DIFFICULTY = "difficulty";
	public Difficulty difficulty;

	/**
	 * Creates a world config with all default options.
	 */
	public WorldConfig()
	{
		this.difficulty = Difficulty.HOSTILE;
	}

	public void loadOverrides(Map<String, String> overrides)
	{
		if (overrides.containsKey(KEY_DIFFICULTY))
		{
			this.difficulty = Difficulty.valueOf(overrides.get(KEY_DIFFICULTY));
		}
	}

	public Map<String, String> getRawOptions()
	{
		return Map.of(
				KEY_DIFFICULTY, this.difficulty.name()
		);
	}
}
