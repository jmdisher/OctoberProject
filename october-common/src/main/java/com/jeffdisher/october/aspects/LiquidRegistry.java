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

	private final Item _bucketEmpty;
	private final Item _bucketWater;

	public LiquidRegistry(ItemRegistry items, BlockAspect blocks)
	{
		_waterSource = blocks.fromItem(items.getItemById("op.water_source"));
		_waterStrong = blocks.fromItem(items.getItemById("op.water_strong"));
		_waterWeak = blocks.fromItem(items.getItemById("op.water_weak"));
		_bucketEmpty = items.getItemById("op.bucket_empty");
		_bucketWater = items.getItemById("op.bucket_water");
	}

	public boolean isSource(Block block)
	{
		return (_waterSource == block);
	}

	public Block chooseEmptyLiquidBlock(Environment env, Block east, Block west, Block north, Block south, Block above, Block below)
	{
		// An "empty" block is one which is left over after breaking a block.
		// It is usually not a liquid type (so return null).
		// Rules for the empty type:
		// -check 4 horizontal blocks, if there are >=2 sources, create a source, otherwise take the max - 1
		// -check the block above and below, if the block below is empty, take the same as above, if not, take strong flow
		int[] types = new int[3];
		_checkBlock(east, types, _waterSource, _waterStrong, _waterWeak);
		_checkBlock(west, types, _waterSource, _waterStrong, _waterWeak);
		_checkBlock(north, types, _waterSource, _waterStrong, _waterWeak);
		_checkBlock(south, types, _waterSource, _waterStrong, _waterWeak);
		
		int strength = 0;
		if (types[0] >= 2)
		{
			// We have at lest 2 adjacent sources, so make this a source.
			strength = 3;
		}
		else if (types[0] >= 1)
		{
			// We are adjacent to at least 1 source, so we want to be strong flow.
			strength = 2;
		}
		else if (types[1] >= 1)
		{
			// We are adjacent to at least 1 strong flow, so we want to be weak flow.
			strength = 1;
		}
		
		int aboveStrength = _index(above, null, _waterWeak, _waterStrong, _waterSource);
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
			type = _waterSource;
			break;
		case 2:
			type = _waterStrong;
			break;
		case 1:
			type = _waterWeak;
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
		boolean isWaterSource = (_waterSource == possibleSource);
		boolean canBeReplaced = env.blocks.canBeReplaced(possibleSource);
		return (isEmptyBucket && isWaterSource)
				|| (isWaterBucket && canBeReplaced)
		;
	}

	public Item bucketAfterUse(Environment env, Item possibleBucket, Block possibleSource)
	{
		boolean isEmptyBucket = (_bucketEmpty == possibleBucket);
		boolean isWaterBucket = (_bucketWater == possibleBucket);
		boolean isWaterSource = (_waterSource == possibleSource);
		boolean canBeReplaced = env.blocks.canBeReplaced(possibleSource);
		
		Item bucketOutput = null;
		if (isEmptyBucket && isWaterSource)
		{
			bucketOutput = _bucketWater;
		}
		else if (isWaterBucket && canBeReplaced)
		{
			bucketOutput = _bucketEmpty;
		}
		return bucketOutput;
	}

	public Block blockAfterBucketUse(Environment env, Item possibleBucket, Block possibleSource)
	{
		boolean isEmptyBucket = (_bucketEmpty == possibleBucket);
		boolean isWaterBucket = (_bucketWater == possibleBucket);
		boolean isWaterSource = (_waterSource == possibleSource);
		boolean canBeReplaced = env.blocks.canBeReplaced(possibleSource);
		
		Block blockOutput = null;
		if (isEmptyBucket && isWaterSource)
		{
			blockOutput = env.special.AIR;
		}
		else if (isWaterBucket && canBeReplaced)
		{
			blockOutput = _waterSource;
		}
		return blockOutput;
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
