package com.jeffdisher.october.aspects;

import java.util.function.Function;

import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;


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

	/**
	 * Converts a Direction object into a byte for storage.
	 * 
	 * @param direction The direction object (cannot be null).
	 * @return The byte to store in the aspect storage.
	 */
	public static byte directionToByte(Direction direction)
	{
		return (byte)direction.ordinal();
	}

	/**
	 * Converts a byte value from storage into a high-level Direction enum object.
	 * 
	 * @param value The byte extracted from storage (must be in valid range).
	 * @return The associated Direction enum object.
	 */
	public static Direction byteToDirection(byte value)
	{
		return Direction.values()[value];
	}

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
		return blockId.equals(HOPPER) || blockId.equals(EMITTER) || blockId.equals(DIODE);
	}

	public static boolean doesAllowDownwardOutput(Block blockType)
	{
		String blockId = blockType.item().id();
		return blockId.equals(HOPPER);
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
	public static Direction getDirectionIfApplicableToSingle(Block blockType, AbsoluteLocation blockLocation, AbsoluteLocation outputLocation)
	{
		String blockId = blockType.item().id();
		boolean has4 = blockId.equals(EMITTER) || blockId.equals(DIODE);
		boolean has5 = blockId.equals(HOPPER);
		OrientationAspect.Direction outputDirection;
		if (has4 || has5)
		{
			// Check the direction of the output, relative to target block.
			outputDirection = _getRelativeDirection(blockLocation, outputLocation);
			if ((OrientationAspect.Direction.DOWN == outputDirection) && !has5)
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

	/**
	 * Determines the orientation direction a block in blockLocation would be if its output is facing outputLocation.
	 * 
	 * @param blockLocation The location of a block.
	 * @param outputLocation The location the output is facing.
	 * @return The relative direction.
	 */
	public static Direction getRelativeDirection(AbsoluteLocation blockLocation, AbsoluteLocation outputLocation)
	{
		return _getRelativeDirection(blockLocation, outputLocation);
	}


	private static Direction _getRelativeDirection(AbsoluteLocation blockLocation, AbsoluteLocation outputLocation)
	{
		// Check the direction of the output, relative to target block.
		int blockX = blockLocation.x();
		int blockY = blockLocation.y();
		int outputX = outputLocation.x();
		int outputY = outputLocation.y();
		
		OrientationAspect.Direction outputDirection;
		if (outputY > blockY)
		{
			outputDirection = OrientationAspect.Direction.NORTH;
		}
		else if (outputY < blockY)
		{
			outputDirection = OrientationAspect.Direction.SOUTH;
		}
		else if (outputX > blockX)
		{
			outputDirection = OrientationAspect.Direction.EAST;
		}
		else if (outputX < blockX)
		{
			outputDirection = OrientationAspect.Direction.WEST;
		}
		else
		{
			outputDirection = OrientationAspect.Direction.DOWN;
		}
		return outputDirection;
	}


	/**
	 * The direction will rotate a given location from NORTH to the given direction.
	 * These are in the order of positive yaw (to the left).
	 */
	public static enum Direction
	{
		// North is the positive Y direction, which we consider the "default" orientation (hence 0).
		NORTH(new int[] {1, 0, 0, 1}, (AbsoluteLocation thisLocation) -> thisLocation.getRelative(0, 1, 0)),
		// West is the negative X direction.
		WEST(new int[] {0, -1, 1, 0}, (AbsoluteLocation thisLocation) -> thisLocation.getRelative(-1, 0, 0)),
		// South is the negative Y direction.
		SOUTH(new int[] {-1, 0, 0, -1}, (AbsoluteLocation thisLocation) -> thisLocation.getRelative(0, -1, 0)),
		// East is the positive X direction.
		EAST(new int[] {0, 1, -1, 0}, (AbsoluteLocation thisLocation) -> thisLocation.getRelative(1, 0, 0)),
		// Down is the negative Z direction but rotation doesn't make sense for this direction.
		DOWN(null, (AbsoluteLocation thisLocation) -> thisLocation.getRelative(0, 0, -1)),
		;
		private final int[] _twoDRotationMatrix;
		private final Function<AbsoluteLocation, AbsoluteLocation> _sinkLocation;
		private Direction(int[] twoDRotationMatrix, Function<AbsoluteLocation, AbsoluteLocation> sinkLocation)
		{
			_twoDRotationMatrix = twoDRotationMatrix;
			_sinkLocation = sinkLocation;
		}
		/**
		 * Given an "in" location in the NORTH orientation, this will rotate that position, about the Z-axis and origin,
		 * into the orientation of the receiver.
		 * 
		 * @param in The input orientation in NORTH orientation (default).
		 * @return The rotated orientation.
		 */
		public AbsoluteLocation rotateAboutZ(AbsoluteLocation in)
		{
			int x = _twoDRotationMatrix[0] * in.x() + _twoDRotationMatrix[1] * in.y();
			int y = _twoDRotationMatrix[2] * in.x() + _twoDRotationMatrix[3] * in.y();
			return new AbsoluteLocation(x, y, in.z());
		}
		/**
		 * Given an "in" X/Y tuple in the NORTH orientation, this will rotate that position, about the Z-axis and origin,
		 * into the orientation of the receiver.
		 * 
		 * @param in The input orientation in NORTH orientation (default).
		 * @return The rotated orientation.
		 */
		public float[] rotateXYTupleAboutZ(float[] in)
		{
			float x = (float)_twoDRotationMatrix[0] * in[0] + (float)_twoDRotationMatrix[1] * in[1];
			float y = (float)_twoDRotationMatrix[2] * in[0] + (float)_twoDRotationMatrix[3] * in[1];
			return new float[] { x, y };
		}
		public AbsoluteLocation getOutputBlockLocation(AbsoluteLocation thisLocation)
		{
			return _sinkLocation.apply(thisLocation);
		}
	}
}
