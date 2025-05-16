package com.jeffdisher.october.aspects;

import java.util.Map;
import java.util.Set;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.config.TabListReader.TabListException;
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
	 * Viscosity is a fraction out of 100 where 100 means "solid".
	 */
	public static final int SOLID_VISCOSITY = 100;
	/**
	 * A viscosity of this level or greater is considered not breathable.
	 */
	public static final int SUFFOCATION_VISCOSITY = 50;
	/**
	 * A viscosity of at least this level, but less than SOLID is considered to allow and entity to swim.
	 */
	public static final int SWIMMABLE_VISCOSITY = 50;
	/**
	 * The limit for random drop chance calculations.
	 */
	public static final int RANDOM_DROP_LIMIT = 100;

	private static final String FLAG_CAN_BE_REPLACED = "can_be_replaced";
	private static final String FLAG_IS_FLAMMABLE = "is_flammable";
	private static final String FLAG_IS_FIRE_SOURCE = "is_fire_source";
	private static final String FLAG_STOPS_FIRE = "stops_fire";
	private static final String FLAG_IS_MULTIBLOCK = "is_multiblock";
	private static final String SUB_PLACED_FROM = "placed_from";
	private static final String SUB_REQUIRES_SUPPORT = "requires_support";
	private static final String SUB_SPECIAL_DROP = "special_drop";
	private static final String SUB_BLOCK_MATERIAL = "block_material";
	private static final String SUB_VISCOSITY = "viscosity";
	private static final String SUB_DAMAGE = "damage";

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
		
		// Run a parser on the normal file.
		_Parser parser = new _Parser(items);
		TabListReader.readEntireFile(parser, stream);
		Block[] blocksByType = new Block[items.ITEMS_BY_TYPE.length];
		for (int i = 0; i < blocksByType.length; ++i)
		{
			Block block = parser.blocksByItemType.get(items.ITEMS_BY_TYPE[i]);
			blocksByType[i] = block;
		}
		
		return new BlockAspect(items
				, blocksByType
				, parser.canBeReplaced
				, parser.isFlammable
				, parser.isFireSource
				, parser.stopsFire
				, parser.isMultiBlock
				, parser.nonSolidViscosity
				, parser.blockDamage
				, parser.specialBlockSupport
				, parser.specialBlockPlacement
				, parser.specialBlockBreak
				, parser.blockMaterials
		);
	}

	private final Block[] _blocksByItemNumber;
	private final Set<Block> _canBeReplaced;
	private final Set<Block> _isFlammable;
	private final Set<Block> _isFireSource;
	private final Set<Block> _stopsFire;
	private final Set<Block> _isMultiBlock;
	private final Map<Block, Integer> _nonSolidViscosity;
	private final Map<Block, Integer> _blockDamage;
	private final Map<Block, Set<Block>> _specialBlockSupport;
	private final Map<Item, Block> _specialBlockPlacement;
	private final Map<Block, _DropChance[]> _specialBlockBreak;
	private final Map<Block, BlockMaterial> _blockMaterials;

	private BlockAspect(ItemRegistry items
			, Block[] blocksByType
			, Set<Block> canBeReplaced
			, Set<Block> isFlammable
			, Set<Block> isFireSource
			, Set<Block> stopsFire
			, Set<Block> isMultiBlock
			, Map<Block, Integer> nonSolidViscosity
			, Map<Block, Integer> blockDamage
			, Map<Block, Set<Block>> specialBlockSupport
			, Map<Item, Block> specialBlockPlacement
			, Map<Block, _DropChance[]> specialBlockBreak
			, Map<Block, BlockMaterial> blockMaterials
	)
	{
		_blocksByItemNumber = blocksByType;
		
		_canBeReplaced = Collections.unmodifiableSet(canBeReplaced);
		_isFlammable = Collections.unmodifiableSet(isFlammable);
		_isFireSource = Collections.unmodifiableSet(isFireSource);
		_stopsFire = Collections.unmodifiableSet(stopsFire);
		_isMultiBlock = Collections.unmodifiableSet(isMultiBlock);
		_nonSolidViscosity = Collections.unmodifiableMap(nonSolidViscosity);
		_blockDamage = Collections.unmodifiableMap(blockDamage);
		_specialBlockSupport = Collections.unmodifiableMap(specialBlockSupport);
		_specialBlockPlacement = Collections.unmodifiableMap(specialBlockPlacement);
		_specialBlockBreak = Collections.unmodifiableMap(specialBlockBreak);
		_blockMaterials = Collections.unmodifiableMap(blockMaterials);
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
	 * Checks if a given block is a flammable type.
	 * 
	 * @param block The block to check.
	 * @return True if this block type can be set on fire.
	 */
	public boolean isFlammable(Block block)
	{
		return _isFlammable.contains(block);
	}

	/**
	 * Checks if a given block type should be considered a fire source (not that these are not flammable but can be
	 * sources of fire spread to other blocks).
	 * 
	 * @param block The block to check.
	 * @return True if this block type is a fire source.
	 */
	public boolean isFireSource(Block block)
	{
		return _isFireSource.contains(block);
	}

	/**
	 * Checks if a given block type stops or extinguishes fire in the block below it.
	 * 
	 * @param block The block to check.
	 * @return True if this block type can stop or extinguish fire.
	 */
	public boolean doesStopFire(Block block)
	{
		return _stopsFire.contains(block);
	}

	/**
	 * Checks if a given block type can only exist as part of a multi-block structure.
	 * 
	 * @param block The block to check.
	 * @return True if this block type can only exist as part of a multi-block structure.
	 */
	public boolean isMultiBlock(Block block)
	{
		return _isMultiBlock.contains(block);
	}

	/**
	 * Checks the viscosity of a given block to determine if it is solid (100).
	 * 
	 * @param block The block to check.
	 * @return True if this block is solid, meaning nothing can pass through it or exist within it.
	 */
	public boolean isSolid(Block block)
	{
		// Note that the _nonSolidViscosity ONLY contains non-solid blocks (< SOLID_VISCOSITY).
		return !_nonSolidViscosity.containsKey(block);
	}

	/**
	 * Checks if this block isn't solid, thus allowing items to be dropped directly into it.
	 * 
	 * @param block The block to check.
	 * @return True if this block can contain an inventory as an empty block (not a station).
	 */
	public boolean hasEmptyBlockInventory(Block block)
	{
		// Note that the _nonSolidViscosity ONLY contains non-solid blocks (< SOLID_VISCOSITY) and we allow ALL non-solid blocks to contain inventories.
		return _nonSolidViscosity.containsKey(block);
	}

	/**
	 * Checks the viscosity of a given block to determine if an entity should be allowed to breath in it (<50).
	 * 
	 * @param block The block to check.
	 * @return True if the entity can breathe in this block.
	 */
	public boolean canBreatheInBlock(Block block)
	{
		return (_nonSolidViscosity.containsKey(block))
				? (_nonSolidViscosity.get(block) < SUFFOCATION_VISCOSITY)
				: false
		;
	}

	/**
	 * Checks the viscosity of a given block to determine if an entity should be able to swim in it (>=50, <100).
	 * 
	 * @param block The block to check.
	 * @return True if the entity can swim in this block.
	 */
	public boolean canSwimInBlock(Block block)
	{
		return (_nonSolidViscosity.containsKey(block))
				? (_nonSolidViscosity.get(block) >= SWIMMABLE_VISCOSITY)
				: false
		;
	}

	/**
	 * Returns a fraction representing the viscosity of this block from 0.0f (air) to 1.0f (solid).
	 * 
	 * @param block The block to check.
	 * @return A fractional value in the range of [0.0f .. 1.0f].
	 */
	public float getViscosityFraction(Block block)
	{
		return (_nonSolidViscosity.containsKey(block))
				? ((float)_nonSolidViscosity.get(block) / 100.0f)
				: 1.0f
		;
	}

	/**
	 * Used to determine if the given block can exist on top of another block type.  This is generally true but some
	 * types have specific requirements.
	 * 
	 * @param topBlock The block being checked (on top).
	 * @param bottomBlock The block underneath (null if not loaded).
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
			Set<Block> specialBottom = _specialBlockSupport.get(topBlock);
			// This can exist if there is no supporting set or if this block is in the supporting set.
			canExist = (null == specialBottom) || specialBottom.contains(bottomBlock);
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
	 * @param random0to99 A random value between [0..99].
	 * @return The array of items (never null).
	 */
	public Item[] droppedBlocksOnBreak(Block block, int random0to99)
	{
		Assert.assertTrue(null != block);
		Assert.assertTrue(random0to99 < RANDOM_DROP_LIMIT);
		
		// See if this is a special-case.
		_DropChance[] chances = _specialBlockBreak.get(block);
		Item[] dropped;
		if (null == chances)
		{
			// By default, all other blocks just drop as their item type.
			dropped = new Item[] { block.item() };
		}
		else
		{
			dropped = Arrays.stream(chances)
					.filter((_DropChance one) -> (random0to99 < one.chance1to100))
					.map((_DropChance one) -> one.item)
					.toArray((int size) -> new Item[size])
			;
		}
		return dropped;
	}

	/**
	 * Returns the material the block is made of, in terms of the tool required to break it.  Note that most blocks have
	 * NO_TOOL.
	 * 
	 * @param block The block to check.
	 * @return The block material.
	 */
	public BlockMaterial getBlockMaterial(Block block)
	{
		BlockMaterial special = _blockMaterials.get(block);
		// We will default to NO_TOOL, if no value specified.
		return (null != special)
				? special
				: BlockMaterial.NO_TOOL
		;
	}

	/**
	 * Returns the damage which should be applied to entities in this block.
	 * 
	 * @param block The block to check.
	 * @return The damage (or 0).
	 */
	public int getBlockDamage(Block block)
	{
		return _blockDamage.containsKey(block)
				? _blockDamage.get(block)
				: 0
		;
	}

	/**
	 * Used to check if a flowing liquid should implicitly break this block type.
	 * 
	 * @param block The block to check.
	 * @return True if a flowing liquid can break this block.
	 */
	public boolean isBrokenByFlowingLiquid(Block block)
	{
		// We will say that any block which requires special support is broken by flowing liquids.
		return _specialBlockSupport.containsKey(block);
	}


	private static record _DropChance(Item item, int chance1to100) {}

	private static class _Parser implements TabListReader.IParseCallbacks
	{
		private final ItemRegistry _items;
		
		public List<Block> blocks = new ArrayList<>();
		public Map<Item, Block> blocksByItemType = new HashMap<>();
		
		public Set<Block> canBeReplaced = new HashSet<>();
		public Set<Block> isFlammable = new HashSet<>();
		public Set<Block> isFireSource = new HashSet<>();
		public Set<Block> stopsFire = new HashSet<>();
		public Set<Block> isMultiBlock = new HashSet<>();
		public Map<Block, Integer> nonSolidViscosity = new HashMap<>();
		public Map<Block, Integer> blockDamage = new HashMap<>();
		public Map<Block, Set<Block>> specialBlockSupport = new HashMap<>();
		public Map<Item, Block> specialBlockPlacement = new HashMap<>();
		public Map<Block, _DropChance[]> specialBlockBreak = new HashMap<>();
		public Map<Block, BlockMaterial> blockMaterials = new HashMap<>();
		
		private Item _currentItem;
		private Block _currentBlock;
		
		public _Parser(ItemRegistry items)
		{
			_items = items;
		}
		@Override
		public void startNewRecord(String name, String[] parameters) throws TabListReader.TabListException
		{
			Assert.assertTrue(null == _currentBlock);
			_currentItem = _getItem(name);
			_currentBlock = new Block(_currentItem);
			
			// Read the flag list.
			for (String value : parameters)
			{
				if (FLAG_CAN_BE_REPLACED.equals(value))
				{
					this.canBeReplaced.add(_currentBlock);
				}
				else if (FLAG_IS_FLAMMABLE.equals(value))
				{
					if (this.isFireSource.contains(_currentBlock) || this.stopsFire.contains(_currentBlock))
					{
						throw new TabListReader.TabListException("A block cannot be both flammable and a fire source or retardant: \"" + name + "\"");
					}
					this.isFlammable.add(_currentBlock);
				}
				else if (FLAG_IS_FIRE_SOURCE.equals(value))
				{
					if (this.isFlammable.contains(_currentBlock) || this.stopsFire.contains(_currentBlock))
					{
						throw new TabListReader.TabListException("A block cannot be both a fire source and flammable or a retardant: \"" + name + "\"");
					}
					this.isFireSource.add(_currentBlock);
				}
				else if (FLAG_STOPS_FIRE.equals(value))
				{
					if (this.isFlammable.contains(_currentBlock) || this.isFireSource.contains(_currentBlock))
					{
						throw new TabListReader.TabListException("A block cannot be both a fire retardant and flammable or a source: \"" + name + "\"");
					}
					this.stopsFire.add(_currentBlock);
				}
				else if (FLAG_IS_MULTIBLOCK.equals(value))
				{
					this.isMultiBlock.add(_currentBlock);
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
			this.blocks.add(_currentBlock);
			this.blocksByItemType.put(_currentItem, _currentBlock);
			_currentItem = null;
			_currentBlock = null;
		}
		@Override
		public void processSubRecord(String name, String[] parameters) throws TabListReader.TabListException
		{
			Assert.assertTrue(null != _currentBlock);
			// See which of the sublists this is an enter the correct state.
			if (SUB_PLACED_FROM.equals(name))
			{
				if (0 == parameters.length)
				{
					throw new TabListReader.TabListException("Missing placed_from value");
				}
				for (String value : parameters)
				{
					Item from = _getItem(value);
					Block previous = this.specialBlockPlacement.put(from, _currentBlock);
					if (null != previous)
					{
						throw new TabListReader.TabListException("Duplicated placed_from mapping: \"" + from + "\"");
					}
				}
			}
			else if (SUB_REQUIRES_SUPPORT.equals(name))
			{
				// We need at least one value here.
				if (0 == parameters.length)
				{
					throw new TabListReader.TabListException("At least one value required for requires_support");
				}
				Set<Block> supportingBlocks = new HashSet<>();
				for (String value : parameters)
				{
					Item item = _getItem(value);
					Block support = this.blocksByItemType.get(item);
					if (null == support)
					{
						throw new TabListReader.TabListException("Unknown block for requires_support: \"" + value + "\"");
					}
					supportingBlocks.add(support);
				}
				Set<Block> previous = this.specialBlockSupport.put(_currentBlock, supportingBlocks);
				// We already checked this in size, above.
				Assert.assertTrue(null == previous);
			}
			else if (SUB_SPECIAL_DROP.equals(name))
			{
				// Note that duplicates are expected in this parameter list (empty also makes sense).
				// This list is always in pairs (probability<TAB>item).
				if (0 != (parameters.length % 2))
				{
					throw new TabListReader.TabListException("Drop parameters must be in pairs: " + _currentBlock);
				}
				int pairCount = parameters.length / 2;
				_DropChance[] drops = new _DropChance[pairCount];
				for (int i = 0; i < pairCount; ++i)
				{
					int probability;
					try
					{
						probability = Integer.parseInt(parameters[2 * i]);
					}
					catch (NumberFormatException e)
					{
						throw new TabListReader.TabListException("Drop probability must be a number: " + _currentBlock);
					}
					Item item = _getItem(parameters[2 * i + 1]);
					drops[i] = new _DropChance(item, probability);
				}
				this.specialBlockBreak.put(_currentBlock, drops);
			}
			else if (SUB_BLOCK_MATERIAL.equals(name))
			{
				if (1 != parameters.length)
				{
					throw new TabListReader.TabListException("Exactly one value required for block_material");
				}
				BlockMaterial material = BlockMaterial.valueOf(parameters[0]);
				if (null == material)
				{
					throw new TabListReader.TabListException("Unknown constant for block_material: \"" + parameters[0] + "\"");
				}
				this.blockMaterials.put(_currentBlock, material);
			}
			else if (SUB_VISCOSITY.equals(name))
			{
				int viscosity = _readIntInRange(parameters, 0, SOLID_VISCOSITY, SUB_VISCOSITY);
				if (viscosity < SOLID_VISCOSITY)
				{
					this.nonSolidViscosity.put(_currentBlock, viscosity);
				}
			}
			else if (SUB_DAMAGE.equals(name))
			{
				int damage = _readIntInRange(parameters, 0, 100, SUB_DAMAGE);
				if (damage > 0)
				{
					this.blockDamage.put(_currentBlock, damage);
				}
			}
			else
			{
				throw new TabListReader.TabListException("Unknown sub-record identifier: \"" + name + "\"");
			}
		}
		private Item _getItem(String id) throws TabListReader.TabListException
		{
			Item item = _items.getItemById(id);
			if (null == item)
			{
				throw new TabListReader.TabListException("Unknown item: \"" + id + "\"");
			}
			return item;
		}
		private int _readIntInRange(String[] parameters, int low, int high, String name) throws TabListException
		{
			int value = -1;
			if (1 == parameters.length)
			{
				try
				{
					value = Integer.parseInt(parameters[0]);
				}
				catch (NumberFormatException e)
				{
					value = -1;
				}
			}
			if ((value < low) || (value > high))
			{
				String message = String.format("One value in [%d..%d] required for %s", low, high, name);
				throw new TabListReader.TabListException(message);
			}
			return value;
		}
	}
}
