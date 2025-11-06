package com.jeffdisher.october.engine;

import java.util.List;

import com.jeffdisher.october.actions.passive.PassiveSynth_ItemSlot;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.DamageHelpers;
import com.jeffdisher.october.mutations.TickUtils;
import com.jeffdisher.october.types.IPassiveAction;
import com.jeffdisher.october.types.PassiveEntity;
import com.jeffdisher.october.types.PassiveType;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Static engine logic related to processing passive entities in the world.
 */
public class EnginePassives
{
	private EnginePassives()
	{
		// This is just static logic.
	}

	/**
	 * Runs all elements in actionsToRun against the given passive, as well as the default passive actions, returning an
	 * updated instance (null if the instance was destroyed or despawned).
	 * 
	 * @param context The context used for running changes.
	 * @param passive The passive to process.
	 * @param actionsToRun A list of actions to run on this passive in this tick.
	 * @return An updated instance of the passive (potentially null).
	 */
	public static PassiveEntity processOneCreature(TickProcessingContext context
		, PassiveEntity passive
		, List<IPassiveAction> actionsToRun
	)
	{
		// Run all the actions, bearing in mind that any of them might cause the passive to despawn.
		PassiveEntity working = passive;
		for (IPassiveAction action : actionsToRun)
		{
			working = action.applyChange(context, working);
			if (null == working)
			{
				break;
			}
		}
		// Now, apply the default action.
		if (null != working)
		{
			PassiveType type = working.type();
			if (PassiveType.ITEM_SLOT == type)
			{
				working = PassiveSynth_ItemSlot.applyChange(context, working);
			}
			else
			{
				// Missing type.
				throw Assert.unreachable();
			}
		}
		// Finally, see if we need to check for environmental damage to force despawn.
		if ((null != working) && TickUtils.canApplyEnvironmentalDamageInTick(context))
		{
			Environment env = Environment.getShared();
			int blockDamage = DamageHelpers.findEnvironmentalDamageInVolume(env, context.previousBlockLookUp, passive.location(), passive.type().volume());
			if (blockDamage > 0)
			{
				// If the environment does any damage at all, despawn.
				working = null;
			}
		}
		return working;
	}
}
