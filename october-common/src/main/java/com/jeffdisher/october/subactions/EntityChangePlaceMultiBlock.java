package com.jeffdisher.october.subactions;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.logic.ViscosityReader;
import com.jeffdisher.october.mutations.EntitySubActionType;
import com.jeffdisher.october.mutations.MultiBlockUtils;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.IEntitySubAction;
import com.jeffdisher.october.types.IMutableInventory;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.CuboidGenerator;


/**
 * Used to place a multi-block structure.  The target location is the "root" and the orientation is used to select how
 * the blocks will be oriented.
 * If the currently-selected block is NOT a multi-block, this does nothing.
 * Since it isn't possible to coordinate the modification of multiple blocks in the same tick, this also schedules
 * follow-up mutations to each of the block locations in order to verify consistency in the following tick.
 * If this consistency is violated, all of the blocks will revert to what they replaced.
 */
public class EntityChangePlaceMultiBlock implements IEntitySubAction<IMutablePlayerEntity>
{
	public static final EntitySubActionType TYPE = EntitySubActionType.MULTI_BLOCK_PLACE;

	public static EntityChangePlaceMultiBlock deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation target = CodecHelpers.readAbsoluteLocation(buffer);
		FacingDirection orientation = CodecHelpers.readOrientation(buffer);
		return new EntityChangePlaceMultiBlock(target, orientation);
	}


	private final AbsoluteLocation _targetBlock;
	private final FacingDirection _orientation;

	public EntityChangePlaceMultiBlock(AbsoluteLocation targetBlock, FacingDirection orientation)
	{
		_targetBlock = targetBlock;
		_orientation = orientation;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		Environment env = Environment.getShared();
		boolean didApply = false;
		
		// There are a few things to check here:
		// -is this target location close by?
		// -is there a selected item?
		// -is the selected item a multi-block?
		// -are the target locations replaceable?
		// -are the target locations not colliding with the entity, itself?
		
		// Find the distance from the eye to the target.
		float distance = SpatialHelpers.distanceFromMutableEyeToBlockSurface(newEntity, _targetBlock);
		boolean isLocationClose = (distance <= MiscConstants.REACH_BLOCK);
		
		int selectedKey = newEntity.getSelectedKey();
		IMutableInventory mutableInventory = newEntity.accessMutableInventory();
		Items stack = (Entity.NO_SELECTION != selectedKey) ? mutableInventory.getStackForKey(selectedKey) : null;
		Item itemType = (null != stack) ? stack.type() : null;
		// Note that we will get a null from the asBlock if this can't be placed.
		Block blockType = (null != itemType) ? env.blocks.getAsPlaceableBlock(itemType) : null;
		boolean isMultiBlock = (null != blockType) ? env.blocks.isMultiBlock(blockType) : false;
		
		if (isLocationClose && isMultiBlock)
		{
			List<AbsoluteLocation> extensions = env.multiBlocks.getExtensions(blockType, _targetBlock, _orientation);
			boolean isSafeLocation = _canPlace(env, newEntity, _targetBlock, extensions, blockType);
			
			if (isSafeLocation)
			{
				// This means that this worked so create the mutations to place all the blocks.
				// WARNING:  If this mutation fails in a later phase, the item will have been destroyed.
				int entityId = newEntity.getId();
				boolean didAttempt = MultiBlockUtils.send2PhaseMultiBlock(env, context, blockType, _targetBlock, _orientation, entityId);
				if (didAttempt)
				{
					// We can now remove from the inventory and place the blocks.
					mutableInventory.removeStackableItems(itemType, 1);
					if (0 == mutableInventory.getCount(itemType))
					{
						newEntity.setSelectedKey(Entity.NO_SELECTION);
					}
					
					
					// Do other state reset.
					newEntity.setCurrentCraftingOperation(null);
					
					didApply = true;
				}
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
		CodecHelpers.writeOrientation(buffer, _orientation);
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
		return "Place selected multi-block " + _targetBlock + " orientation " + _orientation;
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
		
		Function<AbsoluteLocation, BlockProxy> blockLookup = (AbsoluteLocation location) -> {
			CuboidData cuboid = map.get(location.getCuboidAddress());
			if (null == cuboid)
			{
				cuboid = emptyCuboid;
			}
			return new BlockProxy(location.getBlockAddress(), cuboid);
		};
		ViscosityReader reader = new ViscosityReader(env, blockLookup);
		return SpatialHelpers.canExistInLocation(reader, newEntity.getLocation(), newEntity.getType().volume());
	}
}
