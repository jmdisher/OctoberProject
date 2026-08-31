package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.mutations.CommonBlockMutationHelpers;
import com.jeffdisher.october.mutations.MutationBlockOverwriteMisc;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.IBlockProxy;
import com.jeffdisher.october.types.IMutableBlockProxy;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Helpers for managing plant growth.
 */
public class PlantHelpers
{
	public static final byte MIN_LIGHT = 5;

	// Constants related to branch growth.
	public static final byte BRANCH_GROWTH_COUNT = 3;
	public static final byte BRANCH_CHOICE_DIVISOR = 100;
	public static final byte BRANCH_CHANCE_UP = 50;
	public static final byte BRANCH_CHANCE_SIDE = 20;
	public static final byte BRANCH_CHANCE_PER_GROWTH = 10;
	public static final byte BRANCH_CHANCE_BONUS_DIRECTION = 40;

	public static final byte BLOCK_DRY_BYTE = 0x0;
	public static final byte BLOCK_HYDRATED_BYTE = 0x1;

	/**
	 * Used to check if the given block type is one which can grow.
	 * 
	 * @param env The environment.
	 * @param block The existing block type.
	 * @return True if this block type can grow.
	 */
	public static boolean canGrow(Environment env, Block block)
	{
		int growthDivisor = env.plants.growthDivisor(block);
		return (growthDivisor > 0);
	}

	/**
	 * Attempts to perform a growth operation on the given newBlock.  Note that this will change newBlock to apply the
	 * growth (could be BLOCK or BLOCK_DEFINED_BYTE, for example).
	 * 
	 * @param env The environment.
	 * @param context The context.
	 * @param location The location where the growth is happening.
	 * @param newBlock The mutable block which we should grow (block type can be modified).
	 */
	public static void runPlantPeriodic(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy newBlock)
	{
		int growthDivisor = env.plants.growthDivisor(newBlock.getBlock());
		// This MUST be something which can grow.
		Assert.assertTrue(growthDivisor > 0);
		
		// See if this type of block requires light in order to grow.
		boolean isLit = env.plants.requiresLight(newBlock.getBlock())
			? ((newBlock.getLight() >= MIN_LIGHT) || (context.skyLight.lookup(location) >= MIN_LIGHT))
			: true
		;
		
		// See if the random generator says we should grow this tick or try again later.
		int randomBits = context.randomInt.applyAsInt(growthDivisor);
		
		boolean canGrow = isLit && (1 == randomBits);
		if (canGrow)
		{
			_doGrowth(env, context, location, newBlock);
		}
	}

	/**
	 * Performs a growth operation on the given newBlock.
	 * 
	 * @param env The environment.
	 * @param context The context.
	 * @param location The location where the growth is happening.
	 * @param newBlock The mutable block which we should grow (block type can be modified).
	 */
	public static void performForcedGrow(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy newBlock)
	{
		int growthDivisor = env.plants.growthDivisor(newBlock.getBlock());
		// This MUST be something which can grow.
		Assert.assertTrue(growthDivisor > 0);
		_doGrowth(env, context, location, newBlock);
	}

	/**
	 * Attempts to update the hydrated status of the current tilled soil block (this MUST be tilled soil).  Note that
	 * this will change newBlock to update the hydrated status (in BLOCK_DEFINED_BYTE).
	 * 
	 * @param env The environment.
	 * @param context The context.
	 * @param location The location where the check is happening.
	 * @param newBlock The mutable block which we should check (only BLOCK_DEFINED_BYTE is modified).
	 */
	public static void runSoilHydrationPeriodic(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy newBlock)
	{
		// Verify that this is only called on tilled soil.
		Assert.assertTrue(env.special.blockTilledSoil == newBlock.getBlock());
		
		// We want to look for any water block within a 4-block square in the same z-level of this block.
		List<AbsoluteLocation> locations = new ArrayList<>();
		for (int y = -4; y <= 4; ++y)
		{
			for (int x = -4; x <= 4; ++x)
			{
				AbsoluteLocation one = location.getRelative(x, y, 0);
				locations.add(one);
			}
		}
		
		// TODO:  This is very expensive so we might want to add a search helper and maybe some kind of cached result since we always recheck this when wet.
		boolean foundWater = false;
		Map<AbsoluteLocation, BlockProxy> proxies = context.previousBlockLookUp.readBlockBatch(locations);
		for (BlockProxy proxy : proxies.values())
		{
			if (env.special.blockWaterSource == proxy.getBlock())
			{
				foundWater = true;
				break;
			}
		}
		
		// We use the block-defined byte for this, but only the lowest bit (maybe use the others for fertilizer, in the future).
		// NOTE:  This is done instead of a new block type (since that doesn't seem quite right) or a new flag (since that would only apply to this case).
		byte expectedByte = foundWater ? BLOCK_HYDRATED_BYTE : BLOCK_DRY_BYTE;
		if (newBlock.getBlockDefinedByte() != expectedByte)
		{
			newBlock.setBlockDefinedByte(expectedByte);
		}
	}

	/**
	 * Checks if the block in proxy is a hydrated tilled soil block, returning false if not both are true.
	 * 
	 * @param env The environment.
	 * @param proxy The proxy to read.
	 * @return True if this block is both tilled soil and hydrated.
	 */
	public static boolean isSoilHydrated(Environment env, IBlockProxy proxy)
	{
		return _isSoilHydrated(env, proxy);
	}


	private static void _doGrowth(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy newBlock)
	{
		Block block = newBlock.getBlock();
		
		// Check the different ways that this might need to grow.
		if (env.plants.isTree(block))
		{
			_growTree(context, location, newBlock);
		}
		else if (env.plants.isBranch(block))
		{
			_growBranch(context, location, newBlock);
		}
		else
		{
			// If not a tree, we are using the staged growth system so load our count of completed growth stages.
			byte growthPhasesCompleted = newBlock.getBlockDefinedByte();
			
			// These all depend on sitting on hydrated tilled soil so check that it is hydrated before growing.
			// (in the future, we probably want this to be part of the plant configuration).
			BlockProxy belowProxy = context.previousBlockLookUp.readBlock(location.getRelative(0, 0, -1));
			boolean isHydrated = _isSoilHydrated(env, belowProxy);
			
			if (isHydrated)
			{
				// We will add 1 and check if this means we are done.
				growthPhasesCompleted += 1;
				if (growthPhasesCompleted >= env.plants.growthStagesForPlant(block))
				{
					// Replace this with the mature block type.
					Block matureBlock = env.plants.matureBlockForPlant(block);
					CommonBlockMutationHelpers.setBlockWithFollowUps(env, context, location, newBlock, matureBlock);
				}
				else
				{
					// Not done yet so just resave the growth progress.
					newBlock.setBlockDefinedByte(growthPhasesCompleted);
				}
			}
		}
	}

	private static void _growTree(TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy newBlock)
	{
		Environment env = Environment.getShared();
		Block log = env.special.blockLog;
		Block branch = env.special.blockBranch;
		AbsoluteLocation branchLocation = location.getRelative(0, 0, 1);
		// Replace this with a log for the trunk base and a branch to start random growth.
		
		CommonBlockMutationHelpers.setBlockWithFollowUps(env, context, location, newBlock, log);
		MutationBlockOverwriteMisc mutation = new MutationBlockOverwriteMisc(branchLocation, branch, FacingDirection.DOWN, (byte)0);
		context.mutationSink.next(mutation);
	}

	private static void _growBranch(TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy newBlock)
	{
		// The branch has a few rules it applies:
		// -determine if it should branch again using the block-defined byte against BRANCH_GROWTH_COUNT
		// -check the orientation of the branch (points to trunk)
		// -choose random weight for growth into other 5 blocks:
		// --never grow in any direction if we reached BRANCH_GROWTH_COUNT
		// --never allow "down" (branches can't grow down)
		// --bias to favour "up"
		// --bias to favour current growth (opposite orientation)
		// -roll for all 5 non-replaceable blocks (we don't want to overwrite real blocks):
		// --if accepted, grow a branch pointing back at us
		// --if rejected, grow a leaf
		Environment env = Environment.getShared();
		Block log = env.special.blockLog;
		Block leaf = env.special.blockLeaf;
		
		Block branch = newBlock.getBlock();
		byte currentGrowth = newBlock.getBlockDefinedByte();
		FacingDirection trunkDirection = newBlock.getOrientation();
		
		// We want the tree to mostly grow upward but also fan out more as it grows.
		int currentFanning = BRANCH_CHANCE_PER_GROWTH * currentGrowth;
		int upRandom = BRANCH_CHANCE_UP;
		int eastRandom = BRANCH_CHANCE_SIDE + currentFanning;
		int westRandom = BRANCH_CHANCE_SIDE + currentFanning;
		int northRandom = BRANCH_CHANCE_SIDE + currentFanning;
		int southRandom = BRANCH_CHANCE_SIDE + currentFanning;
		switch (trunkDirection)
		{
		case UP:
			// The branches never grow down from the trunk.
			throw Assert.unreachable();
		case DOWN:
			upRandom += BRANCH_CHANCE_BONUS_DIRECTION;
			break;
		case EAST:
			westRandom += BRANCH_CHANCE_BONUS_DIRECTION;
			break;
		case WEST:
			eastRandom += BRANCH_CHANCE_BONUS_DIRECTION;
			break;
		case NORTH:
			southRandom += BRANCH_CHANCE_BONUS_DIRECTION;
			break;
		case SOUTH:
			northRandom += BRANCH_CHANCE_BONUS_DIRECTION;
			break;
		case FLIPPED_EAST:
		case FLIPPED_NORTH:
		case FLIPPED_SOUTH:
		case FLIPPED_WEST:
			// Branches cannot use the flipped orientation.
			throw Assert.unreachable();
		}
		
		CommonBlockMutationHelpers.setBlockWithFollowUps(env, context, location, newBlock, log);
		_tryGrowBranch(env, context, branch, leaf, location, currentGrowth, FacingDirection.UP, FacingDirection.DOWN, upRandom, BRANCH_CHOICE_DIVISOR);
		_tryGrowBranch(env, context, branch, leaf, location, currentGrowth, FacingDirection.EAST, FacingDirection.WEST, eastRandom, BRANCH_CHOICE_DIVISOR);
		_tryGrowBranch(env, context, branch, leaf, location, currentGrowth, FacingDirection.WEST, FacingDirection.EAST, westRandom, BRANCH_CHOICE_DIVISOR);
		_tryGrowBranch(env, context, branch, leaf, location, currentGrowth, FacingDirection.NORTH, FacingDirection.SOUTH, northRandom, BRANCH_CHOICE_DIVISOR);
		_tryGrowBranch(env, context, branch, leaf, location, currentGrowth, FacingDirection.SOUTH, FacingDirection.NORTH, southRandom, BRANCH_CHOICE_DIVISOR);
	}

	private static void _tryGrowBranch(Environment env
		, TickProcessingContext context
		, Block branch
		, Block leaf
		, AbsoluteLocation trunk
		, byte currentGrowth
		, FacingDirection growthDirection
		, FacingDirection trunkDirection
		, int randomCheck
		, int randomDivisor
	)
	{
		AbsoluteLocation branchLocation = growthDirection.getOutputBlockLocation(trunk);
		BlockProxy proxy = context.previousBlockLookUp.readBlock(branchLocation);
		if (null != proxy)
		{
			Block block = proxy.getBlock();
			if (env.blocks.canBeReplaced(block))
			{
				// We can place something here so figure out what (we will never grow in local projections).
				int random = (null != context.randomInt)
					? context.randomInt.applyAsInt(randomDivisor)
					: randomDivisor
				;
				
				MutationBlockOverwriteMisc mutation;
				if ((random < randomCheck) && (currentGrowth < BRANCH_GROWTH_COUNT))
				{
					// Branch.
					mutation = new MutationBlockOverwriteMisc(branchLocation, branch, trunkDirection, (byte)(currentGrowth + 1));
				}
				else
				{
					// Leaf.
					mutation = new MutationBlockOverwriteMisc(branchLocation, leaf, null, (byte)0);
				}
				context.mutationSink.next(mutation);
			}
		}
	}

	private static boolean _isSoilHydrated(Environment env, IBlockProxy proxy)
	{
		// We will only check the hydrated bit if this is tilled soil (this should always be the case but could be a race condition - we haven't yet run the update for it changing).
		return (null != proxy)
			&& (env.special.blockTilledSoil == proxy.getBlock())
			&& (BLOCK_HYDRATED_BYTE == proxy.getBlockDefinedByte())
		;
	}
}
