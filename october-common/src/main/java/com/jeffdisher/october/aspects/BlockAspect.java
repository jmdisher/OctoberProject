package com.jeffdisher.october.aspects;

import java.util.Map;
import java.util.Set;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.utils.Assert;


/**
 * Contains helpers for looking up information related to the block aspect.
 * For convenience, this exposes some constants which map to the constants in ItemRegistry.
 */
public class BlockAspect
{
	/**
	 * Loads the block aspect from the tablist in the given stream, sourcing Items from the given items registry.
	 * 
	 * @param items The existing ItemRegistry.
	 * @param stream The stream containing the tablist.
	 * @return The registry (never null).
	 * @throws IOException There was a problem with the stream.
	 * @throws TabListReader.TabListException The tablist was malformed.
	 */
	public static BlockAspect loadRegistry(ItemRegistry items, InputStream stream) throws IOException, TabListReader.TabListException
	{
		if (null == stream)
		{
			throw new IOException("Resource missing");
		}
		List<Block> blocks = new ArrayList<>();
		Map<Item, Block> blocksByItemType = new HashMap<>();
		
		Set<Block> canBeReplaced = new HashSet<>();
		Set<Block> permitsEntityMovement = new HashSet<>();
		Map<Block, Block> specialBlockSupport = new HashMap<>();
		Map<Item, Block> specialBlockPlacement = new HashMap<>();
		Map<Block, Item[]> specialBlockBreak = new HashMap<>();
		
		TabListReader.readEntireFile(new TabListReader.IParseCallbacks() {
			private Item _currentItem;
			private Block _currentBlock;
			@Override
			public void startNewRecord(String name, String[] parameters) throws TabListReader.TabListException
			{
				Assert.assertTrue(null == _currentBlock);
				_currentItem = _getItem(name);
				_currentBlock = new Block(_currentItem);
				
				// Read the flag list.
				for (String value : parameters)
				{
					if ("can_be_replaced".equals(value))
					{
						canBeReplaced.add(_currentBlock);
					}
					else if ("permits_entity_movement".equals(value))
					{
						permitsEntityMovement.add(_currentBlock);
					}
					else
					{
						throw new TabListReader.TabListException("Unknown flag: \"" + value + "\"");
					}
				}
			}
			@Override
			public void endRecord() throws TabListReader.TabListException
			{
				Assert.assertTrue(null != _currentBlock);
				blocks.add(_currentBlock);
				blocksByItemType.put(_currentItem, _currentBlock);
				_currentItem = null;
				_currentBlock = null;
			}
			@Override
			public void processSubRecord(String name, String[] parameters) throws TabListReader.TabListException
			{
				Assert.assertTrue(null != _currentBlock);
				// See which of the sublists this is an enter the correct state.
				if ("placed_from".equals(name))
				{
					if (0 == parameters.length)
					{
						throw new TabListReader.TabListException("Missing placed_from value");
					}
					for (String value : parameters)
					{
						Item from = _getItem(value);
						Block previous = specialBlockPlacement.put(from, _currentBlock);
						if (null != previous)
						{
							throw new TabListReader.TabListException("Duplicated placed_from mapping: \"" + from + "\"");
						}
					}
				}
				else if ("requires_support".equals(name))
				{
					// TODO: We probably want to support multiple values here.
					if (1 != parameters.length)
					{
						throw new TabListReader.TabListException("Exactly one value required for requires_support");
					}
					for (String value : parameters)
					{
						Item item = _getItem(value);
						Block support = blocksByItemType.get(item);
						if (null == support)
						{
							throw new TabListReader.TabListException("Unknown block for requires_support: \"" + value + "\"");
						}
						Block previous = specialBlockSupport.put(_currentBlock, support);
						// We already checked this in size, above.
						Assert.assertTrue(null == previous);
					}
				}
				else if ("special_drop".equals(name))
				{
					// Note that duplicates are expected in this parameter list (empty also makes sense).
					Item[] drops = new Item[parameters.length];
					for (int i = 0; i < parameters.length; ++i)
					{
						drops[i] = _getItem(parameters[i]);
					}
					specialBlockBreak.put(_currentBlock, drops);
				}
				else
				{
					throw new TabListReader.TabListException("Unknown sub-record identifier: \"" + name + "\"");
				}
			}
			private Item _getItem(String id) throws TabListReader.TabListException
			{
				Item item = items.getItemById(id);
				if (null == item)
				{
					throw new TabListReader.TabListException("Unknown item: \"" + id + "\"");
				}
				return item;
			}
		}, stream);
		
		Block[] blocksByType = new Block[items.ITEMS_BY_TYPE.length];
		for (int i = 0; i < blocksByType.length; ++i)
		{
			Block block = blocksByItemType.get(items.ITEMS_BY_TYPE[i]);
			blocksByType[i] = block;
		}
		return new BlockAspect(items
				, blocksByType
				, canBeReplaced
				, permitsEntityMovement
				, specialBlockSupport
				, specialBlockPlacement
				, specialBlockBreak
		);
	}

	private final Block[] _blocksByItemNumber;
	private final Set<Block> _canBeReplaced;
	private final Set<Block> _permitsEntityMovement;
	private final Map<Block, Block> _specialBlockSupport;
	private final Map<Item, Block> _specialBlockPlacement;
	private final Map<Block, Item[]> _specialBlockBreak;

	private BlockAspect(ItemRegistry items
			, Block[] blocksByType
			, Set<Block> canBeReplaced
			, Set<Block> permitsEntityMovement
			, Map<Block, Block> specialBlockSupport
			, Map<Item, Block> specialBlockPlacement
			, Map<Block, Item[]> specialBlockBreak
	)
	{
		_blocksByItemNumber = blocksByType;
		
		_canBeReplaced = Collections.unmodifiableSet(canBeReplaced);
		_permitsEntityMovement = Collections.unmodifiableSet(permitsEntityMovement);
		_specialBlockSupport = Collections.unmodifiableMap(specialBlockSupport);
		_specialBlockPlacement = Collections.unmodifiableMap(specialBlockPlacement);
		_specialBlockBreak = Collections.unmodifiableMap(specialBlockBreak);
	}

	/**
	 * Returns the corresponding Block for the given Item, null if it cannot be a block.  Note that this is to check the
	 * literal item, itself, and is not the same as "getAsPlaceableBlock(Item)".
	 * 
	 * @param item The Item.
	 * @return The corresponding Block, if item can be directly represented as a block.
	 */
	public Block fromItem(Item item)
	{
		return _blocksByItemNumber[item.number()];
	}

	/**
	 * Used to determine if the given block is something like air/water/etc which can just be overwritten by another
	 * block.
	 * Note that those which cannot be replaced are potentially breakable (although could be indestructible).
	 * 
	 * @param block The block to check.
	 * @return True if block can be directly overwritten by another.
	 */
	public boolean canBeReplaced(Block block)
	{
		return _canBeReplaced.contains(block);
	}

	/**
	 * Used to determine if the given block is something an entity can walk through, like air/water/etc.
	 * NOTE:  These blocks should probably all permit an air inventory or entities will walk through them but items
	 * can't be dropped here, which seems odd.
	 * 
	 * @param block The block to check.
	 * @return True if block allows an entity to pass through.
	 */
	public boolean permitsEntityMovement(Block block)
	{
		return _permitsEntityMovement.contains(block);
	}

	/**
	 * Used to determine if the given block can exist on top of another block type.  This is generally true but some
	 * types have specific requirements.
	 * 
	 * @param topBlock The block being checked (on top).
	 * @param bottomBlock The block underneath.
	 * @return True if topBlock can exist on top of bottomBlock.
	 */
	public boolean canExistOnBlock(Block topBlock, Block bottomBlock)
	{
		// By default, we want to assume that this is ok for all block types.
		boolean canExist = true;
		// Only check the special types if the bottom block is loaded (otherwise, assume it is supported - later update event will fix this).
		if (null != bottomBlock)
		{
			// See if this is one of our special-cases.
			Block specialBottom = _specialBlockSupport.get(topBlock);
			canExist = (null == specialBottom) || (bottomBlock == specialBottom);
		}
		return canExist;
	}

	/**
	 * Returns the given item's corresponding block type when being placed in the world, null if it can't be placed.
	 * 
	 * @param itemType The item to place.
	 * @return The block type to place in the world, null if it can't be placed.
	 */
	public Block getAsPlaceableBlock(Item itemType)
	{
		// This should not be called with null.
		Assert.assertTrue(null != itemType);
		
		// Most items just become the corresponding block, but some are special.
		Block block = _blocksByItemNumber[itemType.number()];
		if (null == block)
		{
			block = _specialBlockPlacement.get(itemType);
		}
		return block;
	}

	/**
	 * Returns the array of items which should be dropped when the given block is broken, in the world.
	 * 
	 * @param block The block to break.
	 * @return The array of items (never null).
	 */
	public Item[] droppedBlocksOnBreak(Block block)
	{
		// See if this is a special-case.
		Item[] dropped = _specialBlockBreak.get(block);
		if (null == dropped)
		{
			// By default, all other blocks just drop as their item type.
			dropped = new Item[] { block.item() };
		}
		return dropped;
	}
}
