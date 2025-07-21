package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Craft;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


public class Deprecated_EntityActionCraftInBlock implements IEntityAction<IMutablePlayerEntity>
{
	public static final EntityActionType TYPE = EntityActionType.DEPRECATED_CRAFT_IN_BLOCK;

	public static Deprecated_EntityActionCraftInBlock deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation targetBlock = CodecHelpers.readAbsoluteLocation(buffer);
		Craft craft = CodecHelpers.readCraft(buffer);
		buffer.getLong();
		return new Deprecated_EntityActionCraftInBlock(targetBlock, craft);
	}


	private final AbsoluteLocation _targetBlock;
	private final Craft _craft;

	@Deprecated
	public Deprecated_EntityActionCraftInBlock(AbsoluteLocation targetBlock, Craft craft)
	{
		Assert.assertTrue(null != targetBlock);
		// Note that craft can be null if it just means "continue".
		
		_targetBlock = targetBlock;
		_craft = craft;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		// Not used.
		return true;
	}

	@Override
	public EntityActionType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writeAbsoluteLocation(buffer, _targetBlock);
		CodecHelpers.writeCraft(buffer, _craft);
		buffer.putLong(0L); // millis no longer stored.
	}

	@Override
	public boolean canSaveToDisk()
	{
		// The block may have changed so drop this.
		return false;
	}

	@Override
	public String toString()
	{
		return "Craft " + _craft + " in block " + _targetBlock;
	}
}
