package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.PassiveType;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * This mutation is specifically used to check if the target block should turn into a passive and fall.
 * The reason why this is done using its own mutation, as opposed to being done inline, is to mirror the behaviour of
 * changing back into a solid block when falling, since that takes 2 ticks:
 * 1) Notice the collision and despawn the passive
 * 2) Run the mutation to place the solid block
 * This means that changing from a solid block into a passive must also take an additional tick so this mutation allows
 * that matching delay.
 */
public class MutationBlockApplyGravity implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.APPLY_GRAVITY;

	public static MutationBlockApplyGravity deserialize(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		return new MutationBlockApplyGravity(location);
	}


	private final AbsoluteLocation _blockLocation;

	public MutationBlockApplyGravity(AbsoluteLocation blockLocation)
	{
		_blockLocation = blockLocation;
	}

	@Override
	public AbsoluteLocation getAbsoluteLocation()
	{
		return _blockLocation;
	}

	@Override
	public boolean applyMutation(TickProcessingContext context, IMutableBlockProxy newBlock)
	{
		Environment env = Environment.getShared();
		boolean didApply = false;
		
		Block thisBlock = newBlock.getBlock();
		if (env.blocks.hasGravity(thisBlock))
		{
			// See if this needs to break and turn into a falling block.
			AbsoluteLocation belowBlockLocation = _blockLocation.getRelative(0, 0, -1);
			BlockProxy belowBlock = context.previousBlockLookUp.apply(belowBlockLocation);
			if (null != belowBlock)
			{
				boolean belowActive = FlagsAspect.isSet(belowBlock.getFlags(), FlagsAspect.FLAG_ACTIVE);
				if (!env.blocks.isSupportedAgainstGravity(thisBlock, belowBlock.getBlock(), belowActive))
				{
					// We need to break this block and drop it as a passive falling block.
					// Note that we will assume that gravity blocks can't have inventories.
					Assert.assertTrue(null == newBlock.getInventory());
					Block emptyBlock = env.special.AIR;
					Block eventualBlock = CommonBlockMutationHelpers.determineEmptyBlockType(context, _blockLocation, emptyBlock);
					CommonBlockMutationHelpers.setBlockCheckingFire(env, context, _blockLocation, newBlock, eventualBlock);
					
					// Create the falling block.
					context.passiveSpawner.spawnPassive(PassiveType.FALLING_BLOCK, _blockLocation.toEntityLocation(), new EntityLocation(0.0f, 0.0f, 0.0f), thisBlock);
					didApply = true;
				}
			}
		}
		return didApply;
	}

	@Override
	public MutationBlockType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writeAbsoluteLocation(buffer, _blockLocation);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Common case.
		return true;
	}
}
