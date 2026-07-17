package com.jeffdisher.october.block_movement;

import java.nio.ByteBuffer;

import com.jeffdisher.october.actions.EntityActionPush;
import com.jeffdisher.october.mutations.MutationBlockType;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.IMutableBlockProxy;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.IMutationBlock;
import com.jeffdisher.october.types.MutableCreature;
import com.jeffdisher.october.types.PartialPassive;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * A mutation which will schedule a "push" on any players, creatures, or passives in a block.
 * This is done using a mutation, instead of called directly, so that the operation can be run as part of a transaction
 * along with other block move operations.
 * NOTE:  This is expected to only be used in transactions (related to block movement) so it isn't serialized.
 */
public class MutationBlockPushEntities implements IMutationBlock
{
	private final AbsoluteLocation _blockLocation;
	private final EntityLocation _pushVector;

	public MutationBlockPushEntities(AbsoluteLocation blockLocation, EntityLocation pushVector)
	{
		_blockLocation = blockLocation;
		_pushVector = pushVector;
	}

	@Override
	public AbsoluteLocation getAbsoluteLocation()
	{
		return _blockLocation;
	}

	@Override
	public void applyMutation(TickProcessingContext context, IMutableBlockProxy newBlock)
	{
		// Check what is in this block.
		EntityLocation base = _blockLocation.toEntityLocation();
		EntityLocation edge = _blockLocation.getRelative(1, 1, 1).toEntityLocation();
		
		// Search for passives.
		PartialPassive[] passives = context.previousPassiveLookUp.findPassiveItemSlotsInRegion(base, edge);
		PassiveActionPush push = new PassiveActionPush(_pushVector);
		for (PartialPassive passive : passives)
		{
			int id = passive.id();
			context.newChangeSink.passive(id, push);
		}
		
		int[] entities = context.previousEntityLookUp.findEntityIdsInRegion(base, edge);
		EntityActionPush<MutableCreature> creaturePush = new EntityActionPush<>(_pushVector);
		EntityActionPush<IMutablePlayerEntity> playerPush = new EntityActionPush<>(_pushVector);
		for (int id : entities)
		{
			if (id > 0)
			{
				context.newChangeSink.next(id, playerPush);
			}
			else
			{
				context.newChangeSink.creature(id, creaturePush);
			}
		}
	}

	@Override
	public MutationBlockType getType()
	{
		// Not serialized so we don't need a type.
		throw Assert.unreachable();
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		// Not serialized.
		throw Assert.unreachable();
	}

	@Override
	public boolean canSaveToDisk()
	{
		// This is only used in transactions, which aren't saved to disk.
		return false;
	}
}
