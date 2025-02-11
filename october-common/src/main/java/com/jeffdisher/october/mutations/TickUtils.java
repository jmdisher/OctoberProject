package com.jeffdisher.october.mutations;

import java.util.function.Function;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.logic.EntityMovementHelpers;
import com.jeffdisher.october.logic.MotionHelpers;
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
	public static final float DECELERATION_DAMAGE_RANGE = MotionHelpers.FALLING_TERMINAL_VELOCITY_PER_SECOND - DECELERATION_DAMAGE_THRESHOLD;
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
			, IFallDamage damageApplication
			, IMutableMinimalEntity newEntity
			, long millisToMove
	)
	{
		EntityLocation oldLocation = newEntity.getLocation();
		EntityLocation oldVelocity = newEntity.getVelocityVector();
		EntityMovementHelpers.allowMovement(previousBlockLookUp, newEntity, millisToMove);
		boolean didApply = !oldLocation.equals(newEntity.getLocation());
		
		if (didApply)
		{
			// Do other state reset now that we are moving.
			newEntity.resetLongRunningOperations();
			
			// If we lost our z-vector, see if this deceleration should cause damage.
			if (0.0f == newEntity.getVelocityVector().z())
			{
				float loss = oldVelocity.z();
				byte damage = _calculateFallDamage(loss);
				if (damage > 0)
				{
					damageApplication.applyDamage(damage);
				}
			}
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
				_applyEndOfTickLocationChanges(context, newEntity);
			}
		}
	}


	private static void _applyEndOfTickLocationChanges(TickProcessingContext context, IMutableMinimalEntity newEntity)
	{
		// Note that we handle food/starvation in EntityChangePeriodic, since it is specific to player entities, but we handle breath/drowning here, since it is common.
		BlockProxy headProxy = _getHeadProxy(context, newEntity);
		if (null != headProxy)
		{
			Environment env = Environment.getShared();
			Block headBlock = headProxy.getBlock();
			
			// Check if we need to apply breath mechanics.
			boolean isBreathable = env.blocks.canBreatheInBlock(headBlock);
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


	public static interface IFallDamage
	{
		public void applyDamage(int damage);
	}
}
