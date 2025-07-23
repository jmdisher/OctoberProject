package com.jeffdisher.october.actions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.OrientationAspect;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.mutations.EntityActionType;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.TickProcessingContext;


public class Deprecated_EntityActionMultiBlockPlace implements IEntityAction<IMutablePlayerEntity>
{
	public static final EntityActionType TYPE = EntityActionType.DEPRECATED_MULTI_BLOCK_PLACE;

	public static Deprecated_EntityActionMultiBlockPlace deserialize(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		AbsoluteLocation target = CodecHelpers.readAbsoluteLocation(buffer);
		OrientationAspect.Direction orientation = CodecHelpers.readOrientation(buffer);
		return new Deprecated_EntityActionMultiBlockPlace(target, orientation);
	}


	private final AbsoluteLocation _targetBlock;
	private final OrientationAspect.Direction _orientation;

	@Deprecated
	public Deprecated_EntityActionMultiBlockPlace(AbsoluteLocation targetBlock, OrientationAspect.Direction orientation)
	{
		_targetBlock = targetBlock;
		_orientation = orientation;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		// Not used.
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
		CodecHelpers.writeAbsoluteLocation(buffer, _targetBlock);
		CodecHelpers.writeOrientation(buffer, _orientation);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Block reference.
		return false;
	}

	@Override
	public String toString()
	{
		return "Place selected multi-block " + _targetBlock + " orientation " + _orientation;
	}
}
