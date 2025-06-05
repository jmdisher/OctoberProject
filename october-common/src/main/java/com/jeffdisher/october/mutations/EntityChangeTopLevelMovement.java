package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * The basis of the "new" movement design in version 1.7 where changes are now fixed to the tick period and can contain
 * a single nested operation to do more than just movement (jump/craft/place/break/etc).
 */
public class EntityChangeTopLevelMovement<T extends IMutableMinimalEntity> implements IMutationEntity<T>
{
	public static final MutationEntityType TYPE = MutationEntityType.TOP_LEVEL_MOVEMENT;

	public static <T extends IMutableMinimalEntity> EntityChangeTopLevelMovement<T> deserializeFromBuffer(ByteBuffer buffer)
	{
		EntityLocation newLocation = CodecHelpers.readEntityLocation(buffer);
		EntityLocation newVelocity = CodecHelpers.readEntityLocation(buffer);
		Intensity intensity = Intensity.read(buffer);
		byte yaw = buffer.get();
		byte pitch = buffer.get();
		IMutationEntity<T> subAction = CodecHelpers.readNullableNestedChange(buffer);
		EntityLocation subActionLocation = CodecHelpers.readNullableEntityLocation(buffer);
		long millis = buffer.getLong();
		return new EntityChangeTopLevelMovement<>(newLocation, newVelocity, intensity, yaw, pitch, subAction, subActionLocation, millis);
	}


	private final EntityLocation _newLocation;
	private final EntityLocation _newVelocity;
	private final Intensity _intensity;
	private final byte _yaw;
	private final byte _pitch;
	private final IMutationEntity<T> _subAction;
	private final EntityLocation _subActionLocation;
	private final long _millis;

	public EntityChangeTopLevelMovement(EntityLocation newLocation
		, EntityLocation newVelocity
		, Intensity intensity
		, byte yaw
		, byte pitch
		, IMutationEntity<T> subAction
		, EntityLocation subActionLocation
		, long millis
	)
	{
		Assert.assertTrue((null != subAction) == (null != subActionLocation));
		Assert.assertTrue(millis > 0L);
		
		_newLocation = newLocation;
		_newVelocity = newVelocity;
		_intensity = intensity;
		_yaw = yaw;
		_pitch = pitch;
		_subAction = subAction;
		_subActionLocation = subActionLocation;
		_millis = millis;
	}

	@Override
	public long getTimeCostMillis()
	{
		// TODO:  Eventually this will be removed as the changes become full-tick.
		return _millis;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, T newEntity)
	{
		// TODO:  Add checks that these moves are valid since we currently just trust whatever the client is telling us.
		if (null != _subAction)
		{
			newEntity.setLocation(_subActionLocation);
			_subAction.applyChange(context, newEntity);
		}
		newEntity.setLocation(_newLocation);
		newEntity.setVelocityVector(_newVelocity);
		newEntity.setOrientation(_yaw, _pitch);
		
		// TODO:  Fix this energy attribution cost.
		int energy;
		switch (_intensity)
		{
		case STANDING:
			energy = EntityChangePeriodic.ENERGY_COST_IDLE;
			break;
		case WALKING:
			energy = EntityChangePeriodic.ENERGY_COST_MOVE_PER_BLOCK;
			break;
		default:
			throw Assert.unreachable();
		}
		newEntity.applyEnergyCost(energy);
		return true;
	}

	@Override
	public MutationEntityType getType()
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
		CodecHelpers.writeNullableEntityLocation(buffer, _subActionLocation);
		buffer.putLong(_millis);
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
		return "Top-level";
	}


	public static enum Intensity
	{
		STANDING,
		WALKING,
		;
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
