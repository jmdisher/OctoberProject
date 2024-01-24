package com.jeffdisher.october.mutations;

import com.jeffdisher.october.aspects.InventoryAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.registries.AspectRegistry;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.MutableInventory;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Asks the entity to move items from its inventory into a block.  Calls MutationBlockStoreItems to update the block
 * inventory.
 * Note that races here can result in items being destroyed (if the same block is over-filled in one tick).
 */
public class MutationEntityPushItems implements IMutationEntity
{
	private final AbsoluteLocation _blockLocation;
	private final Items _offered;

	public MutationEntityPushItems(AbsoluteLocation blockLocation, Items offered)
	{
		Assert.assertTrue(offered.count() > 0);
		
		_blockLocation = blockLocation;
		_offered = offered;
	}

	@Override
	public long getTimeCostMillis()
	{
		// We will currently assume that dropping items is instantaneous.
		return 0L;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, MutableEntity newEntity)
	{
		boolean didApply = false;
		
		// First off, we want to make sure that this is a block which can accept items (currently just air).
		BlockProxy block = context.previousBlockLookUp.apply(_blockLocation);
		if (ItemRegistry.AIR.number() == block.getData15(AspectRegistry.BLOCK))
		{
			// See if there is space in the inventory.
			Inventory inv = block.getDataSpecial(AspectRegistry.INVENTORY);
			MutableInventory checker = new MutableInventory((null != inv) ? inv : Inventory.start(InventoryAspect.CAPACITY_AIR).finish());
			int capacity = checker.maxVacancyForItem(_offered.type());
			int toDrop = Math.min(capacity, _offered.count());
			if (toDrop > 0)
			{
				// We will proceed to remove the items from our inventory and pass them to the block.
				newEntity.newInventory.removeItems(_offered.type(), toDrop);
				context.newMutationSink.accept(new MutationBlockStoreItems(_blockLocation, new Items(_offered.type(), toDrop)));
				
				// We want to deselect this if it was selected.
				if ((_offered.type() == newEntity.newSelectedItem) && (0 == newEntity.newInventory.getCount(_offered.type())))
				{
					newEntity.newSelectedItem = null;
				}
				
				didApply = true;
			}
		}
		return didApply;
	}
}
