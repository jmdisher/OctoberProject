package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.registries.AspectRegistry;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Places the block currently selected in the entity's inventory into the world if it is currently air.  This emits a
 * MutationBlockOverwrite if it is consistent.
 */
public class MutationPlaceSelectedBlock implements IMutationEntity
{
	public static final MutationEntityType TYPE = MutationEntityType.BLOCK_PLACE;

	public static MutationPlaceSelectedBlock deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation target = CodecHelpers.readAbsoluteLocation(buffer);
		return new MutationPlaceSelectedBlock(target);
	}


	private final AbsoluteLocation _targetBlock;

	public MutationPlaceSelectedBlock(AbsoluteLocation targetBlock)
	{
		_targetBlock = targetBlock;
	}

	@Override
	public long getTimeCostMillis()
	{
		// We will say that placing blocks is instantaneous.
		return 0L;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, MutableEntity newEntity)
	{
		boolean didApply = false;
		
		// Make sure that the block is air and that we have something selected.
		Item blockType = newEntity.newSelectedItem;
		if ((null != blockType) && (ItemRegistry.AIR.number() == context.previousBlockLookUp.apply(_targetBlock).getData15(AspectRegistry.BLOCK)))
		{
			// We want to apply this so remove the item from the inventory and create the replace mutation.
			newEntity.newInventory.removeItems(blockType, 1);
			if (0 == newEntity.newInventory.getCount(blockType))
			{
				newEntity.newSelectedItem = null;
			}
			// This means that this worked so create the mutation to place the block.
			// WARNING:  If this mutation fails, the item will have been destroyed.
			MutationBlockOverwrite write = new MutationBlockOverwrite(_targetBlock, blockType);
			context.newMutationSink.accept(write);
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
	}
}
