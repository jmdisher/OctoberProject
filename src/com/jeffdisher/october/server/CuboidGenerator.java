package com.jeffdisher.october.server;

import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IOctree;
import com.jeffdisher.october.data.OctreeObject;
import com.jeffdisher.october.data.OctreeShort;
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
		OctreeShort blockData = OctreeShort.create(fillItem.number());
		OctreeObject inventoryData = OctreeObject.create();
		return CuboidData.createNew(cuboidAddress, new IOctree[] { blockData, inventoryData });
	}
}
