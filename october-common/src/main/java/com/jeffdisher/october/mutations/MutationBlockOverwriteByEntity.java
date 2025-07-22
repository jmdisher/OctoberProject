package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.OrientationAspect;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.net.DeserializationContext;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.EventRecord;
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

	public static MutationBlockOverwriteByEntity deserialize(DeserializationContext context)
	{
		Environment env = context.env();
		ByteBuffer buffer = context.buffer();
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		Block blockType = env.blocks.getAsPlaceableBlock(CodecHelpers.readItem(buffer));
		OrientationAspect.Direction outputDirection = CodecHelpers.readOrientation(buffer);
		int entityId = buffer.getInt();
		return new MutationBlockOverwriteByEntity(location, blockType, outputDirection, entityId);
	}


	private final AbsoluteLocation _location;
	private final Block _blockType;
	private final OrientationAspect.Direction _outputDirection;
	private final int _entityId;

	public MutationBlockOverwriteByEntity(AbsoluteLocation location, Block blockType, OrientationAspect.Direction outputDirection, int entityId)
	{
		Environment env = Environment.getShared();
		// Using this with AIR doesn't make sense.
		Assert.assertTrue(!env.blocks.canBeReplaced(blockType));
		// Note that outputDirection can be null if that doesn't matter for this block type.
		Assert.assertTrue(OrientationAspect.doesSingleBlockRequireOrientation(blockType) == (null != outputDirection));
		// This mutation always requires an entity ID.
		Assert.assertTrue(0 != entityId);
		
		_location = location;
		_blockType = blockType;
		_outputDirection = outputDirection;
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
		boolean didApply = CommonBlockMutationHelpers.overwriteBlock(context, newBlock, _location, _outputDirection, _blockType);
		if (didApply)
		{
			Environment env = Environment.getShared();
			Block oldBlock = newBlock.getBlock();
			// Determine the appropriate event to trigger.
			EventRecord.Type type;
			if (env.liquids.isSource(_blockType))
			{
				// We must have placed water.
				type = EventRecord.Type.LIQUID_PLACED;
			}
			else if ((env.special.AIR == _blockType) && env.liquids.isSource(oldBlock))
			{
				// We removed the water.
				type = EventRecord.Type.LIQUID_REMOVED;
			}
			else
			{
				// Just use the generic block placed type since we did _something_.
				type = EventRecord.Type.BLOCK_PLACED;
			}
			// If there is an orientation for this block, set it here (this doesn't go through the common overwriteBlock() path since it only matters in this case and doesn't trigger anything).
			if (null != _outputDirection)
			{
				newBlock.setOrientation(_outputDirection);
			}
			context.eventSink.post(new EventRecord(type
					, EventRecord.Cause.NONE
					, _location
					, 0
					, _entityId
			));
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
		CodecHelpers.writeItem(buffer, _blockType.item());
		CodecHelpers.writeOrientation(buffer, _outputDirection);
		buffer.putInt(_entityId);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// This references an entity so we can't save it.
		return false;
	}
}
