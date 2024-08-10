package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.IMutableInventory;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Handles the "right-click on block" case for specific items.
 * An example of this is a bucket being used on a water block.
 * Note that this is NOT the same as "hitting" a block.
 */
public class EntityChangeUseSelectedItemOnBlock implements IMutationEntity<IMutablePlayerEntity>
{
	public static final MutationEntityType TYPE = MutationEntityType.USE_SELECTED_ITEM_ON_BLOCK;
	public static final String BUCKET_EMPTY = "op.bucket_empty";
	public static final String BUCKET_WATER = "op.bucket_water";
	public static final String FERTILIZER = "op.fertilizer";

	public static EntityChangeUseSelectedItemOnBlock deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation target = CodecHelpers.readAbsoluteLocation(buffer);
		return new EntityChangeUseSelectedItemOnBlock(target);
	}

	/**
	 * A helper to determine if the given item can be used on a specific block with this entity mutation.
	 * 
	 * @param item The item.
	 * @param block The target block.
	 * @return True if this mutation can be used to apply the item to the block.
	 */
	public static boolean canUseOnBlock(Item item, Block block)
	{
		Environment env = Environment.getShared();
		Item empty = env.items.getItemById(BUCKET_EMPTY);
		Item water = env.items.getItemById(BUCKET_WATER);
		Item fertilizer = env.items.getItemById(FERTILIZER);
		boolean isEmptyBucket = (item == empty);
		boolean isWaterBucket = (item == water);
		boolean isFertilizer = (item == fertilizer);
		boolean isWaterSource = (env.special.WATER_SOURCE == block);
		boolean isEmptyBlock = !isWaterSource && env.blocks.canBeReplaced(block);
		boolean isGrowable = (env.plants.growthDivisor(block) > 0);
		return (isWaterBucket && isEmptyBlock)
				|| (isEmptyBucket && isWaterSource)
				|| (isFertilizer && isGrowable)
		;
	}


	private final AbsoluteLocation _target;

	public EntityChangeUseSelectedItemOnBlock(AbsoluteLocation target)
	{
		_target = target;
	}

	@Override
	public long getTimeCostMillis()
	{
		return 0L;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		Environment env = Environment.getShared();
		int selectedKey = newEntity.getSelectedKey();
		IMutableInventory mutableInventory = newEntity.accessMutableInventory();
		NonStackableItem nonStack = (Entity.NO_SELECTION != selectedKey) ? mutableInventory.getNonStackableForKey(selectedKey) : null;
		Items stack = (Entity.NO_SELECTION != selectedKey) ? mutableInventory.getStackForKey(selectedKey) : null;
		Item type = (null != nonStack)
				? nonStack.type()
				: (null != stack)
					? stack.type()
					: null
		;
		Item empty = env.items.getItemById(BUCKET_EMPTY);
		Item water = env.items.getItemById(BUCKET_WATER);
		Item fertilizer = env.items.getItemById(FERTILIZER);
		boolean isEmptyBucket = (type == empty);
		boolean isWaterBucket = (type == water);
		boolean isFertilizer = (type == fertilizer);
		BlockProxy proxy = context.previousBlockLookUp.apply(_target);
		Block block = (null != proxy) ? proxy.getBlock() : null;
		boolean isWaterSource = (env.special.WATER_SOURCE == block);
		boolean isEmptyBlock = !isWaterSource && env.blocks.canBeReplaced(block);
		boolean isGrowable = (env.plants.growthDivisor(block) > 0);
		
		// We can either place the bucket or pick up a source so see which it is.
		boolean didApply = false;
		if (isWaterBucket && isEmptyBlock)
		{
			// We can place down the bucket.
			mutableInventory.replaceNonStackable(selectedKey, new NonStackableItem(empty, 0));
			context.mutationSink.next(new MutationBlockReplace(_target, block, env.special.WATER_SOURCE));
			didApply = true;
		}
		else if (isEmptyBucket && isWaterSource)
		{
			// We can pick up the source.
			mutableInventory.replaceNonStackable(selectedKey, new NonStackableItem(water, 0));
			context.mutationSink.next(new MutationBlockReplace(_target, block, env.special.AIR));
			didApply = true;
		}
		else if (isFertilizer && isGrowable)
		{
			// We can apply the fertilizer by forcing a growth tick.
			mutableInventory.removeStackableItems(type, 1);
			if (0 == mutableInventory.getCount(type))
			{
				newEntity.setSelectedKey(Entity.NO_SELECTION);
			}
			context.mutationSink.next(new MutationBlockGrow(_target, true));
			didApply = true;
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
		CodecHelpers.writeAbsoluteLocation(buffer, _target);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// The target may have changed.
		return false;
	}

	@Override
	public String toString()
	{
		return "Use selected item on block " + _target;
	}
}
