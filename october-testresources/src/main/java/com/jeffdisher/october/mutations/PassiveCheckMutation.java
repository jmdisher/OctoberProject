package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.PartialPassive;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * A test mutation used to test context.previousPassiveLookUp.findPassivesInRegion:  It just captures all the passives
 * which can be found intersecting the 27 blocks centred around its target block location.
 */
public class PassiveCheckMutation implements IMutationBlock
{
	private final AbsoluteLocation _location;
	public PartialPassive[] output;

	public PassiveCheckMutation(AbsoluteLocation location)
	{
		_location = location;
	}

	@Override
	public AbsoluteLocation getAbsoluteLocation()
	{
		return _location;
	}

	@Override
	public boolean applyMutation(TickProcessingContext context, IMutableBlockProxy newBlock)
	{
		// We just want to store the output.
		Assert.assertTrue(null == this.output);
		EntityLocation base = _location.getRelative(-1, -1, -1).toEntityLocation();
		EntityLocation edge = _location.getRelative(2, 2, 2).toEntityLocation();
		this.output = context.previousPassiveLookUp.findPassiveItemSlotsInRegion(base, edge);
		return null != this.output;
	}

	@Override
	public MutationBlockType getType()
	{
		// Only used in tests.
		throw Assert.unreachable();
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		// Only used in tests.
		throw Assert.unreachable();
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Only used in tests.
		throw Assert.unreachable();
	}
}
