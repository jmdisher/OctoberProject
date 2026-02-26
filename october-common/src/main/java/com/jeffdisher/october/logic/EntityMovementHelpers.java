package com.jeffdisher.october.logic;

import java.util.function.Predicate;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.LiquidRegistry;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Helper class containing the common logic around entity location/velocity and how they are updated with time and
 * validated against the impassable blocks in the environment.
 */
public class EntityMovementHelpers
{
	/**
	 * We apply a fudge factor to magnify the impact of the drag on vertical velocity.  This is done in order to make
	 * the drag behaviour "feel" right while the movement system is redesigned.
	 * TODO:  Remove this once the movement system and viscosity values are redone.
	 */
	public static final float DRAG_FUDGE_FACTOR = 10.0f;
	/**
	 * We back off from a "positive edge" using this fudge factor so that we don't get stuck "in" a block we are
	 * actually right up against.
	 */
	public static final float POSITIVE_EDGE_COLLISION_FUDGE_FACTOR = 0.01f;
	/**
	 * We will use standard Earth gravity -9.8 ms^2.
	 */
	public static final float GRAVITY_CHANGE_PER_SECOND = -9.8f;
	/**
	 * The terminal velocity when falling through air.
	 * (40 m/s seems to be a common free-fall velocity for sky-divers)
	 */
	public static final float AIR_TERMINAL_VELOCITY_PER_SECOND = 40.0f;
	/**
	 * The terminal velocity when falling through air.
	 * (40 m/s seems to be a common free-fall velocity for sky-divers)
	 */
	public static final float FALLING_TERMINAL_VELOCITY_PER_SECOND = -AIR_TERMINAL_VELOCITY_PER_SECOND;
	/**
	 * Just a helpful constant.
	 */
	public static final float FLOAT_MILLIS_PER_SECOND = 1000.0f;
	/**
	 * The velocity added by a block in the environment.
	 */
	public static final float FLOW_VELOCITY_ADDED = 0.5f;
	/**
	 * The maximum magnitude of flow to add due to water.
	 */
	public static final float MAX_FLOW_VELOCITY_MAGNITUDE = 2.0f;

	/**
	 * Finds a path from start, along vectorToMove, using the interactive helper.  This will handle collisions with
	 * solid blocks to further constrain this path.  The final result is returned via the interactive helper.
	 * 
	 * @param start The base location of the entity.
	 * @param volume The volume of the entity.
	 * @param vectorToMove The direction the entity is trying to move, in relative coordinates.
	 * @param helper Used for looking up viscosities and setting final state.
	 */
	public static void interactiveEntityMove(EntityLocation start, EntityVolume volume, EntityLocation vectorToMove, IInteractiveHelper helper)
	{
		_interactiveEntityMove(start, volume, vectorToMove, helper);
	}

	/**
	 * Looks up the maximum viscosity of any block occupied by the given volume rooted at entityBase.
	 * 
	 * @param reader A ViscosityReader to interpret blocks.
	 * @param entityBase The base south-west-down corner of the space to check.
	 * @param volume The volume of the space to check.
	 * @return The maximum viscosity of any of the blocks in the requested space.
	 */
	public static float maxViscosityInEntityBlocks(ViscosityReader reader, EntityLocation entityBase, EntityVolume volume)
	{
		IViscosityLookup helper = (AbsoluteLocation location, boolean fromAbove) -> reader.getViscosityFraction(location, fromAbove);
		
		// In this case, we are just check where we stand, not falling.
		boolean fromAbove = false;
		return _maxViscosityInEntityBlocks(entityBase, volume, helper, fromAbove);
	}

	/**
	 * Used to check if the entity with volume at entityBase is intersecting a specific type of block based on
	 * caller-defined criteria.
	 * 
	 * @param entityBase The base south-west-down corner of the space to check.
	 * @param volume The volume of the space to check.
	 * @param predicate Returns true if the function should this location.
	 * @return The first location where the predicate returned true, or null if it never did.
	 */
	public static AbsoluteLocation checkTypeIntersection(EntityLocation entityBase, EntityVolume volume, Predicate<AbsoluteLocation> predicate)
	{
		VolumeIterator iterator = new VolumeIterator(entityBase, volume);
		
		AbsoluteLocation match = null;
		for (AbsoluteLocation loc : iterator)
		{
			if (predicate.test(loc))
			{
				match = loc;
				break;
			}
		}
		return match;
	}

	/**
	 * The common movement idiom, extracted from EntityActionSimpleMove in order to allow reuse and improve test
	 * coverage.
	 * Given a start location and velocity, plus explicit movement on top of that, this helper computes the final
	 * location and velocity.
	 * 
	 * @param reader A ViscosityReader to interpret blocks.
	 * @param startLocation The starting base location of the entity.
	 * @param startVelocity The starting velocity of the entity.
	 * @param volume The volume of the entity.
	 * @param activeXMovement Absolute active movement in the X direction (in total blocks travelled in seconds).
	 * @param activeYMovement Absolute active movement in the Y direction (in total blocks travelled in seconds).
	 * @param maxVelocityPerSecond The maximum velocity of the entity, accounting for how intensely it is moving.
	 * @param seconds The number of seconds elapsed while the active movement was applied.
	 * @return The final location and components of velocity.
	 */
	public static HighLevelMovementResult commonMovementIdiom(ViscosityReader reader
		, EntityLocation startLocation
		, EntityLocation startVelocity
		, EntityVolume volume
		, float activeXMovement
		, float activeYMovement
		, float maxVelocityPerSecond
		, float seconds
	)
	{
		// In this case, we are just check where we stand, not falling.
		IViscosityLookup viscosityLookup = (AbsoluteLocation location, boolean fromAbove) -> reader.getViscosityFraction(location, fromAbove);
		boolean fromAbove = false;
		float startViscosity = _maxViscosityInEntityBlocks(startLocation, volume, viscosityLookup, fromAbove);
		EntityLocation effectiveVelocity = _adjustVelocityForMovement(startVelocity, activeXMovement, activeYMovement, maxVelocityPerSecond, seconds, startViscosity);
		
		// Derive the effective movement vector for this action.
		EntityLocation effectiveMovement = new EntityLocation(seconds * effectiveVelocity.x()
			, seconds * effectiveVelocity.y()
			, seconds * effectiveVelocity.z()
		);
		
		// Apply the effective movement to find collisions.
		_IInteractiveHelper helper = new _IInteractiveHelper(reader, effectiveVelocity, effectiveMovement.z());
		_interactiveEntityMove(startLocation, volume, effectiveMovement, helper);
		return helper.result;
	}

	/**
	 * Finds a reasonable location where the entity at location should "pop" to if it is stuck inside a block.  This
	 * accounts for the volume of the entity and the given maxPopOutDistance.  Returns null if not needed or if there is
	 * no valid destination in range.
	 * 
	 * @param reader A ViscosityReader to interpret blocks.
	 * @param location The base location of the entity being checked.
	 * @param volume The volume of the entity being checked.
	 * @param maxPopOutDistance The maximum distance that the entity can move from location to satisfy the request.
	 * @return The new location of the entity or null if it doesn't need to pop out or can't find a valid destination in
	 * range.
	 */
	public static EntityLocation popOutLocation(ViscosityReader reader
		, EntityLocation location
		, EntityVolume volume
		, float maxPopOutDistance
	)
	{
		EntityLocation poppedLocation = null;
		if (!SpatialHelpers.canExistInLocation(reader, location, volume))
		{
			// Find if there is a location in an adjacent block where we could stand.
			// We will prioritize pop-out in this order:  up, down, north, south, east, west.
			if (null == poppedLocation)
			{
				float edgeZ = location.z() + volume.height();
				float upZMove = 1.0f - SpatialHelpers.getPositiveFractionalComponent(location.z());
				float downZMove = SpatialHelpers.getPositiveFractionalComponent(edgeZ) + EntityMovementHelpers.POSITIVE_EDGE_COLLISION_FUDGE_FACTOR;
				if (upZMove <= maxPopOutDistance)
				{
					// Up is in range but does it work?
					EntityLocation newLocation = new EntityLocation(location.x(), location.y(), location.z() + upZMove);
					if (SpatialHelpers.canExistInLocation(reader, newLocation, volume))
					{
						poppedLocation = newLocation;
					}
				}
				if ((null == poppedLocation) && (downZMove <= maxPopOutDistance))
				{
					// Down is in range but does it work?
					EntityLocation newLocation = new EntityLocation(location.x(), location.y(), location.z() - downZMove);
					if (SpatialHelpers.canExistInLocation(reader, newLocation, volume))
					{
						poppedLocation = newLocation;
					}
				}
			}
			if (null == poppedLocation)
			{
				float edgeY = location.y() + volume.width();
				float northYMove = 1.0f - SpatialHelpers.getPositiveFractionalComponent(location.y());
				float southYMove = SpatialHelpers.getPositiveFractionalComponent(edgeY) + EntityMovementHelpers.POSITIVE_EDGE_COLLISION_FUDGE_FACTOR;
				if (northYMove <= maxPopOutDistance)
				{
					// Up is in range but does it work?
					EntityLocation newLocation = new EntityLocation(location.x(), location.y() + northYMove, location.z());
					if (SpatialHelpers.canExistInLocation(reader, newLocation, volume))
					{
						poppedLocation = newLocation;
					}
				}
				if ((null == poppedLocation) && (southYMove <= maxPopOutDistance))
				{
					// Down is in range but does it work?
					EntityLocation newLocation = new EntityLocation(location.x(), location.y() - southYMove, location.z());
					if (SpatialHelpers.canExistInLocation(reader, newLocation, volume))
					{
						poppedLocation = newLocation;
					}
				}
			}
			if (null == poppedLocation)
			{
				float edgeX = location.x() + volume.width();
				float eastXMove = 1.0f - SpatialHelpers.getPositiveFractionalComponent(location.x());
				float westXMove = SpatialHelpers.getPositiveFractionalComponent(edgeX) + EntityMovementHelpers.POSITIVE_EDGE_COLLISION_FUDGE_FACTOR;
				if (eastXMove <= maxPopOutDistance)
				{
					// Up is in range but does it work?
					EntityLocation newLocation = new EntityLocation(location.x() + eastXMove, location.y(), location.z());
					if (SpatialHelpers.canExistInLocation(reader, newLocation, volume))
					{
						poppedLocation = newLocation;
					}
				}
				if ((null == poppedLocation) && (westXMove <= maxPopOutDistance))
				{
					// Down is in range but does it work?
					EntityLocation newLocation = new EntityLocation(location.x() - westXMove, location.y(), location.z());
					if (SpatialHelpers.canExistInLocation(reader, newLocation, volume))
					{
						poppedLocation = newLocation;
					}
				}
			}
		}
		return poppedLocation;
	}

	/**
	 * Calculates a vector to add to the velocity of some entity in response to the environment.  This applies to
	 * flowing liquids.
	 * 
	 * @param env The environment.
	 * @param previousBlockLookUp Look-up for previous tick blocks.
	 * @param base The base of the entity.
	 * @param volume The volume of the entity.
	 * @return The velocity vector applied by the environment.
	 */
	public static EntityLocation getEnvironmentalVector(Environment env
		, TickProcessingContext.IBlockFetcher previousBlockLookUp
		, EntityLocation base
		, EntityVolume volume
	)
	{
		// WARNING:  This algorithm is pretty expensive (5x lookups per intersected block) so we might want to re-think
		// this in the future (potentially even storing this flow adjacency as an aspect).
		VolumeIterator iterator = new VolumeIterator(base, volume);
		float x = 0.0f;
		float y = 0.0f;
		float z = 0.0f;
		boolean shouldCompute = false;
		for (AbsoluteLocation location : iterator)
		{
			BlockProxy proxy = previousBlockLookUp.readBlock(location);
			if (null != proxy)
			{
				int strength = env.liquids.getFlowStrength(proxy.getBlock());
				if ((LiquidRegistry.FLOW_WEAK == strength) || (LiquidRegistry.FLOW_STRONG == strength))
				{
					// This has a liquid so see if an adjacent block is "flowing in" to it.
					z += -1.0f * _accumulateFlow(env, previousBlockLookUp, location.getRelative(0, 0, 1), strength);
					y += -1.0f * _accumulateFlow(env, previousBlockLookUp, location.getRelative(0, 1, 0), strength);
					y += 1.0f * _accumulateFlow(env, previousBlockLookUp, location.getRelative(0, -1, 0), strength);
					x += -1.0f * _accumulateFlow(env, previousBlockLookUp, location.getRelative(1, 0, 0), strength);
					x += 1.0f * _accumulateFlow(env, previousBlockLookUp, location.getRelative(-1, 0, 0), strength);
					shouldCompute = true;
				}
			}
		}
		
		EntityLocation flowVelocity;
		if (shouldCompute)
		{
			flowVelocity = new EntityLocation(x, y, z);
			float magnitude = flowVelocity.getMagnitude();
			if (magnitude > MAX_FLOW_VELOCITY_MAGNITUDE)
			{
				flowVelocity = flowVelocity.makeScaledInstance(MAX_FLOW_VELOCITY_MAGNITUDE / magnitude);
			}
		}
		else
		{
			flowVelocity = new EntityLocation(0.0f, 0.0f, 0.0f);
		}
		return flowVelocity;
	}

	/**
	 * Adds together start and saturatingAddend but restricting the final magnitude of each component to
	 * saturatingAddend.
	 * 
	 * @param start The base vector.
	 * @param saturatingAddend The vector to add and use as a saturating limit.
	 * @return The saturated sum of the vectors.
	 */
	public static EntityLocation saturateVectorAddition(EntityLocation start, EntityLocation saturatingAddend)
	{
		float x = _saturateComponentAddition(start.x(), saturatingAddend.x());
		float y = _saturateComponentAddition(start.y(), saturatingAddend.y());
		float z = _saturateComponentAddition(start.z(), saturatingAddend.z());
		return new EntityLocation(x, y, z);
	}


	private static boolean _canOccupyLocation(EntityLocation movingStart, EntityVolume volume, IViscosityLookup helper, boolean fromAbove)
	{
		float maxViscosity = _maxViscosityInEntityBlocks(movingStart, volume, helper, fromAbove);
		boolean canOccupy = (maxViscosity < 1.0f);
		return canOccupy;
	}

	private static float _maxViscosityInEntityBlocks(EntityLocation entityBase, EntityVolume volume, IViscosityLookup helper, boolean fromAbove)
	{
		VolumeIterator iterator = new VolumeIterator(entityBase, volume);
		
		float maxViscosity = 0.0f;
		for (AbsoluteLocation loc : iterator)
		{
			float viscosity = helper.getViscosityForBlockAtLocation(loc, fromAbove);
			maxViscosity = Math.max(maxViscosity, viscosity);
		}
		return maxViscosity;
	}

	private static void _interactiveEntityMoveNotStuck(EntityLocation start, EntityVolume volume, EntityLocation vectorToMove, IInteractiveHelper helper, boolean failZ)
	{
		// We will move in the direction of vectorToMove, biased by viscosity, one block at a time until the move is complete or blocked in all movement directions.
		// Note that, for now at least, we will only compute the viscosity at the beginning of the move, as the move is rarely multiple blocks (falling at terminal velocity being an outlier example).
		
		// Generate the prism of the entity's current location.
		EntityLocation movingStart = start;
		EntityLocation edgeLocation = new EntityLocation(start.x() + volume.width()
			, start.y() + volume.width()
			, start.z() + volume.height()
		);
		Assert.assertTrue(volume.width() == EntityLocation.roundToHundredths(edgeLocation.y() - movingStart.y()));
		
		// We will create effective vector components which can be reduced as we manage collision/movement.
		float effectiveX = vectorToMove.x();
		float effectiveY = vectorToMove.y();
		float effectiveZ = vectorToMove.z();
		
		// We will use a ray-cast from the corner of the prism in the direction of motion.
		float leadingX  = (effectiveX >  0.0f) ? edgeLocation.x() : start.x();
		float leadingY  = (effectiveY >  0.0f) ? edgeLocation.y() : start.y();
		float leadingZ  = (effectiveZ >  0.0f) ? edgeLocation.z() : start.z();
		EntityLocation leadingPoint = new EntityLocation(leadingX, leadingY, leadingZ);
		EntityLocation endOfVector = new EntityLocation(leadingX + effectiveX, leadingY + effectiveY, leadingZ + effectiveZ);
		AbsoluteLocation leadingBlock1 = leadingPoint.getBlockLocation();
		
		// Find the first block intersection of this point moving in the effective vector.
		RayCastHelpers.RayBlock collision = RayCastHelpers.findFirstCollision(leadingPoint, endOfVector, (AbsoluteLocation currentBlock) -> {
			return !leadingBlock1.equals(currentBlock);
		});
		// If we didn't collide, we are done.
		boolean cancelX = false;
		boolean cancelY = false;
		boolean cancelZ = failZ;
		while (null != collision)
		{
			// Move us over by the collision distance, check the block viscosities, and loop.
			float fullVectorLength = (float)Math.sqrt(effectiveX * effectiveX + effectiveY * effectiveY + effectiveZ * effectiveZ);
			float portion = collision.rayDistance() / fullVectorLength;
			float moveX = EntityLocation.roundToHundredths(portion * effectiveX);
			float moveY = EntityLocation.roundToHundredths(portion * effectiveY);
			float moveZ = EntityLocation.roundToHundredths(portion * effectiveZ);
			
			// Adjust any positive movement to keep us within the existing block before we check if we can enter the others.
			if (moveX > 0.0f)
			{
				moveX -= POSITIVE_EDGE_COLLISION_FUDGE_FACTOR;
			}
			if (moveY > 0.0f)
			{
				moveY -= POSITIVE_EDGE_COLLISION_FUDGE_FACTOR;
			}
			if (moveZ > 0.0f)
			{
				moveZ -= POSITIVE_EDGE_COLLISION_FUDGE_FACTOR;
			}
			
			switch (collision.collisionAxis())
			{
			case X: {
				float dirX = (effectiveX > 0.0f) ? POSITIVE_EDGE_COLLISION_FUDGE_FACTOR : -POSITIVE_EDGE_COLLISION_FUDGE_FACTOR;
				EntityLocation adjacent = new EntityLocation(movingStart.x() + moveX + dirX, movingStart.y() + moveY, movingStart.z() + moveZ);
				if (_canOccupyLocation(adjacent, volume, helper, false))
				{
					moveX += dirX;
				}
				else
				{
					effectiveX = 0.0f;
					cancelX = true;
				}
				break;
			}
			case Y: {
				float dirY = (effectiveY > 0.0f) ? POSITIVE_EDGE_COLLISION_FUDGE_FACTOR : -POSITIVE_EDGE_COLLISION_FUDGE_FACTOR;
				EntityLocation adjacent = new EntityLocation(movingStart.x() + moveX, movingStart.y() + moveY + dirY, movingStart.z() + moveZ);
				if (_canOccupyLocation(adjacent, volume, helper, false))
				{
					moveY += dirY;
				}
				else
				{
					effectiveY = 0.0f;
					cancelY = true;
				}
				break;
			}
			case Z: {
				float dirZ = (effectiveZ > 0.0f) ? POSITIVE_EDGE_COLLISION_FUDGE_FACTOR : -POSITIVE_EDGE_COLLISION_FUDGE_FACTOR;
				EntityLocation adjacent = new EntityLocation(movingStart.x() + moveX, movingStart.y() + moveY, movingStart.z() + moveZ + dirZ);
				boolean fromAbove = (dirZ < 0.0f);
				if (_canOccupyLocation(adjacent, volume, helper, fromAbove))
				{
					moveZ += dirZ;
				}
				else
				{
					effectiveZ = 0.0f;
					cancelZ = true;
				}
				break;
			}
			case INTERNAL:
				// We don't expect to see this outside of the initial call due to how we cancel movement on collision per-block.
				throw Assert.unreachable();
			default:
				throw Assert.unreachable();
			}
			leadingX += moveX;
			leadingY += moveY;
			leadingZ += moveZ;
			if (!cancelX)
			{
				effectiveX -= moveX;
			}
			if (!cancelY)
			{
				effectiveY -= moveY;
			}
			if (!cancelZ)
			{
				effectiveZ -= moveZ;
			}
			
			movingStart = new EntityLocation(movingStart.x() + moveX, movingStart.y() + moveY, movingStart.z() + moveZ);
			edgeLocation = new EntityLocation(edgeLocation.x() + moveX, edgeLocation.y() + moveY, edgeLocation.z() + moveZ);
			Assert.assertTrue(volume.width() == EntityLocation.roundToHundredths(edgeLocation.y() - movingStart.y()));
			
			leadingPoint = new EntityLocation(leadingX, leadingY, leadingZ);
			endOfVector = new EntityLocation(leadingX + effectiveX, leadingY + effectiveY, leadingZ + effectiveZ);
			AbsoluteLocation leadingBlock = leadingPoint.getBlockLocation();
			collision = RayCastHelpers.findFirstCollision(leadingPoint, endOfVector, (AbsoluteLocation currentBlock) -> {
				return !leadingBlock.equals(currentBlock);
			});
		}
		// If we didn't collide, we must have reached the end of the vector (make sure we get the position).
		movingStart = new EntityLocation(movingStart.x() + effectiveX
			, movingStart.y() + effectiveY
			, movingStart.z() + effectiveZ
		);
		helper.setLocationAndCancelVelocity(movingStart, cancelX, cancelY, cancelZ);
	}

	private static void _interactiveEntityMove(EntityLocation start, EntityVolume volume, EntityLocation vectorToMove, IInteractiveHelper helper)
	{
		// We want to handle the degenerate case of being stuck in a block first, because it avoids some setup and the 
		// common code assumes it isn't happening so will only report a partial collision.
		boolean fromAbove = true;
		boolean failZ = false;
		if ((vectorToMove.z() < 0.0f) && !_canOccupyLocation(start, volume, helper, fromAbove))
		{
			// Cancel the z-movement.
			vectorToMove = new EntityLocation(vectorToMove.x(), vectorToMove.y(), 0.0f);
			failZ = true;
		}
		fromAbove = false;
		if (_canOccupyLocation(start, volume, helper, fromAbove))
		{
			// We aren't stuck so use the common case.
			_interactiveEntityMoveNotStuck(start, volume, vectorToMove, helper, failZ);
		}
		else
		{
			// We are already stuck here so just fail out without moving, colliding on all axes.
			helper.setLocationAndCancelVelocity(start, true, true, true);
		}
	}

	private static float _clampByAirTerminal(float v)
	{
		float out;
		if (Math.abs(v) > EntityMovementHelpers.AIR_TERMINAL_VELOCITY_PER_SECOND)
		{
			out = Math.signum(v) * EntityMovementHelpers.AIR_TERMINAL_VELOCITY_PER_SECOND;
		}
		else
		{
			out = v;
		}
		return out;
	}

	private static EntityLocation _adjustVelocityForMovement(EntityLocation inputVelocity, float activeXMovement, float activeYMovement, float maxVelocityPerSecond, float seconds, float startViscosity)
	{
		// We calculate the effective velocity at the start of the action (which will be applied and further refined later):
		// 1) Convert XY movement, and gravity, into velocity per second.
		// 2) Add existing velocity to new velocity (note that XY cannot push the velocity over user maximum).
		// 3) Clamp new velocity to terminal velocity in air.
		// 4) Determine starting viscosity and multiply inverse viscosity against this clamped velocity.
		// The result is our effective starting velocity.
		
		float passiveX = inputVelocity.x();
		float passiveY = inputVelocity.y();
		float passiveZ = inputVelocity.z();
		
		float activeVX = activeXMovement / seconds;
		float activeVY = activeYMovement / seconds;
		float activeVZ = EntityMovementHelpers.GRAVITY_CHANGE_PER_SECOND * seconds;
		
		// XY velocity are the ones we can actively change so we will consider them together, limited by maxVelocityPerSecond.
		float sumVX = passiveX + activeVX;
		float sumVY = passiveY + activeVY;
		float squareMax = maxVelocityPerSecond * maxVelocityPerSecond;
		float squareX = sumVX * sumVX;
		float squareY = sumVY * sumVY;
		// Note that we only apply these checks if we were given a maximum velocity.
		if ((maxVelocityPerSecond > 0.0f) && ((squareX + squareY) > squareMax))
		{
			// To keep things simple, we will just combine these and then scale the final vector the max of the magnitudes of the original and max.
			// (this does mean that the user can actively direct the movement above maximum velocity but that isn't a big problem - also rare - and keeps this simple)
			float squarePassiveVelocity = passiveX * passiveX + passiveY * passiveY;
			float maxToImpose = Math.max(squareMax, squarePassiveVelocity);
			
			float fractionX = squareX / (squareX + squareY);
			sumVX = Math.signum(sumVX) * (float) Math.sqrt(fractionX * maxToImpose);
			sumVY = Math.signum(sumVY) * (float) Math.sqrt((1.0f - fractionX) * maxToImpose);
		}
		float sumVZ = passiveZ + activeVZ;
		
		// We now clamp everything by air terminal velocity.
		float airX = _clampByAirTerminal(sumVX);
		float airY = _clampByAirTerminal(sumVY);
		float airZ = _clampByAirTerminal(sumVZ);
		
		// Finally, multiply these clamped values by the inverse viscosity of the starting block.
		// TODO:  We probably need to rework this approach to drag, in the future.
		float startInverseViscosity = 1.0f - startViscosity;
		float visX = startInverseViscosity * airX;
		float visY = startInverseViscosity * airY;
		float visZ = startInverseViscosity * airZ;
		return new EntityLocation(visX, visY, visZ);
	}

	private static float _accumulateFlow(Environment env, TickProcessingContext.IBlockFetcher previousBlockLookUp, AbsoluteLocation relative, int testAgainst)
	{
		BlockProxy proxy = previousBlockLookUp.readBlock(relative);
		int flow = (null != proxy)
			? env.liquids.getFlowStrength(proxy.getBlock())
			: LiquidRegistry.FLOW_NONE
		;
		return (flow > testAgainst)
			? FLOW_VELOCITY_ADDED
			: 0.0f
		;
	}

	private static float _saturateComponentAddition(float start, float saturatingAddend)
	{
		float sum = start + saturatingAddend;
		float abs = Math.abs(sum);
		if ((abs > Math.abs(start)) && (abs > Math.abs(saturatingAddend)))
		{
			sum = saturatingAddend;
		}
		return sum;
	}


	public static interface IViscosityLookup
	{
		/**
		 * Gets the viscosity of the block in the given location as a fraction (0.0f is no resistance while 1.0f is
		 * fully solid).
		 * 
		 * @param location The location to check.
		 * @param fromAbove True if we are asking for viscosity while falling into the block, false for other
		 * collisions.
		 * @return The viscosity fraction (1.0f being solid).
		 */
		public float getViscosityForBlockAtLocation(AbsoluteLocation location, boolean fromAbove);
	}

	/**
	 * The interface used by interactiveEntityMove() in order to look up required information and return the final
	 * result.
	 */
	public static interface IInteractiveHelper extends IViscosityLookup
	{
		/**
		 * Gets the viscosity of the block in the given location as a fraction (0.0f is no resistance while 1.0f is
		 * fully solid).
		 * 
		 * @param location The location to check.
		 * @param fromAbove True if we are asking for viscosity while falling into the block, false for other
		 * collisions.
		 * @return The viscosity fraction (1.0f being solid).
		 */
		public float getViscosityForBlockAtLocation(AbsoluteLocation location, boolean fromAbove);
		/**
		 * The call issued by the interactive call to return all results instead of returning some kind of tuple.
		 * 
		 * @param finalLocation The location where the move stopped.
		 * @param cancelX True if there was a collision on the X axis (East/West).
		 * @param cancelY True if there was a collision on the Y axis (North/South).
		 * @param cancelZ True if there was a collision on the Z axis (Up/Down).
		 */
		public void setLocationAndCancelVelocity(EntityLocation finalLocation, boolean cancelX, boolean cancelY, boolean cancelZ);
	}

	/**
	 * Used to return the final result of commonMovementIdiom().
	 */
	public static record HighLevelMovementResult(EntityLocation location
		, EntityLocation velocity
		, boolean isOnGround
		, boolean didCollide
	) {}


	private static class _IInteractiveHelper implements IInteractiveHelper
	{
		private final ViscosityReader _reader;
		private final EntityLocation _effectiveVelocity;
		private final float _effectiveZMovement;
		public HighLevelMovementResult result;
		
		public _IInteractiveHelper(ViscosityReader reader, EntityLocation effectiveVelocity, float effectiveZMovement)
		{
			_reader = reader;
			_effectiveVelocity = effectiveVelocity;
			_effectiveZMovement = effectiveZMovement;
		}
		@Override
		public void setLocationAndCancelVelocity(EntityLocation finalLocation, boolean cancelX, boolean cancelY, boolean cancelZ)
		{
			// Derive final velocity by checking these collisions (note that we also account for XY surface friction if on the ground).
			boolean isOnGround = false;
			float finX = _effectiveVelocity.x();
			float finY = _effectiveVelocity.y();
			float finZ = _effectiveVelocity.z();
			if (cancelZ)
			{
				finZ = 0.0f;
				isOnGround = (_effectiveZMovement <= 0.0f);
			}
			if (cancelX || isOnGround)
			{
				finX = 0.0f;
			}
			if (cancelY || isOnGround)
			{
				finY = 0.0f;
			}
			EntityLocation velocity = new EntityLocation(finX, finY, finZ);
			boolean didCollide = cancelX || cancelY || cancelZ;
			this.result = new HighLevelMovementResult(finalLocation, velocity, isOnGround, didCollide);
		}
		@Override
		public float getViscosityForBlockAtLocation(AbsoluteLocation location, boolean fromAbove)
		{
			return _reader.getViscosityFraction(location, fromAbove);
		}
	}
}
