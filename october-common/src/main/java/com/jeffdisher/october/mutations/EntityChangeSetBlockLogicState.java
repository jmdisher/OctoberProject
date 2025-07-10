package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Called to change the logical block state of a block when being manually set by an entity.
 * This calls MutationBlockSetLogicState to change the logic state of the underlying block.
 * This is typically used for things like setting a door to the open or closed state.
 */
public class EntityChangeSetBlockLogicState implements IMutationEntity<IMutablePlayerEntity>
{
	public static final MutationEntityType TYPE = MutationEntityType.SET_BLOCK_LOGIC_STATE;

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

	public static boolean getCurrentBlockLogicState(Block block, byte flags)
	{
		Environment env = Environment.getShared();
		
		// We can only ask this for manual cases.
		Assert.assertTrue(env.logic.isManual(block));
		return FlagsAspect.isSet(flags, FlagsAspect.FLAG_ACTIVE);
	}


	private final AbsoluteLocation _targetBlock;
	private final boolean _setHigh;

	public EntityChangeSetBlockLogicState(AbsoluteLocation targetBlock, boolean setHigh)
	{
		_targetBlock = targetBlock;
		_setHigh = setHigh;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		// Make sure that this is in range.
		float distance = SpatialHelpers.distanceFromEyeToBlockSurface(newEntity, _targetBlock);
		boolean isLocationClose = (distance <= MiscConstants.REACH_BLOCK);
		BlockProxy previous = context.previousBlockLookUp.apply(_targetBlock);
		
		boolean didApply = false;
		if (isLocationClose && (null != previous))
		{
			Environment env = Environment.getShared();
			Block previousBlock = previous.getBlock();
			
			if (env.logic.isManual(previousBlock))
			{
				// This is manual so we can trigger this but check if it is a multi-block and make sure we are looking at the root.
				MultiBlockUtils.Lookup lookup = MultiBlockUtils.getLoadedRoot(env, context, _targetBlock);
				MultiBlockUtils.sendMutationToAll(context, (AbsoluteLocation location) -> {
					MutationBlockSetLogicState mutation = new MutationBlockSetLogicState(location, _setHigh);
					return mutation;
				}, lookup);
				didApply = true;
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
