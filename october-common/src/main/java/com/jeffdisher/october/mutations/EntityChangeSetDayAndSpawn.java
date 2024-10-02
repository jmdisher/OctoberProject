package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.logic.PropagationHelpers;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.EntityConstants;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Called to reset the day and set the spawn for the entity.
 */
public class EntityChangeSetDayAndSpawn implements IMutationEntity<IMutablePlayerEntity>
{
	public static final MutationEntityType TYPE = MutationEntityType.SET_DAY_AND_SPAWN;

	public static EntityChangeSetDayAndSpawn deserializeFromBuffer(ByteBuffer buffer)
	{
		EntityLocation spawnLocation = CodecHelpers.readEntityLocation(buffer);
		return new EntityChangeSetDayAndSpawn(spawnLocation);
	}


	private final EntityLocation _spawnLocation;

	public EntityChangeSetDayAndSpawn(EntityLocation spawnLocation)
	{
		Assert.assertTrue(null != spawnLocation);
		
		_spawnLocation = spawnLocation;
	}

	@Override
	public long getTimeCostMillis()
	{
		// We want this to take some time so just return a large constant.
		return 100L;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		// Make sure that the target is reasonably within range.
		EntityLocation targetCentre = SpatialHelpers.getEntityCentre(_spawnLocation, EntityConstants.getVolume(newEntity.getType()));
		EntityLocation entityCentre = SpatialHelpers.getEntityCentre(newEntity.getLocation(), EntityConstants.getVolume(newEntity.getType()));
		float absX = Math.abs(targetCentre.x() - entityCentre.x());
		float absY = Math.abs(targetCentre.y() - entityCentre.y());
		float absZ = Math.abs(targetCentre.z() - entityCentre.z());
		boolean isInRange = ((absX <= EntityChangeIncrementalBlockBreak.MAX_REACH) && (absY <= EntityChangeIncrementalBlockBreak.MAX_REACH) && (absZ <= EntityChangeIncrementalBlockBreak.MAX_REACH));
		
		boolean didApply = false;
		if (isInRange)
		{
			// Set spawn.
			newEntity.setSpawnLocation(_spawnLocation);
			
			// We will reset the day start in the shared WorldConfig instance (note that doing so is racy but should be harmless) and ServerRunner will observe this and broadcast.
			context.config.dayStartTick = (int)PropagationHelpers.resumableStartTick(context.currentTick, context.config.ticksPerDay, context.config.dayStartTick);
			
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
		CodecHelpers.writeEntityLocation(buffer, _spawnLocation);
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
		return "Set Spawn " + _spawnLocation;
	}
}
