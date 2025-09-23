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
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IEntitySubAction;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


public class Deprecated_EntityChangeTopLevelMovement<T extends IMutableMinimalEntity> implements IEntityAction<T>
{
	public static final EntityActionType TYPE = EntityActionType.DEPRECATED_TOP_LEVEL_MOVEMENT;
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
		EntitySubActionType.TESTING_ONLY,
	}).collect(Collectors.toSet());

	public static <T extends IMutableMinimalEntity> Deprecated_EntityChangeTopLevelMovement<T> deserialize(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		EntityLocation newLocation = CodecHelpers.readEntityLocation(buffer);
		EntityLocation newVelocity = CodecHelpers.readEntityLocation(buffer);
		Intensity intensity = Intensity.read(buffer);
		byte yaw = buffer.get();
		byte pitch = buffer.get();
		IEntitySubAction<T> subAction = CodecHelpers.readNullableNestedChange(buffer);
		return new Deprecated_EntityChangeTopLevelMovement<>(newLocation, newVelocity, intensity, yaw, pitch, subAction);
	}


	private final EntityLocation _newLocation;
	private final EntityLocation _newVelocity;
	private final Intensity _intensity;
	private final byte _yaw;
	private final byte _pitch;
	private final IEntitySubAction<T> _subAction;

	@Deprecated
	public Deprecated_EntityChangeTopLevelMovement(EntityLocation newLocation
		, EntityLocation newVelocity
		, Intensity intensity
		, byte yaw
		, byte pitch
		, IEntitySubAction<T> subAction
	)
	{
		Assert.assertTrue((null == subAction) || ALLOWED_TYPES.contains(subAction.getType()));
		
		_newLocation = newLocation;
		_newVelocity = newVelocity;
		_intensity = intensity;
		_yaw = yaw;
		_pitch = pitch;
		_subAction = subAction;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, T newEntity)
	{
		// No longer used.
		return true;
	}

	@Override
	public EntityActionType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writeEntityLocation(buffer, _newLocation);
		CodecHelpers.writeEntityLocation(buffer, _newVelocity);
		Intensity.write(buffer, _intensity);
		buffer.put(_yaw);
		buffer.put(_pitch);
		CodecHelpers.writeNullableNestedChange(buffer, _subAction);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Common case.
		return true;
	}

	@Override
	public String toString()
	{
		return String.format("Top-Level(%s), L(%s), V(%s), Sub: %s", _intensity, _newLocation, _newVelocity, _subAction);
	}


	public static enum Intensity
	{
		STANDING(0.0f, 0),
		WALKING(1.0f, EntityChangePeriodic.ENERGY_COST_PER_TICK_WALKING),
		RUNNING(2.0f, EntityChangePeriodic.ENERGY_COST_PER_TICK_RUNNING),
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
