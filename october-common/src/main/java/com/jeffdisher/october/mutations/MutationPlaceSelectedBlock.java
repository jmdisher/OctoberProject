package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.registries.AspectRegistry;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.worldgen.CuboidGenerator;


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
		
		// There are a few things to check here:
		// -is the target location air?
		// -is there a selected item?
		// -is this target location close by?
		// -is the target location not colliding with the entity, itself?
		boolean isTargetAir = BlockAspect.canBeReplaced(context.previousBlockLookUp.apply(_targetBlock).getBlock());
		
		Item itemType = newEntity.newSelectedItem;
		// Note that we will get a null from the asBlock if this can't be placed.
		Block blockType = BlockAspect.getAsPlaceableBlock(itemType);
		boolean isItemSelected = (null != blockType);
		
		// We want to only consider placing the block if it is within 2 blocks of where the entity currently is.
		int absX = Math.abs(_targetBlock.x() - Math.round(newEntity.newLocation.x()));
		int absY = Math.abs(_targetBlock.y() - Math.round(newEntity.newLocation.y()));
		int absZ = Math.abs(_targetBlock.z() - Math.round(newEntity.newLocation.z()));
		boolean isLocationClose = ((absX <= 2) && (absY <= 2) && (absZ <= 2));
		
		// (to check for collision, we will ask about a world where only this block isn't air).
		boolean isLocationNotColliding = false;
		if (null != blockType)
		{
			CuboidData fakeCuboid = CuboidGenerator.createFilledCuboid(_targetBlock.getCuboidAddress(), ItemRegistry.AIR);
			fakeCuboid.setData15(AspectRegistry.BLOCK, _targetBlock.getBlockAddress(), blockType.number());
			isLocationNotColliding = SpatialHelpers.canExistInLocation((AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), fakeCuboid), newEntity.newLocation, newEntity.original.volume());
		}
		
		// Make sure that this block can be supported by the one under it.
		boolean blockIsSupported = false;
		if (null != blockType)
		{
			blockIsSupported = BlockAspect.canExistOnBlock(blockType, context.previousBlockLookUp.apply(_targetBlock.getRelative(0, 0, -1)).getBlock());
		}
		
		if (isTargetAir && isItemSelected && isLocationClose && isLocationNotColliding && blockIsSupported)
		{
			// We want to apply this so remove the item from the inventory and create the replace mutation.
			newEntity.newInventory.removeItems(itemType, 1);
			if (0 == newEntity.newInventory.getCount(itemType))
			{
				newEntity.newSelectedItem = null;
			}
			// This means that this worked so create the mutation to place the block.
			// WARNING:  If this mutation fails, the item will have been destroyed.
			MutationBlockOverwrite write = new MutationBlockOverwrite(_targetBlock, blockType);
			context.newMutationSink.accept(write);
			didApply = true;
			
			// Do other state reset.
			newEntity.newLocalCraftOperation = null;
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
