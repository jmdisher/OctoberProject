package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.registries.AspectRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Breaks the block, emitting a "BreakBlockMutation", if the block is of the expected type when the change runs.
 */
public class EndBreakBlockChange implements IMutationEntity
{
	public static final MutationEntityType TYPE = MutationEntityType.BLOCK_BREAK_END;

	public static EndBreakBlockChange deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation target = CodecHelpers.readAbsoluteLocation(buffer);
		Item type = CodecHelpers.readItem(buffer);
		return new EndBreakBlockChange(target, type);
	}


	private final AbsoluteLocation _targetBlock;
	private final Item _expectedBlockType;

	public EndBreakBlockChange(AbsoluteLocation targetBlock, Item expectedBlockType)
	{
		_targetBlock = targetBlock;
		_expectedBlockType = expectedBlockType;
	}

	@Override
	public long getTimeCostMillis()
	{
		// In the future, this will depend no the type of block and the tool being used, etc.
		// For now, we use 200 ms since 100 will use an entire tick, but still finish inside that tick, so 200 will force it to finish in the second tick.
		return 200L;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, MutableEntity newEntity)
	{
		// There are a few things to check here:
		// -is the target block the expected type (we weren't preempted in a race)?
		// -is this target location close by?
		
		boolean doesTargetBlockMatch = (_expectedBlockType.number() == context.previousBlockLookUp.apply(_targetBlock).getData15(AspectRegistry.BLOCK));
		
		// We want to only consider breaking the block if it is within 2 blocks of where the entity currently is.
		int absX = Math.abs(_targetBlock.x() - Math.round(newEntity.newLocation.x()));
		int absY = Math.abs(_targetBlock.y() - Math.round(newEntity.newLocation.y()));
		int absZ = Math.abs(_targetBlock.z() - Math.round(newEntity.newLocation.z()));
		boolean isLocationClose = ((absX <= 2) && (absY <= 2) && (absZ <= 2));
		
		boolean didApply = false;
		if (doesTargetBlockMatch && isLocationClose)
		{
			// This means that this worked so create the mutation to break the block.
			BreakBlockMutation mutation = new BreakBlockMutation(_targetBlock, _expectedBlockType);
			context.newMutationSink.accept(mutation);
			didApply = true;
		}
		return didApply;
	}

	@Override
	public MutationEntityType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writeAbsoluteLocation(buffer, _targetBlock);
		CodecHelpers.writeItem(buffer, _expectedBlockType);
	}
}
