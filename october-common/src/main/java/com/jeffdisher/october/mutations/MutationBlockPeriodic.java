package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.logic.CompositeHelpers;
import com.jeffdisher.october.logic.HopperHelpers;
import com.jeffdisher.october.logic.PlantHelpers;
import com.jeffdisher.october.logic.PortalHelpers;
import com.jeffdisher.october.logic.SpecialLogicChangeHelpers;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Applies a mutation to a given location in response to an earlier call to IMutableBlockProxy.requestFutureMutation(long).
 * An example use-case of this is plant growth, as this is scheduled periodically.
 */
public class MutationBlockPeriodic implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.PERIODIC;
	public static final long MILLIS_BETWEEN_GROWTH_CALLS = 10_000L;
	public static final long MILLIS_BETWEEN_HOPPER_CALLS = 1_000L;

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
	public boolean applyMutation(TickProcessingContext context, IMutableBlockProxy newBlock)
	{
		Environment env = Environment.getShared();
		boolean didApply = false;
		// Make sure that this is a block which can grow.
		Block block = newBlock.getBlock();
		if (PlantHelpers.canGrow(env, block))
		{
			boolean shouldReschedule = PlantHelpers.shouldRescheduleAfterPlantPeriodic(env, context, _location, newBlock, block);
			
			if (shouldReschedule)
			{
				newBlock.requestFutureMutation(MILLIS_BETWEEN_GROWTH_CALLS);
			}
			didApply = true;
		}
		else if (env.logic.hasSpecialChangeLogic(block))
		{
			byte flags = newBlock.getFlags();
			SpecialLogicChangeHelpers.periodicUpdate(context, newBlock, _location, block, flags);
			didApply = true;
		}
		else if (HopperHelpers.isHopper(_location, newBlock))
		{
			HopperHelpers.tryProcessHopper(context, _location, newBlock);
			newBlock.requestFutureMutation(MILLIS_BETWEEN_HOPPER_CALLS);
			didApply = true;
		}
		else if (CompositeHelpers.isActiveCornerstone(block))
		{
			// See if we need to change the state of the composite.
			// Note that this implicitly calls requestFutureMutation (called via multiple paths).
			CompositeHelpers.processCornerstoneUpdate(env, context, _location, newBlock);
			
			// If this is the portal cornerstone, see if it needs to update the portal.
			if (PortalHelpers.isKeystone(newBlock))
			{
				PortalHelpers.handlePortalSurface(env, context, _location, newBlock);
			}
			didApply = true;
		}
		return didApply;
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
