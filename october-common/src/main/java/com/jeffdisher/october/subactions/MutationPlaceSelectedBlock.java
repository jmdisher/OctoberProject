package com.jeffdisher.october.subactions;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.logic.ViscosityReader;
import com.jeffdisher.october.mutations.EntitySubActionType;
import com.jeffdisher.october.mutations.MutationBlockOverwriteByEntity;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.IEntitySubAction;
import com.jeffdisher.october.types.IMutableInventory;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.CuboidGenerator;


/**
 * Places the block currently selected in the entity's inventory into the world if it is currently air.  This emits a
 * MutationBlockOverwrite if it is consistent.
 */
public class MutationPlaceSelectedBlock implements IEntitySubAction<IMutablePlayerEntity>
{
	public static final EntitySubActionType TYPE = EntitySubActionType.BLOCK_PLACE;

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
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		Environment env = Environment.getShared();
		boolean didApply = false;
		
		// There are a few things to check here:
		// -is the target location air?
		// -is there a selected item?
		// -is this target location close by?
		// -is the target location not colliding with the entity, itself?
		boolean isTargetAir = env.blocks.canBeReplaced(context.previousBlockLookUp.readBlock(_targetBlock).getBlock());
		
		int selectedKey = newEntity.getSelectedKey();
		IMutableInventory mutableInventory = newEntity.accessMutableInventory();
		Items stack = (Entity.NO_SELECTION != selectedKey) ? mutableInventory.getStackForKey(selectedKey) : null;
		Item itemType = (null != stack) ? stack.type() : null;
		// Note that we will get a null from the asBlock if this can't be placed.
		Block blockType = (null != itemType) ? env.blocks.getAsPlaceableBlock(itemType) : null;
		// We can't place this using this helper if it is a multi-block.
		if ((null != blockType) && env.blocks.isMultiBlock(blockType))
		{
			blockType = null;
		}
		boolean isItemSelected = (null != blockType);
		
		// Find the distance from the eye to the target.
		float distance = SpatialHelpers.distanceFromMutableEyeToBlockSurface(newEntity, _targetBlock);
		boolean isLocationClose = (distance <= MiscConstants.REACH_BLOCK);
		
		// (to check for collision, we will ask about a world where only this block isn't air).
		boolean isLocationNotColliding = false;
		if (null != blockType)
		{
			CuboidData fakeCuboid = CuboidGenerator.createFilledCuboid(_targetBlock.getCuboidAddress(), env.special.AIR);
			fakeCuboid.setData15(AspectRegistry.BLOCK, _targetBlock.getBlockAddress(), blockType.item().number());
			EntityLocation entityLocation = newEntity.getLocation();
			TickProcessingContext.IBlockFetcher blockLookup = new TickProcessingContext.IBlockFetcher() {
				@Override
				public BlockProxy readBlock(AbsoluteLocation location)
				{
					return BlockProxy.load(location.getBlockAddress(), fakeCuboid);
				}
				@Override
				public Map<AbsoluteLocation, BlockProxy> readBlockBatch(Collection<AbsoluteLocation> locations)
				{
					Map<AbsoluteLocation, BlockProxy> completed = new HashMap<>();
					for (AbsoluteLocation location : locations)
					{
						BlockProxy proxy = BlockProxy.load(location.getBlockAddress(), fakeCuboid);
						completed.put(location, proxy);
					}
					return completed;
				}
			};
			ViscosityReader reader = new ViscosityReader(env, blockLookup);
			isLocationNotColliding = SpatialHelpers.canExistInLocation(reader, entityLocation, newEntity.getType().volume());
		}
		
		// Make sure that this block can be supported by the one under it.
		boolean blockIsSupported = false;
		if (null != blockType)
		{
			blockIsSupported = env.blocks.canExistOnBlock(blockType, context.previousBlockLookUp.readBlock(_targetBlock.getRelative(0, 0, -1)).getBlock());
		}
		
		if (isTargetAir && isItemSelected && isLocationClose && isLocationNotColliding && blockIsSupported)
		{
			// Decide if this block type needs special orientation considerations.
			FacingDirection outputDirection = env.orientations.getDirectionIfApplicableToSingle(blockType, _targetBlock, _blockOutput);
			
			// Make sure that the output direction matches the needs of this block type.
			if (env.orientations.doesSingleBlockRequireOrientation(blockType) == (null != outputDirection))
			{
				// This means that this worked so create the mutation to place the block.
				// WARNING:  If this mutation fails, the item will have been destroyed.
				MutationBlockOverwriteByEntity write = new MutationBlockOverwriteByEntity(_targetBlock, blockType, outputDirection, newEntity.getId());
				context.mutationSink.next(write);
				didApply = true;
				
				// We were able to send the mutation, so remove this from the inventory.
				mutableInventory.removeStackableItems(itemType, 1);
				if (0 == mutableInventory.getCount(itemType))
				{
					newEntity.setSelectedKey(Entity.NO_SELECTION);
				}
				
				// Do other state reset.
				newEntity.setCurrentCraftingOperation(null);
			}
		}
		return didApply;
	}

	@Override
	public EntitySubActionType getType()
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

	@Override
	public String toString()
	{
		return "Place selected block " + _targetBlock + " facing " + _blockOutput;
	}
}
