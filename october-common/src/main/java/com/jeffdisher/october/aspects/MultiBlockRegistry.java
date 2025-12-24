package com.jeffdisher.october.aspects;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.config.TabListReader.TabListException;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockVolume;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.utils.Assert;


/**
 * Contains the information describing multi-block structures.
 */
public class MultiBlockRegistry
{
	public static MultiBlockRegistry load(ItemRegistry items
		, BlockAspect blocks
		, InputStream stream
	) throws IOException, TabListException
	{
		Map<Block, List<AbsoluteLocation>> structures = new HashMap<>();
		TabListReader.IParseCallbacks callbacks = new TabListReader.IParseCallbacks() {
			private Block _currentKeystone;
			private Set<AbsoluteLocation> _extensions;
			
			@Override
			public void startNewRecord(String name, String[] parameters) throws TabListException
			{
				Block block = _mapToBlock(name);
				_currentKeystone = block;
				
				if (0 != parameters.length)
				{
					throw new TabListReader.TabListException("No flags expected for: \"" + name + "\"");
				}
				_extensions = new HashSet<>();
			}
			@Override
			public void endRecord() throws TabListException
			{
				if (structures.containsKey(_currentKeystone))
				{
					throw new TabListReader.TabListException("Duplicate key: \"" + _currentKeystone);
				}
				if (_extensions.isEmpty())
				{
					throw new TabListReader.TabListException("No extensions listed for: \"" + _currentKeystone);
				}
				structures.put(_currentKeystone, _extensions.stream().toList());
				_currentKeystone = null;
				_extensions = null;
			}
			@Override
			public void processSubRecord(String name, String[] parameters) throws TabListException
			{
				if (!"EXTENSION".equals(name))
				{
					throw new TabListReader.TabListException("Unknown sub-record: \"" + name + "\" under \"" + _currentKeystone + "\"");
				}
				if (3 != parameters.length)
				{
					throw new TabListReader.TabListException("Sub-record missing x/y/z for: \"" + name + "\" under \"" + _currentKeystone + "\"");
				}
				AbsoluteLocation location;
				try
				{
					location = new AbsoluteLocation(Integer.parseInt(parameters[0]), Integer.parseInt(parameters[1]), Integer.parseInt(parameters[2]));
				}
				catch (NumberFormatException e)
				{
					throw new TabListReader.TabListException("Invalid x/y/z number for: \"" + name + "\" under \"" + _currentKeystone + "\"");
				}
				if (_extensions.contains(location))
				{
					throw new TabListReader.TabListException("Duplicate extension for: \"" + name + "\" under \"" + _currentKeystone + "\"");
				}
				_extensions.add(location);
			}
			private Block _mapToBlock(String name) throws TabListException
			{
				Item item = items.getItemById(name);
				if (null == item)
				{
					throw new TabListReader.TabListException("Not a valid item: \"" + name + "\"");
				}
				Block block = blocks.fromItem(item);
				if (null == block)
				{
					throw new TabListReader.TabListException("Not a block: \"" + name + "\"");
				}
				return block;
			}
		};
		TabListReader.readEntireFile(callbacks, stream);
		
		Map<Block, BlockVolume> dimensions = new HashMap<>();
		for (Map.Entry<Block, List<AbsoluteLocation>> ent : structures.entrySet())
		{
			// All min/max start at 0, since that is where the base is.
			int minX = 0;
			int maxX = 0;
			int minY = 0;
			int maxY = 0;
			int minZ = 0;
			int maxZ = 0;
			for (AbsoluteLocation loc : ent.getValue())
			{
				minX = Math.min(minX, loc.x());
				maxX = Math.max(maxX, loc.x());
				minY = Math.min(minY, loc.y());
				maxY = Math.max(maxY, loc.y());
				minZ = Math.min(minZ, loc.z());
				maxZ = Math.max(maxZ, loc.z());
			}
			// We need to add 1 to each of these to account for the volume of the edge.
			dimensions.put(ent.getKey(), new BlockVolume(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1));
		}
		return new MultiBlockRegistry(structures, dimensions);
	}


	// Every multi-block structure is composed of multiple instances of the same block (these can sometimes all change to another).
	private final Map<Block, List<AbsoluteLocation>> _structures;
	// For client-side rendering help, we also provide the blocks-space dimensions of the structure.
	private final Map<Block, BlockVolume> _dimensions;

	private MultiBlockRegistry(Map<Block, List<AbsoluteLocation>> structures, Map<Block, BlockVolume> dimensions)
	{
		_structures = Collections.unmodifiableMap(structures);
		_dimensions = Collections.unmodifiableMap(dimensions);
	}

	/**
	 * Given a multi-block "root", based in rootLocation and facing direction, returns the other locations where the
	 * "extension" blocks should be placed.
	 * 
	 * @param root The root type.
	 * @param rootLocation The location where the root block is being placed.
	 * @param direction The direction the multi-block structure should "face".
	 * @return The list of other locations (not including rootLocation) where the other "extension" blocks need to be
	 * placed.
	 */
	public List<AbsoluteLocation> getExtensions(Block root, AbsoluteLocation rootLocation, FacingDirection direction)
	{
		List<AbsoluteLocation> relative = _structures.get(root);
		// We should only call this if we know it is a multi-block.
		Assert.assertTrue(null != relative);
		return relative.stream()
				.map((AbsoluteLocation rel) -> {
					AbsoluteLocation rot = direction.rotateAboutZ(rel);
					return rootLocation.getRelative(rot.x(), rot.y(), rot.z());
				})
				.toList()
		;
	}

	/**
	 * Given a multi-block "root", returns the default (non-rotated) total volume of all blocks required as its
	 * extensions.
	 * 
	 * @param root The root type.
	 * @return The volume of the block and all of its extensions in unrotated (facing North) orientation.
	 */
	public BlockVolume getDefaultVolume(Block root)
	{
		BlockVolume volume = _dimensions.get(root);
		// We should only call this if we know it is a multi-block.
		Assert.assertTrue(null != volume);
		return volume;
	}
}
