package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.types.Entity;
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
		// Check that the target is in range.  We will use block breaking distance.
		boolean isInRange;
		Entity targetEntity = context.previousEntityLookUp.apply(_targetEntityId);
		if (null != targetEntity)
		{
			// The target is loaded so check the distances.
			EntityLocation targetCentre = SpatialHelpers.getEntityCentre(targetEntity.location(), targetEntity.volume());
			EntityLocation entityCentre = SpatialHelpers.getEntityCentre(newEntity.newLocation, newEntity.original.volume());
			float absX = Math.abs(targetCentre.x() - entityCentre.x());
			float absY = Math.abs(targetCentre.y() - entityCentre.y());
			float absZ = Math.abs(targetCentre.z() - entityCentre.z());
			isInRange = ((absX <= EntityChangeIncrementalBlockBreak.MAX_REACH) && (absY <= EntityChangeIncrementalBlockBreak.MAX_REACH) && (absZ <= EntityChangeIncrementalBlockBreak.MAX_REACH));
		}
		else
		{
			// Not loaded so just say no.
			isInRange = false;
		}
		if (isInRange)
		{
			EntityChangeTakeDamage takeDamage = new EntityChangeTakeDamage(DAMAGE_PER_ATTACK);
			context.newChangeSink.next(_targetEntityId, takeDamage);
		}
		return isInRange;
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
