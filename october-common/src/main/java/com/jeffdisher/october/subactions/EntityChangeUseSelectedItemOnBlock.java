package com.jeffdisher.october.subactions;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.logic.PropertyHelpers;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.mutations.EntitySubActionType;
import com.jeffdisher.october.mutations.MutationBlockForceGrow;
import com.jeffdisher.october.mutations.MutationBlockReplace;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.properties.PropertyRegistry;
import com.jeffdisher.october.properties.PropertyType;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.IEntitySubAction;
import com.jeffdisher.october.types.IMutableInventory;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Handles the "right-click on block" case for specific items.
 * An example of this is a bucket being used on a water block.
 * Note that this is NOT the same as "hitting" a block.
 */
public class EntityChangeUseSelectedItemOnBlock implements IEntitySubAction<IMutablePlayerEntity>
{
	public static final EntitySubActionType TYPE = EntitySubActionType.USE_SELECTED_ITEM_ON_BLOCK;
	public static final long COOLDOWN_MILLIS = 250L;

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
		
		boolean canUse;
		if ((env.special.itemFertilizer == item) && (env.plants.growthDivisor(block) > 0))
		{
			canUse = true;
		}
		else if (env.liquids.isBucketForUseOneBlock(env, item, block))
		{
			canUse = true;
		}
		else if ((env.special.itemStoneHoe == item) && ((env.special.blockDirt == block) || (env.special.blockGrass == block)))
		{
			canUse = true;
		}
		else if (env.special.itemPortalOrb == item)
		{
			canUse = true;
		}
		else
		{
			canUse = false;
		}
		return canUse;
	}


	private final AbsoluteLocation _target;

	public EntityChangeUseSelectedItemOnBlock(AbsoluteLocation target)
	{
		_target = target;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		// First, we want to make sure that we are not still busy doing something else.
		boolean isReady = ((newEntity.getLastSpecialActionMillis() + COOLDOWN_MILLIS) <= context.currentTickTimeMillis);
		
		// We also want to make sure that this is in range.
		float distance = SpatialHelpers.distanceFromMutableEyeToBlockSurface(newEntity, _target);
		boolean isInRange = (distance <= MiscConstants.REACH_BLOCK);
		
		boolean didApply = false;
		if (isReady && isInRange)
		{
			didApply = _apply(context, newEntity);
			
			if (didApply)
			{
				// Rate-limit us by updating the special action time.
				newEntity.setLastSpecialActionMillis(context.currentTickTimeMillis);
				newEntity.setCurrentChargeMillis(0);
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


	private boolean _apply(TickProcessingContext context, IMutablePlayerEntity newEntity)
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
		boolean isFertilizer = (env.special.itemFertilizer == type);
		BlockProxy proxy = context.previousBlockLookUp.apply(_target);
		Block block = (null != proxy) ? proxy.getBlock() : null;
		boolean isGrowable = (env.plants.growthDivisor(block) > 0);
		
		boolean didApply = false;
		if (env.liquids.isBucketForUseOneBlock(env, type, block))
		{
			// This is a bucket related action so just find out the output types.
			Item outputBucket = env.liquids.bucketAfterUse(env, type, block);
			Block outputBlock = env.liquids.blockAfterBucketUse(env, type, block);
			Assert.assertTrue(null != outputBucket);
			Assert.assertTrue(null != outputBlock);
			// We can place down the bucket.
			mutableInventory.replaceNonStackable(selectedKey, PropertyHelpers.newItemWithDefaults(env, outputBucket));
			context.mutationSink.next(new MutationBlockReplace(_target, block, outputBlock));
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
			context.mutationSink.next(new MutationBlockForceGrow(_target));
			didApply = true;
		}
		else if ((env.special.itemStoneHoe == type) && ((env.special.blockDirt == block) || (env.special.blockGrass == block)))
		{
			// We will decrement the durability of the hoe and replace the target block with tilled soil.
			// (we ignore tool durability on the client)
			if (null != context.randomInt)
			{
				int randomNumberTo255 = context.randomInt.applyAsInt(256);
				NonStackableItem newItem = PropertyHelpers.reduceDurabilityOrBreak(nonStack, 1, randomNumberTo255);
				if (null != newItem)
				{
					// Normal wear.
					mutableInventory.replaceNonStackable(selectedKey, newItem);
				}
				else
				{
					// Broken.
					mutableInventory.removeNonStackableItems(selectedKey);
					newEntity.setSelectedKey(Entity.NO_SELECTION);
				}
			}
			context.mutationSink.next(new MutationBlockReplace(_target, block, env.special.blockTilledSoil));
			didApply = true;
		}
		else if (env.special.itemPortalOrb == type)
		{
			// Set the location in the orb to the touched block.
			AbsoluteLocation oldTarget = PropertyHelpers.getLocation(nonStack);
			if (!_target.equals(oldTarget))
			{
				// We should replace this and update the orb.
				Map<PropertyType<?>, Object> props = new HashMap<>(nonStack.properties());
				props.put(PropertyRegistry.LOCATION, _target);
				NonStackableItem newItem = new NonStackableItem(type, Collections.unmodifiableMap(props));
				mutableInventory.replaceNonStackable(selectedKey, newItem);
			}
			didApply = true;
		}
		return didApply;
	}
}
