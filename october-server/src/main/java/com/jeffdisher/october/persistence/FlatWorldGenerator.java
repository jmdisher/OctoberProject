package com.jeffdisher.october.persistence;

import java.util.function.Function;

import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.worldgen.CuboidGenerator;


/**
 * A simple world generator which just creates a flat world where negative z cuboids are stone and everything above is
 * air.
 */
public class FlatWorldGenerator implements Function<CuboidAddress, CuboidData>
{
	@Override
	public CuboidData apply(CuboidAddress arg0)
	{
		// All negative Z will be stone, everything above is air.
		Item fillItem = (arg0.z() < (short)0)
				? ItemRegistry.STONE
				: ItemRegistry.AIR
		;
		return CuboidGenerator.createFilledCuboid(arg0, fillItem);
	}
}
