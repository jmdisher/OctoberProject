package com.jeffdisher.october.actions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.mutations.EntityActionType;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * An entity mutation for local use by an operator at the server console to spawn a specific creature in a specific
 * location (with default health).
 */
public class EntityActionOperatorSpawnCreature implements IEntityAction<IMutablePlayerEntity>
{
	public static final EntityActionType TYPE = EntityActionType.OPERATOR_SPAWN_CREATURE;

	public static EntityActionOperatorSpawnCreature deserialize(DeserializationContext context)
	{
		// This is never serialized.
		throw Assert.unreachable();
	}


	private final EntityType _type;
	private final EntityLocation _location;

	public EntityActionOperatorSpawnCreature(EntityType type, EntityLocation location)
	{
		_type = type;
		_location = location;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		context.creatureSpawner.spawnCreature(_type, _location, _type.maxHealth());
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
		return "Operator-SpawnCreature" + _location;
	}
}
