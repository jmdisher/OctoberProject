package com.jeffdisher.october.actions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.creatures.CreatureLogic;
import com.jeffdisher.october.mutations.EntityActionType;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutableCreatureEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Tells a creature that it should enter a "pregnant" mode if in love mode and not already pregnant.  The location of
 * the "sire" is also passed for consideration in where the offspring should spawn.
 */
public class EntityActionImpregnateCreature implements IEntityAction<IMutableCreatureEntity>
{
	private final EntityLocation _sireLocation;

	public EntityActionImpregnateCreature(EntityLocation sireLocation)
	{
		_sireLocation = sireLocation;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutableCreatureEntity newEntity)
	{
		// We will say that this worked if the logic helper says they were set pregnant.
		return CreatureLogic.setCreaturePregnant(newEntity, _sireLocation);
	}

	@Override
	public EntityActionType getType()
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
		return "Impregnate by sire at " + _sireLocation;
	}
}
