package com.jeffdisher.october.mutations;

import java.util.function.Function;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.logic.EntityMovementHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * A utility class which allows for motion and entity state updates during a tick, not directly associated with a
 * specific change.
 * Implicitly added by CrowdProcessor and CreatureProcessor at the very end of a tick to account for things like gravity
 * and applying the velocity vector to location.
 */
public class TickUtils
{
	/**
	 * We want the damage threshold to be roughly free-fall for 4 metres (which is just over 8 m/s down).
	 */
	public static final float DECELERATION_DAMAGE_THRESHOLD = -8.0f;
	public static final float DECELERATION_DAMAGE_RANGE = EntityMovementHelpers.FALLING_TERMINAL_VELOCITY_PER_SECOND - DECELERATION_DAMAGE_THRESHOLD;
	/**
	 * Fall damage should be the maximum possible amount of damage.
	 */
	public static final byte DECELERATION_DAMAGE_MAX = Byte.MAX_VALUE;
	public static final float DECELERATION_DAMAGE_MAX_FLOAT = DECELERATION_DAMAGE_MAX;

	private TickUtils()
	{
		// There is no need to instantiate this.
	}

	public static void allowMovement(Function<AbsoluteLocation, BlockProxy> previousBlockLookUp
			, IMutableMinimalEntity newEntity
			, long millisToMove
	)
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
		
		// We will only apply the breath mechanics once per second so see if that is this tick.
		// Note that currentTick is set to 0 when running speculatively on the client so skip it there (always >0 when run on server).
		if (context.currentTick > 0L)
		{
			long ticksPerInterval = (MiscConstants.DAMAGE_ENVIRONMENT_CHECK_MILLIS / context.millisPerTick);
			boolean isBeginningOfSecond = (0 == (context.currentTick % ticksPerInterval));
			if (isBeginningOfSecond)
			{
				_applyEndOfTickBreathAndDamage(context, newEntity);
			}
		}
	}

	/**
	 * Calculates the damage to apply based on the loss of falling velocity.
	 * TODO:  This currently assumes the given value is negative but it should be a threshold in all directions.
	 * 
	 * @param deceleration The change in Z-velocity (specifically, the negative velocity erased).
	 * @return The damage to apply (0 if no damage).
	 */
	public static byte calculateFallDamage(float deceleration)
	{
		return _calculateFallDamage(deceleration);
	}


	private static void _applyEndOfTickBreathAndDamage(TickProcessingContext context, IMutableMinimalEntity newEntity)
	{
		// Note that we handle food/starvation in EntityChangePeriodic, since it is specific to player entities, but we handle breath/drowning here, since it is common.
		BlockProxy headProxy = _getHeadProxy(context, newEntity);
		if (null != headProxy)
		{
			Environment env = Environment.getShared();
			Block headBlock = headProxy.getBlock();
			
			// Check if we need to apply breath mechanics.
			boolean isActive = FlagsAspect.isSet(headProxy.getFlags(), FlagsAspect.FLAG_ACTIVE);
			boolean isBreathable = env.blocks.canBreatheInBlock(headBlock, isActive);
			if (isBreathable)
			{
				// Reset breath.
				newEntity.setBreath(MiscConstants.MAX_BREATH);
			}
			else
			{
				// Suffocate.
				byte breath = newEntity.getBreath();
				if (breath > 0)
				{
					breath -= MiscConstants.SUFFOCATION_BREATH_PER_SECOND;
					if (breath < 0)
					{
						breath = 0;
					}
					newEntity.setBreath(breath);
				}
				else
				{
					// Apply the damage directly inline.
					EntityChangeTakeDamageFromOther.applyDamageDirectlyAndPostEvent(context, newEntity, MiscConstants.SUFFOCATION_DAMAGE_PER_SECOND, EventRecord.Cause.SUFFOCATION);
				}
			}
			
			// See if this block actually applies damage.
			int blockDamage = env.blocks.getBlockDamage(headBlock);
			if (blockDamage > 0)
			{
				EntityChangeTakeDamageFromOther.applyDamageDirectlyAndPostEvent(context, newEntity, (byte)blockDamage, EventRecord.Cause.BLOCK_DAMAGE);
			}
			else
			{
				// If not damaging us directly, see if the block under us is on fire.
				BlockProxy underFoot = context.previousBlockLookUp.apply(newEntity.getLocation().getBlockLocation().getRelative(0, 0, -1));
				if ((null != underFoot) && FlagsAspect.isSet(underFoot.getFlags(), FlagsAspect.FLAG_BURNING))
				{
					EntityChangeTakeDamageFromOther.applyDamageDirectlyAndPostEvent(context, newEntity, MiscConstants.FIRE_DAMAGE_PER_SECOND, EventRecord.Cause.BLOCK_DAMAGE);
				}
			}
		}
	}

	private static byte _calculateFallDamage(float deceleration)
	{
		byte damage;
		// Note that deceleration is measured as a negative value.
		if (deceleration <= DECELERATION_DAMAGE_THRESHOLD)
		{
			// Use this threshold and the terminal velocity to map this linearly onto the damage space.
			float hurtingDeceleration = deceleration - DECELERATION_DAMAGE_THRESHOLD;
			float fractionDamage = hurtingDeceleration / DECELERATION_DAMAGE_RANGE;
			damage = (byte)(DECELERATION_DAMAGE_MAX_FLOAT * fractionDamage);
		}
		else
		{
			// Harmless.
			damage = 0;
		}
		return damage;
	}

	private static BlockProxy _getHeadProxy(TickProcessingContext context, IMutableMinimalEntity newEntity)
	{
		EntityLocation footLocation = newEntity.getLocation();
		EntityVolume volume = newEntity.getType().volume();
		float halfWidth = volume.width() / 2.0f;
		// (we use the floor since that is the block address)
		AbsoluteLocation headLocation = new AbsoluteLocation((int)Math.floor(footLocation.x() + halfWidth)
				, (int)Math.floor(footLocation.y() + halfWidth)
				, (int)Math.floor(footLocation.z() + volume.height())
		);
		return context.previousBlockLookUp.apply(headLocation);
	}
}
