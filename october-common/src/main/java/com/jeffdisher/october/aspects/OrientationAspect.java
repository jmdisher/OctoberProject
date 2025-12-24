package com.jeffdisher.october.aspects;

import java.util.Collections;
import java.util.Set;

import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.FacingDirection;


/**
 * Defines data and helpers related to orientation within the world.
 * NOTE:  We count "facing positive Y" as "forward" so all other orientations rotate about that.  The reasoning for this
 * is to make this consistent with how we consider "north" (positive Y) to be the 0 yaw rotation.
 */
public class OrientationAspect
{
	public static OrientationAspect load(ItemRegistry items, BlockAspect blocks)
	{
		Block hopper = blocks.fromItem(items.getItemById("op.hopper"));
		Block emitter = blocks.fromItem(items.getItemById("op.emitter"));
		Block diode = blocks.fromItem(items.getItemById("op.diode"));
		Block andGate = blocks.fromItem(items.getItemById("op.and_gate"));
		Block orGate = blocks.fromItem(items.getItemById("op.or_gate"));
		Block notGate = blocks.fromItem(items.getItemById("op.not_gate"));
		Block sensorInventory = blocks.fromItem(items.getItemById("op.sensor_inventory"));
		Block portalKeystone = blocks.fromItem(items.getItemById("op.portal_keystone"));
		
		Set<Block> hasOrientation = Set.of(hopper
			, emitter
			, diode
			, andGate
			, orGate
			, notGate
			, sensorInventory
			, portalKeystone
		);
		Set<Block> flatOnly = Set.of(emitter
			, diode
			, andGate
			, orGate
			, notGate
			, sensorInventory
			, portalKeystone
		);
		
		return new OrientationAspect(hasOrientation, flatOnly);
	}


	private final Set<Block> _hasOrientation;
	private final Set<Block> _flatOnly;

	private OrientationAspect(Set<Block> hasOrientation, Set<Block> flatOnly)
	{
		_hasOrientation = Collections.unmodifiableSet(hasOrientation);
		_flatOnly = Collections.unmodifiableSet(flatOnly);
	}

	/**
	 * Checks if this block can and MUST be stored with an orientation byte in order for it to be correctly interpreted.
	 * Blocks which do NOT require orientation MUST NOT store one.
	 * This can only be called for single blocks (not multi-blocks).
	 * 
	 * @param blockType The block type.
	 * @return True if this MUST be stored with an orientation byte.
	 */
	public boolean doesSingleBlockRequireOrientation(Block blockType)
	{
		return _hasOrientation.contains(blockType);
	}

	/**
	 * Checks if the given block can be oriented with a downward output (as opposed to only a flat orientation).
	 * This can only be called for single blocks (not multi-blocks).
	 * 
	 * @param blockType The block type.
	 * @return True if this MUST store an orientation byte and can store a DOWN orientation.
	 */
	public boolean doesAllowDownwardOutput(Block blockType)
	{
		return _downAllowDownwardOutput(blockType);
	}

	/**
	 * Finds the appropriate direction for placing a block of blockType in blockLocation, facing targetLocation, if that
	 * is applicable for this kind of block.
	 * This can only be called for single blocks (not multi-blocks).
	 * 
	 * @param blockType The block type.
	 * @param blockLocation The location where the block is being stored.
	 * @param outputLocation The location where the block is "facing" (output).
	 * @return The Direction enum for this block or null, if it shouldn't use one or doesn't have a valid output.
	 */
	public FacingDirection getDirectionIfApplicableToSingle(Block blockType, AbsoluteLocation blockLocation, AbsoluteLocation outputLocation)
	{
		boolean has4 = _flatOnly.contains(blockType);
		boolean has5 = _downAllowDownwardOutput(blockType);
		FacingDirection outputDirection;
		if ((has4 || has5) && (null != outputLocation))
		{
			// Check the direction of the output, relative to target block.
			outputDirection = FacingDirection.getRelativeDirection(blockLocation, outputLocation);
			if ((FacingDirection.DOWN == outputDirection) && !has5)
			{
				// This is an invalid case so just return null so the caller can check and realize the request is invalid.
				outputDirection = null;
			}
		}
		else
		{
			// Common case.
			outputDirection = null;
		}
		return outputDirection;
	}


	private boolean _downAllowDownwardOutput(Block blockType)
	{
		return _hasOrientation.contains(blockType) && !_flatOnly.contains(blockType);
	}
}
