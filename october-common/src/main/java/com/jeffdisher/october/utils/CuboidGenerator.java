package com.jeffdisher.october.utils;

import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IOctree;
import com.jeffdisher.october.data.OctreeInflatedByte;
import com.jeffdisher.october.data.OctreeObject;
import com.jeffdisher.october.data.OctreeShort;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.CuboidAddress;


/**
 * A utility class to generate common shapes for cuboids.
 * This isn't used for real worldgen, just for tests or other isolated situations where a basic cuboid needs to be
 * quickly built as input for some utility or common logic.
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
		OctreeObject<?> inventoryData = OctreeObject.create();
		OctreeShort damageData = OctreeShort.create((short) 0);
		OctreeObject<?> craftingData = OctreeObject.create();
		OctreeObject<?> fuelledData = OctreeObject.create();
		OctreeInflatedByte lightData = OctreeInflatedByte.empty();
		OctreeInflatedByte logicData = OctreeInflatedByte.empty();
		OctreeInflatedByte flagsData = OctreeInflatedByte.empty();
		OctreeInflatedByte orientationData = OctreeInflatedByte.empty();
		OctreeObject<?> multiBlockRootData = OctreeObject.create();
		OctreeObject<?> specialItemSlotData = OctreeObject.create();
		return CuboidData.createNew(cuboidAddress, new IOctree[] { blockData
				, inventoryData
				, damageData
				, craftingData
				, fuelledData
				, lightData
				, logicData
				
				// Added in version 5.
				, flagsData
				, orientationData
				, multiBlockRootData
				
				// Added in version 8.
				, specialItemSlotData
		});
	}
}
