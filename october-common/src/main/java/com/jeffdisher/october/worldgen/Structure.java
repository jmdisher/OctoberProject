package com.jeffdisher.october.worldgen;

import java.util.ArrayList;
import java.util.List;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.ItemRegistry;
import com.jeffdisher.october.aspects.LightAspect;
import com.jeffdisher.october.aspects.PlantRegistry;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.MutationBlockOverwrite;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Item;


/**
 * A definition of a structure which can be injected into a cuboid when it is generated.
 * Note that these are loaded by StructureLoader and need to account for not just block types but also special follow-up
 * mutations related to the blocks added to the structure, such as lighting updates and growth behaviour.
 */
public class Structure
{
	public static final int CUBOID_EDGE_SIZE = 32;
	private final short[][] _allLayerBlocks;
	private final int _width;

	/**
	 * Creates the structure with the block type values in allLayerBlocks and the given width to use when interpretting
	 * rows.
	 * 
	 * @param allLayerBlocks The block values of the structure (-1 values will be skipped).
	 * @param width The width of a single row within the inner-most array.
	 */
	public Structure(short[][] allLayerBlocks, int width)
	{
		_allLayerBlocks = allLayerBlocks;
		_width = width;
	}

	/**
	 * Determines the total volume occupied by the structure.
	 * 
	 * @return The volume, as an AbsoluteLocation.
	 */
	public AbsoluteLocation totalVolume()
	{
		return new AbsoluteLocation(_width
				, _getYSize()
				, _allLayerBlocks.length
		);
	}

	/**
	 * Applies the receiver structure to the given cuboid, rooting it as baseOffset.  Note that a negative offset means
	 * that the structure begins outside of the cuboid but the part within it will be populated.
	 * 
	 * @param cuboid The cuboid to modify.
	 * @param baseOffset The base offset where the structure will be applied (relative to the base of the cuboid).
	 * @return The mutations which must be applied to this cuboid to finish the load.
	 */
	public List<IMutationBlock> applyToCuboid(CuboidData cuboid, AbsoluteLocation baseOffset)
	{
		int sizeX = _width;
		int sizeY = _getYSize();
		int sizeZ = _allLayerBlocks.length;
		
		// Determine the bounds in our local coordinates (baseOffset is relative to the cuboid, so negative numbers mean we start in the middle of our data).
		int readX;
		int writeX;
		int countX;
		int baseX = baseOffset.x();
		if (baseX >= 0)
		{
			readX = 0;
			writeX = baseX;
			countX = Math.min(sizeX, CUBOID_EDGE_SIZE - writeX);
		}
		else
		{
			readX = -baseX;
			writeX = 0;
			countX = Math.min(sizeX - readX, CUBOID_EDGE_SIZE);
		}
		
		int readY;
		int writeY;
		int countY;
		int baseY = baseOffset.y();
		if (baseY >= 0)
		{
			readY = 0;
			writeY = baseY;
			countY = Math.min(sizeY, CUBOID_EDGE_SIZE - writeY);
		}
		else
		{
			readY = -baseY;
			writeY = 0;
			countY = Math.min(sizeY - readY, CUBOID_EDGE_SIZE);
		}
		
		int readZ;
		int writeZ;
		int countZ;
		int baseZ = baseOffset.z();
		if (baseZ >= 0)
		{
			readZ = 0;
			writeZ = baseZ;
			countZ = Math.min(sizeZ, CUBOID_EDGE_SIZE - writeZ);
		}
		else
		{
			readZ = -baseZ;
			writeZ = 0;
			countZ = Math.min(sizeZ - readZ, CUBOID_EDGE_SIZE);
		}
		
		// Now we can copy, bearing in mind that we need to synthesize events to run after loading.
		AbsoluteLocation baseCuboidLocation = cuboid.getCuboidAddress().getBase();
		Environment env = Environment.getShared();
		ItemRegistry items = env.items;
		BlockAspect blocks = env.blocks;
		LightAspect lights = env.lighting;
		PlantRegistry plants = env.plants;
		short replacementBlock = env.special.AIR.item().number();
		List<IMutationBlock> mutations = new ArrayList<>();
		for (int c = 0; c < countZ; ++c)
		{
			short[] layer = _allLayerBlocks[readZ + c];
			for (int b = 0; b < countY; ++b)
			{
				int readColumn = readY + b;
				for (int a = 0; a < countX; ++a)
				{
					int readIndex = (readColumn * _width) + (readX + a);
					short value = layer[readIndex];
					// -1 means "ignore".
					if (value >= 0)
					{
						// Lighting updates are handled somewhat specially (not just as a simple mutation - since they
						// are so common, they are specially optimized).  Therefore, we will just handle lighting
						// updates and growth mutation requirements the same way:  Make the block air and return a
						// replace block mutation.
						Item rawItem = items.ITEMS_BY_TYPE[value];
						Block block = blocks.fromItem(rawItem);
						boolean needsLightUpdate = (lights.getLightEmission(block) > 0);
						boolean needsGrowth = (plants.growthDivisor(block) > 0);
						AbsoluteLocation thisBlock = baseCuboidLocation.getRelative(writeX + a, writeY + b, writeZ + c);
						if (needsLightUpdate || needsGrowth)
						{
							cuboid.setData15(AspectRegistry.BLOCK, thisBlock.getBlockAddress(), replacementBlock);
							mutations.add(new MutationBlockOverwrite(thisBlock, block));
						}
						else
						{
							cuboid.setData15(AspectRegistry.BLOCK, thisBlock.getBlockAddress(), value);
						}
					}
				}
			}
		}
		return mutations;
	}


	private int _getYSize()
	{
		return _allLayerBlocks[0].length / _width;
	}
}
