package com.jeffdisher.october.aspects;

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
	public static final String HOPPER = "op.hopper";
	public static final String EMITTER = "op.emitter";
	public static final String DIODE = "op.diode";
	public static final String AND_GATE = "op.and_gate";
	public static final String OR_GATE = "op.or_gate";
	public static final String NOT_GATE = "op.not_gate";
	public static final String SENSOR_INVENTORY = "op.sensor_inventory";
	public static final String PORTAL_KEYSTONE = "op.portal_keystone";

	public static final Set<String> HAS_ORIENTATION = Set.of(HOPPER
			, EMITTER
			, DIODE
			, AND_GATE
			, OR_GATE
			, NOT_GATE
			, SENSOR_INVENTORY
			, PORTAL_KEYSTONE
	);
	public static final Set<String> FLAT_ONLY = Set.of(EMITTER
			, DIODE
			, AND_GATE
			, OR_GATE
			, NOT_GATE
			, SENSOR_INVENTORY
			, PORTAL_KEYSTONE
	);

	/**
	 * Checks if this block can and MUST be stored with an orientation byte in order for it to be correctly interpreted.
	 * Blocks which do NOT require orientation MUST NOT store one.
	 * This can only be called for single blocks (not multi-blocks).
	 * 
	 * @param blockType The block type.
	 * @return True if this MUST be stored with an orientation byte.
	 */
	public static boolean doesSingleBlockRequireOrientation(Block blockType)
	{
		String blockId = blockType.item().id();
		return HAS_ORIENTATION.contains(blockId);
	}

	/**
	 * Checks if the given block can be oriented with a downward output (as opposed to only a flat orientation).
	 * This can only be called for single blocks (not multi-blocks).
	 * 
	 * @param blockType The block type.
	 * @return True if this MUST store an orientation byte and can store a DOWN orientation.
	 */
	public static boolean doesAllowDownwardOutput(Block blockType)
	{
		String blockId = blockType.item().id();
		return _downAllowDownwardOutput(blockId);
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
	public static FacingDirection getDirectionIfApplicableToSingle(Block blockType, AbsoluteLocation blockLocation, AbsoluteLocation outputLocation)
	{
		String blockId = blockType.item().id();
		boolean has4 = FLAT_ONLY.contains(blockId);
		boolean has5 = _downAllowDownwardOutput(blockId);
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


	private static boolean _downAllowDownwardOutput(String blockId)
	{
		return HAS_ORIENTATION.contains(blockId) && !FLAT_ONLY.contains(blockId);
	}
}
