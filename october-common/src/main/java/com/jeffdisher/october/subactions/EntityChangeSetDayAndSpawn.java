package com.jeffdisher.october.subactions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.logic.PropagationHelpers;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.mutations.EntitySubActionType;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.IEntitySubAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Called to reset the day and set the spawn for the entity.
 */
public class EntityChangeSetDayAndSpawn implements IEntitySubAction<IMutablePlayerEntity>
{
	public static final EntitySubActionType TYPE = EntitySubActionType.SET_DAY_AND_SPAWN;

	public static EntityChangeSetDayAndSpawn deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation bedLocation = CodecHelpers.readAbsoluteLocation(buffer);
		return new EntityChangeSetDayAndSpawn(bedLocation);
	}


	private final AbsoluteLocation _bedLocation;

	public EntityChangeSetDayAndSpawn(AbsoluteLocation bedLocation)
	{
		Assert.assertTrue(null != bedLocation);
		
		_bedLocation = bedLocation;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		// Make sure that the target is a bed.
		Environment env = Environment.getShared();
		boolean isBed = (env.special.blockBed == context.previousBlockLookUp.apply(_bedLocation).getBlock());
		
		boolean isInRange = false;
		if (isBed)
		{
			// Find the distance from the eye to the bed.
			float distance = SpatialHelpers.distanceFromMutableEyeToBlockSurface(newEntity, _bedLocation);
			isInRange = (distance <= MiscConstants.REACH_BLOCK);
		}
		
		boolean didApply = false;
		if (isInRange)
		{
			// Set spawn.
			newEntity.setSpawnLocation(newEntity.getLocation());
			
			// We will reset the day start in the shared WorldConfig instance (note that doing so is racy but should be harmless) and ServerRunner will observe this and broadcast.
			context.config.dayStartTick = (int)PropagationHelpers.startDayThisTick(context.currentTick, context.config.ticksPerDay);
			
			newEntity.setCurrentChargeMillis(0);
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
