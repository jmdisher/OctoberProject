package com.jeffdisher.october.engine;

import java.util.List;

import com.jeffdisher.october.actions.passive.PassiveActionEveryTick;
import com.jeffdisher.october.types.IPassiveAction;
import com.jeffdisher.october.types.PassiveEntity;
import com.jeffdisher.october.types.TickProcessingContext;


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
			PassiveActionEveryTick action = new PassiveActionEveryTick();
			working = action.applyChange(context, working);
		}
		return working;
	}
}
