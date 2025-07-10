package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.creatures.CreatureLogic;
import com.jeffdisher.october.types.IMutableCreatureEntity;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Applies an item to a creature, potentially changing its state or behaviour.
 * The item should be checked that this is valid before creating the change object so this will fail if it has become
 * invalid in the interim (doesn't attempt to return the item, for example).
 */
public class EntityChangeApplyItemToCreature implements IMutationEntity<IMutableCreatureEntity>
{
	private final Item _itemType;

	public EntityChangeApplyItemToCreature(Item itemType)
	{
		_itemType = itemType;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutableCreatureEntity newEntity)
	{
		// We will say that this worked if the logic helper says it applied.
		return CreatureLogic.applyItemToCreature(_itemType, newEntity);
	}

	@Override
	public MutationEntityType getType()
	{
		// Not in creature-only types.
		throw Assert.unreachable();
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		// Not in creature-only types.
		throw Assert.unreachable();
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Not in creature-only types.
		throw Assert.unreachable();
	}

	@Override
	public String toString()
	{
		return "Apply item to creature " + _itemType;
	}
}
