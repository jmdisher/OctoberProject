package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.IMutableBlockProxy;
import com.jeffdisher.october.types.IMutationBlock;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Applies a mutation to a given location in response to an earlier call to IMutableBlockProxy.requestFutureMutation(long).
 * An example use-case of this is plant growth, as this is scheduled periodically.
 */
public class MutationBlockPeriodic implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.PERIODIC;

	public static MutationBlockPeriodic deserialize(DeserializationContext context)
	{
		// We don't normally need to deserialize these, since they are never stored, but pre-V4 cuboid storage contains them.
		ByteBuffer buffer = context.buffer();
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		return new MutationBlockPeriodic(location);
	}


	private final AbsoluteLocation _location;

	public MutationBlockPeriodic(AbsoluteLocation location)
	{
		_location = location;
	}

	@Override
	public AbsoluteLocation getAbsoluteLocation()
	{
		return _location;
	}

	@Override
	public void applyMutation(TickProcessingContext context, IMutableBlockProxy newBlock)
	{
		Environment env = Environment.getShared();
		env.hooks.doRunPeriodic(env, context, _location, newBlock);
	}

	@Override
	public MutationBlockType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		// These are no longer written to disk and never written to network (was written to disk in pre-V4 cuboid storage).
		throw Assert.unreachable();
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Periodic mutations only exist internally, synthesized when needed from specific rules (except for pre-V4 cuboid storage).
		return false;
	}
}
