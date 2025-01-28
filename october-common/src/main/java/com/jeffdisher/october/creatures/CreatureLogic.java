package com.jeffdisher.october.creatures;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.logic.CreatureMovementHelpers;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.logic.PathFinder;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.mutations.EntityChangeImpregnateCreature;
import com.jeffdisher.october.mutations.EntityChangeTakeDamageFromEntity;
import com.jeffdisher.october.mutations.IMutationEntity;
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
	 * Called when newStepsToNextMove is null in order to determine the next action for the entity.  This is an
	 * opportunity for the creature to either just return the next actions for newStepsToNextMove from an existing plan
	 * or update/change its existing plan.
	 * Of special note, this is also where hostile mobs will be killed if in peaceful mode.
	 * 
	 * @param context The context of the current tick.
	 * @param entityCollection The read-only collection of entities in the world.
	 * @param mutable The mutable creature object currently being evaluated.
	 * @param timeLimitMillis The number of milliseconds left in the tick.
	 * @return The next action to take (null if there is nothing to do).
	 */
	public static IMutationEntity<IMutableCreatureEntity> planNextAction(TickProcessingContext context
			, EntityCollection entityCollection
			, MutableCreature mutable
			, long timeLimitMillis
	)
	{
		// Get the movement plan and see if we should advance it.
		Function<AbsoluteLocation, PathFinder.BlockKind> blockKindLookup = _createLookupHelper(context);
		boolean shouldMakePlan = (null == mutable.newMovementPlan);
		if (shouldMakePlan)
		{
			_makeMovementPlan(context, blockKindLookup, entityCollection, mutable);
		}
		else
		{
			_advanceMovementPlan(blockKindLookup, mutable);
		}
		// We never want to leave an empty movement plan so we expect that has been addressed before we got here.
		Assert.assertTrue((null == mutable.newMovementPlan) || !mutable.newMovementPlan.isEmpty());
		
		// We will only be able to produce an action if we have a movement plan.
		return (null != mutable.newMovementPlan)
				? _produceNextAction(context, blockKindLookup, mutable, mutable.newMovementPlan, timeLimitMillis)
				: null
		;
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
	 * 
	 * @param context The context of the current tick.
	 * @param creatureSpawner A consumer for any new entities spawned.
	 * @param creature The mutable creature object currently being evaluated.
	 * @return True if some special action was taken, meaning that this tick's actions should be skipped for this
	 * creature.
	 */
	public static boolean didTakeSpecialActions(TickProcessingContext context
			, Consumer<CreatureEntity> creatureSpawner
			, MutableCreature creature
	)
	{
		boolean isDone;
		EntityType creatureType = creature.getType();
		if (creatureType.isHostile() && (Difficulty.PEACEFUL == context.config.difficulty))
		{
			// If we are peaceful, we want to despawn any creatures which are hostile.
			creature.newHealth = (byte)0;
			isDone = true;
		}
		else
		{
			// See if this should despawn due to a timeout.
			long despawnTick = creature.newDespawnKeepAliveTick + (MILLIS_UNTIL_NO_ACTION_DESPAWN / context.millisPerTick);
			if (creatureType.canDespawn() && (despawnTick <= context.currentTick))
			{
				creature.setHealth((byte)0);
				isDone = true;
			}
			else
			{
				// Before we attempt to take a special action, see if we have a target which has moved.
				_updatePathIfTargetMoved(context, creature);
				
				if (creatureType.isLivestock())
				{
					isDone = _didTakeLivestockAction(context, creatureSpawner, creature);
				}
				else
				{
					// We assume that anything is either livestock or hostile.
					Assert.assertTrue(creatureType.isHostile());
					isDone = _didTakeHostileAction(context, creature);
				}
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

	private static IMutationEntity<IMutableCreatureEntity> _produceNextAction(TickProcessingContext context
			, Function<AbsoluteLocation, PathFinder.BlockKind> blockKindLookup
			, MutableCreature mutable
			, List<AbsoluteLocation> existingPlan
			, long timeLimitMillis
	)
	{
		Assert.assertTrue(!existingPlan.isEmpty());
		
		Block currentBlock = context.previousBlockLookUp.apply(mutable.newLocation.getBlockLocation()).getBlock();
		float viscosity = Environment.getShared().blocks.getViscosityFraction(currentBlock);
		boolean isIdleMovement = (CreatureEntity.NO_TARGET_ENTITY_ID == mutable.newTargetEntityId);
		
		// We have a path so make sure that we start in a reasonable part of the block so we don't bump into something or fail to jump out of a hole.
		AbsoluteLocation directionHint = existingPlan.get(0);
		if ((existingPlan.size() > 1) && (directionHint.z() > Math.floor(mutable.getLocation().z())))
		{
			// This means we are jumping so choose the next place where we want to go for direction hint.
			directionHint = existingPlan.get(1);
		}
		IMutationEntity<IMutableCreatureEntity> actionProduced = CreatureMovementHelpers.prepareForMove(mutable.getLocation(), mutable.getType(), directionHint, timeLimitMillis, viscosity, isIdleMovement);
		if (null == actionProduced)
		{
			// If we are already in a reasonable location, proceed to move.
			actionProduced = _planNextStep(blockKindLookup, mutable.getLocation(), mutable.getType(), existingPlan, timeLimitMillis, viscosity, isIdleMovement);
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
		EntityLocation creatureLocation = mutable.getLocation();
		EntityType type = mutable.getType();
		
		_TargetEntity newTarget;
		if (type.isLivestock())
		{
			// This is livestock so choose our target based on whether we are looking for a partner or food.
			if (mutable.isInLoveMode())
			{
				// Find another of this type in breeding mode.
				newTarget = _findBreedable(entityCollection, creatureLocation, type, mutable.getId());
			}
			else
			{
				// We will keep this simple:  Find the closest player holding our breeding item, up to our limit.
				newTarget = _findFeedingTarget(entityCollection, creatureLocation, type);
			}
		}
		else
		{
			// This is hostile so just search for a player.
			newTarget = _findPlayerInRange(entityCollection, creatureLocation, type);
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

	private static IMutationEntity<IMutableCreatureEntity> _planNextStep(Function<AbsoluteLocation, PathFinder.BlockKind> blockKindLookup
			, EntityLocation entityLocation
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
		return CreatureMovementHelpers.moveToNextLocation(entityLocation, type, thisStep, timeLimitMillis, viscosity, isIdleMovement, isSwimmable);
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
				if (environment.blocks.isSolid(block))
				{
					kind = PathFinder.BlockKind.SOLID;
				}
				else if (environment.blocks.canSwimInBlock(block))
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

	private static void _updatePathIfTargetMoved(TickProcessingContext context, MutableCreature mutable)
	{
		// If we are tracking another entity, see if we can update our target location.
		if (CreatureEntity.NO_TARGET_ENTITY_ID != mutable.newTargetEntityId)
		{
			// See if they are still loaded.
			MinimalEntity targetEntity = context.previousEntityLookUp.apply(mutable.newTargetEntityId);
			if (null != targetEntity)
			{
				// Make sure that they are still in our site range.
				EntityLocation creatureLocation = mutable.getLocation();
				EntityLocation targetLocation = targetEntity.location();
				float distance = SpatialHelpers.distanceBetween(creatureLocation, targetLocation);
				float pathDistance = mutable.getType().getPathDistance();
				if (distance <= pathDistance)
				{
					// We can keep this but see if we need to update their location.
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
				else
				{
					// They are out of range so forget them.
					_clearTargetAndPlan(mutable);
				}
			}
			else
			{
				_clearTargetAndPlan(mutable);
			}
		}
	}

	private static long _ticksBetweenActions(long millisPerTick)
	{
		return (MINIMUM_MILLIS_TO_ACTION / millisPerTick);
	}

	private static _TargetEntity _findBreedable(EntityCollection entityCollection, EntityLocation creatureLocation, EntityType thisType, int thisCreatureId)
	{
		_TargetEntity[] target = new _TargetEntity[1];
		float[] distanceToTarget = new float[] { Float.MAX_VALUE };
		entityCollection.walkCreaturesInRange(creatureLocation, thisType.viewDistance(), (CreatureEntity check) -> {
			// Ignore ourselves and make sure that they are the same type and in love mode.
			if ((thisCreatureId != check.id()) && (thisType == check.type()) && check.inLoveMode())
			{
				// See how far away they are so we choose the closest.
				EntityLocation end = check.location();
				float distance = SpatialHelpers.distanceBetween(creatureLocation, end);
				if (distance < distanceToTarget[0])
				{
					target[0] = new _TargetEntity(check.id(), end);
					distanceToTarget[0] = distance;
				}
			}
		});
		return target[0];
	}

	private static _TargetEntity _findFeedingTarget(EntityCollection entityCollection, EntityLocation creatureLocation, EntityType thisType)
	{
		_TargetEntity[] target = new _TargetEntity[1];
		float[] distanceToTarget = new float[] { Float.MAX_VALUE };
		entityCollection.walkPlayersInRange(creatureLocation, thisType.viewDistance(), (Entity player) -> {
			// See if this player has the breeding item in their hand.
			int itemKey = player.hotbarItems()[player.hotbarIndex()];
			Items itemsInHand = player.inventory().getStackForKey(itemKey);
			if ((null != itemsInHand) && (thisType.breedingItem() == itemsInHand.type()))
			{
				// See how far away they are so we choose the closest.
				EntityLocation end = player.location();
				float distance = SpatialHelpers.distanceBetween(creatureLocation, end);
				if (distance < distanceToTarget[0])
				{
					target[0] = new _TargetEntity(player.id(), end);
					distanceToTarget[0] = distance;
				}
			}
		});
		return target[0];
	}

	private static _TargetEntity _findPlayerInRange(EntityCollection entityCollection, EntityLocation creatureLocation, EntityType thisType)
	{
		_TargetEntity[] target = new _TargetEntity[1];
		float[] distanceToTarget = new float[] { Float.MAX_VALUE };
		entityCollection.walkPlayersInRange(creatureLocation, thisType.viewDistance(), (Entity player) -> {
			// We are looking for any player so just choose the closest.
			EntityLocation end = player.location();
			float distance = SpatialHelpers.distanceBetween(creatureLocation, end);
			if (distance < distanceToTarget[0])
			{
				target[0] = new _TargetEntity(player.id(), end);
				distanceToTarget[0] = distance;
			}
		});
		return target[0];
	}

	private static boolean _didTakeLivestockAction(TickProcessingContext context, Consumer<CreatureEntity> creatureSpawner, MutableCreature creature)
	{
		boolean isDone = false;
		EntityType creatureType = creature.getType();
		// See if we are pregnant or searching for our mate.
		if (null != creature.newOffspringLocation)
		{
			// Spawn the creature and clear our offspring location.
			creatureSpawner.accept(CreatureEntity.create(context.idAssigner.next(), creatureType, creature.newOffspringLocation, creatureType.maxHealth()));
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
			EntityLocation targetLocation = targetEntity.location();
			float distance = SpatialHelpers.distanceBetween(creature.newLocation, targetLocation);
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
			EntityLocation targetLocation = targetEntity.location();
			float distance = SpatialHelpers.distanceBetween(creature.newLocation, targetLocation);
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


	private static record _TargetEntity(int id, EntityLocation location) {}
}
