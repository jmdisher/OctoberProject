package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.DamageAspect;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.MutableInventory;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Applies damage to the block.  If this results in the damage value exceeding the maximum for this block type, the
 * block will be replaced by air and dropped as an item in the inventory of the air block.
 */
public class MutationBlockIncrementalBreak implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.INCREMENTAL_BREAK_BLOCK;
	/**
	 * A constant we provide in case the block shouldn't be stored back into the inventory of a breking entity.
	 */
	public static final int NO_STORAGE_ENTITY = 0;

	public static MutationBlockIncrementalBreak deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		short damageToApply = buffer.getShort();
		int optionalEntityForStorage = buffer.getInt();
		return new MutationBlockIncrementalBreak(location, damageToApply, optionalEntityForStorage);
	}


	private final AbsoluteLocation _location;
	private final short _damageToApply;
	private final int _optionalEntityForStorage;

	public MutationBlockIncrementalBreak(AbsoluteLocation location, short damageToApply, int optionalEntityForStorage)
	{
		_location = location;
		_damageToApply = damageToApply;
		_optionalEntityForStorage = optionalEntityForStorage;
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
				// We want to see if there are any liquids around this block which we will need to handle.
				Block emptyBlock = env.special.AIR;
				Block eventualBlock = CommonBlockMutationHelpers.determineEmptyBlockType(context, _location, emptyBlock);
				if (emptyBlock != eventualBlock)
				{
					long millisDelay = env.liquids.minFlowDelayMillis(env, eventualBlock, block);
					context.mutationSink.future(new MutationBlockLiquidFlowInto(_location), millisDelay);
				}
				
				// Create the inventory for this type.
				MutableInventory newInventory = new MutableInventory(BlockProxy.getDefaultNormalOrEmptyBlockInventory(env, emptyBlock));
				CommonBlockMutationHelpers.fillInventoryFromBlockWithoutLimit(newInventory, newBlock);
				
				// We are going to break this block so see if we should send it back to an entity.
				// (note that we drop the existing inventory on the ground, either way).
				if (_optionalEntityForStorage > 0)
				{
					// Schedule a mutation to send it back to them (will drop at their feet on failure).
					// This is usually just 1 element so send 1 mutation per item.
					for (Item dropped : env.blocks.droppedBlocksOnBreak(block))
					{
						MutationEntityStoreToInventory store = new MutationEntityStoreToInventory(new Items(dropped, 1), null);
						context.newChangeSink.next(_optionalEntityForStorage, store);
					}
				}
				else
				{
					// Just drop this in the target location.
					for (Item dropped : env.blocks.droppedBlocksOnBreak(block))
					{
						newInventory.addItemsAllowingOverflow(dropped, 1);
					}
				}
				
				// Break the block and replace it with the empty type, storing the inventory into it (may be over-filled).
				newBlock.setBlockAndClear(emptyBlock);
				Inventory inventory = newInventory.freeze();
				newBlock.setInventory(inventory);
				
				// See if the inventory should drop from this block.
				if (inventory.currentEncumbrance > 0)
				{
					CommonBlockMutationHelpers.dropInventoryIfNeeded(context, _location, newBlock);
				}
				
				// This also triggers an event.
				context.eventSink.post(new EventRecord(EventRecord.Type.BLOCK_BROKEN
						, EventRecord.Cause.NONE
						, _location
						, 0
						, _optionalEntityForStorage
				));
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
		buffer.putInt(_optionalEntityForStorage);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// There is an entity reference.
		return false;
	}
}
