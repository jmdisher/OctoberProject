package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;
import java.util.List;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.logic.FireHelpers;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.IMutableBlockProxy;
import com.jeffdisher.october.types.IMutationBlock;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * These mutations are created when another block starts burning, when lava flows into a block, or when a block is
 * placed next to fire or lava.
 */
public class MutationBlockStartFire implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.START_FIRE;
	/**
	 * We will delay ignition by 2 seconds.
	 */
	public static final long IGNITION_DELAY_MILLIS = 2_000L;

	public static MutationBlockStartFire deserialize(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		return new MutationBlockStartFire(location);
	}


	private final AbsoluteLocation _blockLocation;

	public MutationBlockStartFire(AbsoluteLocation blockLocation)
	{
		_blockLocation = blockLocation;
	}

	@Override
	public AbsoluteLocation getAbsoluteLocation()
	{
		return _blockLocation;
	}

	@Override
	public void applyMutation(TickProcessingContext context, IMutableBlockProxy newBlock)
	{
		// Check if this is flammable and isn't already burning.
		Environment env = Environment.getShared();
		if (FireHelpers.canIgnite(env, context, _blockLocation, newBlock))
		{
			_igniteBlockAndSpread(env, context, _blockLocation, newBlock);
		}
	}

	@Override
	public MutationBlockType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writeAbsoluteLocation(buffer, _blockLocation);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Common case.
		return true;
	}


	private static void _igniteBlockAndSpread(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy proxy)
	{
		byte flags = proxy.getFlags();
		
		// Make sure that this isn't already on fire.
		Assert.assertTrue(!FlagsAspect.isSet(flags, FlagsAspect.FLAG_BURNING));
		// Set us on fire (in the future, this will probably be made random).
		flags = FlagsAspect.set(flags, FlagsAspect.FLAG_BURNING);
		proxy.setFlags(flags);
		
		// Schedule the mutation to finish burning.
		context.mutationSink.future(new MutationBlockBurnDown(location), MutationBlockBurnDown.BURN_DELAY_MILLIS);
		
		// See if there are any ignition blocks around this.
		List<AbsoluteLocation> flammable = FireHelpers.findFlammableNeighbours(env, context, location);
		for (AbsoluteLocation neighour : flammable)
		{
			MutationBlockStartFire startFire = new MutationBlockStartFire(neighour);
			context.mutationSink.future(startFire, MutationBlockStartFire.IGNITION_DELAY_MILLIS);
		}
	}
}
