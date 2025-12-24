package com.jeffdisher.october.logic;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.IBlockProxy;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.mutations.MutationBlockPushToBlock;
import com.jeffdisher.october.mutations.MutationBlockStoreItems;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.FuelState;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableInventory;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.PassiveEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Hoppers have a bunch of specialized logic we need to hook into in several block mutations so the logic is kept here
 * for reuse and testing.
 */
public class HopperHelpers
{
	public static final String HOPPER  = "op.hopper";

	public static boolean isHopper(AbsoluteLocation hopperLocation, IMutableBlockProxy hopperBlock)
	{
		AbsoluteLocation sinkLocation = _sinkLocationIfHopper(hopperLocation, hopperBlock);
		return (null != sinkLocation);
	}

	public static void tryProcessHopper(TickProcessingContext context, AbsoluteLocation hopperLocation, IMutableBlockProxy hopperBlock)
	{
		// First, check if this block is a type of hopper.
		AbsoluteLocation sinkLocation = _sinkLocationIfHopper(hopperLocation, hopperBlock);
		
		if (null != sinkLocation)
		{
			// Note that it is possible that the source or sink will not be loaded.
			AbsoluteLocation sourceLocation = hopperLocation.getRelative(0, 0, 1);
			IBlockProxy sourceBlock = context.previousBlockLookUp.apply(sourceLocation);
			IBlockProxy sinkBlock = context.previousBlockLookUp.apply(sinkLocation);
			
			// We want to check that these blocks have the appropriate inventories.
			Inventory sourceInventory = null;
			Inventory sinkInventory = null;
			if (null != sourceBlock)
			{
				sourceInventory = sourceBlock.getInventory();
			}
			byte inventoryType = Inventory.INVENTORY_ASPECT_INVENTORY;
			if (null != sinkBlock)
			{
				// If this is on the same z-level, we will assume it is preferring the fuel inventory.
				if (hopperLocation.z() == sinkLocation.z())
				{
					FuelState fuel = sinkBlock.getFuel();
					if (null != fuel)
					{
						sinkInventory = fuel.fuelInventory();
						inventoryType = Inventory.INVENTORY_ASPECT_FUEL;
					}
				}
				if (null == sinkInventory)
				{
					sinkInventory = sinkBlock.getInventory();
					inventoryType = Inventory.INVENTORY_ASPECT_INVENTORY;
				}
			}
			_processHopper(context, hopperLocation, hopperBlock, sourceLocation, sourceInventory, sinkLocation, sinkInventory, inventoryType);
		}
	}

	public static PassiveEntity tryAbsorbingIntoHopper(TickProcessingContext context, PassiveEntity entity)
	{
		AbsoluteLocation currentLocation = entity.location().getBlockLocation();
		AbsoluteLocation below = currentLocation.getRelative(0, 0, -1);
		BlockProxy belowProxy = context.previousBlockLookUp.apply(below);
		PassiveEntity stillAlive = entity;
		if ((null != belowProxy) && belowProxy.getBlock().item().id().equals(HOPPER))
		{
			// We will allow this if it fits or if it is less than half full (since we will over-fill it).
			Environment env = Environment.getShared();
			Inventory inv = belowProxy.getInventory();
			ItemSlot slot = (ItemSlot)entity.extendedData();
			int size = env.encumbrance.getEncumbrance(slot.getType()) * slot.getCount();
			int available = inv.maxEncumbrance - inv.currentEncumbrance;
			if ((size <= available) || (((float)inv.currentEncumbrance / (float)inv.maxEncumbrance) < 0.5f))
			{
				// We will send this to the hopper and then despawn.
				MutationBlockStoreItems storeToSink = new MutationBlockStoreItems(below, slot.stack, slot.nonStackable, Inventory.INVENTORY_ASPECT_INVENTORY);
				context.mutationSink.next(storeToSink);
				stillAlive = null;
			}
		}
		return stillAlive;
	}


	private static AbsoluteLocation _sinkLocationIfHopper(AbsoluteLocation hopperLocation, IMutableBlockProxy hopperBlock)
	{
		Block block = hopperBlock.getBlock();
		String itemId = block.item().id();
		
		AbsoluteLocation sinkLocation;
		if (itemId.equals(HOPPER))
		{
			// This is a hopper so read the orientation byte to figure out the output.
			FacingDirection outputDirection = hopperBlock.getOrientation();
			sinkLocation = outputDirection.getOutputBlockLocation(hopperLocation);
		}
		else
		{
			// Not a hopper.
			sinkLocation = null;
		}
		return sinkLocation;
	}

	private static void _processHopper(TickProcessingContext context
			, AbsoluteLocation hopperLocation
			, IMutableBlockProxy hopperBlock
			, AbsoluteLocation sourceLocation
			, Inventory sourceInventory
			, AbsoluteLocation sinkLocation
			, Inventory sinkInventory
			, byte inventoryType
	)
	{
		// Note that sourceInventory or sinkInventory could be null if they aren't loaded or are missing the appropriate inventory.
		Environment env = Environment.getShared();
		Inventory hopperInventory = hopperBlock.getInventory();
		int hopperCapacity = hopperInventory.maxEncumbrance;
		MutableInventory mutable = new MutableInventory(hopperInventory);
		
		// First step is to see if we can push anything to the sink.
		if ((mutable.getCurrentEncumbrance() > 0) &&  (null != sinkInventory))
		{
			int spaceRemainingInSink = sinkInventory.maxEncumbrance - sinkInventory.currentEncumbrance;
			int largestHopperKey = _findLargestKey(env, hopperInventory, spaceRemainingInSink);
			if (0 != largestHopperKey)
			{
				// We can push this - we need just one in the case of a stackable.
				NonStackableItem nonStack = mutable.getNonStackableForKey(largestHopperKey);
				Items stackToMove = null;
				if (null != nonStack)
				{
					mutable.removeNonStackableItems(largestHopperKey);
				}
				else
				{
					Items stack = mutable.getStackForKey(largestHopperKey);
					stackToMove = new Items(stack.type(), 1);
					mutable.removeStackableItems(stack.type(), 1);
				}
				MutationBlockStoreItems storeToSink = new MutationBlockStoreItems(sinkLocation, stackToMove, nonStack, inventoryType);
				context.mutationSink.next(storeToSink);
				
				// (the immutable instance is now invalid).
				hopperInventory = null;
			}
		}
		
		// Next step is to see how much space we have in our inventory (minus whatever we pushed), and see if there is something in the source which can fit there.
		int spaceInHopper = hopperCapacity - mutable.getCurrentEncumbrance();
		if ((spaceInHopper > 0) && (null != sourceInventory))
		{
			int largestSourceKey = _findLargestKey(env, sourceInventory, spaceInHopper);
			if (0 != largestSourceKey)
			{
				// We always pull directly from normal inventory and only move 1 item at a time.
				MutationBlockPushToBlock fetchFromSource = new MutationBlockPushToBlock(sourceLocation, largestSourceKey, 1, Inventory.INVENTORY_ASPECT_INVENTORY, hopperLocation);
				context.mutationSink.next(fetchFromSource);
			}
		}
		hopperBlock.setInventory(mutable.freeze());
	}

	private static int _findLargestKey(Environment env, Inventory inventory, int maxSpace)
	{
		int largestKey = 0;
		int largestSize = 0;
		for (int key : inventory.sortedKeys())
		{
			NonStackableItem nonStack = inventory.getNonStackableForKey(key);
			if (null != nonStack)
			{
				Item type = nonStack.type();
				int size = env.encumbrance.getEncumbrance(type);
				if ((size > largestSize) && (size <= maxSpace))
				{
					largestKey = key;
					largestSize = size;
				}
			}
			else
			{
				Items stack = inventory.getStackForKey(key);
				Item type = stack.type();
				int size = env.encumbrance.getEncumbrance(type);
				if ((size > largestSize) && (size <= maxSpace))
				{
					largestKey = key;
					largestSize = size;
				}
			}
		}
		return largestKey;
	}
}
