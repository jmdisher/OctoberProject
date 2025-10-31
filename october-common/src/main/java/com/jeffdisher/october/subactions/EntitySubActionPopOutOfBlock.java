package com.jeffdisher.october.subactions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.logic.ViscosityReader;
import com.jeffdisher.october.mutations.EntitySubActionType;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.IEntitySubAction;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * This sub-action is used to get around the case of an entity being stuck in a block, typically due to synchronization
 * issues which come up due to movement and block placement happening concurrently.
 * It checks that the entity is currently stuck in a block, verifies that the target location is within range of this
 * work-around, and that the destination is one where the entity can exist (not colliding with solid blocks).
 */
public class EntitySubActionPopOutOfBlock<T extends IMutableMinimalEntity> implements IEntitySubAction<T>
{
	public static final float POP_OUT_MAX_DISTANCE = 0.4f;
	public static final EntitySubActionType TYPE = EntitySubActionType.POP_OUT;

	public static <T extends IMutableMinimalEntity> EntitySubActionPopOutOfBlock<T> deserializeFromBuffer(ByteBuffer buffer)
	{
		EntityLocation newLocation = CodecHelpers.readEntityLocation(buffer);
		return new EntitySubActionPopOutOfBlock<>(newLocation);
	}


	private final EntityLocation _newLocation;

	public EntitySubActionPopOutOfBlock(EntityLocation newLocation)
	{
		Assert.assertTrue(null != newLocation);
		
		_newLocation = newLocation;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutableMinimalEntity newEntity)
	{
		boolean didApply = false;
		
		// We will apply this if it is required, is within range, and would free us.
		EntityLocation location = newEntity.getLocation();
		ViscosityReader reader = new ViscosityReader(Environment.getShared(), context.previousBlockLookUp);
		EntityVolume volume = newEntity.getType().volume();
		float delta = Math.abs(location.x() - _newLocation.x()) + Math.abs(location.y() - _newLocation.y()) + Math.abs(location.z() - _newLocation.z());
		if ((delta <= POP_OUT_MAX_DISTANCE) && !SpatialHelpers.canExistInLocation(reader, location, volume) && SpatialHelpers.canExistInLocation(reader, _newLocation, volume))
		{
			newEntity.setLocation(_newLocation);
			newEntity.setVelocityVector(new EntityLocation(0.0f, 0.0f, 0.0f));
			didApply = true;
		}
		return didApply;
	}

	@Override
	public EntitySubActionType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writeEntityLocation(buffer, _newLocation);
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
		return "Pop-out to " + _newLocation;
	}
}
