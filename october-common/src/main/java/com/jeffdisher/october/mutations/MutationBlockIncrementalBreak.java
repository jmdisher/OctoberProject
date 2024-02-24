package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.aspects.DamageAspect;
import com.jeffdisher.october.aspects.InventoryAspect;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.registries.AspectRegistry;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Applies damage to the block.  If this results in the damage value exceeding the maximum for this block type, the
 * block will be replaced by air and dropped as an item in the inventory of the air block.
 */
public class MutationBlockIncrementalBreak implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.INCREMENTAL_BREAK_BLOCK;

	public static MutationBlockIncrementalBreak deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		short damageToApply = buffer.getShort();
		return new MutationBlockIncrementalBreak(location, damageToApply);
	}


	private final AbsoluteLocation _location;
	private final short _damageToApply;

	public MutationBlockIncrementalBreak(AbsoluteLocation location, short damageToApply)
	{
		_location = location;
		_damageToApply = damageToApply;
	}

	@Override
	public AbsoluteLocation getAbsoluteLocation()
	{
		return _location;
	}

	@Override
	public boolean applyMutation(TickProcessingContext context, MutableBlockProxy newBlock)
	{
		boolean didApply = false;
		
		// We want to see if this is a kind of block which can be broken.
		Item block = ItemRegistry.BLOCKS_BY_TYPE[newBlock.getData15(AspectRegistry.BLOCK)];
		if (DamageAspect.UNBREAKABLE != block.toughness())
		{
			// Apply the damage.
			short damage = (short)(newBlock.getData15(AspectRegistry.DAMAGE) + _damageToApply);
			
			// See if this is broken (note that this could overflow.
			if ((damage >= block.toughness()) || (damage < 0))
			{
				// The block is broken so replace it with air and place the block in the inventory.
				newBlock.setData15(AspectRegistry.BLOCK, BlockAspect.AIR);
				newBlock.setData15(AspectRegistry.DAMAGE, DamageAspect.UNBREAKABLE);
				Inventory oldInventory = newBlock.getDataSpecial(AspectRegistry.INVENTORY);
				// This MUST be null or we were given a bogus expectation type (a container, not a block).
				Assert.assertTrue(null == oldInventory);
				Inventory newInventory = Inventory.start(InventoryAspect.CAPACITY_AIR).add(block, 1).finish();
				newBlock.setDataSpecial(AspectRegistry.INVENTORY, newInventory);
			}
			else
			{
				// The block still exists so just update the damage.
				newBlock.setData15(AspectRegistry.DAMAGE, damage);
			}
			didApply = true;
		}
		return didApply;
	}

	@Override
	public MutationBlockType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writeAbsoluteLocation(buffer, _location);
		buffer.putShort(_damageToApply);
	}
}
