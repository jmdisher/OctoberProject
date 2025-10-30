package com.jeffdisher.october.actions;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.logic.EntityMovementHelpers;
import com.jeffdisher.october.mutations.EntityActionType;
import com.jeffdisher.october.mutations.EntitySubActionType;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IEntitySubAction;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * The basic top-level movement action of an entity.  This replaces EntityChangeTopLevelMovement as that approach was
 * too complicated and was based on an old velocity vector approach which didn't easily allow for external knockback.
 * Note that the units of _activeX and _activeY are in total movement in each 2D axis within the tick (meaning dividing
 * by the tick's fraction of a second will return velocity).
 */
public class EntityActionSimpleMove<T extends IMutableMinimalEntity> implements IEntityAction<T>
{
	public static final EntityActionType TYPE = EntityActionType.SIMPLE_MOVE;
	public static final float FUDGE_FACTOR = 1.05f;
	/**
	 * The white-list of sub-actions which can be sent by a client.
	 */
	public static final Set<EntitySubActionType> ALLOWED_TYPES = Arrays.stream(new EntitySubActionType[] {
		EntitySubActionType.JUMP,
		EntitySubActionType.SWIM,
		EntitySubActionType.BLOCK_PLACE,
		EntitySubActionType.CRAFT,
		EntitySubActionType.SELECT_ITEM,
		EntitySubActionType.ITEMS_REQUEST_PUSH,
		EntitySubActionType.ITEMS_REQUEST_PULL,
		EntitySubActionType.INCREMENTAL_BREAK_BLOCK,
		EntitySubActionType.CRAFT_IN_BLOCK,
		EntitySubActionType.ATTACK_ENTITY,
		EntitySubActionType.USE_SELECTED_ITEM_ON_SELF,
		EntitySubActionType.USE_SELECTED_ITEM_ON_BLOCK,
		EntitySubActionType.USE_SELECTED_ITEM_ON_ENTITY,
		EntitySubActionType.CHANGE_HOTBAR_SLOT,
		EntitySubActionType.SWAP_ARMOUR,
		EntitySubActionType.SET_BLOCK_LOGIC_STATE,
		EntitySubActionType.SET_DAY_AND_SPAWN,
		EntitySubActionType.INCREMENTAL_REPAIR_BLOCK,
		EntitySubActionType.MULTI_BLOCK_PLACE,
		EntitySubActionType.LADDER_ASCEND,
		EntitySubActionType.LADDER_DESCEND,
		EntitySubActionType.ITEM_SLOT_REQUEST_SWAP,
		EntitySubActionType.TRAVEL_VIA_BLOCK,
		EntitySubActionType.DROP_ITEMS_AS_PASSIVE,
		EntitySubActionType.PICK_UP_ITEMS_PASSIVE,
		EntitySubActionType.POP_OUT,
		EntitySubActionType.TESTING_ONLY,
	}).collect(Collectors.toSet());

	public static <T extends IMutableMinimalEntity> EntityActionSimpleMove<T> deserialize(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		float activeX = buffer.getFloat();
		float activeY = buffer.getFloat();
		Intensity intensity = Intensity.read(buffer);
		byte yaw = buffer.get();
		byte pitch = buffer.get();
		IEntitySubAction<T> subAction = CodecHelpers.readNullableNestedChange(buffer);
		return new EntityActionSimpleMove<>(activeX, activeY, intensity, yaw, pitch, subAction);
	}


	private final float _activeX;
	private final float _activeY;
	private final Intensity _intensity;
	private final byte _yaw;
	private final byte _pitch;
	private final IEntitySubAction<T> _subAction;

	public EntityActionSimpleMove(float activeX
		, float activeY
		, Intensity intensity
		, byte yaw
		, byte pitch
		, IEntitySubAction<T> subAction
	)
	{
		Assert.assertTrue(null != intensity);
		Assert.assertTrue((null == subAction) || ALLOWED_TYPES.contains(subAction.getType()));
		
		_activeX = activeX;
		_activeY = activeY;
		_intensity = intensity;
		_yaw = yaw;
		_pitch = pitch;
		_subAction = subAction;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, T newEntity)
	{
		// We need to verify that _active* is valid within the tick rate and speed allowed by _intensity.
		
		// At this point, we will run the _subAction.  If this fails, we will fail the move.
		
		// If the _active* is considered valid, then we use existing velocity to determining passive movement,
		// add the active movement to this, apply viscosity multiplier, and move by the given amount, considering
		// collision to determine final location.  This stage will not fail but may not move the player.
		
		// Finally, as long as the _active* is considered valid, we will update velocity based on tick rate,
		// viscosity of the final block(s), friction of the surface(s) we are standing on (assuming we are standing on
		// something solid).  This stage will not fail.
		
		float seconds = ((float)context.millisPerTick / 1000.0f);
		float entityBlocksPerSecond = newEntity.getType().blocksPerSecond();
		float intensityVelocityPerSecond = _intensity.speedMultipler * entityBlocksPerSecond;
		float maxMoveInTick = seconds * intensityVelocityPerSecond;
		boolean isValidMovement = ((_activeX * _activeX)
				+ (_activeY * _activeY)
			) <= (maxMoveInTick * maxMoveInTick * FUDGE_FACTOR)
		;
		
		boolean forceFailure = !isValidMovement;
		if (!forceFailure)
		{
			if (null != _subAction)
			{
				boolean subActionSuccess = _subAction.applyChange(context, newEntity);
				if (!subActionSuccess)
				{
					forceFailure = true;
				}
			}
			else
			{
				// If there is no sub-action, clear whatever partial action we may have been performing.
				newEntity.resetLongRunningOperations();
			}
		}
		
		if (!forceFailure)
		{
			// Explanation of velocity and movement here:
			// Velocity is calculated first, based on the velocity derived from the active movement of this action, but
			// also added to the residual velocity from the previous tick and gravity applied here.
			// From there, movement is calculated from this velocity.
			// Finally, any collisions are used to reset velocity in specific axes.
			// This final location and velocity is then saved back to the entity.
			
			// Derive starting effective velocity.
			EntityLocation startLocation = newEntity.getLocation();
			EntityLocation startVelocity = newEntity.getVelocityVector();
			EntityVolume volume = newEntity.getType().volume();
			EntityMovementHelpers.HighLevelMovementResult result = EntityMovementHelpers.commonMovementIdiom(context.previousBlockLookUp
				, startLocation
				, startVelocity
				, volume
				, _activeX
				, _activeY
				, intensityVelocityPerSecond
				, seconds
			);
			
			// TODO:  For now, we cancel all velocity to make this feel responsive but we probably need a quadratic drag
			// approximation here in order to explosion knockback, etc, to look/feel right.
			EntityLocation finalVelocity = new EntityLocation(0.0f, 0.0f, result.vZ());
			
			// Save back final state.
			newEntity.setLocation(result.location());
			newEntity.setVelocityVector(finalVelocity);
			newEntity.setOrientation(_yaw, _pitch);
			
			newEntity.applyEnergyCost(_intensity.energyCostPerTick);
		}
		return !forceFailure;
	}

	@Override
	public EntityActionType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		buffer.putFloat(_activeX);
		buffer.putFloat(_activeY);
		Intensity.write(buffer, _intensity);
		buffer.put(_yaw);
		buffer.put(_pitch);
		CodecHelpers.writeNullableNestedChange(buffer, _subAction);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Whether or not this can be serialized depends on the sub-action.
		return (null == _subAction) || _subAction.canSaveToDisk();
	}

	@Override
	public String toString()
	{
		return String.format("SimpleMove(%s), by %.2f, %.2f, Sub: %s", _intensity, _activeX, _activeY, _subAction);
	}

	/**
	 * Provided purely so that tests can verify the internal sub-action is what they expect, since this is otherwise
	 * opaque and now contains the details of actions which were previously in smaller instances.
	 * 
	 * @return The sub-action.
	 */
	public IEntitySubAction<T> test_getSubAction()
	{
		return _subAction;
	}


	public static enum Intensity
	{
		STANDING(0.0f, 0),
		WALKING(1.0f, EntityActionPeriodic.ENERGY_COST_PER_TICK_WALKING),
		RUNNING(2.0f, EntityActionPeriodic.ENERGY_COST_PER_TICK_RUNNING),
		;
		public final float speedMultipler;
		public final int energyCostPerTick;
		private Intensity(float speedMultipler, int energyCostPerTick)
		{
			this.speedMultipler = speedMultipler;
			this.energyCostPerTick = energyCostPerTick;
		}
		public static Intensity read(ByteBuffer buffer)
		{
			byte ordinal = buffer.get();
			return Intensity.values()[ordinal];
		}
		public static void write(ByteBuffer buffer, Intensity intensity)
		{
			byte ordinal = (byte)intensity.ordinal();
			buffer.put(ordinal);
		}
	}
}
