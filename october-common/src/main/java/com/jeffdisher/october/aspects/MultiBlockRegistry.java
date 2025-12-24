package com.jeffdisher.october.aspects;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockVolume;
import com.jeffdisher.october.utils.Assert;


/**
 * Contains the information describing multi-block structures.
 */
public class MultiBlockRegistry
{
	public static MultiBlockRegistry load(ItemRegistry items
		, BlockAspect blocks
	)
	{
		// For now, we just hard-code this but it should move into data, eventually.
		Block doubleDoor = blocks.getAsPlaceableBlock(items.getItemById("op.double_door_base"));
		Block portalSurface = blocks.getAsPlaceableBlock(items.getItemById("op.portal_surface"));
		Block door = blocks.getAsPlaceableBlock(items.getItemById("op.door"));
		
		// We expect to find this in the block list.
		Assert.assertTrue(null != doubleDoor);
		Assert.assertTrue(null != portalSurface);
		
		// Double-door root is bottom-left block.
		List<AbsoluteLocation> doubleDoorExtensions = List.of(new AbsoluteLocation(0, 0, 1)
				, new AbsoluteLocation(1, 0, 1)
				, new AbsoluteLocation(1, 0, 0)
		);
		
		// Portal surface root is bottom-centre.
		List<AbsoluteLocation> portalSurfaceExtensions = List.of(new AbsoluteLocation(-1, 0, 0)
				, new AbsoluteLocation(1, 0, 0)
				, new AbsoluteLocation(-1, 0, 1)
				, new AbsoluteLocation( 0, 0, 1)
				, new AbsoluteLocation( 1, 0, 1)
				, new AbsoluteLocation(-1, 0, 2)
				, new AbsoluteLocation( 0, 0, 2)
				, new AbsoluteLocation( 1, 0, 2)
		);
		
		// Door root is bottom block.
		List<AbsoluteLocation> doorExtensions = List.of(new AbsoluteLocation(0, 0, 1)
		);
		
		Map<Block, List<AbsoluteLocation>> structures = Map.of(doubleDoor, doubleDoorExtensions
			, portalSurface, portalSurfaceExtensions
			, door, doorExtensions
		);
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
	public List<AbsoluteLocation> getExtensions(Block root, AbsoluteLocation rootLocation, OrientationAspect.Direction direction)
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
