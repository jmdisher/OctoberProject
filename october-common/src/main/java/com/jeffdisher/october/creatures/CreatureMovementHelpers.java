package com.jeffdisher.october.creatures;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.jeffdisher.october.actions.EntityActionSimpleMove;
import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.EntityMovementHelpers;
import com.jeffdisher.october.logic.ViscosityReader;
import com.jeffdisher.october.logic.OrientationHelpers;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.pathing.PathFinder;
import com.jeffdisher.october.pathing.PathLocation;
import com.jeffdisher.october.pathing.WalkingNavigator;
import com.jeffdisher.october.subactions.EntitySubActionJump;
import com.jeffdisher.october.subactions.EntitySubActionStepUp;
import com.jeffdisher.october.subactions.EntitySubActionSwim;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.FixedRegion;
import com.jeffdisher.october.types.IEntitySubAction;
import com.jeffdisher.october.types.MinimalEntity;
import com.jeffdisher.october.types.MutableCreature;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Helpers related to AI logic around around how creatures move through the world.
 */
public class CreatureMovementHelpers
{
	/**
	 * We use this as a "reasonably close" threshold since we can't reasonably compare against 0.0f in floats.
	 */
	public static final float FLOAT_THRESHOLD = 0.01f;
	/**
	 * How far a creature will look for a target location when performing a random idle movement.
	 */
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
	 * Creates an action to position the creature within its current block such that it can move toward direction in a
	 * later move.
	 * Returns null if such a move would collide or if the creature is already in that location.
	 * 
	 * @param previousBlockLookUp Looks up previous tick's block types.
	 * @param creatureBaseLocation The creature's location.
	 * @param creatureVelocity The creature's velocity.
	 * @param creatureType The type of creature.
	 * @param direction The direction we will eventually take (DOWN if it doesn't matter).
	 * @param millisPerTick The number of milliseconds per tick.
	 * @param intensity The movement intensity.
	 * @return The next move toward direction side of the block (null if the creature is already there, would hit an
	 * obstacle or fall).
	 */
	public static EntityActionSimpleMove<MutableCreature> prepareForMove(TickProcessingContext.IBlockFetcher previousBlockLookUp
		, EntityLocation creatureBaseLocation
		, EntityLocation creatureVelocity
		, EntityType creatureType
		, FacingDirection direction
		, long millisPerTick
		, EntityActionSimpleMove.Intensity intensity
	)
	{
		PathLocation pathBase = PathLocation.forEntityBase(creatureBaseLocation);
		float width = creatureType.volume().width();
		EntityLocation targetBase;
		
		if ((FacingDirection.UP == direction) || (FacingDirection.DOWN == direction))
		{
			// In these cases, we just want to make sure that the creature first within the block.
			// This is somewhat tricky since some creatures can be multiple PathLocations in width and we just want to
			// make sure that we fit into the base block and spill into as few other PathLocation blocks.
			float toSubtract = width - PathLocation.PATH_UNIT * (float)Math.floor(width / PathLocation.PATH_UNIT);
			
			EntityLocation blockBase = pathBase.getBaseLocation();
			float maxX = blockBase.x() + PathLocation.PATH_UNIT - FLOAT_THRESHOLD - toSubtract;
			float maxY = blockBase.y() + PathLocation.PATH_UNIT - FLOAT_THRESHOLD - toSubtract;
			
			float x = creatureBaseLocation.x();
			if (x > maxX)
			{
				x = maxX;
			}
			float y = creatureBaseLocation.y();
			if (y > maxY)
			{
				y = maxY;
			}
			float z = creatureBaseLocation.z();
			targetBase = new EntityLocation(x, y, z);
		}
		else
		{
			targetBase = _findStepLocation(creatureBaseLocation
				, width
				, pathBase
				, direction
			);
		}
		
		// We want to allow the creature to "slip off" the ledge if they are trying to fall down.
		boolean groundConsistent = (FacingDirection.DOWN != direction);
		return _moveAlongDiagonalPath(previousBlockLookUp
			, creatureBaseLocation
			, creatureVelocity
			, creatureType
			, targetBase
			, millisPerTick
			, intensity
			, null
			, groundConsistent
		);
	}

	/**
	 * Creates the changes required for the given creature to move to targetBlock.
	 * 
	 * @param previousBlockLookUp Looks up previous tick's block types.
	 * @param creatureLocation The creature's location.
	 * @param creatureVelocity The creature's velocity.
	 * @param creatureType The type of creature.
	 * @param targetBlock The target location.
	 * @param nextTargetBlock The target next after the targetBlock, which could be null.
	 * @param millisPerTick The number of milliseconds per tick.
	 * @param intensity The movement intensity.
	 * @return The next move toward targetBlock (null if there is no useful action at this time - usually just pass
	 * time).
	 */
	public static EntityActionSimpleMove<MutableCreature> moveToNextLocation(TickProcessingContext.IBlockFetcher previousBlockLookUp
		, EntityLocation creatureLocation
		, EntityLocation creatureVelocity
		, EntityType creatureType
		, PathLocation targetBlock
		, PathLocation nextTargetBlock
		, long millisPerTick
		, EntityActionSimpleMove.Intensity intensity
	)
	{
		EntityVolume volume = creatureType.volume();
		ViscosityReader supplier = new ViscosityReader(Environment.getShared(), previousBlockLookUp);
		float viscosityFraction = supplier.getMaxStillViscosityInVolume(creatureLocation, volume);
		boolean isBlockSwimmable = (viscosityFraction >= BlockAspect.VISOSITY_FLOAT_SWIMMABLE) && (viscosityFraction < BlockAspect.VISOSITY_FLOAT_SOLID);
		
		PathLocation starting = PathLocation.forEntityBase(creatureLocation);
		FacingDirection direction = PathLocation.adjacentDirection(starting, targetBlock);
		
		// The complicated cases here are related to the cases where we are moving up into the target block.
		// This could be a jump, could be swimming, or could be part of a step up into a partial block.
		boolean isJump = false;
		boolean isSwim = false;
		EntityLocation stepUpTarget = null;
		EntitySubActionStepUp<MutableCreature> stepUpSubAction = null;
		if (FacingDirection.UP == direction)
		{
			// This is one of these special-cases so determine which one.
			if (null != nextTargetBlock)
			{
				// Check the next step to see if we are actually going up a full block or just stepping up.
				FacingDirection nextDirection = PathLocation.adjacentDirection(targetBlock, nextTargetBlock);
				if (FacingDirection.UP == nextDirection)
				{
					// This is a jump case (since we are moving 2 steps up).
					isJump = true;
				}
				else
				{
					// This is something else so it is a step.
					// Note that this "realTargetLocation" calculation isn't quite right but needs to be generalized with other calls here, anyway.
					float width = volume.width();
					stepUpTarget = _findStepLocation(creatureLocation, width, nextTargetBlock, nextDirection);
					float deltaX = stepUpTarget.x() - creatureLocation.x();
					float deltaY = stepUpTarget.y() - creatureLocation.y();
					float xToMove = _findRawMoveInTime(creatureType.blocksPerSecond() * intensity.speedMultipler, viscosityFraction, deltaX, creatureVelocity.x(), millisPerTick);
					float yToMove = _findRawMoveInTime(creatureType.blocksPerSecond() * intensity.speedMultipler, viscosityFraction, deltaY, creatureVelocity.y(), millisPerTick);
					EntityLocation realTargetLocation = creatureLocation.getRelative(xToMove, yToMove, EntitySubActionStepUp.MAX_STEP_HEIGHT);
					
					if (supplier.isSolidBlockInVolume(realTargetLocation.getRelative(0.0f, 0.0f, -0.1f), new EntityVolume(0.01f, width), true))
					{
						stepUpSubAction = EntitySubActionStepUp.buildStepUpWithReader(supplier, creatureLocation, volume, deltaX, deltaY);
					}
					else
					{
						// We aren't yet close enough to step up here so continue walking in this direction.
						stepUpTarget = null;
						direction = nextDirection;
					}
				}
			}
			else
			{
				// If there is no other location, just try to jump.
				isJump = true;
			}
			
			// If we want to jump, make sure that we can.
			if (isJump && !EntitySubActionJump.canJumpWithReader(supplier, creatureLocation, volume, creatureVelocity))
			{
				isJump = false;
			}
			
			// If not jumping or stepping up, see if we can swim.
			if (!isJump && (null == stepUpSubAction) && isBlockSwimmable && (creatureVelocity.z() <= 0.0f))
			{
				isSwim = true;
			}
		}
		
		EntityActionSimpleMove<MutableCreature> change;
		if (isJump)
		{
			EntityLocation targetBase = targetBlock.getBaseLocation();
			byte yaw = OrientationHelpers.getYawBetweenPoints(creatureLocation, targetBase);
			byte pitch = 0;
			IEntitySubAction<MutableCreature> subAction = new EntitySubActionJump<>();
			change = new EntityActionSimpleMove<>(0.0f
				, 0.0f
				, EntityActionSimpleMove.Intensity.STANDING
				, yaw
				, pitch
				, subAction
			);
		}
		else if (isSwim)
		{
			EntityLocation targetBase = targetBlock.getBaseLocation();
			byte yaw = OrientationHelpers.getYawBetweenPoints(creatureLocation, targetBase);
			byte pitch = 0;
			IEntitySubAction<MutableCreature> subAction = new EntitySubActionSwim<>();
			change = new EntityActionSimpleMove<>(0.0f
				, 0.0f
				, EntityActionSimpleMove.Intensity.STANDING
				, yaw
				, pitch
				, subAction
			);
		}
		else if (null != stepUpSubAction)
		{
			// We want to try to move with a "step up" to the next block.
			float deltaX = stepUpTarget.x() - creatureLocation.x();
			float deltaY = stepUpTarget.y() - creatureLocation.y();
			float distanceX = Math.abs(deltaX);
			float distanceY = Math.abs(deltaY);
			change = _buildStep(creatureLocation, creatureVelocity, creatureType, millisPerTick, viscosityFraction, intensity, stepUpTarget, distanceX, distanceY, stepUpSubAction);
		}
		else if (FacingDirection.DOWN == direction)
		{
			// The only thing we need to check here is that we aren't "hanging on the lip" of a neighbouring block.
			if (supplier.isSolidBlockInVolume(creatureLocation.getRelative(0.0f, 0.0f, -0.1f), new EntityVolume(0.01f, volume.width()), true))
			{
				// We will try to move toward the west-south corner.
				EntityLocation stepLocation = creatureLocation.getBlockLocation().toEntityLocation();
				float distanceX = Math.abs(stepLocation.x() - creatureLocation.x());
				float distanceY = Math.abs(stepLocation.y() - creatureLocation.y());
				change = _buildStep(creatureLocation, creatureVelocity, creatureType, millisPerTick, viscosityFraction, intensity, stepLocation, distanceX, distanceY, null);
			}
			else
			{
				// We will fall-through and handle this as a normal "do nothing" action.
				change = null;
			}
		}
		else if (FacingDirection.UP == direction)
		{
			// We didn't handle any special "up" action so we must be needing to continue to "coast" so make this "do nothing".
			change = null;
		}
		else
		{
			// This is a normal horizontal move.
			float width = volume.width();
			EntityLocation stepLocation = _findStepLocation(creatureLocation, width, targetBlock, direction);
			float distanceX = Math.abs(stepLocation.x() - creatureLocation.x());
			float distanceY = Math.abs(stepLocation.y() - creatureLocation.y());
			change = _buildStep(creatureLocation, creatureVelocity, creatureType, millisPerTick, viscosityFraction, intensity, stepLocation, distanceX, distanceY, null);
		}
		
		return change;
	}

	/**
	 * Finds an EntityLocation on the same z-level as the current creatureLocation which is somewhere along the path
	 * described by fullPath.  The returned location will be as far along the fullPath as the creature could walk
	 * without needing to walk around a hole or barrier.
	 * 
	 * @param supplier Looks up the viscosity of various blocks.
	 * @param creatureLocation The creature's location.
	 * @param creatureType The type of creature.
	 * @param fullPath The full planned path (cannot be null but can be empty).
	 * @return A direct target location along the fullPath, null if the fullPath is empty, none of the path is on the
	 * creatureLocation's z-level, or none of the blocks on the z-level are on solid blocks, a non-colliding location
	 * was not found.
	 */
	public static EntityLocation findDirectTarget(ViscosityReader supplier
		, EntityLocation creatureLocation
		, EntityType creatureType
		, List<PathLocation> fullPath
	)
	{
		// We just want to find the furthest-away block on the same z-level.
		PathLocation startBlock = PathLocation.forEntityBase(creatureLocation);
		PathLocation prev = startBlock;
		PathLocation furthestBlock = null;
		boolean canSelect = false;
		for (PathLocation next : fullPath)
		{
			FacingDirection direction = PathLocation.adjacentDirection(prev, next);
			if ((FacingDirection.DOWN == direction) || (FacingDirection.UP == direction))
			{
				break;
			}
			else
			{
				// Check that the block under this is solid.
				if (supplier.isSolidBlockInVolume(next.getRelative(0, 0, -1).getBaseLocation(), new EntityVolume(0.01f, 0.01f), true))
				{
					// We want to avoid the first block, since that would mean discarding the entire plan for potentially only half a step.
					if (canSelect)
					{
						furthestBlock = next;
					}
					else
					{
						canSelect = true;
					}
				}
				else
				{
					break;
				}
			}
			prev = next;
		}
		
		EntityLocation directTarget;
		if (null != furthestBlock)
		{
			// For now, just keep us where we are in the block and walk to the new block.
			EntityVolume volume = creatureType.volume();
			// (we recreate the start so we can determine the distance as a whole number)
			EntityLocation entityStart = startBlock.getBaseLocation();
			EntityLocation entityEnd = furthestBlock.getBaseLocation();
			float distanceX = entityEnd.x() - entityStart.x();
			float distanceY = entityEnd.y() - entityStart.y();
			EntityLocation totalDistanceToMove = new EntityLocation(distanceX, distanceY, 0.0f);
			
			// Run the move (helper.result is null if colliding).
			_NoCollideHelper helper = new _NoCollideHelper(supplier);
			EntityMovementHelpers.interactiveEntityMove(creatureLocation, volume, totalDistanceToMove, helper);
			directTarget = helper.result;
		}
		else
		{
			// No target.
			directTarget = null;
		}
		return directTarget;
	}

	/**
	 * Creates an action to move a creature from creatureBaseLocation to targetBase as a direct, typically diagonal,
	 * move.
	 * 
	 * @param previousBlockLookUp Looks up previous tick's block types.
	 * @param creatureBaseLocation The creature's location.
	 * @param creatureType The type of creature.
	 * @param targetBase The target location.
	 * @param millisPerTick The number of milliseconds per tick.
	 * @param intensity The movement intensity.
	 * @return The next move toward targetBase (null if the creature is already there, would hit an obstacle or fall).
	 */
	public static EntityActionSimpleMove<MutableCreature> moveAlongDiagonalPath(TickProcessingContext.IBlockFetcher previousBlockLookUp
		, EntityLocation creatureBaseLocation
		, EntityType creatureType
		, EntityLocation targetBase
		, long millisPerTick
		, EntityActionSimpleMove.Intensity intensity
	)
	{
		// TODO:  Plumb in the starting velocity once it can be something interesting in this case (always 0, for now).
		EntityLocation creatureVelocity = new EntityLocation(0.0f, 0.0f, 0.0f);
		// In this case, we always want to stay on the ground if we are already there.
		boolean groundConsistent = true;
		return _moveAlongDiagonalPath(previousBlockLookUp
			, creatureBaseLocation
			, creatureVelocity
			, creatureType
			, targetBase
			, millisPerTick
			, intensity
			, null
			, groundConsistent
		);
	}

	public static List<PathLocation> findPathToRandomSpot(TickProcessingContext context
		, WalkingNavigator navigator
		, EntityLocation creatureLocation
	)
	{
		Map<PathLocation, PathLocation> possiblePaths = PathFinder.findPlacesWithinLimit(navigator
			, creatureLocation
		);
		// We will only choose paths which don't end in air above air (jumping into the air for no reason) or in water (since none of our creatures can breathe under water).
		PathLocation currentPath = PathLocation.forEntityBase(creatureLocation);
		List<PathLocation> goodTargets1 = new ArrayList<>();
		for (Map.Entry<PathLocation, PathLocation> elt : possiblePaths.entrySet())
		{
			PathLocation end = elt.getKey();
			float endViscosity = navigator.getViscosityAtBase(end.getBaseLocation());
			boolean shouldInclude = true;
			if (endViscosity >= BlockAspect.VISOSITY_FLOAT_SWIMMABLE)
			{
				// This path ends in water so don't include it.
				shouldInclude = false;
			}
			else if (currentPath.equals(end))
			{
				// We don't want to end where we started.
				shouldInclude = false;
			}
			else
			{
				PathLocation under = end.getRelative(0, 0, -1);
				float underViscosity = navigator.getViscosityAtBase(under.getBaseLocation());
				if (underViscosity < BlockAspect.VISOSITY_FLOAT_SWIMMABLE)
				{
					// This block is over top of a walkable block (meaning it was a jump) so ignore it.
					shouldInclude = false;
				}
			}
			if (shouldInclude)
			{
				goodTargets1.add(end);
			}
		}
		
		// Strip out any of the ending positions which we don't want.
		List<PathLocation> goodTargets = goodTargets1;
		
		// Just pick one of these destinations at random, or default to standing still.
		int size = goodTargets.size();
		List<PathLocation> plannedPath;
		if (size > 0)
		{
			int selection = context.randomInt.applyAsInt(size);
			// Skip over this many options (we can't really index into this and choosing the "first" would give a hash-order preference).
			PathLocation target = goodTargets.get(selection);
			
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

	public static void validateNextStepValid(TickProcessingContext context
		, MutableCreature mutable
		, List<PathLocation> fullPlan
	)
	{
		// Invalidate:  Verify that we are in range of the next step and it isn't solid.
		PathLocation thisStep = fullPlan.get(0);
		PathLocation pathBase = PathLocation.forEntityBase(mutable.newLocation);
		
		// We might not yet have noticed that we have moved into the next step so skip this check in that case.
		if (!pathBase.equals(thisStep))
		{
			int distanceToNext = PathLocation.pathStepDistance(pathBase, thisStep);
			boolean isAdjacent = (1 == distanceToNext);
			if (isAdjacent)
			{
				// Just make sure we can move into this next step.
				ViscosityReader reader = new ViscosityReader(Environment.getShared(), context.previousBlockLookUp);
				float nextViscosity = reader.getMaxStillViscosityInVolume(thisStep.getBaseLocation(), mutable.newType.volume());
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

	public static void updateTargetEntityPlan(TickProcessingContext context
		, MutableCreature mutable
		, int targetEntityId
		, EntityLocation targetPreviousLocation
	)
	{
		MinimalEntity targetEntity = context.previousEntityLookUp.getById(targetEntityId);
		
		// The target validity was checked in the invalidate path so this is valid.
		Assert.assertTrue(null != targetEntity);
		
		if (!targetEntity.location().equals(targetPreviousLocation))
		{
			EntityType type = mutable.getType();
			EntityLocation currentLocation = mutable.getLocation();
			
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
				// We want to strip away the first step, since it is the current location.
				path.remove(0);
				
				// We need to store a movement plan whether this path is reduced to nothing, or not.
				CreatureEntity.IMovementPlan plan;
				if (path.size() > 0)
				{
					ViscosityReader reader = new ViscosityReader(Environment.getShared(), context.previousBlockLookUp);
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

	public static void updateDirectTargetPlan(TickProcessingContext context
		, MutableCreature mutable
		, int targetEntityId
	)
	{
		EntityType type = mutable.getType();
		EntityLocation currentLocation = mutable.getLocation();
		
		// We have a target so see if they are within action range.
		MinimalEntity targetEntity = context.previousEntityLookUp.getById(targetEntityId);
		
		// Note that we already checked that this target is valid in an earlier check.
		Assert.assertTrue(null != targetEntity);
		
		EntityLocation sourceEyeLocation = SpatialHelpers.getEyeLocation(currentLocation, type.volume());
		FixedRegion region = FixedRegion.fromMinimal(targetEntity);
		float distance = SpatialHelpers.distanceFromLocationToRegion(sourceEyeLocation, region);
		float actionDistance = type.actionDistance();
		
		if (distance < actionDistance)
		{
			// We can drop the direct location and just keep them in-range.
			mutable.movementPlan = new NearToTarget(targetEntityId, targetEntity.location());
		}
		else
		{
			// We are still approaching them so change nothing.
		}
	}

	/**
	 * Finds an appropriate target location for a creature starting at creatureLocation, of width size, wanting to end
	 * up in targetBlock location, prepared to continue in direction from there.
	 * NOTE:  If the creature is already in direction from targetBlock, it will not move, meaning it will not end up in
	 * targetBlock (as this move will be deemed redundant).
	 * 
	 * @param creatureLocation The starting location of the creature.
	 * @param width The width of the creature.
	 * @param targetBlock The block the creature needs to end up in.
	 * @param direction The direction the creature should be oriented to leave targetBlock, later (MUST be a flat
	 * direction:  WEST, EAST, SOUTH, or NORTH).
	 * @return A reasonable location for the creature to reach, in targetBlock unless already located in the requested
	 * direction from targetBlock.
	 */
	public static EntityLocation findStepLocation(EntityLocation creatureLocation
		, float width
		, PathLocation targetBlock
		, FacingDirection direction
	)
	{
		return _findStepLocation(creatureLocation, width, targetBlock, direction);
	}


	private static EntityActionSimpleMove<MutableCreature> _moveByX(EntityLocation location, long timeLimitMillis, float creatureSpeed, EntityActionSimpleMove.Intensity intensity, float viscosityFraction, float targetX, float passiveVelocityX, EntitySubActionStepUp<MutableCreature> subAction)
	{
		// NOTE:  This call assumes that moving in X is possible (on solid ground or swimming).
		float moveX = targetX - location.x();
		float absoluteMove = Math.abs(moveX);
		EntityActionSimpleMove<MutableCreature> move = null;
		if (absoluteMove > FLOAT_THRESHOLD)
		{
			// Note that we use viscosity to estimate how far we will move but the actual units of movement are just in terms of our basic speed.
			float distanceToMove = _findRawMoveInTime(creatureSpeed * intensity.speedMultipler, viscosityFraction, moveX, passiveVelocityX, timeLimitMillis);
			
			move = new EntityActionSimpleMove<>(distanceToMove
				, 0.0f
				, intensity
				, (moveX > 0.0f) ? OrientationHelpers.YAW_EAST : OrientationHelpers.YAW_WEST
				, OrientationHelpers.PITCH_FLAT
				, subAction
			);
		}
		return move;
	}

	private static EntityActionSimpleMove<MutableCreature> _moveByY(EntityLocation location, long timeLimitMillis, float creatureSpeed, EntityActionSimpleMove.Intensity intensity, float viscosityFraction, float targetY, float passiveVelocityY, EntitySubActionStepUp<MutableCreature> subAction)
	{
		// NOTE:  This call assumes that moving in Y is possible (on solid ground or swimming).
		float moveY = targetY - location.y();
		float absoluteMove = Math.abs(moveY);
		EntityActionSimpleMove<MutableCreature> move = null;
		if (absoluteMove > FLOAT_THRESHOLD)
		{
			// Note that we use viscosity to estimate how far we will move but the actual units of movement are just in terms of our basic speed.
			float distanceToMove = _findRawMoveInTime(creatureSpeed * intensity.speedMultipler, viscosityFraction, moveY, passiveVelocityY, timeLimitMillis);
			
			move = new EntityActionSimpleMove<>(0.0f
				, distanceToMove
				, intensity
				, (moveY > 0.0f) ? OrientationHelpers.YAW_NORTH : OrientationHelpers.YAW_SOUTH
				, OrientationHelpers.PITCH_FLAT
				, subAction
			);
		}
		return move;
	}

	private static float _findRawMoveInTime(float currentCreatureSpeed
		, float viscosityFraction
		, float move
		, float passiveVelocity
		, long timeLimitMillis
	)
	{
		float inverseViscosity = (1.0f - viscosityFraction);
		float effectiveSpeed = inverseViscosity * currentCreatureSpeed;
		
		float seconds = (float)timeLimitMillis / 1000.0f;
		float effectivePassiveMovement = inverseViscosity * passiveVelocity * seconds;
		float movementSum = move - effectivePassiveMovement;
		float absoluteMove = Math.abs(movementSum);
		float maxDistanceInTime = effectiveSpeed * seconds;
		float distanceInTime = Math.min(absoluteMove, maxDistanceInTime);
		float fractionToMove = distanceInTime / maxDistanceInTime;
		
		float rawDistanceInTime = currentCreatureSpeed * seconds;
		float distanceToMove = rawDistanceInTime * fractionToMove;
		float activeDistance = Math.signum(movementSum) * distanceToMove;
		return activeDistance;
	}

	private static EntityActionSimpleMove<MutableCreature> _buildStep(EntityLocation creatureLocation
		, EntityLocation creatureVelocity
		, EntityType creatureType
		, long timeLimitMillis
		, float viscosityFraction
		, EntityActionSimpleMove.Intensity intensity
		, EntityLocation stepLocation
		, float distanceX
		, float distanceY
		, EntitySubActionStepUp<MutableCreature> subAction
	)
	{
		EntityActionSimpleMove<MutableCreature> change;
		// We don't want to walk diagonally so just see which is the largest distance.
		float maxHorizontal = Math.max(distanceX, distanceY);
		if (maxHorizontal > FLOAT_THRESHOLD)
		{
			// We need to move horizontally so figure out which way.
			float speed = creatureType.blocksPerSecond();
			if (maxHorizontal == distanceX)
			{
				change = _moveByX(creatureLocation, timeLimitMillis, speed, intensity, viscosityFraction, stepLocation.x(), creatureVelocity.x(), subAction);
			}
			else
			{
				change = _moveByY(creatureLocation, timeLimitMillis, speed, intensity, viscosityFraction, stepLocation.y(), creatureVelocity.y(), subAction);
			}
		}
		else
		{
			// We don't need horizontal movement so just do nothing.
			change = null;
		}
		return change;
	}

	private static EntityLocation _findStepLocation(EntityLocation creatureLocation
		, float width
		, PathLocation targetBlock
		, FacingDirection direction
	)
	{
		// Note that we will align our X/Y location with the direction we want to exit the block on the matching axis
		// but the step base or existing location, depending on if this is turnnig a corner, or not.
		EntityLocation targetBase = targetBlock.getBaseLocation();
		PathLocation creatureStep = PathLocation.forEntityBase(creatureLocation);
		int xDelta = PathLocation.adjacentStepsX(creatureStep, targetBlock);
		int yDelta = PathLocation.adjacentStepsY(creatureStep, targetBlock);
		
		float stepX;
		float stepY;
		if (FacingDirection.EAST == direction)
		{
			float toSubtract = width - PathLocation.PATH_UNIT * (float)Math.floor(width / PathLocation.PATH_UNIT);
			stepX = Math.max(creatureLocation.x(), targetBase.x() + PathLocation.PATH_UNIT - FLOAT_THRESHOLD - toSubtract);
			if (0 != yDelta)
			{
				stepY = targetBase.y();
			}
			else
			{
				stepY = creatureLocation.y();
			}
		}
		else if (FacingDirection.WEST == direction)
		{
			stepX = Math.min(creatureLocation.x(), targetBase.x() + FLOAT_THRESHOLD);
			if (0 != yDelta)
			{
				stepY = targetBase.y();
			}
			else
			{
				stepY = creatureLocation.y();
			}
		}
		else if (FacingDirection.NORTH == direction)
		{
			if (0 != xDelta)
			{
				stepX = targetBase.x();
			}
			else
			{
				stepX = creatureLocation.x();
			}
			float toSubtract = width - PathLocation.PATH_UNIT * (float)Math.floor(width / PathLocation.PATH_UNIT);
			stepY = Math.max(creatureLocation.y(), targetBase.y() + PathLocation.PATH_UNIT - FLOAT_THRESHOLD - toSubtract);
		}
		else if (FacingDirection.SOUTH == direction)
		{
			if (0 != xDelta)
			{
				stepX = targetBase.x();
			}
			else
			{
				stepX = creatureLocation.x();
			}
			stepY = Math.min(creatureLocation.y(), targetBase.y() + FLOAT_THRESHOLD);
		}
		else
		{
			throw Assert.unreachable();
		}
		float stepZ = targetBase.z();
		EntityLocation stepLocation = new EntityLocation(stepX, stepY, stepZ);
		return stepLocation;
	}

	private static EntityActionSimpleMove<MutableCreature> _moveAlongDiagonalPath(TickProcessingContext.IBlockFetcher previousBlockLookUp
		, EntityLocation creatureBaseLocation
		, EntityLocation creatureVelocity
		, EntityType creatureType
		, EntityLocation targetBase
		, long millisPerTick
		, EntityActionSimpleMove.Intensity intensity
		, IEntitySubAction<MutableCreature> subAction
		, boolean groundConsistent
	)
	{
		EntityActionSimpleMove<MutableCreature> change;
		if (creatureBaseLocation.equals(targetBase))
		{
			// If we already reached the destination, just return null.
			change = null;
		}
		else
		{
			// We assume that our current location and target location are on the same Z-level so verify that.
			Assert.assertTrue(0 == PathLocation.adjacentStepsZ(PathLocation.forEntityBase(creatureBaseLocation), PathLocation.forEntityBase(targetBase)));
			
			Environment env = Environment.getShared();
			ViscosityReader supplier = new ViscosityReader(env, previousBlockLookUp);
			EntityVolume volume = creatureType.volume();
			float viscosityFraction = supplier.getMaxStillViscosityInVolume(creatureBaseLocation, volume);
			
			EntityLocation environmentalVelocity = EntityMovementHelpers.getEnvironmentalVector(env, previousBlockLookUp, creatureBaseLocation, volume);
			float walkingBlocksPerSecond = creatureType.blocksPerSecond();
			change = EntityActionSimpleMove.moveTowardTarget(creatureBaseLocation
				, creatureVelocity
				, environmentalVelocity
				, viscosityFraction
				, targetBase
				, walkingBlocksPerSecond
				, intensity
				, millisPerTick
				, subAction
			);
			
			// Test if this action would actually apply, given what we know about the world.
			TickProcessingContext fakeContext = new TickProcessingContext(0L
				, previousBlockLookUp
				, null
				, null
				, null
				, null
				, null
				, null
				, null
				, null
				, null
				, null
				, null
				, null
				, millisPerTick
				, 0L
			);
			MutableCreature fakeEntity = MutableCreature.existing(CreatureEntity.create(-1, creatureType, creatureBaseLocation, 0L));
			fakeEntity.newVelocity = creatureVelocity;
			EntityMovementHelpers.HighLevelMovementResult result = change.applyChangeForResult(fakeContext, fakeEntity);
			if (null != result)
			{
				if (result.horizontalCollision())
				{
					result = null;
				}
			}
			if (groundConsistent && (null != result))
			{
				// We only allow this if we end standing on the ground.
				boolean mustEndOnGround = SpatialHelpers.isStandingOnGround(supplier, creatureBaseLocation, volume);
				if (mustEndOnGround && !SpatialHelpers.isStandingOnGround(supplier, result.location(), volume))
				{
					result = null;
				}
			}
			
			if (null == result)
			{
				// We hit something so we want to fail out.
				change = null;
			}
		}
		return change;
	}


	private static class _NoCollideHelper implements EntityMovementHelpers.IInteractiveHelper
	{
		private final ViscosityReader _reader;
		public EntityLocation result;
		
		public _NoCollideHelper(ViscosityReader reader)
		{
			_reader = reader;
		}
		@Override
		public void setLocationAndCancelVelocity(EntityLocation finalLocation, boolean cancelX, boolean cancelY, boolean cancelZ)
		{
			// We only store the result if there were no collisions.
			if (!cancelX && !cancelY && !cancelZ)
			{
				this.result = finalLocation;
			}
		}
		@Override
		public boolean isSolid(EntityLocation base, EntityVolume volume, boolean fromAbove)
		{
			return _reader.isSolidBlockInVolume(base, volume, fromAbove);
		}
	}
}
