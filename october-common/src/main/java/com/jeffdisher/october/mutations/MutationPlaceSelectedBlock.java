package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityConstants;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableInventory;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.worldgen.CuboidGenerator;


/**
 * Places the block currently selected in the entity's inventory into the world if it is currently air.  This emits a
 * MutationBlockOverwrite if it is consistent.
 */
public class MutationPlaceSelectedBlock implements IMutationEntity<IMutablePlayerEntity>
{
	public static final MutationEntityType TYPE = MutationEntityType.BLOCK_PLACE;
	// We need to handle hopper placement as a special case since it cares about output direction.
	public static final String HOPPER_DOWN  = "op.hopper_down";
	public static final String HOPPER_NORTH = "op.hopper_north";
	public static final String HOPPER_SOUTH = "op.hopper_south";
	public static final String HOPPER_EAST  = "op.hopper_east";
	public static final String HOPPER_WEST  = "op.hopper_west";

	public static MutationPlaceSelectedBlock deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation target = CodecHelpers.readAbsoluteLocation(buffer);
		AbsoluteLocation blockOutput = CodecHelpers.readAbsoluteLocation(buffer);
		return new MutationPlaceSelectedBlock(target, blockOutput);
	}


	private final AbsoluteLocation _targetBlock;
	private final AbsoluteLocation _blockOutput;

	public MutationPlaceSelectedBlock(AbsoluteLocation targetBlock, AbsoluteLocation blockOutput)
	{
		_targetBlock = targetBlock;
		_blockOutput = blockOutput;
	}

	@Override
	public long getTimeCostMillis()
	{
		// We will say that placing blocks is instantaneous.
		return 0L;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		Environment env = Environment.getShared();
		boolean didApply = false;
		
		// There are a few things to check here:
		// -is the target location air?
		// -is there a selected item?
		// -is this target location close by?
		// -is the target location not colliding with the entity, itself?
		boolean isTargetAir = env.blocks.canBeReplaced(context.previousBlockLookUp.apply(_targetBlock).getBlock());
		
		int selectedKey = newEntity.getSelectedKey();
		MutableInventory mutableInventory = newEntity.accessMutableInventory();
		Items stack = (Entity.NO_SELECTION != selectedKey) ? mutableInventory.getStackForKey(selectedKey) : null;
		Item itemType = (null != stack) ? stack.type() : null;
		// Note that we will get a null from the asBlock if this can't be placed.
		Block blockType = (null != itemType) ? env.blocks.getAsPlaceableBlock(itemType) : null;
		boolean isItemSelected = (null != blockType);
		
		// We want to only consider placing the block if it is within 2 blocks of where the entity currently is.
		EntityLocation entityLocation = newEntity.getLocation();
		int targetX = _targetBlock.x();
		int targetY = _targetBlock.y();
		int absX = Math.abs(targetX - Math.round(entityLocation.x()));
		int absY = Math.abs(targetY - Math.round(entityLocation.y()));
		int absZ = Math.abs(_targetBlock.z() - Math.round(entityLocation.z()));
		boolean isLocationClose = ((absX <= 2) && (absY <= 2) && (absZ <= 2));
		
		// (to check for collision, we will ask about a world where only this block isn't air).
		boolean isLocationNotColliding = false;
		if (null != blockType)
		{
			CuboidData fakeCuboid = CuboidGenerator.createFilledCuboid(_targetBlock.getCuboidAddress(), env.special.AIR);
			fakeCuboid.setData15(AspectRegistry.BLOCK, _targetBlock.getBlockAddress(), blockType.item().number());
			isLocationNotColliding = SpatialHelpers.canExistInLocation((AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), fakeCuboid), entityLocation, EntityConstants.getVolume(newEntity.getType()));
		}
		
		// Make sure that this block can be supported by the one under it.
		boolean blockIsSupported = false;
		if (null != blockType)
		{
			blockIsSupported = env.blocks.canExistOnBlock(blockType, context.previousBlockLookUp.apply(_targetBlock.getRelative(0, 0, -1)).getBlock());
		}
		
		if (isTargetAir && isItemSelected && isLocationClose && isLocationNotColliding && blockIsSupported)
		{
			// We want to apply this so remove the item from the inventory and create the replace mutation.
			mutableInventory.removeStackableItems(itemType, 1);
			if (0 == mutableInventory.getCount(itemType))
			{
				newEntity.setSelectedKey(Entity.NO_SELECTION);
			}
			
			// Decide if this block type needs special orientation considerations.
			// TODO:  Find some way to generalize this (maybe as part of getAsPlaceableBlock()?).
			Block blockToPlace;
			if (blockType.item().id().equals(HOPPER_DOWN))
			{
				// Check the direction of the output, relative to target block.
				int outX = _blockOutput.x();
				int outY = _blockOutput.y();
				if (outY > targetY)
				{
					blockToPlace = env.blocks.fromItem(env.items.getItemById(HOPPER_NORTH));
				}
				else if (outY < targetY)
				{
					blockToPlace = env.blocks.fromItem(env.items.getItemById(HOPPER_SOUTH));
				}
				else if (outX > targetX)
				{
					blockToPlace = env.blocks.fromItem(env.items.getItemById(HOPPER_EAST));
				}
				else if (outX < targetX)
				{
					blockToPlace = env.blocks.fromItem(env.items.getItemById(HOPPER_WEST));
				}
				else
				{
					blockToPlace = blockType;
				}
			}
			else
			{
				// Common case.
				blockToPlace = blockType;
			}
			
			// This means that this worked so create the mutation to place the block.
			// WARNING:  If this mutation fails, the item will have been destroyed.
			MutationBlockOverwrite write = new MutationBlockOverwrite(_targetBlock, blockToPlace);
			context.mutationSink.next(write);
			didApply = true;
			
			// Do other state reset.
			newEntity.setCurrentCraftingOperation(null);
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
		CodecHelpers.writeAbsoluteLocation(buffer, _blockOutput);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Block reference.
		return false;
	}
}
