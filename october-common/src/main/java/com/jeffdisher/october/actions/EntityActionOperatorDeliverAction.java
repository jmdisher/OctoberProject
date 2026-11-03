package com.jeffdisher.october.actions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.MutableCreature;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * An operator action which just schedules the given action on the target entity or creature.
 */
public class EntityActionOperatorDeliverAction implements IEntityAction<IMutablePlayerEntity>
{
	public static final EntityActionType TYPE = EntityActionType.OPERATOR_DELIVER_ACTION;

	public static EntityActionOperatorDeliverAction deserialize(DeserializationContext context)
	{
		// This is never serialized.
		throw Assert.unreachable();
	}


	private final int _targetId;
	private final IEntityAction<IMutablePlayerEntity> _playerAction;
	private final IEntityAction<MutableCreature> _creatureAction;

	public EntityActionOperatorDeliverAction(int targetId, IEntityAction<IMutablePlayerEntity> playerAction, IEntityAction<MutableCreature> creatureAction)
	{
		if (targetId > 0)
		{
			Assert.assertTrue(null != playerAction);
		}
		else if (targetId < 0)
		{
			Assert.assertTrue(null != creatureAction);
		}
		else
		{
			Assert.assertTrue(false);
		}
		
		_targetId = targetId;
		_playerAction = playerAction;
		_creatureAction = creatureAction;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		if (_targetId > 0)
		{
			context.newChangeSink.next(_targetId, _playerAction);
		}
		else
		{
			context.newChangeSink.creature(_targetId, _creatureAction);
		}
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
		// This is never serialized.
		throw Assert.unreachable();
	}

	@Override
	public boolean canSaveToDisk()
	{
		// It doesn't make sense to serialize this.
		return false;
	}

	@Override
	public String toString()
	{
		return "Operator-Deliver " + _targetId
			+ ": " + ((_targetId > 0) ? _playerAction : _creatureAction)
		;
	}
}
