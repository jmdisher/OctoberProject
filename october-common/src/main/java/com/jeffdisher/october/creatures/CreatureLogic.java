package com.jeffdisher.october.creatures;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.jeffdisher.october.actions.EntityActionSimpleMove;
import com.jeffdisher.october.actions.EntityActionSimpleMove.Intensity;
import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.logic.OrientationHelpers;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.logic.ViscosityReader;
import com.jeffdisher.october.pathing.PathFinder;
import com.jeffdisher.october.pathing.PathLocation;
import com.jeffdisher.october.pathing.WalkingNavigator;
import com.jeffdisher.october.subactions.EntitySubActionStepUp;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.FixedRegion;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.MinimalEntity;
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
		ViscosityReader reader = new ViscosityReader(Environment.getShared(), context.previousBlockLookUp);
		EntityType type = mutable.getType();
		EntityLocation currentLocation = mutable.getLocation();
		
		EntityActionSimpleMove<MutableCreature> action;
		if ((null != mutable.movementPlan) && (null != mutable.movementPlan.directLocation))
		{
			// We prioritize walking "directly" before following the conventional plan.
			CreatureEntity.MovementPlan oldPlan = mutable.movementPlan;
			EntityLocation targetLocation = oldPlan.directLocation;
			
			float viscosity = reader.getMaxStillViscosityInVolume(currentLocation, type.volume());
			
			byte yaw = OrientationHelpers.getYawBetweenPoints(currentLocation, targetLocation);
			action = CreatureMovementHelpers.moveAlongDiagonalPath(reader
				, currentLocation
				, yaw
				, (byte)0
				, type
				, targetLocation
				, timeLimitMillis
				, viscosity
				, oldPlan.intensity
			);
			
			if (null == action)
			{
				// This only returns null if we are stuck so clear the plan.
				// Ideally, we would clear this in the "special action" phase but this is only detected here.
				mutable.movementPlan = null;
			}
		}
		else if ((null != mutable.movementPlan) && (null != mutable.movementPlan.fullPlan))
		{
			// We can derive the action directly from this plan.
			CreatureEntity.MovementPlan oldPlan = mutable.movementPlan;
			List<PathLocation> existingPlan = oldPlan.fullPlan;
			Assert.assertTrue(!existingPlan.isEmpty());
			
			EntityActionSimpleMove.Intensity intensity = oldPlan.intensity;
			EntityLocation oldVelocity = mutable.getVelocityVector();
			
			float viscosity = reader.getMaxStillViscosityInVolume(currentLocation, type.volume());
			
			// We have a path so make sure that we start in a reasonable part of the block so we don't bump into something or fail to jump out of a hole.
			PathLocation currentInPath = PathLocation.forEntityBase(currentLocation);
			PathLocation first = existingPlan.get(0);
			PathLocation second = (existingPlan.size() >= 2) ? existingPlan.get(1) : null;
			FacingDirection direction;
			if ((existingPlan.size() > 2)
				&& (FacingDirection.UP == PathLocation.adjacentDirection(currentInPath, first))
				&& (FacingDirection.UP == PathLocation.adjacentDirection(first, existingPlan.get(1)))
			)
			{
				// This means we are jumping so choose the next place where we want to go for direction hint.
				PathLocation third = existingPlan.get(2);
				direction = PathLocation.adjacentDirection(second, third);
			}
			else
			{
				// Otherwise, we just want to lean into the direction we are currently heading.
				direction = PathLocation.adjacentDirection(currentInPath, first);
			}
			EntityActionSimpleMove<MutableCreature> actionProduced = CreatureMovementHelpers.prepareForMove(currentLocation
				, oldVelocity
				, type
				, direction
				, timeLimitMillis
				, viscosity
				, intensity
			);
			if (null == actionProduced)
			{
				// If we are already in a reasonable location, proceed to move.
				// We know that this should only be called AFTER updating our planned path.
				Assert.assertTrue(!PathLocation.forEntityBase(currentLocation).equals(first));
				
				boolean isSwimmable = (viscosity >= BlockAspect.VISOSITY_FLOAT_SWIMMABLE) && (viscosity < BlockAspect.VISOSITY_FLOAT_SOLID);
				actionProduced = CreatureMovementHelpers.moveToNextLocation(reader
					, currentLocation
					, oldVelocity
					, mutable.newYaw
					, mutable.newPitch
					, type
					, first
					, second
					, timeLimitMillis
					, viscosity
					, intensity
					, isSwimmable
				);
			}
			// TODO:  This approach is a hack which requires that we rethink this action production design.
			if ((null != actionProduced) && (actionProduced.getSubAction() instanceof EntitySubActionStepUp))
			{
				List<PathLocation> fullPlan = new ArrayList<>(existingPlan);
				fullPlan.remove(0);
				Assert.assertTrue(null == oldPlan.directLocation);
				if (CreatureEntity.NO_TARGET_ENTITY_ID != oldPlan.targetEntityId)
				{
					mutable.movementPlan = new PlanWithTarget(fullPlan
						, oldPlan.targetEntityId
						, oldPlan.targetPreviousLocation
					);
				}
				else
				{
					mutable.movementPlan = new PlanNoTarget(fullPlan, intensity);
				}
			}
			action = actionProduced;
		}
		else
		{
			// We have no plan so do nothing.
			action = null;
		}
		return action;
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
		
		// If we have a plan, we want to update it whenever we didn't take any special action.
		if (null != mutable.movementPlan)
		{
			// First, handle the invalidate cases.
			
			// Invalidate:  Verify that the target is valid.
			if ((null != mutable.movementPlan) && (CreatureEntity.NO_TARGET_ENTITY_ID != mutable.movementPlan.targetEntityId()))
			{
				boolean isTargetValid = type.extension().isTargetValid(mutable, entityCollection);
				if (!isTargetValid)
				{
					// The target is invalid so clear our state.
					mutable.movementPlan = null;
				}
			}
			
			// Invalidate:  Verify that we aren't stuck in a block.
			if ((null != mutable.movementPlan)
				&& ((null != mutable.movementPlan.directLocation) || (null != mutable.movementPlan.fullPlan))
			)
			{
				float viscosity = reader.getMaxStillViscosityInVolume(currentLocation, type.volume());
				
				if (BlockAspect.VISOSITY_FLOAT_SOLID == viscosity)
				{
					// This typically happens if a block drops on us or if a block was placed on us due to a race condition.
					// Clear the plan since we can't move.
					mutable.movementPlan = null;
				}
			}
			
			// Invalidate:  Verify that we are in range of the next step and it isn't solid.
			if ((null != mutable.movementPlan) && (null != mutable.movementPlan.fullPlan))
			{
				List<PathLocation> existingPlan = mutable.movementPlan.fullPlan;
				PathLocation thisStep = existingPlan.get(0);
				PathLocation pathBase = PathLocation.forEntityBase(currentLocation);
				
				// We might not yet have noticed that we have moved into the next step so skip this check in that case.
				if (!pathBase.equals(thisStep))
				{
					int distanceToNext = PathLocation.pathStepDistance(pathBase, thisStep);
					boolean isAdjacent = (1 == distanceToNext);
					if (isAdjacent)
					{
						// Just make sure we can move into this next step.
						float nextViscosity = reader.getMaxStillViscosityInVolume(thisStep.getBaseLocation(), type.volume());
						if (BlockAspect.VISOSITY_FLOAT_SOLID == nextViscosity)
						{
							// Something is blocked so clear the plan.
							mutable.movementPlan = null;
						}
						else
						{
							// Common case:  The path is still valid so just fall through and continue.
						}
					}
					else
					{
						// We must have fallen or been pushed so clear the plan.
						mutable.movementPlan = null;
					}
				}
			}
			
			// Handle the uncommon case where we are in direct mode but are no longer on the same z-level.
			if ((null != mutable.movementPlan)
				&& (null != mutable.movementPlan.directLocation)
				&& (currentLocation.getBlockLocation().z() != mutable.movementPlan.directLocation.getBlockLocation().z())
			)
			{
				mutable.movementPlan = null;
			}
			
			// --- The path is either clear and valid so check if it should be advanced ---
			
			// Check if the plan should be update in response to a target having moved.
			if ((null != mutable.movementPlan) && (CreatureEntity.NO_TARGET_ENTITY_ID != mutable.movementPlan.targetEntityId()))
			{
				MinimalEntity targetEntity = context.previousEntityLookUp.getById(mutable.movementPlan.targetEntityId());
				
				// The target validity was checked in the invalidate path so this is valid.
				Assert.assertTrue(null != targetEntity);
				
				if (!targetEntity.location().equals(mutable.movementPlan.targetPreviousLocation))
				{
					// This has moved so update the plan in response.
					EntityLocation targetLocation = targetEntity.location();
					// This has moved so update the plan in response.
					WalkingNavigator navigator = new WalkingNavigator(context.previousBlockLookUp
						, type.volume()
						, type.getPathDistance()
						, true
						, true
						, true
						, true
					);
					List<PathLocation> path = PathFinder.findPathWithLimit(navigator, currentLocation, targetLocation);
					if (null != path)
					{
						int targetEntityId = mutable.movementPlan.targetEntityId();
						// We want to strip away the first step, since it is the current location.
						path.remove(0);
						
						// We need to store a movement plan whether this path is reduced to nothing, or not.
						CreatureEntity.MovementPlan plan;
						if (path.size() > 0)
						{
							EntityLocation directLocation = CreatureMovementHelpers.findDirectTarget(reader, currentLocation, type, path);
							
							if (null != directLocation)
							{
								plan = new DirectWithDistantTarget(directLocation, targetEntityId, targetLocation);
							}
							else
							{
								plan = new PlanWithTarget(Collections.unmodifiableList(path), targetEntityId, targetLocation);
							}
						}
						else
						{
							plan = new DirectWithTarget(targetEntityId, targetLocation);
						}
						mutable.movementPlan = plan;
					}
					else
					{
						mutable.movementPlan = null;
					}
				}
			}
			
			// Check if the plan should be updated due to us stepping into the next block.
			if ((null != mutable.movementPlan) && (null != mutable.movementPlan.fullPlan))
			{
				List<PathLocation> existingPlan = mutable.movementPlan.fullPlan;
				PathLocation thisStep = existingPlan.get(0);
				PathLocation pathBase = PathLocation.forEntityBase(currentLocation);
				
				if (pathBase.equals(thisStep))
				{
					// We are in the next part of the plan so remove this step.
					if (existingPlan.size() > 1)
					{
						// Remove this one element and stay in the same plan.
						List<PathLocation> updatedPlan = new ArrayList<>(existingPlan);
						updatedPlan.remove(0);
						
						if (CreatureEntity.NO_TARGET_ENTITY_ID == mutable.movementPlan.targetEntityId())
						{
							List<PathLocation> steps = Collections.unmodifiableList(updatedPlan);
							Intensity intensity = EntityActionSimpleMove.Intensity.IDLE_CREATURE;
							EntityLocation directLocation = CreatureMovementHelpers.findDirectTarget(reader, currentLocation, type, steps);
							
							CreatureEntity.MovementPlan movementPlan;
							if (null != directLocation)
							{
								movementPlan = new DirectNoTarget(directLocation, intensity);
							}
							else
							{
								movementPlan = new PlanNoTarget(steps, intensity);
							}
							mutable.movementPlan = movementPlan;
						}
						else
						{
							int targetId = mutable.movementPlan.targetEntityId();
							EntityLocation targetLocation = mutable.movementPlan.targetPreviousLocation;
							List<PathLocation> updatedPlan1 = Collections.unmodifiableList(updatedPlan);
							EntityLocation directLocation = CreatureMovementHelpers.findDirectTarget(reader, currentLocation, type, updatedPlan1);
							
							CreatureEntity.MovementPlan movementPlan;
							if (null != directLocation)
							{
								movementPlan = new DirectWithDistantTarget(directLocation, targetId, targetLocation);
							}
							else
							{
								movementPlan = new PlanWithTarget(Collections.unmodifiableList(updatedPlan1), targetId, targetLocation);
							}
							mutable.movementPlan = movementPlan;
						}
					}
					else
					{
						// The movement plan is complete so determine if we enter a near target state or no plan.
						if (CreatureEntity.NO_TARGET_ENTITY_ID == mutable.movementPlan.targetEntityId())
						{
							// This means clear the plan since we must have been idling.
							mutable.movementPlan = null;
						}
						else
						{
							// We assume that they are in range as a near target.
							mutable.movementPlan = new DirectWithTarget(mutable.movementPlan.targetEntityId(), mutable.movementPlan.targetPreviousLocation);
						}
					}
				}
			}
			
			// Check if the plan should be updated due to proximity to the target.
			if ((null != mutable.movementPlan) && (null != mutable.movementPlan.directLocation))
			{
				// If we are targeting an entity, then we clear this once within action range, otherwise we clear when we match it.
				if (CreatureEntity.NO_TARGET_ENTITY_ID == mutable.movementPlan.targetEntityId())
				{
					if (currentLocation.equals(mutable.movementPlan.directLocation))
					{
						// We are at our destination, so clear.
						mutable.movementPlan = null;
					}
					else
					{
						// We are still walking there, so leave this unchanged.
					}
				}
				else
				{
					// We have a target so see if they are within action range.
					MinimalEntity targetEntity = context.previousEntityLookUp.getById(mutable.movementPlan.targetEntityId());
					
					// Note that we already checked that this target is valid in an earlier check.
					Assert.assertTrue(null != targetEntity);
					
					EntityLocation sourceEyeLocation = SpatialHelpers.getEyeLocation(currentLocation, type.volume());
					FixedRegion region = FixedRegion.fromMinimal(targetEntity);
					float distance = SpatialHelpers.distanceFromLocationToRegion(sourceEyeLocation, region);
					float actionDistance = type.actionDistance();
					
					if (distance < actionDistance)
					{
						// We can drop the direct location and just keep them in-range.
						mutable.movementPlan = new NearToTarget(mutable.movementPlan.targetEntityId(), targetEntity.location());
					}
					else
					{
						// We are still approaching them so change nothing.
					}
				}
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
