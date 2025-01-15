package com.jeffdisher.october.aspects;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Item;


/**
 * Describes the block and item types associated with liquids and liquid behaviours.
 */
public class LiquidRegistry
{
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

	public LiquidRegistry(ItemRegistry items, BlockAspect blocks)
	{
		// TODO:  Source these from a data file.
		Block waterSource = blocks.fromItem(items.getItemById("op.water_source"));
		Block waterStrong = blocks.fromItem(items.getItemById("op.water_strong"));
		Block waterWeak = blocks.fromItem(items.getItemById("op.water_weak"));
		Block lavaSource = blocks.fromItem(items.getItemById("op.lava_source"));
		Block lavaStrong = blocks.fromItem(items.getItemById("op.lava_strong"));
		Block lavaWeak = blocks.fromItem(items.getItemById("op.lava_weak"));
		
		Block blockStone = blocks.fromItem(items.getItemById("op.stone"));
		Block blockBasalt = blocks.fromItem(items.getItemById("op.basalt"));
		
		Item bucketEmpty = items.getItemById("op.bucket_empty");
		Item bucketWater = items.getItemById("op.bucket_water");
		Item bucketLava = items.getItemById("op.bucket_lava");
		
		_blocksToSource = Map.of(waterSource, waterSource
				, waterStrong, waterSource
				, waterWeak, waterSource
				
				, lavaSource, lavaSource
				, lavaStrong, lavaSource
				, lavaWeak, lavaSource
		);
		_blocksToStrength = Map.of(waterSource, 3
				, waterStrong, 2
				, waterWeak, 1
				
				, lavaSource, 3
				, lavaStrong, 2
				, lavaWeak, 1
		);
		_sourceToSolid = Map.of(waterSource, blockStone
				, lavaSource, blockBasalt
		);
		_sourceToFlowStrengths = Map.of(waterSource, new Block[] {null, waterWeak, waterStrong, waterSource}
				, lavaSource, new Block[] {null, lavaWeak, lavaStrong, lavaSource}
		);
		_sourceToDelayMillis = Map.of(waterSource, 100L
				, lavaSource, 1000L
		);
		_sourceCreationSources = Set.of(waterSource);
		_fullBucketToSource = Map.of(bucketWater, waterSource
				, bucketLava, lavaSource
		);
		_sourceToFullBucket = Map.of(waterSource, bucketWater
				, lavaSource, bucketLava
		);
		_bucketEmpty = bucketEmpty;
		_defaultDelayMillis = _sourceToDelayMillis.values().stream().max((Long one, Long two) -> (int)(one - two)).get();
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
