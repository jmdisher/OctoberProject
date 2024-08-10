package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.EntityConstants;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Called to change the logical block state of a block.  Note that for now (at least), this involves changing the block
 * type so this will issue a "MutationBlockReplace" if the block can be changed to a different logical state equivalent.
 * This is typically used for things like setting a door to the open or closed state.
 */
public class EntityChangeSetBlockLogicState implements IMutationEntity<IMutablePlayerEntity>
{
	public static final MutationEntityType TYPE = MutationEntityType.SET_BLOCK_LOGIC_STATE;
	public static final float MAX_REACH = 1.5f;

	public static EntityChangeSetBlockLogicState deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation target = CodecHelpers.readAbsoluteLocation(buffer);
		boolean setHigh = CodecHelpers.readBoolean(buffer);
		return new EntityChangeSetBlockLogicState(target, setHigh);
	}

	public static boolean canChangeBlockLogicState(Block block)
	{
		// Check that this block is one of the logic-sensitive types.
		Environment env = Environment.getShared();
		
		// This must be a manually-triggered case.
		return env.logic.isManual(block);
	}

	public static boolean getCurrentBlockLogicState(Block block)
	{
		Environment env = Environment.getShared();
		
		// We can only ask this for manual cases.
		Assert.assertTrue(env.logic.isManual(block));
		return env.logic.isHigh(block);
	}


	private final AbsoluteLocation _targetBlock;
	private final boolean _setHigh;

	public EntityChangeSetBlockLogicState(AbsoluteLocation targetBlock, boolean setHigh)
	{
		_targetBlock = targetBlock;
		_setHigh = setHigh;
	}

	@Override
	public long getTimeCostMillis()
	{
		return 0L;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		// Make sure that this is in range.
		EntityLocation entityCentre = SpatialHelpers.getEntityCentre(newEntity.getLocation(), EntityConstants.getVolume(newEntity.getType()));
		EntityLocation blockCentre = SpatialHelpers.getBlockCentre(_targetBlock);
		float absX = Math.abs(blockCentre.x() - entityCentre.x());
		float absY = Math.abs(blockCentre.y() - entityCentre.y());
		float absZ = Math.abs(blockCentre.z() - entityCentre.z());
		boolean isLocationClose = ((absX <= MAX_REACH) && (absY <= MAX_REACH) && (absZ <= MAX_REACH));
		BlockProxy previous = context.previousBlockLookUp.apply(_targetBlock);
		
		boolean didApply = false;
		if (isLocationClose && (null != previous))
		{
			Environment env = Environment.getShared();
			Block previousBlock = previous.getBlock();
			
			if (env.logic.isAware(previousBlock))
			{
				// As long as these are opposites, change to the alternative.
				boolean areOpposite = _setHigh == !env.logic.isHigh(previousBlock);
				if (areOpposite)
				{
					Block alternate = env.logic.getAlternate(previousBlock);
					context.mutationSink.next(new MutationBlockReplace(_targetBlock, previousBlock, alternate));
					didApply = true;
				}
			}
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
		CodecHelpers.writeBoolean(buffer, _setHigh);
	}

	@Override
	public boolean canSaveToDisk()
	{
		return true;
	}

	@Override
	public String toString()
	{
		return "Set logic state of " + _targetBlock + " to " + (_setHigh ? "HIGH" : "LOW");
	}
}
