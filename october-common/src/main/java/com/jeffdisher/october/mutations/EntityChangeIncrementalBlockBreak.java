package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.IMutableInventory;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Issues a "MutationBlockIncrementalBreak" to incrementally break the target block.
 * Note that we typically use a long to apply time values but the underlying damage being changed is a short so we use
 * that instead.
 */
public class EntityChangeIncrementalBlockBreak implements IMutationEntity<IMutablePlayerEntity>
{
	public static final MutationEntityType TYPE = MutationEntityType.INCREMENTAL_BREAK_BLOCK;

	public static EntityChangeIncrementalBlockBreak deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation target = CodecHelpers.readAbsoluteLocation(buffer);
		buffer.getShort();
		return new EntityChangeIncrementalBlockBreak(target);
	}


	private final AbsoluteLocation _targetBlock;

	public EntityChangeIncrementalBlockBreak(AbsoluteLocation targetBlock)
	{
		_targetBlock = targetBlock;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		Environment env = Environment.getShared();
		// We will just check that the block is in range and isn't air (we won't worry about whether or not it is breakable).
		
		// Find the distance from the eye to the target.
		float distance = SpatialHelpers.distanceFromMutableEyeToBlockSurface(newEntity, _targetBlock);
		boolean isLocationClose = (distance <= MiscConstants.REACH_BLOCK);
		
		MultiBlockUtils.Lookup lookup = isLocationClose
				? MultiBlockUtils.getLoadedRoot(env, context, _targetBlock)
				: null
		;
		
		boolean isAir = (null == lookup) || env.blocks.canBeReplaced(lookup.rootProxy().getBlock());
		
		boolean didApply = false;
		if (isLocationClose && !isAir)
		{
			// We know that tools are non-stackable so just check for those types.
			int selectedKey = newEntity.getSelectedKey();
			IMutableInventory mutableInventory = newEntity.accessMutableInventory();
			NonStackableItem selected = mutableInventory.getNonStackableForKey(selectedKey);
			Item selectedItem = (null != selected)
					? selected.type()
					: null
			;
			int breakingMillis = (int)context.millisPerTick;
			// We will apply max damage if creative, otherwise we will consider the tool and material.
			short damageToApply;
			if (newEntity.isCreativeMode())
			{
				damageToApply = Short.MAX_VALUE;
			}
			else
			{
				int speedMultiplier;
				if (env.blocks.getBlockMaterial(lookup.rootProxy().getBlock()) == env.tools.toolTargetMaterial(selectedItem))
				{
					// The tool material matches so set the multiplier.
					speedMultiplier = env.tools.toolSpeedModifier(selectedItem);
				}
				else
				{
					// This doesn't match so use the default of 1.
					speedMultiplier = 1;
				}
				damageToApply = (short)(speedMultiplier * breakingMillis);
			}
			MultiBlockUtils.sendMutationToAll(context, (AbsoluteLocation location) -> {
				MutationBlockIncrementalBreak mutation = new MutationBlockIncrementalBreak(location, damageToApply, newEntity.getId());
				return mutation;
			}, lookup);
			
			// If we have a tool with finite durability equipped, apply this amount of time to wear it down.
			if ((null != selected) && !newEntity.isCreativeMode())
			{
				int totalDurability = env.durability.getDurability(selected.type());
				if (totalDurability > 0)
				{
					int newDurability = selected.durability() - breakingMillis;
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
			newEntity.applyEnergyCost(breakingMillis);
			
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
		buffer.putShort((short)0); // millis no longer stored.
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
		return "Incremental break " + _targetBlock;
	}
}
