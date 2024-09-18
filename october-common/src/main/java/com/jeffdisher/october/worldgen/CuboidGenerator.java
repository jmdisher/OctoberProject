package com.jeffdisher.october.worldgen;

import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IOctree;
import com.jeffdisher.october.data.OctreeInflatedByte;
import com.jeffdisher.october.data.OctreeObject;
import com.jeffdisher.october.data.OctreeShort;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.CuboidAddress;


/**
 * A utility class to generate common shapes for cuboids.
 * In the future, this will likely be adapted into the generalized dynamic cuboid generator but it currently just exists
 * to generate common world idioms for testing.
 * It is in the server package since, although it is used by tests, it is ultimately only called on the server-side.
 */
public class CuboidGenerator
{
	/**
	 * Returns a cuboid with all default aspects except for BLOCK which is filled with the given fillBlock.
	 * 
	 * @param cuboidAddress The cuboid address.
	 * @param fillBlock The block which should fill the entire cuboid.
	 * @return The new cuboid.
	 */
	public static CuboidData createFilledCuboid(CuboidAddress cuboidAddress, Block fillBlock)
	{
		return _createFilledCuboid(cuboidAddress, fillBlock);
	}


	private static CuboidData _createFilledCuboid(CuboidAddress cuboidAddress, Block fillBlock)
	{
		OctreeShort blockData = OctreeShort.create(fillBlock.item().number());
		OctreeObject inventoryData = OctreeObject.create();
		OctreeShort damageData = OctreeShort.create((short) 0);
		OctreeObject craftingData = OctreeObject.create();
		OctreeObject fuelledData = OctreeObject.create();
		OctreeInflatedByte lightData = OctreeInflatedByte.empty();
		OctreeInflatedByte logicData = OctreeInflatedByte.empty();
		return CuboidData.createNew(cuboidAddress, new IOctree[] { blockData
				, inventoryData
				, damageData
				, craftingData
				, fuelledData
				, lightData
				, logicData
		});
	}
}
