package com.jeffdisher.october.block_movement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.transactions.TransactionBuilder;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Contains the core logic used by the block movement support to make the rules clearer and to make testing simpler.
 * Note that we use the "isBrokenByFlowingLiquid" helper to determine if a block can be moved or can be replaced by a
 * moving block since it is related to being broken by an external force.
 */
public class MovableBlockHelpers
{
	/**
	 * We will only allow 10 blocks to be pushed by a single push operation.  This isn't based on any specific limit or
	 * requirement, just seemed like a reasonable limit (since there needs to be one).
	 */
	public static final int BLOCK_PUSH_MAX = 10;

	/**
	 * Attempts to schedule a push transaction, starting at pushBlockLocation along the pushDirection, returning true
	 * if it was successful.
	 * 
	 * @param env The environment.
	 * @param context The current tick context.
	 * @param pushingBlockLocation The location of the block pusher.
	 * @param pushDirection The direction where the blocks should be pushed.
	 * @return True if the push transaction was scheduled or false if something prevented this.
	 */
	public static boolean didSchedulePushTransaction(Environment env
		, TickProcessingContext context
		, AbsoluteLocation pushingBlockLocation
		, FacingDirection pushDirection
	)
	{
		// First, load all the blocks which might be impacted by this.
		AbsoluteLocation offset = pushDirection.getOutputBlockLocation(new AbsoluteLocation(0, 0, 0));
		List<AbsoluteLocation> locations = new ArrayList<>();
		for (int i = 1; i <= (BLOCK_PUSH_MAX + 1); ++i)
		{
			AbsoluteLocation rel = new AbsoluteLocation(i * offset.x(), i * offset.y(), i * offset.z());
			AbsoluteLocation location = pushingBlockLocation.getRelative(rel.x(), rel.y(), rel.z());
			locations.add(location);
		}
		Map<AbsoluteLocation, BlockProxy> proxies = context.previousBlockLookUp.readBlockBatch(locations);
		
		// We now walk the list of blocks:  We expect to start with a not replaceable block and end at the first
		// replaceable block.  We fail if the first block is replaceable, if none of the blocks are, or we reach an
		// unloaded block before reaching the result.
		int blocksToPush = 0;
		boolean didFindFreeSpace = false;
		for (AbsoluteLocation loc : locations)
		{
			BlockProxy proxy = proxies.get(loc);
			if (null == proxy)
			{
				// In this case, just end.
				break;
			}
			else
			{
				if (_canMoveBlock(env, proxy))
				{
					blocksToPush += 1;
				}
				else if (_canFillBlock(env, proxy))
				{
					didFindFreeSpace = true;
					// This is the end of our search.
					break;
				}
				else
				{
					// This is blocking us.
					break;
				}
			}
		}
		
		boolean canPush = ((blocksToPush > 0) && didFindFreeSpace);
		if (canPush)
		{
			// This is valid so create the transaction.
			TransactionBuilder builder = new TransactionBuilder();
			
			// Create a block break mutation, first.
			MutationBlockDeleteBlock delete = new MutationBlockDeleteBlock(locations.get(0));
			builder.addMutation(delete);
			
			// Now, create the move mutations for the other blocks.
			for (int i = 0; i < blocksToPush; ++i)
			{
				int moveFrom = i;
				int moveTo = i + 1;
				BlockProxy proxy = proxies.get(locations.get(moveFrom));
				MovableBlockData blockData = MovableBlockData.fromProxy(proxy);
				boolean isFinal = (blocksToPush == moveTo);
				MutationBlockOverwriteWithMove overwrite = new MutationBlockOverwriteWithMove(locations.get(moveTo), blockData, isFinal);
				builder.addMutation(overwrite);
			}
			
			// If we failed to schedule this, we want to say that we were not able to push.
			canPush = builder.didStartTransaction(context);
		}
		return canPush;
	}

	/**
	 * Attempts to schedule a pull transaction, starting at pullingBlockLocation, pulling a block on the faceToPull
	 * direction, returning true if it was successful.
	 * 
	 * @param env The environment.
	 * @param context The current tick context.
	 * @param pullingBlockLocation The location of the block puller.
	 * @param faceToPull The direction of the face which should do the pulling.
	 * @return True if the pull transaction was scheduled or false if something prevented this.
	 */
	public static boolean didSchedulePullTransaction(Environment env
		, TickProcessingContext context
		, AbsoluteLocation pullingBlockLocation
		, FacingDirection faceToPull
	)
	{
		AbsoluteLocation offset = faceToPull.getOutputBlockLocation(new AbsoluteLocation(0, 0, 0));
		
		// The pull just requires that we see if the first block is replaceable and that the one after it is not.
		AbsoluteLocation blockToFill = pullingBlockLocation.getRelative(1 * offset.x(), 1 * offset.y(), 1 * offset.z());
		AbsoluteLocation blockToMove = pullingBlockLocation.getRelative(2 * offset.x(), 2 * offset.y(), 2 * offset.z());
		Map<AbsoluteLocation, BlockProxy> proxies = context.previousBlockLookUp.readBlockBatch(List.of(blockToFill, blockToMove));
		
		boolean canPull = (2 == proxies.size())
			&& _canFillBlock(env, proxies.get(blockToFill))
			&& _canMoveBlock(env, proxies.get(blockToMove))
		;
		if (canPull)
		{
			// This is valid so create the transaction.
			TransactionBuilder builder = new TransactionBuilder();
			
			// Add the pull mutation.
			BlockProxy proxy = proxies.get(blockToMove);
			MovableBlockData blockData = MovableBlockData.fromProxy(proxy);
			MutationBlockOverwriteWithMove overwrite = new MutationBlockOverwriteWithMove(blockToFill, blockData, true);
			builder.addMutation(overwrite);
			
			// Add the delete block mutation.
			MutationBlockDeleteBlock delete = new MutationBlockDeleteBlock(blockToMove);
			builder.addMutation(delete);
			
			// If we failed to schedule this, we want to say that we were not able to pull.
			canPull = builder.didStartTransaction(context);
		}
		return canPull;
	}


	private static boolean _canMoveBlock(Environment env, BlockProxy proxy)
	{
		// We can move a block if it is solid (note that we ignore the active state for these cases as they can't be aware) and if the movable data can be applied.
		Block block = proxy.getBlock();
		boolean isActive = false;
		return env.blocks.isSolid(block, isActive)
			&& MovableBlockData.canBeMoved(proxy)
		;
	}

	private static boolean _canFillBlock(Environment env, BlockProxy proxy)
	{
		// We can fill a block if it is replaceable or if it can be broken by flowing liquids.
		Block block = proxy.getBlock();
		return env.blocks.canBeReplaced(block)
			|| env.blocks.isBrokenByFlowingLiquid(block)
		;
	}
}
