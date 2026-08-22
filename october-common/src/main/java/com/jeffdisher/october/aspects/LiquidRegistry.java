package com.jeffdisher.october.aspects;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Pair;
import com.jeffdisher.october.utils.Assert;


/**
 * Describes the block and item types associated with liquids and liquid behaviours.
 */
public class LiquidRegistry
{
	public static final String FLAG_CREATES_SOURCE = "creates_source";
	public static final String SUB_FLOW_DELAY_MILLIS = "flow_delay_millis";
	public static final String SUB_FULL_BUCKET = "full_bucket";
	public static final String SUB_EMPTY_BUCKET = "empty_bucket";
	public static final String SUB_STRONG_FLOW = "strong_flow";
	public static final String SUB_WEAK_FLOW = "weak_flow";
	public static final String SUB_SOLID_BLOCK = "solid_block";

	public static final int FLOW_SOURCE = 3;
	public static final int FLOW_STRONG = 2;
	public static final int FLOW_WEAK = 1;
	public static final int FLOW_NONE = 0;

	public static LiquidRegistry loadRegistry(ItemRegistry items, BlockAspect blocks, InputStream stream) throws IOException, TabListReader.TabListException
	{
		if (null == stream)
		{
			throw new IOException("Resource missing");
		}
		Map<Block, Block> blocksToSource = new HashMap<>();
		Map<Block, Integer> blocksToStrength = new HashMap<>();
		Map<Block, Block> sourceToSolid = new HashMap<>();
		Map<Block, Block[]> sourceToFlowStrengths = new HashMap<>();
		Map<Block, Long> sourceToDelayMillis = new HashMap<>();
		Set<Block> sourceCreationSources = new HashSet<>();
		Map<Item, Block> fullBucketToSource = new HashMap<>();
		Map<Block, Item> sourceToFullBucket = new HashMap<>();
		Map<Block, Item> sourceToEmptyBucket = new HashMap<>();
		
		TabListReader.readEntireFile(new TabListReader.IParseCallbacks() {
			private Block _currentSource;
			private Block _strongFlow;
			private Block _weakFlow;
			@Override
			public void startNewRecord(String name, String[] parameters) throws TabListReader.TabListException
			{
				Assert.assertTrue(null == _currentSource);
				_currentSource = _getBlock(name);
				blocksToSource.put(_currentSource, _currentSource);
				blocksToStrength.put(_currentSource, 3);
				
				// Read the flag list.
				for (String value : parameters)
				{
					if (FLAG_CREATES_SOURCE.equals(value))
					{
						sourceCreationSources.add(_currentSource);
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
				Assert.assertTrue(null != _currentSource);
				Assert.assertTrue(null != _strongFlow);
				Assert.assertTrue(null != _weakFlow);
				
				Block[] strengths = new Block[] {null, _weakFlow, _strongFlow, _currentSource };
				sourceToFlowStrengths.put(_currentSource, strengths);
				
				_currentSource = null;
				_strongFlow = null;
				_weakFlow = null;
			}
			@Override
			public void processSubRecord(String name, String[] parameters) throws TabListReader.TabListException
			{
				Assert.assertTrue(null != _currentSource);
				// See which of the sublists this is an enter the correct state.
				if (SUB_FLOW_DELAY_MILLIS.equals(name))
				{
					String delayMillis = _getSingleParam(SUB_FLOW_DELAY_MILLIS, name, parameters);
					long millis = Long.parseLong(delayMillis);
					sourceToDelayMillis.put(_currentSource, millis);
				}
				else if (SUB_FULL_BUCKET.equals(name))
				{
					String fullBucket = _getSingleParam(SUB_FULL_BUCKET, name, parameters);
					Item bucket = _getItem(fullBucket);
					fullBucketToSource.put(bucket, _currentSource);
					sourceToFullBucket.put(_currentSource, bucket);
				}
				else if (SUB_EMPTY_BUCKET.equals(name))
				{
					// We also want to store the type of empty bucket required to pick up a given source type.
					String emptyBucket = _getSingleParam(SUB_EMPTY_BUCKET, name, parameters);
					Item bucket = _getItem(emptyBucket);
					sourceToEmptyBucket.put(_currentSource, bucket);
				}
				else if (SUB_STRONG_FLOW.equals(name))
				{
					Assert.assertTrue(null == _strongFlow);
					String strongFlow = _getSingleParam(SUB_STRONG_FLOW, name, parameters);
					_strongFlow = _getBlock(strongFlow);
					blocksToSource.put(_strongFlow, _currentSource);
					blocksToStrength.put(_strongFlow, 2);
				}
				else if (SUB_WEAK_FLOW.equals(name))
				{
					Assert.assertTrue(null == _weakFlow);
					String weakFlow = _getSingleParam(SUB_WEAK_FLOW, name, parameters);
					_weakFlow = _getBlock(weakFlow);
					blocksToSource.put(_weakFlow, _currentSource);
					blocksToStrength.put(_weakFlow, 1);
				}
				else if (SUB_SOLID_BLOCK.equals(name))
				{
					String solidBlock = _getSingleParam(SUB_SOLID_BLOCK, name, parameters);
					Block block = _getBlock(solidBlock);
					sourceToSolid.put(_currentSource, block);
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
			private Block _getBlock(String id) throws TabListReader.TabListException
			{
				Item item = items.getItemById(id);
				if (null == item)
				{
					throw new TabListReader.TabListException("Unknown item: \"" + id + "\"");
				}
				Block block = blocks.fromItem(item);
				if (null == block)
				{
					throw new TabListReader.TabListException("Not a block: \"" + id + "\"");
				}
				return block;
			}
			private String _getSingleParam(String search, String name, String[] parameters) throws TabListReader.TabListException
			{
				String found = null;
				if (search.equals(name))
				{
					if (1 != parameters.length)
					{
						throw new TabListReader.TabListException("Expected single parameter: " + search);
					}
					found = parameters[0];
				}
				return found;
			}
		}, stream);
		
		return new LiquidRegistry(blocksToSource
			, blocksToStrength
			, sourceToSolid
			, sourceToFlowStrengths
			, sourceToDelayMillis
			, sourceCreationSources
			, fullBucketToSource
			, sourceToFullBucket
			, sourceToEmptyBucket
		);
	}


	private final Map<Block, Block> _blocksToSource;
	private final Map<Block, Integer> _blocksToStrength;
	private final Map<Block, Block> _sourceToSolid;
	private final Map<Block, Block[]> _sourceToFlowStrengths;
	private final Map<Block, Long> _sourceToDelayMillis;
	private final Set<Block> _sourceCreationSources;
	private final Map<Item, Block> _fullBucketToSource;
	private final Map<Block, Item> _sourceToFullBucket;
	private final Map<Block, Item> _sourceToEmptyBucket;
	private final long _defaultDelayMillis;

	private LiquidRegistry(Map<Block, Block> blocksToSource
			, Map<Block, Integer> blocksToStrength
			, Map<Block, Block> sourceToSolid
			, Map<Block, Block[]> sourceToFlowStrengths
			, Map<Block, Long> sourceToDelayMillis
			, Set<Block> sourceCreationSources
			, Map<Item, Block> fullBucketToSource
			, Map<Block, Item> sourceToFullBucket
			, Map<Block, Item> sourceToEmptyBucket
	)
	{
		Assert.assertTrue(!sourceToDelayMillis.isEmpty());
		
		_blocksToSource = Map.copyOf(blocksToSource);
		_blocksToStrength = Map.copyOf(blocksToStrength);
		_sourceToSolid = Map.copyOf(sourceToSolid);
		_sourceToFlowStrengths = Map.copyOf(sourceToFlowStrengths);
		_sourceToDelayMillis = Map.copyOf(sourceToDelayMillis);
		_sourceCreationSources = Set.copyOf(sourceCreationSources);
		_fullBucketToSource = Map.copyOf(fullBucketToSource);
		_sourceToFullBucket = Map.copyOf(sourceToFullBucket);
		_sourceToEmptyBucket = Map.copyOf(sourceToEmptyBucket);
		_defaultDelayMillis = sourceToDelayMillis.values().stream().max((Long one, Long two) -> (int)(one - two)).get();
	}

	/**
	 * Checks if the given block type is a liquid source (that is, the lower-strength liquid will flow from this block).
	 * 
	 * @param block The block type to check.
	 * @return True if this is a source.
	 */
	public boolean isSource(Block block)
	{
		return _sourceToSolid.containsKey(block);
	}

	/**
	 * Determines the correct block should be given the current block state and the surrounding blocks.
	 * There are a few noteworthy rules around how this liquid algorithm works:
	 * -if this block can form a source and is horizontally-adjacent to at least 2 sources, it becomes a source
	 * -if the block isn't above a solid block, it will become weak flow, at best (if not solid or air)
	 * 
	 * @param env The environment.
	 * @param currentBlock The current block type (MUST be a replaceable type).
	 * @param east Block to the East (null if not a liquid).
	 * @param west Block to the West (null if not a liquid).
	 * @param north Block to the North (null if not a liquid).
	 * @param south Block to the South (null if not a liquid).
	 * @param above Block above (null if not a liquid).
	 * @param below Block below (null if replaceable).
	 * @return The block or liquid block type (never null).
	 */
	public Pair<Block, LiquidBlock> chooseEmptyLiquidBlock(Environment env
		, LiquidBlock currentBlock
		, LiquidBlock east
		, LiquidBlock west
		, LiquidBlock north
		, LiquidBlock south
		, LiquidBlock above
		, Block below
	)
	{
		Assert.assertTrue((null == below) || !env.blocks.canBeReplaced(below));
		
		// This takes a few steps:
		// -check if horizontal liquids should act on currentBlock
		// -check if vertical liquids should act on currentBlock
		// -update currentBlock based on horizontal adjacent blocks
		// -apply vertical liquid to the updated currentBlock
		
		boolean isAboveSolidBlock = (null != below) && !env.blocks.canBeReplaced(below);
		_FlowCollector collector = new _FlowCollector(currentBlock, isAboveSolidBlock);
		collector.addAdjacent(east, false);
		collector.addAdjacent(west, false);
		collector.addAdjacent(north, false);
		collector.addAdjacent(south, false);
		collector.addAdjacent(above, true);
		
		// If there is a solid, we prefer that.
		Block nonLiquid = collector.getSolid();
		LiquidBlock liquid = null;
		if (null == nonLiquid)
		{
			liquid = collector.getLiquid();
			if (null == liquid)
			{
				nonLiquid = env.special.AIR;
			}
		}
		return new Pair<>(nonLiquid, liquid);
	}

	/**
	 * Checks to see possibleBucket can be used to pick up or place a liquid in possibleSource.
	 * 
	 * @param env The environment.
	 * @param possibleBucket The item which may be a bucket.
	 * @param possibleSource The block which may be a liquid.
	 * @return True if possibleBucket can be used on this possibleSource.
	 */
	public boolean isBucketForUseOneBlock(Environment env, Item possibleBucket, Block possibleSource)
	{
		Item requiredEmptyBucket = _sourceToEmptyBucket.get(possibleSource);
		boolean isEmptyBucket = (requiredEmptyBucket == possibleBucket);
		boolean canBeReplaced = env.blocks.canBeReplaced(possibleSource);
		Block outputBlock = _fullBucketToSource.get(possibleBucket);
		boolean canBeScooped = _sourceToFullBucket.containsKey(possibleSource);
		return (isEmptyBucket && canBeScooped) || (canBeReplaced && (null != outputBlock));
	}

	/**
	 * Returns the updated bucket after using possibleBucket on possibleSource.  This could involve picking up or
	 * placing a liquid.
	 * 
	 * @param env The environment.
	 * @param possibleBucket The item which may be a bucket.
	 * @param possibleSource The block which may be a liquid.
	 * @return The updated bucket, after the action (null if this request was invalid).
	 */
	public Item bucketAfterUse(Environment env, Item possibleBucket, Block possibleSource)
	{
		Item requiredEmptyBucket = _sourceToEmptyBucket.get(possibleSource);
		boolean isEmptyBucket = (requiredEmptyBucket == possibleBucket);
		boolean canBeReplaced = env.blocks.canBeReplaced(possibleSource);
		Item bucketAfterPickup = _sourceToFullBucket.get(possibleSource);
		
		Item outputBucket;
		if (isEmptyBucket && (null != bucketAfterPickup))
		{
			// We can pick up this block as a liquid source.
			outputBucket = bucketAfterPickup;
		}
		else if (canBeReplaced && _fullBucketToSource.containsKey(possibleBucket))
		{
			// This is a full bucket and we can place it.
			// This means we need to find the empty bucket from this source type we create in placement.
			outputBucket = _sourceToEmptyBucket.get(_fullBucketToSource.get(possibleBucket));
		}
		else
		{
			// We can't apply this so null.
			outputBucket = null;
		}
		return outputBucket;
	}

	/**
	 * Returns the updated block type after using possibleBucket on possibleSource.  This could involve picking up or
	 * placing a liquid so the new block will be air or a liquid source.
	 * 
	 * @param env The environment.
	 * @param possibleBucket The item which may be a bucket.
	 * @param possibleSource The block which may be a liquid.
	 * @return The updated block type, after the action (null if this request was invalid).
	 */
	public Block blockAfterBucketUse(Environment env, Item possibleBucket, Block possibleSource)
	{
		Item requiredEmptyBucket = _sourceToEmptyBucket.get(possibleSource);
		boolean isEmptyBucket = (requiredEmptyBucket == possibleBucket);
		boolean canBeReplaced = env.blocks.canBeReplaced(possibleSource);
		Block sourceAfterDrop = _fullBucketToSource.get(possibleBucket);
		
		Block outputBlock;
		if (isEmptyBucket && _sourceToFullBucket.containsKey(possibleSource))
		{
			// We can pick up this block as a liquid source so make it air.
			outputBlock = env.special.AIR;
		}
		else if (canBeReplaced && (null != sourceAfterDrop))
		{
			// This is what we placed.
			outputBlock = sourceAfterDrop;
		}
		else
		{
			// We can't apply this so null.
			outputBlock = null;
		}
		return outputBlock;
	}

	/**
	 * Checks the number of milliseconds to wait from when a block is placed to when it can flow into neighbouring block
	 * spaces (returns the longest known delay if this isn't a liquid).
	 * 
	 * @param type The block type which may be a liquid block.
	 * @return The number of milliseconds to delay before flowing.
	 */
	public long flowDelayMillis(Block type)
	{
		return _flowDelayMillis(type);
	}

	/**
	 * Finds the minimum flow delay, in milliseconds, between 2 different blocks which may be liquids.
	 * 
	 * @param type1 One block type, which may be a liquid block.
	 * @param type2 Another block type, which may be a liquid block.
	 * @return The smallest of the two flow delays for the 2 block types.
	 */
	public long minFlowDelayMillis(Block type1, Block type2)
	{
		return Math.min(_flowDelayMillis(type1), _flowDelayMillis(type2));
	}

	/**
	 * Returns an integer representing the flow strength of the given block.  This is an integer since it is usually
	 * used in maximum value calculations or direct comparisons.
	 * 
	 * @param block The block type to check.
	 * @return The flow strength (0 if not a liquid, 1 for weak, 2 for strong, 3 for source).
	 */
	public int getFlowStrength(Block block)
	{
		return _getFlowStrength(block);
	}

	/**
	 * A basic mechanism to ask if a block type is a liquid.
	 * 
	 * @param block The block to check.
	 * @return True if this is a liquid (source or otherwise), false if not.
	 */
	public boolean isLiquid(Block block)
	{
		// If it is a source or flowing block.
		return _blocksToSource.containsKey(block);
	}

	/**
	 * Converts the common Block object into a LiquidBlock object, returning null if it is not a liquid.
	 * 
	 * @param block The block.
	 * @return The LiquidBlock or null, if not a liquid.
	 */
	public LiquidBlock liquidFromBlock(Block block)
	{
		LiquidBlock liquid = null;
		if (_blocksToStrength.containsKey(block))
		{
			// TODO:  Change this when flow distance becomes variable by source type.
			int flowStrength = _blocksToStrength.get(block);
			Assert.assertTrue(flowStrength <= FLOW_SOURCE);
			byte flowDistance = (byte)(FLOW_SOURCE - flowStrength);
			Block sourceType = _blocksToSource.get(block);
			liquid = new LiquidBlock(sourceType, flowDistance);
		}
		return liquid;
	}

	/**
	 * Converts a LiquidBlock object into a common Block object.
	 * 
	 * @param liquid The liquid (cannot be null).
	 * @return The Block.
	 */
	public Block blockFromLiquid(LiquidBlock liquid)
	{
		Block[] strengths = _sourceToFlowStrengths.get(liquid.sourceType);
		int index = FLOW_SOURCE - liquid.distance;
		return strengths[index];
	}


	private long _flowDelayMillis(Block type)
	{
		Block liquidType = _blocksToSource.get(type);
		return _getFromMap(_sourceToDelayMillis, liquidType, _defaultDelayMillis);
	}

	private static <T> T _getFromMap(Map<Block, T> map, Block key, T defaultValue)
	{
		return (null != key) ? map.getOrDefault(key, defaultValue) : defaultValue;
	}

	private int _getFlowStrength(Block block)
	{
		return (null != block)
			? _blocksToStrength.getOrDefault(block, FLOW_NONE)
			: FLOW_NONE
		;
	}


	private class _FlowCollector
	{
		private final boolean _isOnSolid;
		private Set<Block> _relevantSourceTypes;
		private Block _previousStartType;
		private Block _sourceType;
		private int _flowStrength;
		private int _adjacentSources;
		private Block _solidType;
		public _FlowCollector(LiquidBlock startLiquid, boolean isOnSolid)
		{
			_isOnSolid = isOnSolid;
			_relevantSourceTypes = new HashSet<>();
			
			// We only consider the starting value if it is a source since we will otherwise recalculate it.
			if (null != startLiquid)
			{
				_previousStartType = startLiquid.sourceType;
				if (0 == startLiquid.distance)
				{
					_sourceType = _previousStartType;
					_flowStrength = FLOW_SOURCE - startLiquid.distance;
				}
				if (null != _previousStartType)
				{
					_relevantSourceTypes.add(_previousStartType);
				}
			}
		}
		public void addAdjacent(LiquidBlock liquid, boolean fromAbove)
		{
			if (null == liquid)
			{
				// We allow this to be called with null just to simplify the caller.
			}
			else if (null != _solidType)
			{
				// Once we have set the solid type once, we assume that we are done and can ignore all other calls.
			}
			else
			{
				// We apply our logic based on source and flow strength, independently.
				Block sourceType = liquid.sourceType;
				int flowStrength = FLOW_SOURCE - liquid.distance;
				// (track the flow strength if the liquid were to flow into this block)
				int insideStrength = flowStrength - 1;
				
				if ((null != _previousStartType) && (sourceType != _previousStartType))
				{
					// This is a special-case where we allow solidification of the previous liquid, even if there was no source to maintain it.
					_solidType = _sourceToSolid.get(_previousStartType);
				}
				else if (null == _sourceType)
				{
					// We are flowing into air.
					if (fromAbove)
					{
						// Flowing from above is always weak flow unless we hit a solid, when it becomes strong.
						_sourceType = sourceType;
						if (_isOnSolid)
						{
							_flowStrength = FLOW_STRONG;
						}
						else
						{
							_flowStrength = FLOW_WEAK;
						}
					}
					else if (insideStrength > FLOW_NONE)
					{
						// This is just flowing from the side so it always tapers, and will always become weak when not on solid block.
						_sourceType = sourceType;
						if (_isOnSolid)
						{
							_flowStrength = insideStrength;
						}
						else
						{
							_flowStrength = FLOW_WEAK;
						}
						_accumulateSources(flowStrength);
					}
				}
				else if (_sourceType == sourceType)
				{
					// We are just flowing into the same type.
					_flowStrength = Math.max(_flowStrength, insideStrength);
					if (!fromAbove)
					{
						_accumulateSources(flowStrength);
					}
				}
				else
				{
					// We are colliding and need to solidify.
					// WARNING:  We are comparing the flow rate to determine which block should be "first in the block"
					// to become solidified but this won't work as well if there are more than 2 colliding liquids as it
					// will only make the decision once, based on implementation.
					Block typeToSolidify;
					if (insideStrength > FLOW_NONE)
					{
						// Both types will be "in" the block so figure out which to solidify.
						long originalFlow = _flowDelayMillis(_sourceType);
						long newFlow = _flowDelayMillis(sourceType);
						typeToSolidify = (originalFlow < newFlow)
							? _sourceType
							: sourceType
						;
					}
					else
					{
						// This new liquid is flowing to the edge, so it will solidify what is already there, unambiguously.
						typeToSolidify = _sourceType;
					}
					_solidType = _sourceToSolid.get(typeToSolidify);
				}
				
				// Handle the case where a liquid is flowing in but should be solidified by an adjacent liquid we already observed.
				_relevantSourceTypes.add(sourceType);
				if ((null == _solidType) && (null != _sourceType) && (_relevantSourceTypes.size() > 1))
				{
					// This happens when there are other liquids around this but not flowing in.
					_solidType = _sourceToSolid.get(_sourceType);
				}
			}
		}
		public Block getSolid()
		{
			return _solidType;
		}
		public LiquidBlock getLiquid()
		{
			return (null != _sourceType)
				? new LiquidBlock(_sourceType, (byte)(FLOW_SOURCE - _flowStrength))
				: null
			;
		}
		private void _accumulateSources(int flowStrength)
		{
			if (FLOW_SOURCE == flowStrength)
			{
				// If this is the second adjacent source block of a type which creates sources, create that now.
				_adjacentSources += 1;
				if ((_adjacentSources >= 2) && _sourceCreationSources.contains(_sourceType))
				{
					_flowStrength = FLOW_SOURCE;
				}
			}
		}
	}

	public static record LiquidBlock(Block sourceType
		, byte distance
	)
	{
	}
}
