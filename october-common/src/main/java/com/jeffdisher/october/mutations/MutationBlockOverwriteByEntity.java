package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Over-writes a the given block if it can be replaced, also destroying any inventory it might have had.
 * Upon failure, no retry is attempted so the block being placed is lost.
 * Note that this overwrite mutation is intended for cases where an entity is placing the block.
 */
public class MutationBlockOverwriteByEntity implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.OVERWRITE_BLOCK_BY_ENTITY;

	public static MutationBlockOverwriteByEntity deserializeFromBuffer(ByteBuffer buffer)
	{
		Environment env = Environment.getShared();
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		Block blockType = env.blocks.getAsPlaceableBlock(CodecHelpers.readItem(buffer));
		int entityId = buffer.getInt();
		return new MutationBlockOverwriteByEntity(location, blockType, entityId);
	}


	private final AbsoluteLocation _location;
	private final Block _blockType;
	private final int _entityId;

	public MutationBlockOverwriteByEntity(AbsoluteLocation location, Block blockType, int entityId)
	{
		Environment env = Environment.getShared();
		// Using this with AIR doesn't make sense.
		Assert.assertTrue(!env.blocks.canBeReplaced(blockType));
		Assert.assertTrue(0 != entityId);
		
		_location = location;
		_blockType = blockType;
		_entityId = entityId;
	}

	@Override
	public AbsoluteLocation getAbsoluteLocation()
	{
		return _location;
	}

	@Override
	public boolean applyMutation(TickProcessingContext context, IMutableBlockProxy newBlock)
	{
		boolean didApply = CommonBlockMutationHelpers.overwriteBlock(context, newBlock, _location, _blockType);
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
		CodecHelpers.writeItem(buffer, _blockType.item());
		buffer.putInt(_entityId);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// This references an entity so we can't save it.
		return false;
	}
}
