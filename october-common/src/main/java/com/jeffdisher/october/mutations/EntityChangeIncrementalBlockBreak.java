package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityConstants;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.IMutableInventory;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Issues a "MutationBlockIncrementalBreak" to incrementally break the target block.
 * Note that we typically use a long to apply time values but the underlying damage being changed is a short so we use
 * that instead.
 */
public class EntityChangeIncrementalBlockBreak implements IMutationEntity<IMutablePlayerEntity>
{
	public static final MutationEntityType TYPE = MutationEntityType.INCREMENTAL_BREAK_BLOCK;
	public static final float MAX_REACH = 1.5f;

	public static EntityChangeIncrementalBlockBreak deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation target = CodecHelpers.readAbsoluteLocation(buffer);
		short millisToApply = buffer.getShort();
		return new EntityChangeIncrementalBlockBreak(target, millisToApply);
	}


	private final AbsoluteLocation _targetBlock;
	private final short _millisToApply;

	public EntityChangeIncrementalBlockBreak(AbsoluteLocation targetBlock, short millisToApply)
	{
		// Make sure that this is positive.
		Assert.assertTrue(millisToApply > 0);
		
		_targetBlock = targetBlock;
		_millisToApply = millisToApply;
	}

	@Override
	public long getTimeCostMillis()
	{
		return _millisToApply;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		Environment env = Environment.getShared();
		// We will just check that the block is in range and isn't air (we won't worry about whether or not it is breakable).
		
		// We want to only consider breaking the block if it is within 2 blocks of where the entity currently is.
		EntityLocation entityCentre = SpatialHelpers.getEntityCentre(newEntity.getLocation(), EntityConstants.getVolume(newEntity.getType()));
		EntityLocation blockCentre = SpatialHelpers.getBlockCentre(_targetBlock);
		float absX = Math.abs(blockCentre.x() - entityCentre.x());
		float absY = Math.abs(blockCentre.y() - entityCentre.y());
		float absZ = Math.abs(blockCentre.z() - entityCentre.z());
		boolean isLocationClose = ((absX <= MAX_REACH) && (absY <= MAX_REACH) && (absZ <= MAX_REACH));
		// Note that the cuboid could theoretically not be loaded (although this shouldn't happen in normal clients).
		BlockProxy proxy = context.previousBlockLookUp.apply(_targetBlock);
		boolean isAir = (null == proxy) || env.blocks.canBeReplaced(proxy.getBlock());
		IMutableInventory mutableInventory = newEntity.accessMutableInventory();
		
		boolean didApply = false;
		if (isLocationClose && !isAir)
		{
			// We know that tools are non-stackable so just check for those types.
			int selectedKey = newEntity.getSelectedKey();
			NonStackableItem selected = mutableInventory.getNonStackableForKey(selectedKey);
			Item selectedItem = (null != selected)
					? selected.type()
					: null
			;
			// We will apply max damage if creative, otherwise we will consider the tool and material.
			short damageToApply;
			if (newEntity.isCreativeMode())
			{
				damageToApply = Short.MAX_VALUE;
			}
			else
			{
				int speedMultiplier;
				if (env.blocks.getBlockMaterial(proxy.getBlock()) == env.tools.toolTargetMaterial(selectedItem))
				{
					// The tool material matches so set the multiplier.
					speedMultiplier = env.tools.toolSpeedModifier(selectedItem);
				}
				else
				{
					// This doesn't match so use the default of 1.
					speedMultiplier = 1;
				}
				damageToApply = (short)(speedMultiplier * _millisToApply);
			}
			MutationBlockIncrementalBreak mutation = new MutationBlockIncrementalBreak(_targetBlock, damageToApply, newEntity.getId());
			context.mutationSink.next(mutation);
			
			// If we have a tool with finite durability equipped, apply this amount of time to wear it down.
			if ((null != selected) && !newEntity.isCreativeMode())
			{
				int totalDurability = env.durability.getDurability(selected.type());
				if (totalDurability > 0)
				{
					int newDurability = selected.durability() - _millisToApply;
					if (newDurability > 0)
					{
						// Write this back.
						NonStackableItem updated = new NonStackableItem(selected.type(), newDurability);
						mutableInventory.replaceNonStackable(selectedKey, updated);
					}
					else
					{
						// Remove this and clear the selection.
						mutableInventory.removeNonStackableItems(selectedKey);
						newEntity.setSelectedKey(Entity.NO_SELECTION);
					}
				}
			}
			didApply = true;
			
			// Do other state reset.
			newEntity.setCurrentCraftingOperation(null);
			
			// Breaking a block expends energy proportional to breaking time.
			newEntity.applyEnergyCost(_millisToApply);
			
			// While this is an action which is considered primary, it should actually delay secondary actions, too.
			newEntity.setLastSpecialActionMillis(context.currentTickTimeMillis);
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
		CodecHelpers.writeAbsoluteLocation(buffer, _targetBlock);
		buffer.putShort(_millisToApply);
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
		return "Incremental break " + _targetBlock + " for " + _millisToApply + " ms";
	}
}
