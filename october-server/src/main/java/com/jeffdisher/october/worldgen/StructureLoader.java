package com.jeffdisher.october.worldgen;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
	public static final char C_COPPER_ORE = 'R';
	public static final char C_DIAMOND_ORE = 'M';

	/**
	 * Creates the mapping structure for the basic loader cases (when everything is internally defined and block-only).
	 * 
	 * @param terrainBindings The bindings from the WorldGenConfig.
	 * @return A mapping to use when defining a StructureLoader.
	 */
	public static Map<Character, Structure.AspectData> getBasicMapping(TerrainBindings terrainBindings)
	{
		Map<Character, Structure.AspectData> temp = new HashMap<>();
		Assert.assertTrue(null == temp.put(C_DIRT, _wrapBlockWithNulls(terrainBindings.dirtBlock)));
		Assert.assertTrue(null == temp.put(C_SOIL, _wrapBlockWithNulls(terrainBindings.tilledSoilBlock)));
		Assert.assertTrue(null == temp.put(C_WATER, _wrapBlockWithNulls(terrainBindings.waterSourceBlock)));
		Assert.assertTrue(null == temp.put(C_BRICK, _wrapBlockWithNulls(terrainBindings.stoneBrickBlock)));
		Assert.assertTrue(null == temp.put(C_LANTERN, _wrapBlockWithNulls(terrainBindings.lanternBlock)));
		Assert.assertTrue(null == temp.put(C_SEEDLING, _wrapBlockWithNulls(terrainBindings.wheatSeedlingBlock)));
		Assert.assertTrue(null == temp.put(C_SAPLING, _wrapBlockWithNulls(terrainBindings.saplingBlock)));
		Assert.assertTrue(null == temp.put(C_CARROT, _wrapBlockWithNulls(terrainBindings.carrotSeedlingBlock)));
		Assert.assertTrue(null == temp.put(C_COAL_ORE, _wrapBlockWithNulls(terrainBindings.coalOreBlock)));
		Assert.assertTrue(null == temp.put(C_IRON_ORE, _wrapBlockWithNulls(terrainBindings.ironOreBlock)));
		Assert.assertTrue(null == temp.put(C_TREE_LOG, _wrapBlockWithNulls(terrainBindings.logBlock)));
		Assert.assertTrue(null == temp.put(C_TREE_LEAF, _wrapBlockWithNulls(terrainBindings.leafBlock)));
		Assert.assertTrue(null == temp.put(C_COPPER_ORE, _wrapBlockWithNulls(terrainBindings.copperOreBlock)));
		Assert.assertTrue(null == temp.put(C_DIAMOND_ORE, _wrapBlockWithNulls(terrainBindings.diamondOreBlock)));
		return Collections.unmodifiableMap(temp);
	}

	/**
	 * A helper to read a full description of a generated structure from the given input stream and split it into the
	 * z-layers which are expected in the other load paths.
	 * Each z-layer must be followed by an empty line (including the final layer).  Each non-empty line must be the same
	 * length and each z-layer must have the same number of lines.
	 * The first character in the stream represents the West-South-Bottom corner of the structure while the last
	 * non-newline character in the stream represents the East-North-Top corner of the structure.
	 * 
	 * @param stream The stream containing the string defining the structure.
	 * @return The z-layers as strings (bottom-first).
	 * @throws IOException There was a problem reading the stream.
	 */
	public static String[] splitStreamIntoZLayerStrings(InputStream stream) throws IOException
	{
		// All lines must be the same length and all layers must have the same number of lines.
		int xWidth = 0;
		int yHeight = 0;
		int currentLineCount = 0;
		
		List<String> processed = new ArrayList<>();
		StringBuilder currentChunk = new StringBuilder();
		
		BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
		String line;
		while (null != (line = reader.readLine()))
		{
			int length = line.length();
			if (0 == length)
			{
				// This comes at the end of every layer so carve off the previous.
				String layer = currentChunk.toString();
				if (processed.isEmpty())
				{
					// This is the first chunk, so we will calibrate our checks on dimensions.
					// This better be at least one line.
					Assert.assertTrue(currentLineCount > 0);
					yHeight = currentLineCount;
				}
				else
				{
					// This layer must be the same number of lines as the others.
					Assert.assertTrue(yHeight == currentLineCount);
				}
				
				processed.add(layer);
				currentChunk = new StringBuilder();
				currentLineCount = 0;
			}
			else
			{
				if (0 == xWidth)
				{
					// This is the first line so capture it.
					xWidth = length;
				}
				else
				{
					// All lines must be the same length.
					Assert.assertTrue(xWidth == length);
				}
				
				currentChunk.append(line);
				currentChunk.append('\n');
				currentLineCount += 1;
			}
		}
		
		// We expect the last chunk to be closed in order to be considered a valid end of file.
		Assert.assertTrue(currentChunk.toString().isEmpty());
		
		return processed.toArray((int size) -> new String[size]);
	}

	private static Structure.AspectData _wrapBlockWithNulls(Block block)
	{
		return new Structure.AspectData(block, null, null, null, null);
	}


	private final Map<Character, Structure.AspectData> _lookup;

	/**
	 * Creates the structure loader.
	 * 
	 * @param mapping The map which describes how to interpret the character data in the structure definition.
	 */
	public StructureLoader(Map<Character, Structure.AspectData> mapping)
	{
		_lookup = mapping;
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
		Structure.AspectData[][] allLayerBlocks = new Structure.AspectData[zLayers.length][];
		int width = -1;
		for (int i = 0; i < zLayers.length; ++i)
		{
			String layer = zLayers[i];
			// We expect that these are all the same size.
			Assert.assertTrue(layerSize == layer.length());
			int totalNewlines = _countNewlines(layer);
			Structure.AspectData[] blocks = new Structure.AspectData[layerSize - totalNewlines];
			int newlines = 0;
			for (int j = 0; j < layerSize; ++j)
			{
				char c = layer.charAt(j);
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
					blocks[j - newlines] = _lookup.get(c);
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
