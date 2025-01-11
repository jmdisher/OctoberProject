package com.jeffdisher.october.aspects;

import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.utils.Assert;


/**
 * Describes the block and item types associated with liquids and liquid behaviours.
 */
public class LiquidRegistry
{
	// TODO:  Generalize this in the future by moving this into data.
	public static final long FLOW_DELAY_MILLIS_WATER = 100L;
	public static final long FLOW_DELAY_MILLIS_LAVA  = 1000L;
	public static final long FLOW_DELAY_MILLIS_SOLID = 1000L;

	// TODO:  Remove these ivars once this is pulled from a data file.
	private final Block _waterSource;
	private final Block _waterStrong;
	private final Block _waterWeak;
	private final Block _lavaSource;
	private final Block _lavaStrong;
	private final Block _lavaWeak;

	private final Block _blockStone;
	private final Block _blockBasalt;

	private final Item _bucketEmpty;
	private final Item _bucketWater;
	private final Item _bucketLava;

	public LiquidRegistry(ItemRegistry items, BlockAspect blocks)
	{
		_waterSource = blocks.fromItem(items.getItemById("op.water_source"));
		_waterStrong = blocks.fromItem(items.getItemById("op.water_strong"));
		_waterWeak = blocks.fromItem(items.getItemById("op.water_weak"));
		_lavaSource = blocks.fromItem(items.getItemById("op.lava_source"));
		_lavaStrong = blocks.fromItem(items.getItemById("op.lava_strong"));
		_lavaWeak = blocks.fromItem(items.getItemById("op.lava_weak"));
		
		_blockStone = blocks.fromItem(items.getItemById("op.stone"));
		_blockBasalt = blocks.fromItem(items.getItemById("op.basalt"));
		
		_bucketEmpty = items.getItemById("op.bucket_empty");
		_bucketWater = items.getItemById("op.bucket_water");
		_bucketLava = items.getItemById("op.bucket_lava");
	}

	public boolean isSource(Block block)
	{
		return (_waterSource == block) || (_lavaSource == block);
	}

	/**
	 * Determines the correct block should be given the current block state and the surrounding blocks.
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
		// An "empty" block is one which is left over after breaking a block.
		// It is usually not a liquid type (so return null).
		// Rules for the empty type:
		// -check 4 horizontal blocks, if there are >=2 sources, create a source, otherwise take the max - 1
		// -check the block above and below, if the block below is empty, take the same as above, if not, take strong flow
		// We will only apply either water OR lava, currently treating a conflict as "air".
		int[] types = new int[6];
		_checkBlock(east, types, _waterSource, _waterStrong, _waterWeak, _lavaSource, _lavaStrong, _lavaWeak);
		_checkBlock(west, types, _waterSource, _waterStrong, _waterWeak, _lavaSource, _lavaStrong, _lavaWeak);
		_checkBlock(north, types, _waterSource, _waterStrong, _waterWeak, _lavaSource, _lavaStrong, _lavaWeak);
		_checkBlock(south, types, _waterSource, _waterStrong, _waterWeak, _lavaSource, _lavaStrong, _lavaWeak);
		
		boolean isWaterSource = (currentBlock == _waterSource);
		boolean isLavaSource = (currentBlock == _lavaSource);
		boolean currentlyWater = isWaterSource || (currentBlock == _waterStrong) || (currentBlock == _waterWeak);
		boolean currentlyLava = isLavaSource || (currentBlock == _lavaStrong) || (currentBlock == _lavaWeak);
		boolean adjacentToWater = (types[0] + types[1] + types[2]) > 0;
		boolean adjacentToLava = (types[3] + types[4] + types[5]) > 0;
		
		int offset = adjacentToLava ? 3 : 0;
		int strength = 0;
		if (adjacentToWater && adjacentToLava)
		{
			// This is a conflict which we currently treat as "air".
			strength = 0;
		}
		else if (adjacentToWater && (types[offset] >= 2))
		{
			// We have at lest 2 adjacent sources, so make this a source.
			// (we only generate sources with water).
			strength = 3;
		}
		else if (types[offset] >= 1)
		{
			// We are adjacent to at least 1 source, so we want to be strong flow.
			strength = 2;
		}
		else if (types[offset + 1] >= 1)
		{
			// We are adjacent to at least 1 strong flow, so we want to be weak flow.
			strength = 1;
		}
		
		// Account for if this is already a source.
		if ((adjacentToWater && isWaterSource && (strength > 0))
				|| (adjacentToLava && isLavaSource && (strength > 0))
		)
		{
			strength = 3;
		}
		
		int aboveStrength = 0;
		Block solidType = null;
		int aboveStrengthWater = _index(above, null, _waterWeak, _waterStrong, _waterSource);
		int aboveStrengthLava = _index(above, null, _lavaWeak, _lavaStrong, _lavaSource);
		if (aboveStrengthWater > 0)
		{
			// Water is falling onto us.
			if (adjacentToLava || currentlyLava)
			{
				adjacentToLava = false;
				strength = 0;
				solidType = _blockBasalt;
			}
			else
			{
				adjacentToWater = true;
				// Flow from above is always strong.
				aboveStrength = 2;
			}
		}
		if (aboveStrengthLava > 0)
		{
			// Lava is falling onto us.
			if (adjacentToWater || currentlyWater)
			{
				adjacentToWater = false;
				strength = 0;
				solidType = _blockStone;
			}
			else
			{
				adjacentToLava = true;
				// Flow from above is always strong.
				aboveStrength = 2;
			}
		}
		
		if (null == solidType)
		{
			if (-1 == aboveStrength)
			{
				aboveStrength = 0;
			}
			strength = Math.max(strength, aboveStrength);
		}
		
		Block type;
		if (null != solidType)
		{
			// We decided to solidify.
			type = solidType;
		}
		else
		{
			// We aren't going to solidify so just see what this should be.
			switch (strength)
			{
			case 3:
				type = adjacentToWater ? _waterSource : _lavaSource;
				break;
			case 2:
				type = adjacentToWater ? _waterStrong : _lavaStrong;
				break;
			case 1:
				type = adjacentToWater ? _waterWeak : _lavaWeak;
				break;
			case 0:
				type = isWaterSource
					? _waterSource
					: isLavaSource
						? _lavaSource
						: env.special.AIR
				;
				break;
				default:
					throw Assert.unreachable();
			}
		}
		return type;
	}

	public boolean isBucketForUseOneBlock(Environment env, Item possibleBucket, Block possibleSource)
	{
		boolean isEmptyBucket = (_bucketEmpty == possibleBucket);
		boolean isWaterBucket = (_bucketWater == possibleBucket);
		boolean isLavaBucket = (_bucketLava == possibleBucket);
		boolean isWaterSource = (_waterSource == possibleSource);
		boolean isLavaSource = (_lavaSource == possibleSource);
		boolean canBeReplaced = env.blocks.canBeReplaced(possibleSource);
		return (isEmptyBucket && (isWaterSource || isLavaSource))
				|| (canBeReplaced && (isWaterBucket || isLavaBucket))
		;
	}

	public Item bucketAfterUse(Environment env, Item possibleBucket, Block possibleSource)
	{
		boolean isEmptyBucket = (_bucketEmpty == possibleBucket);
		boolean isWaterBucket = (_bucketWater == possibleBucket);
		boolean isLavaBucket = (_bucketLava == possibleBucket);
		boolean isWaterSource = (_waterSource == possibleSource);
		boolean isLavaSource = (_lavaSource == possibleSource);
		boolean canBeReplaced = env.blocks.canBeReplaced(possibleSource);
		
		Item bucketOutput = null;
		if (isEmptyBucket)
		{
			if (isWaterSource)
			{
				bucketOutput = _bucketWater;
			}
			else if (isLavaSource)
			{
				bucketOutput = _bucketLava;
			}
		}
		else if (canBeReplaced && (isWaterBucket || isLavaBucket))
		{
			bucketOutput = _bucketEmpty;
		}
		return bucketOutput;
	}

	public Block blockAfterBucketUse(Environment env, Item possibleBucket, Block possibleSource)
	{
		boolean isEmptyBucket = (_bucketEmpty == possibleBucket);
		boolean isWaterBucket = (_bucketWater == possibleBucket);
		boolean isLavaBucket = (_bucketLava == possibleBucket);
		boolean isWaterSource = (_waterSource == possibleSource);
		boolean isLavaSource = (_lavaSource == possibleSource);
		boolean canBeReplaced = env.blocks.canBeReplaced(possibleSource);
		
		Block blockOutput = null;
		if (isEmptyBucket && (isWaterSource || isLavaSource))
		{
			blockOutput = env.special.AIR;
		}
		else if (canBeReplaced)
		{
			if (isWaterBucket)
			{
				blockOutput = _waterSource;
			}
			else if (isLavaBucket)
			{
				blockOutput = _lavaSource;
			}
		}
		return blockOutput;
	}

	public long flowDelayMillis(Environment env, Block type)
	{
		// TODO:  Generalize this in the future by moving this into data.
		boolean isWater = (_waterSource == type) || (_waterStrong == type) || (_waterWeak == type);
		boolean isLava = (_lavaSource == type) || (_lavaStrong == type) || (_lavaWeak == type);
		return isWater
				? FLOW_DELAY_MILLIS_WATER
				: isLava
					? FLOW_DELAY_MILLIS_LAVA
					: FLOW_DELAY_MILLIS_SOLID
		;
	}


	private static void _checkBlock(Block block, int[] types, Block... blocks)
	{
		int index = _index(block, blocks);
		if (index >= 0)
		{
			types[index] += 1;
		}
	}

	private static int _index(Block block, Block... blocks)
	{
		int index = -1;
		for (int i = 0; i < blocks.length; ++i)
		{
			if (block == blocks[i])
			{
				index = i;
				break;
			}
		}
		return index;
	}
}
