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
	public static final String FLAG_ALLOWS_UP = "allows_up";

	public static OrientationAspect load(ItemRegistry items
		, BlockAspect blocks
		, InputStream stream
	) throws IOException, TabListException
	{
		Set<Block> hasOrientation = new HashSet<>();
		Set<Block> allowsDown = new HashSet<>();
		Set<Block> allowsUp = new HashSet<>();
		
		TabListReader.IParseCallbacks callbacks = new TabListReader.IParseCallbacks() {
			@Override
			public void startNewRecord(String name, String[] parameters) throws TabListException
			{
				Block block = _getBlock(name);
				if (hasOrientation.contains(block))
				{
					throw new TabListReader.TabListException("Duplicate entry for: \"" + name + "\"");
				}
				if (parameters.length > 2)
				{
					throw new TabListReader.TabListException("Must have at most 2 parameters: \"" + name + "\"");
				}
				
				hasOrientation.add(block);
				for (String param : parameters)
				{
					if (FLAG_ALLOWS_DOWN.equals(param))
					{
						boolean wasAdded = allowsDown.add(block);
						if (!wasAdded)
						{
							throw new TabListReader.TabListException("Duplicate flag \"" + param + "\" for: \"" + name + "\"");
						}
					}
					else if (FLAG_ALLOWS_UP.equals(param))
					{
						boolean wasAdded = allowsUp.add(block);
						if (!wasAdded)
						{
							throw new TabListReader.TabListException("Duplicate flag \"" + param + "\" for: \"" + name + "\"");
						}
					}
					else
					{
						throw new TabListReader.TabListException("Unknown flag \"" + param + "\" for: \"" + name + "\"");
					}
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
		
		return new OrientationAspect(hasOrientation, allowsDown, allowsUp);
	}


	private final Set<Block> _hasOrientation;
	private final Set<Block> _allowsDown;
	private final Set<Block> _allowsUp;

	private OrientationAspect(Set<Block> hasOrientation
		, Set<Block> allowsDown
		, Set<Block> allowsUp
	)
	{
		_hasOrientation = Collections.unmodifiableSet(hasOrientation);
		_allowsDown = Collections.unmodifiableSet(allowsDown);
		_allowsUp = Collections.unmodifiableSet(allowsUp);
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
		return _allowsDown.contains(blockType);
	}

	/**
	 * Checks if the given block can be oriented with an upward output orientation (as opposed to only a flat
	 * orientation).
	 * This can only be called for single blocks (not multi-blocks).
	 * 
	 * @param blockType The block type.
	 * @return True if this MUST store an orientation byte and can store an UP orientation.
	 */
	public boolean doesAllowUpwardOutput(Block blockType)
	{
		return _allowsDown.contains(blockType);
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
		FacingDirection outputDirection;
		if (null == outputLocation)
		{
			// This is kind of a degenerate case where we allow this to be called with null so the caller can avoid extra checks.
			outputDirection = null;
		}
		else if (_hasOrientation.contains(blockType))
		{
			outputDirection = FacingDirection.getRelativeDirection(blockLocation, outputLocation);
			if (((FacingDirection.DOWN == outputDirection) && !_allowsDown.contains(blockType))
				|| ((FacingDirection.UP == outputDirection) && !_allowsUp.contains(blockType))
			)
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
}
