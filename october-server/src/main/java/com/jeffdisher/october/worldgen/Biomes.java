package com.jeffdisher.october.worldgen;


/**
 * Contains the biome listing and related utilities used by BasicWorldGenerator.  Much of this is structures as public
 * static fields since these don't need to be encapsulated, so much as organized for effective reuse and look-up.
 * Note that the Biome instance, themselves, are records instead of an enum since they will likely move into data files,
 * in the future.
 */
public class Biomes
{
	public static final int MASK_BIOME   = 0x0000F000;
	public static final int SHIFT_BIOME   = 12;

	public static final char FOREST_CODE = 'R';
	public static final char FIELD_CODE = 'F';
	public static final char MEADOW_CODE = 'E';

	public static final Biome[] BIOMES = {
		new Biome("Deep Ocean 2"
			, 'D'
			, -200
		),
		new Biome("Deep Ocean"
			, 'D'
			, -100
		),
		new Biome("Ocean 2"
			, 'O'
			, -50
		),
		new Biome("Ocean"
			, 'O'
			, -20
		),
		new Biome("Coast 2"
			, 'C'
			, -10
		),
		new Biome("Coast"
			, 'C'
			, -10
		),
		new Biome("Field"
			, FIELD_CODE
			, 0
		),
		new Biome("Meadow"
			, MEADOW_CODE
			, 0
		),
		new Biome("Forest"
			, FOREST_CODE
			, 0
		),
		new Biome("Swamp"
			, 'S'
			, 0
		),
		new Biome("Foothills"
			, 'h'
			, 10
		),
		new Biome("Foothills 2"
			, 'h'
			, 10
		),
		new Biome("Hills"
			, 'H'
			, 20
		),
		new Biome("Hills 2"
			, 'H'
			, 50
		),
		new Biome("Mountain"
			, 'M'
			, 100
		),
		new Biome("Mountain 2"
			, 'M'
			, 200
		),
	};

	public static Biome chooseBiomeFromSeeds5x5(PerColumnRandomSeedField.View subField)
	{
		int biomeTotal = 0;
		for (int y = -2; y <= 2; ++y)
		{
			for (int x = -2; x <= 2; ++x)
			{
				// We need to pick a value in [0..15]:
				int ballot = subField.get(x, y);
				int vote = (MASK_BIOME & ballot) >> SHIFT_BIOME;
				biomeTotal += vote;
			}
		}
		// We want to spread the biomes more aggressively since this averaging will push them too close together.
		biomeTotal = (biomeTotal * 2) + 1;
		// We also want to avoid division truncation making the numbers smaller.
		int biome = biomeTotal / 25;
		if ((biomeTotal % 25) > 12)
		{
			biome += 1;
		}
		// We can now strip off the edges and collapse this back into [0..15].
		biome -= 8;
		if (biome < 0)
		{
			biome = 0;
		}
		else if (biome > 15)
		{
			biome = 15;
		}
		return BIOMES[biome];
	}


	public static record Biome(String name
		, char code
		, int heightOffset
	)
	{}
}
