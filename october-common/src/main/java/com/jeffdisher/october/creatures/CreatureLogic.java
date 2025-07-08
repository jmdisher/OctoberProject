package com.jeffdisher.october.creatures;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.logic.CreatureMovementHelpers;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.logic.PathFinder;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.logic.ViscosityReader;
import com.jeffdisher.october.mutations.EntityChangeImpregnateCreature;
import com.jeffdisher.october.mutations.EntityChangeTakeDamageFromEntity;
import com.jeffdisher.october.mutations.EntityChangeTopLevelMovement;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.Difficulty;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.IMutableCreatureEntity;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
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
	public static final float RANDOM_MOVEMENT_DISTANCE = 3.5f;
	/**
	 * The minimum number of millis we can wait from our last action until we decide to make a deliberate plan.
	 */
	public static final long MINIMUM_MILLIS_TO_ACTION = 1_000L;
	/**
	 * The chance of making an idle action instead, of we fail to make a deliberate action when acting.
	 */
	public static final int IDLE_ACTION_DENOMINATOR = 30;
	/**
	 * The amount of time a hostile mob will continue to live if not taking any deliberate action before despawn (5
	 * minutes).
	 */
	public static final long MILLIS_UNTIL_NO_ACTION_DESPAWN = 5L * 60L * 1_000L;
	/**
	 * A creature will wait one second between attacks.
	 */
	public static final long MILLIS_ATTACK_COOLDOWN = 1000L;


	/**
	 * A helper to determine if the given item can be used on a specific entity type with this entity mutation.
	 * 
	 * @param item The item.
	 * @param entityType The target entity type.
	 * @return True if this mutation can be used to apply the item to the entity.
	 */
	public static boolean canUseOnEntity(Item item, EntityType entityType)
	{
		// We only worry about use of item for breeding (at least for now).
		return (item == entityType.breedingItem());
	}

	/**
	 * Called within a mutation to apply an item to a creature.  This may change their state or not.
	 * 
	 * @param itemType The item type to apply (it will be consumed, either way).
	 * @param creature The creature to change.
	 * @return True if the creature state changed or false if it had no effect.
	 */
	public static boolean applyItemToCreature(Item itemType, IMutableCreatureEntity creature)
	{
		boolean didApply = false;
		EntityType creatureType = creature.getType();
		// The only item application case which currently exists is breeding items so make sure that is the case.
		// Don't redundantly enter love mode.
		// We can't enter love mode if already pregnant (although that would only remain the case for a single tick).
		if (!creature.isInLoveMode() && (null == creature.getOffspringLocation()) && (creatureType.breedingItem() == itemType))
		{
			// If we applied this, put us into love mode and clear other plans.
			creature.setLoveMode(true);
			creature.setMovementPlan(null);
			creature.setReadyForAction();
			didApply = true;
		}
		return didApply;
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
	public static EntityChangeTopLevelMovement<IMutableCreatureEntity> planNextAction(TickProcessingContext context
			, MutableCreature mutable
			, long timeLimitMillis
	)
	{
		EntityChangeTopLevelMovement<IMutableCreatureEntity> action;
		if (null != mutable.newMovementPlan)
		{
			// If we have a movement plan, we want to try to advance it and then produce the next action.
			Function<AbsoluteLocation, PathFinder.BlockKind> blockKindLookup = _createLookupHelper(context);
			_advanceMovementPlan(blockKindLookup, mutable);
			// We never want to leave an empty movement plan so we expect that has been addressed before we got here.
			Assert.assertTrue((null == mutable.newMovementPlan) || !mutable.newMovementPlan.isEmpty());
			action = (null != mutable.newMovementPlan)
					? _produceNextAction(context, blockKindLookup, mutable, mutable.newMovementPlan, timeLimitMillis)
					: null
			;
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
	 * @return True if the entity became pregnant.
	 */
	public static boolean setCreaturePregnant(IMutableCreatureEntity creature, EntityLocation sireLocation)
	{
		boolean didBecomePregnant = false;
		EntityType creatureType = creature.getType();
		// We can only attempt set the pregnant state if this creature is in love mode.
		if (null != creatureType.breedingItem())
		{
			// Average the locations.
			EntityLocation parentLocation = creature.getLocation();
			EntityLocation spawnLocation = new EntityLocation((sireLocation.x() + parentLocation.x()) / 2.0f
					, (sireLocation.y() + parentLocation.y()) / 2.0f
					, (sireLocation.z() + parentLocation.z()) / 2.0f
			);
			// Clear the love mode, set the spawn location, and clear existing plans.
			creature.setLoveMode(false);
			creature.setOffspringLocation(spawnLocation);
			creature.setMovementPlan(null);
			creature.setReadyForAction();
			didBecomePregnant = true;
		}
		return didBecomePregnant;
	}

	/**
	 * Called by CreatureProcessor at the beginning of each tick for each creature so that they can take special
	 * actions.
	 * This includes despawning if hostile in a peaceful world or if the mob is a despawning type and is idle.
	 * Normally, however, it involves finding a target and/or creating a movement plan.
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
		boolean isDone;
		if (_didDespawn(context, mutable))
		{
			// This counts as being "done" since we should skip any walking.
			isDone = true;
		}
		else
		{
			EntityType creatureType = mutable.getType();
			// We want to update our state so check the relevant variables.
			if (CreatureEntity.NO_TARGET_ENTITY_ID != mutable.newTargetEntityId)
			{
				// We have some target so see if they are still valid and update our path to them.
				boolean isTargetValid = _isTargetValid(entityCollection, mutable);
				
				if (isTargetValid)
				{
					// The target is valid so we want to see if we should update our plan of just drop it, if close enough.
					_updateValidPathIfTargetMoved(context, mutable);
				}
				else
				{
					// The target is invalid so clear our state.
					_clearTargetAndPlan(mutable);
					mutable.setReadyForAction();
				}
				// If there is no plan, just skip movement.
				isDone = (null == mutable.newMovementPlan);
			}
			else if (null == mutable.newMovementPlan)
			{
				// We have no plan so make a new one.
				Function<AbsoluteLocation, PathFinder.BlockKind> blockKindLookup = _createLookupHelper(context);
				_makeMovementPlan(context, blockKindLookup, entityCollection, mutable);
			}
			
			if (creatureType.isLivestock())
			{
				isDone = _didTakeLivestockAction(context, mutable);
			}
			else
			{
				// We assume that anything is either livestock or hostile.
				Assert.assertTrue(creatureType.isHostile());
				isDone = _didTakeHostileAction(context, mutable);
			}
		}
		return isDone;
	}

	/**
	 * Just a testing entry-point for the random target path selection (idle movement).
	 * 
	 * @param context The testing context.
	 * @param location The creature's location.
	 * @param type The creature's type.
	 * @return A path to a randomly selected target.
	 */
	public static List<AbsoluteLocation> test_findPathToRandomSpot(TickProcessingContext context, EntityLocation location, EntityType type)
	{
		Function<AbsoluteLocation, PathFinder.BlockKind> blockKindLookup = _createLookupHelper(context);
		return _findPathToRandomSpot(context
				, blockKindLookup
				, location
				, type
		);
	}


	private static void _makeMovementPlan(TickProcessingContext context
			, Function<AbsoluteLocation, PathFinder.BlockKind> blockKindLookup
			, EntityCollection entityCollection
			, MutableCreature mutable
	)
	{
		// We will first see if they can make a deliberate plan.
		long nextDeliberateActionTick = mutable.newLastActionTick + _ticksBetweenActions(context.millisPerTick);
		boolean canMakeAction = mutable.newShouldTakeAction || ( context.currentTick >= nextDeliberateActionTick);
		List<AbsoluteLocation> movementPlan;
		if (canMakeAction)
		{
			movementPlan = _buildDeliberatePath(context
					, blockKindLookup
					, entityCollection
					, mutable
			);
			if (null == movementPlan)
			{
				// If we don't have anything deliberate to do, we will just do some random "idle" movement but this is
				// somewhat expensive so only do it if we have been waiting a while or if we are in danger (since the random
				// movements are "safe").
				boolean isInDanger = (mutable.newBreath < MiscConstants.MAX_BREATH);
				// We use "1" here just because it makes some testing simpler but any number < IDLE_ACTION_DENOMINATOR would work.
				boolean canMakeIdleAction = (1 == context.randomInt.applyAsInt(IDLE_ACTION_DENOMINATOR));
				if (isInDanger || canMakeIdleAction)
				{
					movementPlan = _findPathToRandomSpot(context
							, blockKindLookup
							, mutable.getLocation()
							, mutable.getType()
					);
					// We can't plan an empty path.
					Assert.assertTrue((null == movementPlan) || !movementPlan.isEmpty());
				}
			}
			else if (movementPlan.isEmpty())
			{
				// This can return an empty array just so we know a decision was made but we want to null it.
				movementPlan = null;
			}
			mutable.newLastActionTick = context.currentTick;
			mutable.newShouldTakeAction = false;
		}
		else
		{
			movementPlan = null;
		}
		mutable.newMovementPlan = movementPlan;
	}

	private static EntityChangeTopLevelMovement<IMutableCreatureEntity> _produceNextAction(TickProcessingContext context
			, Function<AbsoluteLocation, PathFinder.BlockKind> blockKindLookup
			, MutableCreature mutable
			, List<AbsoluteLocation> existingPlan
			, long timeLimitMillis
	)
	{
		Assert.assertTrue(!existingPlan.isEmpty());
		
		float viscosity = new ViscosityReader(Environment.getShared(), context.previousBlockLookUp).getViscosityFraction(mutable.newLocation.getBlockLocation());
		boolean isIdleMovement = (CreatureEntity.NO_TARGET_ENTITY_ID == mutable.newTargetEntityId);
		
		// We have a path so make sure that we start in a reasonable part of the block so we don't bump into something or fail to jump out of a hole.
		AbsoluteLocation directionHint = existingPlan.get(0);
		if ((existingPlan.size() > 1) && (directionHint.z() > Math.floor(mutable.getLocation().z())))
		{
			// This means we are jumping so choose the next place where we want to go for direction hint.
			directionHint = existingPlan.get(1);
		}
		ViscosityReader reader = new ViscosityReader(Environment.getShared(), context.previousBlockLookUp);
		EntityChangeTopLevelMovement<IMutableCreatureEntity> actionProduced = CreatureMovementHelpers.prepareForMove(reader, mutable.getLocation(), mutable.getVelocityVector(), mutable.getType(), directionHint, timeLimitMillis, viscosity, isIdleMovement);
		if (null == actionProduced)
		{
			// If we are already in a reasonable location, proceed to move.
			actionProduced = _planNextStep(blockKindLookup, reader, mutable.getLocation(), mutable.getVelocityVector(), mutable.newYaw, mutable.newPitch, mutable.getType(), existingPlan, timeLimitMillis, viscosity, isIdleMovement);
		}
		
		return actionProduced;
	}

	// NOTE:  This will return an empty path if it made a decision but the decision has no steps.
	private static List<AbsoluteLocation> _buildDeliberatePath(TickProcessingContext context
			, Function<AbsoluteLocation, PathFinder.BlockKind> blockPermitsPassage
			, EntityCollection entityCollection
			, MutableCreature mutable
	)
	{
		EntityType type = mutable.getType();
		
		_TargetEntity newTarget;
		if (type.isLivestock())
		{
			// This is livestock so choose our target based on whether we are looking for a partner or food.
			if (mutable.isInLoveMode())
			{
				// Find another of this type in breeding mode.
				newTarget = _findBreedable(entityCollection, mutable);
			}
			else
			{
				// We will keep this simple:  Find the closest player holding our breeding item, up to our limit.
				newTarget = _findFeedingTarget(entityCollection, mutable);
			}
		}
		else
		{
			// This is hostile so just search for a player.
			newTarget = _findPlayerInRange(entityCollection, mutable);
		}
		
		// Determine the path if we found a target.
		List<AbsoluteLocation> path = null;
		if (null != newTarget)
		{
			// We want to verify that we have a path before we update our state.
			// We have a target so try to build a path (we will use double the distance for pathing overhead).
			EntityLocation targetLocation = newTarget.location();
			// If this fails, it will return null which is already our failure case.
			EntityVolume volume = type.volume();
			EntityLocation creatureLocation = mutable.getLocation();
			path = PathFinder.findPathWithLimit(blockPermitsPassage, volume, creatureLocation, targetLocation, type.getPathDistance());
			if (null != path)
			{
				// The path was valid so set our target.
				mutable.newTargetEntityId = newTarget.id();
				mutable.newTargetPreviousLocation = targetLocation.getBlockLocation();
				// We want to strip away the first step, since it is the current location.
				path.remove(0);
				// (we will still return an empty path just to communicate that we made a decision.
				// As long as we found a new deliberate target, reset our despawn timeout.
				mutable.newDespawnKeepAliveTick = context.currentTick;
			}
		}
		return path;
	}

	private static List<AbsoluteLocation> _findPathToRandomSpot(TickProcessingContext context
			, Function<AbsoluteLocation, PathFinder.BlockKind> blockPermitsUser
			, EntityLocation creatureLocation
			, EntityType type
	)
	{
		EntityVolume volume = type.volume();
		float limitSteps = RANDOM_MOVEMENT_DISTANCE;
		Map<AbsoluteLocation, AbsoluteLocation> possiblePaths = PathFinder.findPlacesWithinLimit(blockPermitsUser, volume, creatureLocation, limitSteps);
		
		// Strip out any of the ending positions which we don't want.
		List<AbsoluteLocation> goodTargets = _extractAcceptablePathTargets(blockPermitsUser, possiblePaths, creatureLocation.getBlockLocation());
		
		// Just pick one of these destinations at random, or default to standing still.
		int size = goodTargets.size();
		List<AbsoluteLocation> plannedPath;
		if (size > 0)
		{
			int selection = context.randomInt.applyAsInt(size);
			// Skip over this many options (we can't really index into this and choosing the "first" would give a hash-order preference).
			AbsoluteLocation target = goodTargets.get(selection);
			
			// We can now build the plan - note that we build this in reverse since the map is back-pointers.
			plannedPath = new ArrayList<>();
			while (null != target)
			{
				plannedPath.add(0, target);
				target = possiblePaths.get(target);
			}
			// We should never see a path ending where we started.
			Assert.assertTrue(plannedPath.size() > 0);
			// We want to strip away the first step, since it is the current location.
			plannedPath.remove(0);
			// If the path ends up being empty, don't choose it.
			if (plannedPath.isEmpty())
			{
				plannedPath = null;
			}
		}
		else
		{
			plannedPath = null;
		}
		return plannedPath;
	}

	// Note:  This will update mutable.newMovementPlan
	private static void _advanceMovementPlan(Function<AbsoluteLocation, PathFinder.BlockKind> blockKindLookup
			, MutableCreature mutable
	)
	{
		List<AbsoluteLocation> existingPlan = mutable.newMovementPlan;
		AbsoluteLocation thisStep = existingPlan.get(0);
		EntityLocation entityLocation = mutable.getLocation();
		AbsoluteLocation currentLocation = entityLocation.getBlockLocation();
		
		List<AbsoluteLocation> updatedPlan;
		// First, check to see if we are already in our next location.
		if (currentLocation.equals(thisStep))
		{
			// If we are, that means that we can remove this from the path and plan to move to the next step.
			if (existingPlan.size() > 1)
			{
				updatedPlan = new ArrayList<>(existingPlan);
				updatedPlan.remove(0);
			}
			else
			{
				updatedPlan = null;
			}
		}
		else
		{
			// We just need to see if the plan is still valid.  This means that the next step must be adjacent and both our current and next locations can be entered.
			int distanceToNext = Math.abs(currentLocation.x() - thisStep.x())
					+ Math.abs(currentLocation.y() - thisStep.y())
					+ Math.abs(currentLocation.z() - thisStep.z())
			;
			boolean isAdjacent = (1 == distanceToNext);
			if (isAdjacent)
			{
				// Just make sure we can move in both of these.
				PathFinder.BlockKind currentKind = blockKindLookup.apply(currentLocation);
				PathFinder.BlockKind nextKind = blockKindLookup.apply(thisStep);
				if ((PathFinder.BlockKind.SOLID == currentKind) || (PathFinder.BlockKind.SOLID == nextKind))
				{
					// Something is blocked so clear the plan.
					updatedPlan = null;
				}
				else
				{
					// We can continue along this path.
					updatedPlan = existingPlan;
				}
			}
			else
			{
				// We must have fallen or been pushed so clear the plan.
				updatedPlan = null;
			}
		}
		mutable.newMovementPlan = updatedPlan;
	}

	private static EntityChangeTopLevelMovement<IMutableCreatureEntity> _planNextStep(Function<AbsoluteLocation, PathFinder.BlockKind> blockKindLookup
			, ViscosityReader reader
			, EntityLocation entityLocation
			, EntityLocation entityVelocity
			, byte yaw
			, byte pitch
			, EntityType type
			, List<AbsoluteLocation> existingPlan
			, long timeLimitMillis
			, float viscosity
			, boolean isIdleMovement
	)
	{
		AbsoluteLocation thisStep = existingPlan.get(0);
		AbsoluteLocation currentLocation = entityLocation.getBlockLocation();
		
		// We know that this should only be called AFTER updating our planned path.
		Assert.assertTrue(!currentLocation.equals(thisStep));
		
		boolean isSwimmable = (PathFinder.BlockKind.SWIMMABLE == blockKindLookup.apply(currentLocation));
		return CreatureMovementHelpers.moveToNextLocation(reader, entityLocation, entityVelocity, yaw, pitch, type, thisStep, timeLimitMillis, viscosity, isIdleMovement, isSwimmable);
	}

	private static Function<AbsoluteLocation, PathFinder.BlockKind> _createLookupHelper(TickProcessingContext context)
	{
		Environment environment = Environment.getShared();
		Function<AbsoluteLocation, PathFinder.BlockKind> blockKind = (AbsoluteLocation location) -> {
			BlockProxy proxy = context.previousBlockLookUp.apply(location);
			PathFinder.BlockKind kind;
			if (null == proxy)
			{
				// If we can't find the proxy, we will treat this as solid.
				kind = PathFinder.BlockKind.SOLID;
			}
			else
			{
				Block block = proxy.getBlock();
				boolean isActive = FlagsAspect.isSet(proxy.getFlags(), FlagsAspect.FLAG_ACTIVE);
				if (environment.blocks.isSolid(block, isActive))
				{
					kind = PathFinder.BlockKind.SOLID;
				}
				else if (environment.blocks.canSwimInBlock(block, isActive))
				{
					kind = PathFinder.BlockKind.SWIMMABLE;
				}
				else
				{
					kind = PathFinder.BlockKind.WALKABLE;
				}
			}
			return kind;
		};
		return blockKind;
	}

	private static List<AbsoluteLocation> _extractAcceptablePathTargets(Function<AbsoluteLocation, PathFinder.BlockKind> blockPermitsUser
			, Map<AbsoluteLocation, AbsoluteLocation> possiblePaths
			, AbsoluteLocation currentLocation
	)
	{
		// We will only choose paths which don't end in air above air (jumping into the air for no reason) or in water (since none of our creatures can breathe under water).
		List<AbsoluteLocation> goodTargets = new ArrayList<>();
		for (Map.Entry<AbsoluteLocation, AbsoluteLocation> elt : possiblePaths.entrySet())
		{
			AbsoluteLocation end = elt.getKey();
			PathFinder.BlockKind endKind = blockPermitsUser.apply(end);
			boolean shouldInclude = true;
			if (PathFinder.BlockKind.SWIMMABLE == endKind)
			{
				// This path ends in water so don't include it.
				shouldInclude = false;
			}
			else if (currentLocation.equals(end))
			{
				// We don't want to end where we started.
				shouldInclude = false;
			}
			else
			{
				// Blocks we pass through can only be walkable or swimmable.
				Assert.assertTrue(PathFinder.BlockKind.WALKABLE == endKind);
				AbsoluteLocation under = end.getRelative(0, 0, -1);
				if (PathFinder.BlockKind.WALKABLE == blockPermitsUser.apply(under))
				{
					// This block is over top of a walkable block (meaning it was a jump) so ignore it.
					shouldInclude = false;
				}
			}
			if (shouldInclude)
			{
				goodTargets.add(end);
			}
		}
		return goodTargets;
	}

	private static void _updateValidPathIfTargetMoved(TickProcessingContext context, MutableCreature mutable)
	{
		// We know that the target is valid and in range when we get here so the exist and are in range.
		MinimalEntity targetEntity = context.previousEntityLookUp.apply(mutable.newTargetEntityId);
		float distance = SpatialHelpers.distanceFromMutableEyeToEntitySurface(mutable, targetEntity);
		EntityType creatureType = mutable.getType();
		float pathDistance = creatureType.getPathDistance();
		Assert.assertTrue(distance <= pathDistance);
		if (distance < creatureType.actionDistance())
		{
			// They are close enough that we don't need to bother with the movement plan.
			mutable.newTargetPreviousLocation = null;
			mutable.newMovementPlan = null;
		}
		else
		{
			// We can keep this but see if we need to update their location.
			EntityLocation targetLocation = targetEntity.location();
			AbsoluteLocation newLocation = targetLocation.getBlockLocation();
			if (!newLocation.equals(mutable.newTargetPreviousLocation))
			{
				// They moved by at least a block so update their location and build a new path.
				mutable.newTargetPreviousLocation = newLocation;
				EntityVolume volume = mutable.getType().volume();
				Function<AbsoluteLocation, PathFinder.BlockKind> blockKindLookup = _createLookupHelper(context);
				mutable.setMovementPlan(PathFinder.findPathWithLimit(blockKindLookup, volume, mutable.getLocation(), targetLocation, pathDistance));
			}
		}
	}

	private static long _ticksBetweenActions(long millisPerTick)
	{
		return (MINIMUM_MILLIS_TO_ACTION / millisPerTick);
	}

	private static _TargetEntity _findBreedable(EntityCollection entityCollection, IMutableMinimalEntity creature)
	{
		_TargetEntity[] target = new _TargetEntity[1];
		float[] distanceToTarget = new float[] { Float.MAX_VALUE };
		EntityType thisType = creature.getType();
		int thisCreatureId = creature.getId();
		entityCollection.walkCreaturesInViewDistance(creature, (CreatureEntity check) -> {
			// Ignore ourselves and make sure that they are the same type and in love mode.
			if ((thisCreatureId != check.id()) && (thisType == check.type()) && check.ephemeral().inLoveMode())
			{
				// See how far away they are so we choose the closest.
				EntityLocation end = check.location();
				float distance = SpatialHelpers.distanceFromMutableEyeToEntitySurface(creature, MinimalEntity.fromCreature(check));
				if (distance < distanceToTarget[0])
				{
					target[0] = new _TargetEntity(check.id(), end);
					distanceToTarget[0] = distance;
				}
			}
		});
		return target[0];
	}

	private static _TargetEntity _findFeedingTarget(EntityCollection entityCollection, IMutableMinimalEntity creature)
	{
		_TargetEntity[] target = new _TargetEntity[1];
		float[] distanceToTarget = new float[] { Float.MAX_VALUE };
		EntityType thisType = creature.getType();
		entityCollection.walkPlayersInViewDistance(creature, (Entity player) -> {
			// See if this player has the breeding item in their hand.
			if (thisType.breedingItem() == _itemInPlayerHand(player))
			{
				// See how far away they are so we choose the closest.
				EntityLocation end = player.location();
				float distance = SpatialHelpers.distanceFromMutableEyeToEntitySurface(creature, MinimalEntity.fromEntity(player));
				if (distance < distanceToTarget[0])
				{
					target[0] = new _TargetEntity(player.id(), end);
					distanceToTarget[0] = distance;
				}
			}
		});
		return target[0];
	}

	private static _TargetEntity _findPlayerInRange(EntityCollection entityCollection, IMutableMinimalEntity creature)
	{
		_TargetEntity[] target = new _TargetEntity[1];
		float[] distanceToTarget = new float[] { Float.MAX_VALUE };
		entityCollection.walkPlayersInViewDistance(creature, (Entity player) -> {
			// We are looking for any player so just choose the closest.
			EntityLocation end = player.location();
			float distance = SpatialHelpers.distanceFromMutableEyeToEntitySurface(creature, MinimalEntity.fromEntity(player));
			if (distance < distanceToTarget[0])
			{
				target[0] = new _TargetEntity(player.id(), end);
				distanceToTarget[0] = distance;
			}
		});
		return target[0];
	}

	private static boolean _didTakeLivestockAction(TickProcessingContext context, MutableCreature creature)
	{
		boolean isDone = false;
		EntityType creatureType = creature.getType();
		// See if we are pregnant or searching for our mate.
		if (null != creature.newOffspringLocation)
		{
			// Spawn the creature and clear our offspring location.
			context.creatureSpawner.spawnCreature(creatureType, creature.newOffspringLocation, creatureType.maxHealth());
			creature.newOffspringLocation = null;
			_clearTargetAndPlan(creature);
			isDone = true;
		}
		else if (creature.newInLoveMode && (CreatureEntity.NO_TARGET_ENTITY_ID != creature.newTargetEntityId))
		{
			// We are in love mode, and have found a target, so see if we are close enough to impregnate our target.
			// We have a target so see if we are in love mode and if they are in range to breed.
			MinimalEntity targetEntity = context.previousEntityLookUp.apply(creature.newTargetEntityId);
			// If we got here, they must not have unloaded (we would have observed that in didUpdateTargetLocation.
			Assert.assertTrue(null != targetEntity);
			
			// See if they are within mating distance and we are the father.
			float distance = SpatialHelpers.distanceFromMutableEyeToEntitySurface(creature, targetEntity);
			float matingDistance = creatureType.actionDistance();
			if ((distance <= matingDistance) && (targetEntity.id() < creature.getId()))
			{
				// Send the message to impregnate them.
				EntityChangeImpregnateCreature sperm = new EntityChangeImpregnateCreature(creature.newLocation);
				context.newChangeSink.creature(creature.newTargetEntityId, sperm);
				// We can also now clear our plans since we are done with them.
				creature.newInLoveMode = false;
				_clearTargetAndPlan(creature);
				isDone = true;
			}
		}
		return isDone;
	}

	private static boolean _didTakeHostileAction(TickProcessingContext context, MutableCreature creature)
	{
		boolean isDone;
		// The only special action we will take is attacking but this path will also reset our tracking if the target moves.
		// We don't have an objective measurement of time but the tick rate is considered constant within a server instance so we will estimate time passed.
		long millisSinceLastAttack = (context.currentTick - creature.newLastAttackTick) * context.millisPerTick;
		if ((CreatureEntity.NO_TARGET_ENTITY_ID != creature.newTargetEntityId) && (millisSinceLastAttack >= MILLIS_ATTACK_COOLDOWN))
		{
			// We are tracking a target so see if they have moved (since we would need to clear our existing targets and
			// movement plans unless they are close enough for other actions).
			MinimalEntity targetEntity = context.previousEntityLookUp.apply(creature.newTargetEntityId);
			// If we got here, they must not have unloaded (we would have observed that in didUpdateTargetLocation.
			Assert.assertTrue(null != targetEntity);
			
			// See if they are in attack range.
			EntityType creatureType = creature.getType();
			float distance = SpatialHelpers.distanceFromMutableEyeToEntitySurface(creature, targetEntity);
			float attackDistance = creatureType.actionDistance();
			if (distance <= attackDistance)
			{
				// We can attack them so choose the target.
				int index = context.randomInt.applyAsInt(BodyPart.values().length);
				BodyPart target = BodyPart.values()[index];
				EntityChangeTakeDamageFromEntity<IMutablePlayerEntity> takeDamage = new EntityChangeTakeDamageFromEntity<>(target, creatureType.attackDamage(), creature.getId());
				context.newChangeSink.next(creature.newTargetEntityId, takeDamage);
				// Since we sent the attack, put us on attack cooldown.
				creature.newLastAttackTick = context.currentTick;
				// We only count a successful attack as an "action".
				isDone = true;
			}
			else
			{
				// Too far away.
				isDone = false;
			}
		}
		else
		{
			// Nothing to do.
			isDone = false;
		}
		return isDone;
	}

	private static void _clearTargetAndPlan(MutableCreature mutable)
	{
		mutable.newTargetEntityId = CreatureEntity.NO_TARGET_ENTITY_ID;
		mutable.newTargetPreviousLocation = null;
		mutable.newMovementPlan = null;
	}

	private static Item _itemInPlayerHand(Entity player)
	{
		int itemKey = player.hotbarItems()[player.hotbarIndex()];
		Items itemsInHand = player.inventory().getStackForKey(itemKey);
		return (null != itemsInHand)
				? itemsInHand.type()
				: null
		;
	}

	private static boolean _isTargetValid(EntityCollection entityCollection, MutableCreature mutable)
	{
		// We can only call this if there is a target.
		int targetId = mutable.newTargetEntityId;
		Assert.assertTrue(CreatureEntity.NO_TARGET_ENTITY_ID != targetId);
		
		// How we look at the target depends on our type and state.
		EntityType creatureType = mutable.getType();
		boolean isValid = false;
		if (creatureType.isLivestock())
		{
			// This may be a player or a partner creature, depending on state.
			if (mutable.newInLoveMode)
			{
				// We must be looking at a partner so make sure that they are here and still in breeding mode.
				CreatureEntity partner = entityCollection.getCreatureById(targetId);
				isValid = (null != partner)
						? partner.ephemeral().inLoveMode()
						: false
				;
			}
			else
			{
				// We must be looking at a player so make sure they still have food.
				isValid = _isPlayerVisibleAndHoldingFeed(entityCollection, mutable, targetId);
			}
		}
		else
		{
			// We currently must be one of these.
			Assert.assertTrue(creatureType.isHostile());
			
			// Make sure that they still exist, are in range.
			Entity player = entityCollection.getPlayerById(targetId);
			if (null != player)
			{
				float distance = SpatialHelpers.distanceFromMutableEyeToEntitySurface(mutable, MinimalEntity.fromEntity(player));
				isValid = (distance <= creatureType.viewDistance());
			}
		}
		return isValid;
	}

	private static boolean _isPlayerVisibleAndHoldingFeed(EntityCollection entityCollection, IMutableMinimalEntity creature, int targetId)
	{
		boolean isValid = false;
		Entity player = entityCollection.getPlayerById(targetId);
		if (null != player)
		{
			float distance = SpatialHelpers.distanceFromMutableEyeToEntitySurface(creature, MinimalEntity.fromEntity(player));
			EntityType creatureType = creature.getType();
			if (distance <= creatureType.viewDistance())
			{
				isValid = (creatureType.breedingItem() == _itemInPlayerHand(player));
			}
		}
		return isValid;
	}

	private static boolean _didDespawn(TickProcessingContext context, MutableCreature mutable)
	{
		boolean didDespawn = false;
		EntityType creatureType = mutable.getType();
		if (creatureType.isHostile() && (Difficulty.PEACEFUL == context.config.difficulty))
		{
			// If we are peaceful, we want to despawn any creatures which are hostile.
			mutable.newHealth = (byte)0;
			didDespawn = true;
		}
		else
		{
			// See if this should despawn due to a timeout.
			long despawnTick = mutable.newDespawnKeepAliveTick + (MILLIS_UNTIL_NO_ACTION_DESPAWN / context.millisPerTick);
			if (creatureType.canDespawn() && (despawnTick <= context.currentTick))
			{
				mutable.setHealth((byte)0);
				didDespawn = true;
			}
		}
		return didDespawn;
	}


	private static record _TargetEntity(int id, EntityLocation location) {}
}
