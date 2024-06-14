package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
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
	public static boolean OPEN_DOOR = true;
	public static boolean CLOSE_DOOR = false;
	public static boolean SWITCH_ON = true;
	public static boolean SWITCH_OFF = false;

	// TODO:  Replace this with something generalized into the broader logic aspect design.
	private static final String STRING_DOOR_CLOSED = "op.door_closed";
	private static final String STRING_DOOR_OPEN = "op.door_open";
	private static final String STRING_SWITCH_OFF = "op.switch_off";
	private static final String STRING_SWITCH_ON = "op.switch_on";

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
		
		// TODO:  Generalize this lookup into data.
		Block doorClosed = env.blocks.getAsPlaceableBlock(env.items.getItemById(STRING_DOOR_CLOSED));
		Block doorOpen = env.blocks.getAsPlaceableBlock(env.items.getItemById(STRING_DOOR_OPEN));
		Block switchOff = env.blocks.getAsPlaceableBlock(env.items.getItemById(STRING_SWITCH_OFF));
		Block switchOn = env.blocks.getAsPlaceableBlock(env.items.getItemById(STRING_SWITCH_ON));
		return (block == doorClosed)
				|| (block == doorOpen)
				|| (block == switchOff)
				|| (block == switchOn)
		;
	}

	public static boolean getCurrentBlockLogicState(Block block)
	{
		// Logic interpretation as "high":  Open door and switch on.
		Environment env = Environment.getShared();
		Block doorClosed = env.blocks.getAsPlaceableBlock(env.items.getItemById(STRING_DOOR_CLOSED));
		Block doorOpen = env.blocks.getAsPlaceableBlock(env.items.getItemById(STRING_DOOR_OPEN));
		Block switchOff = env.blocks.getAsPlaceableBlock(env.items.getItemById(STRING_SWITCH_OFF));
		Block switchOn = env.blocks.getAsPlaceableBlock(env.items.getItemById(STRING_SWITCH_ON));
		Assert.assertTrue((block == doorClosed)
				|| (block == doorOpen)
				|| (block == switchOff)
				|| (block == switchOn)
		);
		return (block == doorOpen)
				|| (block == switchOn)
		;
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
		EntityLocation entityCentre = SpatialHelpers.getEntityCentre(newEntity.getLocation(), newEntity.getVolume());
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
			
			// Logic interpretation as "high":  Open door and switch on.
			Block doorClosed = env.blocks.getAsPlaceableBlock(env.items.getItemById(STRING_DOOR_CLOSED));
			Block doorOpen = env.blocks.getAsPlaceableBlock(env.items.getItemById(STRING_DOOR_OPEN));
			Block switchOff = env.blocks.getAsPlaceableBlock(env.items.getItemById(STRING_SWITCH_OFF));
			Block switchOn = env.blocks.getAsPlaceableBlock(env.items.getItemById(STRING_SWITCH_ON));
			if (_setHigh)
			{
				// Open the door.
				if (doorClosed == previousBlock)
				{
					context.mutationSink.next(new MutationBlockReplace(_targetBlock, previousBlock, doorOpen));
					didApply = true;
				}
				// Switch on.
				else if (switchOff == previousBlock)
				{
					context.mutationSink.next(new MutationBlockReplace(_targetBlock, previousBlock, switchOn));
					didApply = true;
				}
			}
			else
			{
				// Close the door.
				if (doorOpen == previousBlock)
				{
					context.mutationSink.next(new MutationBlockReplace(_targetBlock, previousBlock, doorClosed));
					didApply = true;
				}
				// Switch off.
				else if (switchOn == previousBlock)
				{
					context.mutationSink.next(new MutationBlockReplace(_targetBlock, previousBlock, switchOff));
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
}
