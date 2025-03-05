package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Issues a "MutationBlockIncrementalRepair" to incrementally repair the target block or extinguish a fire within it.
 * Note that we typically use a long to apply time values but the underlying damage being changed is a short so we use
 * that instead.
 */
public class EntityChangeIncrementalBlockRepair implements IMutationEntity<IMutablePlayerEntity>
{
	public static final MutationEntityType TYPE = MutationEntityType.INCREMENTAL_REPAIR_BLOCK;

	public static EntityChangeIncrementalBlockRepair deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation target = CodecHelpers.readAbsoluteLocation(buffer);
		short millisToApply = buffer.getShort();
		return new EntityChangeIncrementalBlockRepair(target, millisToApply);
	}


	private final AbsoluteLocation _targetBlock;
	private final short _millisToApply;

	public EntityChangeIncrementalBlockRepair(AbsoluteLocation targetBlock, short millisToApply)
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
		// Repairing a block requires a few things:
		// 1) There must be nothing in the entity's hand.
		// 2) They must be able to reach the target block.
		// 3) The block must have a positive damage value or be on fire.
		Environment env = Environment.getShared();
		
		boolean isHandEmpty = (Entity.NO_SELECTION == newEntity.getSelectedKey());
		
		// Find the distance from the eye to the target.
		float distance = SpatialHelpers.distanceFromEyeToBlockSurface(newEntity, _targetBlock);
		boolean isReachable = (distance <= MiscConstants.REACH_BLOCK);
		
		// Note that the cuboid could theoretically not be loaded (although this shouldn't happen in normal clients).
		MultiBlockUtils.Lookup lookup = (isHandEmpty && isReachable)
				? MultiBlockUtils.getLoadedRoot(env, context, _targetBlock)
				: null
		;
		
		boolean canRepairSomething = false;
		if (null != lookup)
		{
			boolean hasPositiveDamage = (lookup.rootProxy().getDamage() > 0);
			boolean isOnFire = FlagsAspect.isSet(lookup.rootProxy().getFlags(), FlagsAspect.FLAG_BURNING);
			canRepairSomething = (hasPositiveDamage || isOnFire);
		}
		
		boolean didApply = false;
		if (canRepairSomething)
		{
			// We can do something so send the mutation to the block (it will apply the change with bounds checks).
			MultiBlockUtils.sendMutationToAll(context, (AbsoluteLocation location) -> {
				MutationBlockIncrementalRepair mutation = new MutationBlockIncrementalRepair(location, _millisToApply);
				return mutation;
			}, lookup);
			didApply = true;
			
			// Do other state reset.
			newEntity.setCurrentCraftingOperation(null);
			
			// Repairing a block expends energy proportional to repairing time.
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
