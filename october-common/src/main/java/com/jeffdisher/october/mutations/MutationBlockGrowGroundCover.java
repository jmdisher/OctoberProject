package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.logic.GroundCoverHelpers;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * This mutation is scheduled against a block which can accept ground cover when the possibility to spread is realized.
 * Internally, it checks that the spread is valid and applies it.
 */
public class MutationBlockGrowGroundCover implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.SPREAD_GROUND_COVER;
	/**
	 * We will delay spread by 10 seconds.
	 * TODO:  We probably want to make this have a chance to spread on each attempt, in order to add some randomness.
	 */
	public static final long SPREAD_DELAY_MILLIS = 10_000L;

	public static MutationBlockGrowGroundCover deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		Block groundCoverType = CodecHelpers.readBlock(buffer);
		return new MutationBlockGrowGroundCover(location, groundCoverType);
	}


	private final AbsoluteLocation _blockLocation;
	private final Block _groundCoverType;

	public MutationBlockGrowGroundCover(AbsoluteLocation blockLocation, Block groundCoverType)
	{
		_blockLocation = blockLocation;
		_groundCoverType = groundCoverType;
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
		
		// Check that all the rules to change this are passed.
		if (GroundCoverHelpers.canChangeToGroundCover(env, context.previousBlockLookUp, _blockLocation, newBlock.getBlock(), _groundCoverType))
		{
			// Change our block type.
			CommonBlockMutationHelpers.setBlockCheckingFire(env, context, _blockLocation, newBlock, _groundCoverType);
			didApply = true;
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
		CodecHelpers.writeBlock(buffer, _groundCoverType);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Common case.
		return true;
	}
}
