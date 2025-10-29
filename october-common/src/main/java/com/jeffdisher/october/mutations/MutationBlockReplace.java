package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Replaces the block with the new block type, but only if the existing block type is what is expected.  On failure,
 * changes nothing and does nothing.
 * NOTE:  This can ONLY be used if both types are the replaceable (this could change in the future but narrows testing
 * surface in the near-term).
 */
public class MutationBlockReplace implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.REPLACE_BLOCK;

	public static MutationBlockReplace deserialize(DeserializationContext context)
	{
		Environment env = context.env();
		ByteBuffer buffer = context.buffer();
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		Block originalType = env.blocks.getAsPlaceableBlock(CodecHelpers.readItem(buffer));
		Block newType = env.blocks.getAsPlaceableBlock(CodecHelpers.readItem(buffer));
		return new MutationBlockReplace(location, originalType, newType);
	}


	private final AbsoluteLocation _location;
	private final Block _originalType;
	private final Block _newType;

	public MutationBlockReplace(AbsoluteLocation location, Block originalType, Block newType)
	{
		_location = location;
		_originalType = originalType;
		_newType = newType;
	}

	@Override
	public AbsoluteLocation getAbsoluteLocation()
	{
		return _location;
	}

	@Override
	public boolean applyMutation(TickProcessingContext context, IMutableBlockProxy newBlock)
	{
		boolean didApply = false;
		
		// Check to see if this is the expected type.
		Block oldType = newBlock.getBlock();
		if (oldType == _originalType)
		{
			Environment env = Environment.getShared();
			
			CommonBlockMutationHelpers.setBlockCheckingFire(env, context, _location, newBlock, _newType);
			
			// See if we might need to reflow water (consider if this was a bucket picking up a source).
			CommonBlockMutationHelpers.scheduleLiquidFlowIfRequired(env, context, _location, oldType, _newType);
			
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
		CodecHelpers.writeAbsoluteLocation(buffer, _location);
		CodecHelpers.writeItem(buffer, _originalType.item());
		CodecHelpers.writeItem(buffer, _newType.item());
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Common case.
		return true;
	}
}
