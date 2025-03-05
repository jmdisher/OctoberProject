package com.jeffdisher.october.aspects;

import java.util.List;
import java.util.Map;

import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.utils.Assert;


/**
 * Contains the information describing multi-block structures.
 */
public class MultiBlockRegistry
{
	// Every multi-block structure is composed of multiple instances of the same block (these can sometimes all change to another).
	private final Map<Block, List<AbsoluteLocation>> _structures;

	public MultiBlockRegistry(ItemRegistry items, BlockAspect blocks)
	{
		// For now, we just hard-code this but it should move into data, eventually.
		Block closedDoubleDoor = blocks.getAsPlaceableBlock(items.getItemById("op.double_door_closed_base"));
		Block openDoubleDoor = blocks.getAsPlaceableBlock(items.getItemById("op.double_door_open_base"));
		// We expect to find this in the block list.
		Assert.assertTrue(null != closedDoubleDoor);
		Assert.assertTrue(null != openDoubleDoor);
		List<AbsoluteLocation> doubleDoorExtensions = List.of(new AbsoluteLocation(0, 0, 1)
				, new AbsoluteLocation(1, 0, 1)
				, new AbsoluteLocation(1, 0, 0)
		);
		_structures = Map.of(closedDoubleDoor, doubleDoorExtensions
				, openDoubleDoor, doubleDoorExtensions
		);
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
}
