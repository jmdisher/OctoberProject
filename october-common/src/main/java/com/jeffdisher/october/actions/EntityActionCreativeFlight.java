package com.jeffdisher.october.actions;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.mutations.EntityActionType;
import com.jeffdisher.october.mutations.EntitySubActionType;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.IEntitySubAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Direct top-level actions from a player is typically sent as EntityActionSimpleMove.  This is a sibling action to that
 * which can be sent if the player is in creative mode.  It has most of the same sub-actions, except for those related
 * to movement, and otherwise allows free movement through the world, ignoring gravity or collision.
 * Note that there is no "flight mode", from the server's perspective.  A client would need to maintain this mode
 * setting in order to know which kind of action to send but the server will permit either, so long as the entity is in
 * "creative mode".
 */
public class EntityActionCreativeFlight implements IEntityActionFromClient<IMutablePlayerEntity>
{
	public static final EntityActionType TYPE = EntityActionType.CREATIVE_FLIGHT;
	public static final float MIN_COAST_VELOCITY_AXIS = 0.05f;
	/**
	 * The white-list of sub-actions which can be sent by a client.  This is essentially the subset of those in
	 * EntityActionSimpleMove, removing those related to movement related to collision or gravity.
	 */
	public static final Set<EntitySubActionType> ALLOWED_TYPES = Arrays.stream(new EntitySubActionType[] {
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
		EntitySubActionType.ITEM_SLOT_REQUEST_SWAP,
		EntitySubActionType.TRAVEL_VIA_BLOCK,
		EntitySubActionType.DROP_ITEMS_AS_PASSIVE,
		EntitySubActionType.PICK_UP_ITEMS_PASSIVE,
		EntitySubActionType.CHARGE_WEAPON,
		EntitySubActionType.TRIGGER_CHARGED_WEAPON,
		EntitySubActionType.TESTING_ONLY,
	}).collect(Collectors.toSet());

	public static EntityActionCreativeFlight deserialize(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		float activeX = buffer.getFloat();
		float activeY = buffer.getFloat();
		float activeZ = buffer.getFloat();
		byte yaw = buffer.get();
		byte pitch = buffer.get();
		IEntitySubAction<IMutablePlayerEntity> subAction = CodecHelpers.readNullableNestedChange(context);
		return new EntityActionCreativeFlight(activeX, activeY, activeZ, yaw, pitch, subAction);
	}


	private final float _activeX;
	private final float _activeY;
	private final float _activeZ;
	private final byte _yaw;
	private final byte _pitch;
	private final IEntitySubAction<IMutablePlayerEntity> _subAction;

	public EntityActionCreativeFlight(float activeX
		, float activeY
		, float activeZ
		, byte yaw
		, byte pitch
		, IEntitySubAction<IMutablePlayerEntity> subAction
	)
	{
		Assert.assertTrue((null == subAction) || ALLOWED_TYPES.contains(subAction.getType()));
		
		_activeX = activeX;
		_activeY = activeY;
		_activeZ = activeZ;
		_yaw = yaw;
		_pitch = pitch;
		_subAction = subAction;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		// The only check here is that we are in creative mode.
		boolean didApply = false;
		
		if (newEntity.isCreativeMode())
		{
			boolean subActionSuccess = true;
			if (null != _subAction)
			{
				subActionSuccess = _subAction.applyChange(context, newEntity);
			}
			if (subActionSuccess)
			{
				// Coast velocity in a basic way (ignore viscosity).
				EntityLocation velocity = newEntity.getVelocityVector();
				float vX = velocity.x() / 2.0f;
				float vY = velocity.y() / 2.0f;
				float vZ = velocity.z() / 2.0f;
				if (Math.abs(vX) < MIN_COAST_VELOCITY_AXIS)
				{
					vX = 0.0f;
				}
				if (Math.abs(vY) < MIN_COAST_VELOCITY_AXIS)
				{
					vY = 0.0f;
				}
				if (Math.abs(vZ) < MIN_COAST_VELOCITY_AXIS)
				{
					vZ = 0.0f;
				}
				
				// We will set our maximum to 2x the normal walking speed.
				float seconds = ((float)context.millisPerTick / 1000.0f);
				float entityBlocksPerSecond = 2.0f * newEntity.getType().blocksPerSecond();
				EntityLocation newVelocity = new EntityLocation(vX + _activeX, vY + _activeY, vZ + _activeZ);
				float magnitude = newVelocity.getMagnitude();
				if (magnitude > entityBlocksPerSecond)
				{
					float scaleDown = entityBlocksPerSecond / magnitude;
					newVelocity = newVelocity.makeScaledInstance(scaleDown);
				}
				
				EntityLocation location = newEntity.getLocation();
				float mX = seconds * newVelocity.x();
				float mY = seconds * newVelocity.y();
				float mZ = seconds * newVelocity.z();
				EntityLocation newLocation = new EntityLocation(location.x() + mX, location.y() + mY, location.z() + mZ);
				
				newEntity.setOrientation(_yaw, _pitch);
				newEntity.setLocation(newLocation);
				newEntity.setVelocityVector(newVelocity);
				didApply = true;
			}
		}
		return didApply;
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
		buffer.putFloat(_activeZ);
		buffer.put(_yaw);
		buffer.put(_pitch);
		CodecHelpers.writeNullableNestedChange(buffer, _subAction);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Don't bother save creative flight to disk (we still need the codec logic since it goes over the network).
		return false;
	}

	@Override
	public String toString()
	{
		return String.format("CreativeFlight by %.2f, %.2f, %.2f, Sub: %s", _activeX, _activeY, _activeZ, _subAction);
	}

	/**
	 * Some tests but also things like MovementAccumulator need to know if there is a special sub-action inside this
	 * instance so this allows them to view it.
	 * 
	 * @return The sub-action.
	 */
	public IEntitySubAction<IMutablePlayerEntity> getSubAction()
	{
		return _subAction;
	}
}
