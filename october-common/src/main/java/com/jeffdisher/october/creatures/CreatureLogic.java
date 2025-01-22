package com.jeffdisher.october.creatures;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import com.jeffdisher.october.aspects.CreatureRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.logic.CreatureMovementHelpers;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.logic.PathFinder;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.Difficulty;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.IMutableCreatureEntity;
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
	public static final float RANDOM_MOVEMENT_DISTANCE = 3.5f;
	/**
	 * The minimum number of millis we can wait from our last action until we decide to make a deliberate plan.
	 */
	public static final long MINIMUM_MILLIS_TO_DELIBERATE_ACTION = 1_000L;
	/**
	 * The minimum number of millis we can wait from our last action until we decide to make an idling plan, assuming
	 * there was no good deliberate option.
	 */
	public static final long MINIMUM_MILLIS_TO_IDLE_ACTION = 30_000L;


	/**
	 * A helper to determine if the given item can be used on a specific entity type with this entity mutation.
	 * 
	 * @param item The item.
	 * @param entityType The target entity type.
	 * @return True if this mutation can be used to apply the item to the entity.
	 */
	public static boolean canUseOnEntity(Item item, EntityType entityType)
	{
		boolean canUse;
		if (CreatureRegistry.COW == entityType)
		{
			canUse = CowStateMachine.canUseItem(item);
		}
		else if (CreatureRegistry.ORC == entityType)
		{
			canUse = false;
		}
		else
		{
			throw Assert.unreachable();
		}
		return canUse;
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
		boolean didApply;
		if (CreatureRegistry.COW == creature.getType())
		{
			Object originalData = creature.getExtendedData();
			CowStateMachine cow = CowStateMachine.extractFromData(originalData);
			boolean didChangeState = cow.applyItem(itemType);
			if (didChangeState)
			{
				creature.setMovementPlan(null);
			}
			Object updated = cow.freezeToData();
			if (originalData != updated)
			{
				creature.setExtendedData(updated);
				didApply = true;
			}
			else
			{
				didApply = false;
			}
		}
		else if (CreatureRegistry.ORC == creature.getType())
		{
			didApply = false;
		}
		else
		{
			throw Assert.unreachable();
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
		ICreatureStateMachine machine;
		
		// The machine must be decoded based on type but the planning logic is common (at least for now).
		if (CreatureRegistry.COW == mutable.getType())
		{
			machine = CowStateMachine.extractFromData(mutable.newExtendedData);
		}
		else if (CreatureRegistry.ORC == mutable.getType())
		{
			machine = OrcStateMachine.extractFromData(mutable.newExtendedData);
		}
		else
		{
			throw Assert.unreachable();
		}
		
		// Get the movement plan and see if we should advance it.
		List<AbsoluteLocation> movementPlan = mutable.newMovementPlan;
		Function<AbsoluteLocation, PathFinder.BlockKind> blockKindLookup = _createLookupHelper(context);
		boolean shouldMakePlan = (null == movementPlan);
		if (shouldMakePlan)
		{
			movementPlan = _makeMovementPlan(context, blockKindLookup, entityCollection, mutable, machine);
		}
		else
		{
			movementPlan = _advanceMovementPlan(blockKindLookup, mutable, movementPlan);
		}
		Assert.assertTrue((null == movementPlan) || !movementPlan.isEmpty());
		
		IMutationEntity<IMutableCreatureEntity> actionProduced;
		if (null != movementPlan)
		{
			actionProduced = _produceNextAction(context, blockKindLookup, mutable, machine, movementPlan, timeLimitMillis);
		}
		else
		{
			actionProduced = null;
		}
		mutable.newExtendedData = machine.freezeToData();
		return actionProduced;
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
		boolean didBecomePregnant;
		if (CreatureRegistry.COW == creature.getType())
		{
			CowStateMachine machine = CowStateMachine.extractFromData(creature.getExtendedData());
			// Average the locations.
			EntityLocation parentLocation = creature.getLocation();
			EntityLocation spawnLocation = new EntityLocation((sireLocation.x() + parentLocation.x()) / 2.0f
					, (sireLocation.y() + parentLocation.y()) / 2.0f
					, (sireLocation.z() + parentLocation.z()) / 2.0f
			);
			didBecomePregnant = machine.setPregnant(spawnLocation);
			if (didBecomePregnant)
			{
				creature.setMovementPlan(null);
				creature.resetDeliberateTick();
				creature.setExtendedData(machine.freezeToData());
			}
		}
		else if (CreatureRegistry.ORC == creature.getType())
		{
			// This case shouldn't be reachable since a cow should only target another cow and IDs are never reused.
			throw Assert.unreachable();
		}
		else
		{
			throw Assert.unreachable();
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
		Runnable requestDespawnWithoutDrops = () -> {
			// If we want to despawn them without drops, just set their health to zero without asking the creature to handle the death.
			creature.setHealth((byte)0);
		};
		boolean isDone;
		if (CreatureRegistry.COW == creature.getType())
		{
			CowStateMachine machine = CowStateMachine.extractFromData(creature.getExtendedData());
			
			// Before we attempt to take a special action, see if we have a target which has moved.
			_updatePathIfTargetMoved(context, creature, machine.getPathDistance());
			
			// Now, account for the special actions.
			isDone = machine.doneSpecialActions(context, creatureSpawner, requestDespawnWithoutDrops, creature.getLocation(), creature.getId(), creature.newTargetEntityId);
			if (isDone)
			{
				creature.setMovementPlan(null);
				creature.resetDeliberateTick();
				creature.setExtendedData(machine.freezeToData());
			}
		}
		else if (CreatureRegistry.ORC == creature.getType())
		{
			// Orcs are hostile mobs so we will kill this entity off if in peaceful mode.
			if (Difficulty.PEACEFUL == context.config.difficulty)
			{
				creature.newHealth = (byte)0;
				isDone = true;
			}
			else
			{
				OrcStateMachine machine = OrcStateMachine.extractFromData(creature.newExtendedData);
				
				// Before we attempt to take a special action, see if we have a target which has moved.
				_updatePathIfTargetMoved(context, creature, machine.getPathDistance());
				
				// Now, account for the special actions.
				isDone = machine.doneSpecialActions(context, creatureSpawner, requestDespawnWithoutDrops, creature.getLocation(), creature.getId(), creature.newTargetEntityId);
				if (isDone)
				{
					creature.setMovementPlan(null);
					creature.resetDeliberateTick();
					creature.setExtendedData(machine.freezeToData());
				}
			}
		}
		else
		{
			throw Assert.unreachable();
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


	private static List<AbsoluteLocation> _makeMovementPlan(TickProcessingContext context
			, Function<AbsoluteLocation, PathFinder.BlockKind> blockKindLookup
			, EntityCollection entityCollection
			, MutableCreature mutable
			, ICreatureStateMachine machine
	)
	{
		// We will first see if they can make a deliberate plan.
		List<AbsoluteLocation> movementPlan = _buildDeliberatePath(context
				, blockKindLookup
				, entityCollection
				, mutable
				, machine
		);
		if (null != movementPlan)
		{
			// We made a deliberate plan but it might not have any steps.
			if (movementPlan.isEmpty())
			{
				movementPlan = null;
			}
		}
		else
		{
			// If we don't have anything deliberate to do, we will just do some random "idle" movement but this is
			// somewhat expensive so only do it if we have been waiting a while or if we are in danger (since the random
			// movements are "safe").
			boolean isInDanger = (mutable.newBreath < MiscConstants.MAX_BREATH);
			if (isInDanger
					|| _canMakeIdleMovement(context, mutable)
			)
			{
				// We couldn't find a player so just make a random move.
				movementPlan = _findPathToRandomSpot(context
						, blockKindLookup
						, mutable.getLocation()
						, mutable.getType()
				);
			}
		}
		mutable.newMovementPlan = movementPlan;
		return movementPlan;
	}

	private static IMutationEntity<IMutableCreatureEntity> _produceNextAction(TickProcessingContext context
			, Function<AbsoluteLocation, PathFinder.BlockKind> blockKindLookup
			, MutableCreature mutable
			, ICreatureStateMachine machine
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
			, ICreatureStateMachine machine
	)
	{
		EntityLocation creatureLocation = mutable.getLocation();
		EntityType type = mutable.getType();
		int creatureId = mutable.getId();
		
		List<AbsoluteLocation> path = null;
		if (context.currentTick >= mutable.newNextDeliberateActTick)
		{
			ICreatureStateMachine.TargetEntity newTarget = machine.selectTarget(context, entityCollection, creatureLocation, creatureId);
			if (null != newTarget)
			{
				EntityLocation targetLocation = newTarget.location();
				mutable.newTargetEntityId = newTarget.id();
				mutable.newTargetPreviousLocation = targetLocation.getBlockLocation();
				// We have a target so try to build a path (we will use double the distance for pathing overhead).
				// If this fails, it will return null which is already our failure case.
				EntityVolume volume = type.volume();
				path = PathFinder.findPathWithLimit(blockPermitsPassage, volume, creatureLocation, targetLocation, machine.getPathDistance());
				// We want to strip away the first step, since it is the current location.
				if (null != path)
				{
					path.remove(0);
					// (we will still return an empty path just to communicate that we made a decision.
				}
			}
			
			// Update our next action ticks.
			mutable.newNextDeliberateActTick = context.currentTick + (MINIMUM_MILLIS_TO_DELIBERATE_ACTION / context.millisPerTick);
			if (null != newTarget)
			{
				// If we found someone, we also want to delay idle actions (we should fall into idle movement if we keep failing here).
				mutable.newNextIdleActTick = context.currentTick + (MINIMUM_MILLIS_TO_IDLE_ACTION / context.millisPerTick);
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

	private static List<AbsoluteLocation> _advanceMovementPlan(Function<AbsoluteLocation, PathFinder.BlockKind> blockKindLookup
			, MutableCreature mutable
			, List<AbsoluteLocation> existingPlan
	)
	{
		// First, check to see if we are already in our next location.
		AbsoluteLocation thisStep = existingPlan.get(0);
		EntityLocation entityLocation = mutable.getLocation();
		AbsoluteLocation currentLocation = entityLocation.getBlockLocation();
		
		List<AbsoluteLocation> updatedPlan;
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
		return updatedPlan;
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

	private static boolean _canMakeIdleMovement(TickProcessingContext context
			, MutableCreature mutable
	)
	{
		boolean canMove = (context.currentTick >= mutable.newNextIdleActTick);
		if (canMove)
		{
			// We want to "consume" this decision.
			mutable.newNextIdleActTick = context.currentTick + (MINIMUM_MILLIS_TO_IDLE_ACTION / context.millisPerTick);
		}
		return canMove;
	}

	private static void _updatePathIfTargetMoved(TickProcessingContext context, MutableCreature mutable, float pathDistance)
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
					mutable.newTargetEntityId = CreatureEntity.NO_TARGET_ENTITY_ID;
					mutable.newTargetPreviousLocation = null;
					mutable.newMovementPlan = null;
				}
			}
			else
			{
				// The unloaded, so clear.
				mutable.newTargetEntityId = CreatureEntity.NO_TARGET_ENTITY_ID;
				mutable.newTargetPreviousLocation = null;
				mutable.newMovementPlan = null;
			}
		}
	}
}
