package com.jeffdisher.october.worldgen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.utils.Assert;


/**
 * Handles the in-world structures which have explicit locations, allowing them to be resolved by cuboid address, alone.
 * This is to make cuboid generation scale a little better when these structures need to be put into some of them.
 */
public class StructureRegistry
{
	// Note that we will likely make this into a list, later on, but this keeps things simple for now.
	private final Map<CuboidAddress, _StructureIdiom> _mappings = new HashMap<>();

	/**
	 * Registers the given structure at the globalRoot and rotation so that it can be generated, later.
	 * 
	 * @param structure The structure to register.
	 * @param globalRoot The location where the structure is located.
	 * @param rotation The rotation of the structure.
	 */
	public void register(Structure structure, AbsoluteLocation globalRoot, FacingDirection rotation)
	{
		Set<CuboidAddress> addresses = structure.findIntersectingCuboids(globalRoot, rotation);
		Assert.assertTrue(addresses.size() > 0);
		
		_StructureIdiom idiom = new _StructureIdiom(structure, globalRoot, rotation);
		for (CuboidAddress address : addresses)
		{
			_StructureIdiom old = _mappings.put(address, idiom);
			Assert.assertTrue(null == old);
		}
	}

	/**
	 * Generates any structures which intersect the given cuboid data and returns a description of required follow-ups.
	 * 
	 * @param data The cuboid to populate.
	 * @return The follow-ups for any structures generated (never null).
	 */
	public Structure.FollowUp generateAllInCuboid(CuboidData data)
	{
		_StructureIdiom idiom = _mappings.get(data.getCuboidAddress());
		Structure.FollowUp followUp = null;
		if (null != idiom)
		{
			followUp = idiom.structure.applyToCuboid(data, idiom.globalRoot, idiom.rotation, Structure.REPLACE_ALL);
		}
		else
		{
			followUp = new Structure.FollowUp(List.of(), Map.of());
		}
		return followUp;
	}


	private static record _StructureIdiom(Structure structure, AbsoluteLocation globalRoot, FacingDirection rotation)
	{}
}
