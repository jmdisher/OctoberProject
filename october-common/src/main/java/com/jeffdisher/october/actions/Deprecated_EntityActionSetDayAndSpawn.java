package com.jeffdisher.october.actions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.mutations.EntityActionType;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


public class Deprecated_EntityActionSetDayAndSpawn implements IEntityAction<IMutablePlayerEntity>
{
	public static final EntityActionType TYPE = EntityActionType.DEPRECATED_SET_DAY_AND_SPAWN;

	public static Deprecated_EntityActionSetDayAndSpawn deserialize(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		AbsoluteLocation bedLocation = CodecHelpers.readAbsoluteLocation(buffer);
		return new Deprecated_EntityActionSetDayAndSpawn(bedLocation);
	}


	private final AbsoluteLocation _bedLocation;

	@Deprecated
	public Deprecated_EntityActionSetDayAndSpawn(AbsoluteLocation bedLocation)
	{
		Assert.assertTrue(null != bedLocation);
		
		_bedLocation = bedLocation;
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
		CodecHelpers.writeAbsoluteLocation(buffer, _bedLocation);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Common case.
		return true;
	}

	@Override
	public String toString()
	{
		return "Set spawn on bed at " + _bedLocation;
	}
}
