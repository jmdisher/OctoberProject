package com.jeffdisher.october.mutations;

import java.util.function.Function;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.logic.EntityMovementHelpers;
import com.jeffdisher.october.logic.MotionHelpers;
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
	/**
	 * We will base our damage threshold on the terminal velocity.
	 */
	public static final float DECELERATION_DAMAGE_THRESHOLD = MotionHelpers.FALLING_TERMINAL_VELOCITY_PER_SECOND / 4.0f;
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
			, IDamageApplication damageApplication
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
			long ticksPerSecond = (1_000L / context.millisPerTick);
			boolean isBeginningOfSecond = (0 == (context.currentTick % ticksPerSecond));
			if (isBeginningOfSecond)
			{
				_applyBreathMechanics(context, newEntity);
			}
		}
	}


	private static void _applyBreathMechanics(TickProcessingContext context, IMutableMinimalEntity newEntity)
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
				byte breath = newEntity.getBreath();
				if (breath > 0)
				{
					breath -= EntityConstants.SUFFOCATION_BREATH_PER_SECOND;
					if (breath < 0)
					{
						breath = 0;
					}
					newEntity.setBreath(breath);
				}
				else
				{
					// The damage isn't applied to a specific body part.
					int id = newEntity.getId();
					if (id > 0)
					{
						EntityChangeTakeDamage<IMutablePlayerEntity> takeDamage = new EntityChangeTakeDamage<>(null, (byte)EntityConstants.SUFFOCATION_DAMAGE_PER_SECOND);
						context.newChangeSink.next(id, takeDamage);
					}
					else
					{
						EntityChangeTakeDamage<IMutableCreatureEntity> takeDamage = new EntityChangeTakeDamage<>(null, (byte)EntityConstants.SUFFOCATION_DAMAGE_PER_SECOND);
						context.newChangeSink.creature(id, takeDamage);
					}
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


	public static interface IDamageApplication
	{
		public void applyDamage(byte damage);
	}
}
