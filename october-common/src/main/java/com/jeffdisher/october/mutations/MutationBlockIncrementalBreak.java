package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.DamageAspect;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.MutableInventory;
import com.jeffdisher.october.types.TickProcessingContext;


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
	public boolean applyMutation(TickProcessingContext context, IMutableBlockProxy newBlock)
	{
		Environment env = Environment.getShared();
		boolean didApply = false;
		
		// We want to see if this is a kind of block which can be broken.
		Block block = newBlock.getBlock();
		if (DamageAspect.UNBREAKABLE != env.damage.getToughness(block))
		{
			// Apply the damage.
			short damage = (short)(newBlock.getDamage() + _damageToApply);
			
			// See if this is broken (note that damage could overflow).
			if ((damage >= env.damage.getToughness(block)) || (damage < 0))
			{
				// We have decided to break this block so determine what block it will become.
				Block emptyBlock = CommonBlockMutationHelpers.determineEmptyBlockType(context, _location);
				
				// Create the inventory for this type.
				int newInventoryCapacity = env.inventory.getInventoryCapacity(emptyBlock);
				MutableInventory newInventory = new MutableInventory(Inventory.start(newInventoryCapacity).finish());
				CommonBlockMutationHelpers.fillInventoryFromBlockWithoutLimit(newInventory, newBlock);
				
				// Add this block's drops to the inventory.
				for (Item dropped : env.blocks.droppedBlocksOnBreak(block))
				{
					newInventory.addItemsAllowingOverflow(dropped, 1);
				}
				
				// Break the block and replace it with the empty type, storing the inventory into it (may be over-filled).
				newBlock.setBlockAndClear(emptyBlock);
				newBlock.setInventory(newInventory.freeze());
				
				// See if the inventory should drop from this block.
				CommonBlockMutationHelpers.dropInventoryIfNeeded(context, _location, newBlock);
			}
			else
			{
				// The block still exists so just update the damage.
				newBlock.setDamage(damage);
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
