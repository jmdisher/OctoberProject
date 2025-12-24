package com.jeffdisher.october.aspects;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.config.TabListReader.TabListException;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Item;


/**
 * Describes the types of blocks which can passively spread to other blocks or are destroyed by other blocks covering
 * them.
 * The most obvious example of this is grass:  It can spread to nearby dirt blocks but reverts to dirt is not under air,
 * specifically.
 * An example of a different use-case is tilled soil:  It doesn't spread but is made invalid by placing things on top of
 * it and converts to dirt.
 */
public class GroundCoverRegistry
{
	public static final String KEY_CAN_SPREAD = "can_spread";

	public static GroundCoverRegistry load(ItemRegistry items
		, BlockAspect blocks
		, InputStream stream
	) throws IOException, TabListException
	{
		Map<Block, Block> spreadingGroundCoverToTargets = new HashMap<>();
		Map<Block, Block> nonSpreadingToBaseType = new HashMap<>();
		Map<Block, Set<Block>> canGrowGroundCover = new HashMap<>();
		
		TabListReader.IParseCallbacks callbacks = new TabListReader.IParseCallbacks() {
			private Block _block;
			private Block _baseBlock;
			private Boolean _canSpread;
			@Override
			public void startNewRecord(String name, String[] parameters) throws TabListException
			{
				_block = _getBlock(name);
				if (spreadingGroundCoverToTargets.containsKey(_block) || nonSpreadingToBaseType.containsKey(_block))
				{
					throw new TabListReader.TabListException("Duplicate entry for: \"" + name + "\"");
				}
				if (1 != parameters.length)
				{
					throw new TabListReader.TabListException("Base block type expected for: \"" + name + "\"");
				}
				_baseBlock = _getBlock(parameters[0]);
			}
			@Override
			public void endRecord() throws TabListException
			{
				if (null == _canSpread)
				{
					throw new TabListReader.TabListException("Missing \"can_spread\" sub-field for: \"" + _block + "\"");
				}
				if (_canSpread)
				{
					spreadingGroundCoverToTargets.put(_block, _baseBlock);
				}
				else
				{
					nonSpreadingToBaseType.put(_block, _baseBlock);
				}
				
				Set<Block> revert = canGrowGroundCover.get(_baseBlock);
				if (null == revert)
				{
					revert = Set.of(_block);
				}
				else
				{
					Set<Block> mutable = new HashSet<>();
					mutable.addAll(revert);
					mutable.add(_block);
					revert = Collections.unmodifiableSet(mutable);
				}
				canGrowGroundCover.put(_baseBlock, revert);
				
				_block = null;
				_baseBlock = null;
				_canSpread = null;
			}
			@Override
			public void processSubRecord(String name, String[] parameters) throws TabListException
			{
				if (KEY_CAN_SPREAD.equals(name))
				{
					if (null != _canSpread)
					{
						throw new TabListReader.TabListException("Duplicate \"can_spread\" sub-field for: \"" + _block + "\"");
					}
					if (1 != parameters.length)
					{
						throw new TabListReader.TabListException("Sub-field \"can_spread\" expects only one parameter for: \"" + name + "\"");
					}
					_canSpread = Boolean.parseBoolean(parameters[0]);
				}
				else
				{
					throw new TabListReader.TabListException("Unknown sub-field \"" + name + "\" for: \"" + _block + "\"");
				}
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
		
		return new GroundCoverRegistry(spreadingGroundCoverToTargets
			, nonSpreadingToBaseType
			, canGrowGroundCover
		);
	}


	private final Map<Block, Block> _groundCoverToTargets;
	private final Map<Block, Block> _nonSpreadingToBaseType;
	private final Map<Block, Set<Block>> _canGrowGroundCover;

	private GroundCoverRegistry(Map<Block, Block> groundCoverToTargets
		, Map<Block, Block> nonSpreadingToBaseType
		, Map<Block, Set<Block>> canGrowGroundCover
	)
	{
		_groundCoverToTargets = Collections.unmodifiableMap(groundCoverToTargets);
		_nonSpreadingToBaseType = Collections.unmodifiableMap(nonSpreadingToBaseType);
		_canGrowGroundCover = Collections.unmodifiableMap(canGrowGroundCover);
	}

	/**
	 * Checks if a given block is a ground cover block (not to be confused with a target for spread).
	 * 
	 * @param block The block to check.
	 * @return True if the block is ground cover.
	 */
	public boolean isGroundCover(Block block)
	{
		return _groundCoverToTargets.containsKey(block) || _nonSpreadingToBaseType.containsKey(block);
	}

	/**
	 * Checks if a given possibleGroundCoverBlock block is ground cover and can exist under the above type block.
	 * 
	 * @param possibleGroundCoverBlock The block which might be a ground cover block.
	 * @param above The block type above.
	 * @return True if this is a ground cover type and can exist under the above type.
	 */
	public boolean canGroundCoverExistUnder(Block possibleGroundCoverBlock, Block above)
	{
		// For now, at least, we will consider all groundcover to be able to exist under any breathable block.
		// We always assume inactive since we don't want this to change based on that state.
		boolean isActive = false;
		return (_groundCoverToTargets.containsKey(possibleGroundCoverBlock) || _nonSpreadingToBaseType.containsKey(possibleGroundCoverBlock))
				&& Environment.getShared().blocks.canBreatheInBlock(above, isActive);
	}

	/**
	 * Finds the type of target block possibleGroundCoverBlock grows on.
	 * 
	 * @param possibleGroundCoverBlock The block which might be a ground cover block.
	 * @return The type of block this can grow on or null if not a ground cover block.
	 */
	public Block getSpreadToTypeForGroundCover(Block possibleGroundCoverBlock)
	{
		return _groundCoverToTargets.get(possibleGroundCoverBlock);
	}

	/**
	 * Finds what type of block a ground cover block should revert into when covered.
	 * 
	 * @param possibleGroundCoverBlock The block which might be a ground cover block.
	 * @return The block type this should revert into or null if this isn't a ground cover type.
	 */
	public Block getRevertTypeForGroundCover(Block possibleGroundCoverBlock)
	{
		return _groundCoverToTargets.containsKey(possibleGroundCoverBlock)
				? _groundCoverToTargets.get(possibleGroundCoverBlock)
				: _nonSpreadingToBaseType.get(possibleGroundCoverBlock)
		;
	}

	/**
	 * Used to check if a given block type is a target type for some kind of ground cover.
	 * 
	 * @param blockType The block type to check.
	 * @return The set of block types which can spread to this block or null if there aren't any (never empty).
	 */
	public Set<Block> canGrowGroundCover(Block blockType)
	{
		return _canGrowGroundCover.get(blockType);
	}
}
