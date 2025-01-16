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
import com.jeffdisher.october.utils.Assert;


/**
 * Describes the block and item types associated with liquids and liquid behaviours.
 */
public class LiquidRegistry
{
	public static final String FLAG_CREATES_SOURCE = "creates_source";
	public static final String SUB_FLOW_DELAY_MILLIS = "flow_delay_millis";
	public static final String SUB_FULL_BUCKET = "full_bucket";
	public static final String SUB_STRONG_FLOW = "strong_flow";
	public static final String SUB_WEAK_FLOW = "weak_flow";
	public static final String SUB_SOLID_BLOCK = "solid_block";


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
		
		Item bucketEmpty = items.getItemById("op.bucket_empty");
		return new LiquidRegistry(blocksToSource
			, blocksToStrength
			, sourceToSolid
			, sourceToFlowStrengths
			, sourceToDelayMillis
			, sourceCreationSources
			, fullBucketToSource
			, sourceToFullBucket
			, bucketEmpty
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
	private final Item _bucketEmpty;
	private final long _defaultDelayMillis;

	private LiquidRegistry(Map<Block, Block> blocksToSource
			, Map<Block, Integer> blocksToStrength
			, Map<Block, Block> sourceToSolid
			, Map<Block, Block[]> sourceToFlowStrengths
			, Map<Block, Long> sourceToDelayMillis
			, Set<Block> sourceCreationSources
			, Map<Item, Block> fullBucketToSource
			, Map<Block, Item> sourceToFullBucket
			, Item bucketEmpty
	)
	{
		Assert.assertTrue(null != bucketEmpty);
		Assert.assertTrue(!sourceToDelayMillis.isEmpty());
		
		_blocksToSource = Map.copyOf(blocksToSource);
		_blocksToStrength = Map.copyOf(blocksToStrength);
		_sourceToSolid = Map.copyOf(sourceToSolid);
		_sourceToFlowStrengths = Map.copyOf(sourceToFlowStrengths);
		_sourceToDelayMillis = Map.copyOf(sourceToDelayMillis);
		_sourceCreationSources = Set.copyOf(sourceCreationSources);
		_fullBucketToSource = Map.copyOf(fullBucketToSource);
		_sourceToFullBucket = Map.copyOf(sourceToFullBucket);
		_bucketEmpty = bucketEmpty;
		_defaultDelayMillis = sourceToDelayMillis.values().stream().max((Long one, Long two) -> (int)(one - two)).get();
	}

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
	 * @param currentBlock The current block type.
	 * @param east Block to the East.
	 * @param west Block to the West.
	 * @param north Block to the North.
	 * @param south Block to the South.
	 * @param above Block above.
	 * @param below Block below.
	 * @return The block type (never null).
	 */
	public Block chooseEmptyLiquidBlock(Environment env, Block currentBlock, Block east, Block west, Block north, Block south, Block above, Block below)
	{
		// This takes a few steps:
		// -check if horizontal liquids should act on currentBlock
		// -check if vertical liquids should act on currentBlock
		// -update currentBlock based on horizontal adjacent blocks
		// -apply vertical liquid to the updated currentBlock
		Block updatedBlock;
		if (!env.blocks.canBeReplaced(currentBlock))
		{
			// This can't be replaced so it isn't a liquid or air.
			updatedBlock = currentBlock;
		}
		else
		{
			// Since we need to work on this block, see what type it is.
			Block currentType = _blocksToSource.get(currentBlock);
			boolean isAboveSolidBlock = !env.blocks.canBeReplaced(below);
			boolean isCurrentlySource = _sourceToSolid.containsKey(currentBlock);
			
			// We first need to see what the adjacent horizontal blocks thing should be selected.
			Block horizontalBlock = _candidateBlockHorizontal(env, east, west, north, south, above, isAboveSolidBlock);
			Block horizontalType = _blocksToSource.get(horizontalBlock);
			
			// Check to see if this is a conflict to see what we should pass on to the next step.
			Block newBlock;
			Block blockToConflict;
			if (currentType == horizontalType)
			{
				// We agree so pick the new one unless the first is a source.
				newBlock = isCurrentlySource
						? currentBlock
						: horizontalBlock
				;
				blockToConflict = newBlock;
			}
			else if (env.special.AIR == horizontalBlock)
			{
				// This means it should be nothing so just consider what we had for the conflict and only keep what we had if it was a source.
				newBlock = isCurrentlySource
						? currentBlock
						: horizontalBlock
				;
				blockToConflict = currentBlock;
			}
			else if (env.special.AIR == currentBlock)
			{
				// This means we are replacing what was there so just consider this new block, directly.
				newBlock = horizontalBlock;
				blockToConflict = horizontalBlock;
			}
			else if (!env.blocks.canBeReplaced(horizontalBlock))
			{
				// Horizontal is suggesting solidification so we will use that end now.
				newBlock = horizontalBlock;
				blockToConflict = horizontalBlock;
			}
			else
			{
				// There is some kind of conflict so convert what was into a solid and we are done.
				newBlock = _sourceToSolid.get(currentType);
				blockToConflict = newBlock;
			}
			
			// If we aren't yet forming a solid, check what is above the block.
			if (env.blocks.canBeReplaced(blockToConflict))
			{
				Block verticalBlock = _candidateBlockVertical(env, above, isAboveSolidBlock);
				Block conflictType = _blocksToSource.get(blockToConflict);
				Block verticalType = _blocksToSource.get(verticalBlock);
				if (conflictType == verticalType)
				{
					// We agree so pick the new one unless the first is a source.
					updatedBlock = _sourceToSolid.containsKey(newBlock)
							? newBlock
							: verticalBlock
					;
				}
				else if (env.special.AIR == verticalBlock)
				{
					// This means it should be nothing so just consider what we had for the conflict.
					updatedBlock = newBlock;
				}
				else if (env.special.AIR == blockToConflict)
				{
					// This means we are replacing what was there so just consider what is falling.
					updatedBlock = verticalBlock;
				}
				else
				{
					// There is some kind of conflict so convert what was into a solid and we are done.
					updatedBlock = _sourceToSolid.get(conflictType);
				}
			}
			else
			{
				// We already know the final state.
				updatedBlock = newBlock;
			}
		}
		return updatedBlock;
	}

	public boolean isBucketForUseOneBlock(Environment env, Item possibleBucket, Block possibleSource)
	{
		boolean isEmptyBucket = (_bucketEmpty == possibleBucket);
		boolean canBeReplaced = env.blocks.canBeReplaced(possibleSource);
		Block outputBlock = _fullBucketToSource.get(possibleBucket);
		boolean canBeScooped = _sourceToFullBucket.containsKey(possibleSource);
		return (isEmptyBucket && canBeScooped) || (canBeReplaced && (null != outputBlock));
	}

	public Item bucketAfterUse(Environment env, Item possibleBucket, Block possibleSource)
	{
		boolean isEmptyBucket = (_bucketEmpty == possibleBucket);
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
			outputBucket = _bucketEmpty;
		}
		else
		{
			// We can't apply this so null.
			outputBucket = null;
		}
		return outputBucket;
	}

	public Block blockAfterBucketUse(Environment env, Item possibleBucket, Block possibleSource)
	{
		boolean isEmptyBucket = (_bucketEmpty == possibleBucket);
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

	public long flowDelayMillis(Environment env, Block type)
	{
		return _flowDelayMillis(env, type);
	}

	public long minFlowDelayMillis(Environment env, Block type1, Block type2)
	{
		return Math.min(_flowDelayMillis(env, type1), _flowDelayMillis(env, type2));
	}


	private long _flowDelayMillis(Environment env, Block type)
	{
		Block liquidType = _blocksToSource.get(type);
		return _getFromMap(_sourceToDelayMillis, liquidType, _defaultDelayMillis);
	}

	private Block _candidateBlockHorizontal(Environment env, Block east, Block west, Block north, Block south, Block above, boolean isAboveSolidBlock)
	{
		// Decide what block the horizontal blocks suggest.
		// Check the adjacent blocks.
		Block eastType = _getFromMap(_blocksToSource, east, null);
		Block westType = _getFromMap(_blocksToSource, west, null);
		Block northType = _getFromMap(_blocksToSource, north, null);
		Block southType = _getFromMap(_blocksToSource, south, null);
		
		Set<Block> adjacentTypes = new HashSet<>();
		if (null != eastType)
		{
			adjacentTypes.add(eastType);
		}
		if (null != westType)
		{
			adjacentTypes.add(westType);
		}
		if (null != northType)
		{
			adjacentTypes.add(northType);
		}
		if (null != southType)
		{
			adjacentTypes.add(southType);
		}
		
		int size = adjacentTypes.size();
		Block horizontalBlock;
		if (0 == size)
		{
			// There is nothing so suggest an air block.
			horizontalBlock = env.special.AIR;
		}
		else if (1 == size)
		{
			// This might become a liquid or it might remain air (if liquid is weak).
			int eastStrength = _getFromMap(_blocksToStrength, east, 0);
			int westStrength = _getFromMap(_blocksToStrength, west, 0);
			int northStrength = _getFromMap(_blocksToStrength, north, 0);
			int southStrength = _getFromMap(_blocksToStrength, south, 0);
			int maxStrength = Math.max(Math.max(eastStrength, westStrength), Math.max(northStrength, southStrength));
			if (maxStrength > 1)
			{
				// This is going to become the liquid so see if this becomes a source.
				Block sourceType = adjacentTypes.iterator().next();
				int idealStrength;
				if (3 == maxStrength)
				{
					// We have at least one adjacent source so see if this should be a source.
					
					if (_sourceCreationSources.contains(sourceType))
					{
						int sourceCount = 0;
						if (3 == eastStrength)
						{
							sourceCount += 1;
						}
						if (3 == westStrength)
						{
							sourceCount += 1;
						}
						if (3 == northStrength)
						{
							sourceCount += 1;
						}
						if (3 == southStrength)
						{
							sourceCount += 1;
						}
						if (sourceCount >= 2)
						{
							idealStrength = 3;
						}
						else
						{
							idealStrength = 2;
						}
					}
					else
					{
						idealStrength = 2;
					}
				}
				else
				{
					idealStrength = maxStrength - 1;
				}
				// Account for whether or not this is above an open space for strong flow.
				if (!isAboveSolidBlock && (2 == idealStrength))
				{
					idealStrength = 1;
				}
				horizontalBlock = _sourceToFlowStrengths.get(sourceType)[idealStrength];
			}
			else
			{
				// The flow is too weak so this is air.
				horizontalBlock = env.special.AIR;
			}
		}
		else
		{
			// This will either solidify or remain air (if liquid is weak).
			// Check the fastest flow type of strength at least 2 and solidify that (air if none are >= 2).
			long chosenFlowRate = Long.MAX_VALUE;
			Block chosenBlock = env.special.AIR;
			if (_blocksToStrength.getOrDefault(east, 0) >= 2)
			{
				long flow = _sourceToDelayMillis.get(eastType);
				if (flow < chosenFlowRate)
				{
					chosenFlowRate = flow;
					chosenBlock = _sourceToSolid.get(eastType);
				}
			}
			if (_blocksToStrength.getOrDefault(west, 0) >= 2)
			{
				long flow = _sourceToDelayMillis.get(westType);
				if (flow < chosenFlowRate)
				{
					chosenFlowRate = flow;
					chosenBlock = _sourceToSolid.get(westType);
				}
			}
			if (_blocksToStrength.getOrDefault(north, 0) >= 2)
			{
				long flow = _sourceToDelayMillis.get(northType);
				if (flow < chosenFlowRate)
				{
					chosenFlowRate = flow;
					chosenBlock = _sourceToSolid.get(northType);
				}
			}
			if (_blocksToStrength.getOrDefault(south, 0) >= 2)
			{
				long flow = _sourceToDelayMillis.get(southType);
				if (flow < chosenFlowRate)
				{
					chosenFlowRate = flow;
					chosenBlock = _sourceToSolid.get(southType);
				}
			}
			horizontalBlock = chosenBlock;
		}
		return horizontalBlock;
	}

	private Block _candidateBlockVertical(Environment env, Block above, boolean isAboveSolidBlock)
	{
		// See what what is flowing from above and if it interacts with what we have here.
		Block aboveType = _getFromMap(_blocksToSource, above, null);
		Block verticalBlock;
		if (null != aboveType)
		{
			// Above is always a weak flow unless it hits a solid block.
			int strength = isAboveSolidBlock ? 2 : 1;
			verticalBlock = _sourceToFlowStrengths.get(aboveType)[strength];
		}
		else
		{
			// Just default to air.
			verticalBlock = env.special.AIR;
		}
		return verticalBlock;
	}

	private static <T> T _getFromMap(Map<Block, T> map, Block key, T defaultValue)
	{
		return (null != key) ? map.getOrDefault(key, defaultValue) : defaultValue;
	}
}
