package com.jeffdisher.october.aspects;

import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.utils.Assert;


/**
 * Describes the block and item types associated with liquids and liquid behaviours.
 */
public class LiquidRegistry
{
	// TODO:  Remove these ivars once this is pulled from a data file.
	private final Block _waterSource;
	private final Block _waterStrong;
	private final Block _waterWeak;
	private final Block _lavaSource;
	private final Block _lavaStrong;
	private final Block _lavaWeak;

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
		_bucketEmpty = items.getItemById("op.bucket_empty");
		_bucketWater = items.getItemById("op.bucket_water");
		_bucketLava = items.getItemById("op.bucket_lava");
	}

	public boolean isSource(Block block)
	{
		return (_waterSource == block) || (_lavaSource == block);
	}

	public Block chooseEmptyLiquidBlock(Environment env, Block east, Block west, Block north, Block south, Block above, Block below)
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
		
		boolean isWater = (types[0] + types[1] + types[2]) > 0;
		boolean isLava = (types[3] + types[4] + types[5]) > 0;
		
		int offset = isLava ? 3 : 0;
		int strength = 0;
		if (isWater && isLava)
		{
			// This is a conflict which we currently treat as "air".
			strength = 0;
		}
		else if (isWater && (types[offset] >= 2))
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
		
		int aboveStrength = 0;
		int aboveStrengthWater = _index(above, null, _waterWeak, _waterStrong, _waterSource);
		int aboveStrengthLava = _index(above, null, _lavaWeak, _lavaStrong, _lavaSource);
		if (aboveStrengthWater > 0)
		{
			// Water is falling onto us.
			if (isLava)
			{
				isLava = false;
				strength = 0;
			}
			else
			{
				isWater = true;
				aboveStrength = aboveStrengthWater;
			}
		}
		if (aboveStrengthLava > 0)
		{
			// Lava is falling onto us.
			if (isWater)
			{
				isWater = false;
				strength = 0;
			}
			else
			{
				isLava = true;
				aboveStrength = aboveStrengthLava;
			}
		}
		
		if (-1 == aboveStrength)
		{
			aboveStrength = 0;
		}
		if ((null != below) && env.blocks.canBeReplaced(below))
		{
			// Empty.
			strength = Math.max(strength, aboveStrength);
		}
		else
		{
			// Solid block so we make this strong flow if up is any water type.
			if ((aboveStrength > 0) && (strength < 2))
			{
				strength = 2;
			}
		}
		
		Block type;
		switch (strength)
		{
		case 3:
			type = isWater ? _waterSource : _lavaSource;
			break;
		case 2:
			type = isWater ? _waterStrong : _lavaStrong;
			break;
		case 1:
			type = isWater ? _waterWeak : _lavaWeak;
			break;
		case 0:
			type = null;
			break;
			default:
				throw Assert.unreachable();
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
				? 100L
				: isLava
					? 1000L
					: 0L
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
