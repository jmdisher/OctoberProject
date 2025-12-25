package com.jeffdisher.october.aspects;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.config.TabListReader.TabListException;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.Item;


/**
 * Defines data and helpers related to orientation within the world.
 * NOTE:  We count "facing positive Y" as "forward" so all other orientations rotate about that.  The reasoning for this
 * is to make this consistent with how we consider "north" (positive Y) to be the 0 yaw rotation.
 */
public class OrientationAspect
{
	public static final String FLAG_ALLOWS_DOWN = "allows_down";

	public static OrientationAspect load(ItemRegistry items
		, BlockAspect blocks
		, InputStream stream
	) throws IOException, TabListException
	{
		Set<Block> hasOrientation = new HashSet<>();
		Set<Block> flatOnly = new HashSet<>();
		
		TabListReader.IParseCallbacks callbacks = new TabListReader.IParseCallbacks() {
			@Override
			public void startNewRecord(String name, String[] parameters) throws TabListException
			{
				Block block = _getBlock(name);
				if (hasOrientation.contains(block))
				{
					throw new TabListReader.TabListException("Duplicate entry for: \"" + name + "\"");
				}
				if (parameters.length > 1)
				{
					throw new TabListReader.TabListException("Must have 0 or 1 parameter: \"" + name + "\"");
				}
				
				hasOrientation.add(block);
				if (1 == parameters.length)
				{
					if (!FLAG_ALLOWS_DOWN.equals(parameters[0]))
					{
						throw new TabListReader.TabListException("Unknown flag \"" + parameters[0] + "\" for: \"" + name + "\"");
					}
				}
				else
				{
					flatOnly.add(block);
				}
			}
			@Override
			public void endRecord() throws TabListException
			{
			}
			@Override
			public void processSubRecord(String name, String[] parameters) throws TabListException
			{
				throw new TabListReader.TabListException("No sub-records expected for: \"" + name + "\"");
			}
			private Block _getBlock(String name) throws TabListException
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
