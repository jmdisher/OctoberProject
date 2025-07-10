package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.logic.OrientationHelpers;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


public class Deprecated_EntityChangeSetOrientation<T extends IMutableMinimalEntity> implements IMutationEntity<T>
{
	public static final MutationEntityType TYPE = MutationEntityType.DEPRECATED_SET_ORIENTATION;

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
		// We don't want to apply this if it doesn't change anything (this is mostly to avoid a redundant check on the client-side).
		boolean didApply = false;
		if ((newEntity.getYaw() != _yaw) || (newEntity.getPitch() != _pitch))
		{
			newEntity.setOrientation(_yaw, _pitch);
			didApply = true;
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
