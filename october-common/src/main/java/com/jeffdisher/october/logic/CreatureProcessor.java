package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.creatures.CreatureVolumes;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.mutations.EntityChangeDoNothing;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.MutableCreature;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Static logic which implements the parallel game tick logic when operating on CreatureEntity instances within the
 * world.
 * This is very similar to CrowdProcessor but has some different internal logic and other helpers related to creatures.
 */
public class CreatureProcessor
{
	public static final long MINIMUM_TICKS_TO_NEW_ACTION = 10L;
	public static final float RANDOM_MOVEMENT_DISTANCE = 2.5f;

	private CreatureProcessor()
	{
		// This is just static logic.
	}

	/**
	 * Runs the given changesToRun on the creatures provided in creaturesById, in parallel.
	 * 
	 * @param processor The current thread.
	 * @param creaturesById The map of all read-only creatures from the previous tick.
	 * @param context The context used for running changes.
	 * @param millisSinceLastTick Milliseconds based since last tick.
	 * @param changesToRun The map of changes to run in this tick, keyed by the ID of the creature on which they are
	 * scheduled.
	 * @return The subset of the changesToRun work which was completed by this thread.
	 */
	public static CreatureGroup processCreatureGroupParallel(ProcessorElement processor
			, Map<Integer, CreatureEntity> creaturesById
			, TickProcessingContext context
			, long millisSinceLastTick
			, Map<Integer, List<IMutationEntity<IMutableMinimalEntity>>> changesToRun
	)
	{
		Map<Integer, CreatureEntity> updatedCreatures = new HashMap<>();
		List<Integer> deadCreatureIds = new ArrayList<>();
		int committedMutationCount = 0;
		Random random = new Random(context.currentTick);
		for (Map.Entry<Integer, CreatureEntity> elt : creaturesById.entrySet())
		{
			if (processor.handleNextWorkUnit())
			{
				// This is our element.
				Integer id = elt.getKey();
				CreatureEntity creature = elt.getValue();
				processor.creaturesProcessed += 1;
				
				MutableCreature mutable = MutableCreature.existing(creature);
				List<IMutationEntity<IMutableMinimalEntity>> changes = changesToRun.get(id);
				if (null == changes)
				{
					// We have nothing to do, and nothing is acting on us, so see if we have a plan to apply or should select one.
					changes = _setupNextMovements(context, random, millisSinceLastTick, mutable);
				}
				
				long millisApplied = 0L;
				
				if (null != changes)
				{
					for (IMutationEntity<IMutableMinimalEntity> change : changes)
					{
						processor.creatureChangesProcessed += 1;
						boolean didApply = change.applyChange(context, mutable);
						if (didApply)
						{
							committedMutationCount += 1;
						}
						millisApplied += change.getTimeCostMillis();
					}
					mutable.newLastActionGameTick = context.currentTick;
				}
				
				// See if we need to account for doing nothing.
				if (millisApplied < millisSinceLastTick)
				{
					long millisToWait = millisSinceLastTick - millisApplied;
					EntityChangeDoNothing<IMutableMinimalEntity> doNothing = new EntityChangeDoNothing<>(mutable.newLocation, millisToWait);
					boolean didApply = doNothing.applyChange(context, mutable);
					if (didApply)
					{
						committedMutationCount += 1;
					}
				}
				
				// If there was a change, we want to send it back so that the snapshot can be updated and clients can be informed.
				// This freeze() call will return the original instance if it is identical.
				// Note that the creature will become null if it died.
				CreatureEntity newEntity = mutable.freeze();
				if (null == newEntity)
				{
					deadCreatureIds.add(id);
				}
				else if (newEntity != creature)
				{
					updatedCreatures.put(id, newEntity);
				}
			}
		}
		return new CreatureGroup(committedMutationCount
				, updatedCreatures
				, deadCreatureIds
		);
	}


	private static List<IMutationEntity<IMutableMinimalEntity>> _setupNextMovements(TickProcessingContext context, Random random, long millisSinceLastTick, MutableCreature mutable)
	{
		List<IMutationEntity<IMutableMinimalEntity>> changes = null;
		if (null == mutable.newMovementPlan)
		{
			// We have no plan so determine one.
			// Note that we must also not have any other steps if we get here.
			Assert.assertTrue(null == mutable.newStepsToNextMove);
			// We will only do this if we have been idle for long enough.
			if (context.currentTick > (mutable.newLastActionGameTick + MINIMUM_TICKS_TO_NEW_ACTION))
			{
				mutable.newMovementPlan = _findPath(context, random, mutable.creature);
				if (null == mutable.newMovementPlan)
				{
					// There are no possible moves so do nothing, but consider this an action so we reset the timer.
					changes = List.of();
				}
				else
				{
					// We have a path so make sure we are centred in a block, first.
					mutable.newStepsToNextMove = CreatureMovementHelpers.centreOnCurrentBlock(mutable.creature);
					if (mutable.newStepsToNextMove.isEmpty())
					{
						// We are already in the centre so just get started.
						mutable.newStepsToNextMove = null;
					}
				}
			}
		}
		if (null == mutable.newStepsToNextMove)
		{
			// Convert our next destination into commands.
			if (null != mutable.newMovementPlan)
			{
				// We shouldn't be running anything yet.
				Assert.assertTrue(null == changes);
				
				changes = _determineNextSteps(mutable);
			}
		}
		if (null != mutable.newStepsToNextMove)
		{
			// We must have something in this.
			Assert.assertTrue(!mutable.newStepsToNextMove.isEmpty());
			// We shouldn't have a plan yet.
			Assert.assertTrue(null == changes);
			
			changes = _scheduleForThisTick(millisSinceLastTick, mutable);
		}
		return changes;
	}

	private static List<AbsoluteLocation> _findPath(TickProcessingContext context, Random random, CreatureEntity creature)
	{
		// Our current only action is to find a block nearby and walk to it.
		Environment environment = Environment.getShared();
		Predicate<AbsoluteLocation> blockPermitsUser = (AbsoluteLocation location) -> {
			BlockProxy proxy = context.previousBlockLookUp.apply(location);
			return (null != proxy)
					? environment.blocks.permitsEntityMovement(proxy.getBlock())
					: false
			;
		};
		EntityVolume volume = CreatureVolumes.getVolume(creature);
		EntityLocation source = creature.location();
		float limitSteps = RANDOM_MOVEMENT_DISTANCE;
		Map<AbsoluteLocation, AbsoluteLocation> possiblePaths = PathFinder.findPlacesWithinLimit(blockPermitsUser, volume, source, limitSteps);
		// Just pick one of these destinations at random, of default to standing still.
		int size = possiblePaths.size();
		List<AbsoluteLocation> plannedPath;
		if (size > 0)
		{
			int selection = Math.abs(random.nextInt()) % size;
			// Skip over this many options (we can't really index into this and choosing the "first" would give a hash-order preference).
			AbsoluteLocation target = possiblePaths.keySet().toArray((int arraySize) -> new AbsoluteLocation[arraySize])[selection];
			
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

	private static List<IMutationEntity<IMutableMinimalEntity>> _determineNextSteps(MutableCreature mutable)
	{
		// First, check to see if we are already in our next location.
		AbsoluteLocation thisStep = mutable.newMovementPlan.get(0);
		EntityLocation entityLocation = mutable.newLocation;
		AbsoluteLocation currentLocation = entityLocation.getBlockLocation();
		
		List<IMutationEntity<IMutableMinimalEntity>> changes = null;
		if (currentLocation.equals(thisStep))
		{
			// If we are, that means that we can remove this from the path and plan to move to the next step.
			// Make this path mutable.
			mutable.newMovementPlan = new ArrayList<>(mutable.newMovementPlan);
			mutable.newMovementPlan.remove(0);
			if (mutable.newMovementPlan.isEmpty())
			{
				mutable.newMovementPlan = null;
			}
			else
			{
				AbsoluteLocation nextStep = mutable.newMovementPlan.get(0);
				mutable.newStepsToNextMove = CreatureMovementHelpers.moveToNextLocation(mutable.creature, nextStep);
				if (mutable.newStepsToNextMove.isEmpty())
				{
					// In this case, just allow us to do nothing and reset the timer (should only be empty if falling, for example).
					mutable.newStepsToNextMove = null;
					changes = List.of();
				}
			}
		}
		else
		{
			// Otherwise, check to see if we are currently in the air, since we might just be jumping/falling into the next location.
			if (SpatialHelpers.isBlockAligned(entityLocation.z()))
			{
				// We appear to be on the surface, meaning that we were somehow blocked.  Therefore, clear the plan and we will try again after our idle timer.
				mutable.newMovementPlan = null;
				mutable.newStepsToNextMove = null;
			}
			else
			{
				// Do nothing since we are moving through the air.
			}
		}
		return changes;
	}

	private static List<IMutationEntity<IMutableMinimalEntity>> _scheduleForThisTick(long millisSinceLastTick, MutableCreature mutable)
	{
		// We are already on the way to the next step in our path so see what we can apply in this tick.
		List<IMutationEntity<IMutableMinimalEntity>> changes = new ArrayList<>();
		long millisRemaining = millisSinceLastTick;
		List<IMutationEntity<IMutableMinimalEntity>> remainingSteps = new ArrayList<>();
		boolean canAdd = true;
		for (IMutationEntity<IMutableMinimalEntity> check : mutable.newStepsToNextMove)
		{
			if (canAdd)
			{
				long timeCostMillis = check.getTimeCostMillis();
				// This must be able to fit into a single tick.
				Assert.assertTrue(timeCostMillis <= millisSinceLastTick);
				if (timeCostMillis <= millisRemaining)
				{
					changes.add(check);
					millisRemaining -= timeCostMillis;
				}
				else
				{
					// We have done all we can.
					canAdd = false;
				}
			}
			if (!canAdd)
			{
				remainingSteps.add(check);
			}
		}
		
		// We must have at least done something.
		Assert.assertTrue(!changes.isEmpty());
		if (remainingSteps.isEmpty())
		{
			mutable.newStepsToNextMove = null;
		}
		else
		{
			mutable.newStepsToNextMove = remainingSteps;
		}
		return changes;
	}


	public static record CreatureGroup(int committedMutationCount
			// Note that we will only pass back a new Entity object if it changed.
			, Map<Integer, CreatureEntity> updatedCreatures
			, List<Integer> deadCreatureIds
	) {}
}
