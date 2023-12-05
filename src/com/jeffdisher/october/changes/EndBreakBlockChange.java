package com.jeffdisher.october.changes;

import com.jeffdisher.october.mutations.BreakBlockMutation;
import com.jeffdisher.october.registries.AspectRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * The first phase of breaking a block - determines if the block can be broken and how long it should take.
 * The second phase of the breaking operation will check that the block is still the same type and will then emit a
 * mutation to replace it with air containing the block as an item.
 */
public class EndBreakBlockChange implements IEntityChange
{
	private final int _id;
	private final AbsoluteLocation _targetBlock;
	private final short _expectedBlockType;

	public EndBreakBlockChange(int id, AbsoluteLocation targetBlock, short expectedBlockType)
	{
		_id = id;
		_targetBlock = targetBlock;
		_expectedBlockType = expectedBlockType;
	}

	@Override
	public int getTargetId()
	{
		return _id;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, MutableEntity newEntity)
	{
		// Make sure that the block is the same type.
		short blockType = context.previousBlockLookUp.apply(_targetBlock).getData15(AspectRegistry.BLOCK);
		boolean didApply = false;
		if (_expectedBlockType == blockType)
		{
			// This means that this worked so create the mutation to break the block.
			BreakBlockMutation mutation = new BreakBlockMutation(_targetBlock, _expectedBlockType);
			context.newMutationSink.accept(mutation);
			didApply = true;
		}
		return didApply;
	}

	@Override
	public boolean canReplacePrevious(IEntityChange previousChange)
	{
		// These can never replace anything.
		return false;
	}
}
