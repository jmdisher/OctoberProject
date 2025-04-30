package com.jeffdisher.october.worldgen;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.aspects.ItemRegistry;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.utils.Assert;


/**
 * A class which will load Structure objects from declarative string data.
 * Note that the declarative structure is a string per z-level, with newlines breaking up each row.  Note that the rows
 * are directly addressed as x locations, with each newline advancing a y location and each string in the array being
 * the next z location.
 * This means that a row has x offsets:  "012345..."
 * A column has y offsets:
 * "0...\n"
 * "1...\n"
 * A z-level has z offsets directly related to its string array index.
 * This interpretation means that looking at a generated structure from the top-down in OctoberPlains will be y-inverted
 * in the actual string and the z-levels will appear bottom to top as progressing along the string array.
 * All strings in the z-level array must be of the same length with the same number of newlines and all rows must have
 * the same number of characters.
 */
public class StructureLoader
{
	// We will list supported block types here (built into a map for quick lookup).
	public static final char C_DIRT     = 'D';
	public static final char C_SOIL     = 'O';
	public static final char C_WATER    = 'W';
	public static final char C_BRICK    = 'B';
	public static final char C_LANTERN  = 'L';
	public static final char C_SEEDLING = 'P';
	public static final char C_SAPLING  = 'S';
	public static final char C_CARROT   = 'C';
	public static final char C_COAL_ORE = 'A';
	public static final char C_IRON_ORE = 'I';
	public static final char C_TREE_LOG = 'T';
	public static final char C_TREE_LEAF= 'E';

	private final Map<Character, Block> _lookup;

	/**
	 * Creates the structure loader.
	 * 
	 * @param items The items to aid in block lookup.
	 * @param blocks The blocks to use to populate the loader mapping.
	 */
	public StructureLoader(ItemRegistry items, BlockAspect blocks)
	{
		Map<Character, Block> temp = new HashMap<>();
		Assert.assertTrue(null == temp.put(C_DIRT, blocks.fromItem(items.getItemById("op.dirt"))));
		Assert.assertTrue(null == temp.put(C_SOIL, blocks.fromItem(items.getItemById("op.tilled_soil"))));
		Assert.assertTrue(null == temp.put(C_WATER, blocks.fromItem(items.getItemById("op.water_source"))));
		Assert.assertTrue(null == temp.put(C_BRICK, blocks.fromItem(items.getItemById("op.stone_brick"))));
		Assert.assertTrue(null == temp.put(C_LANTERN, blocks.fromItem(items.getItemById("op.lantern"))));
		Assert.assertTrue(null == temp.put(C_SEEDLING, blocks.fromItem(items.getItemById("op.wheat_seedling"))));
		Assert.assertTrue(null == temp.put(C_SAPLING, blocks.fromItem(items.getItemById("op.sapling"))));
		Assert.assertTrue(null == temp.put(C_CARROT, blocks.fromItem(items.getItemById("op.carrot_seedling"))));
		Assert.assertTrue(null == temp.put(C_COAL_ORE, blocks.fromItem(items.getItemById("op.coal_ore"))));
		Assert.assertTrue(null == temp.put(C_IRON_ORE, blocks.fromItem(items.getItemById("op.iron_ore"))));
		Assert.assertTrue(null == temp.put(C_TREE_LOG, blocks.fromItem(items.getItemById("op.log"))));
		Assert.assertTrue(null == temp.put(C_TREE_LEAF, blocks.fromItem(items.getItemById("op.leaf"))));
		_lookup = Collections.unmodifiableMap(temp);
	}

	/**
	 * Loads a new Structure from the structure described in the given zLayers (see class comment for interpretation
	 * details).
	 * Note that malformed data will assert fail.
	 * 
	 * @param zLayers The string array describing the structure.
	 * @return The Structure.
	 */
	public Structure loadFromStrings(String[] zLayers)
	{
		// We will just define this as an array of short arrays (a short array for each z-level).
		int layerSize = zLayers[0].length();
		short[][] allLayerBlocks = new short[zLayers.length][];
		int width = -1;
		for (int i = 0; i < zLayers.length; ++i)
		{
			String layer = zLayers[i];
			// We expect that these are all the same size.
			Assert.assertTrue(layerSize == layer.length());
			int totalNewlines = _countNewlines(layer);
			short[] blocks = new short[layerSize - totalNewlines];
			int newlines = 0;
			for (int j = 0; j < layerSize; ++j)
			{
				char c = layer.charAt(j);
				Block block = _lookup.get(c);
				if ('\n' == c)
				{
					if (-1 == width)
					{
						width = j;
					}
					else
					{
						// We assume that these are always aligned.
						Assert.assertTrue(0 == ((j - newlines) % width));
					}
					newlines += 1;
				}
				else
				{
					short value;
					if (null != block)
					{
						value = block.item().number();
					}
					else
					{
						// We will store -1 if we ignore the block.
						value = -1;
					}
					blocks[j - newlines] = value;
				}
			}
			allLayerBlocks[i] = blocks;
		}
		return new Structure(allLayerBlocks, width);
	}


	private int _countNewlines(String layer)
	{
		// And each layer MUST end with a newline.
		Assert.assertTrue(layer.endsWith("\n"));
		
		int count = 0;
		for (int i = 0; i < layer.length(); ++i)
		{
			if ('\n' == layer.charAt(i))
			{
				count += 1;
			}
		}
		return count;
	}
}
