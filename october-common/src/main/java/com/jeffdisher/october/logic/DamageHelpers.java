package com.jeffdisher.october.logic;

import java.util.function.Function;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * A container for helpers related to applying damage to an entity.
 */
public class DamageHelpers
{
	/**
	 * Applies damage directly to the given newEntity.  Internally, this will either reduce their health or kill them,
	 * emitting the corresponding event.
	 * This is intended to be applied at the end of tick when processing environment damage factors applied to the
	 * entity.
	 * 
	 * @param context The current tick context.
	 * @param newEntity The entity to modify.
	 * @param damageToApply The damage to apply (must be > 0).
	 * @param cause The cause of the damage when emiting the event for the damage.
	 */
	public static void applyDamageDirectlyAndPostEvent(TickProcessingContext context, IMutableMinimalEntity newEntity, byte damageToApply, EventRecord.Cause cause)
	{
		int finalHealth = newEntity.getHealth() - damageToApply;
		if (finalHealth < 0)
		{
			finalHealth = 0;
		}
		AbsoluteLocation entityLocation = newEntity.getLocation().getBlockLocation();
		EventRecord.Type type;
		if (finalHealth > 0)
		{
			// We can apply the damage.
			newEntity.setHealth((byte)finalHealth);
			type = EventRecord.Type.ENTITY_HURT;
		}
		else
		{
			// The entity is dead so use the type-specific death logic.
			newEntity.handleEntityDeath(context);
			type = EventRecord.Type.ENTITY_KILLED;
		}
		
		context.eventSink.post(new EventRecord(type
				, cause
				, entityLocation
				, newEntity.getId()
				, 0
		));
	}

	/**
	 * Checks if the region of volume size, based at base, intersects any blocks which should do damage (either due to
	 * the block doing direct damage or being just above a burning block), and returns the maximum damage which would be
	 * applied to any of those blocks.
	 * 
	 * @param env The environment instance.
	 * @param previousBlockLookUp Look-up helper for the previous tick's block states.
	 * @param base The base of the region to check.
	 * @param volume The total volume of the region to check.
	 * @return The maximum damage of any blocks in this region (0 if none do damage).
	 */
	public static int findEnvironmentalDamageInVolume(Environment env, Function<AbsoluteLocation, BlockProxy> previousBlockLookUp, EntityLocation base, EntityVolume volume)
	{
		// We want to check if this is a block which can do direct harm or if it is on top of a burning block (since
		// those still do damage).
		int maxDamage = 0;
		for (AbsoluteLocation location : new VolumeIterator(base, volume))
		{
			BlockProxy thisBlock = previousBlockLookUp.apply(location);
			int blockDamage = (null != thisBlock)
				? env.blocks.getBlockDamage(thisBlock.getBlock())
				: 0
			;
			if (0 == blockDamage)
			{
				BlockProxy belowBlock = previousBlockLookUp.apply(location.getRelative(0, 0, -1));
				boolean isBurning = ((null != belowBlock) && FlagsAspect.isSet(belowBlock.getFlags(), FlagsAspect.FLAG_BURNING));
				blockDamage = isBurning
					? MiscConstants.FIRE_DAMAGE_PER_SECOND
					: 0
				;
			}
			maxDamage = Math.max(maxDamage, blockDamage);
		}
		return maxDamage;
	}
}
