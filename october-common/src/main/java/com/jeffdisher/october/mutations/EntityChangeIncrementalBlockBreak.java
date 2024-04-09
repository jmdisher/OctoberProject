package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Issues a "MutationBlockIncrementalBreak" to incrementally break the target block.
 * Note that we typically use a long to apply time values but the underlying damage being changed is a short so we use
 * that instead.
 */
public class EntityChangeIncrementalBlockBreak implements IMutationEntity
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
	public boolean applyChange(TickProcessingContext context, MutableEntity newEntity)
	{
		Environment env = Environment.getShared();
		// We will just check that the block is in range and isn't air (we won't worry about whether or not it is breakable).
		
		// We want to only consider breaking the block if it is within 2 blocks of where the entity currently is.
		EntityLocation entityCentre = _entityCentre(newEntity.newLocation, newEntity.original.volume());
		EntityLocation blockCentre = _blockCentre(_targetBlock);
		float absX = Math.abs(blockCentre.x() - entityCentre.x());
		float absY = Math.abs(blockCentre.y() - entityCentre.y());
		float absZ = Math.abs(blockCentre.z() - entityCentre.z());
		boolean isLocationClose = ((absX <= MAX_REACH) && (absY <= MAX_REACH) && (absZ <= MAX_REACH));
		// Note that the cuboid could theoretically not be loaded (although this shouldn't happen in normal clients).
		BlockProxy proxy = context.previousBlockLookUp.apply(_targetBlock);
		boolean isAir = (null == proxy) || env.blocks.canBeReplaced(proxy.getBlock());
		
		boolean didApply = false;
		if (isLocationClose && !isAir)
		{
			// TODO:  Use a real multiplier once we have tools.  This should be 1 for "no tool" but we use 10 to speed up play testing.
			short damageToApply = (short)(10 * _millisToApply);
			MutationBlockIncrementalBreak mutation = new MutationBlockIncrementalBreak(_targetBlock, damageToApply, newEntity.original.id());
			context.newMutationSink.accept(mutation);
			didApply = true;
			
			// Do other state reset.
			newEntity.newLocalCraftOperation = null;
		}
		
		// Account for any movement while we were busy.
		// NOTE:  This is currently wrong as it is only applied in the last part of the operation, not each tick.
		// This will need to be revisited when we change how blocks are broken.
		boolean didMove = EntityChangeMove.handleMotion(newEntity, context.previousBlockLookUp, _millisToApply);
		
		return didApply || didMove;
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


	private EntityLocation _entityCentre(EntityLocation base, EntityVolume volume)
	{
		float halfWidth = volume.width() / 2.0f;
		return new EntityLocation(
				base.x() + halfWidth,
				base.y() + halfWidth,
				base.z() + (volume.height() / 2.0f)
		);
	}

	private EntityLocation _blockCentre(AbsoluteLocation block)
	{
		return new EntityLocation(
				block.x() + 0.5f,
				block.y() + 0.5f,
				block.z() + 0.5f
		);
	}
}
