package com.jeffdisher.october.actions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.mutations.EntityActionType;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * An entity mutation for local use by an operator at the server console to teleport a player entity to a specific
 * location (also resets velocity).
 */
public class EntityChangeOperatorSetLocation implements IEntityAction<IMutablePlayerEntity>
{
	public static final EntityActionType TYPE = EntityActionType.OPERATOR_SET_LOCATION;

	public static EntityChangeOperatorSetLocation deserialize(DeserializationContext context)
	{
		// This is never serialized.
		throw Assert.unreachable();
	}


	private final EntityLocation _location;

	public EntityChangeOperatorSetLocation(EntityLocation location)
	{
		_location = location;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		newEntity.setLocation(_location);
		newEntity.setVelocityVector(new EntityLocation(0.0f, 0.0f, 0.0f));
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
		// This is never serialized.
		throw Assert.unreachable();
	}

	@Override
	public boolean canSaveToDisk()
	{
		// It doesn't make sense to serialize this.
		return false;
	}

	@Override
	public String toString()
	{
		return "Operator-SetLocation" + _location;
	}
}
