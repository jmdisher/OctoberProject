package com.jeffdisher.october.creatures;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.logic.CreatureMovementHelpers;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.logic.PathFinder;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.Difficulty;
import com.jeffdisher.october.types.EntityConstants;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.IMutableCreatureEntity;
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
	public static final float RANDOM_MOVEMENT_DISTANCE = 3.5f;


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
		switch (entityType)
		{
		case COW:
			canUse = CowStateMachine.canUseItem(item);
			break;
		case ORC:
			canUse = false;
			break;
		case ERROR:
		case PLAYER:
		default:
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
		switch (creature.getType())
		{
		case COW:
			Object originalData = creature.getExtendedData();
			CowStateMachine cow = CowStateMachine.extractFromData(originalData);
			cow.applyItem(itemType);
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
			break;
		case ORC:
			didApply = false;
			break;
		case ERROR:
		case PLAYER:
		default:
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
		switch (mutable.getType())
		{
		case COW: {
			machine = CowStateMachine.extractFromData(mutable.newExtendedData);
		}
			break;
		case ORC: {
			machine = OrcStateMachine.extractFromData(mutable.newExtendedData);
		}
			break;
		case ERROR:
		case PLAYER:
		default:
			throw Assert.unreachable();
		}
		
		// Get the movement plan and see if we should advance it.
		List<AbsoluteLocation> movementPlan = machine.getMovementPlan();
		Function<AbsoluteLocation, PathFinder.BlockKind> blockKindLookup = _createLookupHelper(context);
		boolean shouldMakePlan = (null == movementPlan);
		if (shouldMakePlan)
		{
			movementPlan = _makeMovementPlan(context, blockKindLookup, entityCollection, mutable, machine);
		}
		else
		{
			movementPlan = _advanceMovementPlan(blockKindLookup, mutable.getLocation(), movementPlan);
		}
		Assert.assertTrue((null == movementPlan) || !movementPlan.isEmpty());
		machine.setMovementPlan(movementPlan);
		
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
		switch (creature.getType())
		{
		case COW:{
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
				creature.setExtendedData(machine.freezeToData());
			}
			break;
		}
		case ORC:
			// This case shouldn't be reachable since a cow should only target another cow and IDs are never reused.
		case ERROR:
		case PLAYER:
		default:
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
		switch (creature.getType())
		{
		case COW: {
			CowStateMachine machine = CowStateMachine.extractFromData(creature.getExtendedData());
			isDone = machine.doneSpecialActions(context, creatureSpawner, requestDespawnWithoutDrops, creature.getLocation(), creature.getId());
			if (isDone)
			{
				creature.setExtendedData(machine.freezeToData());
			}
			break;
		}
		case ORC: {
			// Orcs are hostile mobs so we will kill this entity off if in peaceful mode.
			if (Difficulty.PEACEFUL == context.config.difficulty)
			{
				creature.newHealth = (byte)0;
				isDone = true;
			}
			else
			{
				OrcStateMachine machine = OrcStateMachine.extractFromData(creature.newExtendedData);
				isDone = machine.doneSpecialActions(context, creatureSpawner, requestDespawnWithoutDrops, creature.getLocation(), creature.getId());
				if (isDone)
				{
					creature.setExtendedData(machine.freezeToData());
				}
			}
		}
			break;
		case ERROR:
		case PLAYER:
		default:
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
				, mutable.getLocation()
				, mutable.getType()
				, mutable.getId()
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
			boolean isInDanger = (mutable.newBreath < EntityConstants.MAX_BREATH);
			if (isInDanger
					|| machine.canMakeIdleMovement(context)
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
		boolean isIdleMovement = !machine.isPlanDeliberate();
		
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
			, EntityLocation creatureLocation
			, EntityType type
			, int creatureId
			, ICreatureStateMachine machine
	)
	{
		EntityLocation targetLocation = machine.selectDeliberateTarget(context, entityCollection, creatureLocation, creatureId);
		List<AbsoluteLocation> path = null;
		if (null != targetLocation)
		{
			// We have a target so try to build a path (we will use double the distance for pathing overhead).
			// If this fails, it will return null which is already our failure case.
			EntityVolume volume = EntityConstants.getVolume(type);
			path = PathFinder.findPathWithLimit(blockPermitsPassage, volume, creatureLocation, targetLocation, machine.getPathDistance());
			// We want to strip away the first step, since it is the current location.
			if (null != path)
			{
				path.remove(0);
				// (we will still return an empty path just to communicate that we made a decision.
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
		EntityVolume volume = EntityConstants.getVolume(type);
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
			, EntityLocation entityLocation
			, List<AbsoluteLocation> existingPlan
	)
	{
		// First, check to see if we are already in our next location.
		AbsoluteLocation thisStep = existingPlan.get(0);
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
}
