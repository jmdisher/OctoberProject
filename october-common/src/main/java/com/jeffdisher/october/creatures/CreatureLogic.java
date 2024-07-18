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
import com.jeffdisher.october.logic.SpatialHelpers;
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
	 * opportunity for the creature to either just return the next actions for newStepsToNextMove from an existing plan,
	 * take special actions before doing that, or update/change its existing plan.
	 * Of special note, this is also where hostile mobs will be killed if in peaceful mode.
	 * 
	 * @param context The context of the current tick.
	 * @param creatureSpawner A consumer for any new entities spawned.
	 * @param entityCollection The read-only collection of entities in the world.
	 * @param millisSinceLastAction The number of milliseconds since this creature last took an action.
	 * @param mutable The mutable creature object currently being evaluated.
	 * @return Returns the list of actions to take next (could be null if nothing to do or empty just to reset the idle
	 * timer).
	 */
	public static List<IMutationEntity<IMutableCreatureEntity>> planNextActions(TickProcessingContext context
			, Consumer<CreatureEntity> creatureSpawner
			, EntityCollection entityCollection
			, long millisSinceLastAction
			, MutableCreature mutable
	)
	{
		// Only called if we don't currently have a published set of next steps.
		Assert.assertTrue(null == mutable.newStepsToNextMove);
		List<IMutationEntity<IMutableCreatureEntity>> actionsProduced;
		
		// The logic is per-creature type.
		switch (mutable.creature.type())
		{
		case COW: {
			CowStateMachine machine = CowStateMachine.extractFromData(mutable.newExtendedData);
			actionsProduced = _planNextActions(context, creatureSpawner, entityCollection, millisSinceLastAction, mutable, machine);
			mutable.newExtendedData = machine.freezeToData();
		}
			break;
		case ORC: {
			// Orcs are hostile mobs so we will kill this entity off if in peaceful mode.
			if (Difficulty.PEACEFUL == context.difficulty)
			{
				actionsProduced = null;
				mutable.newHealth = (byte)0;
			}
			else
			{
				OrcStateMachine machine = OrcStateMachine.extractFromData(mutable.newExtendedData);
				actionsProduced = _planNextActions(context, creatureSpawner, entityCollection, millisSinceLastAction, mutable, machine);
				mutable.newExtendedData = machine.freezeToData();
			}
		}
			break;
		case ERROR:
		case PLAYER:
		default:
			throw Assert.unreachable();
		}
		return actionsProduced;
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
	 * Just a testing entry-point for the random target path selection (idle movement).
	 * 
	 * @param context The testing context.
	 * @param creature The test creature.
	 * @return A path to a randomly selected target.
	 */
	public static List<AbsoluteLocation> test_findPathToRandomSpot(TickProcessingContext context, CreatureEntity creature)
	{
		return _findPathToRandomSpot(context, creature);
	}


	private static List<IMutationEntity<IMutableCreatureEntity>> _planNextActions(TickProcessingContext context
			, Consumer<CreatureEntity> creatureSpawner
			, EntityCollection entityCollection
			, long millisSinceLastAction
			, MutableCreature mutable
			, ICreatureStateMachine machine
	)
	{
		List<IMutationEntity<IMutableCreatureEntity>> actionsProduced;
		
		// We will first determine if we need to take any special actions (sending actions, spawning offspring, etc)
		boolean isDone = machine.doneSpecialActions(context, creatureSpawner, mutable.creature);
		
		if (isDone)
		{
			// We are done after taking special actions for this tick.
			actionsProduced = null;
		}
		else
		{
			actionsProduced = _actionsToProduce(context, entityCollection, millisSinceLastAction, mutable, machine);
		}
		return actionsProduced;
	}

	private static List<IMutationEntity<IMutableCreatureEntity>> _actionsToProduce(TickProcessingContext context
			, EntityCollection entityCollection
			, long millisSinceLastAction
			, MutableCreature mutable
			, ICreatureStateMachine machine
	)
	{
		List<IMutationEntity<IMutableCreatureEntity>> actionsProduced;
		// Now, determine if we have a plan or need to make one.
		List<AbsoluteLocation> movementPlan = machine.getMovementPlan();
		// We only want to check every MINIMUM_MILLIS_TO_DELIBERATE_ACTION so that we don't slam this decision-making on every tick.
		if ((null == movementPlan)
				&& (millisSinceLastAction >= MINIMUM_MILLIS_TO_DELIBERATE_ACTION)
				&& ((millisSinceLastAction % MINIMUM_MILLIS_TO_DELIBERATE_ACTION) <= context.millisPerTick)
		)
		{
			// First, we want to see if we should walk toward a player.
			movementPlan = _buildDeliberatePath(context, entityCollection, mutable.creature, machine);
			
			// If we don't have anything deliberate to do, we will just do some random "idle" movement but this is
			// somewhat expensive so only do it if we have been waiting a while or if we are in danger (since the random
			// movements are "safe").
			boolean isInDanger = (mutable.newBreath < EntityConstants.MAX_BREATH);
			if ((null == movementPlan) && (
					isInDanger
					|| (millisSinceLastAction >= MINIMUM_MILLIS_TO_IDLE_ACTION)
			))
			{
				// We couldn't find a player so just make a random move.
				movementPlan = _findPathToRandomSpot(context, mutable.creature);
			}
			
			// Convert the new plan into actions.
			if (null == movementPlan)
			{
				// Fall through if we have no plan so we will try again on the next tick.
				actionsProduced = null;
			}
			else
			{
				// We have a path so make sure that we start in a reasonable part of the block so we don't bump into something or fail to jump out of a hole.
				AbsoluteLocation directionHint;
				if (movementPlan.size() > 1)
				{
					directionHint = movementPlan.get(1);
					if ((movementPlan.size() > 2) && (directionHint.z() > Math.floor(mutable.creature.location().z())))
					{
						// This means we are jumping so choose the next place where we want to go for direction hint.
						directionHint = movementPlan.get(2);
					}
				}
				else
				{
					directionHint = movementPlan.get(0);
				}
				// We have a path so make sure we are centred in a block, first.
				actionsProduced = CreatureMovementHelpers.centreOnCurrentBlock(mutable.creature, directionHint);
				if (actionsProduced.isEmpty())
				{
					// We are already in the centre so just get started.
					actionsProduced = null;
				}
			}
		}
		else
		{
			// We start with no actions and will fall-through to the plans below.
			actionsProduced = null;
		}
		
		// Determine if we need to set the next steps in case we devised a plan.
		// (the movementPlan should only be null here if we are waiting to make our next decision)
		if (null != movementPlan)
		{
			// The only reason why this is still null at this point is if we have a plan and we are ready to use it.
			List<AbsoluteLocation> mutablePlan = new ArrayList<>(movementPlan);
			if (null == actionsProduced)
			{
				actionsProduced = _determineNextSteps(mutable.creature, mutablePlan, !machine.isPlanDeliberate());
			}
			
			// We can now update our extended data.
			if (mutablePlan.isEmpty())
			{
				mutablePlan = null;
			}
			machine.setMovementPlan(mutablePlan);
		}
		return actionsProduced;
	}

	private static List<AbsoluteLocation> _buildDeliberatePath(TickProcessingContext context, EntityCollection entityCollection, CreatureEntity creature, ICreatureStateMachine machine)
	{
		Function<AbsoluteLocation, PathFinder.BlockKind> blockPermitsPassage = _createLookupHelper(context);
		EntityLocation targetLocation = machine.selectDeliberateTarget(entityCollection, creature);
		List<AbsoluteLocation> path = null;
		if (null != targetLocation)
		{
			// We have a target so try to build a path (we will use double the distance for pathing overhead).
			// If this fails, it will return null which is already our failure case.
			EntityVolume volume = EntityConstants.getVolume(creature.type());
			EntityLocation start = creature.location();
			path = PathFinder.findPathWithLimit(blockPermitsPassage, volume, start, targetLocation, machine.getPathDistance());
		}
		return path;
	}

	private static List<AbsoluteLocation> _findPathToRandomSpot(TickProcessingContext context, CreatureEntity creature)
	{
		Function<AbsoluteLocation, PathFinder.BlockKind> blockPermitsUser = _createLookupHelper(context);
		EntityVolume volume = EntityConstants.getVolume(creature.type());
		EntityLocation source = creature.location();
		float limitSteps = RANDOM_MOVEMENT_DISTANCE;
		Map<AbsoluteLocation, AbsoluteLocation> possiblePaths = PathFinder.findPlacesWithinLimit(blockPermitsUser, volume, source, limitSteps);
		
		// Strip out any of the ending positions which we don't want.
		List<AbsoluteLocation> goodTargets = _extractAcceptablePathTargets(blockPermitsUser, possiblePaths);
		
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
		}
		else
		{
			plannedPath = null;
		}
		return plannedPath;
	}

	private static List<IMutationEntity<IMutableCreatureEntity>> _determineNextSteps(CreatureEntity creature, List<AbsoluteLocation> mutablePlan, boolean isIdleMovement)
	{
		// First, check to see if we are already in our next location.
		AbsoluteLocation thisStep = mutablePlan.get(0);
		EntityLocation entityLocation = creature.location();
		AbsoluteLocation currentLocation = entityLocation.getBlockLocation();
		
		List<IMutationEntity<IMutableCreatureEntity>> changes;
		if (currentLocation.equals(thisStep))
		{
			// If we are, that means that we can remove this from the path and plan to move to the next step.
			mutablePlan.remove(0);
			if (!mutablePlan.isEmpty())
			{
				AbsoluteLocation nextStep = mutablePlan.get(0);
				changes = CreatureMovementHelpers.moveToNextLocation(creature, nextStep, isIdleMovement);
				// If this is empty, just allow us to do nothing and reset the timer instead of returning null (should only be empty if falling, for example).
			}
			else
			{
				// There is nothing left in the plan so just return null so that the timer will expire soon for the next plan.
				changes = null;
			}
		}
		else
		{
			// Otherwise, check to see if we are currently in the air, since we might just be jumping/falling into the next location.
			if (SpatialHelpers.isBlockAligned(entityLocation.z()))
			{
				// We appear to be on the surface, meaning that we were somehow blocked.  Therefore, clear the plan and we will try again after our idle timer.
				mutablePlan.clear();
				changes = null;
			}
			else
			{
				// Do nothing since we are moving through the air.
				changes = null;
			}
		}
		return changes;
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
