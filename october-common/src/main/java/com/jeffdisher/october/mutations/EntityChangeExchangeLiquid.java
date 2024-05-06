package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.MutableInventory;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Attempts to exchange the liquid between the bucket and selected block, failing if it isn't a bucket or there is no
 * valid exchange (the block is solid, both are empty, or both are full).
 */
public class EntityChangeExchangeLiquid implements IMutationEntity<IMutablePlayerEntity>
{
	public static final MutationEntityType TYPE = MutationEntityType.EXCHANGE_LIQUID;

	public static EntityChangeExchangeLiquid deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation target = CodecHelpers.readAbsoluteLocation(buffer);
		return new EntityChangeExchangeLiquid(target);
	}


	private final AbsoluteLocation _target;

	public EntityChangeExchangeLiquid(AbsoluteLocation target)
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
		MutableInventory mutableInventory = newEntity.accessMutableInventory();
		NonStackableItem nonStack = (Entity.NO_SELECTION != selectedKey) ? mutableInventory.getNonStackableForKey(selectedKey) : null;
		Item type = (null != nonStack) ? nonStack.type() : null;
		Item empty = env.items.getItemById("op.bucket_empty");
		Item water = env.items.getItemById("op.bucket_water");
		boolean isEmptyBucket = (type == empty);
		boolean isWaterBucket = (type == water);
		BlockProxy proxy = context.previousBlockLookUp.apply(_target);
		Block block = (null != proxy) ? proxy.getBlock() : null;
		boolean isWaterSource = (env.special.WATER_SOURCE == block);
		boolean isEmptyBlock = !isWaterSource && env.blocks.canBeReplaced(block);
		
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
}
