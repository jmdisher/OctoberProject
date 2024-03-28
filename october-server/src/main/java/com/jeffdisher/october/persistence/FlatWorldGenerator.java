package com.jeffdisher.october.persistence;

import java.util.function.Function;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.aspects.InventoryAspect;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.registries.AspectRegistry;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.worldgen.CuboidGenerator;


/**
 * A relatively simple world generator, designed to include the basic block types supported.
 * We also drop other miscellaneous items to make testing easier in the 0,0,0 cuboid.
 */
public class FlatWorldGenerator implements Function<CuboidAddress, CuboidData>
{
	@Override
	public CuboidData apply(CuboidAddress address)
	{
		// We will store the block types in the negative z blocks, but leave the non-negative blocks full or air.
		CuboidData data;
		if (address.z() < (short)0)
		{
			data = CuboidGenerator.createFilledCuboid(address, BlockAspect.STONE);
			_fillPlane(data, (byte)31, BlockAspect.DIRT);
			_fillPlane(data, (byte)29, BlockAspect.LOG);
			_fillPlane(data, (byte)27, BlockAspect.COAL_ORE);
			_fillPlane(data, (byte)25, BlockAspect.IRON_ORE);
			// We want to add a bit of water.
			data.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)6, (byte)6, (byte)31), BlockAspect.WATER_SOURCE.item().number());
			data.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)7, (byte)7, (byte)31), BlockAspect.WATER_SOURCE.item().number());
		}
		else
		{
			data = CuboidGenerator.createFilledCuboid(address, BlockAspect.AIR);
			// If this is the 0,0,0 cuboid, drop other useful testing items on the ground.
			if ((0 == address.x()) && (0 == address.y()) && (0 == address.z()))
			{
				Inventory starting = Inventory.start(InventoryAspect.CAPACITY_AIR)
						.add(ItemRegistry.SAPLING, 2)
						.add(ItemRegistry.LANTERN, 2)
						.add(ItemRegistry.WHEAT_SEED, 2)
						.finish();
				data.setDataSpecial(AspectRegistry.INVENTORY, new BlockAddress((byte)0, (byte)0, (byte)0), starting);
			}
		}
		return data;
	}

	private static void _fillPlane(CuboidData data, byte z, Block block)
	{
		short number = block.item().number();
		for (int y = 0; y < 32; ++y)
		{
			for (int x = 0; x < 32; ++x)
			{
				data.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)x, (byte)y, z), number);
			}
		}
	}
}
