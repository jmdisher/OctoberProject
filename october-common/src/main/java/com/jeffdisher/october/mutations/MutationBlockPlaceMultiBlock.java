package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.OrientationAspect;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Over-writes a the given block if it can be replaced, also destroying any inventory it might have had.
 * Upon failure, no retry is attempted so the block being placed is lost.
 * Note that this overwrite mutation is intended for cases where an entity is placing a multi-block.
 */
public class MutationBlockPlaceMultiBlock implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.MULTI_PHASE1;

	public static MutationBlockPlaceMultiBlock deserializeFromBuffer(ByteBuffer buffer)
	{
		Environment env = Environment.getShared();
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		Block blockType = env.blocks.getAsPlaceableBlock(CodecHelpers.readItem(buffer));
		AbsoluteLocation rootLocation = CodecHelpers.readAbsoluteLocation(buffer);
		OrientationAspect.Direction direction = CodecHelpers.readOrientation(buffer);
		int entityId = buffer.getInt();
		return new MutationBlockPlaceMultiBlock(location, blockType, rootLocation, direction, entityId);
	}


	private final AbsoluteLocation _location;
	private final Block _blockType;
	private final AbsoluteLocation _rootLocation;
	private final OrientationAspect.Direction _direction;
	private final int _entityId;

	public MutationBlockPlaceMultiBlock(AbsoluteLocation location, Block blockType, AbsoluteLocation rootLocation, OrientationAspect.Direction direction, int entityId)
	{
		Environment env = Environment.getShared();
		// This MUST be a multi-block type.
		Assert.assertTrue(env.blocks.isMultiBlock(blockType));
		// This must be placed by a player entity.
		Assert.assertTrue(entityId > 0);
		
		_location = location;
		_blockType = blockType;
		_rootLocation = rootLocation;
		_direction = direction;
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
		Environment env = Environment.getShared();
		boolean isRoot = _rootLocation.equals(_location);
		Block oldBlock = newBlock.getBlock();
		boolean didApply = CommonBlockMutationHelpers.overwriteBlock(context, newBlock, _location, _direction, _blockType);
		if (didApply)
		{
			if (isRoot)
			{
				newBlock.setOrientation(_direction);
				
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
				context.eventSink.post(new EventRecord(type
						, EventRecord.Cause.NONE
						, _location
						, 0
						, _entityId
				));
			}
			else
			{
				newBlock.setMultiBlockRoot(_rootLocation);
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
		CodecHelpers.writeAbsoluteLocation(buffer, _location);
		CodecHelpers.writeItem(buffer, _blockType.item());
		CodecHelpers.writeAbsoluteLocation(buffer, _rootLocation);
		CodecHelpers.writeOrientation(buffer, _direction);
		buffer.putInt(_entityId);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// This references an entity so we can't save it.
		return false;
	}
}
