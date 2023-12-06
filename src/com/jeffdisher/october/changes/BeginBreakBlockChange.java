package com.jeffdisher.october.changes;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.registries.AspectRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * The first phase of breaking a block - determines if the block can be broken and how long it should take.
 * The second phase of the breaking operation will check that the block is still the same type and will then emit a
 * mutation to replace it with air containing the block as an item.
 */
public class BeginBreakBlockChange implements IEntityChange
{
	private final int _id;
	private final AbsoluteLocation _targetBlock;

	public BeginBreakBlockChange(int id, AbsoluteLocation targetBlock)
	{
		_id = id;
		_targetBlock = targetBlock;
	}

	@Override
	public int getTargetId()
	{
		return _id;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, MutableEntity newEntity)
	{
		// Make sure that the block isn't air.
		short blockType = context.previousBlockLookUp.apply(_targetBlock).getData15(AspectRegistry.BLOCK);
		boolean didApply = false;
		if (BlockAspect.AIR != blockType)
		{
			// Now, schedule the second phase.
			EndBreakBlockChange phase2 = new EndBreakBlockChange(_id, _targetBlock, blockType);
			// For now, we will say that this takes 100 ms.
			context.twoPhaseChangeSink.accept(phase2, 100L);
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