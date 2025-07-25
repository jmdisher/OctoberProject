package com.jeffdisher.october.actions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.logic.OrientationHelpers;
import com.jeffdisher.october.mutations.EntityActionType;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


public class Deprecated_EntityChangeSetOrientation<T extends IMutableMinimalEntity> implements IEntityAction<T>
{
	public static final EntityActionType TYPE = EntityActionType.DEPRECATED_SET_ORIENTATION;

	public static <T extends IMutableMinimalEntity> Deprecated_EntityChangeSetOrientation<T> deserializeFromBuffer(ByteBuffer buffer)
	{
		byte yaw = buffer.get();
		byte pitch = buffer.get();
		return new Deprecated_EntityChangeSetOrientation<>(yaw, pitch);
	}


	private final byte _yaw;
	private final byte _pitch;

	@Deprecated
	public Deprecated_EntityChangeSetOrientation(byte yaw, byte pitch)
	{
		// Make sure that this is valid within our limits.
		// TODO:  Define a better failure mode when the server deserializes these from the network.
		Assert.assertTrue(pitch >= OrientationHelpers.PITCH_DOWN);
		Assert.assertTrue(pitch <= OrientationHelpers.PITCH_UP);
		
		_yaw = yaw;
		_pitch = pitch;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutableMinimalEntity newEntity)
	{
		// This is deprecated so just do nothing (only exists to read old data).
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
		buffer.put(_yaw);
		buffer.put(_pitch);
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
		return "Orient " + _yaw + " by " + _pitch;
	}
}
