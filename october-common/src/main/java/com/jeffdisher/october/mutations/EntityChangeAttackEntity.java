package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Issues EntityChangeTakeDamage to the given entity and it will determine if the damage source was in range.  This may
 * change to check the range on the sender side (here), in the future.
 * In the future, we will need this to have some time cost but this is just to get the first step working.
 */
public class EntityChangeAttackEntity implements IMutationEntity
{
	public static final MutationEntityType TYPE = MutationEntityType.ATTACK_ENTITY;
	public static final byte DAMAGE_PER_ATTACK = 20;

	public static EntityChangeAttackEntity deserializeFromBuffer(ByteBuffer buffer)
	{
		int targetEntityId = buffer.getInt();
		return new EntityChangeAttackEntity(targetEntityId);
	}


	private final int _targetEntityId;

	public EntityChangeAttackEntity(int targetEntityId)
	{
		// Make sure that this is positive (currently no other entity types).
		Assert.assertTrue(targetEntityId > 0);
		
		_targetEntityId = targetEntityId;
	}

	@Override
	public long getTimeCostMillis()
	{
		// TODO:  Make this a real cost.
		return 0L;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, MutableEntity newEntity)
	{
		// Send the damage to the other entity, saying it originated from our source.
		// Note that we will likely move the range check here, later on.
		EntityLocation entityCentre = SpatialHelpers.getEntityCentre(newEntity.newLocation, newEntity.original.volume());
		EntityChangeTakeDamage takeDamage = new EntityChangeTakeDamage(entityCentre, DAMAGE_PER_ATTACK);
		context.newChangeSink.next(_targetEntityId, takeDamage);
		return true;
	}

	@Override
	public MutationEntityType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		buffer.putInt(_targetEntityId);
	}
}
