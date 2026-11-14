package com.jeffdisher.october.creatures;

import java.util.Collections;
import java.util.List;

import com.jeffdisher.october.actions.EntityActionSimpleMove;
import com.jeffdisher.october.actions.EntityActionSimpleMove.Intensity;
import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.logic.ViscosityReader;
import com.jeffdisher.october.pathing.PathFinder;
import com.jeffdisher.october.pathing.PathLocation;
import com.jeffdisher.october.pathing.WalkingNavigator;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.MutableCreature;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Helpers and data types used to implement creature behaviour.  In the future, this might move (especially if we end up
 * changing this to move of an OO approach).
 */
public class CreatureLogic
{
	/**
	 * Called within a mutation to apply an item to a creature.  This may change their state or not.
	 * 
	 * @param itemType The item type to apply (it will be consumed, either way).
	 * @param creature The creature to change.
	 * @param gameTimeMillis The current game time, in milliseconds.
	 * @return True if the creature state changed or false if it had no effect.
	 */
	public static boolean applyItemToCreature(Item itemType, MutableCreature creature, long gameTimeMillis)
	{
		EntityType creatureType = creature.getType();
		return creatureType.extension().applyItemToCreature(creature, itemType, gameTimeMillis);
	}

	/**
	 * Called multiple times per tick to use up time in movement.
	 * If there is no plan or the next action in the plan would take too long, it should return null.
	 * Internally, it shouldn't try to build a new plan in this call.
	 * 
	 * @param context The context of the current tick.
	 * @param mutable The mutable creature object currently being evaluated.
	 * @param timeLimitMillis The number of milliseconds left in the tick.
	 * @return The next action to take (null if there is nothing to do).
	 */
	public static EntityActionSimpleMove<MutableCreature> planNextAction(TickProcessingContext context
			, MutableCreature mutable
			, long timeLimitMillis
	)
	{
		return (null != mutable.movementPlan)
			? mutable.movementPlan.planNextAction(context, mutable, timeLimitMillis)
			: null
		;
	}

	/**
	 * Requests that the given creature be set pregnant.
	 * 
	 * @param creature The creature.
	 * @param sireLocation The location of the sire of the new spawn (the "father").
	 * @param gameTimeMillis The current game time, in milliseconds.
	 * @return True if the entity became pregnant.
	 */
	public static boolean setCreaturePregnant(MutableCreature creature, EntityLocation sireLocation, long gameTimeMillis)
	{
		EntityType creatureType = creature.getType();
		return creatureType.extension().setCreaturePregnant(creature, sireLocation, gameTimeMillis);
	}

	/**
	 * Called by CreatureProcessor on each creature in each tick, after any externally-originating actions have been run
	 * but before any movement/standing-related actions are synthesized and run to allow the creature to update its
	 * path-finding and goals.
	 * 
	 * @param context The context of the current tick.
	 * @param entityCollection The read-only collection of entities in the world.
	 * @param mutable The mutable creature object currently being evaluated.
	 */
	public static void createOrUpdatePathFinding(TickProcessingContext context
		, EntityCollection entityCollection
		, MutableCreature mutable
	)
	{
		ViscosityReader reader = new ViscosityReader(Environment.getShared(), context.previousBlockLookUp);
		EntityType type = mutable.getType();
		EntityLocation currentLocation = mutable.getLocation();
		
		// First, we will check if we should invalidate the plan based on common information and checks.
		
		// If we are stuck in a solid block, drop the plan.
		if (null != mutable.movementPlan)
		{
			float viscosity = reader.getMaxStillViscosityInVolume(currentLocation, type.volume());
			
			if (BlockAspect.VISOSITY_FLOAT_SOLID == viscosity)
			{
				// This typically happens if a block drops on us or if a block was placed on us due to a race condition.
				// Clear the plan since we can't move.
				mutable.movementPlan = null;
			}
		}
		
		// If the target is no longer valid, drop the plan.
		if ((null != mutable.movementPlan) && (CreatureEntity.NO_TARGET_ENTITY_ID != mutable.movementPlan.targetEntityId()))
		{
			boolean isTargetValid = type.extension().isTargetValid(mutable, entityCollection);
			if (!isTargetValid)
			{
				// The target is invalid so clear our state.
				mutable.movementPlan = null;
			}
		}
		
		// If we have a plan, we want to update it whenever we didn't take any special action.
		if (null != mutable.movementPlan)
		{
			// This can change instances while we progress so we loop while we transition through the various states.
			CreatureEntity.MovementPlan start = null;
			while ((null != mutable.movementPlan) && (start != mutable.movementPlan))
			{
				start = mutable.movementPlan;
				mutable.movementPlan.updateMovementPlan(context, entityCollection, mutable);
			}
		}
		
		// If we have no plan, try to create a new one.
		if (null == mutable.movementPlan)
		{
			// We have no plan so make a new one.
			boolean canMakeAction = mutable.shouldTakeActionInTick || (context.currentTickTimeMillis >= mutable.nextMovementPlanMillis);
			if (canMakeAction)
			{
				// We first check to see if we should be running away.
				if (null != mutable.lastInjuryVector)
				{
					float injuryX = mutable.lastInjuryVector.x();
					float injuryY = mutable.lastInjuryVector.y();
					mutable.movementPlan = _createFleeingPlan(context, reader, type, currentLocation, injuryX, injuryY);
					mutable.lastInjuryVector = null;
				}
				
				// See if there is some deliberate plan to take.
				if (null == mutable.movementPlan)
				{
					EntityType.TargetEntity newTarget = type.extension().findDeliberateTarget(mutable, entityCollection);
					if (null != newTarget)
					{
						CreatureEntity.MovementPlan movementPlan = _createDeliberatePlanToTarget(context, reader, type, currentLocation, newTarget);
						
						if (null != movementPlan)
						{
							// Even if the path was empty, this counts as a decision and reset despawn timeout.
							mutable.despawnMillis = context.currentTickTimeMillis + CreatureEntity.MILLIS_UNTIL_NO_ACTION_DESPAWN;
						}
						mutable.movementPlan = movementPlan;
					}
				}
				
				// See if there is some idle action we can take.
				if (null == mutable.movementPlan)
				{
					byte breath = mutable.newBreath;
					mutable.movementPlan = _createIdlePlan(context, reader, type, currentLocation, breath);
				}
				
				// We at least tried to take an action so reset our timers.
				mutable.nextMovementPlanMillis = context.currentTickTimeMillis + CreatureMovementHelpers.MINIMUM_MILLIS_TO_ACTION;
				mutable.shouldTakeActionInTick = false;
			}
		}
	}

	/**
	 * Called by CreatureProcessor on each creature in each tick, after any externally-originating actions have been run
	 * but before any movement/standing-related actions are synthesized and run to allow the creature to take special
	 * actions.
	 * This includes despawning if hostile in a peaceful world or if the mob is a despawning type and is idle.
	 * 
	 * @param context The context of the current tick.
	 * @param entityCollection The read-only collection of entities in the world.
	 * @param mutable The mutable creature object currently being evaluated.
	 * @return True if some special action was taken, meaning that this tick's actions should be skipped for this
	 * creature.
	 */
	public static boolean didTakeSpecialActions(TickProcessingContext context
			, EntityCollection entityCollection
			, MutableCreature mutable
	)
	{
		EntityType creatureType = mutable.getType();
		
		// We will only consider special actions or despawning if we are no longer on cooldown.
		boolean isDone = false;
		if (mutable.nextActionMillis <= context.currentTickTimeMillis)
		{
			EntityType.IExtension extension = creatureType.extension();
			if (extension.shouldDespawn(mutable, context))
			{
				// This counts as being "done" since we should skip any walking.
				mutable.setHealth((byte)0);
				isDone = true;
			}
			else
			{
				isDone = extension.didTakeSpecialAction(mutable, context, entityCollection);
				
				// Whether we did something or not, we want to go onto cooldown.
				mutable.nextActionMillis = context.currentTickTimeMillis + creatureType.actionCooldownMillis();
			}
		}
		
		return isDone;
	}


	private static CreatureEntity.MovementPlan _createFleeingPlan(TickProcessingContext context
		, ViscosityReader reader
		, EntityType type
		, EntityLocation currentLocation
		, float injuryX
		, float injuryY
	)
	{
		boolean allowWest = (injuryX <= 0.0f);
		boolean allowEast = (injuryX >= 0.0f);
		boolean allowSouth = (injuryY <= 0.0f);
		boolean allowNorth = (injuryY >= 0.0f);
		
		WalkingNavigator navigator = new WalkingNavigator(context.previousBlockLookUp
			, type.volume()
			, CreatureMovementHelpers.RANDOM_MOVEMENT_DISTANCE
			, allowWest
			, allowEast
			, allowSouth
			, allowNorth
		);
		Intensity intensity = EntityActionSimpleMove.Intensity.RUNNING;
		List<PathLocation> steps = CreatureMovementHelpers.findPathToRandomSpot(context
			, navigator
			, currentLocation
		);
		
		CreatureEntity.MovementPlan movementPlan = null;
		if (null != steps)
		{
			// We can't plan an empty path.
			Assert.assertTrue(!steps.isEmpty());
			EntityLocation directLocation = CreatureMovementHelpers.findDirectTarget(reader, currentLocation, type, steps);
			
			if (null != directLocation)
			{
				movementPlan = new DirectNoTarget(directLocation, intensity);
			}
			else
			{
				movementPlan = new PlanNoTarget(steps, intensity);
			}
		}
		return movementPlan;
	}

	private static CreatureEntity.MovementPlan _createDeliberatePlanToTarget(TickProcessingContext context
		, ViscosityReader reader
		, EntityType type
		, EntityLocation currentLocation
		, EntityType.TargetEntity newTarget
	)
	{
		// We want to verify that we have a path before we update our state.
		// We have a target so try to build a path (we will use double the distance for pathing overhead).
		// If this fails, it will return null which is already our failure case.
		WalkingNavigator navigator = new WalkingNavigator(context.previousBlockLookUp
			, type.volume()
			, type.getPathDistance()
			, true
			, true
			, true
			, true
		);
		EntityLocation targetLocation = newTarget.location();
		List<PathLocation> path = PathFinder.findPathWithLimit(navigator, currentLocation, targetLocation);
		
		CreatureEntity.MovementPlan movementPlan = null;
		if (null != path)
		{
			int targetEntityId = newTarget.id();
			// We want to strip away the first step, since it is the current location.
			path.remove(0);
			
			// We need to store a movement plan whether this path is reduced to nothing, or not.
			if (path.size() > 0)
			{
				EntityLocation directLocation = CreatureMovementHelpers.findDirectTarget(reader, currentLocation, type, path);
				
				if (null != directLocation)
				{
					movementPlan = new DirectWithDistantTarget(directLocation, targetEntityId, targetLocation);
				}
				else
				{
					movementPlan = new PlanWithTarget(Collections.unmodifiableList(path), targetEntityId, targetLocation);
				}
			}
			else
			{
				movementPlan = new DirectWithTarget(targetEntityId, targetLocation);
			}
		}
		return movementPlan;
	}

	private static CreatureEntity.MovementPlan _createIdlePlan(TickProcessingContext context
		, ViscosityReader reader
		, EntityType type
		, EntityLocation currentLocation
		, byte breath
	)
	{
		CreatureEntity.MovementPlan movementPlan = null;
		
		// If we don't have anything deliberate to do, we will just do some random "idle" movement but this is
		// somewhat expensive so only do it if we have been waiting a while or if we are in danger (since the random
		// movements are "safe").
		boolean isInDanger = (breath < MiscConstants.MAX_BREATH);
		// We use "1" here just because it makes some testing simpler but any number < IDLE_ACTION_DENOMINATOR would work.
		boolean canMakeIdleAction = (1 == context.randomInt.applyAsInt(CreatureMovementHelpers.IDLE_ACTION_DENOMINATOR));
		if (isInDanger || canMakeIdleAction)
		{
			WalkingNavigator navigator = new WalkingNavigator(context.previousBlockLookUp
				, type.volume()
				, CreatureMovementHelpers.RANDOM_MOVEMENT_DISTANCE
				, true
				, true
				, true
				, true
			);
			Intensity intensity = EntityActionSimpleMove.Intensity.IDLE_CREATURE;
			List<PathLocation> steps = CreatureMovementHelpers.findPathToRandomSpot(context
				, navigator
				, currentLocation
			);
			
			if (null != steps)
			{
				// We can't plan an empty path.
				Assert.assertTrue(!steps.isEmpty());
				EntityLocation directLocation = CreatureMovementHelpers.findDirectTarget(reader, currentLocation, type, steps);
				
				if (null != directLocation)
				{
					movementPlan = new DirectNoTarget(directLocation, intensity);
				}
				else
				{
					movementPlan = new PlanNoTarget(steps, intensity);
				}
			}
		}
		return movementPlan;
	}
}
