package com.jeffdisher.october.mutations;

import java.util.function.Function;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.logic.EntityMovementHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.EntityConstants;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.IMutableCreatureEntity;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * A utility class which allows for motion and entity state updates during a tick, not directly associated with a
 * specific change.
 * Implicitly added by CrowdProcessor and CreatureProcessor at the very end of a tick to account for things like gravity
 * and applying the velocity vector to location.
 */
public class TickUtils
{
	private TickUtils()
	{
		// There is no need to instantiate this.
	}

	public static void allowMovement(Function<AbsoluteLocation, BlockProxy> previousBlockLookUp, IMutableMinimalEntity newEntity, long millisToMove)
	{
		EntityLocation oldLocation = newEntity.getLocation();
		EntityMovementHelpers.allowMovement(previousBlockLookUp, newEntity, millisToMove);
		boolean didApply = !oldLocation.equals(newEntity.getLocation());
		
		if (didApply)
		{
			// Do other state reset now that we are moving.
			newEntity.resetLongRunningOperations();
		}
	}

	public static void endOfTick(TickProcessingContext context, IMutableMinimalEntity newEntity)
	{
		// Note that we handle food/starvation in EntityChangePeriodic, since it is specific to player entities, but we handle breath/drowning here, since it is common.
		EntityLocation footLocation = newEntity.getLocation();
		EntityVolume volume = EntityConstants.getVolume(newEntity.getType());
		float halfWidth = volume.width() / 2.0f;
		// (we use the floor since that is the block address)
		AbsoluteLocation headLocation = new AbsoluteLocation((int)Math.floor(footLocation.x() + halfWidth)
				, (int)Math.floor(footLocation.y() + halfWidth)
				, (int)Math.floor(footLocation.z() + volume.height())
		);
		BlockProxy headProxy = context.previousBlockLookUp.apply(headLocation);
		if (null != headProxy)
		{
			Environment env = Environment.getShared();
			boolean isBreathable = env.blocks.canBreatheInBlock(headProxy.getBlock());
			if (isBreathable)
			{
				// Reset breath.
				newEntity.setBreath(EntityConstants.MAX_BREATH);
			}
			else
			{
				// Suffocate.
				int breath = newEntity.getBreath();
				if (breath > 0)
				{
					breath -= 1;
					newEntity.setBreath(breath);
				}
				else
				{
					// The damage isn't applied to a specific body part.
					int id = newEntity.getId();
					if (id > 0)
					{
						EntityChangeTakeDamage<IMutablePlayerEntity> takeDamage = new EntityChangeTakeDamage<>(null, (byte)1);
						context.newChangeSink.next(id, takeDamage);
					}
					else
					{
						EntityChangeTakeDamage<IMutableCreatureEntity> takeDamage = new EntityChangeTakeDamage<>(null, (byte)1);
						context.newChangeSink.creature(id, takeDamage);
					}
				}
			}
		}
	}
}
