package com.jeffdisher.october.worldgen;

import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IOctree;
import com.jeffdisher.october.data.OctreeByte;
import com.jeffdisher.october.data.OctreeObject;
import com.jeffdisher.october.data.OctreeShort;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Item;


/**
 * A utility class to generate common shapes for cuboids.
 * In the future, this will likely be adapted into the generalized dynamic cuboid generator but it currently just exists
 * to generate common world idioms for testing.
 * It is in the server package since, although it is used by tests, it is ultimately only called on the server-side.
 */
public class CuboidGenerator
{
	public static CuboidData createFilledCuboid(CuboidAddress cuboidAddress, Item fillItem)
	{
		return _createFilledCuboid(cuboidAddress, fillItem);
	}

	/**
	 * This helper returns the cuboids needed to describe the default static world we will be using for initial
	 * integration play testing:  A 5x5x5 block of cuboids, centred around 0,0,0, with the top 3 layers air and the
	 * bottom 2 layers stone.
	 * This world will have dimensions in each axis of [-32, 48).
	 * 
	 * @return The array of cuboids to load as the static world.
	 */
	public static CuboidData[] generateStatic555World()
	{
		CuboidData[] list = new CuboidData[5*5*5];
		int index = 0;
		for (int x = -2; x < 3; ++x)
		{
			for (int y = -2; y < 3; ++y)
			{
				for (int z = -2; z < 3; ++z)
				{
					CuboidAddress address = new CuboidAddress((short)x, (short)y, (short)z);
					Item fillItem = (z < 0)
							// Stone in the bottom 2 layers.
							? ItemRegistry.STONE
							// Air in the top 3 layers.
							: ItemRegistry.AIR
					;
					list[index] = _createFilledCuboid(address, fillItem);
					index += 1;
				}
			}
		}
		return list;
	}


	private static CuboidData _createFilledCuboid(CuboidAddress cuboidAddress, Item fillItem)
	{
		OctreeShort blockData = OctreeShort.create(fillItem.number());
		OctreeObject inventoryData = OctreeObject.create();
		OctreeShort damageData = OctreeShort.create((short) 0);
		OctreeObject craftingData = OctreeObject.create();
		OctreeObject fueledData = OctreeObject.create();
		OctreeByte lightData = OctreeByte.create((byte) 0);
		return CuboidData.createNew(cuboidAddress, new IOctree[] { blockData
				, inventoryData
				, damageData
				, craftingData
				, fueledData
				, lightData
		});
	}
}
