package com.jeffdisher.october.subactions;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.logic.ViscosityReader;
import com.jeffdisher.october.mutations.MultiBlockUtils;
import com.jeffdisher.october.mutations.MutationBlockOverwriteByEntity;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.IEntitySubAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableSlotManager;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;
import com.jeffdisher.october.utils.CuboidGenerator;


/**
 * The sub-action a client uses in order to place the block currently selected in their hotbar into the world with the
 * given FacingDirection.
 * This helper can be used for multi-blocks or single blocks and when in normal or creative mode.
 * The call will fail if the given FacingDirection cannot be applied to the currently-selected block (if the block does
 * NOT support orientation, this argument must be null).
 * A valid single-block placement will result in running MutationBlockOverwriteByEntity on the next tick.
 * A valid multi-block placement will result in the multi-block 2-phase commit placement of all blocks in the next 2
 * game ticks.  This means that the second phase may revert all the placements.
 */
public class EntitySubActionPlaceSelectedBlockGeneric implements IEntitySubAction<IMutablePlayerEntity>
{
	public static final EntitySubActionType TYPE = EntitySubActionType.PLACE_SELECTED_BLOCK_GENERIC;

	public static EntitySubActionPlaceSelectedBlockGeneric deserializeFromContext(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		AbsoluteLocation target = CodecHelpers.readAbsoluteLocation(buffer);
		FacingDirection orientation = CodecHelpers.readOrientation(buffer);
		return new EntitySubActionPlaceSelectedBlockGeneric(target, orientation);
	}

	/**
	 * A basic helper to use when selecting an orientation for placing a block.
	 * 
	 * @param env The environment.
	 * @param block The block to place (cannot be null).
	 * @param orientation The orientation to use (can be null for no orientation).
	 * @return True if this is a valid combination.
	 */
	public static boolean isValidOrientationForBlock(Environment env, Block block, FacingDirection orientation)
	{
		Assert.assertTrue(null != block);
		
		return _isValidOrientationForBlock(env, block, orientation);
	}


	private final AbsoluteLocation _targetBlock;
	private final FacingDirection _orientation;

	public EntitySubActionPlaceSelectedBlockGeneric(AbsoluteLocation targetBlock, FacingDirection orientation)
	{
		_targetBlock = targetBlock;
		_orientation = orientation;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		Environment env = Environment.getShared();
		boolean didApply = false;
		
		Block validPlacement = _blockAfterCommonChecks(env, newEntity);
		if (null == validPlacement)
		{
			// Common checks failed so fail out.
		}
		else if (env.blocks.isMultiBlock(validPlacement))
		{
			// We have some checks specific to multi-block:
			// -are target locations replaceable
			// -are the target locations not colliding with the entity, itself
			List<AbsoluteLocation> extensions = env.multiBlocks.getExtensions(validPlacement, _targetBlock, _orientation);
			boolean isSafeLocation = _canBlocksBeReplaced(env, context, _targetBlock, extensions)
				&& _canPlace(env, newEntity, _targetBlock, extensions, validPlacement)
			;
			if (!isSafeLocation)
			{
				validPlacement = null;
			}
		}
		else
		{
			// We have some checks specific to single-block:
			// -is the block correctly supported
			// -is target location replaceable
			// -is the target location not colliding with the entity, itself
			
			AbsoluteLocation supportLocation = (null != _orientation)
				? _orientation.getOutputBlockLocation(_targetBlock)
				: _targetBlock.getRelative(0, 0, -1)
			;
			BlockProxy supportBlock = context.previousBlockLookUp.readBlock(supportLocation);
			boolean blockIsSupported = (null != supportBlock)
				? env.blocks.canExistOnBlock(validPlacement, supportBlock.getBlock())
				: false
			;
			
			if (blockIsSupported)
			{
				List<AbsoluteLocation> extensions = List.of();
				boolean isSafeLocation = _canBlocksBeReplaced(env, context, _targetBlock, extensions)
					&& _canPlace(env, newEntity, _targetBlock, extensions, validPlacement)
				;
				if (!isSafeLocation)
				{
					validPlacement = null;
				}
			}
		}
		
		if (null != validPlacement)
		{
			// Determine the blocks to place and then do the common inventory and entity action management.
			
			if (env.blocks.isMultiBlock(validPlacement))
			{
				// This means that this worked so create the mutations to place all the blocks.
				// WARNING:  If this mutation fails in a later phase, the item will have been destroyed.
				int entityId = newEntity.getId();
				MultiBlockUtils.send2PhaseMultiBlock(env, context, validPlacement, _targetBlock, _orientation, entityId);
			}
			else
			{
				MutationBlockOverwriteByEntity write = new MutationBlockOverwriteByEntity(_targetBlock, validPlacement, _orientation, newEntity.getId());
				context.mutationSink.next(write);
			}
			
			// Remove the corresponding item from the inventory.
			MutableSlotManager slotManager = newEntity.getSlotManager();
			int selectedKey = slotManager.getSelectedKey();
			ItemSlot slot = slotManager.getSlot(selectedKey);
			if (null != slot.stack)
			{
				slotManager.removeStackable(slot.stack.type(), 1);
			}
			else
			{
				slotManager.removeNonStackable(selectedKey);
			}
			
			// Do other state reset.
			newEntity.setCurrentCraftingOperation(null);
			
			didApply = true;
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
		CodecHelpers.writeOrientation(buffer, _orientation);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// For now, we won't save these.  It makes more sense for the client to re-place the block when they load in.
		return false;
	}

	@Override
	public String toString()
	{
		return "Place selected block at " + _targetBlock + " with orientation " + _orientation;
	}


	private static boolean _canPlace(Environment env, IMutablePlayerEntity newEntity, AbsoluteLocation root, List<AbsoluteLocation> extensions, Block blockType)
	{
		// We will use the fake cuboid technique to verify that none of these blocks collide.
		Map<CuboidAddress, CuboidData> map = new HashMap<>();
		CuboidData emptyCuboid = CuboidGenerator.createFilledCuboid(root.getCuboidAddress(), env.special.AIR);
		CuboidData fakeCuboid = CuboidGenerator.createFilledCuboid(root.getCuboidAddress(), env.special.AIR);
		short blockNumber = blockType.item().number();
		fakeCuboid.setData15(AspectRegistry.BLOCK, root.getBlockAddress(), blockNumber);
		map.put(root.getCuboidAddress(), fakeCuboid);
		for (AbsoluteLocation location : extensions)
		{
			CuboidAddress address = location.getCuboidAddress();
			if (!map.containsKey(address))
			{
				CuboidData newCuboid = CuboidGenerator.createFilledCuboid(address, env.special.AIR);
				map.put(address, newCuboid);
			}
			CuboidData cuboid = map.get(address);
			cuboid.setData15(AspectRegistry.BLOCK, location.getBlockAddress(), blockNumber);
		}
		
		TickProcessingContext.IBlockFetcher blockLookup = new TickProcessingContext.IBlockFetcher() {
			@Override
			public BlockProxy readBlock(AbsoluteLocation location)
			{
				return _readBlock(location);
			}
			@Override
			public Map<AbsoluteLocation, BlockProxy> readBlockBatch(Collection<AbsoluteLocation> locations)
			{
				Map<AbsoluteLocation, BlockProxy> completed = new HashMap<>();
				for (AbsoluteLocation location : locations)
				{
					BlockProxy proxy = _readBlock(location);
					completed.put(location, proxy);
				}
				return completed;
			}
			private BlockProxy _readBlock(AbsoluteLocation location)
			{
				CuboidData cuboid = map.get(location.getCuboidAddress());
				if (null == cuboid)
				{
					cuboid = emptyCuboid;
				}
				return BlockProxy.load(location.getBlockAddress(), cuboid);
			}
		};
		ViscosityReader reader = new ViscosityReader(env, blockLookup);
		return SpatialHelpers.canExistInLocation(reader, newEntity.getLocation(), newEntity.getType().volume());
	}

	private Block _blockAfterCommonChecks(Environment env, IMutablePlayerEntity newEntity)
	{
		// We have some common checks to make:
		// -is in range
		// -is selected block placeable
		// -is the orientation valid for this kind of block
		
		EntityLocation sourceEyeLocation = SpatialHelpers.getEntityEye(newEntity);
		float distance = SpatialHelpers.distanceFromLocationToBlockSurface(sourceEyeLocation, _targetBlock);
		boolean isInRange = (distance <= MiscConstants.REACH_BLOCK);
		
		MutableSlotManager slotManager = newEntity.getSlotManager();
		int selectedKey = slotManager.getSelectedKey();
		Items stack = (Entity.NO_SELECTION != selectedKey)
			? slotManager.getSlot(selectedKey).stack
			: null
		;
		Item itemType = (null != stack) ? stack.type() : null;
		Block placeableBlock = (null != itemType) ? env.blocks.getAsPlaceableBlock(itemType) : null;
		
		Block validOrientationBlock = null;
		if (isInRange && (null != placeableBlock))
		{
			// Check if the orientation specified is valid for this block type.
			boolean isValid = _isValidOrientationForBlock(env, placeableBlock, _orientation);
			validOrientationBlock = isValid ? placeableBlock : null;
		}
		return validOrientationBlock;
	}

	private static boolean _canBlocksBeReplaced(Environment env, TickProcessingContext context, AbsoluteLocation root, List<AbsoluteLocation> extensions)
	{
		boolean canBeReplaced = env.blocks.canBeReplaced(context.previousBlockLookUp.readBlock(root).getBlock());
		for (AbsoluteLocation location : extensions)
		{
			BlockProxy one = context.previousBlockLookUp.readBlock(location);
			canBeReplaced &= (null != one) && env.blocks.canBeReplaced(one.getBlock());
		}
		return canBeReplaced;
	}

	private static boolean _isValidOrientationForBlock(Environment env, Block block, FacingDirection orientation)
	{
		boolean requiresHorizontalOrientations = env.blocks.isMultiBlock(block) || env.orientations.doesSingleBlockRequireOrientation(block);
		boolean isValid;
		if (null != orientation)
		{
			switch (orientation)
			{
			case NORTH:
			case WEST:
			case SOUTH:
			case EAST:
				isValid = requiresHorizontalOrientations;
				break;
			case DOWN:
				isValid = env.orientations.doesAllowDownwardOutput(block);
				break;
			case UP:
				isValid = env.orientations.doesAllowUpwardOutput(block);
				break;
			case FLIPPED_NORTH:
			case FLIPPED_WEST:
			case FLIPPED_SOUTH:
			case FLIPPED_EAST:
				isValid = env.orientations.doesAllowFlippedHorizontal(block);
				break;
			default:
				// Not used yet.
				throw Assert.unreachable();
			}
		}
		else if (!requiresHorizontalOrientations)
		{
			isValid = true;
		}
		else
		{
			// Mismatch.
			isValid = false;
		}
		return isValid;
	}
}
